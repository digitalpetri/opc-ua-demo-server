package com.digitalpetri.opcua.server.gds;

import static org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.Unsigned.ubyte;
import static org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.Unsigned.uint;

import com.digitalpetri.opcua.server.DemoServerCapabilities;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.eclipse.milo.opcua.sdk.client.DiscoveryClient;
import org.eclipse.milo.opcua.sdk.client.OpcUaClient;
import org.eclipse.milo.opcua.sdk.client.OpcUaClientConfig;
import org.eclipse.milo.opcua.sdk.client.gds.GdsClient;
import org.eclipse.milo.opcua.sdk.client.identity.AnonymousProvider;
import org.eclipse.milo.opcua.sdk.client.identity.IdentityProvider;
import org.eclipse.milo.opcua.sdk.client.identity.UsernameProvider;
import org.eclipse.milo.opcua.sdk.server.OpcUaServer;
import org.eclipse.milo.opcua.stack.core.Stack;
import org.eclipse.milo.opcua.stack.core.StatusCodes;
import org.eclipse.milo.opcua.stack.core.UaException;
import org.eclipse.milo.opcua.stack.core.gds.types.ApplicationRecordDataType;
import org.eclipse.milo.opcua.stack.core.security.CertificateGroup;
import org.eclipse.milo.opcua.stack.core.security.DefaultClientCertificateValidator;
import org.eclipse.milo.opcua.stack.core.security.SecurityPolicy;
import org.eclipse.milo.opcua.stack.core.security.UserTokenSecurityPolicyRules;
import org.eclipse.milo.opcua.stack.core.types.builtin.ExpandedNodeId;
import org.eclipse.milo.opcua.stack.core.types.builtin.LocalizedText;
import org.eclipse.milo.opcua.stack.core.types.builtin.NodeId;
import org.eclipse.milo.opcua.stack.core.types.builtin.StatusCode;
import org.eclipse.milo.opcua.stack.core.types.enumerated.ApplicationType;
import org.eclipse.milo.opcua.stack.core.types.enumerated.MessageSecurityMode;
import org.eclipse.milo.opcua.stack.core.types.enumerated.UserTokenType;
import org.eclipse.milo.opcua.stack.core.types.structured.ApplicationDescription;
import org.eclipse.milo.opcua.stack.core.types.structured.EndpointDescription;
import org.eclipse.milo.opcua.stack.core.types.structured.UserTokenPolicy;
import org.eclipse.milo.opcua.stack.core.util.validation.ValidationCheck;
import org.eclipse.milo.opcua.stack.transport.client.tcp.OpcTcpClientTransport;
import org.eclipse.milo.opcua.stack.transport.client.tcp.OpcTcpClientTransportConfig;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * One successful directory registration per startup, with recoverable failures retried in
 * isolation.
 */
public final class GdsRegistrationService implements AutoCloseable {
  private static final Logger LOGGER = LoggerFactory.getLogger(GdsRegistrationService.class);
  private final OpcUaServer server;
  private final GdsRegistrationConfig config;
  private final GdsRegistrationStore store;
  private final CompletableFuture<@Nullable Void> completion = new CompletableFuture<>();
  private final Object lock = new Object();
  private @Nullable ScheduledExecutorService executor;
  private @Nullable Attempt active;
  private boolean stopped;
  private @Nullable String lastFailure;
  private boolean warnedLoopback;

  /**
   * Creates an inert service. Start it only after the server has bound its endpoints.
   *
   * @param server the running demo server.
   * @param config the validated registration settings.
   * @param dataDirectory the demo's persistent data directory.
   */
  public GdsRegistrationService(
      OpcUaServer server, GdsRegistrationConfig config, Path dataDirectory) {
    this.server = server;
    this.config = config;
    store = new GdsRegistrationStore(dataDirectory.resolve("gds/registration.json"));
  }

  /** Starts the owned worker after endpoint binding. */
  public void start() {
    synchronized (lock) {
      if (stopped || executor != null)
        throw new IllegalStateException("Registration worker already started or stopped");
      executor =
          Executors.newSingleThreadScheduledExecutor(
              Thread.ofPlatform().daemon().name("gds-registration").factory());
      executor.execute(this::runAttempt);
    }
  }

  CompletableFuture<@Nullable Void> completion() {
    return completion;
  }

  private void runAttempt() {
    Attempt attempt;
    synchronized (lock) {
      if (stopped) return;
      attempt = new Attempt();
      active = attempt;
    }
    Exception failure = null;
    try {
      register(attempt);
    } catch (Exception e) {
      failure = e;
    } finally {
      attempt.cleanup();
      synchronized (lock) {
        active = null;
      }
    }
    synchronized (lock) {
      if (stopped) return;
      if (failure == null) {
        if (lastFailure != null) LOGGER.info("GDS registration recovered");
        completion.complete(null);
        return;
      }
      boolean retry = retryable(failure);
      String detail = diagnostic(failure);
      String action =
          retry ? "will retry after retry-interval" : "operator action and restart required";
      if (!detail.equals(lastFailure))
        LOGGER.warn("GDS registration failed: {}; {}", detail, action);
      else LOGGER.debug("GDS registration failed: {}; {}", detail, action);
      lastFailure = detail;
      if (!retry) completion.completeExceptionally(failure);
      if (retry)
        Objects.requireNonNull(executor)
            .schedule(this::runAttempt, config.retryIntervalMillis(), TimeUnit.MILLISECONDS);
    }
  }

  // Kept separate from scheduling: every network wait consumes the same deadline, and this method
  // has no async callbacks that can write state or create another attempt after cancellation.
  private void register(Attempt attempt) throws Exception {
    ApplicationRecordDataType desired = desiredRecord(server, config);
    if (!warnedLoopback
        && config.discoveryUrls().isEmpty()
        && Arrays.stream(Objects.requireNonNull(desired.getDiscoveryUrls()))
            .allMatch(GdsRegistrationService::loopback)) {
      LOGGER.warn(
          "GDS discovery URLs contain only loopback addresses; configure gds.registration.discovery-url-list for remote discovery");
      warnedLoopback = true;
    }
    EndpointDescription endpoint = attempt.discover();
    OpcUaClient client = createClient(endpoint);
    attempt.connect(client);
    // Resolve explicitly with a bounded wait so GdsClient.create cannot perform its blocking
    // NamespaceArray fallback outside the attempt deadline.
    if (client.getNamespaceTable().getIndex(GdsClient.NAMESPACE_URI) == null) {
      attempt.await(client.readNamespaceTableAsync());
      if (client.getNamespaceTable().getIndex(GdsClient.NAMESPACE_URI) == null) {
        throw new RegistrationException("GDS Directory namespace is unavailable");
      }
    }
    GdsClient gds = GdsClient.create(client);
    String gdsUri = endpoint.getServer().getApplicationUri();
    if (gdsUri == null || gdsUri.isBlank())
      throw new RegistrationException("Invalid GDS ApplicationUri");
    String applicationUri = Objects.requireNonNull(desired.getApplicationUri());
    store.load(config.endpointUrl(), gdsUri, applicationUri);
    ApplicationRecordDataType[] records = attempt.await(gds.findApplicationsAsync(applicationUri));
    NodeId id;
    if (records.length == 0) {
      try {
        id = attempt.await(gds.registerApplicationAsync(desired));
      } catch (Exception e) {
        if (status(e) != StatusCodes.Bad_EntryExists) throw e;
        records = attempt.await(gds.findApplicationsAsync(applicationUri));
        id = reuse(attempt, gds, desired, records);
      }
    } else {
      id = reuse(attempt, gds, desired, records);
    }
    requireId(id);
    ExpandedNodeId portableId =
        id.expanded()
            .absolute(client.getNamespaceTable())
            .orElseThrow(() -> new RegistrationException("ApplicationId namespace is unavailable"));
    synchronized (lock) {
      attempt.checkRunning();
      try {
        store.save(
            new GdsRegistrationStore.State(
                config.endpointUrl(), gdsUri, applicationUri, portableId, Instant.now()));
      } catch (IOException e) {
        throw new IOException("Remote registration succeeded but local persistence failed");
      }
      LOGGER.info(
          "GDS registration confirmed: ApplicationUri={}, ApplicationId={}",
          applicationUri,
          portableId.toParseableString());
    }
  }

  private NodeId reuse(
      Attempt attempt,
      GdsClient gds,
      ApplicationRecordDataType desired,
      @Nullable ApplicationRecordDataType[] records)
      throws Exception {
    if (records.length != 1)
      throw new RegistrationException("Expected one application record; found " + records.length);
    ApplicationRecordDataType existing = records[0];
    if (existing == null) throw new RegistrationException("Invalid application record");
    requireId(existing.getApplicationId());
    List<String> differences = differences(desired, existing);
    if (!differences.isEmpty()) {
      if (!config.updateExisting())
        throw new RegistrationException(
            "Metadata differs: " + String.join(", ", differences) + "; review update-existing");
      LOGGER.info("Updating GDS metadata fields: {}", String.join(", ", differences));
      attempt.await(gds.updateApplicationAsync(withId(desired, existing.getApplicationId())));
    }
    return existing.getApplicationId();
  }

  static ApplicationRecordDataType desiredRecord(OpcUaServer server, GdsRegistrationConfig config)
      throws RegistrationException {
    List<EndpointDescription> endpoints = server.getApplicationContext().getEndpointDescriptions();
    if (endpoints.isEmpty()) throw new RegistrationException("No bound discovery URLs");
    ApplicationDescription application = endpoints.getFirst().getServer();
    List<String> urls = config.discoveryUrls();
    if (urls.isEmpty()) {
      String[] advertised = application.getDiscoveryUrls();
      urls =
          advertised == null
              ? List.of()
              : List.copyOf(new LinkedHashSet<>(Arrays.asList(advertised)));
    }
    if (urls.isEmpty()) throw new RegistrationException("Empty effective DiscoveryUrls");
    for (String url : urls)
      GdsRegistrationConfig.validateUrl(url, "gds.registration.discovery-url-list");
    return new ApplicationRecordDataType(
        NodeId.NULL_VALUE,
        application.getApplicationUri(),
        ApplicationType.Server,
        new LocalizedText[] {application.getApplicationName()},
        application.getProductUri(),
        urls.toArray(String[]::new),
        DemoServerCapabilities.VALUES.toArray(String[]::new));
  }

  static List<String> differences(
      ApplicationRecordDataType desired, ApplicationRecordDataType existing)
      throws RegistrationException {
    var identity = new ArrayList<String>();
    if (!Objects.equals(desired.getApplicationUri(), existing.getApplicationUri()))
      identity.add("ApplicationUri");
    if (desired.getApplicationType() != existing.getApplicationType())
      identity.add("ApplicationType");
    if (!Objects.equals(desired.getProductUri(), existing.getProductUri()))
      identity.add("ProductUri");
    if (!identity.isEmpty())
      throw new RegistrationException(
          "Conflicting identity fields: " + String.join(", ", identity));
    var fields = new ArrayList<String>();
    if (!set(desired.getApplicationNames()).equals(set(existing.getApplicationNames())))
      fields.add("ApplicationNames");
    if (!set(desired.getDiscoveryUrls()).equals(set(existing.getDiscoveryUrls())))
      fields.add("DiscoveryUrls");
    if (!set(desired.getServerCapabilities()).equals(set(existing.getServerCapabilities())))
      fields.add("ServerCapabilities");
    return fields;
  }

  private static <T> Set<T> set(T @Nullable [] values) {
    return values == null ? Set.of() : new HashSet<>(Arrays.asList(values));
  }

  static ApplicationRecordDataType withId(ApplicationRecordDataType record, NodeId id) {
    return new ApplicationRecordDataType(
        id,
        record.getApplicationUri(),
        record.getApplicationType(),
        record.getApplicationNames(),
        record.getProductUri(),
        record.getDiscoveryUrls(),
        record.getServerCapabilities());
  }

  private static void requireId(@Nullable NodeId id) throws RegistrationException {
    if (id == null || id.isNull()) throw new RegistrationException("Invalid empty ApplicationId");
  }

  private static boolean loopback(String url) {
    String host = URI.create(url).getHost();
    return host != null
        && (host.equalsIgnoreCase("localhost")
            || host.startsWith("127.")
            || host.equals("[::1]")
            || host.equals("[0:0:0:0:0:0:0:1]"));
  }

  OpcUaClient createClient(EndpointDescription endpoint) throws UaException {
    CertificateGroup group =
        server.getConfig().getCertificateManager().getDefaultApplicationGroup().orElseThrow();
    var validator =
        new DefaultClientCertificateValidator(
            group.getTrustListManager(),
            ValidationCheck.ALL_OPTIONAL_CHECKS,
            group.getCertificateQuarantine());
    var clientConfig =
        OpcUaClientConfig.builder()
            .setApplicationUri(server.getConfig().getApplicationUri())
            .setApplicationName(server.getConfig().getApplicationName())
            .setProductUri(server.getConfig().getProductUri())
            .setEndpoint(endpoint)
            .setCertificateGroup(group)
            .setCertificateValidator(validator)
            .setIdentityProvider(identityProvider(endpoint, validator))
            .setRequestTimeout(uint(config.requestTimeoutMillis()))
            .build();
    return OpcUaClient.create(
        clientConfig,
        b ->
            // Netty's socket connect timeout uses a signed int; UA request timeouts use UInt32.
            b.setConnectTimeout(uint(Math.min(config.requestTimeoutMillis(), Integer.MAX_VALUE)))
                .setAcknowledgeTimeout(uint(config.requestTimeoutMillis())));
  }

  private IdentityProvider identityProvider(
      EndpointDescription endpoint, DefaultClientCertificateValidator validator) {
    return switch (config.identity()) {
      case GdsRegistrationConfig.Anonymous _ -> AnonymousProvider.INSTANCE;
      case GdsRegistrationConfig.Credentials credentials ->
          new UsernameProvider(
              credentials.username(),
              credentials.password(),
              validator,
              policies ->
                  policies.stream()
                      .filter(p -> compatibleToken(p, endpoint, UserTokenType.UserName))
                      .findFirst()
                      .orElseThrow());
    };
  }

  static UserTokenType tokenType(GdsRegistrationConfig.Identity identity) {
    return switch (identity) {
      case GdsRegistrationConfig.Anonymous _ -> UserTokenType.Anonymous;
      case GdsRegistrationConfig.Credentials _ -> UserTokenType.UserName;
    };
  }

  static boolean compatibleToken(
      UserTokenPolicy token, EndpointDescription endpoint, UserTokenType tokenType) {
    if (token.getTokenType() != tokenType) return false;
    // Anonymous tokens carry no secret, so no token security policy applies.
    if (tokenType == UserTokenType.Anonymous) return true;
    String uri = token.getSecurityPolicyUri();
    boolean explicit = uri != null && !uri.isEmpty();
    try {
      SecurityPolicy policy =
          SecurityPolicy.fromUri(explicit ? uri : endpoint.getSecurityPolicyUri());
      UserTokenSecurityPolicyRules.requireSecuredChannelForEnhancedSecret(endpoint, policy);
      UserTokenSecurityPolicyRules.requireSamePublicKeyAlgorithmAsChannel(
          endpoint, policy, explicit);
      return true;
    } catch (UaException e) {
      return false;
    }
  }

  static EndpointDescription selectEndpoint(
      List<EndpointDescription> endpoints, SecurityPolicy policy, UserTokenType tokenType)
      throws RegistrationException {
    return endpoints.stream()
        .filter(
            e ->
                policy.getUri().equals(e.getSecurityPolicyUri())
                    && e.getSecurityMode() == MessageSecurityMode.SignAndEncrypt
                    && Stack.TCP_UASC_UABINARY_TRANSPORT_URI.equals(e.getTransportProfileUri())
                    && e.getUserIdentityTokens() != null
                    && Arrays.stream(e.getUserIdentityTokens())
                        .anyMatch(t -> compatibleToken(t, e, tokenType)))
        .findFirst()
        .orElseThrow(
            () ->
                new RegistrationException(
                    "No endpoint supports configured policy, SignAndEncrypt, UA TCP binary, and "
                        + (tokenType == UserTokenType.Anonymous ? "anonymous" : "username")
                        + " authentication"));
  }

  static boolean retryable(Throwable failure) {
    for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
      if (cause instanceof RegistrationException || cause instanceof IllegalArgumentException)
        return false;
    }
    long code = status(failure);
    if (code != 0 && code != StatusCodes.Bad_UnexpectedError) {
      return code == StatusCodes.Bad_Timeout
          || code == StatusCodes.Bad_RequestTimeout
          || code == StatusCodes.Bad_CommunicationError
          || code == StatusCodes.Bad_ConnectionClosed
          || code == StatusCodes.Bad_ConnectionRejected
          || code == StatusCodes.Bad_OutOfService
          || code == StatusCodes.Bad_SecureChannelIdInvalid
          || code == StatusCodes.Bad_TcpSecureChannelUnknown
          || code == StatusCodes.Bad_SessionIdInvalid
          || code == StatusCodes.Bad_ServerNotConnected
          || code == StatusCodes.Bad_NoCommunication
          || code == StatusCodes.Bad_ServerHalted
          || code == StatusCodes.Bad_Shutdown
          || code == StatusCodes.Bad_ResourceUnavailable
          || code == StatusCodes.Bad_ServerTooBusy
          || code == StatusCodes.Bad_TooManySessions
          || code == StatusCodes.Bad_TcpServerTooBusy
          || code == StatusCodes.Bad_SecureChannelClosed
          || code == StatusCodes.Bad_SessionClosed
          || (code >= StatusCodes.Bad_CertificateInvalid
              && code <= StatusCodes.Bad_CertificateIssuerRevoked);
    }
    for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
      if (cause instanceof IOException
          || cause instanceof TimeoutException
          || cause instanceof InterruptedException) return true;
    }
    return false;
  }

  private static long status(Throwable failure) {
    for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
      if (cause instanceof UaException ua) return ua.getStatusCode().value();
    }
    return 0;
  }

  private static String diagnostic(Exception failure) {
    if (failure instanceof RegistrationException) return failure.getMessage();
    if (failure instanceof IOException
        && "Remote registration succeeded but local persistence failed"
            .equals(failure.getMessage())) return failure.getMessage();
    long code = status(failure);
    return code == 0 ? failure.getClass().getSimpleName() : new StatusCode(code).toString();
  }

  /** Stops retries and disconnects clients, waiting at most five seconds for the owned worker. */
  @Override
  public void close() {
    ScheduledExecutorService worker;
    synchronized (lock) {
      stopped = true;
      completion.cancel(false);
      if (active != null) active.disconnect(true);
      worker = executor;
      if (worker != null) worker.shutdownNow();
    }
    if (worker != null) {
      try {
        if (!worker.awaitTermination(5, TimeUnit.SECONDS)) {
          LOGGER.warn("GDS registration worker did not stop within five seconds");
        }
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
    }
  }

  static final class RegistrationException extends Exception {
    RegistrationException(String message) {
      super(message);
    }
  }

  private final class Attempt {
    private final long started = System.nanoTime();
    private @Nullable OpcUaClient client;
    private @Nullable DiscoveryClient discovery;
    private CompletableFuture<?> disconnected = CompletableFuture.completedFuture(null);

    void checkRunning() throws InterruptedException, TimeoutException {
      if (stopped || Thread.currentThread().isInterrupted()) throw new InterruptedException();
      if (remainingMillis() <= 0) throw new TimeoutException();
    }

    long remainingMillis() {
      return config.attemptTimeoutMillis()
          - TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);
    }

    <T> T await(CompletableFuture<T> future) throws Exception {
      synchronized (lock) {
        checkRunning();
      }
      try {
        return future.get(
            Math.clamp(remainingMillis(), 1, config.requestTimeoutMillis()), TimeUnit.MILLISECONDS);
      } catch (ExecutionException e) {
        if (e.getCause() instanceof Exception exception) throw exception;
        throw e;
      }
    }

    EndpointDescription discover() throws Exception {
      var endpoint =
          new EndpointDescription(
              config.endpointUrl(),
              null,
              null,
              MessageSecurityMode.None,
              SecurityPolicy.None.getUri(),
              null,
              Stack.TCP_UASC_UABINARY_TRANSPORT_URI,
              ubyte(0));
      var transport =
          new OpcTcpClientTransport(
              OpcTcpClientTransportConfig.newBuilder()
                  .setConnectTimeout(
                      uint(Math.min(config.requestTimeoutMillis(), Integer.MAX_VALUE)))
                  .setAcknowledgeTimeout(uint(config.requestTimeoutMillis()))
                  .build());
      var candidate = new DiscoveryClient(endpoint, transport);
      CompletableFuture<DiscoveryClient> connected;
      synchronized (lock) {
        checkRunning();
        discovery = candidate;
        connected = candidate.connectAsync();
      }
      await(connected);
      var response =
          await(
              candidate.getEndpoints(
                  config.endpointUrl(),
                  new String[0],
                  new String[] {Stack.TCP_UASC_UABINARY_TRANSPORT_URI}));
      await(candidate.disconnectAsync());
      EndpointDescription[] endpoints = response.getEndpoints();
      return selectEndpoint(
          endpoints == null ? List.of() : Arrays.asList(endpoints),
          config.securityPolicy(),
          tokenType(config.identity()));
    }

    void connect(OpcUaClient candidate) throws Exception {
      CompletableFuture<OpcUaClient> connected;
      synchronized (lock) {
        // Publish before checking cancellation so even a newly constructed client is closed.
        client = candidate;
        checkRunning();
        connected = candidate.connectAsync();
      }
      await(connected);
    }

    void disconnect(boolean forceTransport) {
      CompletableFuture<?> discoveryClosed =
          discovery == null ? CompletableFuture.completedFuture(null) : discovery.disconnectAsync();
      CompletableFuture<?> clientClosed = CompletableFuture.completedFuture(null);
      if (client != null) {
        clientClosed = client.disconnectAsync();
        // Cancellation cannot wait for a session still being created or activated. Ordinary
        // cleanup lets CloseSession reach the GDS before disconnectAsync closes the transport.
        if (forceTransport) client.getTransport().disconnect();
      }
      disconnected = CompletableFuture.allOf(discoveryClosed, clientClosed);
    }

    void cleanup() {
      synchronized (lock) {
        disconnect(stopped || Thread.currentThread().isInterrupted() || remainingMillis() <= 0);
      }
      try {
        disconnected.get(Math.max(1, remainingMillis()), TimeUnit.MILLISECONDS);
      } catch (Exception ignored) {
        synchronized (lock) {
          // Bound graceful cleanup by the same attempt deadline and unblock pending session work.
          if (client != null) client.getTransport().disconnect();
        }
      }
    }
  }
}

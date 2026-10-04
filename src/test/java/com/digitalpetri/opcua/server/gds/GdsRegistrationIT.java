package com.digitalpetri.opcua.server.gds;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.digitalpetri.opcua.server.OpcUaDemoServer;
import com.digitalpetri.opcua.server.OpcUaTestClient;
import com.digitalpetri.opcua.server.OpcUaTestServerBuilder;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import com.typesafe.config.ConfigRenderOptions;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import org.eclipse.milo.opcua.sdk.client.DiscoveryClient;
import org.eclipse.milo.opcua.sdk.client.OpcUaClient;
import org.eclipse.milo.opcua.sdk.client.gds.GdsClient;
import org.eclipse.milo.opcua.sdk.client.gds.testing.FakeGdsNamespace;
import org.eclipse.milo.opcua.sdk.client.identity.UsernameProvider;
import org.eclipse.milo.opcua.sdk.server.Session;
import org.eclipse.milo.opcua.sdk.server.SessionListener;
import org.eclipse.milo.opcua.sdk.server.methods.MethodInvocationHandler;
import org.eclipse.milo.opcua.sdk.server.nodes.UaMethodNode;
import org.eclipse.milo.opcua.sdk.server.servicesets.impl.DefaultSessionServiceSet;
import org.eclipse.milo.opcua.stack.core.NodeIds;
import org.eclipse.milo.opcua.stack.core.StatusCodes;
import org.eclipse.milo.opcua.stack.core.UaException;
import org.eclipse.milo.opcua.stack.core.gds.GdsNodeIds;
import org.eclipse.milo.opcua.stack.core.gds.types.ApplicationRecordDataType;
import org.eclipse.milo.opcua.stack.core.security.CertificateGroup;
import org.eclipse.milo.opcua.stack.core.security.CertificateValidator;
import org.eclipse.milo.opcua.stack.core.security.SecurityPolicy;
import org.eclipse.milo.opcua.stack.core.types.builtin.ByteString;
import org.eclipse.milo.opcua.stack.core.types.builtin.NodeId;
import org.eclipse.milo.opcua.stack.core.types.builtin.StatusCode;
import org.eclipse.milo.opcua.stack.core.types.builtin.Variant;
import org.eclipse.milo.opcua.stack.core.types.enumerated.MessageSecurityMode;
import org.eclipse.milo.opcua.stack.core.types.structured.CallMethodRequest;
import org.eclipse.milo.opcua.stack.core.types.structured.CallMethodResult;
import org.eclipse.milo.opcua.stack.core.types.structured.CreateSessionRequest;
import org.eclipse.milo.opcua.stack.core.types.structured.CreateSessionResponse;
import org.eclipse.milo.opcua.stack.core.types.structured.EndpointDescription;
import org.eclipse.milo.opcua.stack.core.util.CertificateUtil;
import org.eclipse.milo.opcua.stack.core.util.SelfSignedCertificateBuilder;
import org.eclipse.milo.opcua.stack.transport.server.ServiceRequestContext;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GdsRegistrationIT {
  @TempDir Path directory;
  private OpcUaDemoServer gdsServer;
  private OpcUaDemoServer demo;
  private FakeGdsNamespace namespace;
  private final List<GdsRegistrationService> services = new ArrayList<>();
  private final List<OpcUaClient> clients = new ArrayList<>();
  private String gdsDirectory = "gds";
  private int gdsPort;
  private String endpointUrl;

  private static Config secure() {
    return ConfigFactory.parseMap(
        Map.of(
            "security-policy-list", List.of("None", "Basic256Sha256"),
            "security-mode-list", List.of("None", "SignAndEncrypt"),
            "endpoint-address-list", List.of("localhost"),
            "certificate-hostname-list", List.of("localhost")));
  }

  @BeforeEach
  void startServers() throws Exception {
    try (var socket = new ServerSocket(0)) {
      gdsPort = socket.getLocalPort();
    }
    startGds(3);
    demo =
        OpcUaTestServerBuilder.builder()
            .withDataDir(directory.resolve("demo"))
            .withConfig(secure().withFallback(ConfigFactory.parseString("gds-push-enabled=true")))
            .build();
    demo.startup();
    trustBoth();
  }

  private void startGds(int namespaceIndex) throws Exception {
    gdsServer =
        OpcUaTestServerBuilder.builder()
            .withDataDir(directory.resolve(gdsDirectory))
            .withPort(gdsPort)
            .withConfig(
                secure().withFallback(ConfigFactory.parseString("trust-all-certificates=false")))
            .build();
    for (int i = 3; i < namespaceIndex; i++) {
      gdsServer.getServer().getNamespaceTable().set(i, "urn:test:padding:" + i);
    }
    gdsServer.getServer().getNamespaceTable().set(namespaceIndex, GdsClient.NAMESPACE_URI);
    namespace = new FakeGdsNamespace(gdsServer.getServer());
    namespace.setRegisterApplicationAccess(FakeGdsNamespace.MethodAccess.CREDENTIALED);
    gdsServer.getServer().addLifecycleParticipant(namespace);
    gdsServer.startup();
    String advertisedUrl = secureEndpoint(gdsServer).getEndpointUrl();
    assertNotNull(advertisedUrl);
    endpointUrl = advertisedUrl;
  }

  @AfterEach
  void stopServers() throws Exception {
    services.forEach(GdsRegistrationService::close);
    for (OpcUaClient client : clients) client.disconnectAsync().get(5, TimeUnit.SECONDS);
    if (demo != null) demo.shutdown();
    if (gdsServer != null) gdsServer.shutdown();
  }

  private static CertificateGroup group(OpcUaDemoServer server) {
    return server
        .getServer()
        .getConfig()
        .getCertificateManager()
        .getDefaultApplicationGroup()
        .orElseThrow();
  }

  private static X509Certificate certificate(OpcUaDemoServer server) {
    return group(server)
        .getCertificateChain(NodeIds.RsaSha256ApplicationCertificateType)
        .orElseThrow()[0];
  }

  private void trustBoth() {
    group(demo).getTrustListManager().addTrustedCertificate(certificate(gdsServer));
    group(gdsServer).getTrustListManager().addTrustedCertificate(certificate(demo));
  }

  private static EndpointDescription secureEndpoint(OpcUaDemoServer server) {
    return server.getServer().getApplicationContext().getEndpointDescriptions().stream()
        .filter(e -> e.getSecurityMode() == MessageSecurityMode.SignAndEncrypt)
        .findFirst()
        .orElseThrow();
  }

  private Config settings() {
    return ConfigFactory.parseMap(
        Map.of(
            "gds.registration.enabled",
            true,
            "gds.registration.endpoint-url",
            endpointUrl,
            "gds.registration.identity.username",
            "SecurityAdmin",
            "gds.registration.identity.password",
            "password",
            "gds.registration.request-timeout",
            "2 seconds",
            "gds.registration.attempt-timeout",
            "6 seconds",
            "gds.registration.retry-interval",
            "100ms"));
  }

  private GdsRegistrationConfig registrationConfig(Config overrides) {
    return GdsRegistrationConfig.fromConfig(overrides.withFallback(settings())).orElseThrow();
  }

  private GdsRegistrationService service(Config overrides) {
    var service =
        new GdsRegistrationService(
            demo.getServer(), registrationConfig(overrides), directory.resolve("demo"));
    services.add(service);
    return service;
  }

  private void register(Config overrides) throws Exception {
    GdsRegistrationService service = service(overrides);
    service.start();
    service.completion().get(20, TimeUnit.SECONDS);
  }

  private GdsClient inspector() throws Exception {
    OpcUaClient client = service(ConfigFactory.empty()).createClient(secureEndpoint(gdsServer));
    clients.add(client);
    client.connectAsync().get(5, TimeUnit.SECONDS);
    return GdsClient.create(client);
  }

  private ApplicationRecordDataType desired() throws Exception {
    return GdsRegistrationService.desiredRecord(
        demo.getServer(), registrationConfig(ConfigFactory.empty()));
  }

  private Path stateFile() {
    return directory.resolve("demo/gds/registration.json");
  }

  // Registration must release the GDS session, rather than consuming capacity until it expires.
  @Test
  void successfulAttemptClosesGdsSession() throws Exception {
    register(ConfigFactory.empty());
    assertEquals(1, namespace.getRegisterApplicationCallCount());
    assertTrue(gdsServer.getServer().getSessionManager().getAllSessions().isEmpty());
  }

  // The supported UInt32 request timeout must not overflow Netty's signed socket connect timeout.
  @Test
  void largestRequestTimeoutStillRegisters() throws Exception {
    register(
        ConfigFactory.parseString(
            """
            gds.registration {
              request-timeout = 4294967295ms
              attempt-timeout = 4294967295ms
            }
            """));
    assertEquals(1, namespace.getRegisterApplicationCallCount());
    assertTrue(Files.isRegularFile(stateFile()));
  }

  @Test
  void registersRunningIdentityAndReachableUrlsThenReusesOnRestart() throws Exception {
    register(ConfigFactory.empty());
    GdsClient gds = inspector();
    ApplicationRecordDataType[] records =
        gds.findApplications(demo.getServer().getConfig().getApplicationUri());
    assertEquals(1, records.length);
    assertTrue(GdsRegistrationService.differences(desired(), records[0]).isEmpty());
    String[] discoveryUrls = records[0].getDiscoveryUrls();
    assertNotNull(discoveryUrls);
    String url = discoveryUrls[0];
    assertFalse(DiscoveryClient.getEndpoints(url).get(5, TimeUnit.SECONDS).isEmpty());
    String id = ConfigFactory.parseFile(stateFile().toFile()).getString("applicationId");
    assertTrue(id.contains("nsu="));
    int demoPort = demo.getServer().getConfig().getEndpoints().iterator().next().getBindPort();
    String applicationUri = demo.getServer().getConfig().getApplicationUri();
    demo.shutdown();
    demo =
        OpcUaTestServerBuilder.builder()
            .withDataDir(directory.resolve("demo"))
            .withPort(demoPort)
            .withConfig(secure())
            .build();
    demo.startup();
    assertEquals(applicationUri, demo.getServer().getConfig().getApplicationUri());
    register(ConfigFactory.empty());
    assertEquals(1, namespace.getRegisterApplicationCallCount());
    assertEquals(id, ConfigFactory.parseFile(stateFile().toFile()).getString("applicationId"));
    Files.writeString(stateFile(), "broken JSON");
    register(ConfigFactory.empty());
    assertEquals(
        1,
        namespace.getRegisterApplicationCallCount(),
        "malformed local state must not cause another write");
  }

  @Test
  void updatesOnlyWithExplicitPermission() throws Exception {
    register(ConfigFactory.empty());
    Config changed =
        ConfigFactory.parseMap(
            Map.of(
                "gds.registration.discovery-url-list",
                List.of("opc.tcp://public.example:4840/milo")));
    GdsRegistrationService refused = service(changed);
    refused.start();
    ExecutionException error =
        assertThrows(
            ExecutionException.class, () -> refused.completion().get(10, TimeUnit.SECONDS));
    assertTrue(error.getCause().getMessage().contains("DiscoveryUrls"));
    assertArrayEquals(
        desired().getDiscoveryUrls(),
        inspector()
            .findApplications(demo.getServer().getConfig().getApplicationUri())[0]
            .getDiscoveryUrls());
    register(
        ConfigFactory.parseString("gds.registration.update-existing=true").withFallback(changed));
    assertArrayEquals(
        new String[] {"opc.tcp://public.example:4840/milo"},
        inspector()
            .findApplications(demo.getServer().getConfig().getApplicationUri())[0]
            .getDiscoveryUrls());
    assertEquals(1, namespace.getRegisterApplicationCallCount());
  }

  @Test
  void duplicateRecordsAndIdentityConflictsStopWithoutWrites() throws Exception {
    GdsClient gds = inspector();
    ApplicationRecordDataType desired = desired();
    var conflict =
        new ApplicationRecordDataType(
            NodeId.NULL_VALUE,
            desired.getApplicationUri(),
            desired.getApplicationType(),
            desired.getApplicationNames(),
            "urn:wrong-product",
            desired.getDiscoveryUrls(),
            desired.getServerCapabilities());
    NodeId conflictId = gds.registerApplication(conflict);
    GdsRegistrationService refused =
        service(ConfigFactory.parseString("gds.registration.update-existing=true"));
    refused.start();
    assertThrows(ExecutionException.class, () -> refused.completion().get(10, TimeUnit.SECONDS));
    assertEquals("urn:wrong-product", gds.getApplication(conflictId).getProductUri());
    gds.registerApplication(desired);
    GdsRegistrationService ambiguous = service(ConfigFactory.empty());
    ambiguous.start();
    ExecutionException error =
        assertThrows(
            ExecutionException.class, () -> ambiguous.completion().get(10, TimeUnit.SECONDS));
    assertTrue(error.getCause().getMessage().contains("found 2"));
    assertEquals(2, namespace.getRegisterApplicationCallCount());
    assertFalse(Files.exists(stateFile()));
  }

  @Test
  void recoversLostWriteResponseByLookup() throws Exception {
    UaMethodNode method =
        method(
            GdsNodeIds.Directory_RegisterApplication.toNodeIdOrThrow(
                gdsServer.getServer().getNamespaceTable()));
    MethodInvocationHandler original = method.getInvocationHandler();
    var release = new CountDownLatch(1);
    method.setInvocationHandler(
        (context, request) -> {
          CallMethodResult result = original.invoke(context, request);
          try {
            release.await(10, TimeUnit.SECONDS);
          } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
          }
          return result;
        });
    try {
      register(ConfigFactory.empty());
      assertEquals(
          1, namespace.getRegisterApplicationCallCount(), "retry must discover the accepted write");
    } finally {
      release.countDown();
    }
  }

  @Test
  void entryExistsRaceRepeatsLookupWithinAttempt() throws Exception {
    UaMethodNode method =
        method(
            GdsNodeIds.Directory_RegisterApplication.toNodeIdOrThrow(
                gdsServer.getServer().getNamespaceTable()));
    MethodInvocationHandler original = method.getInvocationHandler();
    method.setInvocationHandler(
        (context, request) -> {
          original.invoke(context, request);
          return bad(StatusCodes.Bad_EntryExists);
        });
    register(ConfigFactory.empty());
    assertEquals(1, namespace.getRegisterApplicationCallCount());
  }

  @Test
  void persistenceFailureRetriesWithoutDuplicateRegistration() throws Exception {
    Files.createDirectories(stateFile());
    Files.writeString(stateFile().resolve("block"), "block atomic replacement");
    GdsRegistrationService service = service(ConfigFactory.empty());
    service.start();
    await(() -> namespace.getRegisterApplicationCallCount() == 1);
    // Synchronize through another lookup before repairing the local filesystem.
    assertEquals(
        1, inspector().findApplications(demo.getServer().getConfig().getApplicationUri()).length);
    Files.delete(stateFile().resolve("block"));
    Files.delete(stateFile());
    service.completion().get(15, TimeUnit.SECONDS);
    assertEquals(1, namespace.getRegisterApplicationCallCount());
    assertTrue(Files.isRegularFile(stateFile()));
  }

  @Test
  void untrustedGdsRetriesUntilApprovedWhileDemoRemainsUsable() throws Exception {
    group(demo)
        .getTrustListManager()
        .removeTrustedCertificate(CertificateUtil.thumbprint(certificate(gdsServer)));
    GdsRegistrationService service = service(ConfigFactory.empty());
    service.start();
    await(() -> !group(demo).getCertificateQuarantine().getRejectedCertificates().isEmpty());
    assertEquals(0, namespace.getRegisterApplicationCallCount());
    assertFalse(
        DiscoveryClient.getEndpoints(secureEndpoint(demo).getEndpointUrl())
            .get(5, TimeUnit.SECONDS)
            .isEmpty());
    group(demo).getTrustListManager().addTrustedCertificate(certificate(gdsServer));
    service.completion().get(15, TimeUnit.SECONDS);
    assertEquals(1, namespace.getRegisterApplicationCallCount());
  }

  @Test
  void deniedRegistrationAndInvalidCredentialsStopUntilRestart() throws Exception {
    namespace.setRegisterApplicationAccess(FakeGdsNamespace.MethodAccess.NOBODY);
    GdsRegistrationService denied = service(ConfigFactory.empty());
    denied.start();
    assertThrows(ExecutionException.class, () -> denied.completion().get(10, TimeUnit.SECONDS));
    assertEquals(1, namespace.getRegisterApplicationCallCount());
    namespace.setRegisterApplicationAccess(FakeGdsNamespace.MethodAccess.CREDENTIALED);
    GdsRegistrationService wrongPassword =
        service(ConfigFactory.parseString("gds.registration.identity.password=wrong"));
    wrongPassword.start();
    assertThrows(
        ExecutionException.class, () -> wrongPassword.completion().get(10, TimeUnit.SECONDS));
    assertEquals(1, namespace.getRegisterApplicationCallCount(), "no anonymous fallback");
    register(ConfigFactory.empty());
    assertEquals(2, namespace.getRegisterApplicationCallCount());
  }

  // Anonymous registration is explicit, keeps SignAndEncrypt, and needs a GDS that grants it.
  @Test
  void anonymousIdentityRegistersOnlyWhenGdsGrantsAnonymousSessions() throws Exception {
    Config anonymous = ConfigFactory.parseString("gds.registration.identity.type=anonymous");
    GdsRegistrationService denied = service(anonymous);
    denied.start();
    assertThrows(ExecutionException.class, () -> denied.completion().get(10, TimeUnit.SECONDS));
    assertFalse(Files.exists(stateFile()));
    namespace.setRegisterApplicationAccess(FakeGdsNamespace.MethodAccess.ANYONE);
    register(anonymous);
    assertTrue(Files.isRegularFile(stateFile()));
    assertTrue(gdsServer.getServer().getSessionManager().getAllSessions().isEmpty());
    ApplicationRecordDataType[] records =
        inspector().findApplications(demo.getServer().getConfig().getApplicationUri());
    assertEquals(1, records.length);
  }

  @Test
  void namespaceIndexChangeAndGdsIdentityChangeInvalidateRememberedState() throws Exception {
    register(ConfigFactory.empty());
    String oldId = ConfigFactory.parseFile(stateFile().toFile()).getString("applicationId");
    gdsServer.shutdown();
    startGds(5);
    trustBoth();
    GdsClient gds = inspector();
    gds.registerApplication(desired());
    register(ConfigFactory.empty());
    assertEquals(1, namespace.getRegisterApplicationCallCount());
    assertEquals(oldId, ConfigFactory.parseFile(stateFile().toFile()).getString("applicationId"));
    assertEquals(
        5,
        gds.findApplications(demo.getServer().getConfig().getApplicationUri())[0]
            .getApplicationId()
            .getNamespaceIndex()
            .intValue());
    // Change target URL and application identity, retaining the demo's remembered file.
    clients.forEach(OpcUaClient::disconnectAsync);
    clients.clear();
    gdsServer.shutdown();
    try (var socket = new ServerSocket(0)) {
      gdsPort = socket.getLocalPort();
    }
    gdsDirectory = "changed-gds";
    startGds(4);
    trustBoth();
    register(ConfigFactory.empty());
    assertEquals(
        endpointUrl, ConfigFactory.parseFile(stateFile().toFile()).getString("endpointUrl"));
    assertEquals(
        gdsServer.getServer().getConfig().getApplicationUri(),
        ConfigFactory.parseFile(stateFile().toFile()).getString("gdsApplicationUri"));
    assertEquals(1, namespace.getRegisterApplicationCallCount());
  }

  @Test
  void shutdownDuringDiscoveryClosesSocketAndPreventsRetry() throws Exception {
    try (var blackhole = new ServerSocket(0)) {
      var accepted =
          CompletableFuture.supplyAsync(
              () -> {
                try {
                  return blackhole.accept();
                } catch (Exception e) {
                  throw new RuntimeException(e);
                }
              });
      GdsRegistrationService service =
          service(
              ConfigFactory.parseMap(
                  Map.of(
                      "gds.registration.endpoint-url",
                      "opc.tcp://localhost:" + blackhole.getLocalPort())));
      service.start();
      try (Socket socket = accepted.get(5, TimeUnit.SECONDS)) {
        assertTimeoutPreemptively(Duration.ofSeconds(5), service::close);
        socket.setSoTimeout(5000);
        // Drain the Hello before EOF.
        socket.getInputStream().transferTo(OutputStream.nullOutputStream());
        blackhole.setSoTimeout(500);
        assertThrows(
            java.net.SocketTimeoutException.class,
            blackhole::accept,
            "closed worker must not reconnect");
      }
      assertFalse(Files.exists(stateFile()));
    }
  }

  @Test
  void totalAttemptDeadlineStopsSlowSequenceAndShutdownCancelsRetry() throws Exception {
    var calls = new AtomicInteger();
    UaMethodNode method =
        method(
            GdsNodeIds.Directory_FindApplications.toNodeIdOrThrow(
                gdsServer.getServer().getNamespaceTable()));
    MethodInvocationHandler original = method.getInvocationHandler();
    method.setInvocationHandler(
        (context, request) -> {
          calls.incrementAndGet();
          try {
            new CountDownLatch(1).await(800, TimeUnit.MILLISECONDS);
          } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
          }
          return original.invoke(context, request);
        });
    UaMethodNode register =
        method(
            GdsNodeIds.Directory_RegisterApplication.toNodeIdOrThrow(
                gdsServer.getServer().getNamespaceTable()));
    register.setInvocationHandler(
        (_, _) -> {
          try {
            new CountDownLatch(1).await(800, TimeUnit.MILLISECONDS);
          } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
          }
          return bad(StatusCodes.Bad_Timeout);
        });
    GdsRegistrationService service =
        service(
            ConfigFactory.parseString(
                """
        gds.registration { request-timeout=1s, attempt-timeout=1s, retry-interval=100ms }
        """));
    service.start();
    await(() -> calls.get() >= 2);
    assertFalse(service.completion().isDone());
    service.close();
    int stoppedAt = calls.get();
    new CountDownLatch(1).await(1200, TimeUnit.MILLISECONDS);
    assertEquals(stoppedAt, calls.get(), "no lookup or reconnect after shutdown");
    assertFalse(Files.exists(stateFile()));
  }

  @Test
  void startupBindingFailureNeverStartsRegistration() throws Exception {
    try (var occupied = new ServerSocket(0)) {
      var failed =
          OpcUaTestServerBuilder.builder()
              .withDataDir(directory.resolve("failed"))
              .withPort(occupied.getLocalPort())
              .withConfig(settings())
              .build();
      assertThrows(RuntimeException.class, failed::startup);
      assertEquals(0, namespace.getRegisterApplicationCallCount());
      assertFalse(Files.exists(directory.resolve("failed/gds")));
    }
  }

  @Test
  void outerLifecycleRegistersAfterBindingAndDisabledStartupCreatesNoState() throws Exception {
    assertFalse(Files.exists(stateFile()));
    demo.shutdown();
    demo =
        OpcUaTestServerBuilder.builder()
            .withDataDir(directory.resolve("demo"))
            .withConfig(secure().withFallback(settings()))
            .build();
    demo.startup();
    await(() -> Files.isRegularFile(stateFile()));
    ApplicationRecordDataType record =
        inspector().findApplications(demo.getServer().getConfig().getApplicationUri())[0];
    String[] discoveryUrls = record.getDiscoveryUrls();
    assertNotNull(discoveryUrls);
    assertFalse(DiscoveryClient.getEndpoints(discoveryUrls[0]).get(5, TimeUnit.SECONDS).isEmpty());
  }

  @Test
  void pushManagementStillWorksAndRetrySelectsReplacedCertificate() throws Exception {
    register(ConfigFactory.empty());
    X509Certificate old = certificate(demo);
    group(gdsServer)
        .getTrustListManager()
        .removeTrustedCertificate(CertificateUtil.thumbprint(old));
    GdsRegistrationService retrying = service(ConfigFactory.empty());
    retrying.start();
    await(() -> !group(gdsServer).getCertificateQuarantine().getRejectedCertificates().isEmpty());

    OpcUaClient admin =
        OpcUaTestClient.create(
            demo.getServer(),
            SecurityPolicy.Basic256Sha256,
            MessageSecurityMode.SignAndEncrypt,
            builder ->
                builder
                    .setApplicationUri(gdsServer.getServer().getConfig().getApplicationUri())
                    .setCertificateGroup(group(gdsServer))
                    .setCertificateValidator(
                        new CertificateValidator.InsecureCertificateValidator())
                    .setIdentityProvider(new UsernameProvider("SecurityAdmin", "password")));
    clients.add(admin);
    admin.connectAsync().get(5, TimeUnit.SECONDS);
    X509Certificate replacement =
        new SelfSignedCertificateBuilder(
                group(demo).getKeyPair(NodeIds.RsaSha256ApplicationCertificateType).orElseThrow())
            .setCommonName("Rotated demo certificate")
            .setApplicationUri(demo.getServer().getConfig().getApplicationUri())
            .addDnsName("localhost")
            .build();
    var observed = new CompletableFuture<X509Certificate>();
    gdsServer
        .getServer()
        .getSessionManager()
        .addSessionListener(
            new SessionListener() {
              @Override
              public void onSessionCreated(Session session) {
                X509Certificate presented =
                    session.getSecurityConfiguration().getClientCertificate();
                if (replacement.equals(presented)) observed.complete(presented);
              }
            });
    CallMethodResult[] updateResults =
        admin
            .callAsync(
                List.of(
                    new CallMethodRequest(
                        NodeIds.ServerConfiguration,
                        NodeIds.ServerConfiguration_UpdateCertificate,
                        new Variant[] {
                          new Variant(
                              NodeIds
                                  .ServerConfiguration_CertificateGroups_DefaultApplicationGroup),
                          new Variant(NodeIds.RsaSha256ApplicationCertificateType),
                          new Variant(ByteString.of(replacement.getEncoded())),
                          new Variant(new ByteString[0]),
                          new Variant(null),
                          new Variant(null)
                        })))
            .get(5, TimeUnit.SECONDS)
            .getResults();
    assertNotNull(updateResults);
    CallMethodResult updated = updateResults[0];
    assertTrue(updated.getStatusCode().isGood(), updated.toString());
    Variant[] outputArguments = updated.getOutputArguments();
    assertNotNull(outputArguments);
    assertEquals(Boolean.TRUE, outputArguments[0].getValue());
    assertEquals(old, certificate(demo), "push update is staged until ApplyChanges");
    CallMethodResult[] applyResults =
        admin
            .callAsync(
                List.of(
                    new CallMethodRequest(
                        NodeIds.ServerConfiguration,
                        NodeIds.ServerConfiguration_ApplyChanges,
                        new Variant[0])))
            .get(5, TimeUnit.SECONDS)
            .getResults();
    assertNotNull(applyResults);
    CallMethodResult applied = applyResults[0];
    assertTrue(applied.getStatusCode().isGood(), applied.toString());
    group(gdsServer).getTrustListManager().addTrustedCertificate(replacement);
    retrying.completion().get(20, TimeUnit.SECONDS);
    assertEquals(replacement, observed.get(5, TimeUnit.SECONDS));
    assertEquals(
        1,
        namespace.getRegisterApplicationCallCount(),
        "rotating a certificate must not create another directory record");
  }

  @Test
  void shutdownDuringSessionCreationPreventsLateRegistrationAndReconnect() throws Exception {
    var entered = new CompletableFuture<@Nullable Void>();
    var release = new CountDownLatch(1);
    var creations = new AtomicInteger();
    gdsServer
        .getServer()
        .addServiceSet(
            "/milo",
            new DefaultSessionServiceSet(gdsServer.getServer()) {
              @Override
              public CreateSessionResponse onCreateSession(
                  ServiceRequestContext context, CreateSessionRequest request) throws UaException {
                creations.incrementAndGet();
                CreateSessionResponse response = super.onCreateSession(context, request);
                entered.complete(null);
                try {
                  release.await(10, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                  Thread.currentThread().interrupt();
                }
                return response;
              }
            });
    GdsRegistrationService service = service(ConfigFactory.empty());
    service.start();
    try {
      entered.get(5, TimeUnit.SECONDS);
      assertTimeoutPreemptively(Duration.ofSeconds(5), service::close);
    } finally {
      release.countDown();
    }
    new CountDownLatch(1).await(2500, TimeUnit.MILLISECONDS);
    assertEquals(1, creations.get(), "cancelled session creation must not reconnect");
    assertEquals(0, namespace.getRegisterApplicationCallCount());
    assertFalse(Files.exists(stateFile()));
  }

  @Test
  void emptyApplicationIdAndUnsupportedMethodStopWithoutPersistence() throws Exception {
    UaMethodNode register =
        method(
            GdsNodeIds.Directory_RegisterApplication.toNodeIdOrThrow(
                gdsServer.getServer().getNamespaceTable()));
    register.setInvocationHandler(
        (_, _) ->
            new CallMethodResult(
                StatusCode.GOOD, null, null, new Variant[] {new Variant(NodeId.NULL_VALUE)}));
    GdsRegistrationService invalid = service(ConfigFactory.empty());
    invalid.start();
    ExecutionException error =
        assertThrows(
            ExecutionException.class, () -> invalid.completion().get(10, TimeUnit.SECONDS));
    assertTrue(error.getCause().getMessage().contains("ApplicationId"));
    assertFalse(Files.exists(stateFile()));
    register.setInvocationHandler(MethodInvocationHandler.NOT_IMPLEMENTED);
    GdsRegistrationService unsupported = service(ConfigFactory.empty());
    unsupported.start();
    assertThrows(
        ExecutionException.class, () -> unsupported.completion().get(10, TimeUnit.SECONDS));
    assertFalse(Files.exists(stateFile()));
  }

  @Test
  void unavailableGdsRetriesUntilStartedWhileDemoRemainsUsable() throws Exception {
    gdsServer.shutdown();
    GdsRegistrationService service = service(ConfigFactory.empty());
    service.start();
    assertThrows(
        java.util.concurrent.TimeoutException.class,
        () -> service.completion().get(500, TimeUnit.MILLISECONDS),
        "an unavailable GDS must keep retrying rather than stop permanently");
    assertFalse(
        DiscoveryClient.getEndpoints(secureEndpoint(demo).getEndpointUrl())
            .get(5, TimeUnit.SECONDS)
            .isEmpty());
    startGds(3);
    trustBoth();
    service.completion().get(15, TimeUnit.SECONDS);
    assertEquals(1, namespace.getRegisterApplicationCallCount());
  }

  @Test
  void nativeBinaryRegistersSecurely() throws Exception {
    String binary = System.getProperty("gds.native.binary");
    assumeTrue(binary != null, "native smoke test runs after native packaging");
    demo.shutdown();
    Config nativeConfig =
        secure()
            .withFallback(settings())
            .withFallback(
                ConfigFactory.parseMap(
                    Map.of(
                        "bind-port",
                            demo.getServer()
                                .getConfig()
                                .getEndpoints()
                                .iterator()
                                .next()
                                .getBindPort(),
                        "rate-limit-enabled", false,
                        "address-space.mass.enabled", false,
                        "address-space.ctt.enabled", false,
                        "address-space.data-type-test.enabled", false)));
    Files.writeString(
        directory.resolve("demo/server.conf"),
        nativeConfig.root().render(ConfigRenderOptions.defaults()));
    // The standalone main resolves data/ relative to its working directory.
    Path nativeWork = directory.resolve("native");
    Files.createDirectories(nativeWork);
    Files.createSymbolicLink(nativeWork.resolve("data"), directory.resolve("demo"));
    Process process =
        new ProcessBuilder(binary)
            .directory(nativeWork.toFile())
            .redirectErrorStream(true)
            .redirectOutput(directory.resolve("native.log").toFile())
            .start();
    try {
      await(() -> Files.isRegularFile(stateFile()) || !process.isAlive());
      assertTrue(Files.isRegularFile(stateFile()), () -> readLog(directory.resolve("native.log")));
      ApplicationRecordDataType record =
          inspector().findApplications(demo.getServer().getConfig().getApplicationUri())[0];
      String[] discoveryUrls = record.getDiscoveryUrls();
      assertNotNull(discoveryUrls);
      assertFalse(
          DiscoveryClient.getEndpoints(discoveryUrls[0]).get(5, TimeUnit.SECONDS).isEmpty());
      assertEquals(1, namespace.getRegisterApplicationCallCount());
    } finally {
      process.destroy();
      if (!process.waitFor(10, TimeUnit.SECONDS)) process.destroyForcibly().waitFor();
    }
  }

  private UaMethodNode method(NodeId id) {
    return (UaMethodNode)
        gdsServer.getServer().getAddressSpaceManager().getManagedNode(id).orElseThrow();
  }

  private static CallMethodResult bad(long code) {
    return new CallMethodResult(new StatusCode(code), null, null, new Variant[0]);
  }

  private static void await(BooleanSupplier condition) throws Exception {
    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20);
    while (!condition.getAsBoolean()) {
      if (System.nanoTime() >= deadline) fail("condition did not become true within 20 seconds");
      CompletableFuture.runAsync(
              () -> {}, CompletableFuture.delayedExecutor(25, TimeUnit.MILLISECONDS))
          .get(1, TimeUnit.SECONDS);
    }
  }

  private static String readLog(Path path) {
    try {
      return Files.readString(path);
    } catch (Exception e) {
      return "native process failed";
    }
  }
}

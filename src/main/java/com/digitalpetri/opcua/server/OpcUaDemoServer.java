package com.digitalpetri.opcua.server;

import static org.eclipse.milo.opcua.sdk.server.OpcUaServerConfig.USER_TOKEN_POLICY_ANONYMOUS;
import static org.eclipse.milo.opcua.sdk.server.OpcUaServerConfig.USER_TOKEN_POLICY_USERNAME;
import static org.eclipse.milo.opcua.sdk.server.OpcUaServerConfig.USER_TOKEN_POLICY_X509;

import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.joran.JoranConfigurator;
import ch.qos.logback.core.util.StatusPrinter2;
import com.digitalpetri.opcua.server.namespace.demo.DemoNamespace;
import com.digitalpetri.opcua.server.namespace.test.DataTypeTestNamespace;
import com.digitalpetri.opcua.server.objects.ServerConfigurationObject;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.Security;
import java.security.cert.X509Certificate;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TimeZone;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import java.util.function.Supplier;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.eclipse.milo.opcua.sdk.server.AbstractLifecycle;
import org.eclipse.milo.opcua.sdk.server.EndpointConfig;
import org.eclipse.milo.opcua.sdk.server.OpcUaServer;
import org.eclipse.milo.opcua.sdk.server.OpcUaServerConfig;
import org.eclipse.milo.opcua.sdk.server.RoleMapper;
import org.eclipse.milo.opcua.sdk.server.identity.AnonymousIdentityValidator;
import org.eclipse.milo.opcua.sdk.server.identity.CompositeValidator;
import org.eclipse.milo.opcua.sdk.server.identity.Identity;
import org.eclipse.milo.opcua.sdk.server.identity.Identity.AnonymousIdentity;
import org.eclipse.milo.opcua.sdk.server.identity.Identity.UsernameIdentity;
import org.eclipse.milo.opcua.sdk.server.identity.UsernameIdentityValidator;
import org.eclipse.milo.opcua.sdk.server.identity.X509IdentityValidator;
import org.eclipse.milo.opcua.sdk.server.model.objects.ServerConfigurationTypeNode;
import org.eclipse.milo.opcua.sdk.server.nodes.UaNode;
import org.eclipse.milo.opcua.sdk.server.util.HostnameUtil;
import org.eclipse.milo.opcua.stack.core.NodeIds;
import org.eclipse.milo.opcua.stack.core.Stack;
import org.eclipse.milo.opcua.stack.core.security.CertificateManager;
import org.eclipse.milo.opcua.stack.core.security.CertificateQuarantine;
import org.eclipse.milo.opcua.stack.core.security.CertificateValidator;
import org.eclipse.milo.opcua.stack.core.security.DefaultApplicationGroup;
import org.eclipse.milo.opcua.stack.core.security.DefaultCertificateManager;
import org.eclipse.milo.opcua.stack.core.security.DefaultServerCertificateValidator;
import org.eclipse.milo.opcua.stack.core.security.FileBasedCertificateQuarantine;
import org.eclipse.milo.opcua.stack.core.security.FileBasedTrustListManager;
import org.eclipse.milo.opcua.stack.core.security.KeyStoreCertificateStore;
import org.eclipse.milo.opcua.stack.core.security.MemoryCertificateQuarantine;
import org.eclipse.milo.opcua.stack.core.security.SecurityPolicy;
import org.eclipse.milo.opcua.stack.core.security.TrustListManager;
import org.eclipse.milo.opcua.stack.core.transport.TransportProfile;
import org.eclipse.milo.opcua.stack.core.types.builtin.DateTime;
import org.eclipse.milo.opcua.stack.core.types.builtin.LocalizedText;
import org.eclipse.milo.opcua.stack.core.types.builtin.NodeId;
import org.eclipse.milo.opcua.stack.core.types.enumerated.MessageSecurityMode;
import org.eclipse.milo.opcua.stack.core.types.structured.BuildInfo;
import org.eclipse.milo.opcua.stack.core.util.ManifestUtil;
import org.eclipse.milo.opcua.stack.core.util.validation.ValidationCheck;
import org.eclipse.milo.opcua.stack.transport.server.OpcServerTransportFactory;
import org.eclipse.milo.opcua.stack.transport.server.tcp.OpcTcpServerTransport;
import org.eclipse.milo.opcua.stack.transport.server.tcp.OpcTcpServerTransportConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class OpcUaDemoServer extends AbstractLifecycle {

  private static final String APPLICATION_URI_BASE = "urn:opc:eclipse:milo:opc-ua-demo-server";

  private static final String PRODUCT_URI = "https://github.com/digitalpetri/opc-ua-demo-server";

  private static final String PROPERTY_BUILD_DATE = "X-Server-Build-Date";
  private static final String PROPERTY_BUILD_NUMBER = "X-Server-Build-Number";
  private static final String PROPERTY_SOFTWARE_VERSION = "X-Server-Software-Version";

  private final OpcUaServer server;

  public OpcUaDemoServer(Path dataDirPath) throws Exception {
    Path securityDirPath = dataDirPath.resolve("security");
    Path pkiDirPath = securityDirPath.resolve("pki");
    Path userPkiDirPath = securityDirPath.resolve("pki-user");

    if (!pkiDirPath.toFile().exists() && !pkiDirPath.toFile().mkdirs()) {
      throw new RuntimeException("failed to resolve or create pki dir: " + pkiDirPath);
    }
    if (!userPkiDirPath.toFile().exists() && !userPkiDirPath.toFile().mkdirs()) {
      throw new RuntimeException("failed to resolve or create user pki dir: " + userPkiDirPath);
    }

    Path configFilePath = dataDirPath.resolve("server.conf");

    InputStream defaultConfigInputStream =
        OpcUaDemoServer.class.getClassLoader().getResourceAsStream("default-server.conf");

    assert defaultConfigInputStream != null;

    // If the config file doesn't exist, copy the default from the classpath.
    if (!configFilePath.toFile().exists()) {
      Files.copy(defaultConfigInputStream, configFilePath);
    }

    Config defaultConfig =
        ConfigFactory.parseReader(new InputStreamReader(defaultConfigInputStream));

    Config userConfig = ConfigFactory.parseFile(configFilePath.toFile());

    // Load the user config and merge it with the default config in case anything is missing.
    // This also allows the user config to contain only override values.
    Config config = userConfig.withFallback(defaultConfig);

    Stack.ConnectionLimits.RATE_LIMIT_ENABLED = config.getBoolean("rate-limit-enabled");

    UUID applicationUuid = readOrCreateApplicationUuid(dataDirPath);
    String applicationUri = "%s:%s".formatted(APPLICATION_URI_BASE, applicationUuid);

    var certificateStore =
        KeyStoreCertificateStore.createAndInitialize(
            new KeyStoreCertificateStore.Settings(
                pkiDirPath.resolve("certificates.pfx"),
                "password"::toCharArray,
                alias -> "password".toCharArray()));

    Path rejectedDirPath = securityDirPath.resolve("rejected");
    if (!rejectedDirPath.toFile().exists() && !rejectedDirPath.toFile().mkdirs()) {
      throw new RuntimeException("failed to resolve or create rejected dir: " + rejectedDirPath);
    }
    CertificateQuarantine certificateQuarantine =
        new FileBasedCertificateQuarantine(rejectedDirPath.toFile());

    TrustListManager trustListManager = FileBasedTrustListManager.createAndInitialize(pkiDirPath);

    final CertificateValidator certificateValidator;

    if (config.getBoolean("trust-all-certificates")) {
      certificateValidator =
          (chain, uri, hostnames) -> {

            // No validation, just accept all certificates.
            LoggerFactory.getLogger(OpcUaDemoServer.class)
                .info("Skipping validation for certificate chain:");

            for (int i = 0; i < chain.size(); i++) {
              X509Certificate certificate = chain.get(i);

              trustListManager.addTrustedCertificate(certificate);

              LoggerFactory.getLogger(OpcUaDemoServer.class)
                  .info("  certificate[{}]: {}", i, certificate.getSubjectX500Principal());
            }
          };
    } else {
      certificateValidator =
          new DefaultServerCertificateValidator(
              trustListManager, ValidationCheck.ALL_OPTIONAL_CHECKS, certificateQuarantine);
    }

    DefaultApplicationGroup defaultApplicationGroup =
        new DefaultApplicationGroup(
            trustListManager,
            certificateStore,
            new RsaSha256CertificateFactoryImpl(
                applicationUri, () -> getCertificateHostnames(config)),
            certificateValidator);

    defaultApplicationGroup.initialize();

    CertificateManager certificateManager =
        new DefaultCertificateManager(certificateQuarantine, defaultApplicationGroup);

    Supplier<X509Certificate> certificateSupplier =
        () -> {
          X509Certificate[] certificateChain =
              certificateManager
                  .getDefaultApplicationGroup()
                  .orElseThrow()
                  .getCertificateChain(NodeIds.RsaSha256ApplicationCertificateType)
                  .orElseThrow();

          return certificateChain[0];
        };

    var serverConfigBuilder = OpcUaServerConfig.builder();
    serverConfigBuilder
        .setProductUri(PRODUCT_URI)
        .setApplicationUri(applicationUri)
        .setApplicationName(LocalizedText.english("Eclipse Milo OPC UA Demo Server"))
        .setBuildInfo(createBuildInfo())
        .setEndpoints(createEndpointConfigs(config, certificateSupplier))
        .setCertificateManager(certificateManager)
        .setIdentityValidator(
            new CompositeValidator(
                AnonymousIdentityValidator.INSTANCE,
                createUsernameIdentityValidator(),
                createX509IdentityValidator(userPkiDirPath)))
        .setRoleMapper(new DemoRoleMapper())
        .setLimits(new DemoConfigLimits())
        .build();

    OpcServerTransportFactory transportFactory =
        transportProfile -> {
          if (transportProfile == TransportProfile.TCP_UASC_UABINARY) {
            OpcTcpServerTransportConfig transportConfig =
                OpcTcpServerTransportConfig.newBuilder().build();

            return new OpcTcpServerTransport(transportConfig);
          }
          return null;
        };

    server = new OpcUaServer(serverConfigBuilder.build(), transportFactory);

    server.getNamespaceTable().set(2, DemoNamespace.NAMESPACE_URI);

    server.getNamespaceTable().set(3, DataTypeTestNamespace.NAMESPACE_URI);
    var dataTypeTestNamespace = DataTypeTestNamespace.create(server);
    dataTypeTestNamespace.startup();

    var demoNamespace = new DemoNamespace(server, config);
    demoNamespace.startup();

    boolean gdsPushEnabled = config.getBoolean("gds-push-enabled");

    if (gdsPushEnabled) {
      ServerConfigurationTypeNode serverConfigurationNode =
          server
              .getAddressSpaceManager()
              .getManagedNode(NodeIds.ServerConfiguration)
              .map(ServerConfigurationTypeNode.class::cast)
              .orElseThrow();

      var serverConfigurationObject =
          new ServerConfigurationObject(server, serverConfigurationNode);
      serverConfigurationObject.startup();
    }

    server.getAddressSpaceManager().getManagedNode(NodeIds.Aliases).ifPresent(UaNode::delete);
    server.getAddressSpaceManager().getManagedNode(NodeIds.Locations).ifPresent(UaNode::delete);
  }

  @Override
  protected void onStartup() {
    server.startup();
  }

  @Override
  protected void onShutdown() {
    server.shutdown();
  }

  private UsernameIdentityValidator createUsernameIdentityValidator() {
    return new UsernameIdentityValidator(
        authenticationChallenge -> {
          String username = authenticationChallenge.getUsername();
          String password = authenticationChallenge.getPassword();

          var validUser = "User".equals(username) && "password".equals(password);
          var validUserA = "UserA".equals(username) && "password".equals(password);
          var validUserB = "UserB".equals(username) && "password".equals(password);
          var validSiteAdmin = "SiteAdmin".equals(username) && "password".equals(password);
          var validSecurityAdmin = "SecurityAdmin".equals(username) && "password".equals(password);

          return validUser || validUserA || validUserB || validSiteAdmin || validSecurityAdmin;
        });
  }

  private X509IdentityValidator createX509IdentityValidator(Path userPkiDirPath)
      throws IOException {

    var userTrustListManager = FileBasedTrustListManager.createAndInitialize(userPkiDirPath);

    var validator =
        new DefaultServerCertificateValidator(
            userTrustListManager,
            Set.of(ValidationCheck.VALIDITY, ValidationCheck.REVOCATION),
            new MemoryCertificateQuarantine());

    Predicate<X509Certificate> validate =
        certificate -> {
          try {
            validator.validateCertificateChain(List.of(certificate), null, null);
            return true;
          } catch (Exception e) {
            return false;
          }
        };

    return new X509IdentityValidator(validate);
  }

  private BuildInfo createBuildInfo() {
    var dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
    dateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));

    var manufacturerName = "digitalpetri";
    var productName = "Eclipse Milo OPC UA Demo Server";

    var softwareVersion = ManifestUtil.read(PROPERTY_SOFTWARE_VERSION).orElse("dev");
    var buildNumber = ManifestUtil.read(PROPERTY_BUILD_NUMBER).orElse("dev");
    var buildDate =
        ManifestUtil.read(PROPERTY_BUILD_DATE)
            .map(
                date -> {
                  try {
                    return new DateTime(dateFormat.parse(date));
                  } catch (Throwable t) {
                    return DateTime.NULL_VALUE;
                  }
                })
            .orElse(DateTime.NULL_VALUE);

    return new BuildInfo(
        PRODUCT_URI, manufacturerName, productName, softwareVersion, buildNumber, buildDate);
  }

  private Set<EndpointConfig> createEndpointConfigs(
      Config config, Supplier<X509Certificate> certificate) {
    var endpointConfigs = new LinkedHashSet<EndpointConfig>();

    List<String> bindAddresses = config.getStringList("bind-address-list");
    int bindPort = config.getInt("bind-port");
    List<String> securityPolicies = config.getStringList("security-policy-list");
    List<String> securityModes = config.getStringList("security-mode-list");

    for (String bindAddress : bindAddresses) {
      Set<String> hostnames = getEndpointHostnames(config);

      for (String hostname : hostnames) {
        EndpointConfig.Builder builder = EndpointConfig.newBuilder();
        builder
            .setTransportProfile(TransportProfile.TCP_UASC_UABINARY)
            .setBindAddress(bindAddress)
            .setBindPort(bindPort)
            .setHostname(hostname)
            .setPath("/milo")
            .setCertificate(certificate)
            .addTokenPolicies(
                USER_TOKEN_POLICY_ANONYMOUS, USER_TOKEN_POLICY_USERNAME, USER_TOKEN_POLICY_X509);

        for (String securityPolicyString : securityPolicies) {
          SecurityPolicy securityPolicy = SecurityPolicy.valueOf(securityPolicyString);

          if (securityPolicy == SecurityPolicy.None) {
            // No need to iterate over security modes for the None policy.
            builder.setSecurityPolicy(securityPolicy).setSecurityMode(MessageSecurityMode.None);

            endpointConfigs.add(builder.build());
          } else {
            for (String securityModeString : securityModes) {
              MessageSecurityMode securityMode = MessageSecurityMode.valueOf(securityModeString);

              if (securityMode == MessageSecurityMode.None) {
                // At this point, SecurityPolicy != None, so we ignore MessageSecurityMode.None.
                continue;
              }

              builder.setSecurityPolicy(securityPolicy).setSecurityMode(securityMode);

              endpointConfigs.add(builder.build());
            }
          }
        }

        // Expose a discovery-specific endpoint with no security.
        // Usage of the "/discovery" suffix is defined by OPC UA Part 6.

        EndpointConfig.Builder discoveryBuilder =
            builder
                .setPath("/milo/discovery")
                .setSecurityPolicy(SecurityPolicy.None)
                .setSecurityMode(MessageSecurityMode.None);

        endpointConfigs.add(discoveryBuilder.build());
      }
    }

    return endpointConfigs;
  }

  private Set<String> getEndpointHostnames(Config config) {
    Set<String> hostnames = new LinkedHashSet<>();

    for (String hostname : config.getStringList("endpoint-address-list")) {
      if (hostname.startsWith("<") && hostname.endsWith(">")) {
        hostnames.addAll(
            HostnameUtil.getHostnames(hostname.substring(1, hostname.length() - 1), true, false));
      } else {
        hostnames.add(hostname);
      }
    }

    return hostnames;
  }

  private Set<String> getCertificateHostnames(Config config) {
    Set<String> hostnames = new LinkedHashSet<>();

    for (String hostname : config.getStringList("certificate-hostname-list")) {
      if (hostname.startsWith("<") && hostname.endsWith(">")) {
        hostnames.addAll(
            HostnameUtil.getHostnames(hostname.substring(1, hostname.length() - 1), true, false));
      } else {
        hostnames.add(hostname);
      }
    }

    return hostnames;
  }

  private static UUID readOrCreateApplicationUuid(Path dataDirPath) {
    Path uuidPath = dataDirPath.resolve(".uuid");

    if (uuidPath.toFile().exists()) {
      try {
        return UUID.fromString(Files.readString(uuidPath));
      } catch (IllegalArgumentException | IOException e) {
        return createApplicationUuidFile(uuidPath);
      }
    } else {
      return createApplicationUuidFile(uuidPath);
    }
  }

  private static UUID createApplicationUuidFile(Path uuidPath) {
    UUID uuid = UUID.randomUUID();
    try {
      Files.writeString(
          uuidPath,
          uuid.toString(),
          StandardOpenOption.CREATE,
          StandardOpenOption.TRUNCATE_EXISTING);
    } catch (IOException ex) {
      throw new RuntimeException("failed to create application UUID", ex);
    }
    return uuid;
  }

  private static class DemoRoleMapper implements RoleMapper {

    public static final NodeId ROLE_SITE_A_READ = NodeId.parse("ns=1;s=SiteA_Read");
    public static final NodeId ROLE_SITE_A_WRITE = NodeId.parse("ns=1;s=SiteA_Write");
    public static final NodeId ROLE_SITE_B_READ = NodeId.parse("ns=1;s=SiteB_Read");
    public static final NodeId ROLE_SITE_B_WRITE = NodeId.parse("ns=1;s=SiteB_Write");
    public static final NodeId ROLE_SITE_ADMIN = NodeId.parse("ns=1;s=SiteAdmin");

    @Override
    public List<NodeId> getRoleIds(Identity identity) {
      if (identity instanceof AnonymousIdentity) {
        return List.of(NodeIds.WellKnownRole_Anonymous);
      } else if (identity instanceof UsernameIdentity ui) {
        return switch (ui.getUsername()) {
          case "User" -> List.of(NodeIds.WellKnownRole_AuthenticatedUser);

          case "UserA" -> List.of(ROLE_SITE_A_READ, ROLE_SITE_A_WRITE);

          case "UserB" -> List.of(ROLE_SITE_B_READ, ROLE_SITE_B_WRITE);

          case "SiteAdmin" -> List.of(ROLE_SITE_ADMIN);

          case "SecurityAdmin" -> List.of(NodeIds.WellKnownRole_SecurityAdmin);

          case null, default -> List.of();
        };
      } else {
        return Collections.emptyList();
      }
    }
  }

  // region Bootstrap

  public static void main(String[] args) throws Exception {
    // start running this static initializer ASAP, it measurably affects startup time.
    var initializerLatch = new CountDownLatch(1);
    new Thread(
            () -> {
              var ignored = NodeIds.Boolean;
              initializerLatch.countDown();
            })
        .start();

    // Needed for `SecurityPolicy.Aes256_Sha256_RsaPss`
    Security.addProvider(new BouncyCastleProvider());

    final long startTime = System.nanoTime();

    Path userDirPath = new File(System.getProperty("user.dir")).toPath();

    Path dataDirPath = userDirPath.resolve("data");
    if (!dataDirPath.toFile().exists()) {
      if (!dataDirPath.toFile().mkdir()) {
        throw new RuntimeException("failed to resolve or create data dir: " + dataDirPath);
      }
    }

    File logbackXmlFile = dataDirPath.resolve("logback.xml").toFile();
    if (!logbackXmlFile.exists()) {
      InputStream inputStream =
          OpcUaDemoServer.class.getClassLoader().getResourceAsStream("default-logback.xml");
      assert inputStream != null;

      Files.copy(inputStream, logbackXmlFile.toPath());
    }

    configureLogback(logbackXmlFile);

    var server = new OpcUaDemoServer(dataDirPath);
    server.startup();

    long startupDuration =
        TimeUnit.MILLISECONDS.convert(System.nanoTime() - startTime, TimeUnit.NANOSECONDS);

    String version =
        ManifestUtil.read(PROPERTY_SOFTWARE_VERSION).map("v%s"::formatted).orElse("(dev version)");

    Logger logger = LoggerFactory.getLogger(OpcUaDemoServer.class);
    logger.info("Eclipse Milo OPC UA Demo Server {} started in {}ms", version, startupDuration);
    logger.info("user dir: {}", userDirPath);
    logger.info("data dir: {}", dataDirPath);
    logger.info("security dir: {}", dataDirPath.resolve("security"));
    logger.info("security pki dir: {}", dataDirPath.resolve("security").resolve("pki"));

    initializerLatch.await();

    if (args.length >= 1 && args[0].equalsIgnoreCase("exit")) {
      // Shut down the server and exit. This is used by the AOT cache training run, which is
      // targeting startup and static initialization time.
      Thread.sleep(5000);
      server.shutdown();
      System.exit(0);
    } else {
      waitForShutdownHook(server);
    }
  }

  private static void waitForShutdownHook(OpcUaDemoServer server) throws InterruptedException {
    var shutdownLatch = new CountDownLatch(1);
    Runtime.getRuntime()
        .addShutdownHook(
            new Thread(
                () -> {
                  System.out.println("Shutting down server...");
                  try {
                    server.shutdown();
                  } finally {
                    shutdownLatch.countDown();
                  }
                }));
    shutdownLatch.await();
  }

  private static void configureLogback(File logbackXmlFile) {
    var context = (LoggerContext) LoggerFactory.getILoggerFactory();

    try {
      var configurator = new JoranConfigurator();
      configurator.setContext(context);
      context.reset();

      configurator.doConfigure(logbackXmlFile);
    } catch (Exception e) {
      System.err.println("Error configuring logback: " + e.getMessage());
      throw new RuntimeException(e);
    }

    new StatusPrinter2().printInCaseOfErrorsOrWarnings(context);
  }

  // endregion

}

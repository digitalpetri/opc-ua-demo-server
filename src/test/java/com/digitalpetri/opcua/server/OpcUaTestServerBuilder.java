package com.digitalpetri.opcua.server;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.jspecify.annotations.Nullable;

/**
 * Builder utility for creating OPC UA Demo Server instances in integration tests.
 *
 * <p>Provides sensible defaults for testing:
 *
 * <ul>
 *   <li>Random available port to avoid conflicts
 *   <li>SecurityPolicy.None for simplicity
 *   <li>Trust all certificates
 *   <li>Disabled rate limiting
 *   <li>Minimal address space features
 * </ul>
 *
 * <p>Example usage:
 *
 * <pre>{@code
 * // Basic usage with defaults (finds available port automatically)
 * OpcUaDemoServer server = OpcUaTestServerBuilder.builder()
 *     .build();
 * server.startup();
 *
 * // Custom configuration
 * OpcUaDemoServer server = OpcUaTestServerBuilder.builder()
 *     .withPort(12345)
 *     .withDataDir(customTempDir)
 *     .build();
 * }</pre>
 */
public class OpcUaTestServerBuilder {

  private Path dataDir;
  private @Nullable Integer port; // null means find available port automatically
  private Config customConfig;

  private OpcUaTestServerBuilder() {}

  /**
   * Create a new builder instance.
   *
   * @return new builder.
   */
  public static OpcUaTestServerBuilder builder() {
    return new OpcUaTestServerBuilder();
  }

  /**
   * Set a custom data directory. If not set, a temporary directory will be created.
   *
   * @param dataDir path to data directory.
   * @return this builder.
   */
  public OpcUaTestServerBuilder withDataDir(Path dataDir) {
    this.dataDir = dataDir;
    return this;
  }

  /**
   * Set the bind port. Use 0 for dynamic allocation (default).
   *
   * @param port bind port number.
   * @return this builder.
   */
  public OpcUaTestServerBuilder withPort(int port) {
    this.port = port;
    return this;
  }

  /**
   * Provide a custom configuration. This will be merged with test defaults, with custom config
   * taking precedence.
   *
   * @param config custom configuration.
   * @return this builder.
   */
  public OpcUaTestServerBuilder withConfig(Config config) {
    this.customConfig = config;
    return this;
  }

  /**
   * Build the OPC UA Demo Server with test configuration.
   *
   * @return configured server instance.
   * @throws Exception if server creation fails.
   */
  public OpcUaDemoServer build() throws Exception {
    // Create a data directory if not provided
    Path effectiveDataDir = dataDir;
    if (effectiveDataDir == null) {
      effectiveDataDir = Files.createTempDirectory("opcua-test-");
    }

    // Ensure data directory exists
    if (!Files.exists(effectiveDataDir)) {
      Files.createDirectories(effectiveDataDir);
    }

    // Build test configuration
    Config config = buildTestConfig();

    return new OpcUaDemoServer(effectiveDataDir, config);
  }

  /**
   * Find an available port by trying to bind to a random port in the range 10000-65535. If binding
   * fails, recursively tries again with a new random port.
   *
   * @return an available port number.
   */
  private static int findAvailablePort() {
    var port = new Random().nextInt(65535 - 10000) + 10000;

    try {
      var ss = new ServerSocket();
      var isa = new InetSocketAddress(InetAddress.getLocalHost(), port);
      ss.bind(isa);
      ss.close();
      return port;
    } catch (Throwable t) {
      // Port not available, try again
      return findAvailablePort();
    }
  }

  private Config buildTestConfig() {
    var testDefaults = new HashMap<String, Object>();

    // Network configuration - use available port
    int effectivePort = port != null ? port : findAvailablePort();
    testDefaults.put("bind-address-list", List.of("0.0.0.0"));
    testDefaults.put("bind-port", effectivePort);
    testDefaults.put("endpoint-address-list", List.of("<localhost>"));
    testDefaults.put("certificate-hostname-list", List.of("<localhost>"));

    // Security configuration - minimal for testing
    testDefaults.put("security-policy-list", List.of("None"));
    testDefaults.put("security-mode-list", List.of("None"));
    testDefaults.put("trust-all-certificates", true);
    testDefaults.put("gds-push-enabled", false);

    // Performance configuration
    testDefaults.put("rate-limit-enabled", false);

    // Address space configuration - disable all optional features for faster startup
    var addressSpace = new HashMap<String, Object>();
    addressSpace.put("ctt.enabled", false);
    addressSpace.put("data-type-test.enabled", false);
    addressSpace.put("dynamic.enabled", false);
    addressSpace.put("mass.enabled", false);
    addressSpace.put("null.enabled", false);
    addressSpace.put("turtles.enabled", false);
    testDefaults.put("address-space", addressSpace);

    // RBAC configuration - minimal setup for testing
    var rbac = new HashMap<String, Object>();
    rbac.put(
        "site-a",
        List.of(
            Map.of("role-id", "ns=1;s=SiteA_Read", "permissions", List.of("Browse", "Read")),
            Map.of("role-id", "ns=1;s=SiteA_Write", "permissions", List.of("Write", "Call")),
            Map.of(
                "role-id",
                "ns=1;s=SiteAdmin",
                "permissions",
                List.of("Browse", "Read", "ReadRolePermissions"))));
    rbac.put(
        "site-b",
        List.of(
            Map.of("role-id", "ns=1;s=SiteB_Read", "permissions", List.of("Browse", "Read")),
            Map.of("role-id", "ns=1;s=SiteB_Write", "permissions", List.of("Write", "Call")),
            Map.of(
                "role-id",
                "ns=1;s=SiteAdmin",
                "permissions",
                List.of("Browse", "Read", "ReadRolePermissions"))));
    testDefaults.put("rbac", rbac);

    Config defaultTestConfig = ConfigFactory.parseMap(testDefaults);

    // Merge with custom config if provided
    if (customConfig != null) {
      return customConfig.withFallback(defaultTestConfig);
    }

    return defaultTestConfig;
  }
}

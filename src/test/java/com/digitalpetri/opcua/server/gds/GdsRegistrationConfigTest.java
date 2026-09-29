package com.digitalpetri.opcua.server.gds;

import static org.junit.jupiter.api.Assertions.*;

import com.digitalpetri.opcua.server.OpcUaDemoServer;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GdsRegistrationConfigTest {
  static Config enabled() {
    return ConfigFactory.parseString(
        """
        gds.registration {
          enabled = true
          endpoint-url = "opc.tcp://localhost:58810/GDS"
          identity.username = "registrar"
          identity.password = "secret-value"
        }
        """);
  }

  @Test
  void absentAndDisabledSectionsIgnorePlaceholders() {
    assertTrue(GdsRegistrationConfig.fromConfig(ConfigFactory.empty()).isEmpty());
    assertTrue(
        GdsRegistrationConfig.fromConfig(
                ConfigFactory.parseString(
                    """
        gds.registration { enabled = false, identity = "placeholder", request-timeout = -1 }
        """))
            .isEmpty());
  }

  @Test
  void enabledSettingsUseDefaultsAndDeduplicateOverrides() {
    Config config =
        ConfigFactory.parseMap(
                Map.of(
                    "gds.registration.discovery-url-list",
                    List.of(
                        "opc.tcp://public.example:4840/milo",
                        "opc.tcp://public.example:4840/milo",
                        "opc.tcp://[::1]/milo")))
            .withFallback(enabled());
    GdsRegistrationConfig parsed = GdsRegistrationConfig.fromConfig(config).orElseThrow();
    assertEquals(10_000, parsed.requestTimeoutMillis());
    assertEquals(60_000, parsed.attemptTimeoutMillis());
    assertEquals(
        List.of("opc.tcp://public.example:4840/milo", "opc.tcp://[::1]/milo"),
        parsed.discoveryUrls());
    assertEquals("opc.tcp://localhost:58810/GDS", parsed.endpointUrl());
  }

  @Test
  void rejectsInvalidFieldsWithoutRenderingSecrets() {
    Map<String, List<Object>> invalid =
        Map.of(
            "endpoint-url",
                List.of(
                    "http://host",
                    "opc.tcp:///path",
                    "opc.tcp://user:secret-value@host",
                    "opc.tcp://host:0",
                    "opc.tcp://host:65536",
                    "opc.tcp://host?secret-value",
                    "opc.tcp://host#secret-value",
                    "opc.tcp://host:"),
            "security-policy", List.of("None", "ECC_nistP256", "unknown"),
            "identity.username", List.of(" ", Map.of("secret-value", "x")),
            "identity.password", List.of("", Map.of("secret-value", "x")),
            "request-timeout",
                List.of("0ms", "-1s", "0.5ms", "4294967296ms", "1000000000000000000000s"),
            "attempt-timeout", List.of("1ms", "0ms", "1000000000000000000000s"),
            "retry-interval", List.of("0ms", "0.5ms", "1000000000000000000000s"),
            "discovery-url-list", List.of(List.of("opc.tcp://host/path?secret-value")),
            "update-existing", List.of(Map.of("secret-value", "x")));
    invalid.forEach(
        (key, values) ->
            values.forEach(
                value -> {
                  Config config =
                      ConfigFactory.parseMap(Map.of("gds.registration." + key, value))
                          .withFallback(enabled());
                  IllegalArgumentException error =
                      assertThrows(
                          IllegalArgumentException.class,
                          () -> GdsRegistrationConfig.fromConfig(config),
                          key + "=" + value);
                  assertTrue(error.getMessage().contains("gds.registration." + key));
                  assertFalse(error.toString().contains("secret-value"));
                  assertNull(error.getCause());
                }));
  }

  @Test
  void credentialsAndContainingConfigAreRedacted() {
    GdsRegistrationConfig parsed = GdsRegistrationConfig.fromConfig(enabled()).orElseThrow();
    assertFalse(parsed.toString().contains("secret-value"));
    assertFalse(parsed.identity().toString().contains("registrar"));
  }

  @Test
  void firstRunAndOldFilesKeepDefaultsAndResolveEnvironment(@TempDir Path directory)
      throws Exception {
    Path path = directory.resolve("server.conf");
    Config first = OpcUaDemoServer.loadConfiguration(path);
    assertFalse(first.getBoolean("gds.registration.enabled"));
    assertTrue(first.getBoolean("gds-push-enabled"));
    Files.writeString(path, "gds.registration.identity.password = ${PATH}");
    Config old = OpcUaDemoServer.loadConfiguration(path);
    assertEquals(System.getenv("PATH"), old.getString("gds.registration.identity.password"));
    assertEquals("Basic256Sha256", old.getString("gds.registration.security-policy"));
    Files.writeString(
        path, "gds.registration.identity.password = ${MISSING_GDS_TEST_SUBSTITUTION}");
    IllegalArgumentException error =
        assertThrows(IllegalArgumentException.class, () -> OpcUaDemoServer.loadConfiguration(path));
    assertNull(error.getCause());
  }
}

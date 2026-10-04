package com.digitalpetri.opcua.server.reverse;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import org.eclipse.milo.opcua.sdk.server.reverse.ReverseConnectTarget;
import org.junit.jupiter.api.Test;

class ReverseConnectConfigTest {

  // region empty configurations

  @Test
  void absentReverseConnectSectionYieldsEmptyTargets() {
    Config config = ConfigFactory.parseString("");

    ReverseConnectConfig reverseConnectConfig = ReverseConnectConfig.fromConfig(config);

    assertTrue(reverseConnectConfig.targets().isEmpty());
  }

  @Test
  void emptyTargetListYieldsEmptyTargets() {
    Config config =
        ConfigFactory.parseString(
            """
            reverse-connect {
              target-list = []
            }
            """);

    ReverseConnectConfig reverseConnectConfig = ReverseConnectConfig.fromConfig(config);

    assertTrue(reverseConnectConfig.targets().isEmpty());
  }

  @Test
  void shippedDefaultServerConfSchedulesNoTargets() {
    Config config = ConfigFactory.parseResources("default-server.conf");

    // Guard against a missing resource: parseResources silently returns an empty Config, and this
    // also proves the shipped default documents the reverse-connect.target-list section.
    assertTrue(
        config.hasPath("reverse-connect.target-list"),
        "default-server.conf must contain the reverse-connect.target-list section");

    ReverseConnectConfig reverseConnectConfig = ReverseConnectConfig.fromConfig(config);

    assertTrue(reverseConnectConfig.targets().isEmpty());
  }

  // endregion

  // region parsing and defaults

  @Test
  void fullySpecifiedTargetParses() {
    Config config =
        ConfigFactory.parseString(
            """
            reverse-connect {
              target-list = [
                {
                  enabled = false
                  client-listener-url = "opc.tcp://client.example.com:48060"
                  endpoint-url = "opc.tcp://localhost:4840/milo"
                  registration-period = 45 seconds
                  connect-timeout = 10 seconds
                }
              ]
            }
            """);

    ReverseConnectConfig reverseConnectConfig = ReverseConnectConfig.fromConfig(config);

    assertEquals(1, reverseConnectConfig.targets().size());

    ReverseConnectTargetConfig target = reverseConnectConfig.targets().get(0);
    assertFalse(target.enabled());
    assertEquals("opc.tcp://client.example.com:48060", target.clientListenerUrl());
    assertEquals("opc.tcp://localhost:4840/milo", target.endpointUrl());
    assertEquals(Duration.ofSeconds(45), target.registrationPeriod());
    assertEquals(Duration.ofSeconds(10), target.connectTimeout());
  }

  @Test
  void omittedOptionalFieldsUseDefaults() {
    Config config =
        ConfigFactory.parseString(
            """
            reverse-connect {
              target-list = [
                {
                  client-listener-url = "opc.tcp://client.example.com:48060"
                  endpoint-url = "opc.tcp://localhost:4840/milo"
                }
              ]
            }
            """);

    ReverseConnectConfig reverseConnectConfig = ReverseConnectConfig.fromConfig(config);

    ReverseConnectTargetConfig target = reverseConnectConfig.targets().get(0);
    assertEquals(ReverseConnectConfig.DEFAULT_ENABLED, target.enabled());
    assertTrue(target.enabled());
    assertEquals(ReverseConnectConfig.DEFAULT_REGISTRATION_PERIOD, target.registrationPeriod());
    assertEquals(Duration.ofSeconds(30), target.registrationPeriod());
    assertEquals(ReverseConnectConfig.DEFAULT_CONNECT_TIMEOUT, target.connectTimeout());
    assertEquals(Duration.ofSeconds(5), target.connectTimeout());
  }

  @Test
  void durationSyntaxVariantsParse() {
    Config config =
        ConfigFactory.parseString(
            """
            reverse-connect {
              target-list = [
                {
                  client-listener-url = "opc.tcp://client.example.com:48060"
                  endpoint-url = "opc.tcp://localhost:4840/milo"
                  registration-period = 30 seconds
                  connect-timeout = 5000 ms
                },
                {
                  client-listener-url = "opc.tcp://client.example.com:48060"
                  endpoint-url = "opc.tcp://localhost:4840/milo"
                  registration-period = 30s
                  connect-timeout = "5s"
                }
              ]
            }
            """);

    ReverseConnectConfig reverseConnectConfig = ReverseConnectConfig.fromConfig(config);

    ReverseConnectTargetConfig first = reverseConnectConfig.targets().get(0);
    assertEquals(Duration.ofSeconds(30), first.registrationPeriod());
    assertEquals(Duration.ofMillis(5000), first.connectTimeout());

    ReverseConnectTargetConfig second = reverseConnectConfig.targets().get(1);
    assertEquals(Duration.ofSeconds(30), second.registrationPeriod());
    assertEquals(Duration.ofSeconds(5), second.connectTimeout());
  }

  // endregion

  // region validation failures

  @Test
  void missingClientListenerUrlFails() {
    Config config =
        ConfigFactory.parseString(
            """
            reverse-connect {
              target-list = [
                {
                  endpoint-url = "opc.tcp://localhost:4840/milo"
                }
              ]
            }
            """);

    IllegalArgumentException e =
        assertThrows(IllegalArgumentException.class, () -> ReverseConnectConfig.fromConfig(config));

    assertTrue(e.getMessage().contains("reverse-connect.target-list[0].client-listener-url"));
    assertTrue(e.getMessage().contains("is required"));
    assertTrue(e.getMessage().contains("opc.tcp://host:port"));
  }

  @Test
  void missingEndpointUrlFails() {
    Config config =
        ConfigFactory.parseString(
            """
            reverse-connect {
              target-list = [
                {
                  client-listener-url = "opc.tcp://client.example.com:48060"
                }
              ]
            }
            """);

    IllegalArgumentException e =
        assertThrows(IllegalArgumentException.class, () -> ReverseConnectConfig.fromConfig(config));

    assertTrue(e.getMessage().contains("reverse-connect.target-list[0].endpoint-url"));
    assertTrue(e.getMessage().contains("is required"));
    assertTrue(e.getMessage().contains("opc.tcp://host:port"));
  }

  @Test
  void nonOpcTcpSchemeFails() {
    Config config =
        ConfigFactory.parseString(
            """
            reverse-connect {
              target-list = [
                {
                  client-listener-url = "http://client.example.com:48060"
                  endpoint-url = "opc.tcp://localhost:4840/milo"
                }
              ]
            }
            """);

    IllegalArgumentException e =
        assertThrows(IllegalArgumentException.class, () -> ReverseConnectConfig.fromConfig(config));

    assertTrue(e.getMessage().contains("reverse-connect.target-list[0].client-listener-url"));
    assertTrue(e.getMessage().contains("opc.tcp"));
    assertTrue(e.getMessage().contains("http://client.example.com:48060"));
  }

  @Test
  void urlWithNoHostFails() {
    Config config =
        ConfigFactory.parseString(
            """
            reverse-connect {
              target-list = [
                {
                  client-listener-url = "opc.tcp://client.example.com:48060"
                  endpoint-url = "opc.tcp://"
                }
              ]
            }
            """);

    IllegalArgumentException e =
        assertThrows(IllegalArgumentException.class, () -> ReverseConnectConfig.fromConfig(config));

    assertTrue(e.getMessage().contains("reverse-connect.target-list[0].endpoint-url"));
    assertTrue(e.getMessage().contains("opc.tcp://host:port"));
    assertTrue(e.getMessage().contains("opc.tcp://"));
  }

  @Test
  void zeroRegistrationPeriodFails() {
    IllegalArgumentException e =
        assertThrows(
            IllegalArgumentException.class,
            () -> ReverseConnectConfig.fromConfig(targetWith("registration-period = 0 seconds")));

    assertTrue(e.getMessage().contains("reverse-connect.target-list[0].registration-period"));
    assertTrue(e.getMessage().contains("positive HOCON duration"));
  }

  @Test
  void negativeRegistrationPeriodFails() {
    IllegalArgumentException e =
        assertThrows(
            IllegalArgumentException.class,
            () -> ReverseConnectConfig.fromConfig(targetWith("registration-period = -5 seconds")));

    assertTrue(e.getMessage().contains("reverse-connect.target-list[0].registration-period"));
    assertTrue(e.getMessage().contains("positive HOCON duration"));
  }

  @Test
  void zeroConnectTimeoutFails() {
    IllegalArgumentException e =
        assertThrows(
            IllegalArgumentException.class,
            () -> ReverseConnectConfig.fromConfig(targetWith("connect-timeout = 0 ms")));

    assertTrue(e.getMessage().contains("reverse-connect.target-list[0].connect-timeout"));
    assertTrue(e.getMessage().contains("positive HOCON duration"));
  }

  @Test
  void negativeConnectTimeoutFails() {
    IllegalArgumentException e =
        assertThrows(
            IllegalArgumentException.class,
            () -> ReverseConnectConfig.fromConfig(targetWith("connect-timeout = -1 seconds")));

    assertTrue(e.getMessage().contains("reverse-connect.target-list[0].connect-timeout"));
    assertTrue(e.getMessage().contains("positive HOCON duration"));
  }

  @Test
  void unparseableDurationFails() {
    IllegalArgumentException e =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                ReverseConnectConfig.fromConfig(
                    targetWith("registration-period = \"not a duration\"")));

    assertTrue(e.getMessage().contains("reverse-connect.target-list[0].registration-period"));
    assertTrue(e.getMessage().contains("positive HOCON duration"));
    assertTrue(e.getMessage().contains("30 seconds"));
    assertTrue(e.getMessage().contains("not a duration"));
  }

  @Test
  void errorMessageIdentifiesLaterTargetIndex() {
    Config config =
        ConfigFactory.parseString(
            """
            reverse-connect {
              target-list = [
                {
                  client-listener-url = "opc.tcp://client.example.com:48060"
                  endpoint-url = "opc.tcp://localhost:4840/milo"
                },
                {
                  client-listener-url = "opc.tcp://client.example.com:48061"
                  endpoint-url = "opc.tcp://localhost:4840/milo"
                  registration-period = 0s
                }
              ]
            }
            """);

    IllegalArgumentException e =
        assertThrows(IllegalArgumentException.class, () -> ReverseConnectConfig.fromConfig(config));

    assertTrue(e.getMessage().contains("reverse-connect.target-list[1].registration-period"));
  }

  @Test
  void registrationPeriodExceedingUnsignedMillisRangeFails() {
    // 50 days is 4,320,000,000 ms, which exceeds the unsigned 32-bit range of 4,294,967,295 ms.
    IllegalArgumentException e =
        assertThrows(
            IllegalArgumentException.class,
            () -> ReverseConnectConfig.fromConfig(targetWith("registration-period = 50 days")));

    assertTrue(e.getMessage().contains("reverse-connect.target-list[0]"));
    assertTrue(e.getMessage().contains("4294967295"));
  }

  // endregion

  // region SDK target mapping

  @Test
  void toTargetsMapsFieldsToSdkTarget() {
    Config config =
        ConfigFactory.parseString(
            """
            reverse-connect {
              target-list = [
                {
                  enabled = false
                  client-listener-url = "opc.tcp://client.example.com:48060"
                  endpoint-url = "opc.tcp://localhost:4840/milo"
                  registration-period = 45 seconds
                  connect-timeout = 10 seconds
                }
              ]
            }
            """);

    Set<ReverseConnectTarget> targets = ReverseConnectConfig.fromConfig(config).toTargets();

    assertEquals(1, targets.size());

    ReverseConnectTarget target = targets.iterator().next();
    assertEquals("opc.tcp://client.example.com:48060", target.getClientListenerUrl());
    assertEquals("opc.tcp://localhost:4840/milo", target.getEndpointUrl());
    assertEquals(45_000L, target.getRegistrationPeriod().longValue());
    assertEquals(10_000L, target.getConnectTimeout().longValue());
    assertFalse(target.isEnabled());
    assertFalse(target.isPaused());
  }

  @Test
  void toTargetsAppliesDefaultsAndPreservesEnabled() {
    Set<ReverseConnectTarget> targets = ReverseConnectConfig.fromConfig(targetWith("")).toTargets();

    ReverseConnectTarget target = targets.iterator().next();
    assertTrue(target.isEnabled());
    assertEquals(30_000L, target.getRegistrationPeriod().longValue());
    assertEquals(5_000L, target.getConnectTimeout().longValue());
  }

  // endregion

  // region endpoint URL cross-validation

  @Test
  void validateEndpointUrlsAcceptsExactUrlMatch() {
    // targetWith("") configures endpoint-url = opc.tcp://localhost:4840/milo
    ReverseConnectConfig reverseConnectConfig = ReverseConnectConfig.fromConfig(targetWith(""));

    assertDoesNotThrow(
        () ->
            reverseConnectConfig.validateEndpointUrls(
                List.of(
                    "opc.tcp://localhost:4840/milo", "opc.tcp://localhost:4840/milo/discovery")));
  }

  @Test
  void validateEndpointUrlsAcceptsEndpointPathMatch() {
    ReverseConnectConfig reverseConnectConfig =
        ReverseConnectConfig.fromConfig(
            targetWith("endpoint-url = \"opc.tcp://public.example.com:4840/milo\""));

    // Different host and port, but the endpoint path "/milo" matches.
    assertDoesNotThrow(
        () -> reverseConnectConfig.validateEndpointUrls(List.of("opc.tcp://localhost:12686/milo")));
  }

  @Test
  void validateEndpointUrlsRejectsMismatch() {
    ReverseConnectConfig reverseConnectConfig = ReverseConnectConfig.fromConfig(targetWith(""));

    IllegalArgumentException e =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                reverseConnectConfig.validateEndpointUrls(
                    List.of("opc.tcp://localhost:4840/other")));

    assertTrue(e.getMessage().contains("reverse-connect.target-list[0].endpoint-url"));
    assertTrue(e.getMessage().contains("opc.tcp://localhost:4840/milo"));
    assertTrue(e.getMessage().contains("opc.tcp://localhost:4840/other"));
  }

  @Test
  void validateEndpointUrlsIdentifiesLaterTargetIndex() {
    Config config =
        ConfigFactory.parseString(
            """
            reverse-connect {
              target-list = [
                {
                  client-listener-url = "opc.tcp://client.example.com:48060"
                  endpoint-url = "opc.tcp://localhost:4840/milo"
                },
                {
                  client-listener-url = "opc.tcp://client.example.com:48061"
                  endpoint-url = "opc.tcp://localhost:4840/nomatch"
                }
              ]
            }
            """);

    ReverseConnectConfig reverseConnectConfig = ReverseConnectConfig.fromConfig(config);

    IllegalArgumentException e =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                reverseConnectConfig.validateEndpointUrls(
                    List.of("opc.tcp://localhost:4840/milo")));

    assertTrue(e.getMessage().contains("reverse-connect.target-list[1].endpoint-url"));
    assertTrue(e.getMessage().contains("opc.tcp://localhost:4840/nomatch"));
  }

  @Test
  void validateEndpointUrlsValidatesDisabledTargets() {
    ReverseConnectConfig reverseConnectConfig =
        ReverseConnectConfig.fromConfig(targetWith("enabled = false"));

    IllegalArgumentException e =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                reverseConnectConfig.validateEndpointUrls(
                    List.of("opc.tcp://localhost:4840/other")));

    assertTrue(e.getMessage().contains("reverse-connect.target-list[0].endpoint-url"));
  }

  @Test
  void validateEndpointUrlsWithNoTargetsAcceptsAnyEndpointList() {
    ReverseConnectConfig reverseConnectConfig =
        ReverseConnectConfig.fromConfig(ConfigFactory.parseString(""));

    assertDoesNotThrow(() -> reverseConnectConfig.validateEndpointUrls(List.of()));
  }

  // endregion

  private static Config targetWith(String extraField) {
    return ConfigFactory.parseString(
        """
        reverse-connect {
          target-list = [
            {
              client-listener-url = "opc.tcp://client.example.com:48060"
              endpoint-url = "opc.tcp://localhost:4840/milo"
              %s
            }
          ]
        }
        """
            .formatted(extraField));
  }
}

package com.digitalpetri.opcua.server.reverse;

import static org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.Unsigned.uint;

import java.time.Duration;
import java.util.Objects;
import org.eclipse.milo.opcua.sdk.server.reverse.ReverseConnectTarget;
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.UInteger;

/**
 * Immutable configuration for one server-side Reverse Connect target parsed from {@code
 * server.conf}.
 *
 * @param enabled whether this target should schedule outbound connection attempts.
 * @param clientListenerUrl the {@code opc.tcp://host:port} URL of the client reverse listener the
 *     server should dial.
 * @param endpointUrl the server endpoint URL advertised in {@code ReverseHello}.
 * @param registrationPeriod the retry/re-registration interval after failed attempts or closed
 *     reverse channels.
 * @param connectTimeout the TCP connection timeout for each outbound attempt.
 */
public record ReverseConnectTargetConfig(
    boolean enabled,
    String clientListenerUrl,
    String endpointUrl,
    Duration registrationPeriod,
    Duration connectTimeout) {

  private static final Duration MAX_CONNECT_TIMEOUT = Duration.ofMillis(Integer.MAX_VALUE);

  private static final Duration MAX_REGISTRATION_PERIOD = Duration.ofMillis(UInteger.MAX_VALUE);

  public ReverseConnectTargetConfig {
    Objects.requireNonNull(clientListenerUrl, "clientListenerUrl cannot be null");
    Objects.requireNonNull(endpointUrl, "endpointUrl cannot be null");
    Objects.requireNonNull(registrationPeriod, "registrationPeriod cannot be null");
    Objects.requireNonNull(connectTimeout, "connectTimeout cannot be null");

    if (registrationPeriod.isZero() || registrationPeriod.isNegative()) {
      throw new IllegalArgumentException(
          "registrationPeriod must be positive but was " + registrationPeriod);
    }
    if (registrationPeriod.compareTo(MAX_REGISTRATION_PERIOD) > 0) {
      throw new IllegalArgumentException(
          "registrationPeriod must not exceed "
              + UInteger.MAX_VALUE
              + " milliseconds but was "
              + registrationPeriod);
    }
    if (connectTimeout.isZero() || connectTimeout.isNegative()) {
      throw new IllegalArgumentException(
          "connectTimeout must be positive but was " + connectTimeout);
    }
    if (connectTimeout.compareTo(MAX_CONNECT_TIMEOUT) > 0) {
      throw new IllegalArgumentException(
          "connectTimeout must not exceed "
              + Integer.MAX_VALUE
              + " milliseconds but was "
              + connectTimeout);
    }
  }

  /**
   * Map this configuration to the Milo SDK {@link ReverseConnectTarget} it describes.
   *
   * <p>Durations are converted to unsigned millisecond values. The SDK default retry policy is
   * retained, so retries after failed attempts or closed reverse channels are delayed by {@code
   * registrationPeriod}.
   *
   * @return the equivalent SDK {@link ReverseConnectTarget}.
   */
  public ReverseConnectTarget toTarget() {
    return ReverseConnectTarget.builder()
        .setClientListenerUrl(clientListenerUrl)
        .setEndpointUrl(endpointUrl)
        .setRegistrationPeriod(uint(registrationPeriod.toMillis()))
        .setConnectTimeout(uint(connectTimeout.toMillis()))
        .setEnabled(enabled)
        .build();
  }
}

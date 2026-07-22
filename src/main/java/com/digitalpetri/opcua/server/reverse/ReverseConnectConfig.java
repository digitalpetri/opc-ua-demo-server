package com.digitalpetri.opcua.server.reverse;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.eclipse.milo.opcua.sdk.server.reverse.ReverseConnectTarget;
import org.eclipse.milo.opcua.stack.core.util.EndpointUtil;

/**
 * Immutable Reverse Connect configuration parsed from the {@code reverse-connect} section of {@code
 * server.conf}.
 *
 * <p>An absent {@code reverse-connect.target-list} path or an empty list yields a configuration
 * with no targets, meaning no outbound Reverse Connect attempts are scheduled.
 *
 * @param targets the configured Reverse Connect targets, possibly empty.
 */
public record ReverseConnectConfig(List<ReverseConnectTargetConfig> targets) {

  /** Default value for {@code enabled} when omitted from a target. */
  public static final boolean DEFAULT_ENABLED = true;

  /** Default value for {@code registration-period} when omitted from a target. */
  public static final Duration DEFAULT_REGISTRATION_PERIOD = Duration.ofSeconds(30);

  /** Default value for {@code connect-timeout} when omitted from a target. */
  public static final Duration DEFAULT_CONNECT_TIMEOUT = Duration.ofSeconds(5);

  private static final String TARGET_LIST_PATH = "reverse-connect.target-list";

  private static final String URL_SHAPE = "opc.tcp://host:port";

  private static final String DURATION_SHAPE =
      "a positive HOCON duration (e.g. \"30 seconds\", \"30s\", \"5000 ms\")";

  public ReverseConnectConfig {
    Objects.requireNonNull(targets, "targets cannot be null");
    targets = List.copyOf(targets);
  }

  /**
   * Parse Reverse Connect configuration from the {@code reverse-connect.target-list} section of
   * {@code config}.
   *
   * @param config the server configuration to parse.
   * @return the parsed configuration; empty targets if {@code reverse-connect.target-list} is
   *     absent or empty.
   * @throws IllegalArgumentException if any target is invalid; the message identifies the target
   *     index and field along with the expected value shape.
   */
  public static ReverseConnectConfig fromConfig(Config config) {
    Objects.requireNonNull(config, "config cannot be null");

    if (!config.hasPath(TARGET_LIST_PATH)) {
      return new ReverseConnectConfig(List.of());
    }

    List<? extends Config> targetConfigs;
    try {
      targetConfigs = config.getConfigList(TARGET_LIST_PATH);
    } catch (ConfigException e) {
      throw new IllegalArgumentException(
          TARGET_LIST_PATH + ": expected a list of target objects", e);
    }

    List<ReverseConnectTargetConfig> targets = new ArrayList<>(targetConfigs.size());

    for (int i = 0; i < targetConfigs.size(); i++) {
      targets.add(parseTarget(targetConfigs.get(i), i));
    }

    return new ReverseConnectConfig(targets);
  }

  /**
   * Map the configured targets to the Milo SDK {@link ReverseConnectTarget}s they describe.
   *
   * <p>Disabled targets are included: the SDK registers them but never schedules outbound
   * connection attempts, matching the spec requirement that disabled targets are accepted but
   * inert.
   *
   * @return the equivalent SDK targets, possibly empty.
   */
  public Set<ReverseConnectTarget> toTargets() {
    return targets.stream()
        .map(ReverseConnectTargetConfig::toTarget)
        .collect(Collectors.toUnmodifiableSet());
  }

  /**
   * Validate that every target's {@code endpoint-url} matches one of the server's configured {@code
   * opc.tcp} endpoint URLs, either by exact URL or by endpoint path.
   *
   * <p>This mirrors the validation Milo's {@code ReverseConnectTargetManager} performs at server
   * startup, so a configuration that passes here cannot fail SDK-side endpoint matching later.
   *
   * @param serverEndpointUrls the endpoint URLs of the server's configured opc.tcp endpoints.
   * @throws IllegalArgumentException if any target's endpoint-url matches no configured endpoint;
   *     the message identifies the target index and field and lists the configured endpoint URLs.
   */
  public void validateEndpointUrls(Collection<String> serverEndpointUrls) {
    Objects.requireNonNull(serverEndpointUrls, "serverEndpointUrls cannot be null");

    for (int i = 0; i < targets.size(); i++) {
      String endpointUrl = targets.get(i).endpointUrl();
      String endpointPath = EndpointUtil.getPath(endpointUrl);

      boolean match =
          serverEndpointUrls.stream()
              .anyMatch(
                  serverUrl ->
                      endpointUrl.equals(serverUrl)
                          || endpointPath.equals(EndpointUtil.getPath(serverUrl)));

      if (!match) {
        String configuredUrls =
            serverEndpointUrls.stream().distinct().sorted().collect(Collectors.joining(", "));

        throw new IllegalArgumentException(
            fieldContext(i, "endpoint-url")
                + ": \""
                + endpointUrl
                + "\" does not match any configured server opc.tcp endpoint by URL or endpoint"
                + " path; configured endpoint URLs: ["
                + configuredUrls
                + "]");
      }
    }
  }

  private static ReverseConnectTargetConfig parseTarget(Config target, int index) {
    boolean enabled = parseEnabled(target, index);
    String clientListenerUrl = parseRequiredOpcTcpUrl(target, index, "client-listener-url");
    String endpointUrl = parseRequiredOpcTcpUrl(target, index, "endpoint-url");
    Duration registrationPeriod =
        parseDuration(target, index, "registration-period", DEFAULT_REGISTRATION_PERIOD);
    Duration connectTimeout =
        parseDuration(target, index, "connect-timeout", DEFAULT_CONNECT_TIMEOUT);

    try {
      return new ReverseConnectTargetConfig(
          enabled, clientListenerUrl, endpointUrl, registrationPeriod, connectTimeout);
    } catch (IllegalArgumentException e) {
      // Add index context to bound violations reported by the record constructor, e.g. a
      // registration-period that exceeds the unsigned 32-bit millisecond range.
      throw new IllegalArgumentException(
          TARGET_LIST_PATH + "[" + index + "]: " + e.getMessage(), e);
    }
  }

  private static boolean parseEnabled(Config target, int index) {
    if (!target.hasPath("enabled")) {
      return DEFAULT_ENABLED;
    }

    try {
      return target.getBoolean("enabled");
    } catch (ConfigException e) {
      throw new IllegalArgumentException(
          fieldContext(index, "enabled")
              + ": expected a boolean (true or false) but was "
              + rawValue(target, "enabled"),
          e);
    }
  }

  private static String parseRequiredOpcTcpUrl(Config target, int index, String field) {
    if (!target.hasPath(field)) {
      throw new IllegalArgumentException(
          fieldContext(index, field) + " is required (" + URL_SHAPE + ")");
    }

    String url;
    try {
      url = target.getString(field);
    } catch (ConfigException e) {
      throw new IllegalArgumentException(
          fieldContext(index, field)
              + ": expected a URL string ("
              + URL_SHAPE
              + ") but was "
              + rawValue(target, field),
          e);
    }

    String scheme = EndpointUtil.getScheme(url);
    String host = EndpointUtil.getHost(url);

    if (scheme == null || host == null || host.isBlank()) {
      throw new IllegalArgumentException(
          fieldContext(index, field)
              + ": expected an opc.tcp URL with a host ("
              + URL_SHAPE
              + ") but was \""
              + url
              + "\"");
    }
    if (!"opc.tcp".equals(scheme)) {
      throw new IllegalArgumentException(
          fieldContext(index, field)
              + ": expected URL scheme opc.tcp ("
              + URL_SHAPE
              + ") but was \""
              + url
              + "\"");
    }

    return url;
  }

  private static Duration parseDuration(
      Config target, int index, String field, Duration defaultValue) {

    if (!target.hasPath(field)) {
      return defaultValue;
    }

    Duration duration;
    try {
      duration = target.getDuration(field);
    } catch (ConfigException e) {
      throw new IllegalArgumentException(
          fieldContext(index, field)
              + ": expected "
              + DURATION_SHAPE
              + " but was "
              + rawValue(target, field),
          e);
    }

    if (duration.isZero() || duration.isNegative()) {
      throw new IllegalArgumentException(
          fieldContext(index, field)
              + ": expected "
              + DURATION_SHAPE
              + " but was "
              + rawValue(target, field));
    }

    return duration;
  }

  private static String fieldContext(int index, String field) {
    return TARGET_LIST_PATH + "[" + index + "]." + field;
  }

  private static String rawValue(Config target, String field) {
    return "\"" + target.getValue(field).unwrapped() + "\"";
  }
}

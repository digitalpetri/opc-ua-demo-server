package com.digitalpetri.opcua.server.gds;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigException;
import com.typesafe.config.ConfigFactory;
import java.net.URI;
import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import org.eclipse.milo.opcua.stack.core.security.SecurityPolicy;

/** Validated opt-in directory registration settings. Credentials never appear in diagnostics. */
public record GdsRegistrationConfig(
    String endpointUrl,
    SecurityPolicy securityPolicy,
    Identity identity,
    List<String> discoveryUrls,
    boolean updateExisting,
    long requestTimeoutMillis,
    long attemptTimeoutMillis,
    long retryIntervalMillis) {

  private static final String PREFIX = "gds.registration.";
  private static final Set<SecurityPolicy> POLICIES =
      Set.of(
          SecurityPolicy.Basic256Sha256,
          SecurityPolicy.Aes128_Sha256_RsaOaep,
          SecurityPolicy.Aes256_Sha256_RsaPss);

  /** Identity used only for outgoing GDS sessions. */
  public sealed interface Identity {}

  /** An anonymous session, used only when configured explicitly. */
  public record Anonymous() implements Identity {}

  /** Username credentials. */
  public record Credentials(String username, String password) implements Identity {
    @Override
    public String toString() {
      return "Credentials[redacted]";
    }
  }

  /**
   * Parses enabled settings, supplying defaults for older configuration files.
   *
   * @param config the resolved server configuration.
   * @return empty when registration is absent or disabled.
   */
  public static Optional<GdsRegistrationConfig> fromConfig(Config config) {
    Config merged = config.withFallback(ConfigFactory.parseResources("default-server.conf"));
    if (!read("enabled", () -> merged.getBoolean(PREFIX + "enabled"))) {
      return Optional.empty();
    }
    String endpoint = string(merged, "endpoint-url");
    validateUrl(endpoint, PREFIX + "endpoint-url");
    SecurityPolicy policy =
        read("security-policy", () -> SecurityPolicy.valueOf(string(merged, "security-policy")));
    if (!POLICIES.contains(policy)) {
      throw invalid("security-policy", "expected a supported RSA policy");
    }
    Identity identity =
        switch (string(merged, "identity.type")) {
          case "anonymous" -> new Anonymous();
          case "username" -> {
            String username = string(merged, "identity.username");
            String password = string(merged, "identity.password");
            if (username.isBlank()) throw invalid("identity.username", "must not be blank");
            if (password.isEmpty()) throw invalid("identity.password", "must not be empty");
            yield new Credentials(username, password);
          }
          default -> throw invalid("identity.type", "expected username or anonymous");
        };
    List<String> urls =
        read("discovery-url-list", () -> merged.getStringList(PREFIX + "discovery-url-list"));
    for (int i = 0; i < urls.size(); i++) {
      validateUrl(urls.get(i), PREFIX + "discovery-url-list[" + i + "]");
    }
    long request = duration(merged, "request-timeout");
    if (request > 0xffff_ffffL) throw invalid("request-timeout", "exceeds UInt32 milliseconds");
    long attempt = duration(merged, "attempt-timeout");
    if (attempt < request) throw invalid("attempt-timeout", "must be at least request-timeout");
    return Optional.of(
        new GdsRegistrationConfig(
            endpoint,
            policy,
            identity,
            List.copyOf(new LinkedHashSet<>(urls)),
            read("update-existing", () -> merged.getBoolean(PREFIX + "update-existing")),
            request,
            attempt,
            duration(merged, "retry-interval")));
  }

  static void validateUrl(String value, String key) {
    try {
      URI uri = new URI(value);
      if (!"opc.tcp".equalsIgnoreCase(uri.getScheme())
          || uri.getHost() == null
          || uri.getRawUserInfo() != null
          || uri.getRawQuery() != null
          || uri.getRawFragment() != null
          || uri.getPort() == 0
          || uri.getPort() > 65535
          || uri.getRawAuthority().endsWith(":")) {
        throw new IllegalArgumentException();
      }
    } catch (Exception e) {
      throw new IllegalArgumentException(
          key + ": expected an opc.tcp URL with host and valid optional port");
    }
  }

  private static String string(Config config, String key) {
    return read(key, () -> config.getString(PREFIX + key));
  }

  private static long duration(Config config, String key) {
    return read(
        key,
        () -> {
          Duration duration = config.getDuration(PREFIX + key);
          long millis = duration.toMillis();
          if (millis <= 0 || !duration.equals(Duration.ofMillis(millis))) {
            throw invalid(key, "expected positive whole milliseconds");
          }
          return millis;
        });
  }

  private static <T> T read(String key, Supplier<T> supplier) {
    try {
      return supplier.get();
    } catch (ConfigException | IllegalArgumentException | ArithmeticException e) {
      // ConfigException can render the offending value, including passwords. Do not retain it.
      throw invalid(key, "invalid value");
    }
  }

  private static IllegalArgumentException invalid(String key, String reason) {
    return new IllegalArgumentException(PREFIX + key + ": " + reason);
  }
}

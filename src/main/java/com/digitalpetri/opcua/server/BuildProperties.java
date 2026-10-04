package com.digitalpetri.opcua.server;

import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;
import java.util.Properties;
import org.slf4j.LoggerFactory;

/**
 * Build metadata that Maven resource filtering writes into {@code build-info.properties}.
 *
 * <p>These values used to be read from the shaded JAR's manifest, which the GraalVM native image
 * never sees: {@code native-maven-plugin} compiles from {@code target/classes} plus the dependency
 * JARs, and {@code target/classes} has no manifest. Keeping the metadata in a plain classpath
 * resource makes it available to every packaging form.
 */
final class BuildProperties {

  static final String SOFTWARE_VERSION = "software.version";
  static final String BUILD_NUMBER = "build.number";
  static final String BUILD_DATE = "build.date";

  private static final String RESOURCE = "build-info.properties";

  private static final Properties PROPERTIES = load();

  private BuildProperties() {}

  /**
   * Reads a build property.
   *
   * @param key one of {@link #SOFTWARE_VERSION}, {@link #BUILD_NUMBER}, or {@link #BUILD_DATE}.
   * @return the property value, or empty if it is absent or was never filtered.
   */
  static Optional<String> read(String key) {
    String value = PROPERTIES.getProperty(key);

    // An unfiltered resource still holds its Maven placeholder, e.g. "${timestamp}". Treating that
    // as absent is better than surfacing it verbatim in BuildInfo.
    if (value == null || value.isBlank() || value.startsWith("${")) {
      return Optional.empty();
    }

    return Optional.of(value);
  }

  private static Properties load() {
    var properties = new Properties();

    try (InputStream stream = BuildProperties.class.getResourceAsStream(RESOURCE)) {
      if (stream != null) {
        properties.load(stream);
      } else {
        LoggerFactory.getLogger(BuildProperties.class)
            .warn("{} not found on the classpath", RESOURCE);
      }
    } catch (IOException e) {
      LoggerFactory.getLogger(BuildProperties.class).warn("failed to read {}", RESOURCE, e);
    }

    return properties;
  }
}

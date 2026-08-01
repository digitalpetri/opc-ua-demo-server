package com.digitalpetri.opcua.server;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.text.SimpleDateFormat;
import java.util.Optional;
import java.util.TimeZone;
import org.junit.jupiter.api.Test;

/**
 * Guards the build metadata that populates the server's BuildInfo structure.
 *
 * <p>Reading this from a filtered classpath resource rather than the shaded JAR's manifest is what
 * lets the GraalVM native image report a real build date instead of falling back to {@code
 * DateTime.NULL_VALUE}.
 */
class BuildPropertiesTest {

  @Test
  void resourceIsPresentAndFiltered() {
    for (String key :
        new String[] {
          BuildProperties.SOFTWARE_VERSION, BuildProperties.BUILD_NUMBER, BuildProperties.BUILD_DATE
        }) {

      Optional<String> value = BuildProperties.read(key);

      assertTrue(
          value.isPresent(),
          "'%s' is missing or still holds an unfiltered Maven placeholder".formatted(key));
    }
  }

  @Test
  void buildDateParsesWithTheFormatBuildInfoUses() {
    String buildDate = BuildProperties.read(BuildProperties.BUILD_DATE).orElseThrow();

    // Must match the format OpcUaDemoServer.createBuildInfo() parses with, or the date silently
    // degrades to DateTime.NULL_VALUE.
    var dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
    dateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));

    assertDoesNotThrow(() -> dateFormat.parse(buildDate), "unparseable build date: " + buildDate);
  }
}

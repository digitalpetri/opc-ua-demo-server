package com.digitalpetri.opcua.server.namespace.demo.ctt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

class AlarmsAndConditionsFragmentTest {

  @Test
  void defaultLevelTrajectoryCompletesWithinCttFilterCaptureWindow() {
    assertEquals(
        List.of(0.0, 70.0, 90.0, 70.0, 0.0, -70.0, -90.0, -70.0),
        AlarmsAndConditionsFragment.LEVEL_TRAJECTORY);

    Config config = ConfigFactory.parseResources("default-server.conf").resolve();
    Duration dwellTime = config.getDuration("address-space.ctt.alarms-and-conditions.dwell-time");
    Duration cycleTime = AlarmsAndConditionsFragment.cycleTime(dwellTime);

    assertEquals(Duration.ofMillis(400), dwellTime);
    assertEquals(Duration.ofMillis(3200), cycleTime);
    assertTrue(cycleTime.compareTo(Duration.ofSeconds(4)) < 0);
  }
}

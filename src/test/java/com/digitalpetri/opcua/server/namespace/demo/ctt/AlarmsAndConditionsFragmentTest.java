package com.digitalpetri.opcua.server.namespace.demo.ctt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.digitalpetri.opcua.server.namespace.demo.ctt.AlarmsAndConditionsFragment.OptionalStateConfig;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

class AlarmsAndConditionsFragmentTest {

  /** The CTT project's "Alarm Cycle Time" setting, which A &amp; C Shelving Test_006 compares. */
  private static final Duration CTT_ALARM_CYCLE_TIME = Duration.ofSeconds(60);

  /**
   * The CTT maximum test time: the alarm cycle time times the AlarmTester's cycle multiplier of
   * three. A one-shot shelve has to expire inside it for A &amp; C Shelving Test_004 to observe it.
   */
  private static final Duration CTT_MAXIMUM_TEST_TIME = CTT_ALARM_CYCLE_TIME.multipliedBy(3);

  /** The TimedShelve duration several A &amp; C Shelving tests request. */
  private static final Duration CTT_TIMED_SHELVE_TIME = Duration.ofSeconds(20);

  private static final String OPTIONAL_STATE_FIXTURES =
      "address-space.ctt.alarms-and-conditions.optional-state-fixtures.";

  @Test
  void defaultLevelTrajectoryCompletesWithinCttFilterCaptureWindow() {
    assertEquals(
        List.of(0.0, 70.0, 90.0, 70.0, 0.0, -70.0, -90.0, -70.0),
        AlarmsAndConditionsFragment.LEVEL_TRAJECTORY);

    Duration dwellTime =
        defaultConfig().getDuration("address-space.ctt.alarms-and-conditions.dwell-time");
    Duration cycleTime = AlarmsAndConditionsFragment.cycleTime(dwellTime);

    assertEquals(Duration.ofMillis(400), dwellTime);
    assertEquals(Duration.ofMillis(3200), cycleTime);
    assertTrue(cycleTime.compareTo(Duration.ofSeconds(4)) < 0);
  }

  /**
   * The shipped MaxTimeShelved values are not free choices: each CTT Shelving test decides whether
   * it can run at all by comparing the alarm's MaxTimeShelved against a fixed time of its own, and
   * the two fixtures exist because no single value satisfies every comparison.
   */
  @Test
  void defaultMaxTimeShelvedValuesSatisfyTheCttShelvingPreconditions() {
    Config config = defaultConfig();

    Duration cyclingMaxTimeShelved =
        config.getDuration(OPTIONAL_STATE_FIXTURES + "shelving-cycling-max-time-shelved");
    Duration steadyMaxTimeShelved =
        config.getDuration(OPTIONAL_STATE_FIXTURES + "shelving-steady-max-time-shelved");

    // Test_006 skips an alarm whose MaxTimeShelved does not exceed the alarm cycle time.
    assertTrue(
        cyclingMaxTimeShelved.compareTo(CTT_ALARM_CYCLE_TIME) > 0,
        () ->
            "cycling MaxTimeShelved must exceed the CTT alarm cycle time: "
                + cyclingMaxTimeShelved);

    // Test_005 and Test_007 skip an alarm that cannot be shelved for their 20 s TimedShelve.
    assertTrue(
        steadyMaxTimeShelved.compareTo(CTT_TIMED_SHELVE_TIME) > 0,
        () ->
            "steady MaxTimeShelved must exceed the CTT TimedShelve time: " + steadyMaxTimeShelved);

    // Test_004 skips an alarm whose one-shot shelve would outlast the run watching for its expiry.
    assertTrue(
        steadyMaxTimeShelved.compareTo(CTT_MAXIMUM_TEST_TIME) < 0,
        () ->
            "steady MaxTimeShelved must stay inside the CTT maximum test time: "
                + steadyMaxTimeShelved);
  }

  /**
   * The two Shelving fixtures share an event type, so the CTT reaches the steady one only through
   * its chattering list. Distinct MaxTimeShelved values are what make that split worth having.
   */
  @Test
  void shelvingFixturesUseDistinctMaxTimeShelvedValues() {
    Config config = defaultConfig();

    assertNotEquals(
        config.getDuration(OPTIONAL_STATE_FIXTURES + "shelving-cycling-max-time-shelved"),
        config.getDuration(OPTIONAL_STATE_FIXTURES + "shelving-steady-max-time-shelved"));
  }

  /**
   * The steady Shelving fixture emits nothing but Severity changes, so a zero heartbeat would leave
   * it silent after its first event and every test reaching it stalled rather than failed.
   */
  @Test
  void optionalStateConfigRejectsNonPositiveDurations() {
    Duration valid = Duration.ofSeconds(1);

    assertThrows(
        IllegalArgumentException.class,
        () -> new OptionalStateConfig(Duration.ZERO, valid, valid, valid, valid));
    assertThrows(
        IllegalArgumentException.class,
        () -> new OptionalStateConfig(valid, Duration.ofSeconds(-1), valid, valid, valid));
    assertThrows(
        IllegalArgumentException.class,
        () -> new OptionalStateConfig(valid, valid, Duration.ZERO, valid, valid));
    assertThrows(
        IllegalArgumentException.class,
        () -> new OptionalStateConfig(valid, valid, valid, Duration.ZERO, valid));
    assertThrows(
        IllegalArgumentException.class,
        () -> new OptionalStateConfig(valid, valid, valid, valid, Duration.ZERO));
  }

  /**
   * The shipped configuration leaves the optional-state fixtures out of the address space. Enabling
   * them adds two alarm types the other A &amp; C conformance units enumerate but cannot drive,
   * which costs those units their recorded results.
   */
  @Test
  void optionalStateFixturesAreDisabledByDefault() {
    assertFalse(defaultConfig().getBoolean(OPTIONAL_STATE_FIXTURES + "enabled"));
  }

  private static Config defaultConfig() {
    return ConfigFactory.parseResources("default-server.conf").resolve();
  }
}

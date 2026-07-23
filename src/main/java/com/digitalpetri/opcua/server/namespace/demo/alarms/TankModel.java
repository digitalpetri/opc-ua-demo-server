package com.digitalpetri.opcua.server.namespace.demo.alarms;

import static com.digitalpetri.opcua.server.namespace.demo.alarms.AlarmNodeFactory.badValue;
import static com.digitalpetri.opcua.server.namespace.demo.alarms.AlarmNodeFactory.booleanValue;
import static com.digitalpetri.opcua.server.namespace.demo.alarms.AlarmNodeFactory.doubleValue;
import static org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.Unsigned.ushort;

import java.time.Duration;
import java.util.List;
import org.eclipse.milo.opcua.sdk.core.CefactEngineeringUnits;
import org.eclipse.milo.opcua.sdk.server.conditions.Condition;
import org.eclipse.milo.opcua.sdk.server.conditions.InstrumentDiagnosticAlarm;
import org.eclipse.milo.opcua.sdk.server.conditions.NonExclusiveDeviationAlarm;
import org.eclipse.milo.opcua.sdk.server.conditions.NonExclusiveLevelAlarm;
import org.eclipse.milo.opcua.sdk.server.conditions.NonExclusiveRateOfChangeAlarm;
import org.eclipse.milo.opcua.sdk.server.model.variables.AnalogItemTypeNode;
import org.eclipse.milo.opcua.sdk.server.nodes.UaObjectNode;
import org.eclipse.milo.opcua.sdk.server.nodes.UaVariableNode;
import org.eclipse.milo.opcua.stack.core.StatusCodes;
import org.eclipse.milo.opcua.stack.core.UaException;
import org.eclipse.milo.opcua.stack.core.types.builtin.NodeId;
import org.eclipse.milo.opcua.stack.core.types.builtin.StatusCode;
import org.eclipse.milo.opcua.stack.core.types.builtin.Variant;
import org.eclipse.milo.opcua.stack.core.types.structured.Range;

/**
 * Storage tank TK-200: a fill and draw cycle that overfills and runs low, a leak episode fast
 * enough to trip a falling rate-of-change limit, a temperature loop that wanders off its setpoint,
 * and a level transmitter that fails outright.
 *
 * <p>Covers NonExclusiveLevelAlarm, NonExclusiveRateOfChangeAlarm, NonExclusiveDeviationAlarm with
 * shelving, and InstrumentDiagnosticAlarm. The transmitter failure and the PLC communication outage
 * driven by {@link PlcModel} both demonstrate the Part 9 §5.14 underlying-system pattern: the
 * conditions fed by a failed source report a Bad Quality rather than silently holding stale state.
 */
final class TankModel implements EquipmentModel {

  static final String NAME = "TK-200";

  static final String LEVEL = "Level";
  static final String TEMPERATURE = "Temperature";
  static final String TEMPERATURE_SETPOINT = "TemperatureSetpoint";
  static final String TRANSMITTER_HEALTHY = "LevelTransmitterHealthy";
  static final String TRANSMITTER_NORMAL_STATE = "LevelTransmitterNormalState";
  static final String SIMULATION_ENABLED = "SimulationEnabled";

  static final String LEVEL_ALARM = "LevelAlarm";
  static final String LEVEL_RATE_ALARM = "LevelRateAlarm";
  static final String TEMPERATURE_ALARM = "TemperatureAlarm";
  static final String TRANSMITTER_ALARM = "LevelTransmitterAlarm";

  private static final StatusCode SENSOR_FAILURE = StatusCode.of(StatusCodes.Bad_SensorFailure);
  private static final StatusCode COMMUNICATION_ERROR =
      StatusCode.of(StatusCodes.Bad_CommunicationError);

  /** Simulated seconds per fill and draw cycle, and the phase boundaries within it. */
  private static final long CYCLE_TICKS = 200;

  private static final long FILL_TICKS = 80;
  private static final long HOLD_HIGH_TICKS = 20;
  private static final long DRAW_TICKS = 80;

  /**
   * The leak runs inside the draw phase of every {@code LEAK_CYCLE_INTERVAL}th fill-and-draw cycle.
   *
   * <p>Anchoring it to the cycle rather than to a free-running period is load-bearing: only a leak
   * on top of a draw is fast enough to cross the falling rate limit, so a leak that drifts across
   * the fill and hold phases would never trip the alarm it exists to demonstrate.
   */
  private static final long LEAK_CYCLE_INTERVAL = 3;

  private static final long LEAK_START_PHASE = FILL_TICKS + HOLD_HIGH_TICKS + 20;
  private static final long LEAK_DURATION_TICKS = 25;

  /** Simulated seconds between scripted transmitter failures, and how long each one lasts. */
  private static final long TRANSMITTER_FAILURE_PERIOD_TICKS = 500;

  private static final long TRANSMITTER_FAILURE_DURATION_TICKS = 30;

  private static final long TEMPERATURE_PERIOD_TICKS = 130;

  private static final double DEFAULT_TEMPERATURE_SETPOINT = 45.0;

  private final Noise noise = new Noise(3_000L);

  private final AnalogItemTypeNode level;
  private final AnalogItemTypeNode temperature;
  private final AnalogItemTypeNode temperatureSetpoint;
  private final UaVariableNode transmitterHealthy;
  private final UaVariableNode transmitterNormalState;
  private final UaVariableNode simulationEnabled;

  private final NonExclusiveLevelAlarm levelAlarm;
  private final NonExclusiveRateOfChangeAlarm levelRateAlarm;
  private final NonExclusiveDeviationAlarm temperatureAlarm;
  private final InstrumentDiagnosticAlarm transmitterAlarm;

  private final AlarmNodeFactory factory;

  private double levelValue = 20.0;
  private boolean transmitterFailed;
  private boolean communicationLost;

  TankModel(AlarmNodeFactory factory, UaObjectNode areaNode) throws UaException {
    this.factory = factory;

    UaObjectNode node = factory.equipmentNode(areaNode, NAME);
    NodeId id = node.getNodeId();

    level =
        factory.analogVariable(
            id, LEVEL, CefactEngineeringUnits.CODE_P1, new Range(0.0, 100.0), levelValue);
    temperature =
        factory.analogVariable(
            id,
            TEMPERATURE,
            CefactEngineeringUnits.CODE_CEL,
            new Range(0.0, 100.0),
            DEFAULT_TEMPERATURE_SETPOINT);
    temperatureSetpoint =
        factory.writableAnalogVariable(
            id,
            TEMPERATURE_SETPOINT,
            CefactEngineeringUnits.CODE_CEL,
            new Range(0.0, 100.0),
            DEFAULT_TEMPERATURE_SETPOINT);
    transmitterHealthy = factory.booleanVariable(id, TRANSMITTER_HEALTHY, true);
    transmitterNormalState = factory.booleanVariable(id, TRANSMITTER_NORMAL_STATE, true);
    simulationEnabled = factory.writableBooleanVariable(id, SIMULATION_ENABLED, true);

    levelAlarm =
        NonExclusiveLevelAlarm.create(
            factory.nodeContext(),
            builder ->
                factory
                    .condition(
                        builder,
                        node,
                        LEVEL_ALARM,
                        "Level",
                        level.getNodeId(),
                        ConditionClass.PROCESS,
                        500)
                    .highHighLimit(95.0, ushort(900))
                    .highLimit(85.0, ushort(500))
                    .lowLimit(15.0, ushort(500)));

    // Rates are percent of span per simulated second, which is what EngineeringUnits being absent
    // means on a rate-of-change alarm: the input's unit per second. A normal draw runs near
    // -1.1 %/s, so only a leak on top of one crosses the limit.
    levelRateAlarm =
        NonExclusiveRateOfChangeAlarm.create(
            factory.nodeContext(),
            builder ->
                factory
                    .condition(
                        builder,
                        node,
                        LEVEL_RATE_ALARM,
                        "Level Rate of Change",
                        level.getNodeId(),
                        ConditionClass.PROCESS,
                        700)
                    .lowLimit(-1.8, ushort(700)));

    temperatureAlarm =
        NonExclusiveDeviationAlarm.create(
            factory.nodeContext(),
            builder ->
                factory
                    .condition(
                        builder,
                        node,
                        TEMPERATURE_ALARM,
                        "Temperature Deviation",
                        temperature.getNodeId(),
                        ConditionClass.PROCESS,
                        500)
                    .setpointNode(temperatureSetpoint.getNodeId())
                    .highLimit(5.0)
                    .lowLimit(-5.0)
                    .withShelving(Duration.ofMinutes(30)));

    transmitterAlarm =
        InstrumentDiagnosticAlarm.create(
            factory.nodeContext(),
            builder ->
                factory
                    .condition(
                        builder,
                        node,
                        TRANSMITTER_ALARM,
                        "Level Transmitter Fault",
                        transmitterHealthy.getNodeId(),
                        ConditionClass.MAINTENANCE,
                        800)
                    .normalState(transmitterNormalState.getNodeId()));
  }

  @Override
  public List<Condition> conditions() {
    return List.of(levelAlarm, levelRateAlarm, temperatureAlarm, transmitterAlarm);
  }

  /**
   * Apply or clear the blast radius of a communication outage on the PLC that reads this tank.
   *
   * <p>While communication is lost the tank's conditions report Bad_CommunicationError Quality and
   * its process variables publish that status instead of stale values, and the simulation holds its
   * state: no fresh samples arrive, so no alarm evaluates.
   *
   * @param lost whether communication with the tank's I/O is lost.
   */
  void setCommunicationLost(boolean lost) {
    if (lost == communicationLost) {
      return;
    }

    communicationLost = lost;

    updateQuality();

    if (lost) {
      level.setValue(badValue(COMMUNICATION_ERROR));
      temperature.setValue(badValue(COMMUNICATION_ERROR));
    }
  }

  /**
   * Report the Quality every Condition currently deserves, derived from the two independent faults
   * that can invalidate it.
   *
   * <p>Deriving all four from the current fault state rather than writing them at each transition
   * is what keeps a recovering communication outage from clearing the Quality of a level Condition
   * whose transmitter is still failed. {@code setQuality} is a no-op when the Quality is unchanged,
   * so re-asserting the whole set generates no spurious events.
   */
  private void updateQuality() {
    StatusCode plcQuality = communicationLost ? COMMUNICATION_ERROR : StatusCode.GOOD;

    // The transmitter feeds the level readings only; the temperature loop is a separate instrument.
    StatusCode levelQuality =
        communicationLost
            ? COMMUNICATION_ERROR
            : transmitterFailed ? SENSOR_FAILURE : StatusCode.GOOD;

    levelAlarm.setQuality(levelQuality);
    levelRateAlarm.setQuality(levelQuality);
    temperatureAlarm.setQuality(plcQuality);
    transmitterAlarm.setQuality(plcQuality);
  }

  @Override
  public void tick(long tick) {
    if (!booleanValue(simulationEnabled, true) || communicationLost) {
      return;
    }

    updateTransmitterHealth(tick);
    updateLevel(tick);
    updateTemperature(tick);
  }

  private void updateTransmitterHealth(long tick) {
    boolean failed =
        EquipmentModel.episodeActive(
            tick, TRANSMITTER_FAILURE_PERIOD_TICKS, TRANSMITTER_FAILURE_DURATION_TICKS);

    if (failed != transmitterFailed) {
      transmitterFailed = failed;

      factory.setValue(transmitterHealthy, Variant.ofBoolean(!failed));
      transmitterAlarm.evaluate(!failed, booleanValue(transmitterNormalState, true));

      updateQuality();
    }
  }

  private void updateLevel(long tick) {
    if (transmitterFailed) {
      level.setValue(badValue(SENSOR_FAILURE));
      return;
    }

    long phase = tick % CYCLE_TICKS;
    double nominalRate = nominalRate(phase);

    boolean leaking =
        (tick / CYCLE_TICKS) % LEAK_CYCLE_INTERVAL == LEAK_CYCLE_INTERVAL - 1
            && phase >= LEAK_START_PHASE
            && phase < LEAK_START_PHASE + LEAK_DURATION_TICKS;
    double rate = nominalRate + (leaking ? -1.5 : 0.0) + noise.sample(0.05);

    double previousLevel = levelValue;
    levelValue = Math.clamp(levelValue + rate, 0.0, 100.0);

    factory.publish(level, levelValue, levelAlarm::evaluate);

    // The rate alarm watches the per-tick delta, which the clamp above may have shortened, so it
    // has to be measured from the published level rather than taken from the nominal rate.
    levelRateAlarm.evaluate(levelValue - previousLevel);
  }

  /** The nominal level rate over one cycle: filling, held high, drawing down, then held low. */
  private static double nominalRate(long phase) {
    if (phase < FILL_TICKS) {
      return 1.0;
    }
    if (phase < FILL_TICKS + HOLD_HIGH_TICKS) {
      return 0.0;
    }
    if (phase < FILL_TICKS + HOLD_HIGH_TICKS + DRAW_TICKS) {
      return -1.1;
    }

    return 0.0;
  }

  private void updateTemperature(long tick) {
    double setpoint = doubleValue(temperatureSetpoint, DEFAULT_TEMPERATURE_SETPOINT);
    double value =
        Math.clamp(
            DEFAULT_TEMPERATURE_SETPOINT
                + 8.0 * Math.sin(2.0 * Math.PI * tick / TEMPERATURE_PERIOD_TICKS)
                + noise.sample(0.1),
            0.0,
            100.0);

    // A deviation alarm watches the distance from setpoint, not the sample itself.
    factory.setValue(temperature, Variant.ofDouble(value));
    temperatureAlarm.evaluate(value - setpoint);
  }
}

package com.digitalpetri.opcua.server.namespace.demo.alarms;

import static com.digitalpetri.opcua.server.namespace.demo.alarms.AlarmNodeFactory.booleanValue;
import static com.digitalpetri.opcua.server.namespace.demo.alarms.AlarmNodeFactory.doubleValue;
import static org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.Unsigned.ushort;

import java.util.List;
import org.eclipse.milo.opcua.sdk.core.CefactEngineeringUnits;
import org.eclipse.milo.opcua.sdk.server.conditions.Condition;
import org.eclipse.milo.opcua.sdk.server.conditions.ExclusiveDeviationAlarm;
import org.eclipse.milo.opcua.sdk.server.conditions.ExclusiveLevelAlarm;
import org.eclipse.milo.opcua.sdk.server.conditions.NonExclusiveLevelAlarm;
import org.eclipse.milo.opcua.sdk.server.conditions.OffNormalAlarm;
import org.eclipse.milo.opcua.sdk.server.model.variables.AnalogItemTypeNode;
import org.eclipse.milo.opcua.sdk.server.nodes.UaObjectNode;
import org.eclipse.milo.opcua.sdk.server.nodes.UaVariableNode;
import org.eclipse.milo.opcua.stack.core.UaException;
import org.eclipse.milo.opcua.stack.core.types.builtin.NodeId;
import org.eclipse.milo.opcua.stack.core.types.builtin.Variant;
import org.eclipse.milo.opcua.stack.core.types.structured.Range;

/**
 * Boiler B-100: a fired steam boiler whose firing cycle repeatedly drives drum level, steam
 * pressure, and outlet temperature across their alarm limits, with an occasional pilot flame-out.
 *
 * <p>Covers NonExclusiveLevelAlarm (four limits with a raised LowLow severity and Confirm),
 * ExclusiveLevelAlarm (per-limit severities plus deadband hysteresis), ExclusiveDeviationAlarm
 * (against a writable setpoint), and OffNormalAlarm.
 */
final class BoilerModel implements EquipmentModel {

  static final String NAME = "B-100";

  static final String DRUM_LEVEL = "DrumLevel";
  static final String STEAM_PRESSURE = "SteamPressure";
  static final String OUTLET_TEMPERATURE = "OutletTemperature";
  static final String TEMPERATURE_SETPOINT = "TemperatureSetpoint";
  static final String PILOT_FLAME = "PilotFlame";
  static final String PILOT_FLAME_NORMAL_STATE = "PilotFlameNormalState";
  static final String SIMULATION_ENABLED = "SimulationEnabled";

  static final String DRUM_LEVEL_ALARM = "DrumLevelAlarm";
  static final String STEAM_PRESSURE_ALARM = "SteamPressureAlarm";
  static final String OUTLET_TEMPERATURE_ALARM = "OutletTemperatureAlarm";
  static final String PILOT_FLAME_ALARM = "PilotFlameAlarm";

  /** Simulated seconds per firing cycle: ramp up, hold, ramp down. */
  private static final long FIRING_CYCLE_TICKS = 150;

  private static final long RAMP_UP_TICKS = 60;
  private static final long HOLD_TICKS = 30;

  /** Simulated seconds between scripted pilot flame-outs, and how long each one lasts. */
  private static final long FLAME_OUT_PERIOD_TICKS = 370;

  private static final long FLAME_OUT_DURATION_TICKS = 25;

  private static final double DEFAULT_TEMPERATURE_SETPOINT = 300.0;

  private final Noise noise = new Noise(1_000L);

  private final AnalogItemTypeNode drumLevel;
  private final AnalogItemTypeNode steamPressure;
  private final AnalogItemTypeNode outletTemperature;
  private final AnalogItemTypeNode temperatureSetpoint;
  private final UaVariableNode pilotFlame;
  private final UaVariableNode pilotFlameNormalState;
  private final UaVariableNode simulationEnabled;

  private final NonExclusiveLevelAlarm drumLevelAlarm;
  private final ExclusiveLevelAlarm steamPressureAlarm;
  private final ExclusiveDeviationAlarm outletTemperatureAlarm;
  private final OffNormalAlarm pilotFlameAlarm;

  private final AlarmNodeFactory factory;

  BoilerModel(AlarmNodeFactory factory, UaObjectNode areaNode) throws UaException {
    this.factory = factory;

    UaObjectNode node = factory.equipmentNode(areaNode, NAME);
    NodeId id = node.getNodeId();

    drumLevel =
        factory.analogVariable(
            id, DRUM_LEVEL, CefactEngineeringUnits.CODE_P1, new Range(0.0, 100.0), 50.0);
    steamPressure =
        factory.analogVariable(
            id, STEAM_PRESSURE, CefactEngineeringUnits.CODE_BAR, new Range(0.0, 50.0), 30.0);
    outletTemperature =
        factory.analogVariable(
            id,
            OUTLET_TEMPERATURE,
            CefactEngineeringUnits.CODE_CEL,
            new Range(0.0, 400.0),
            DEFAULT_TEMPERATURE_SETPOINT);
    temperatureSetpoint =
        factory.writableAnalogVariable(
            id,
            TEMPERATURE_SETPOINT,
            CefactEngineeringUnits.CODE_CEL,
            new Range(0.0, 400.0),
            DEFAULT_TEMPERATURE_SETPOINT);
    pilotFlame = factory.booleanVariable(id, PILOT_FLAME, true);
    pilotFlameNormalState = factory.booleanVariable(id, PILOT_FLAME_NORMAL_STATE, true);
    simulationEnabled = factory.writableBooleanVariable(id, SIMULATION_ENABLED, true);

    drumLevelAlarm =
        NonExclusiveLevelAlarm.create(
            factory.nodeContext(),
            builder ->
                factory
                    .condition(
                        builder,
                        node,
                        DRUM_LEVEL_ALARM,
                        "Drum Level",
                        drumLevel.getNodeId(),
                        ConditionClass.PROCESS,
                        500)
                    .highHighLimit(90.0, ushort(700))
                    .highLimit(80.0, ushort(500))
                    .lowLimit(20.0, ushort(500))
                    .lowLowLimit(10.0, ushort(900))
                    .withConfirm());

    steamPressureAlarm =
        ExclusiveLevelAlarm.create(
            factory.nodeContext(),
            builder ->
                factory
                    .condition(
                        builder,
                        node,
                        STEAM_PRESSURE_ALARM,
                        "Steam Pressure",
                        steamPressure.getNodeId(),
                        ConditionClass.PROCESS,
                        500)
                    .highLimit(42.0, ushort(500))
                    .highHighLimit(45.0, ushort(800))
                    .highDeadband(0.5)
                    .highHighDeadband(0.5));

    outletTemperatureAlarm =
        ExclusiveDeviationAlarm.create(
            factory.nodeContext(),
            builder ->
                factory
                    .condition(
                        builder,
                        node,
                        OUTLET_TEMPERATURE_ALARM,
                        "Outlet Temperature Deviation",
                        outletTemperature.getNodeId(),
                        ConditionClass.PROCESS,
                        500)
                    .setpointNode(temperatureSetpoint.getNodeId())
                    .highLimit(15.0)
                    .lowLimit(-15.0));

    pilotFlameAlarm =
        OffNormalAlarm.create(
            factory.nodeContext(),
            builder ->
                factory
                    .condition(
                        builder,
                        node,
                        PILOT_FLAME_ALARM,
                        "Pilot Flame",
                        pilotFlame.getNodeId(),
                        ConditionClass.PROCESS,
                        800)
                    .normalState(pilotFlameNormalState.getNodeId()));
  }

  @Override
  public List<Condition> conditions() {
    return List.of(drumLevelAlarm, steamPressureAlarm, outletTemperatureAlarm, pilotFlameAlarm);
  }

  @Override
  public void tick(long tick) {
    if (!booleanValue(simulationEnabled, true)) {
      return;
    }

    long phase = tick % FIRING_CYCLE_TICKS;
    double firingRate = firingRate(phase);

    // Shrink and swell: the drum level sweeps its full range once per firing cycle, crossing every
    // configured limit in both directions.
    double level =
        Math.clamp(
            50.0 + 45.0 * Math.sin(2.0 * Math.PI * phase / FIRING_CYCLE_TICKS) + noise.sample(0.4),
            0.0,
            100.0);
    factory.publish(drumLevel, level, drumLevelAlarm::evaluate);

    // 30..48 bar: crosses High (42) and HighHigh (45) near the top of every firing ramp.
    double pressure = Math.clamp(30.0 + 18.0 * firingRate + noise.sample(0.15), 0.0, 50.0);
    factory.publish(steamPressure, pressure, steamPressureAlarm::evaluate);

    // 260..330 C against a setpoint the operator can move: the deviation sweeps -40..+30. The alarm
    // watches the deviation, so what it evaluates is not the published sample itself.
    double temperature = Math.clamp(260.0 + 70.0 * firingRate + noise.sample(0.3), 0.0, 400.0);
    double setpoint = doubleValue(temperatureSetpoint, DEFAULT_TEMPERATURE_SETPOINT);
    factory.setValue(outletTemperature, Variant.ofDouble(temperature));
    outletTemperatureAlarm.evaluate(temperature - setpoint);

    boolean flame =
        !EquipmentModel.episodeActive(tick, FLAME_OUT_PERIOD_TICKS, FLAME_OUT_DURATION_TICKS);
    factory.setValue(pilotFlame, Variant.ofBoolean(flame));
    pilotFlameAlarm.evaluate(flame, booleanValue(pilotFlameNormalState, true));
  }

  /** The firing rate over one cycle: ramping up, held at full fire, then ramping back down. */
  private static double firingRate(long phase) {
    if (phase < RAMP_UP_TICKS) {
      return (double) phase / RAMP_UP_TICKS;
    }
    if (phase < RAMP_UP_TICKS + HOLD_TICKS) {
      return 1.0;
    }

    long rampDownTicks = FIRING_CYCLE_TICKS - RAMP_UP_TICKS - HOLD_TICKS;
    return 1.0 - (double) (phase - RAMP_UP_TICKS - HOLD_TICKS) / rampDownTicks;
  }
}

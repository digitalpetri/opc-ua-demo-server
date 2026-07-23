package com.digitalpetri.opcua.server.namespace.demo.alarms;

import static com.digitalpetri.opcua.server.namespace.demo.alarms.AlarmNodeFactory.booleanValue;
import static org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.Unsigned.ushort;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.eclipse.milo.opcua.sdk.core.CefactEngineeringUnits;
import org.eclipse.milo.opcua.sdk.server.Session;
import org.eclipse.milo.opcua.sdk.server.conditions.Condition;
import org.eclipse.milo.opcua.sdk.server.conditions.ConditionBranch;
import org.eclipse.milo.opcua.sdk.server.conditions.ConditionMethodInterceptor;
import org.eclipse.milo.opcua.sdk.server.conditions.DiscreteAlarm;
import org.eclipse.milo.opcua.sdk.server.conditions.ExclusiveLevelAlarm;
import org.eclipse.milo.opcua.sdk.server.conditions.ExclusiveRateOfChangeAlarm;
import org.eclipse.milo.opcua.sdk.server.conditions.NonExclusiveLevelAlarm;
import org.eclipse.milo.opcua.sdk.server.conditions.TripAlarm;
import org.eclipse.milo.opcua.sdk.server.methods.AbstractMethodInvocationHandler.InvocationContext;
import org.eclipse.milo.opcua.sdk.server.model.variables.AnalogItemTypeNode;
import org.eclipse.milo.opcua.sdk.server.nodes.UaObjectNode;
import org.eclipse.milo.opcua.sdk.server.nodes.UaVariableNode;
import org.eclipse.milo.opcua.stack.core.UaException;
import org.eclipse.milo.opcua.stack.core.types.builtin.LocalizedText;
import org.eclipse.milo.opcua.stack.core.types.builtin.NodeId;
import org.eclipse.milo.opcua.stack.core.types.builtin.Variant;
import org.eclipse.milo.opcua.stack.core.types.structured.Range;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Boiler feedwater pump P-101 and its motor M-101, running a coherent degradation story: start,
 * steady running, a slow mechanical degradation that raises vibration and bearing temperature past
 * their limits, an overload trip that latches the machine out, and a restart once the trip is
 * reset.
 *
 * <p>Covers ExclusiveLevelAlarm, NonExclusiveLevelAlarm with shelving, ExclusiveRateOfChangeAlarm,
 * TripAlarm (latched, with a method interceptor logging operator actions), and DiscreteAlarm.
 */
final class PumpModel implements EquipmentModel {

  static final String NAME = "P-101";

  static final String BEARING_TEMPERATURE = "BearingTemperature";
  static final String VIBRATION = "Vibration";
  static final String WINDING_TEMPERATURE = "WindingTemperature";
  static final String OVERLOAD_TRIP = "OverloadTrip";
  static final String RESET_TRIP = "ResetTrip";
  static final String FORCE_TRIP = "ForceTrip";
  static final String RUNNING_STATE = "RunningState";
  static final String COMMANDED_STATE = "CommandedState";
  static final String ACK_LOG = "AckLog";
  static final String SIMULATION_ENABLED = "SimulationEnabled";

  static final String BEARING_TEMPERATURE_ALARM = "BearingTemperatureAlarm";
  static final String VIBRATION_ALARM = "VibrationAlarm";
  static final String WINDING_TEMPERATURE_ALARM = "WindingTemperatureAlarm";
  static final String OVERLOAD_TRIP_ALARM = "OverloadTripAlarm";
  static final String COMMANDED_STATE_ALARM = "CommandedStateAlarm";

  /** The stage of the pump's story loop; each tick is one simulated second. */
  private enum Stage {
    /** Motor energized; windings heat quickly enough to trip the rate-of-change alarm. */
    STARTING(20),

    /** Healthy operation: vibration and temperatures hold steady. */
    RUNNING(100),

    /** Mechanical degradation: vibration and bearing temperature climb past both limits. */
    DEGRADING(50),

    /**
     * Overload trip latched in; the machine coasts down and waits for a reset. Its duration is how
     * long the simulation waits before clearing the trip on the operator's behalf, so the story
     * keeps looping on an unattended demo server; writing {@code ResetTrip} ends it immediately.
     */
    TRIPPED(60);

    final long durationTicks;

    Stage(long durationTicks) {
      this.durationTicks = durationTicks;
    }

    /** The stage that follows this one, closing the loop from a cleared trip back to a restart. */
    Stage next() {
      return switch (this) {
        case STARTING -> RUNNING;
        case RUNNING -> DEGRADING;
        case DEGRADING -> TRIPPED;
        case TRIPPED -> STARTING;
      };
    }
  }

  /** Simulated seconds between energizing the motor and the running feedback coming back. */
  private static final long START_DELAY_TICKS = 10;

  private static final double WINDING_AMBIENT = 65.0;

  /** How many operator actions the AckLog retains. */
  private static final int ACK_LOG_ENTRIES = 20;

  private final Noise noise = new Noise(2_000L);

  private final AnalogItemTypeNode bearingTemperature;
  private final AnalogItemTypeNode vibration;
  private final AnalogItemTypeNode windingTemperature;
  private final UaVariableNode overloadTrip;
  private final UaVariableNode resetTrip;
  private final UaVariableNode forceTrip;
  private final UaVariableNode runningState;
  private final UaVariableNode commandedState;
  private final UaVariableNode ackLog;
  private final UaVariableNode simulationEnabled;

  private final ExclusiveLevelAlarm bearingTemperatureAlarm;
  private final NonExclusiveLevelAlarm vibrationAlarm;
  private final ExclusiveRateOfChangeAlarm windingTemperatureAlarm;
  private final TripAlarm overloadTripAlarm;
  private final DiscreteAlarm commandedStateAlarm;

  private final AlarmNodeFactory factory;

  private Stage stage = Stage.STARTING;
  private long stageTick;
  private double winding = WINDING_AMBIENT;

  PumpModel(AlarmNodeFactory factory, UaObjectNode areaNode) throws UaException {
    this.factory = factory;

    UaObjectNode node = factory.equipmentNode(areaNode, NAME);
    NodeId id = node.getNodeId();

    bearingTemperature =
        factory.analogVariable(
            id, BEARING_TEMPERATURE, CefactEngineeringUnits.CODE_CEL, new Range(0.0, 150.0), 45.0);
    vibration =
        factory.analogVariable(
            id, VIBRATION, CefactEngineeringUnits.CODE_C16, new Range(0.0, 20.0), 1.5);
    windingTemperature =
        factory.analogVariable(
            id,
            WINDING_TEMPERATURE,
            CefactEngineeringUnits.CODE_CEL,
            new Range(0.0, 200.0),
            WINDING_AMBIENT);
    overloadTrip = factory.booleanVariable(id, OVERLOAD_TRIP, false);
    resetTrip = factory.writableBooleanVariable(id, RESET_TRIP, false);
    forceTrip = factory.writableBooleanVariable(id, FORCE_TRIP, false);
    runningState = factory.booleanVariable(id, RUNNING_STATE, false);
    commandedState = factory.writableBooleanVariable(id, COMMANDED_STATE, true);
    ackLog = factory.stringVariable(id, ACK_LOG, "");
    simulationEnabled = factory.writableBooleanVariable(id, SIMULATION_ENABLED, true);

    bearingTemperatureAlarm =
        ExclusiveLevelAlarm.create(
            factory.nodeContext(),
            builder ->
                factory
                    .condition(
                        builder,
                        node,
                        BEARING_TEMPERATURE_ALARM,
                        "Bearing Temperature",
                        bearingTemperature.getNodeId(),
                        ConditionClass.PROCESS,
                        500)
                    .highLimit(80.0, ushort(500))
                    .highHighLimit(95.0, ushort(800)));

    // ISO 10816 zone boundaries for a small pump set: 4.5 mm/s leaves the acceptable zone, 7.1 mm/s
    // is unacceptable. Shelving lets an operator silence a known-bad bearing during a planned run.
    vibrationAlarm =
        NonExclusiveLevelAlarm.create(
            factory.nodeContext(),
            builder ->
                factory
                    .condition(
                        builder,
                        node,
                        VIBRATION_ALARM,
                        "Vibration",
                        vibration.getNodeId(),
                        ConditionClass.MAINTENANCE,
                        500)
                    .highLimit(4.5, ushort(500))
                    .highHighLimit(7.1, ushort(800))
                    .withShelving(Duration.ofMinutes(30)));

    windingTemperatureAlarm =
        ExclusiveRateOfChangeAlarm.create(
            factory.nodeContext(),
            builder ->
                factory
                    .condition(
                        builder,
                        node,
                        WINDING_TEMPERATURE_ALARM,
                        "Winding Temperature Rate of Change",
                        windingTemperature.getNodeId(),
                        ConditionClass.PROCESS,
                        600)
                    .highLimit(5.0)
                    .engineeringUnits(CefactEngineeringUnits.CODE_H13));

    overloadTripAlarm =
        TripAlarm.create(
            factory.nodeContext(),
            builder ->
                factory
                    .condition(
                        builder,
                        node,
                        OVERLOAD_TRIP_ALARM,
                        "Motor M-101 Overload Trip",
                        overloadTrip.getNodeId(),
                        ConditionClass.SYSTEM,
                        800)
                    .withConfirm());

    overloadTripAlarm.setInterceptor(new AckLogInterceptor());

    commandedStateAlarm =
        DiscreteAlarm.create(
            factory.nodeContext(),
            builder ->
                factory.condition(
                    builder,
                    node,
                    COMMANDED_STATE_ALARM,
                    "Commanded State Mismatch",
                    runningState.getNodeId(),
                    ConditionClass.SYSTEM,
                    400));
  }

  @Override
  public List<Condition> conditions() {
    return List.of(
        bearingTemperatureAlarm,
        vibrationAlarm,
        windingTemperatureAlarm,
        overloadTripAlarm,
        commandedStateAlarm);
  }

  @Override
  public void tick(long tick) {
    if (!booleanValue(simulationEnabled, true)) {
      return;
    }

    advanceStage();

    // The start delay belongs to the STARTING stage alone. Gating on stageTick across every stage
    // would restart it at each stage change, stopping a machine that is already running.
    boolean withinStartDelay = stage == Stage.STARTING && stageTick < START_DELAY_TICKS;
    boolean running = stage != Stage.TRIPPED && !withinStartDelay;
    double progress = stageProgress();

    double vibrationValue =
        switch (stage) {
          case STARTING -> 1.5 + 0.5 * progress;
          case RUNNING -> 2.0;
          case DEGRADING -> 2.0 + 6.5 * progress;
          case TRIPPED -> 8.5 * Math.exp(-progress * 6.0);
        };
    double bearingValue =
        switch (stage) {
          case STARTING -> 40.0 + 15.0 * progress;
          case RUNNING -> 55.0;
          case DEGRADING -> 55.0 + 43.0 * progress;
          case TRIPPED -> 40.0 + 58.0 * Math.exp(-progress * 4.0);
        };

    // Windings heat fast on start (about 12 C/min, past the 5 C/min limit), drift while running,
    // heat again as the motor labors through the degradation, and cool once tripped.
    double windingTarget =
        switch (stage) {
          case STARTING -> WINDING_AMBIENT + 4.0 * progress;
          case RUNNING -> WINDING_AMBIENT + 7.0;
          case DEGRADING -> WINDING_AMBIENT + 7.0 + 13.0 * progress;
          case TRIPPED -> WINDING_AMBIENT;
        };

    double previousWinding = winding;
    winding = Math.clamp(windingTarget + noise.sample(0.01), 0.0, 200.0);

    // One tick is one simulated second, so the rate in C/min is the per-tick delta times 60. That
    // keeps the reported rate physically sensible at any configured wall-clock tick interval.
    double windingRate = (winding - previousWinding) * 60.0;

    factory.publish(
        vibration,
        Math.clamp(vibrationValue + noise.sample(0.05), 0.0, 20.0),
        vibrationAlarm::evaluate);
    factory.publish(
        bearingTemperature,
        Math.clamp(bearingValue + noise.sample(0.1), 0.0, 150.0),
        bearingTemperatureAlarm::evaluate);

    // The rate-of-change alarm watches the rate, not the temperature, so the published sample and
    // the evaluated one legitimately differ here.
    factory.setValue(windingTemperature, Variant.ofDouble(winding));
    windingTemperatureAlarm.evaluate(windingRate);

    factory.setValue(runningState, Variant.ofBoolean(running));
    factory.setValue(overloadTrip, Variant.ofBoolean(stage == Stage.TRIPPED));

    // Suppress the mismatch alarm while the motor is still within its start delay: the running
    // feedback legitimately lags the command there.
    boolean commanded = booleanValue(commandedState, true);
    commandedStateAlarm.setActive(!withinStartDelay && commanded != running);

    stageTick++;
  }

  private void advanceStage() {
    if (consumeOperatorRequest(forceTrip)) {
      enterStage(Stage.TRIPPED);
      return;
    }

    // Every stage ends when its duration elapses. A latched trip additionally ends the moment an
    // operator writes ResetTrip, which is the only way out that is not just the clock running down.
    boolean resetRequested = stage == Stage.TRIPPED && consumeOperatorRequest(resetTrip);

    if (resetRequested || stageTick >= stage.durationTicks) {
      enterStage(stage.next());
    }
  }

  /**
   * Read a momentary operator request, clearing it so the write acts once rather than latching the
   * simulation into the requested state.
   */
  private boolean consumeOperatorRequest(UaVariableNode request) {
    if (!booleanValue(request, false)) {
      return false;
    }

    factory.setValue(request, Variant.ofBoolean(false));

    return true;
  }

  private void enterStage(Stage next) {
    stage = next;
    stageTick = 0;

    // The trip latches: while TRIPPED it stays active regardless of the process values falling back
    // below their limits, and it clears only on leaving the stage.
    overloadTripAlarm.setActive(next == Stage.TRIPPED);
  }

  private double stageProgress() {
    return Math.min(1.0, (double) stageTick / stage.durationTicks);
  }

  /**
   * Appends the acknowledging or confirming client's user id to the pump's AckLog Variable, then
   * lets the SDK apply its default semantics. A real system would write an audit trail here.
   */
  private class AckLogInterceptor implements ConditionMethodInterceptor {

    @Override
    @NullMarked
    public Outcome onAcknowledge(
        InvocationContext context, ConditionBranch branch, @Nullable LocalizedText comment) {

      append("Acknowledge", context);
      return Outcome.PROCEED;
    }

    @Override
    @NullMarked
    public Outcome onConfirm(
        InvocationContext context, ConditionBranch branch, @Nullable LocalizedText comment) {

      append("Confirm", context);
      return Outcome.PROCEED;
    }

    private void append(String method, InvocationContext context) {
      String user = context.getSession().map(Session::getClientUserId).orElse("anonymous");
      String previous =
          ackLog.getValue().getValue().getValue() instanceof String value ? value : "";

      // Bounded: an unattended demo server acknowledges this alarm indefinitely, and an
      // append-only String would grow without limit and be republished in full every time.
      var entries = new ArrayList<String>();
      if (!previous.isEmpty()) {
        entries.addAll(List.of(previous.split(";", -1)));
      }
      entries.add("%s:%s".formatted(method, user));

      List<String> retained =
          entries.size() > ACK_LOG_ENTRIES
              ? entries.subList(entries.size() - ACK_LOG_ENTRIES, entries.size())
              : entries;

      factory.setValue(ackLog, Variant.ofString(String.join(";", retained)));
    }
  }
}

package com.digitalpetri.opcua.server.namespace.demo.alarms;

import static com.digitalpetri.opcua.server.namespace.demo.alarms.AlarmNodeFactory.booleanValue;

import java.util.List;
import org.eclipse.milo.opcua.sdk.server.conditions.Condition;
import org.eclipse.milo.opcua.sdk.server.conditions.OffNormalAlarm;
import org.eclipse.milo.opcua.sdk.server.conditions.SystemDiagnosticAlarm;
import org.eclipse.milo.opcua.sdk.server.conditions.SystemOffNormalAlarm;
import org.eclipse.milo.opcua.sdk.server.nodes.UaObjectNode;
import org.eclipse.milo.opcua.sdk.server.nodes.UaVariableNode;
import org.eclipse.milo.opcua.stack.core.UaException;
import org.eclipse.milo.opcua.stack.core.types.builtin.NodeId;
import org.eclipse.milo.opcua.stack.core.types.builtin.Variant;

/**
 * PLC-01, the controller and remote I/O rack serving the tank farm: it periodically loses
 * communication with its remote I/O, and rarely faults its own CPU.
 *
 * <p>Covers SystemOffNormalAlarm and SystemDiagnosticAlarm, and drives the Part 9 §5.14
 * underlying-system pattern across an equipment boundary: while communication is down, every
 * condition on {@link TankModel} reports Bad_CommunicationError Quality and the tank's process
 * variables publish that status rather than stale values.
 */
final class PlcModel implements EquipmentModel {

  static final String NAME = "PLC-01";

  static final String COMMS_HEALTHY = "CommunicationHealthy";
  static final String FORCE_COMMS_LOSS = "ForceCommsLoss";
  static final String CPU_HEALTHY = "CpuHealthy";
  static final String HEALTHY_NORMAL_STATE = "HealthyNormalState";
  static final String SIMULATION_ENABLED = "SimulationEnabled";

  static final String COMMS_ALARM = "CommunicationAlarm";
  static final String CPU_ALARM = "CpuDiagnosticAlarm";

  /** Simulated seconds between remote I/O dropouts, and how long each one lasts. */
  private static final long COMMS_PERIOD_TICKS = 420;

  private static final long COMMS_OUTAGE_TICKS = 20;

  /** Simulated seconds between CPU fault episodes, and how long each one lasts. */
  private static final long CPU_FAULT_PERIOD_TICKS = 1800;

  private static final long CPU_FAULT_TICKS = 45;

  private final UaVariableNode commsHealthy;
  private final UaVariableNode forceCommsLoss;
  private final UaVariableNode cpuHealthy;
  private final UaVariableNode healthyNormalState;
  private final UaVariableNode simulationEnabled;

  private final SystemOffNormalAlarm commsAlarm;
  private final SystemDiagnosticAlarm cpuAlarm;

  private final AlarmNodeFactory factory;
  private final TankModel tank;

  private boolean commsLost;
  private boolean cpuFaulted;

  PlcModel(AlarmNodeFactory factory, UaObjectNode areaNode, TankModel tank) throws UaException {
    this.factory = factory;
    this.tank = tank;

    UaObjectNode node = factory.equipmentNode(areaNode, NAME);
    NodeId id = node.getNodeId();

    commsHealthy = factory.booleanVariable(id, COMMS_HEALTHY, true);
    forceCommsLoss = factory.writableBooleanVariable(id, FORCE_COMMS_LOSS, false);
    cpuHealthy = factory.booleanVariable(id, CPU_HEALTHY, true);
    healthyNormalState = factory.booleanVariable(id, HEALTHY_NORMAL_STATE, true);
    simulationEnabled = factory.writableBooleanVariable(id, SIMULATION_ENABLED, true);

    commsAlarm =
        SystemOffNormalAlarm.create(
            factory.nodeContext(),
            builder ->
                factory
                    .condition(
                        builder,
                        node,
                        COMMS_ALARM,
                        "Remote I/O Communication Lost",
                        commsHealthy.getNodeId(),
                        ConditionClass.SYSTEM,
                        900)
                    .normalState(healthyNormalState.getNodeId()));

    cpuAlarm =
        SystemDiagnosticAlarm.create(
            factory.nodeContext(),
            builder ->
                factory
                    .condition(
                        builder,
                        node,
                        CPU_ALARM,
                        "CPU Fault",
                        cpuHealthy.getNodeId(),
                        ConditionClass.SYSTEM,
                        800)
                    .normalState(healthyNormalState.getNodeId()));
  }

  @Override
  public List<Condition> conditions() {
    return List.of(commsAlarm, cpuAlarm);
  }

  @Override
  public void tick(long tick) {
    if (!booleanValue(simulationEnabled, true)) {
      return;
    }

    boolean lost =
        booleanValue(forceCommsLoss, false)
            || EquipmentModel.episodeActive(tick, COMMS_PERIOD_TICKS, COMMS_OUTAGE_TICKS);

    if (lost != commsLost) {
      commsLost = lost;
      reportHealth(commsHealthy, commsAlarm, !lost);

      // The tank is ticked before the PLC, so it picks the new state up on the next tick: an
      // outage costs the tank one stale sample, and recovery one held one.
      tank.setCommunicationLost(lost);
    }

    boolean faulted = EquipmentModel.episodeActive(tick, CPU_FAULT_PERIOD_TICKS, CPU_FAULT_TICKS);

    if (faulted != cpuFaulted) {
      cpuFaulted = faulted;
      reportHealth(cpuHealthy, cpuAlarm, !faulted);
    }
  }

  private void reportHealth(UaVariableNode healthy, OffNormalAlarm alarm, boolean isHealthy) {
    factory.setValue(healthy, Variant.ofBoolean(isHealthy));
    alarm.evaluate(isHealthy, booleanValue(healthyNormalState, true));
  }
}

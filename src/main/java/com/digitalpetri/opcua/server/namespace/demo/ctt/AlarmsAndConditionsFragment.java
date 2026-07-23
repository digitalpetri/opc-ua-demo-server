package com.digitalpetri.opcua.server.namespace.demo.ctt;

import static org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.Unsigned.ushort;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import org.eclipse.milo.opcua.sdk.core.AccessLevel;
import org.eclipse.milo.opcua.sdk.core.Reference;
import org.eclipse.milo.opcua.sdk.core.Reference.Direction;
import org.eclipse.milo.opcua.sdk.core.ValueRanks;
import org.eclipse.milo.opcua.sdk.server.AddressSpaceComposite;
import org.eclipse.milo.opcua.sdk.server.AddressSpaceFilter;
import org.eclipse.milo.opcua.sdk.server.ManagedAddressSpaceFragmentWithLifecycle;
import org.eclipse.milo.opcua.sdk.server.OpcUaServer;
import org.eclipse.milo.opcua.sdk.server.SimpleAddressSpaceFilter;
import org.eclipse.milo.opcua.sdk.server.conditions.Condition;
import org.eclipse.milo.opcua.sdk.server.conditions.ConditionBuilder;
import org.eclipse.milo.opcua.sdk.server.conditions.ExclusiveLevelAlarm;
import org.eclipse.milo.opcua.sdk.server.conditions.ExclusiveLimitAlarm;
import org.eclipse.milo.opcua.sdk.server.conditions.NonExclusiveLevelAlarm;
import org.eclipse.milo.opcua.sdk.server.conditions.NonExclusiveLimitAlarm;
import org.eclipse.milo.opcua.sdk.server.conditions.OffNormalAlarm;
import org.eclipse.milo.opcua.sdk.server.items.DataItem;
import org.eclipse.milo.opcua.sdk.server.items.MonitoredItem;
import org.eclipse.milo.opcua.sdk.server.nodes.UaFolderNode;
import org.eclipse.milo.opcua.sdk.server.nodes.UaNode;
import org.eclipse.milo.opcua.sdk.server.nodes.UaObjectNode;
import org.eclipse.milo.opcua.sdk.server.nodes.UaObjectNode.UaObjectNodeBuilder;
import org.eclipse.milo.opcua.sdk.server.nodes.UaVariableNode;
import org.eclipse.milo.opcua.sdk.server.nodes.UaVariableNode.UaVariableNodeBuilder;
import org.eclipse.milo.opcua.sdk.server.util.SubscriptionModel;
import org.eclipse.milo.opcua.stack.core.NodeIds;
import org.eclipse.milo.opcua.stack.core.ReferenceTypes;
import org.eclipse.milo.opcua.stack.core.UaException;
import org.eclipse.milo.opcua.stack.core.types.builtin.DataValue;
import org.eclipse.milo.opcua.stack.core.types.builtin.DateTime;
import org.eclipse.milo.opcua.stack.core.types.builtin.LocalizedText;
import org.eclipse.milo.opcua.stack.core.types.builtin.NodeId;
import org.eclipse.milo.opcua.stack.core.types.builtin.QualifiedName;
import org.eclipse.milo.opcua.stack.core.types.builtin.StatusCode;
import org.eclipse.milo.opcua.stack.core.types.builtin.Variant;
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.UShort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** CTT-only Alarms and Conditions fixtures with deterministic, continuously repeating inputs. */
public final class AlarmsAndConditionsFragment extends ManagedAddressSpaceFragmentWithLifecycle {

  static final String ROOT_ID = "CTT.AlarmsAndConditions";
  static final String DISCRETE_INPUT_ID = ROOT_ID + ".Discrete.Input";
  static final String DISCRETE_NORMAL_STATE_ID = ROOT_ID + ".Discrete.NormalState";
  static final String DISCRETE_CONDITION_ID = ROOT_ID + ".Discrete.Condition";
  static final String EXCLUSIVE_LIMIT_INPUT_ID = ROOT_ID + ".ExclusiveLimit.Input";
  static final String EXCLUSIVE_LIMIT_CONDITION_ID = ROOT_ID + ".ExclusiveLimit.Condition";
  static final String EXCLUSIVE_LEVEL_INPUT_ID = ROOT_ID + ".ExclusiveLevel.Input";
  static final String EXCLUSIVE_LEVEL_CONDITION_ID = ROOT_ID + ".ExclusiveLevel.Condition";
  static final String NON_EXCLUSIVE_LIMIT_INPUT_ID = ROOT_ID + ".NonExclusiveLimit.Input";
  static final String NON_EXCLUSIVE_LIMIT_CONDITION_ID = ROOT_ID + ".NonExclusiveLimit.Condition";
  static final String NON_EXCLUSIVE_LEVEL_INPUT_ID = ROOT_ID + ".NonExclusiveLevel.Input";
  static final String NON_EXCLUSIVE_LEVEL_CONDITION_ID = ROOT_ID + ".NonExclusiveLevel.Condition";

  static final List<Double> LEVEL_TRAJECTORY =
      List.of(0.0, 70.0, 90.0, 70.0, 0.0, -70.0, -90.0, -70.0);

  private static final double HIGH_HIGH_LIMIT = 80.0;
  private static final double HIGH_LIMIT = 60.0;
  private static final double LOW_LIMIT = -60.0;
  private static final double LOW_LOW_LIMIT = -80.0;

  private static final Logger LOGGER = LoggerFactory.getLogger(AlarmsAndConditionsFragment.class);

  private final AddressSpaceFilter filter;
  private final SubscriptionModel subscriptionModel;
  private final NodeId parentNodeId;
  private final UShort namespaceIndex;
  private final Duration dwellTime;
  private final List<Condition> conditions = new ArrayList<>();

  /** Guards the scenario state and Conditions, making a step and shutdown mutually exclusive. */
  private final ReentrantLock scenarioLock = new ReentrantLock();

  private UaVariableNode discreteInput;
  private UaVariableNode discreteNormalState;
  private UaVariableNode exclusiveLimitInput;
  private UaVariableNode exclusiveLevelInput;
  private UaVariableNode nonExclusiveLimitInput;
  private UaVariableNode nonExclusiveLevelInput;

  private UaObjectNode discreteSource;
  private UaObjectNode exclusiveLimitSource;
  private UaObjectNode exclusiveLevelSource;
  private UaObjectNode nonExclusiveLimitSource;
  private UaObjectNode nonExclusiveLevelSource;

  private OffNormalAlarm discreteAlarm;
  private ExclusiveLimitAlarm exclusiveLimitAlarm;
  private ExclusiveLevelAlarm exclusiveLevelAlarm;
  private NonExclusiveLimitAlarm nonExclusiveLimitAlarm;
  private NonExclusiveLevelAlarm nonExclusiveLevelAlarm;

  private ScheduledFuture<?> scenarioFuture;
  private volatile boolean running;
  private int levelTrajectoryIndex;
  private boolean discreteValue;

  public AlarmsAndConditionsFragment(
      OpcUaServer server,
      AddressSpaceComposite composite,
      NodeId parentNodeId,
      UShort namespaceIndex,
      Duration dwellTime) {

    super(server, composite);

    if (dwellTime.isZero() || dwellTime.isNegative()) {
      throw new IllegalArgumentException("A&C fixture dwell time must be positive: " + dwellTime);
    }

    this.parentNodeId = parentNodeId;
    this.namespaceIndex = namespaceIndex;
    this.dwellTime = dwellTime;

    filter = SimpleAddressSpaceFilter.create(getNodeManager()::containsNode);

    subscriptionModel = new SubscriptionModel(server, composite);
    getLifecycleManager().addLifecycle(subscriptionModel);

    getLifecycleManager().addStartupTask(this::startFixture);
    getLifecycleManager().addShutdownTask(this::stopFixture);
  }

  @Override
  public AddressSpaceFilter getFilter() {
    return filter;
  }

  @Override
  public void onDataItemsCreated(List<DataItem> dataItems) {
    subscriptionModel.onDataItemsCreated(dataItems);
  }

  @Override
  public void onDataItemsModified(List<DataItem> dataItems) {
    subscriptionModel.onDataItemsModified(dataItems);
  }

  @Override
  public void onDataItemsDeleted(List<DataItem> dataItems) {
    subscriptionModel.onDataItemsDeleted(dataItems);
  }

  @Override
  public void onMonitoringModeChanged(List<MonitoredItem> monitoredItems) {
    subscriptionModel.onMonitoringModeChanged(monitoredItems);
  }

  static Duration cycleTime(Duration dwellTime) {
    return dwellTime.multipliedBy(LEVEL_TRAJECTORY.size());
  }

  private void startFixture() {
    try {
      createAddressSpace();
      createConditions();

      conditions.forEach(getServer().getConditionManager()::register);

      initializeScenario();
      running = true;

      // The scheduler only dispatches: advancing the scenario sets five values and fans their
      // condition events out synchronously, which does not belong on the shared scheduled executor
      // that every publish timer and sampling task in the server shares.
      //
      // At a fixed rate rather than a fixed delay, so a step that runs long does not stretch the
      // dwell that follows it. Steps that pile up queue on the scenario lock rather than being
      // dropped, which is what advanceScenarioSafely wants.
      long dwellNanos = dwellTime.toNanos();
      scenarioFuture =
          getServer()
              .getConfig()
              .getScheduledExecutorService()
              .scheduleAtFixedRate(
                  () -> getServer().getExecutorService().execute(this::advanceScenarioSafely),
                  dwellNanos,
                  dwellNanos,
                  TimeUnit.NANOSECONDS);
    } catch (UaException e) {
      throw new IllegalStateException("failed to create CTT Alarms and Conditions fixtures", e);
    }
  }

  /**
   * Stop the scenario and unregister its Conditions, without interrupting a step that is midway
   * through driving a Condition's state machine.
   *
   * <p>Cancelling without interruption stops further dispatches and lets any step in flight finish;
   * taking the scenario lock then waits for it. Clearing {@code running} under that lock closes the
   * remaining race, where a step dispatched to the executor just before the cancel would otherwise
   * run and evaluate alarms whose Conditions have just been unregistered.
   */
  private void stopFixture() {
    ScheduledFuture<?> future = scenarioFuture;
    if (future != null) {
      // Without interruption: the scheduled task only dispatches, so an interrupt could never
      // reach the scenario step, and would instead land on a scheduler thread the whole server
      // shares. Clearing `running` under the lock below is what stops the scenario.
      future.cancel(false);
      scenarioFuture = null;
    }

    scenarioLock.lock();
    try {
      running = false;

      conditions.forEach(getServer().getConditionManager()::unregister);
      conditions.clear();
    } finally {
      scenarioLock.unlock();
    }
  }

  private void createAddressSpace() {
    var rootFolder =
        new UaFolderNode(
            getNodeContext(),
            nodeId(ROOT_ID),
            qualifiedName("AlarmsAndConditions"),
            LocalizedText.english("AlarmsAndConditions"));
    getNodeManager().addNode(rootFolder);
    addComponent(rootFolder, parentNodeId);

    discreteSource = createSourceObject(rootFolder, "Discrete");
    discreteInput =
        createVariable(
            discreteSource, DISCRETE_INPUT_ID, "Input", NodeIds.Boolean, Variant.ofBoolean(false));
    discreteNormalState =
        createVariable(
            discreteSource,
            DISCRETE_NORMAL_STATE_ID,
            "NormalState",
            NodeIds.Boolean,
            Variant.ofBoolean(false));

    exclusiveLimitSource = createSourceObject(rootFolder, "ExclusiveLimit");
    exclusiveLimitInput =
        createVariable(
            exclusiveLimitSource,
            EXCLUSIVE_LIMIT_INPUT_ID,
            "Input",
            NodeIds.Double,
            Variant.ofDouble(LEVEL_TRAJECTORY.getFirst()));

    exclusiveLevelSource = createSourceObject(rootFolder, "ExclusiveLevel");
    exclusiveLevelInput =
        createVariable(
            exclusiveLevelSource,
            EXCLUSIVE_LEVEL_INPUT_ID,
            "Input",
            NodeIds.Double,
            Variant.ofDouble(LEVEL_TRAJECTORY.getFirst()));

    nonExclusiveLimitSource = createSourceObject(rootFolder, "NonExclusiveLimit");
    nonExclusiveLimitInput =
        createVariable(
            nonExclusiveLimitSource,
            NON_EXCLUSIVE_LIMIT_INPUT_ID,
            "Input",
            NodeIds.Double,
            Variant.ofDouble(LEVEL_TRAJECTORY.getFirst()));

    nonExclusiveLevelSource = createSourceObject(rootFolder, "NonExclusiveLevel");
    nonExclusiveLevelInput =
        createVariable(
            nonExclusiveLevelSource,
            NON_EXCLUSIVE_LEVEL_INPUT_ID,
            "Input",
            NodeIds.Double,
            Variant.ofDouble(LEVEL_TRAJECTORY.getFirst()));
  }

  private void createConditions() throws UaException {
    discreteAlarm =
        OffNormalAlarm.create(
            getNodeContext(),
            builder ->
                builder
                    .nodeId(nodeId(DISCRETE_CONDITION_ID))
                    .browseName(qualifiedName("Condition"))
                    .displayName(LocalizedText.english("Discrete Off-Normal Condition"))
                    .conditionSource(discreteSource)
                    .conditionName("CttDiscreteOffNormal")
                    .conditionClass(
                        NodeIds.TestingConditionClassType,
                        new LocalizedText("", "TestingConditionClassType"))
                    .severity(ushort(400))
                    .inputNode(discreteInput.getNodeId())
                    .normalState(discreteNormalState.getNodeId()));
    addCondition(discreteAlarm, discreteSource);

    exclusiveLimitAlarm =
        ExclusiveLimitAlarm.create(
            getNodeContext(),
            builder ->
                configureLimitCondition(
                    builder, exclusiveLimitSource, exclusiveLimitInput, "ExclusiveLimit", 500));
    addCondition(exclusiveLimitAlarm, exclusiveLimitSource);

    exclusiveLevelAlarm =
        ExclusiveLevelAlarm.create(
            getNodeContext(),
            builder ->
                configureLimitCondition(
                    builder, exclusiveLevelSource, exclusiveLevelInput, "ExclusiveLevel", 600));
    addCondition(exclusiveLevelAlarm, exclusiveLevelSource);

    nonExclusiveLimitAlarm =
        NonExclusiveLimitAlarm.create(
            getNodeContext(),
            builder ->
                configureLimitCondition(
                    builder,
                    nonExclusiveLimitSource,
                    nonExclusiveLimitInput,
                    "NonExclusiveLimit",
                    700));
    addCondition(nonExclusiveLimitAlarm, nonExclusiveLimitSource);

    nonExclusiveLevelAlarm =
        NonExclusiveLevelAlarm.create(
            getNodeContext(),
            builder ->
                configureLimitCondition(
                    builder,
                    nonExclusiveLevelSource,
                    nonExclusiveLevelInput,
                    "NonExclusiveLevel",
                    800));
    addCondition(nonExclusiveLevelAlarm, nonExclusiveLevelSource);
  }

  private void configureLimitCondition(
      ConditionBuilder builder,
      UaObjectNode source,
      UaVariableNode input,
      String name,
      int severity) {

    builder
        .nodeId(nodeId(ROOT_ID + "." + name + ".Condition"))
        .browseName(qualifiedName("Condition"))
        .displayName(LocalizedText.english(name + " Condition"))
        .conditionSource(source)
        .conditionName("Ctt" + name)
        .conditionClass(
            NodeIds.TestingConditionClassType, new LocalizedText("", "TestingConditionClassType"))
        .severity(ushort(severity))
        .inputNode(input.getNodeId())
        .highHighLimit(HIGH_HIGH_LIMIT)
        .highLimit(HIGH_LIMIT)
        .lowLimit(LOW_LIMIT)
        .lowLowLimit(LOW_LOW_LIMIT);
  }

  private void addCondition(Condition condition, UaNode source) {
    conditions.add(condition);
    addComponent(condition.getNode(), source.getNodeId());
  }

  private UaObjectNode createSourceObject(UaFolderNode rootFolder, String name) {
    UaObjectNode source =
        new UaObjectNodeBuilder(getNodeContext())
            .setNodeId(nodeId(ROOT_ID + "." + name))
            .setBrowseName(qualifiedName(name))
            .setDisplayName(LocalizedText.english(name))
            .buildAndAdd();
    addComponent(source, rootFolder.getNodeId());
    return source;
  }

  private UaVariableNode createVariable(
      UaObjectNode source, String identifier, String name, NodeId dataType, Variant initialValue) {

    UaVariableNode variable =
        new UaVariableNodeBuilder(getNodeContext())
            .setNodeId(nodeId(identifier))
            .setBrowseName(qualifiedName(name))
            .setDisplayName(LocalizedText.english(name))
            .setDataType(dataType)
            .setValueRank(ValueRanks.Scalar)
            .setAccessLevel(AccessLevel.toValue(AccessLevel.CurrentRead))
            .setUserAccessLevel(AccessLevel.toValue(AccessLevel.CurrentRead))
            .setMinimumSamplingInterval(0.0)
            .setValue(new DataValue(initialValue))
            .buildAndAdd();
    addComponent(variable, source.getNodeId());
    return variable;
  }

  private void initializeScenario() {
    levelTrajectoryIndex = 0;
    discreteValue = false;

    updateDiscrete(false);
    updateExclusiveLimit(LEVEL_TRAJECTORY.getFirst());
    updateExclusiveLevel(LEVEL_TRAJECTORY.getFirst());
    updateNonExclusiveLimit(LEVEL_TRAJECTORY.getFirst());
    updateNonExclusiveLevel(LEVEL_TRAJECTORY.getFirst());
  }

  /**
   * Advance the scenario one dwell.
   *
   * <p>Blocking on the lock rather than skipping the step, which is where this parts company with
   * the {@code Demo/Alarms} plant: the CTT selections read the eight-state limit trajectory in
   * order, so a dropped step corrupts the fixture in a way a slow step does not.
   */
  private void advanceScenarioSafely() {
    scenarioLock.lock();
    try {
      // Re-checked under the lock: this step may have been dispatched before a shutdown that has
      // since unregistered the Conditions it is about to drive.
      if (!running) {
        return;
      }

      discreteValue = !discreteValue;
      levelTrajectoryIndex = (levelTrajectoryIndex + 1) % LEVEL_TRAJECTORY.size();
      double level = LEVEL_TRAJECTORY.get(levelTrajectoryIndex);

      updateDiscrete(discreteValue);
      updateExclusiveLimit(level);
      updateExclusiveLevel(level);
      updateNonExclusiveLimit(level);
      updateNonExclusiveLevel(level);
    } catch (RuntimeException e) {
      // A failing step must not kill the scheduled task and freeze the whole fixture.
      LOGGER.error("Error advancing CTT Alarms and Conditions scenario", e);
    } finally {
      scenarioLock.unlock();
    }
  }

  private void updateDiscrete(boolean value) {
    publish(discreteInput, Variant.ofBoolean(value), () -> discreteAlarm.evaluate(value, false));
  }

  private void updateExclusiveLimit(double value) {
    publish(
        exclusiveLimitInput, Variant.ofDouble(value), () -> exclusiveLimitAlarm.evaluate(value));
  }

  private void updateExclusiveLevel(double value) {
    publish(
        exclusiveLevelInput, Variant.ofDouble(value), () -> exclusiveLevelAlarm.evaluate(value));
  }

  private void updateNonExclusiveLimit(double value) {
    publish(
        nonExclusiveLimitInput,
        Variant.ofDouble(value),
        () -> nonExclusiveLimitAlarm.evaluate(value));
  }

  private void updateNonExclusiveLevel(double value) {
    publish(
        nonExclusiveLevelInput,
        Variant.ofDouble(value),
        () -> nonExclusiveLevelAlarm.evaluate(value));
  }

  /**
   * Publish {@code value} on {@code input} with Good quality, then evaluate the alarm watching it.
   *
   * <p>Both are stamped with one reading of the clock, which is what lets a client correlate an
   * input value with the condition event it caused: the alarm's event Time and the DataValue's
   * source timestamp share an origin. Arrival order is not part of that correlation — the value
   * reaches a monitored item on the sampling interval its client asked for, which may well be after
   * the event.
   *
   * @param input the {@link UaVariableNode} to publish on.
   * @param value the value the input transitioned to.
   * @param evaluate evaluates the alarm watching {@code input} against that same transition.
   */
  private static void publish(UaVariableNode input, Variant value, Runnable evaluate) {
    DateTime transitionTime = DateTime.now();
    input.setValue(new DataValue(value, StatusCode.GOOD, transitionTime, transitionTime));
    evaluate.run();
  }

  private void addComponent(UaNode child, NodeId parent) {
    child.addReference(
        new Reference(
            child.getNodeId(), ReferenceTypes.HasComponent, parent.expanded(), Direction.INVERSE));
  }

  private NodeId nodeId(String identifier) {
    return new NodeId(namespaceIndex, identifier);
  }

  private QualifiedName qualifiedName(String name) {
    return new QualifiedName(namespaceIndex, name);
  }
}

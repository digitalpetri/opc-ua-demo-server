package com.digitalpetri.opcua.server.namespace.demo.ctt;

import static org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.Unsigned.ushort;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
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
import org.eclipse.milo.opcua.stack.core.AttributeId;
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
import org.eclipse.milo.opcua.stack.core.types.enumerated.TimestampsToReturn;
import org.eclipse.milo.opcua.stack.core.types.structured.ReadValueId;
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
  private final Object scenarioLock = new Object();

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

      long dwellNanos = dwellTime.toNanos();
      scenarioFuture =
          getServer()
              .getConfig()
              .getScheduledExecutorService()
              .scheduleAtFixedRate(
                  this::advanceScenarioSafely, dwellNanos, dwellNanos, TimeUnit.NANOSECONDS);
    } catch (UaException e) {
      throw new IllegalStateException("failed to create CTT Alarms and Conditions fixtures", e);
    }
  }

  private void stopFixture() {
    running = false;

    ScheduledFuture<?> future = scenarioFuture;
    if (future != null) {
      future.cancel(true);
      scenarioFuture = null;
    }

    synchronized (scenarioLock) {
      // Wait for a tick that was already emitting when shutdown began.
    }

    conditions.forEach(getServer().getConditionManager()::unregister);
    conditions.clear();
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

  private void advanceScenarioSafely() {
    synchronized (scenarioLock) {
      if (!running) {
        return;
      }

      try {
        discreteValue = !discreteValue;
        levelTrajectoryIndex = (levelTrajectoryIndex + 1) % LEVEL_TRAJECTORY.size();
        double level = LEVEL_TRAJECTORY.get(levelTrajectoryIndex);

        updateDiscrete(discreteValue);
        updateExclusiveLimit(level);
        updateExclusiveLevel(level);
        updateNonExclusiveLimit(level);
        updateNonExclusiveLevel(level);
      } catch (RuntimeException e) {
        LOGGER.error("Error advancing CTT Alarms and Conditions scenario", e);
      }
    }
  }

  private void updateDiscrete(boolean value) {
    DateTime transitionTime = DateTime.now();
    setInputValue(discreteInput, goodValue(Variant.ofBoolean(value), transitionTime));
    discreteAlarm.evaluate(value, false);
  }

  private void updateExclusiveLimit(double value) {
    DateTime transitionTime = DateTime.now();
    setInputValue(exclusiveLimitInput, goodValue(Variant.ofDouble(value), transitionTime));
    exclusiveLimitAlarm.evaluate(value);
  }

  private void updateExclusiveLevel(double value) {
    DateTime transitionTime = DateTime.now();
    setInputValue(exclusiveLevelInput, goodValue(Variant.ofDouble(value), transitionTime));
    exclusiveLevelAlarm.evaluate(value);
  }

  private void updateNonExclusiveLimit(double value) {
    DateTime transitionTime = DateTime.now();
    setInputValue(nonExclusiveLimitInput, goodValue(Variant.ofDouble(value), transitionTime));
    nonExclusiveLimitAlarm.evaluate(value);
  }

  private void updateNonExclusiveLevel(double value) {
    DateTime transitionTime = DateTime.now();
    setInputValue(nonExclusiveLevelInput, goodValue(Variant.ofDouble(value), transitionTime));
    nonExclusiveLevelAlarm.evaluate(value);
  }

  private void setInputValue(UaVariableNode input, DataValue value) {
    input.setValue(value);

    // Condition events are enqueued immediately, while SubscriptionModel normally samples values
    // on a timer. Queue the matching value first so clients can correlate it with the event.
    for (DataItem dataItem : subscriptionModel.getDataItems()) {
      ReadValueId readValueId = dataItem.getReadValueId();

      if (dataItem.isSamplingEnabled()
          && input.getNodeId().equals(readValueId.getNodeId())
          && AttributeId.Value.isEqual(readValueId.getAttributeId())) {

        TimestampsToReturn timestampsToReturn = dataItem.getTimestampsToReturn();
        DataValue monitoredValue =
            timestampsToReturn != null ? DataValue.derivedValue(value, timestampsToReturn) : value;

        dataItem.setValue(monitoredValue);
      }
    }
  }

  private static DataValue goodValue(Variant value, DateTime transitionTime) {
    return new DataValue(value, StatusCode.GOOD, transitionTime, transitionTime);
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

package com.digitalpetri.opcua.server.namespace.demo.alarms;

import static com.digitalpetri.opcua.server.namespace.demo.Util.deriveChildNodeId;
import static org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.Unsigned.ushort;

import com.digitalpetri.opcua.server.namespace.demo.EuRangeCheckFilter;
import java.util.function.DoubleConsumer;
import org.eclipse.milo.opcua.sdk.core.AccessLevel;
import org.eclipse.milo.opcua.sdk.core.Reference;
import org.eclipse.milo.opcua.sdk.core.Reference.Direction;
import org.eclipse.milo.opcua.sdk.core.ValueRanks;
import org.eclipse.milo.opcua.sdk.server.conditions.ConditionBuilder;
import org.eclipse.milo.opcua.sdk.server.model.variables.AnalogItemTypeNode;
import org.eclipse.milo.opcua.sdk.server.nodes.UaNode;
import org.eclipse.milo.opcua.sdk.server.nodes.UaNodeContext;
import org.eclipse.milo.opcua.sdk.server.nodes.UaObjectNode;
import org.eclipse.milo.opcua.sdk.server.nodes.UaObjectNode.UaObjectNodeBuilder;
import org.eclipse.milo.opcua.sdk.server.nodes.UaVariableNode;
import org.eclipse.milo.opcua.sdk.server.nodes.UaVariableNode.UaVariableNodeBuilder;
import org.eclipse.milo.opcua.sdk.server.nodes.instantiation.BrowsePath;
import org.eclipse.milo.opcua.sdk.server.nodes.instantiation.InstantiationRequest;
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
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.UByte;
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.UShort;
import org.eclipse.milo.opcua.stack.core.types.structured.EUInformation;
import org.eclipse.milo.opcua.stack.core.types.structured.Range;

/**
 * Node-building helpers shared by the {@code Demo/Alarms} equipment models: Object Nodes, process
 * variables, and the publish that pairs a sampled value with the alarm evaluation it feeds.
 */
final class AlarmNodeFactory {

  private static final BrowsePath ENGINEERING_UNITS =
      BrowsePath.of(new QualifiedName(0, "EngineeringUnits"));

  private final UaNodeContext nodeContext;
  private final UShort namespaceIndex;

  AlarmNodeFactory(UaNodeContext nodeContext, UShort namespaceIndex) {
    this.nodeContext = nodeContext;
    this.namespaceIndex = namespaceIndex;
  }

  UaNodeContext nodeContext() {
    return nodeContext;
  }

  /**
   * Build a {@link QualifiedName} in the demo namespace.
   *
   * @param name the name component.
   * @return the {@link QualifiedName}.
   */
  QualifiedName qualifiedName(String name) {
    return new QualifiedName(namespaceIndex, name);
  }

  /**
   * Create an area Object Node with <i>no</i> hierarchical parent Reference.
   *
   * <p>HasNotifier and HasEventSource are themselves subtypes of HierarchicalReferences, so an area
   * wired both as a component and as a notifier of its parent appears twice in a client's browse
   * tree. Every Node in the plant therefore gets exactly one hierarchical parent, and for an area
   * below another area that parent is the HasNotifier Reference its caller adds.
   *
   * @param parentNodeId the {@link NodeId} the new Node's own {@link NodeId} is derived from.
   * @param name the BrowseName and DisplayName of the new Node.
   * @return the created {@link UaObjectNode}.
   */
  UaObjectNode areaNode(NodeId parentNodeId, String name) {
    return new UaObjectNodeBuilder(nodeContext)
        .setNodeId(deriveChildNodeId(parentNodeId, name))
        .setBrowseName(new QualifiedName(namespaceIndex, name))
        .setDisplayName(LocalizedText.english(name))
        .setTypeDefinition(NodeIds.BaseObjectType)
        .buildAndAdd();
  }

  /**
   * Create an equipment Object Node whose sole hierarchical parent is the area's HasEventSource
   * Reference to it.
   *
   * <p>That Reference must exist before any Condition names this Node as its condition source.
   * Condition wiring adds a {@code Server → HasEventSource → source} shortcut only when the source
   * has no inverse HasEventSource-or-subtype Reference, and that shortcut would leak every
   * equipment event past the area scope. Equipment models must therefore build their Object Node
   * with this method rather than with {@link #areaNode(NodeId, String)}, which deliberately leaves
   * the wiring to its caller.
   *
   * @param areaNode the area {@link UaObjectNode} the equipment belongs to.
   * @param name the BrowseName and DisplayName of the new Node.
   * @return the created {@link UaObjectNode}.
   */
  UaObjectNode equipmentNode(UaObjectNode areaNode, String name) {
    UaObjectNode node = areaNode(areaNode.getNodeId(), name);

    nodeContext
        .getNodeManager()
        .addReferences(
            new Reference(
                areaNode.getNodeId(),
                NodeIds.HasEventSource,
                node.getNodeId().expanded(),
                Direction.FORWARD),
            nodeContext.getServer().getNamespaceTable());

    return node;
  }

  /**
   * Add a HasComponent Reference pair making {@code child} a component of {@code parentNodeId}.
   *
   * @param child the child {@link UaNode}.
   * @param parentNodeId the {@link NodeId} of the parent Node.
   */
  void addComponent(UaNode child, NodeId parentNodeId) {
    child.addReference(
        new Reference(
            child.getNodeId(),
            ReferenceTypes.HasComponent,
            parentNodeId.expanded(),
            Direction.INVERSE));
  }

  /**
   * Apply the plant's Condition naming and wiring conventions to a {@link ConditionBuilder}.
   *
   * <p>Every name identifying the Condition is derived from the equipment it reports for, so a
   * Condition cannot end up named after one machine while sourcing its events from another.
   *
   * @param builder the {@link ConditionBuilder} to configure.
   * @param source the equipment {@link UaObjectNode} that is the Condition's source, as returned by
   *     {@link #equipmentNode(UaObjectNode, String)}.
   * @param name the BrowseName of the Condition, and the suffix of its ConditionName.
   * @param displayName the DisplayName of the Condition, prefixed with the equipment name.
   * @param inputNode the {@link NodeId} of the Variable the Condition watches.
   * @param conditionClass the {@link ConditionClass} the Condition belongs to.
   * @param severity the severity to report while the Condition is active.
   * @return the configured {@link ConditionBuilder}, for the caller to add its limits to.
   */
  ConditionBuilder condition(
      ConditionBuilder builder,
      UaObjectNode source,
      String name,
      String displayName,
      NodeId inputNode,
      ConditionClass conditionClass,
      int severity) {

    String equipmentName = source.getBrowseName().getName();

    return builder
        .nodeId(deriveChildNodeId(source.getNodeId(), name))
        .browseName(qualifiedName(name))
        .displayName(LocalizedText.english("%s %s".formatted(equipmentName, displayName)))
        .conditionSource(source)
        .conditionName("%s.%s".formatted(equipmentName, name))
        .conditionClass(conditionClass.typeId(), conditionClass.displayName())
        .severity(ushort(severity))
        .inputNode(inputNode);
  }

  /**
   * Create a read-only AnalogItemType Variable carrying engineering units and an EU range.
   *
   * @param parentNodeId the {@link NodeId} of the parent Node.
   * @param name the BrowseName and DisplayName of the new Node.
   * @param engineeringUnits the EngineeringUnits Property value.
   * @param euRange the EURange Property value.
   * @param initialValue the initial value.
   * @return the created {@link AnalogItemTypeNode}.
   * @throws UaException if instantiating the Node fails.
   */
  AnalogItemTypeNode analogVariable(
      NodeId parentNodeId,
      String name,
      EUInformation engineeringUnits,
      Range euRange,
      double initialValue)
      throws UaException {

    return analogVariable(parentNodeId, name, engineeringUnits, euRange, initialValue, false);
  }

  /**
   * Create a writable AnalogItemType Variable whose writes are range-checked against its EURange.
   *
   * @param parentNodeId the {@link NodeId} of the parent Node.
   * @param name the BrowseName and DisplayName of the new Node.
   * @param engineeringUnits the EngineeringUnits Property value.
   * @param euRange the EURange Property value.
   * @param initialValue the initial value.
   * @return the created {@link AnalogItemTypeNode}.
   * @throws UaException if instantiating the Node fails.
   */
  AnalogItemTypeNode writableAnalogVariable(
      NodeId parentNodeId,
      String name,
      EUInformation engineeringUnits,
      Range euRange,
      double initialValue)
      throws UaException {

    return analogVariable(parentNodeId, name, engineeringUnits, euRange, initialValue, true);
  }

  private AnalogItemTypeNode analogVariable(
      NodeId parentNodeId,
      String name,
      EUInformation engineeringUnits,
      Range euRange,
      double initialValue,
      boolean writable)
      throws UaException {

    UByte accessLevel =
        AccessLevel.toValue(writable ? AccessLevel.READ_WRITE : AccessLevel.READ_ONLY);

    var request =
        InstantiationRequest.of(AnalogItemTypeNode.class, NodeIds.AnalogItemType)
            .nodeId(deriveChildNodeId(parentNodeId, name))
            .browseName(qualifiedName(name))
            .displayName(LocalizedText.english(name))
            .parent(parentNodeId, NodeIds.HasComponent)
            .target(nodeContext.getNodeManager())
            .includeOptional(ENGINEERING_UNITS)
            .rootAttribute(AttributeId.DataType, NodeIds.Double)
            .rootAttribute(AttributeId.ValueRank, ValueRanks.Scalar)
            .rootAttribute(AttributeId.AccessLevel, accessLevel)
            .rootAttribute(AttributeId.UserAccessLevel, accessLevel)
            .rootAttribute(AttributeId.MinimumSamplingInterval, 0.0)
            .value(goodValue(Variant.ofDouble(initialValue)));

    if (writable) {
      // Node-local behavior belongs in a hook, which runs against the staged graph before anything
      // is published; structure and Property values do not (they would escape the batch).
      request.onNode(
          (declaration, node, parent, graph) -> {
            if (declaration == null) {
              node.getFilterChain().addLast(EuRangeCheckFilter.INSTANCE);
            }
          });
    }

    AnalogItemTypeNode analogNode =
        nodeContext.getServer().getNodeInstantiator().instantiate(request.build()).root();

    analogNode.setEngineeringUnits(engineeringUnits);
    analogNode.setEuRange(euRange);

    return analogNode;
  }

  /**
   * Create a read-only Boolean Variable.
   *
   * @param parentNodeId the {@link NodeId} of the parent Node.
   * @param name the BrowseName and DisplayName of the new Node.
   * @param initialValue the initial value.
   * @return the created {@link UaVariableNode}.
   */
  UaVariableNode booleanVariable(NodeId parentNodeId, String name, boolean initialValue) {
    return scalarVariable(
        parentNodeId, name, NodeIds.Boolean, Variant.ofBoolean(initialValue), false);
  }

  /**
   * Create a writable Boolean Variable, for operator inputs such as resets and mode switches.
   *
   * @param parentNodeId the {@link NodeId} of the parent Node.
   * @param name the BrowseName and DisplayName of the new Node.
   * @param initialValue the initial value.
   * @return the created {@link UaVariableNode}.
   */
  UaVariableNode writableBooleanVariable(NodeId parentNodeId, String name, boolean initialValue) {
    return scalarVariable(
        parentNodeId, name, NodeIds.Boolean, Variant.ofBoolean(initialValue), true);
  }

  /**
   * Create a read-only String Variable.
   *
   * @param parentNodeId the {@link NodeId} of the parent Node.
   * @param name the BrowseName and DisplayName of the new Node.
   * @param initialValue the initial value.
   * @return the created {@link UaVariableNode}.
   */
  UaVariableNode stringVariable(NodeId parentNodeId, String name, String initialValue) {
    return scalarVariable(
        parentNodeId, name, NodeIds.String, Variant.ofString(initialValue), false);
  }

  private UaVariableNode scalarVariable(
      NodeId parentNodeId, String name, NodeId dataType, Variant initialValue, boolean writable) {

    UByte accessLevel =
        AccessLevel.toValue(writable ? AccessLevel.READ_WRITE : AccessLevel.READ_ONLY);

    UaVariableNode node =
        new UaVariableNodeBuilder(nodeContext)
            .setNodeId(deriveChildNodeId(parentNodeId, name))
            .setBrowseName(new QualifiedName(namespaceIndex, name))
            .setDisplayName(LocalizedText.english(name))
            .setDataType(dataType)
            .setValueRank(ValueRanks.Scalar)
            .setAccessLevel(accessLevel)
            .setUserAccessLevel(accessLevel)
            .setMinimumSamplingInterval(0.0)
            .setValue(goodValue(initialValue))
            .buildAndAdd();

    addComponent(node, parentNodeId);

    return node;
  }

  /**
   * Publish {@code value} on {@code variable} with Good quality and the current time.
   *
   * @param variable the {@link UaVariableNode} to publish on.
   * @param value the new value.
   */
  void setValue(UaVariableNode variable, Variant value) {
    variable.setValue(goodValue(value));
  }

  /**
   * Publish one sample and evaluate the alarm watching it against that same sample.
   *
   * <p>Taking the sample as a parameter is deliberate: computing it once here is what keeps the
   * value a client reads identical to the value that decided the alarm state, rather than two
   * independently noised samples that disagree.
   *
   * @param variable the {@link UaVariableNode} to publish on.
   * @param value the sampled value.
   * @param evaluate the alarm's {@code evaluate} method.
   */
  void publish(UaVariableNode variable, double value, DoubleConsumer evaluate) {
    setValue(variable, Variant.ofDouble(value));
    evaluate.accept(value);
  }

  /**
   * Read the current Boolean value of a Variable, e.g. an operator input sampled inside a tick.
   *
   * @param variable the {@link UaVariableNode} to read.
   * @param defaultValue the value to return if the Variable does not currently hold a Boolean.
   * @return the current Boolean value.
   */
  static boolean booleanValue(UaVariableNode variable, boolean defaultValue) {
    return variable.getValue().getValue().getValue() instanceof Boolean value
        ? value
        : defaultValue;
  }

  /**
   * Read the current numeric value of a Variable, e.g. a writable setpoint sampled inside a tick.
   *
   * @param variable the {@link UaVariableNode} to read.
   * @param defaultValue the value to return if the Variable does not currently hold a number.
   * @return the current numeric value.
   */
  static double doubleValue(UaVariableNode variable, double defaultValue) {
    return variable.getValue().getValue().getValue() instanceof Number value
        ? value.doubleValue()
        : defaultValue;
  }

  /**
   * Build a Good-quality {@link DataValue} whose source and server timestamps are the current time.
   *
   * @param value the {@link Variant} value.
   * @return the {@link DataValue}.
   */
  static DataValue goodValue(Variant value) {
    DateTime now = DateTime.now();
    return new DataValue(value, StatusCode.GOOD, now, now);
  }

  /**
   * Build a {@link DataValue} reporting {@code statusCode} with no value, for a process variable
   * whose source has gone bad.
   *
   * @param statusCode the {@link StatusCode} to report.
   * @return the {@link DataValue}.
   */
  static DataValue badValue(StatusCode statusCode) {
    DateTime now = DateTime.now();
    return new DataValue(Variant.NULL_VALUE, statusCode, now, now);
  }
}

package com.digitalpetri.opcua.server.namespace;

import static com.digitalpetri.opcua.server.namespace.Util.deriveChildNodeId;
import static org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.Unsigned.ubyte;
import static org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.Unsigned.uint;
import static org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.Unsigned.ulong;
import static org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.Unsigned.ushort;

import java.util.List;
import java.util.UUID;
import org.eclipse.milo.opcua.sdk.core.AccessLevel;
import org.eclipse.milo.opcua.sdk.core.Reference;
import org.eclipse.milo.opcua.sdk.core.Reference.Direction;
import org.eclipse.milo.opcua.sdk.core.ValueRanks;
import org.eclipse.milo.opcua.sdk.server.AddressSpaceFilter;
import org.eclipse.milo.opcua.sdk.server.ManagedAddressSpaceFragmentWithLifecycle;
import org.eclipse.milo.opcua.sdk.server.OpcUaServer;
import org.eclipse.milo.opcua.sdk.server.SimpleAddressSpaceFilter;
import org.eclipse.milo.opcua.sdk.server.items.DataItem;
import org.eclipse.milo.opcua.sdk.server.items.MonitoredItem;
import org.eclipse.milo.opcua.sdk.server.nodes.UaFolderNode;
import org.eclipse.milo.opcua.sdk.server.nodes.UaVariableNode;
import org.eclipse.milo.opcua.sdk.server.nodes.UaVariableNode.UaVariableNodeBuilder;
import org.eclipse.milo.opcua.sdk.server.util.SubscriptionModel;
import org.eclipse.milo.opcua.stack.core.ReferenceTypes;
import org.eclipse.milo.opcua.stack.core.encoding.EncodingContext;
import org.eclipse.milo.opcua.stack.core.types.builtin.ByteString;
import org.eclipse.milo.opcua.stack.core.types.builtin.DataValue;
import org.eclipse.milo.opcua.stack.core.types.builtin.DateTime;
import org.eclipse.milo.opcua.stack.core.types.builtin.ExpandedNodeId;
import org.eclipse.milo.opcua.stack.core.types.builtin.ExtensionObject;
import org.eclipse.milo.opcua.stack.core.types.builtin.LocalizedText;
import org.eclipse.milo.opcua.stack.core.types.builtin.Matrix;
import org.eclipse.milo.opcua.stack.core.types.builtin.NodeId;
import org.eclipse.milo.opcua.stack.core.types.builtin.QualifiedName;
import org.eclipse.milo.opcua.stack.core.types.builtin.StatusCode;
import org.eclipse.milo.opcua.stack.core.types.builtin.Variant;
import org.eclipse.milo.opcua.stack.core.types.builtin.XmlElement;
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.UByte;
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.UInteger;
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.ULong;
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.UShort;
import org.eclipse.milo.opcua.stack.core.types.enumerated.ApplicationType;
import org.eclipse.milo.opcua.stack.core.types.structured.AccessLevelExType;
import org.eclipse.milo.opcua.stack.core.types.structured.AccessLevelType;
import org.eclipse.milo.opcua.stack.core.types.structured.AccessRestrictionType;
import org.eclipse.milo.opcua.stack.core.types.structured.XVType;
import org.eclipse.milo.opcua.test.DataTypeTestNodeIds;
import org.eclipse.milo.opcua.test.types.ConcreteTestType;
import org.eclipse.milo.opcua.test.types.ConcreteTestTypeEx;
import org.eclipse.milo.opcua.test.types.StructWithAbstractArrayFields;
import org.eclipse.milo.opcua.test.types.StructWithAbstractMatrixFields;
import org.eclipse.milo.opcua.test.types.StructWithAbstractScalarFields;
import org.eclipse.milo.opcua.test.types.StructWithBuiltinArrayFields;
import org.eclipse.milo.opcua.test.types.StructWithBuiltinArrayFieldsEx;
import org.eclipse.milo.opcua.test.types.StructWithBuiltinMatrixFields;
import org.eclipse.milo.opcua.test.types.StructWithBuiltinMatrixFieldsEx;
import org.eclipse.milo.opcua.test.types.StructWithBuiltinScalarFields;
import org.eclipse.milo.opcua.test.types.StructWithBuiltinScalarFieldsEx;
import org.eclipse.milo.opcua.test.types.StructWithOptionalArrayFields;
import org.eclipse.milo.opcua.test.types.StructWithOptionalMatrixFields;
import org.eclipse.milo.opcua.test.types.StructWithOptionalScalarFields;
import org.eclipse.milo.opcua.test.types.StructWithStructureArrayFields;
import org.eclipse.milo.opcua.test.types.StructWithStructureMatrixFields;
import org.eclipse.milo.opcua.test.types.StructWithStructureScalarFields;
import org.eclipse.milo.opcua.test.types.TestEnumType;
import org.eclipse.milo.opcua.test.types.UnionOfArray;
import org.eclipse.milo.opcua.test.types.UnionOfMatrix;
import org.eclipse.milo.opcua.test.types.UnionOfScalar;

public class DataTypeTestNodesFragment extends ManagedAddressSpaceFragmentWithLifecycle {

  private final DemoNamespace namespace;
  private final UShort namespaceIndex;

  private final SimpleAddressSpaceFilter filter;
  private final SubscriptionModel subscriptionModel;

  public DataTypeTestNodesFragment(OpcUaServer server, DemoNamespace namespace) {
    super(server, namespace);

    this.namespace = namespace;
    this.namespaceIndex = namespace.getNamespaceIndex();

    filter = SimpleAddressSpaceFilter.create(getNodeManager()::containsNode);

    subscriptionModel = new SubscriptionModel(server, this);
    getLifecycleManager().addLifecycle(subscriptionModel);

    getLifecycleManager().addStartupTask(this::addDataTypeTestNodes);
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

  private void addDataTypeTestNodes() {
    UaFolderNode dataTypeTestFolder =
        new UaFolderNode(
            getNodeContext(),
            deriveChildNodeId(namespace.getDemoFolder().getNodeId(), "DataTypeTest"),
            new QualifiedName(namespaceIndex, "DataTypeTest"),
            LocalizedText.english("DataTypeTest"));

    getNodeManager().addNode(dataTypeTestFolder);

    dataTypeTestFolder.addReference(
        new Reference(
            dataTypeTestFolder.getNodeId(),
            ReferenceTypes.HasComponent,
            namespace.getDemoFolder().getNodeId().expanded(),
            Direction.INVERSE));

    addTestEnumType(dataTypeTestFolder.getNodeId());
    addTestEnumTypeArray(dataTypeTestFolder.getNodeId());
    addTestEnumTypeMatrix(dataTypeTestFolder.getNodeId());

    addAbstractTestType(dataTypeTestFolder.getNodeId());
    addConcreteTestType(dataTypeTestFolder.getNodeId());
    addConcreteTestTypeEx(dataTypeTestFolder.getNodeId());
    addUnionOfScalar(dataTypeTestFolder.getNodeId());
    addUnionOfArray(dataTypeTestFolder.getNodeId());
    addUnionOfMatrix(dataTypeTestFolder.getNodeId());

    addStructWithAbstractScalarFields(dataTypeTestFolder.getNodeId());
    addStructWithAbstractArrayFields(dataTypeTestFolder.getNodeId());
    addStructWithAbstractMatrixFields(dataTypeTestFolder.getNodeId());

    addStructWithBuiltinScalarFields(dataTypeTestFolder.getNodeId());
    addStructWithBuiltinArrayFields(dataTypeTestFolder.getNodeId());
    addStructWithBuiltinMatrixFields(dataTypeTestFolder.getNodeId());

    addStructWithBuiltinScalarFieldsEx(dataTypeTestFolder.getNodeId());
    addStructWithBuiltinArrayFieldsEx(dataTypeTestFolder.getNodeId());
    addStructWithBuiltinMatrixFieldsEx(dataTypeTestFolder.getNodeId());

    addStructWithOptionalScalarFields(dataTypeTestFolder.getNodeId());
    addStructWithOptionalArrayFields(dataTypeTestFolder.getNodeId());
    addStructWithOptionalMatrixFields(dataTypeTestFolder.getNodeId());

    addStructWithStructureScalarFields(dataTypeTestFolder.getNodeId());
    addStructWithStructureArrayFields(dataTypeTestFolder.getNodeId());
    addStructWithStructureMatrixFields(dataTypeTestFolder.getNodeId());
  }

  private void addTestEnumType(NodeId parentNodeId) {
    var builder = new UaVariableNodeBuilder(getNodeContext());
    builder
        .setNodeId(deriveChildNodeId(parentNodeId, "TestEnumType"))
        .setBrowseName(new QualifiedName(namespaceIndex, "TestEnumType"))
        .setDisplayName(LocalizedText.english("TestEnumType"))
        .setDescription(LocalizedText.english("TestEnumType"))
        .setDataType(
            DataTypeTestNodeIds.TestEnumType.toNodeId(getNodeContext().getNamespaceTable())
                .orElseThrow())
        .setAccessLevel(AccessLevel.toValue(AccessLevel.READ_WRITE))
        .setUserAccessLevel(AccessLevel.toValue(AccessLevel.READ_WRITE))
        .setMinimumSamplingInterval(0.0);

    var value = TestEnumType.A;
    builder.setValue(new DataValue(Variant.ofEnum(value)));

    UaVariableNode variableNode = builder.build();

    getNodeManager().addNode(variableNode);

    variableNode.addReference(
        new Reference(
            variableNode.getNodeId(),
            ReferenceTypes.HasComponent,
            parentNodeId.expanded(),
            Direction.INVERSE));
  }

  private void addTestEnumTypeArray(NodeId parentNodeId) {
    var builder = new UaVariableNodeBuilder(getNodeContext());
    builder
        .setNodeId(deriveChildNodeId(parentNodeId, "TestEnumTypeArray"))
        .setBrowseName(new QualifiedName(namespaceIndex, "TestEnumTypeArray"))
        .setDisplayName(LocalizedText.english("TestEnumTypeArray"))
        .setDescription(LocalizedText.english("TestEnumTypeArray"))
        .setDataType(
            DataTypeTestNodeIds.TestEnumType.toNodeId(getNodeContext().getNamespaceTable())
                .orElseThrow())
        .setValueRank(ValueRanks.OneDimension)
        .setArrayDimensions(new UInteger[] {uint(0)})
        .setAccessLevel(AccessLevel.toValue(AccessLevel.READ_WRITE))
        .setUserAccessLevel(AccessLevel.toValue(AccessLevel.READ_WRITE))
        .setMinimumSamplingInterval(0.0);

    var value = new TestEnumType[] {TestEnumType.A, TestEnumType.B};
    builder.setValue(new DataValue(Variant.of(value)));

    UaVariableNode variableNode = builder.build();

    getNodeManager().addNode(variableNode);

    variableNode.addReference(
        new Reference(
            variableNode.getNodeId(),
            ReferenceTypes.HasComponent,
            parentNodeId.expanded(),
            Direction.INVERSE));
  }

  private void addTestEnumTypeMatrix(NodeId parentNodeId) {
    var builder = new UaVariableNodeBuilder(getNodeContext());
    builder
        .setNodeId(deriveChildNodeId(parentNodeId, "TestEnumTypeMatrix"))
        .setBrowseName(new QualifiedName(namespaceIndex, "TestEnumTypeMatrix"))
        .setDisplayName(LocalizedText.english("TestEnumTypeMatrix"))
        .setDescription(LocalizedText.english("TestEnumTypeMatrix"))
        .setDataType(
            DataTypeTestNodeIds.TestEnumType.toNodeId(getNodeContext().getNamespaceTable())
                .orElseThrow())
        .setValueRank(2)
        .setArrayDimensions(new UInteger[] {uint(0), uint(0)})
        .setAccessLevel(AccessLevel.toValue(AccessLevel.READ_WRITE))
        .setUserAccessLevel(AccessLevel.toValue(AccessLevel.READ_WRITE))
        .setMinimumSamplingInterval(0.0);

    var value =
        Matrix.ofEnum(
            new TestEnumType[][] {
              {TestEnumType.A, TestEnumType.B, TestEnumType.C},
              {TestEnumType.C, TestEnumType.B, TestEnumType.A}
            });
    builder.setValue(new DataValue(Variant.of(value)));

    UaVariableNode variableNode = builder.build();

    getNodeManager().addNode(variableNode);

    variableNode.addReference(
        new Reference(
            variableNode.getNodeId(),
            ReferenceTypes.HasComponent,
            parentNodeId.expanded(),
            Direction.INVERSE));
  }

  private void addUnionOfScalar(NodeId parentNodeId) {
    var builder = new UaVariableNodeBuilder(getNodeContext());
    builder
        .setNodeId(deriveChildNodeId(parentNodeId, "UnionOfScalar"))
        .setBrowseName(new QualifiedName(namespaceIndex, "UnionOfScalar"))
        .setDisplayName(LocalizedText.english("UnionOfScalar"))
        .setDescription(LocalizedText.english("UnionOfScalar"))
        .setDataType(
            DataTypeTestNodeIds.UnionOfScalar.toNodeId(getNodeContext().getNamespaceTable())
                .orElseThrow())
        .setAccessLevel(AccessLevel.toValue(AccessLevel.READ_WRITE))
        .setUserAccessLevel(AccessLevel.toValue(AccessLevel.READ_WRITE))
        .setMinimumSamplingInterval(0.0);

    var struct = UnionOfScalar.ofBoolean(true);

    builder.setValue(new DataValue(Variant.ofStruct(struct)));

    UaVariableNode variableNode = builder.build();

    getNodeManager().addNode(variableNode);

    variableNode.addReference(
        new Reference(
            variableNode.getNodeId(),
            ReferenceTypes.HasComponent,
            parentNodeId.expanded(),
            Direction.INVERSE));
  }

  private void addUnionOfArray(NodeId parentNodeId) {
    var builder = new UaVariableNodeBuilder(getNodeContext());
    builder
        .setNodeId(deriveChildNodeId(parentNodeId, "UnionOfArray"))
        .setBrowseName(new QualifiedName(namespaceIndex, "UnionOfArray"))
        .setDisplayName(LocalizedText.english("UnionOfArray"))
        .setDescription(LocalizedText.english("UnionOfArray"))
        .setDataType(
            DataTypeTestNodeIds.UnionOfArray.toNodeId(getNodeContext().getNamespaceTable())
                .orElseThrow())
        .setAccessLevel(AccessLevel.toValue(AccessLevel.READ_WRITE))
        .setUserAccessLevel(AccessLevel.toValue(AccessLevel.READ_WRITE))
        .setMinimumSamplingInterval(0.0);

    var struct = UnionOfArray.ofBoolean(new Boolean[] {true, false});

    builder.setValue(new DataValue(Variant.ofStruct(struct)));

    UaVariableNode variableNode = builder.build();

    getNodeManager().addNode(variableNode);

    variableNode.addReference(
        new Reference(
            variableNode.getNodeId(),
            ReferenceTypes.HasComponent,
            parentNodeId.expanded(),
            Direction.INVERSE));
  }

  private void addUnionOfMatrix(NodeId parentNodeId) {
    var builder = new UaVariableNodeBuilder(getNodeContext());
    builder
        .setNodeId(deriveChildNodeId(parentNodeId, "UnionOfMatrix"))
        .setBrowseName(new QualifiedName(namespaceIndex, "UnionOfMatrix"))
        .setDisplayName(LocalizedText.english("UnionOfMatrix"))
        .setDescription(LocalizedText.english("UnionOfMatrix"))
        .setDataType(
            DataTypeTestNodeIds.UnionOfMatrix.toNodeId(getNodeContext().getNamespaceTable())
                .orElseThrow())
        .setAccessLevel(AccessLevel.toValue(AccessLevel.READ_WRITE))
        .setUserAccessLevel(AccessLevel.toValue(AccessLevel.READ_WRITE))
        .setMinimumSamplingInterval(0.0);

    var struct = UnionOfMatrix.ofBoolean(Matrix.ofBoolean(new Boolean[][] {{true, false}}));

    builder.setValue(new DataValue(Variant.ofStruct(struct)));

    UaVariableNode variableNode = builder.build();

    getNodeManager().addNode(variableNode);

    variableNode.addReference(
        new Reference(
            variableNode.getNodeId(),
            ReferenceTypes.HasComponent,
            parentNodeId.expanded(),
            Direction.INVERSE));
  }

  private void addAbstractTestType(NodeId parentNodeId) {
    var builder = new UaVariableNodeBuilder(getNodeContext());
    builder
        .setNodeId(deriveChildNodeId(parentNodeId, "AbstractTestType"))
        .setBrowseName(new QualifiedName(namespaceIndex, "AbstractTestType"))
        .setDisplayName(LocalizedText.english("AbstractTestType"))
        .setDescription(LocalizedText.english("AbstractTestType"))
        .setDataType(
            DataTypeTestNodeIds.AbstractTestType.toNodeId(getNodeContext().getNamespaceTable())
                .orElseThrow())
        .setAccessLevel(AccessLevel.toValue(AccessLevel.READ_WRITE))
        .setUserAccessLevel(AccessLevel.toValue(AccessLevel.READ_WRITE))
        .setMinimumSamplingInterval(0.0);

    var struct = new ConcreteTestType((short) 0, 0.0, "", false);

    builder.setValue(new DataValue(Variant.ofStruct(struct)));

    UaVariableNode variableNode = builder.build();

    getNodeManager().addNode(variableNode);

    variableNode.addReference(
        new Reference(
            variableNode.getNodeId(),
            ReferenceTypes.HasComponent,
            parentNodeId.expanded(),
            Direction.INVERSE));
  }

  private void addConcreteTestType(NodeId parentNodeId) {
    var builder = new UaVariableNodeBuilder(getNodeContext());
    builder
        .setNodeId(deriveChildNodeId(parentNodeId, "ConcreteTestType"))
        .setBrowseName(new QualifiedName(namespaceIndex, "ConcreteTestType"))
        .setDisplayName(LocalizedText.english("ConcreteTestType"))
        .setDescription(LocalizedText.english("ConcreteTestType"))
        .setDataType(
            DataTypeTestNodeIds.ConcreteTestType.toNodeId(getNodeContext().getNamespaceTable())
                .orElseThrow())
        .setAccessLevel(AccessLevel.toValue(AccessLevel.READ_WRITE))
        .setUserAccessLevel(AccessLevel.toValue(AccessLevel.READ_WRITE))
        .setMinimumSamplingInterval(0.0);

    var struct = new ConcreteTestType((short) 0, 0.0, "", false);

    builder.setValue(new DataValue(Variant.ofStruct(struct)));

    UaVariableNode variableNode = builder.build();

    getNodeManager().addNode(variableNode);

    variableNode.addReference(
        new Reference(
            variableNode.getNodeId(),
            ReferenceTypes.HasComponent,
            parentNodeId.expanded(),
            Direction.INVERSE));
  }

  private void addConcreteTestTypeEx(NodeId parentNodeId) {
    var builder = new UaVariableNodeBuilder(getNodeContext());
    builder
        .setNodeId(deriveChildNodeId(parentNodeId, "ConcreteTestTypeEx"))
        .setBrowseName(new QualifiedName(namespaceIndex, "ConcreteTestTypeEx"))
        .setDisplayName(LocalizedText.english("ConcreteTestTypeEx"))
        .setDescription(LocalizedText.english("ConcreteTestTypeEx"))
        .setDataType(
            DataTypeTestNodeIds.ConcreteTestTypeEx.toNodeId(getNodeContext().getNamespaceTable())
                .orElseThrow())
        .setAccessLevel(AccessLevel.toValue(AccessLevel.READ_WRITE))
        .setUserAccessLevel(AccessLevel.toValue(AccessLevel.READ_WRITE))
        .setMinimumSamplingInterval(0.0);

    var struct = new ConcreteTestTypeEx((short) 0, 0.0, "", false, uint(0));

    builder.setValue(new DataValue(Variant.ofStruct(struct)));

    UaVariableNode variableNode = builder.build();

    getNodeManager().addNode(variableNode);

    variableNode.addReference(
        new Reference(
            variableNode.getNodeId(),
            ReferenceTypes.HasComponent,
            parentNodeId.expanded(),
            Direction.INVERSE));
  }

  private void addStructWithAbstractScalarFields(NodeId parentNodeId) {
    var builder = new UaVariableNodeBuilder(getNodeContext());
    builder
        .setNodeId(deriveChildNodeId(parentNodeId, "StructWithAbstractScalarFields"))
        .setBrowseName(new QualifiedName(namespaceIndex, "StructWithAbstractScalarFields"))
        .setDisplayName(LocalizedText.english("StructWithAbstractScalarFields"))
        .setDescription(LocalizedText.english("StructWithAbstractScalarFields"))
        .setDataType(
            DataTypeTestNodeIds.StructWithAbstractScalarFields.toNodeId(
                    getNodeContext().getNamespaceTable())
                .orElseThrow())
        .setAccessLevel(AccessLevel.toValue(AccessLevel.READ_WRITE))
        .setUserAccessLevel(AccessLevel.toValue(AccessLevel.READ_WRITE))
        .setMinimumSamplingInterval(0.0);

    var struct =
        new StructWithAbstractScalarFields(
            0,
            new ConcreteTestType((short) 0, 0.0, "", false),
            new ConcreteTestTypeEx((short) 0, 0.0, "", false, uint(0)));

    builder.setValue(new DataValue(Variant.ofStruct(struct)));

    UaVariableNode variableNode = builder.build();

    getNodeManager().addNode(variableNode);

    variableNode.addReference(
        new Reference(
            variableNode.getNodeId(),
            ReferenceTypes.HasComponent,
            parentNodeId.expanded(),
            Direction.INVERSE));
  }

  private void addStructWithAbstractArrayFields(NodeId parentNodeId) {
    var builder = new UaVariableNodeBuilder(getNodeContext());
    builder
        .setNodeId(new NodeId(namespaceIndex, "StructWithAbstractArrayFields"))
        .setBrowseName(new QualifiedName(namespaceIndex, "StructWithAbstractArrayFields"))
        .setDisplayName(LocalizedText.english("StructWithAbstractArrayFields"))
        .setDescription(LocalizedText.english("StructWithAbstractArrayFields"))
        .setDataType(
            DataTypeTestNodeIds.StructWithAbstractArrayFields.toNodeId(
                    getNodeContext().getNamespaceTable())
                .orElseThrow())
        .setAccessLevel(AccessLevel.toValue(AccessLevel.READ_WRITE))
        .setUserAccessLevel(AccessLevel.toValue(AccessLevel.READ_WRITE))
        .setMinimumSamplingInterval(0.0);

    var struct =
        new StructWithAbstractArrayFields(
            new Integer[] {0, 0},
            new ConcreteTestType[] {
              new ConcreteTestType((short) 0, 0.0, "", false),
              new ConcreteTestType((short) 0, 0.0, "", false)
            },
            new ConcreteTestTypeEx[] {
              new ConcreteTestTypeEx((short) 0, 0.0, "", false, uint(0)),
              new ConcreteTestTypeEx((short) 0, 0.0, "", false, uint(0))
            });

    builder.setValue(new DataValue(Variant.ofStruct(struct)));

    UaVariableNode variableNode = builder.build();

    getNodeManager().addNode(variableNode);

    variableNode.addReference(
        new Reference(
            variableNode.getNodeId(),
            ReferenceTypes.HasComponent,
            parentNodeId.expanded(),
            Direction.INVERSE));
  }

  private void addStructWithAbstractMatrixFields(NodeId parentNodeId) {
    var builder = new UaVariableNodeBuilder(getNodeContext());
    builder
        .setNodeId(new NodeId(namespaceIndex, "StructWithAbstractMatrixFields"))
        .setBrowseName(new QualifiedName(namespaceIndex, "StructWithAbstractMatrixFields"))
        .setDisplayName(LocalizedText.english("StructWithAbstractMatrixFields"))
        .setDescription(LocalizedText.english("StructWithAbstractMatrixFields"))
        .setDataType(
            DataTypeTestNodeIds.StructWithAbstractMatrixFields.toNodeId(
                    getNodeContext().getNamespaceTable())
                .orElseThrow())
        .setAccessLevel(AccessLevel.toValue(AccessLevel.READ_WRITE))
        .setUserAccessLevel(AccessLevel.toValue(AccessLevel.READ_WRITE))
        .setMinimumSamplingInterval(0.0);

    var struct =
        new StructWithAbstractMatrixFields(
            Matrix.ofInt32(new Integer[][] {{0, 0}, {0, 0}}),
            Matrix.ofStruct(
                new ConcreteTestType[][] {
                  {
                    new ConcreteTestType((short) 0, 0.0, "", false),
                    new ConcreteTestType((short) 0, 0.0, "", false)
                  },
                  {
                    new ConcreteTestType((short) 0, 0.0, "", false),
                    new ConcreteTestType((short) 0, 0.0, "", false)
                  }
                }),
            Matrix.ofStruct(
                new ConcreteTestTypeEx[][] {
                  {
                    new ConcreteTestTypeEx((short) 0, 0.0, "", false, uint(0)),
                    new ConcreteTestTypeEx((short) 0, 0.0, "", false, uint(0))
                  },
                  {
                    new ConcreteTestTypeEx((short) 0, 0.0, "", false, uint(0)),
                    new ConcreteTestTypeEx((short) 0, 0.0, "", false, uint(0))
                  }
                }));

    builder.setValue(new DataValue(Variant.ofStruct(struct)));

    UaVariableNode variableNode = builder.build();

    getNodeManager().addNode(variableNode);

    variableNode.addReference(
        new Reference(
            variableNode.getNodeId(),
            ReferenceTypes.HasComponent,
            parentNodeId.expanded(),
            Direction.INVERSE));
  }

  private void addStructWithBuiltinScalarFields(NodeId parentNodeId) {
    var builder = new UaVariableNodeBuilder(getNodeContext());
    builder
        .setNodeId(new NodeId(namespaceIndex, "StructWithBuiltinScalarFields"))
        .setBrowseName(new QualifiedName(namespaceIndex, "StructWithBuiltinScalarFields"))
        .setDisplayName(LocalizedText.english("StructWithBuiltinScalarFields"))
        .setDescription(LocalizedText.english("StructWithBuiltinScalarFields"))
        .setDataType(
            DataTypeTestNodeIds.StructWithBuiltinScalarFields.toNodeId(
                    getNodeContext().getNamespaceTable())
                .orElseThrow())
        .setAccessLevel(AccessLevel.toValue(AccessLevel.READ_WRITE))
        .setUserAccessLevel(AccessLevel.toValue(AccessLevel.READ_WRITE))
        .setMinimumSamplingInterval(0.0);

    var struct =
        new StructWithBuiltinScalarFields(
            false,
            (byte) 0,
            ubyte(0),
            (short) 0,
            ushort(0),
            0,
            uint(0),
            0L,
            ulong(0L),
            0.0f,
            0.0d,
            "",
            DateTime.MIN_DATE_TIME,
            UUID.fromString("00000000-0000-0000-0000-000000000000"),
            ByteString.of(new byte[] {0}),
            XmlElement.of(""),
            new NodeId(0, 0),
            new ExpandedNodeId(ushort(0), null, uint(0)),
            StatusCode.GOOD,
            new QualifiedName(0, ""),
            LocalizedText.NULL_VALUE,
            new DataValue(new Variant(0)),
            Variant.ofInt32(0));

    builder.setValue(new DataValue(Variant.ofStruct(struct)));

    UaVariableNode variableNode = builder.build();

    getNodeManager().addNode(variableNode);

    variableNode.addReference(
        new Reference(
            variableNode.getNodeId(),
            ReferenceTypes.HasComponent,
            parentNodeId.expanded(),
            Direction.INVERSE));
  }

  private void addStructWithBuiltinArrayFields(NodeId nodeId) {
    var builder = new UaVariableNodeBuilder(getNodeContext());
    builder
        .setNodeId(new NodeId(namespaceIndex, "StructWithBuiltinArrayFields"))
        .setBrowseName(new QualifiedName(namespaceIndex, "StructWithBuiltinArrayFields"))
        .setDisplayName(LocalizedText.english("StructWithBuiltinArrayFields"))
        .setDescription(LocalizedText.english("StructWithBuiltinArrayFields"))
        .setDataType(
            DataTypeTestNodeIds.StructWithBuiltinArrayFields.toNodeId(
                    getNodeContext().getNamespaceTable())
                .orElseThrow())
        .setAccessLevel(AccessLevel.toValue(AccessLevel.READ_WRITE))
        .setUserAccessLevel(AccessLevel.toValue(AccessLevel.READ_WRITE))
        .setMinimumSamplingInterval(0.0);

    var struct =
        new StructWithBuiltinArrayFields(
            new Boolean[] {false, false},
            new Byte[] {0, 0},
            new UByte[] {ubyte(0), ubyte(0)},
            new Short[] {0, 0},
            new UShort[] {ushort(0), ushort(0)},
            new Integer[] {0, 0},
            new UInteger[] {uint(0), uint(0)},
            new Long[] {0L, 0L},
            new ULong[] {ulong(0L), ulong(0L)},
            new Float[] {0.0f, 0.0f},
            new Double[] {0.0d, 0.0d},
            new String[] {"", ""},
            new DateTime[] {DateTime.MIN_DATE_TIME, DateTime.MIN_DATE_TIME},
            new UUID[] {
              UUID.fromString("00000000-0000-0000-0000-000000000000"),
              UUID.fromString("00000000-0000-0000-0000-000000000000")
            },
            new ByteString[] {ByteString.of(new byte[] {0}), ByteString.of(new byte[] {0})},
            new XmlElement[] {XmlElement.of(""), XmlElement.of("")},
            new NodeId[] {new NodeId(0, 0), new NodeId(0, 0)},
            new ExpandedNodeId[] {
              new ExpandedNodeId(ushort(0), null, uint(0)),
              new ExpandedNodeId(ushort(0), null, uint(0))
            },
            new StatusCode[] {StatusCode.GOOD, StatusCode.GOOD},
            new QualifiedName[] {new QualifiedName(0, ""), new QualifiedName(0, "")},
            new LocalizedText[] {LocalizedText.NULL_VALUE, LocalizedText.NULL_VALUE},
            new DataValue[] {new DataValue(new Variant(0)), new DataValue(new Variant(0))},
            new Variant[] {Variant.ofInt32(0), Variant.ofInt32(0)});

    builder.setValue(new DataValue(Variant.ofStruct(struct)));

    UaVariableNode variableNode = builder.build();

    getNodeManager().addNode(variableNode);

    variableNode.addReference(
        new Reference(
            variableNode.getNodeId(),
            ReferenceTypes.HasComponent,
            nodeId.expanded(),
            Direction.INVERSE));
  }

  private void addStructWithBuiltinMatrixFields(NodeId parentNodeId) {
    var builder = new UaVariableNodeBuilder(getNodeContext());
    builder
        .setNodeId(new NodeId(namespaceIndex, "StructWithBuiltinMatrixFields"))
        .setBrowseName(new QualifiedName(namespaceIndex, "StructWithBuiltinMatrixFields"))
        .setDisplayName(LocalizedText.english("StructWithBuiltinMatrixFields"))
        .setDescription(LocalizedText.english("StructWithBuiltinMatrixFields"))
        .setDataType(
            DataTypeTestNodeIds.StructWithBuiltinMatrixFields.toNodeId(
                    getNodeContext().getNamespaceTable())
                .orElseThrow())
        .setAccessLevel(AccessLevel.toValue(AccessLevel.READ_WRITE))
        .setUserAccessLevel(AccessLevel.toValue(AccessLevel.READ_WRITE))
        .setMinimumSamplingInterval(0.0);

    var struct =
        new StructWithBuiltinMatrixFields(
            Matrix.ofBoolean(new Boolean[][] {{false, false}, {false, false}}),
            Matrix.ofSByte(new Byte[][] {{0, 0}, {0, 0}}),
            Matrix.ofByte(new UByte[][] {{ubyte(0), ubyte(0)}, {ubyte(0), ubyte(0)}}),
            Matrix.ofInt16(new Short[][] {{0, 0}, {0, 0}}),
            Matrix.ofUInt16(new UShort[][] {{ushort(0), ushort(0)}, {ushort(0), ushort(0)}}),
            Matrix.ofInt32(new Integer[][] {{0, 0}, {0, 0}}),
            Matrix.ofUInt32(new UInteger[][] {{uint(0), uint(0)}, {uint(0), uint(0)}}),
            Matrix.ofInt64(new Long[][] {{0L, 0L}, {0L, 0L}}),
            Matrix.ofUInt64(new ULong[][] {{ulong(0L), ulong(0L)}, {ulong(0L), ulong(0L)}}),
            Matrix.ofFloat(new Float[][] {{0.0f, 0.0f}, {0.0f, 0.0f}}),
            Matrix.ofDouble(new Double[][] {{0.0d, 0.0d}, {0.0d, 0.0d}}),
            Matrix.ofString(new String[][] {{"", ""}, {"", ""}}),
            Matrix.ofDateTime(
                new DateTime[][] {
                  {DateTime.MIN_DATE_TIME, DateTime.MIN_DATE_TIME},
                  {DateTime.MIN_DATE_TIME, DateTime.MIN_DATE_TIME}
                }),
            Matrix.ofGuid(
                new UUID[][] {
                  {
                    UUID.fromString("00000000-0000-0000-0000-000000000000"),
                    UUID.fromString("00000000-0000-0000-0000-000000000000")
                  },
                  {
                    UUID.fromString("00000000-0000-0000-0000-000000000000"),
                    UUID.fromString("00000000-0000-0000-0000-000000000000")
                  }
                }),
            Matrix.ofByteString(
                new ByteString[][] {
                  {ByteString.of(new byte[] {0}), ByteString.of(new byte[] {0})},
                  {ByteString.of(new byte[] {0}), ByteString.of(new byte[] {0})}
                }),
            Matrix.ofXmlElement(
                new XmlElement[][] {
                  {XmlElement.of(""), XmlElement.of("")},
                  {XmlElement.of(""), XmlElement.of("")}
                }),
            Matrix.ofNodeId(
                new NodeId[][] {
                  {new NodeId(0, 0), new NodeId(0, 0)},
                  {new NodeId(0, 0), new NodeId(0, 0)}
                }),
            Matrix.ofExpandedNodeId(
                new ExpandedNodeId[][] {
                  {
                    new ExpandedNodeId(ushort(0), null, uint(0)),
                    new ExpandedNodeId(ushort(0), null, uint(0))
                  },
                  {
                    new ExpandedNodeId(ushort(0), null, uint(0)),
                    new ExpandedNodeId(ushort(0), null, uint(0))
                  }
                }),
            Matrix.ofStatusCode(
                new StatusCode[][] {
                  {StatusCode.GOOD, StatusCode.GOOD},
                  {StatusCode.GOOD, StatusCode.GOOD}
                }),
            Matrix.ofQualifiedName(
                new QualifiedName[][] {
                  {new QualifiedName(0, ""), new QualifiedName(0, "")},
                  {new QualifiedName(0, ""), new QualifiedName(0, "")}
                }),
            Matrix.ofLocalizedText(
                new LocalizedText[][] {
                  {LocalizedText.NULL_VALUE, LocalizedText.NULL_VALUE},
                  {LocalizedText.NULL_VALUE, LocalizedText.NULL_VALUE}
                }),
            Matrix.ofDataValue(
                new DataValue[][] {
                  {new DataValue(new Variant(0)), new DataValue(new Variant(0))},
                  {new DataValue(new Variant(0)), new DataValue(new Variant(0))}
                }),
            Matrix.ofVariant(
                new Variant[][] {
                  {Variant.ofInt32(0), Variant.ofInt32(0)},
                  {Variant.ofInt32(0), Variant.ofInt32(0)}
                }));

    builder.setValue(new DataValue(Variant.ofStruct(struct)));

    UaVariableNode variableNode = builder.build();

    getNodeManager().addNode(variableNode);

    variableNode.addReference(
        new Reference(
            variableNode.getNodeId(),
            ReferenceTypes.HasComponent,
            parentNodeId.expanded(),
            Direction.INVERSE));
  }

  private void addStructWithBuiltinScalarFieldsEx(NodeId parentNodeId) {
    var builder = new UaVariableNodeBuilder(getNodeContext());
    builder
        .setNodeId(new NodeId(namespaceIndex, "StructWithBuiltinScalarFieldsEx"))
        .setBrowseName(new QualifiedName(namespaceIndex, "StructWithBuiltinScalarFieldsEx"))
        .setDisplayName(LocalizedText.english("StructWithBuiltinScalarFieldsEx"))
        .setDescription(LocalizedText.english("StructWithBuiltinScalarFieldsEx"))
        .setDataType(
            DataTypeTestNodeIds.StructWithBuiltinScalarFieldsEx.toNodeId(
                    getNodeContext().getNamespaceTable())
                .orElseThrow())
        .setAccessLevel(AccessLevel.toValue(AccessLevel.READ_WRITE))
        .setUserAccessLevel(AccessLevel.toValue(AccessLevel.READ_WRITE))
        .setMinimumSamplingInterval(0.0);

    var struct =
        new StructWithBuiltinScalarFieldsEx(
            false,
            (byte) 0,
            ubyte(0),
            (short) 0,
            ushort(0),
            0,
            uint(0),
            0L,
            ulong(0L),
            0.0f,
            0.0d,
            "",
            DateTime.MIN_DATE_TIME,
            UUID.fromString("00000000-0000-0000-0000-000000000000"),
            ByteString.of(new byte[] {0}),
            XmlElement.of(""),
            new NodeId(0, 0),
            new ExpandedNodeId(ushort(0), null, uint(0)),
            StatusCode.GOOD,
            new QualifiedName(0, ""),
            LocalizedText.NULL_VALUE,
            new DataValue(new Variant(0)),
            Variant.ofInt32(0),
            0.0,
            ApplicationType.Server,
            TestEnumType.A,
            new XVType(0.0d, 0.0f),
            new ConcreteTestType((short) 0, 0.0, "", false),
            UnionOfScalar.ofBoolean(false),
            UnionOfArray.ofBoolean(new Boolean[] {false, false}),
            AccessLevelType.of(),
            AccessRestrictionType.of(),
            AccessLevelExType.of(),
            ulong(0));

    builder.setValue(new DataValue(Variant.ofStruct(struct)));

    UaVariableNode variableNode = builder.build();

    getNodeManager().addNode(variableNode);

    variableNode.addReference(
        new Reference(
            variableNode.getNodeId(),
            ReferenceTypes.HasComponent,
            parentNodeId.expanded(),
            Direction.INVERSE));
  }

  private void addStructWithBuiltinArrayFieldsEx(NodeId parentNodeId) {
    var builder = new UaVariableNodeBuilder(getNodeContext());
    builder
        .setNodeId(new NodeId(namespaceIndex, "StructWithBuiltinArrayFieldsEx"))
        .setBrowseName(new QualifiedName(namespaceIndex, "StructWithBuiltinArrayFieldsEx"))
        .setDisplayName(LocalizedText.english("StructWithBuiltinArrayFieldsEx"))
        .setDescription(LocalizedText.english("StructWithBuiltinArrayFieldsEx"))
        .setDataType(
            DataTypeTestNodeIds.StructWithBuiltinArrayFieldsEx.toNodeId(
                    getNodeContext().getNamespaceTable())
                .orElseThrow())
        .setAccessLevel(AccessLevel.toValue(AccessLevel.READ_WRITE))
        .setUserAccessLevel(AccessLevel.toValue(AccessLevel.READ_WRITE))
        .setMinimumSamplingInterval(0.0);

    var struct =
        new StructWithBuiltinArrayFieldsEx(
            new Boolean[] {false, false},
            new Byte[] {0, 0},
            new UByte[] {ubyte(0), ubyte(0)},
            new Short[] {0, 0},
            new UShort[] {ushort(0), ushort(0)},
            new Integer[] {0, 0},
            new UInteger[] {uint(0), uint(0)},
            new Long[] {0L, 0L},
            new ULong[] {ulong(0L), ulong(0L)},
            new Float[] {0.0f, 0.0f},
            new Double[] {0.0d, 0.0d},
            new String[] {"", ""},
            new DateTime[] {DateTime.MIN_DATE_TIME, DateTime.MIN_DATE_TIME},
            new UUID[] {
              UUID.fromString("00000000-0000-0000-0000-000000000000"),
              UUID.fromString("00000000-0000-0000-0000-000000000000")
            },
            new ByteString[] {ByteString.of(new byte[] {0}), ByteString.of(new byte[] {0})},
            new XmlElement[] {XmlElement.of(""), XmlElement.of("")},
            new NodeId[] {new NodeId(0, 0), new NodeId(0, 0)},
            new ExpandedNodeId[] {
              new ExpandedNodeId(ushort(0), null, uint(0)),
              new ExpandedNodeId(ushort(0), null, uint(0))
            },
            new StatusCode[] {StatusCode.GOOD, StatusCode.GOOD},
            new QualifiedName[] {new QualifiedName(0, ""), new QualifiedName(0, "")},
            new LocalizedText[] {LocalizedText.NULL_VALUE, LocalizedText.NULL_VALUE},
            new DataValue[] {new DataValue(new Variant(0)), new DataValue(new Variant(0))},
            new Variant[] {Variant.ofInt32(0), Variant.ofInt32(0)},
            new Double[] {0.0, 0.0},
            new ApplicationType[] {ApplicationType.Server, ApplicationType.Client},
            new TestEnumType[] {TestEnumType.A, TestEnumType.B},
            new XVType[] {new XVType(0.0d, 0.0f), new XVType(0.0d, 0.0f)},
            new ConcreteTestType[] {
              new ConcreteTestType((short) 0, 0.0, "", false),
              new ConcreteTestType((short) 0, 0.0, "", false)
            },
            new UnionOfScalar[] {UnionOfScalar.ofBoolean(false), UnionOfScalar.ofByte(ubyte(0))},
            new UnionOfArray[] {
              UnionOfArray.ofBoolean(new Boolean[] {false, false}),
              UnionOfArray.ofSByte(new Byte[] {0, 0})
            },
            new AccessLevelType[] {AccessLevelType.of(), AccessLevelType.of()},
            new AccessRestrictionType[] {AccessRestrictionType.of(), AccessRestrictionType.of()},
            new AccessLevelExType[] {AccessLevelExType.of(), AccessLevelExType.of()},
            new ULong[] {ulong(0L), ulong(0L)});

    builder.setValue(new DataValue(Variant.ofStruct(struct)));

    UaVariableNode variableNode = builder.build();

    getNodeManager().addNode(variableNode);

    variableNode.addReference(
        new Reference(
            variableNode.getNodeId(),
            ReferenceTypes.HasComponent,
            parentNodeId.expanded(),
            Direction.INVERSE));
  }

  private void addStructWithBuiltinMatrixFieldsEx(NodeId parentNodeId) {
    var builder = new UaVariableNodeBuilder(getNodeContext());
    builder
        .setNodeId(new NodeId(namespaceIndex, "StructWithBuiltinMatrixFieldsEx"))
        .setBrowseName(new QualifiedName(namespaceIndex, "StructWithBuiltinMatrixFieldsEx"))
        .setDisplayName(LocalizedText.english("StructWithBuiltinMatrixFieldsEx"))
        .setDescription(LocalizedText.english("StructWithBuiltinMatrixFieldsEx"))
        .setDataType(
            DataTypeTestNodeIds.StructWithBuiltinMatrixFieldsEx.toNodeId(
                    getNodeContext().getNamespaceTable())
                .orElseThrow())
        .setAccessLevel(AccessLevel.toValue(AccessLevel.READ_WRITE))
        .setUserAccessLevel(AccessLevel.toValue(AccessLevel.READ_WRITE))
        .setMinimumSamplingInterval(0.0);

    var struct =
        new StructWithBuiltinMatrixFieldsEx(
            Matrix.ofBoolean(new Boolean[][] {{false, false}, {false, false}}),
            Matrix.ofSByte(new Byte[][] {{0, 0}, {0, 0}}),
            Matrix.ofByte(new UByte[][] {{ubyte(0), ubyte(0)}, {ubyte(0), ubyte(0)}}),
            Matrix.ofInt16(new Short[][] {{0, 0}, {0, 0}}),
            Matrix.ofUInt16(new UShort[][] {{ushort(0), ushort(0)}, {ushort(0), ushort(0)}}),
            Matrix.ofInt32(new Integer[][] {{0, 0}, {0, 0}}),
            Matrix.ofUInt32(new UInteger[][] {{uint(0), uint(0)}, {uint(0), uint(0)}}),
            Matrix.ofInt64(new Long[][] {{0L, 0L}, {0L, 0L}}),
            Matrix.ofUInt64(new ULong[][] {{ulong(0L), ulong(0L)}, {ulong(0L), ulong(0L)}}),
            Matrix.ofFloat(new Float[][] {{0.0f, 0.0f}, {0.0f, 0.0f}}),
            Matrix.ofDouble(new Double[][] {{0.0d, 0.0d}, {0.0d, 0.0d}}),
            Matrix.ofString(new String[][] {{"", ""}, {"", ""}}),
            Matrix.ofDateTime(
                new DateTime[][] {
                  {DateTime.MIN_DATE_TIME, DateTime.MIN_DATE_TIME},
                  {DateTime.MIN_DATE_TIME, DateTime.MIN_DATE_TIME}
                }),
            Matrix.ofGuid(
                new UUID[][] {
                  {
                    UUID.fromString("00000000-0000-0000-0000-000000000000"),
                    UUID.fromString("00000000-0000-0000-0000-000000000000")
                  },
                  {
                    UUID.fromString("00000000-0000-0000-0000-000000000000"),
                    UUID.fromString("00000000-0000-0000-0000-000000000000")
                  }
                }),
            Matrix.ofByteString(
                new ByteString[][] {
                  {ByteString.of(new byte[] {0}), ByteString.of(new byte[] {0})},
                  {ByteString.of(new byte[] {0}), ByteString.of(new byte[] {0})}
                }),
            Matrix.ofXmlElement(
                new XmlElement[][] {
                  {XmlElement.of(""), XmlElement.of("")},
                  {XmlElement.of(""), XmlElement.of("")}
                }),
            Matrix.ofNodeId(
                new NodeId[][] {
                  {new NodeId(0, 0), new NodeId(0, 0)},
                  {new NodeId(0, 0), new NodeId(0, 0)}
                }),
            Matrix.ofExpandedNodeId(
                new ExpandedNodeId[][] {
                  {
                    new ExpandedNodeId(ushort(0), null, uint(0)),
                    new ExpandedNodeId(ushort(0), null, uint(0))
                  },
                  {
                    new ExpandedNodeId(ushort(0), null, uint(0)),
                    new ExpandedNodeId(ushort(0), null, uint(0))
                  }
                }),
            Matrix.ofStatusCode(
                new StatusCode[][] {
                  {StatusCode.GOOD, StatusCode.GOOD},
                  {StatusCode.GOOD, StatusCode.GOOD}
                }),
            Matrix.ofQualifiedName(
                new QualifiedName[][] {
                  {new QualifiedName(0, ""), new QualifiedName(0, "")},
                  {new QualifiedName(0, ""), new QualifiedName(0, "")}
                }),
            Matrix.ofLocalizedText(
                new LocalizedText[][] {
                  {LocalizedText.NULL_VALUE, LocalizedText.NULL_VALUE},
                  {LocalizedText.NULL_VALUE, LocalizedText.NULL_VALUE}
                }),
            Matrix.ofDataValue(
                new DataValue[][] {
                  {new DataValue(new Variant(0)), new DataValue(new Variant(0))},
                  {new DataValue(new Variant(0)), new DataValue(new Variant(0))}
                }),
            Matrix.ofVariant(
                new Variant[][] {
                  {Variant.ofInt32(0), Variant.ofInt32(0)},
                  {Variant.ofInt32(0), Variant.ofInt32(0)}
                }),
            Matrix.ofDouble(new Double[][] {{0.0, 0.0}, {0.0, 0.0}}),
            Matrix.ofEnum(
                new ApplicationType[][] {
                  {ApplicationType.Server, ApplicationType.Client},
                  {ApplicationType.Server, ApplicationType.Client}
                }),
            Matrix.ofEnum(
                new TestEnumType[][] {
                  {TestEnumType.A, TestEnumType.B},
                  {TestEnumType.A, TestEnumType.B}
                }),
            Matrix.ofStruct(
                new XVType[][] {
                  {new XVType(0.0d, 0.0f), new XVType(0.0d, 0.0f)},
                  {new XVType(0.0d, 0.0f), new XVType(0.0d, 0.0f)}
                }),
            Matrix.ofStruct(
                new ConcreteTestType[][] {
                  {
                    new ConcreteTestType((short) 0, 0.0, "", false),
                    new ConcreteTestType((short) 0, 0.0, "", false)
                  },
                  {
                    new ConcreteTestType((short) 0, 0.0, "", false),
                    new ConcreteTestType((short) 0, 0.0, "", false)
                  }
                }),
            Matrix.ofStruct(
                new UnionOfScalar[][] {
                  {UnionOfScalar.ofBoolean(false), UnionOfScalar.ofByte(ubyte(0))},
                  {UnionOfScalar.ofBoolean(false), UnionOfScalar.ofByte(ubyte(0))}
                }),
            Matrix.ofStruct(
                new UnionOfArray[][] {
                  {
                    UnionOfArray.ofBoolean(new Boolean[] {false, false}),
                    UnionOfArray.ofSByte(new Byte[] {0, 0})
                  },
                  {
                    UnionOfArray.ofBoolean(new Boolean[] {false, false}),
                    UnionOfArray.ofSByte(new Byte[] {0, 0})
                  }
                }),
            Matrix.ofOptionSetUI(
                new AccessLevelType[][] {
                  {AccessLevelType.of(), AccessLevelType.of()},
                  {AccessLevelType.of(), AccessLevelType.of()}
                }),
            Matrix.ofOptionSetUI(
                new AccessRestrictionType[][] {
                  {AccessRestrictionType.of(), AccessRestrictionType.of()},
                  {AccessRestrictionType.of(), AccessRestrictionType.of()}
                }),
            Matrix.ofOptionSetUI(
                new AccessLevelExType[][] {
                  {AccessLevelExType.of(), AccessLevelExType.of()},
                  {AccessLevelExType.of(), AccessLevelExType.of()}
                }),
            Matrix.ofUInt64(new ULong[][] {{ulong(0), ulong(0)}, {ulong(0), ulong(0)}}));

    builder.setValue(new DataValue(Variant.ofStruct(struct)));

    UaVariableNode variableNode = builder.build();

    getNodeManager().addNode(variableNode);

    variableNode.addReference(
        new Reference(
            variableNode.getNodeId(),
            ReferenceTypes.HasComponent,
            parentNodeId.expanded(),
            Direction.INVERSE));
  }

  private void addStructWithOptionalScalarFields(NodeId parentNodeId) {
    var builder = new UaVariableNodeBuilder(getNodeContext());
    builder
        .setNodeId(new NodeId(namespaceIndex, "StructWithOptionalScalarFields"))
        .setBrowseName(new QualifiedName(namespaceIndex, "StructWithOptionalScalarFields"))
        .setDisplayName(LocalizedText.english("StructWithOptionalScalarFields"))
        .setDescription(LocalizedText.english("StructWithOptionalScalarFields"))
        .setDataType(
            DataTypeTestNodeIds.StructWithOptionalScalarFields.toNodeId(
                    getNodeContext().getNamespaceTable())
                .orElseThrow())
        .setAccessLevel(AccessLevel.toValue(AccessLevel.READ_WRITE))
        .setUserAccessLevel(AccessLevel.toValue(AccessLevel.READ_WRITE))
        .setMinimumSamplingInterval(0.0);

    var struct =
        new StructWithOptionalScalarFields(
            "",
            "",
            0,
            0,
            0.0,
            0.0,
            new ConcreteTestType((short) 0, 0.0, "", false),
            new ConcreteTestType((short) 0, 0.0, "", false));

    builder.setValue(new DataValue(Variant.ofStruct(struct)));

    UaVariableNode variableNode = builder.build();

    getNodeManager().addNode(variableNode);

    variableNode.addReference(
        new Reference(
            variableNode.getNodeId(),
            ReferenceTypes.HasComponent,
            parentNodeId.expanded(),
            Direction.INVERSE));
  }

  private void addStructWithOptionalArrayFields(NodeId parentNodeId) {
    var builder = new UaVariableNodeBuilder(getNodeContext());
    builder
        .setNodeId(new NodeId(namespaceIndex, "StructWithOptionalArrayFields"))
        .setBrowseName(new QualifiedName(namespaceIndex, "StructWithOptionalArrayFields"))
        .setDisplayName(LocalizedText.english("StructWithOptionalArrayFields"))
        .setDescription(LocalizedText.english("StructWithOptionalArrayFields"))
        .setDataType(
            DataTypeTestNodeIds.StructWithOptionalArrayFields.toNodeId(
                    getNodeContext().getNamespaceTable())
                .orElseThrow())
        .setAccessLevel(AccessLevel.toValue(AccessLevel.READ_WRITE))
        .setUserAccessLevel(AccessLevel.toValue(AccessLevel.READ_WRITE))
        .setMinimumSamplingInterval(0.0);

    var struct =
        new StructWithOptionalArrayFields(
            new Integer[] {0, 0},
            new Integer[] {0, 0},
            new String[] {"", ""},
            new String[] {"", ""},
            new Double[] {0.0, 0.0},
            new Double[] {0.0, 0.0},
            new ConcreteTestType[] {
              new ConcreteTestType((short) 0, 0.0, "", false),
              new ConcreteTestType((short) 0, 0.0, "", false)
            },
            new ConcreteTestType[] {
              new ConcreteTestType((short) 0, 0.0, "", false),
              new ConcreteTestType((short) 0, 0.0, "", false)
            });

    builder.setValue(new DataValue(Variant.ofStruct(struct)));

    UaVariableNode variableNode = builder.build();

    getNodeManager().addNode(variableNode);

    variableNode.addReference(
        new Reference(
            variableNode.getNodeId(),
            ReferenceTypes.HasComponent,
            parentNodeId.expanded(),
            Direction.INVERSE));
  }

  private void addStructWithOptionalMatrixFields(NodeId parentNodeId) {
    var builder = new UaVariableNodeBuilder(getNodeContext());
    builder
        .setNodeId(new NodeId(namespaceIndex, "StructWithOptionalMatrixFields"))
        .setBrowseName(new QualifiedName(namespaceIndex, "StructWithOptionalMatrixFields"))
        .setDisplayName(LocalizedText.english("StructWithOptionalMatrixFields"))
        .setDescription(LocalizedText.english("StructWithOptionalMatrixFields"))
        .setDataType(
            DataTypeTestNodeIds.StructWithOptionalMatrixFields.toNodeId(
                    getNodeContext().getNamespaceTable())
                .orElseThrow())
        .setAccessLevel(AccessLevel.toValue(AccessLevel.READ_WRITE))
        .setUserAccessLevel(AccessLevel.toValue(AccessLevel.READ_WRITE))
        .setMinimumSamplingInterval(0.0);

    var struct =
        new StructWithOptionalMatrixFields(
            Matrix.ofInt32(new Integer[][] {{0, 0}, {0, 0}}),
            Matrix.ofInt32(new Integer[][] {{0, 0}, {0, 0}}),
            Matrix.ofString(new String[][] {{"", ""}, {"", ""}}),
            Matrix.ofString(new String[][] {{"", ""}, {"", ""}}),
            Matrix.ofDouble(new Double[][] {{0.0, 0.0}, {0.0, 0.0}}),
            Matrix.ofDouble(new Double[][] {{0.0, 0.0}, {0.0, 0.0}}),
            Matrix.ofStruct(
                new ConcreteTestType[][] {
                  {
                    new ConcreteTestType((short) 0, 0.0, "", false),
                    new ConcreteTestType((short) 0, 0.0, "", false)
                  },
                  {
                    new ConcreteTestType((short) 0, 0.0, "", false),
                    new ConcreteTestType((short) 0, 0.0, "", false)
                  }
                }),
            Matrix.ofStruct(
                new ConcreteTestType[][] {
                  {
                    new ConcreteTestType((short) 0, 0.0, "", false),
                    new ConcreteTestType((short) 0, 0.0, "", false)
                  },
                  {
                    new ConcreteTestType((short) 0, 0.0, "", false),
                    new ConcreteTestType((short) 0, 0.0, "", false)
                  }
                }));

    builder.setValue(new DataValue(Variant.ofStruct(struct)));

    UaVariableNode variableNode = builder.build();

    getNodeManager().addNode(variableNode);

    variableNode.addReference(
        new Reference(
            variableNode.getNodeId(),
            ReferenceTypes.HasComponent,
            parentNodeId.expanded(),
            Direction.INVERSE));
  }

  private void addStructWithStructureScalarFields(NodeId parentNodeId) {
    var builder = new UaVariableNodeBuilder(getNodeContext());
    builder
        .setNodeId(new NodeId(namespaceIndex, "StructWithStructureScalarFields"))
        .setBrowseName(new QualifiedName(namespaceIndex, "StructWithStructureScalarFields"))
        .setDisplayName(LocalizedText.english("StructWithStructureScalarFields"))
        .setDescription(LocalizedText.english("StructWithStructureScalarFields"))
        .setDataType(
            DataTypeTestNodeIds.StructWithStructureScalarFields.toNodeId(
                    getNodeContext().getNamespaceTable())
                .orElseThrow())
        .setAccessLevel(AccessLevel.toValue(AccessLevel.READ_WRITE))
        .setUserAccessLevel(AccessLevel.toValue(AccessLevel.READ_WRITE))
        .setMinimumSamplingInterval(0.0);

    EncodingContext encodingContext = getNodeContext().getServer().getStaticEncodingContext();

    var struct =
        new StructWithStructureScalarFields(
            ExtensionObject.encode(encodingContext, new XVType(0.0d, 0.0f)),
            ExtensionObject.encode(
                encodingContext, new ConcreteTestType((short) 0, 0.0, "", false)),
            ExtensionObject.encode(
                encodingContext, new ConcreteTestTypeEx((short) 0, 0.0, "", false, uint(0))));

    builder.setValue(new DataValue(Variant.ofStruct(struct)));

    UaVariableNode variableNode = builder.build();

    getNodeManager().addNode(variableNode);

    variableNode.addReference(
        new Reference(
            variableNode.getNodeId(),
            ReferenceTypes.HasComponent,
            parentNodeId.expanded(),
            Direction.INVERSE));
  }

  private void addStructWithStructureArrayFields(NodeId parentNodeId) {
    var builder = new UaVariableNodeBuilder(getNodeContext());
    builder
        .setNodeId(new NodeId(namespaceIndex, "StructWithStructureArrayFields"))
        .setBrowseName(new QualifiedName(namespaceIndex, "StructWithStructureArrayFields"))
        .setDisplayName(LocalizedText.english("StructWithStructureArrayFields"))
        .setDescription(LocalizedText.english("StructWithStructureArrayFields"))
        .setDataType(
            DataTypeTestNodeIds.StructWithStructureArrayFields.toNodeId(
                    getNodeContext().getNamespaceTable())
                .orElseThrow())
        .setAccessLevel(AccessLevel.toValue(AccessLevel.READ_WRITE))
        .setUserAccessLevel(AccessLevel.toValue(AccessLevel.READ_WRITE))
        .setMinimumSamplingInterval(0.0);

    EncodingContext encodingContext = getNodeContext().getServer().getStaticEncodingContext();

    var xo1 = ExtensionObject.encode(encodingContext, new XVType(0.0d, 0.0f));
    var xo2 =
        ExtensionObject.encode(encodingContext, new ConcreteTestType((short) 0, 0.0, "", false));
    var xo3 =
        ExtensionObject.encode(
            encodingContext, new ConcreteTestTypeEx((short) 0, 0.0, "", false, uint(0)));
    var struct =
        new StructWithStructureArrayFields(
            new ExtensionObject[] {xo1, xo1},
            new ExtensionObject[] {xo2, xo2},
            new ExtensionObject[] {xo3, xo3});

    builder.setValue(new DataValue(Variant.ofStruct(struct)));

    UaVariableNode variableNode = builder.build();

    getNodeManager().addNode(variableNode);

    variableNode.addReference(
        new Reference(
            variableNode.getNodeId(),
            ReferenceTypes.HasComponent,
            parentNodeId.expanded(),
            Direction.INVERSE));
  }

  private void addStructWithStructureMatrixFields(NodeId parentNodeId) {
    var builder = new UaVariableNodeBuilder(getNodeContext());
    builder
        .setNodeId(new NodeId(namespaceIndex, "StructWithStructureMatrixFields"))
        .setBrowseName(new QualifiedName(namespaceIndex, "StructWithStructureMatrixFields"))
        .setDisplayName(LocalizedText.english("StructWithStructureMatrixFields"))
        .setDescription(LocalizedText.english("StructWithStructureMatrixFields"))
        .setDataType(
            DataTypeTestNodeIds.StructWithStructureMatrixFields.toNodeId(
                    getNodeContext().getNamespaceTable())
                .orElseThrow())
        .setAccessLevel(AccessLevel.toValue(AccessLevel.READ_WRITE))
        .setUserAccessLevel(AccessLevel.toValue(AccessLevel.READ_WRITE))
        .setMinimumSamplingInterval(0.0);

    EncodingContext encodingContext = getNodeContext().getServer().getStaticEncodingContext();

    var xo1 = ExtensionObject.encode(encodingContext, new XVType(0.0d, 0.0f));
    var xo2 =
        ExtensionObject.encode(encodingContext, new ConcreteTestType((short) 0, 0.0, "", false));
    var xo3 =
        ExtensionObject.encode(
            encodingContext, new ConcreteTestTypeEx((short) 0, 0.0, "", false, uint(0)));
    var struct =
        new StructWithStructureMatrixFields(
            Matrix.ofExtensionObject(new ExtensionObject[][] {{xo1, xo1}, {xo1, xo1}}),
            Matrix.ofExtensionObject(new ExtensionObject[][] {{xo2, xo2}, {xo2, xo2}}),
            Matrix.ofExtensionObject(new ExtensionObject[][] {{xo3, xo3}, {xo3, xo3}}));

    builder.setValue(new DataValue(Variant.ofStruct(struct)));

    UaVariableNode variableNode = builder.build();

    getNodeManager().addNode(variableNode);

    variableNode.addReference(
        new Reference(
            variableNode.getNodeId(),
            ReferenceTypes.HasComponent,
            parentNodeId.expanded(),
            Direction.INVERSE));
  }
}

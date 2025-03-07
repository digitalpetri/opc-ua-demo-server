package com.digitalpetri.opcua.server.namespace;

import static com.digitalpetri.opcua.server.namespace.Util.deriveChildNodeId;
import static org.eclipse.milo.opcua.stack.core.types.builtin.DateTime.*;
import static org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.Unsigned.ubyte;
import static org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.Unsigned.uint;
import static org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.Unsigned.ulong;
import static org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.Unsigned.ushort;

import java.lang.reflect.Array;
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
import org.eclipse.milo.opcua.sdk.server.methods.AbstractMethodInvocationHandler;
import org.eclipse.milo.opcua.sdk.server.model.variables.AnalogItemType;
import org.eclipse.milo.opcua.sdk.server.model.variables.AnalogItemTypeNode;
import org.eclipse.milo.opcua.sdk.server.nodes.UaFolderNode;
import org.eclipse.milo.opcua.sdk.server.nodes.UaMethodNode;
import org.eclipse.milo.opcua.sdk.server.nodes.UaMethodNode.UaMethodNodeBuilder;
import org.eclipse.milo.opcua.sdk.server.nodes.UaNode;
import org.eclipse.milo.opcua.sdk.server.nodes.UaVariableNode;
import org.eclipse.milo.opcua.sdk.server.nodes.UaVariableNode.UaVariableNodeBuilder;
import org.eclipse.milo.opcua.sdk.server.nodes.filters.AttributeFilter;
import org.eclipse.milo.opcua.sdk.server.nodes.filters.AttributeFilterContext;
import org.eclipse.milo.opcua.sdk.server.util.SubscriptionModel;
import org.eclipse.milo.opcua.stack.core.AttributeId;
import org.eclipse.milo.opcua.stack.core.NodeIds;
import org.eclipse.milo.opcua.stack.core.OpcUaDataType;
import org.eclipse.milo.opcua.stack.core.ReferenceTypes;
import org.eclipse.milo.opcua.stack.core.StatusCodes;
import org.eclipse.milo.opcua.stack.core.UaException;
import org.eclipse.milo.opcua.stack.core.types.builtin.ByteString;
import org.eclipse.milo.opcua.stack.core.types.builtin.DataValue;
import org.eclipse.milo.opcua.stack.core.types.builtin.DiagnosticInfo;
import org.eclipse.milo.opcua.stack.core.types.builtin.ExpandedNodeId;
import org.eclipse.milo.opcua.stack.core.types.builtin.ExtensionObject;
import org.eclipse.milo.opcua.stack.core.types.builtin.LocalizedText;
import org.eclipse.milo.opcua.stack.core.types.builtin.Matrix;
import org.eclipse.milo.opcua.stack.core.types.builtin.NodeId;
import org.eclipse.milo.opcua.stack.core.types.builtin.QualifiedName;
import org.eclipse.milo.opcua.stack.core.types.builtin.StatusCode;
import org.eclipse.milo.opcua.stack.core.types.builtin.Variant;
import org.eclipse.milo.opcua.stack.core.types.builtin.XmlElement;
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.UInteger;
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.UShort;
import org.eclipse.milo.opcua.stack.core.types.structured.Argument;
import org.eclipse.milo.opcua.stack.core.types.structured.Range;
import org.jspecify.annotations.Nullable;

public class CttNodesFragment extends ManagedAddressSpaceFragmentWithLifecycle {

  private final SimpleAddressSpaceFilter filter;
  private final SubscriptionModel subscriptionModel;

  private final UShort namespaceIndex;

  public CttNodesFragment(OpcUaServer server, DemoNamespace namespace) {
    super(server, namespace);

    this.namespaceIndex = namespace.getNamespaceIndex();

    filter = SimpleAddressSpaceFilter.create(getNodeManager()::containsNode);

    subscriptionModel = new SubscriptionModel(server, this);
    getLifecycleManager().addLifecycle(subscriptionModel);

    getLifecycleManager().addStartupTask(this::addCttNodes);
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

  private void addCttNodes() {
    var cttFolder =
        new UaFolderNode(
            getNodeContext(),
            new NodeId(namespaceIndex, "CTT"),
            new QualifiedName(namespaceIndex, "CTT"),
            new LocalizedText("CTT"));

    getNodeManager().addNode(cttFolder);

    cttFolder.addReference(
        new Reference(
            cttFolder.getNodeId(),
            ReferenceTypes.HasComponent,
            NodeIds.ObjectsFolder.expanded(),
            Direction.INVERSE));

    addStaticNodes(cttFolder.getNodeId());
    addMethodNodes(cttFolder.getNodeId());
    addSecurityAccessNodes(cttFolder.getNodeId());
  }

  // region Static

  private void addStaticNodes(NodeId parentNodeId) {
    var staticFolder =
        new UaFolderNode(
            getNodeContext(),
            deriveChildNodeId(parentNodeId, "Static"),
            new QualifiedName(namespaceIndex, "Static"),
            new LocalizedText("Static"));

    getNodeManager().addNode(staticFolder);

    staticFolder.addReference(
        new Reference(
            staticFolder.getNodeId(),
            ReferenceTypes.HasComponent,
            parentNodeId.expanded(),
            Direction.INVERSE));

    addAllProfilesNodes(staticFolder.getNodeId());
    addDataAccessProfileNodes(staticFolder.getNodeId());
    addReferencesNodes(staticFolder.getNodeId());
    addPathsNodes(staticFolder.getNodeId());
  }

  // region Static -> AllProfiles

  private void addAllProfilesNodes(NodeId parentNodeId) {
    var allProfilesFolder =
        new UaFolderNode(
            getNodeContext(),
            deriveChildNodeId(parentNodeId, "AllProfiles"),
            new QualifiedName(namespaceIndex, "AllProfiles"),
            new LocalizedText("AllProfiles"));

    getNodeManager().addNode(allProfilesFolder);

    allProfilesFolder.addReference(
        new Reference(
            allProfilesFolder.getNodeId(),
            ReferenceTypes.HasComponent,
            parentNodeId.expanded(),
            Direction.INVERSE));

    addScalarNodes(allProfilesFolder.getNodeId());
    addArrayNodes(allProfilesFolder.getNodeId());
    addMatrixNodes(allProfilesFolder.getNodeId());
  }

  private void addScalarNodes(NodeId parentNodeId) {
    var scalarFolder =
        new UaFolderNode(
            getNodeContext(),
            deriveChildNodeId(parentNodeId, "Scalar"),
            new QualifiedName(namespaceIndex, "Scalar"),
            new LocalizedText("Scalar"));

    getNodeManager().addNode(scalarFolder);

    scalarFolder.addReference(
        new Reference(
            scalarFolder.getNodeId(),
            ReferenceTypes.HasComponent,
            parentNodeId.expanded(),
            Direction.INVERSE));

    for (OpcUaDataType dataType : OpcUaDataType.values()) {
      if (dataType == OpcUaDataType.DiagnosticInfo) continue;

      var builder = new UaVariableNodeBuilder(getNodeContext());
      builder
          .setNodeId(deriveChildNodeId(scalarFolder.getNodeId(), dataType.name()))
          .setBrowseName(new QualifiedName(namespaceIndex, dataType.name()))
          .setDisplayName(new LocalizedText(dataType.name()))
          .setDataType(dataType.getNodeId())
          .setAccessLevel(AccessLevel.toValue(AccessLevel.READ_WRITE))
          .setUserAccessLevel(AccessLevel.toValue(AccessLevel.READ_WRITE))
          .setMinimumSamplingInterval(100.0);

      Object value = getDefaultScalarValue(dataType);
      if (value instanceof Variant v) {
        builder.setValue(new DataValue(v));
      } else {
        builder.setValue(new DataValue(Variant.of(value)));
      }

      UaVariableNode variableNode = builder.build();

      getNodeManager().addNode(variableNode);

      variableNode.addReference(
          new Reference(
              variableNode.getNodeId(),
              ReferenceTypes.HasComponent,
              scalarFolder.getNodeId().expanded(),
              Direction.INVERSE));
    }

    // Add abstract type Integer
    {
      var builder = new UaVariableNodeBuilder(getNodeContext());
      builder
          .setNodeId(deriveChildNodeId(scalarFolder.getNodeId(), "Integer"))
          .setBrowseName(new QualifiedName(namespaceIndex, "Integer"))
          .setDisplayName(new LocalizedText("Integer"))
          .setDataType(NodeIds.Integer)
          .setAccessLevel(AccessLevel.toValue(AccessLevel.READ_WRITE))
          .setUserAccessLevel(AccessLevel.toValue(AccessLevel.READ_WRITE))
          .setMinimumSamplingInterval(100.0);

      builder.setValue(new DataValue(Variant.of(Integer.MIN_VALUE)));

      UaVariableNode variableNode = builder.build();

      getNodeManager().addNode(variableNode);

      variableNode.addReference(
          new Reference(
              variableNode.getNodeId(),
              ReferenceTypes.HasComponent,
              scalarFolder.getNodeId().expanded(),
              Direction.INVERSE));
    }

    // Add abstract type UInteger
    {
      var builder = new UaVariableNodeBuilder(getNodeContext());
      builder
          .setNodeId(deriveChildNodeId(scalarFolder.getNodeId(), "UInteger"))
          .setBrowseName(new QualifiedName(namespaceIndex, "UInteger"))
          .setDisplayName(new LocalizedText("UInteger"))
          .setDataType(NodeIds.UInteger)
          .setAccessLevel(AccessLevel.toValue(AccessLevel.READ_WRITE))
          .setUserAccessLevel(AccessLevel.toValue(AccessLevel.READ_WRITE))
          .setMinimumSamplingInterval(100.0);

      builder.setValue(new DataValue(Variant.of(UInteger.MIN_VALUE)));

      UaVariableNode variableNode = builder.build();

      getNodeManager().addNode(variableNode);

      variableNode.addReference(
          new Reference(
              variableNode.getNodeId(),
              ReferenceTypes.HasComponent,
              scalarFolder.getNodeId().expanded(),
              Direction.INVERSE));
    }
  }

  private void addArrayNodes(NodeId parentNodeId) {
    var arrayFolder =
        new UaFolderNode(
            getNodeContext(),
            deriveChildNodeId(parentNodeId, "Array"),
            new QualifiedName(namespaceIndex, "Array"),
            new LocalizedText("Array"));

    getNodeManager().addNode(arrayFolder);

    arrayFolder.addReference(
        new Reference(
            arrayFolder.getNodeId(),
            ReferenceTypes.HasComponent,
            parentNodeId.expanded(),
            Direction.INVERSE));

    for (OpcUaDataType dataType : OpcUaDataType.values()) {
      if (dataType == OpcUaDataType.DiagnosticInfo) continue;

      var builder = new UaVariableNodeBuilder(getNodeContext());
      builder
          .setNodeId(deriveChildNodeId(arrayFolder.getNodeId(), dataType.name() + "Array"))
          .setBrowseName(new QualifiedName(namespaceIndex, dataType.name() + "Array"))
          .setDisplayName(new LocalizedText(dataType.name() + "Array"))
          .setDataType(dataType.getNodeId())
          .setValueRank(ValueRanks.OneDimension)
          .setArrayDimensions(new UInteger[] {uint(0)})
          .setAccessLevel(AccessLevel.toValue(AccessLevel.READ_WRITE))
          .setUserAccessLevel(AccessLevel.toValue(AccessLevel.READ_WRITE))
          .setMinimumSamplingInterval(100.0);

      Object value = getDefaultArrayValue(dataType);
      if (value instanceof Variant v) {
        builder.setValue(new DataValue(v));
      } else {
        builder.setValue(new DataValue(Variant.of(value)));
      }

      UaVariableNode variableNode = builder.build();

      getNodeManager().addNode(variableNode);

      variableNode.addReference(
          new Reference(
              variableNode.getNodeId(),
              ReferenceTypes.HasComponent,
              arrayFolder.getNodeId().expanded(),
              Direction.INVERSE));
    }
  }

  private void addMatrixNodes(NodeId parentNodeId) {
    var matrixFolder =
        new UaFolderNode(
            getNodeContext(),
            deriveChildNodeId(parentNodeId, "Matrix"),
            new QualifiedName(namespaceIndex, "Matrix"),
            new LocalizedText("Matrix"));

    getNodeManager().addNode(matrixFolder);

    matrixFolder.addReference(
        new Reference(
            matrixFolder.getNodeId(),
            ReferenceTypes.HasComponent,
            parentNodeId.expanded(),
            Direction.INVERSE));

    for (OpcUaDataType dataType : OpcUaDataType.values()) {
      if (dataType == OpcUaDataType.DiagnosticInfo) continue;

      var builder = new UaVariableNodeBuilder(getNodeContext());
      builder
          .setNodeId(deriveChildNodeId(matrixFolder.getNodeId(), dataType.name() + "Matrix"))
          .setBrowseName(new QualifiedName(namespaceIndex, dataType.name() + "Matrix"))
          .setDisplayName(new LocalizedText(dataType.name() + "Matrix"))
          .setDataType(dataType.getNodeId())
          .setValueRank(2)
          .setArrayDimensions(new UInteger[] {uint(0), uint(0)})
          .setAccessLevel(AccessLevel.toValue(AccessLevel.READ_WRITE))
          .setUserAccessLevel(AccessLevel.toValue(AccessLevel.READ_WRITE))
          .setMinimumSamplingInterval(100.0);

      Matrix value = getDefaultMatrixValue(dataType);
      builder.setValue(new DataValue(Variant.ofMatrix(value)));

      UaVariableNode variableNode = builder.build();

      getNodeManager().addNode(variableNode);

      variableNode.addReference(
          new Reference(
              variableNode.getNodeId(),
              ReferenceTypes.HasComponent,
              matrixFolder.getNodeId().expanded(),
              Direction.INVERSE));
    }
  }

  // endregion

  // region Static -> DataAccess

  private void addDataAccessProfileNodes(NodeId parentNodeId) {
    var dataAccessFolder =
        new UaFolderNode(
            getNodeContext(),
            deriveChildNodeId(parentNodeId, "DataAccessProfile"),
            new QualifiedName(namespaceIndex, "DataAccessProfile"),
            new LocalizedText("DataAccessProfile"));

    getNodeManager().addNode(dataAccessFolder);

    dataAccessFolder.addReference(
        new Reference(
            dataAccessFolder.getNodeId(),
            ReferenceTypes.HasComponent,
            parentNodeId.expanded(),
            Direction.INVERSE));

    try {
      addAnalogItemTypeNodes(dataAccessFolder.getNodeId());
    } catch (UaException e) {
      throw new RuntimeException(e);
    }
  }

  private void addAnalogItemTypeNodes(NodeId parentNodeId) throws UaException {
    var analogTypes =
        List.of(
            OpcUaDataType.Byte,
            OpcUaDataType.Double,
            OpcUaDataType.Float,
            OpcUaDataType.Int16,
            OpcUaDataType.Int32,
            OpcUaDataType.Int64,
            OpcUaDataType.SByte,
            OpcUaDataType.UInt16,
            OpcUaDataType.UInt32,
            OpcUaDataType.UInt64);

    for (OpcUaDataType dataType : analogTypes) {
      UaNode node =
          getNodeFactory()
              .createNode(
                  deriveChildNodeId(parentNodeId, dataType.name() + "Analog"),
                  NodeIds.AnalogItemType);

      if (node instanceof AnalogItemTypeNode analogItemNode) {
        analogItemNode.setBrowseName(new QualifiedName(namespaceIndex, dataType.name() + "Analog"));
        analogItemNode.setDisplayName(new LocalizedText(dataType.name() + "Analog"));
        analogItemNode.setDataType(dataType.getNodeId());
        analogItemNode.setAccessLevel(AccessLevel.toValue(AccessLevel.READ_WRITE));
        analogItemNode.setUserAccessLevel(AccessLevel.toValue(AccessLevel.READ_WRITE));
        analogItemNode.setMinimumSamplingInterval(100.0);

        analogItemNode.setEuRange(new Range(0.0, 100.0));
        analogItemNode.setValue(new DataValue(Variant.of(getDefaultScalarValue(dataType))));

        analogItemNode.getFilterChain().addLast(EuRangeCheckFilter.INSTANCE);

        getNodeManager().addNode(analogItemNode);

        analogItemNode.addReference(
            new Reference(
                analogItemNode.getNodeId(),
                ReferenceTypes.HasComponent,
                parentNodeId.expanded(),
                Direction.INVERSE));
      }
    }
  }

  // endregion

  // region References

  private void addReferencesNodes(NodeId parentNodeId) {
    var referencesFolder =
        new UaFolderNode(
            getNodeContext(),
            deriveChildNodeId(parentNodeId, "References"),
            new QualifiedName(namespaceIndex, "References"),
            new LocalizedText("References"));

    getNodeManager().addNode(referencesFolder);

    referencesFolder.addReference(
        new Reference(
            referencesFolder.getNodeId(),
            ReferenceTypes.HasComponent,
            parentNodeId.expanded(),
            Direction.INVERSE));

    addHasForwardReferencesNodes(referencesFolder.getNodeId());
    addHasInverseAndForwardReferencesNodes(referencesFolder.getNodeId());
  }

  private void addHasForwardReferencesNodes(NodeId parentNodeId) {
    for (int i = 1; i <= 5; i++) {
      var folder =
          new UaFolderNode(
              getNodeContext(),
              deriveChildNodeId(parentNodeId, "Has3ForwardReferences%d".formatted(i)),
              new QualifiedName(namespaceIndex, "Has3ForwardReferences%d".formatted(i)),
              new LocalizedText("Has3ForwardReferences%d".formatted(i).formatted(i)));

      getNodeManager().addNode(folder);

      folder.addReference(
          new Reference(
              folder.getNodeId(),
              ReferenceTypes.HasComponent,
              parentNodeId.expanded(),
              Direction.INVERSE));

      for (int j = 0; j < 3; j++) {
        var builder = new UaVariableNodeBuilder(getNodeContext());
        builder
            .setNodeId(deriveChildNodeId(folder.getNodeId(), "Variable%d".formatted(j)))
            .setBrowseName(new QualifiedName(namespaceIndex, "Variable%d".formatted(j)))
            .setDisplayName(new LocalizedText("Variable%d".formatted(j)))
            .setDataType(NodeIds.Int32);

        builder.setValue(new DataValue(Variant.ofInt32(j)));

        UaVariableNode variableNode = builder.build();

        getNodeManager().addNode(variableNode);

        variableNode.addReference(
            new Reference(
                variableNode.getNodeId(),
                ReferenceTypes.HasComponent,
                folder.getNodeId().expanded(),
                Direction.INVERSE));
      }
    }
  }

  private void addHasInverseAndForwardReferencesNodes(NodeId parentNodeId) {
    var folder =
        new UaFolderNode(
            getNodeContext(),
            deriveChildNodeId(parentNodeId, "HasInverseAndForwardReferences"),
            new QualifiedName(namespaceIndex, "HasInverseAndForwardReferences"),
            new LocalizedText("HasInverseAndForwardReferences"));

    getNodeManager().addNode(folder);

    folder.addReference(
        new Reference(
            folder.getNodeId(),
            ReferenceTypes.HasComponent,
            parentNodeId.expanded(),
            Direction.INVERSE));

    for (int i = 0; i < 3; i++) {
      var builder = new UaVariableNodeBuilder(getNodeContext());
      builder
          .setNodeId(deriveChildNodeId(folder.getNodeId(), "Variable%d".formatted(i)))
          .setBrowseName(new QualifiedName(namespaceIndex, "Variable%d".formatted(i)))
          .setDisplayName(new LocalizedText("Variable%d".formatted(i)))
          .setDataType(NodeIds.Int32);

      builder.setValue(new DataValue(Variant.ofInt32(i)));

      UaVariableNode variableNode = builder.build();

      getNodeManager().addNode(variableNode);

      variableNode.addReference(
          new Reference(
              variableNode.getNodeId(),
              ReferenceTypes.HasComponent,
              folder.getNodeId().expanded(),
              Direction.INVERSE));
    }
  }

  // endregion

  // region Paths

  private void addPathsNodes(NodeId parentNodeId) {
    var pathsFolder =
        new UaFolderNode(
            getNodeContext(),
            deriveChildNodeId(parentNodeId, "Paths"),
            new QualifiedName(namespaceIndex, "Paths"),
            new LocalizedText("Paths"));

    getNodeManager().addNode(pathsFolder);

    pathsFolder.addReference(
        new Reference(
            pathsFolder.getNodeId(),
            ReferenceTypes.HasComponent,
            parentNodeId.expanded(),
            Direction.INVERSE));

    // Add 10 folders each recursively underneath the next one.
    NodeId currentParent = pathsFolder.getNodeId();

    for (int i = 0; i < 10; i++) {
      var folder =
          new UaFolderNode(
              getNodeContext(),
              deriveChildNodeId(currentParent, "Folder%d".formatted(i)),
              new QualifiedName(namespaceIndex, "Folder%d".formatted(i)),
              new LocalizedText("Folder%d".formatted(i)));

      getNodeManager().addNode(folder);

      folder.addReference(
          new Reference(
              folder.getNodeId(),
              ReferenceTypes.HasComponent,
              currentParent.expanded(),
              Direction.INVERSE));

      currentParent = folder.getNodeId();
    }
  }

  // endregion

  // endregion

  // region Methods

  private void addMethodNodes(NodeId parentNodeId) {
    var methodFolder =
        new UaFolderNode(
            getNodeContext(),
            deriveChildNodeId(parentNodeId, "Methods"),
            new QualifiedName(namespaceIndex, "Methods"),
            new LocalizedText("Methods"));

    getNodeManager().addNode(methodFolder);

    methodFolder.addReference(
        new Reference(
            methodFolder.getNodeId(),
            ReferenceTypes.HasComponent,
            parentNodeId.expanded(),
            Direction.INVERSE));

    addMethodNoArgs(methodFolder.getNodeId());
    addMethodI(methodFolder.getNodeId());
    addMethodO(methodFolder.getNodeId());
    addMethodIO(methodFolder.getNodeId());
  }

  private void addMethodNoArgs(NodeId parentNodeId) {
    var builder = new UaMethodNodeBuilder(getNodeContext());
    builder.setNodeId(deriveChildNodeId(parentNodeId, "MethodNoArgs"));
    builder.setBrowseName(new QualifiedName(namespaceIndex, "MethodNoArgs"));
    builder.setDisplayName(new LocalizedText("MethodNoArgs"));

    UaMethodNode methodNode = builder.build();

    methodNode.setInvocationHandler(
        new AbstractMethodInvocationHandler(methodNode) {
          @Override
          public Argument[] getInputArguments() {
            Argument[] inputArguments = methodNode.getInputArguments();
            return inputArguments != null ? inputArguments : new Argument[0];
          }

          @Override
          public Argument[] getOutputArguments() {
            Argument[] outputArguments = methodNode.getOutputArguments();
            return outputArguments != null ? outputArguments : new Argument[0];
          }

          @Override
          protected Variant[] invoke(InvocationContext invocationContext, Variant[] inputValues) {
            return new Variant[0];
          }
        });

    getNodeManager().addNode(methodNode);

    methodNode.addReference(
        new Reference(
            methodNode.getNodeId(),
            ReferenceTypes.HasComponent,
            parentNodeId.expanded(),
            Direction.INVERSE));
  }

  private void addMethodI(NodeId parentNodeId) {
    var builder = new UaMethodNodeBuilder(getNodeContext());
    builder.setNodeId(deriveChildNodeId(parentNodeId, "MethodI"));
    builder.setBrowseName(new QualifiedName(namespaceIndex, "MethodI"));
    builder.setDisplayName(new LocalizedText("MethodI"));

    UaMethodNode methodNode = builder.build();
    methodNode.setInputArguments(INPUT_ARGUMENTS);

    methodNode.setInvocationHandler(
        new AbstractMethodInvocationHandler(methodNode) {
          @Override
          public Argument[] getInputArguments() {
            Argument[] inputArguments = methodNode.getInputArguments();
            return inputArguments != null ? inputArguments : new Argument[0];
          }

          @Override
          public Argument[] getOutputArguments() {
            Argument[] outputArguments = methodNode.getOutputArguments();
            return outputArguments != null ? outputArguments : new Argument[0];
          }

          @Override
          protected Variant[] invoke(InvocationContext invocationContext, Variant[] inputValues) {
            return new Variant[0];
          }
        });

    getNodeManager().addNode(methodNode);

    methodNode.addReference(
        new Reference(
            methodNode.getNodeId(),
            ReferenceTypes.HasComponent,
            parentNodeId.expanded(),
            Direction.INVERSE));
  }

  private void addMethodO(NodeId parentNodeId) {
    var builder = new UaMethodNodeBuilder(getNodeContext());
    builder.setNodeId(deriveChildNodeId(parentNodeId, "MethodO"));
    builder.setBrowseName(new QualifiedName(namespaceIndex, "MethodO"));
    builder.setDisplayName(new LocalizedText("MethodO"));

    UaMethodNode methodNode = builder.build();
    methodNode.setOutputArguments(OUTPUT_ARGUMENTS);

    methodNode.setInvocationHandler(
        new AbstractMethodInvocationHandler(methodNode) {
          @Override
          public Argument[] getInputArguments() {
            Argument[] inputArguments = methodNode.getInputArguments();
            return inputArguments != null ? inputArguments : new Argument[0];
          }

          @Override
          public Argument[] getOutputArguments() {
            Argument[] outputArguments = methodNode.getOutputArguments();
            return outputArguments != null ? outputArguments : new Argument[0];
          }

          @Override
          protected Variant[] invoke(InvocationContext invocationContext, Variant[] inputValues) {
            return new Variant[] {Variant.ofInt32(42)};
          }
        });

    getNodeManager().addNode(methodNode);

    methodNode.addReference(
        new Reference(
            methodNode.getNodeId(),
            ReferenceTypes.HasComponent,
            parentNodeId.expanded(),
            Direction.INVERSE));
  }

  private void addMethodIO(NodeId parentNodeId) {
    var builder = new UaMethodNodeBuilder(getNodeContext());
    builder.setNodeId(deriveChildNodeId(parentNodeId, "MethodIO"));
    builder.setBrowseName(new QualifiedName(namespaceIndex, "MethodIO"));
    builder.setDisplayName(new LocalizedText("MethodIO"));

    UaMethodNode methodNode = builder.build();
    methodNode.setInputArguments(INPUT_ARGUMENTS);
    methodNode.setOutputArguments(OUTPUT_ARGUMENTS);

    methodNode.setInvocationHandler(
        new AbstractMethodInvocationHandler(methodNode) {
          @Override
          public Argument[] getInputArguments() {
            Argument[] inputArguments = methodNode.getInputArguments();
            return inputArguments != null ? inputArguments : new Argument[0];
          }

          @Override
          public Argument[] getOutputArguments() {
            Argument[] outputArguments = methodNode.getOutputArguments();
            return outputArguments != null ? outputArguments : new Argument[0];
          }

          @Override
          protected Variant[] invoke(InvocationContext invocationContext, Variant[] inputValues) {
            return new Variant[] {Variant.ofInt32((Integer) inputValues[0].getValue())};
          }
        });

    getNodeManager().addNode(methodNode);

    methodNode.addReference(
        new Reference(
            methodNode.getNodeId(),
            ReferenceTypes.HasComponent,
            parentNodeId.expanded(),
            Direction.INVERSE));
  }

  private static final Argument[] INPUT_ARGUMENTS = {
    new Argument("i", NodeIds.Int32, -1, null, null)
  };

  private static final Argument[] OUTPUT_ARGUMENTS = {
    new Argument("o", NodeIds.Int32, -1, null, null)
  };

  // endregion

  // region SecurityAccess

  private void addSecurityAccessNodes(NodeId parentNodeId) {
    var securityAccessFolder =
        new UaFolderNode(
            getNodeContext(),
            deriveChildNodeId(parentNodeId, "SecurityAccess"),
            new QualifiedName(namespaceIndex, "SecurityAccess"),
            new LocalizedText("SecurityAccess"));

    getNodeManager().addNode(securityAccessFolder);

    securityAccessFolder.addReference(
        new Reference(
            securityAccessFolder.getNodeId(),
            ReferenceTypes.HasComponent,
            parentNodeId.expanded(),
            Direction.INVERSE));

    // AccessLevel_CurrentRead
    {
      var builder = new UaVariableNodeBuilder(getNodeContext());
      builder
          .setNodeId(deriveChildNodeId(securityAccessFolder.getNodeId(), "AccessLevel_CurrentRead"))
          .setBrowseName(new QualifiedName(namespaceIndex, "AccessLevel_CurrentRead"))
          .setDisplayName(new LocalizedText("AccessLevel_CurrentRead"))
          .setDataType(NodeIds.Int32)
          .setValueRank(ValueRanks.Scalar)
          .setAccessLevel(AccessLevel.toValue(AccessLevel.CurrentRead))
          .setUserAccessLevel(AccessLevel.toValue(AccessLevel.CurrentRead))
          .setMinimumSamplingInterval(0.0);

      builder.setValue(new DataValue(Variant.ofInt32(0)));

      UaVariableNode variableNode = builder.build();

      getNodeManager().addNode(variableNode);

      variableNode.addReference(
          new Reference(
              variableNode.getNodeId(),
              ReferenceTypes.HasComponent,
              securityAccessFolder.getNodeId().expanded(),
              Direction.INVERSE));
    }

    // AccessLevel_CurrentWrite
    {
      var builder = new UaVariableNodeBuilder(getNodeContext());
      builder
          .setNodeId(
              deriveChildNodeId(securityAccessFolder.getNodeId(), "AccessLevel_CurrentWrite"))
          .setBrowseName(new QualifiedName(namespaceIndex, "AccessLevel_CurrentWrite"))
          .setDisplayName(new LocalizedText("AccessLevel_CurrentWrite"))
          .setDataType(NodeIds.Int32)
          .setValueRank(ValueRanks.Scalar)
          .setAccessLevel(AccessLevel.toValue(AccessLevel.CurrentWrite))
          .setUserAccessLevel(AccessLevel.toValue(AccessLevel.CurrentWrite))
          .setMinimumSamplingInterval(0.0);

      builder.setValue(new DataValue(Variant.ofInt32(0)));

      UaVariableNode variableNode = builder.build();

      getNodeManager().addNode(variableNode);

      variableNode.addReference(
          new Reference(
              variableNode.getNodeId(),
              ReferenceTypes.HasComponent,
              securityAccessFolder.getNodeId().expanded(),
              Direction.INVERSE));
    }

    // AccessLevel_CurrentRead_NotUser
    {
      var builder = new UaVariableNodeBuilder(getNodeContext());
      builder
          .setNodeId(
              deriveChildNodeId(
                  securityAccessFolder.getNodeId(), "AccessLevel_CurrentRead_NotUser"))
          .setBrowseName(new QualifiedName(namespaceIndex, "AccessLevel_CurrentRead_NotUser"))
          .setDisplayName(new LocalizedText("AccessLevel_CurrentRead_NotUser"))
          .setDataType(NodeIds.Int32)
          .setValueRank(ValueRanks.Scalar)
          .setAccessLevel(AccessLevel.toValue(AccessLevel.CurrentRead))
          .setUserAccessLevel(AccessLevel.toValue())
          .setMinimumSamplingInterval(0.0);

      builder.setValue(new DataValue(Variant.ofInt32(0)));

      UaVariableNode variableNode = builder.build();

      getNodeManager().addNode(variableNode);

      variableNode.addReference(
          new Reference(
              variableNode.getNodeId(),
              ReferenceTypes.HasComponent,
              securityAccessFolder.getNodeId().expanded(),
              Direction.INVERSE));
    }

    // AccessLevel_CurrentWrite_NotUser
    {
      var builder = new UaVariableNodeBuilder(getNodeContext());
      builder
          .setNodeId(
              deriveChildNodeId(
                  securityAccessFolder.getNodeId(), "AccessLevel_CurrentWrite_NotUser"))
          .setBrowseName(new QualifiedName(namespaceIndex, "AccessLevel_CurrentWrite_NotUser"))
          .setDisplayName(new LocalizedText("AccessLevel_CurrentWrite_NotUser"))
          .setDataType(NodeIds.Int32)
          .setValueRank(ValueRanks.Scalar)
          .setAccessLevel(AccessLevel.toValue(AccessLevel.CurrentWrite))
          .setUserAccessLevel(AccessLevel.toValue())
          .setMinimumSamplingInterval(0.0);

      builder.setValue(new DataValue(Variant.ofInt32(0)));

      UaVariableNode variableNode = builder.build();

      getNodeManager().addNode(variableNode);

      variableNode.addReference(
          new Reference(
              variableNode.getNodeId(),
              ReferenceTypes.HasComponent,
              securityAccessFolder.getNodeId().expanded(),
              Direction.INVERSE));
    }

    // AccessLevel_CurrentRead_NotCurrentWrite
    {
      var builder = new UaVariableNodeBuilder(getNodeContext());
      builder
          .setNodeId(
              deriveChildNodeId(
                  securityAccessFolder.getNodeId(), "AccessLevel_CurrentRead_NotCurrentWrite"))
          .setBrowseName(
              new QualifiedName(namespaceIndex, "AccessLevel_CurrentRead_NotCurrentWrite"))
          .setDisplayName(new LocalizedText("AccessLevel_CurrentRead_NotCurrentWrite"))
          .setDataType(NodeIds.Int32)
          .setValueRank(ValueRanks.Scalar)
          .setAccessLevel(AccessLevel.toValue(AccessLevel.CurrentRead, AccessLevel.CurrentWrite))
          .setUserAccessLevel(AccessLevel.toValue(AccessLevel.CurrentRead))
          .setMinimumSamplingInterval(0.0);

      builder.setValue(new DataValue(Variant.ofInt32(0)));

      UaVariableNode variableNode = builder.build();

      getNodeManager().addNode(variableNode);

      variableNode.addReference(
          new Reference(
              variableNode.getNodeId(),
              ReferenceTypes.HasComponent,
              securityAccessFolder.getNodeId().expanded(),
              Direction.INVERSE));
    }

    // AccessLevel_CurrentWrite_NotCurrentRead
    {
      var builder = new UaVariableNodeBuilder(getNodeContext());
      builder
          .setNodeId(
              deriveChildNodeId(
                  securityAccessFolder.getNodeId(), "AccessLevel_CurrentWrite_NotCurrentRead"))
          .setBrowseName(
              new QualifiedName(namespaceIndex, "AccessLevel_CurrentWrite_NotCurrentRead"))
          .setDisplayName(new LocalizedText("AccessLevel_CurrentWrite_NotCurrentRead"))
          .setDataType(NodeIds.Int32)
          .setValueRank(ValueRanks.Scalar)
          .setAccessLevel(AccessLevel.toValue(AccessLevel.CurrentWrite, AccessLevel.CurrentRead))
          .setUserAccessLevel(AccessLevel.toValue(AccessLevel.CurrentWrite))
          .setMinimumSamplingInterval(0.0);

      builder.setValue(new DataValue(Variant.ofInt32(0)));

      UaVariableNode variableNode = builder.build();

      getNodeManager().addNode(variableNode);

      variableNode.addReference(
          new Reference(
              variableNode.getNodeId(),
              ReferenceTypes.HasComponent,
              securityAccessFolder.getNodeId().expanded(),
              Direction.INVERSE));
    }
  }

  // endregion

  private static Object getDefaultScalarValue(OpcUaDataType dataType) {
    return switch (dataType) {
      case Boolean -> Boolean.FALSE;
      case SByte -> (byte) 0;
      case Int16 -> (short) 0;
      case Int32 -> 0;
      case Int64 -> 0L;
      case Byte -> ubyte(0);
      case UInt16 -> ushort(0);
      case UInt32 -> uint(0);
      case UInt64 -> ulong(0);
      case Float -> 0f;
      case Double -> 0.0;
      case String -> "";
      case DateTime -> NULL_VALUE;
      case Guid -> UUID.randomUUID();
      case ByteString -> ByteString.NULL_VALUE;
      case XmlElement -> new XmlElement(null);
      case NodeId -> NodeId.NULL_VALUE;
      case ExpandedNodeId -> ExpandedNodeId.NULL_VALUE;
      case StatusCode -> StatusCode.GOOD;
      case QualifiedName -> QualifiedName.NULL_VALUE;
      case LocalizedText -> LocalizedText.NULL_VALUE;
      case ExtensionObject -> new ExtensionObject(ByteString.NULL_VALUE, NodeId.NULL_VALUE);
      case DataValue -> new DataValue(Variant.NULL_VALUE);
      case Variant -> Variant.ofInt32(42);
      case DiagnosticInfo -> DiagnosticInfo.NULL_VALUE;
    };
  }

  private static Object getDefaultArrayValue(OpcUaDataType dataType) {
    Object value = getDefaultScalarValue(dataType);
    Object array = Array.newInstance(value.getClass(), 5);
    for (int i = 0; i < 5; i++) {
      Array.set(array, i, value);
    }
    return array;
  }

  private static Matrix getDefaultMatrixValue(OpcUaDataType dataType) {
    Object value = getDefaultScalarValue(dataType);
    Object array = Array.newInstance(value.getClass(), 5, 5);
    for (int i = 0; i < 5; i++) {
      Object innerArray = Array.newInstance(value.getClass(), 5);
      for (int j = 0; j < 5; j++) {
        Array.set(innerArray, j, value);
      }
      Array.set(array, i, innerArray);
    }
    return new Matrix(array);
  }

  private static class EuRangeCheckFilter implements AttributeFilter {

    private static final EuRangeCheckFilter INSTANCE = new EuRangeCheckFilter();

    @Override
    public void writeAttribute(
        AttributeFilterContext ctx, AttributeId attributeId, @Nullable Object value)
        throws UaException {

      UaNode node = ctx.getNode();

      if (attributeId == AttributeId.Value && node instanceof AnalogItemType analogItem) {
        if (value instanceof DataValue dataValue) {
          Object v = dataValue.getValue().getValue();

          if (v instanceof Number n) {
            Double low = analogItem.getEuRange().getLow();
            Double high = analogItem.getEuRange().getHigh();

            if (n.doubleValue() < low || n.doubleValue() > high) {
              throw new UaException(
                  StatusCodes.Bad_OutOfRange,
                  "value %s is out of range [%s, %s]".formatted(n, low, high));
            }
          } else {
            throw new UaException(
                StatusCodes.Bad_TypeMismatch, "value %s is not a number".formatted(v));
          }
        }
      }

      ctx.writeAttribute(attributeId, value);
    }
  }
}

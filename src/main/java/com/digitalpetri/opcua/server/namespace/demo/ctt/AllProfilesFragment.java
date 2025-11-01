package com.digitalpetri.opcua.server.namespace.demo.ctt;

import static com.digitalpetri.opcua.server.namespace.demo.Util.deriveChildNodeId;
import static org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.Unsigned.uint;

import com.digitalpetri.opcua.server.namespace.demo.Util;
import java.util.List;
import org.eclipse.milo.opcua.sdk.core.AccessLevel;
import org.eclipse.milo.opcua.sdk.core.Reference;
import org.eclipse.milo.opcua.sdk.core.Reference.Direction;
import org.eclipse.milo.opcua.sdk.core.ValueRanks;
import org.eclipse.milo.opcua.sdk.server.AddressSpaceComposite;
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
import org.eclipse.milo.opcua.stack.core.NodeIds;
import org.eclipse.milo.opcua.stack.core.OpcUaDataType;
import org.eclipse.milo.opcua.stack.core.ReferenceTypes;
import org.eclipse.milo.opcua.stack.core.types.builtin.DataValue;
import org.eclipse.milo.opcua.stack.core.types.builtin.LocalizedText;
import org.eclipse.milo.opcua.stack.core.types.builtin.Matrix;
import org.eclipse.milo.opcua.stack.core.types.builtin.NodeId;
import org.eclipse.milo.opcua.stack.core.types.builtin.QualifiedName;
import org.eclipse.milo.opcua.stack.core.types.builtin.Variant;
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.UInteger;
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.UShort;

public class AllProfilesFragment extends ManagedAddressSpaceFragmentWithLifecycle {

  private final SimpleAddressSpaceFilter filter;
  private final SubscriptionModel subscriptionModel;

  private final NodeId parentNodeId;
  private final UShort namespaceIndex;

  public AllProfilesFragment(
      OpcUaServer server,
      AddressSpaceComposite composite,
      NodeId parentNodeId,
      UShort namespaceIndex) {

    super(server, composite);

    this.parentNodeId = parentNodeId;
    this.namespaceIndex = namespaceIndex;

    filter = SimpleAddressSpaceFilter.create(getNodeManager()::containsNode);

    subscriptionModel = new SubscriptionModel(server, this);
    getLifecycleManager().addLifecycle(subscriptionModel);

    getLifecycleManager().addStartupTask(this::addNodes);
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

  private void addNodes() {
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

      Object value = Util.getDefaultScalarValue(dataType);
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

      Object value = Util.getDefaultArrayValue(dataType);
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

      Matrix value = Util.getDefaultMatrixValue(dataType);
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
}

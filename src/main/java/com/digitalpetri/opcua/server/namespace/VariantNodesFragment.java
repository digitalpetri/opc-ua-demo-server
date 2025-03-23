package com.digitalpetri.opcua.server.namespace;

import static com.digitalpetri.opcua.server.namespace.Util.deriveChildNodeId;
import static com.digitalpetri.opcua.server.namespace.Util.getDefaultScalarValue;
import static org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.Unsigned.uint;

import java.util.List;
import org.eclipse.milo.opcua.sdk.core.AccessLevel;
import org.eclipse.milo.opcua.sdk.core.Reference;
import org.eclipse.milo.opcua.sdk.core.ValueRanks;
import org.eclipse.milo.opcua.sdk.server.AddressSpaceFilter;
import org.eclipse.milo.opcua.sdk.server.ManagedAddressSpaceFragmentWithLifecycle;
import org.eclipse.milo.opcua.sdk.server.OpcUaServer;
import org.eclipse.milo.opcua.sdk.server.SimpleAddressSpaceFilter;
import org.eclipse.milo.opcua.sdk.server.items.DataItem;
import org.eclipse.milo.opcua.sdk.server.items.MonitoredItem;
import org.eclipse.milo.opcua.sdk.server.nodes.UaFolderNode;
import org.eclipse.milo.opcua.sdk.server.nodes.UaVariableNode.UaVariableNodeBuilder;
import org.eclipse.milo.opcua.sdk.server.util.SubscriptionModel;
import org.eclipse.milo.opcua.stack.core.OpcUaDataType;
import org.eclipse.milo.opcua.stack.core.ReferenceTypes;
import org.eclipse.milo.opcua.stack.core.types.builtin.DataValue;
import org.eclipse.milo.opcua.stack.core.types.builtin.LocalizedText;
import org.eclipse.milo.opcua.stack.core.types.builtin.Matrix;
import org.eclipse.milo.opcua.stack.core.types.builtin.NodeId;
import org.eclipse.milo.opcua.stack.core.types.builtin.QualifiedName;
import org.eclipse.milo.opcua.stack.core.types.builtin.Variant;
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.UInteger;

public class VariantNodesFragment extends ManagedAddressSpaceFragmentWithLifecycle {

  private final SimpleAddressSpaceFilter filter;
  private final SubscriptionModel subscriptionModel;

  private final DemoNamespace namespace;

  public VariantNodesFragment(OpcUaServer server, DemoNamespace namespace) {
    super(server, namespace);

    this.namespace = namespace;

    filter = SimpleAddressSpaceFilter.create(getNodeManager()::containsNode);

    subscriptionModel = new SubscriptionModel(server, this);
    getLifecycleManager().addLifecycle(subscriptionModel);

    getLifecycleManager().addStartupTask(this::addVariantNodes);
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

  private void addVariantNodes() {
    var variantsFolder =
        new UaFolderNode(
            getNodeContext(),
            deriveChildNodeId(namespace.getDemoFolder().getNodeId(), "Variants"),
            new QualifiedName(namespace.getNamespaceIndex(), "Variants"),
            new LocalizedText("Variants"));

    getNodeManager().addNode(variantsFolder);

    variantsFolder.addReference(
        new Reference(
            variantsFolder.getNodeId(),
            ReferenceTypes.Organizes,
            namespace.getDemoFolder().getNodeId().expanded(),
            Reference.Direction.INVERSE));

    addScalarVariants(variantsFolder.getNodeId());
    addArrayVariants(variantsFolder.getNodeId());
    addMatrixVariants(variantsFolder.getNodeId());
  }

  private void addScalarVariants(NodeId parentNodeId) {
    var scalarFolder =
        new UaFolderNode(
            getNodeContext(),
            deriveChildNodeId(parentNodeId, "Scalar"),
            new QualifiedName(namespace.getNamespaceIndex(), "Scalar"),
            new LocalizedText("Scalar"));

    getNodeManager().addNode(scalarFolder);

    scalarFolder.addReference(
        new Reference(
            scalarFolder.getNodeId(),
            ReferenceTypes.Organizes,
            parentNodeId.expanded(),
            Reference.Direction.INVERSE));

    for (OpcUaDataType dataType : OpcUaDataType.values()) {
      if (dataType == OpcUaDataType.Variant || dataType == OpcUaDataType.DiagnosticInfo) {
        continue;
      }

      var builder = new UaVariableNodeBuilder(getNodeContext());
      builder
          .setNodeId(deriveChildNodeId(scalarFolder.getNodeId(), dataType.name()))
          .setBrowseName(new QualifiedName(namespace.getNamespaceIndex(), dataType.name()))
          .setDisplayName(new LocalizedText(dataType.name()))
          .setDataType(OpcUaDataType.Variant.getNodeId())
          .setAccessLevel(AccessLevel.toValue(AccessLevel.READ_WRITE))
          .setUserAccessLevel(AccessLevel.toValue(AccessLevel.READ_WRITE))
          .setMinimumSamplingInterval(100.0);

      builder.setValue(new DataValue(Variant.of(getDefaultScalarValue(dataType))));

      var variableNode = builder.build();

      getNodeManager().addNode(variableNode);

      variableNode.addReference(
          new Reference(
              variableNode.getNodeId(),
              ReferenceTypes.HasComponent,
              scalarFolder.getNodeId().expanded(),
              Reference.Direction.INVERSE));
    }
  }

  private void addArrayVariants(NodeId parentNodeId) {
    var arrayFolder =
        new UaFolderNode(
            getNodeContext(),
            deriveChildNodeId(parentNodeId, "Array"),
            new QualifiedName(namespace.getNamespaceIndex(), "Array"),
            new LocalizedText("Array"));

    getNodeManager().addNode(arrayFolder);

    arrayFolder.addReference(
        new Reference(
            arrayFolder.getNodeId(),
            ReferenceTypes.Organizes,
            parentNodeId.expanded(),
            Reference.Direction.INVERSE));

    for (OpcUaDataType dataType : OpcUaDataType.values()) {
      if (dataType == OpcUaDataType.DiagnosticInfo || dataType == OpcUaDataType.Variant) {
        continue;
      }

      var builder = new UaVariableNodeBuilder(getNodeContext());
      builder
          .setNodeId(deriveChildNodeId(arrayFolder.getNodeId(), dataType.name()))
          .setBrowseName(new QualifiedName(namespace.getNamespaceIndex(), dataType.name()))
          .setDisplayName(new LocalizedText(dataType.name()))
          .setDataType(OpcUaDataType.Variant.getNodeId())
          .setValueRank(ValueRanks.OneDimension)
          .setArrayDimensions(new UInteger[] {uint(0)})
          .setAccessLevel(AccessLevel.toValue(AccessLevel.READ_WRITE))
          .setUserAccessLevel(AccessLevel.toValue(AccessLevel.READ_WRITE))
          .setMinimumSamplingInterval(100.0);

      Variant[] variants = new Variant[5];
      for (int i = 0; i < variants.length; i++) {
        variants[i] = Variant.of(getDefaultScalarValue(dataType));
      }
      builder.setValue(new DataValue(Variant.ofVariantArray(variants)));

      var variableNode = builder.build();

      getNodeManager().addNode(variableNode);

      variableNode.addReference(
          new Reference(
              variableNode.getNodeId(),
              ReferenceTypes.HasComponent,
              arrayFolder.getNodeId().expanded(),
              Reference.Direction.INVERSE));
    }
  }

  private void addMatrixVariants(NodeId parentNodeId) {
    var matrixFolder =
        new UaFolderNode(
            getNodeContext(),
            deriveChildNodeId(parentNodeId, "Matrix"),
            new QualifiedName(namespace.getNamespaceIndex(), "Matrix"),
            new LocalizedText("Matrix"));

    getNodeManager().addNode(matrixFolder);

    matrixFolder.addReference(
        new Reference(
            matrixFolder.getNodeId(),
            ReferenceTypes.Organizes,
            parentNodeId.expanded(),
            Reference.Direction.INVERSE));

    for (OpcUaDataType dataType : OpcUaDataType.values()) {
      if (dataType == OpcUaDataType.DiagnosticInfo || dataType == OpcUaDataType.Variant) {
        continue;
      }

      var builder = new UaVariableNodeBuilder(getNodeContext());
      builder
          .setNodeId(deriveChildNodeId(matrixFolder.getNodeId(), dataType.name()))
          .setBrowseName(new QualifiedName(namespace.getNamespaceIndex(), dataType.name()))
          .setDisplayName(new LocalizedText(dataType.name()))
          .setDataType(OpcUaDataType.Variant.getNodeId())
          .setValueRank(2)
          .setArrayDimensions(new UInteger[] {uint(0), uint(0)})
          .setAccessLevel(AccessLevel.toValue(AccessLevel.READ_WRITE))
          .setUserAccessLevel(AccessLevel.toValue(AccessLevel.READ_WRITE))
          .setMinimumSamplingInterval(100.0);

      Variant[][] variants = new Variant[5][5];

      for (int i = 0; i < variants.length; i++) {
        for (int j = 0; j < variants[i].length; j++) {
          variants[i][j] = Variant.of(getDefaultScalarValue(dataType));
        }
      }

      builder.setValue(new DataValue(Variant.ofMatrix(Matrix.ofVariant(variants))));

      var variableNode = builder.build();

      getNodeManager().addNode(variableNode);

      variableNode.addReference(
          new Reference(
              variableNode.getNodeId(),
              ReferenceTypes.HasComponent,
              matrixFolder.getNodeId().expanded(),
              Reference.Direction.INVERSE));
    }
  }
}

package com.digitalpetri.opcua.server.namespace;

import static com.digitalpetri.opcua.server.namespace.Util.deriveChildNodeId;

import java.util.List;
import org.eclipse.milo.opcua.sdk.core.Reference;
import org.eclipse.milo.opcua.sdk.core.Reference.Direction;
import org.eclipse.milo.opcua.sdk.server.AddressSpaceFilter;
import org.eclipse.milo.opcua.sdk.server.ManagedAddressSpaceFragmentWithLifecycle;
import org.eclipse.milo.opcua.sdk.server.OpcUaServer;
import org.eclipse.milo.opcua.sdk.server.SimpleAddressSpaceFilter;
import org.eclipse.milo.opcua.sdk.server.items.DataItem;
import org.eclipse.milo.opcua.sdk.server.items.MonitoredItem;
import org.eclipse.milo.opcua.sdk.server.nodes.UaFolderNode;
import org.eclipse.milo.opcua.sdk.server.nodes.UaObjectNode;
import org.eclipse.milo.opcua.sdk.server.nodes.UaObjectNode.UaObjectNodeBuilder;
import org.eclipse.milo.opcua.sdk.server.nodes.UaVariableNode;
import org.eclipse.milo.opcua.sdk.server.nodes.UaVariableNode.UaVariableNodeBuilder;
import org.eclipse.milo.opcua.sdk.server.util.SubscriptionModel;
import org.eclipse.milo.opcua.stack.core.NodeIds;
import org.eclipse.milo.opcua.stack.core.ReferenceTypes;
import org.eclipse.milo.opcua.stack.core.types.builtin.DataValue;
import org.eclipse.milo.opcua.stack.core.types.builtin.LocalizedText;
import org.eclipse.milo.opcua.stack.core.types.builtin.NodeId;
import org.eclipse.milo.opcua.stack.core.types.builtin.QualifiedName;
import org.eclipse.milo.opcua.stack.core.types.builtin.Variant;
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.UShort;

public class MassNodesFragment extends ManagedAddressSpaceFragmentWithLifecycle {

  private final SimpleAddressSpaceFilter filter;
  private final SubscriptionModel subscriptionModel;

  private final DemoNamespace namespace;
  private final UShort namespaceIndex;

  public MassNodesFragment(OpcUaServer server, DemoNamespace namespace) {
    super(server, namespace);

    this.namespace = namespace;
    this.namespaceIndex = namespace.getNamespaceIndex();

    filter = SimpleAddressSpaceFilter.create(getNodeManager()::containsNode);

    subscriptionModel = new SubscriptionModel(server, this);
    getLifecycleManager().addLifecycle(subscriptionModel);

    getLifecycleManager().addStartupTask(this::addMassNodes);
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

  private void addMassNodes() {
    var massFolder =
        new UaFolderNode(
            getNodeContext(),
            deriveChildNodeId(namespace.getDemoFolder().getNodeId(), "Mass"),
            new QualifiedName(namespaceIndex, "Mass"),
            new LocalizedText("Mass"));

    getNodeManager().addNode(massFolder);

    massFolder.addReference(
        new Reference(
            massFolder.getNodeId(),
            ReferenceTypes.HasComponent,
            namespace.getDemoFolder().getNodeId().expanded(),
            Direction.INVERSE));

    addFlatNodes(massFolder.getNodeId());
    addNestedNodes(massFolder.getNodeId());
  }

  private void addNestedNodes(NodeId parentNodeId) {
    var nestedFolder =
        new UaFolderNode(
            getNodeContext(),
            deriveChildNodeId(parentNodeId, "Nested"),
            new QualifiedName(namespaceIndex, "Nested"),
            new LocalizedText("Nested"));

    getNodeManager().addNode(nestedFolder);

    nestedFolder.addReference(
        new Reference(
            nestedFolder.getNodeId(),
            ReferenceTypes.HasComponent,
            parentNodeId.expanded(),
            Direction.INVERSE));

    int nestedQuantity1 = namespace.getConfig().getInt("address-space.mass.nested-quantity1");
    int nestedQuantity2 = namespace.getConfig().getInt("address-space.mass.nested-quantity2");
    var formatString1 = "%%0%dd".formatted((int) Math.log10(nestedQuantity1 - 1) + 1);
    var formatString2 = "%%0%dd".formatted((int) Math.log10(nestedQuantity2 - 1) + 1);

    for (int i = 0; i < nestedQuantity1; i++) {
      String outerName = formatString1.formatted(i);
      var folder =
          new UaFolderNode(
              getNodeContext(),
              deriveChildNodeId(nestedFolder.getNodeId(), outerName),
              new QualifiedName(namespaceIndex, outerName),
              new LocalizedText(outerName));

      getNodeManager().addNode(folder);

      folder.addReference(
          new Reference(
              folder.getNodeId(),
              ReferenceTypes.HasComponent,
              nestedFolder.getNodeId().expanded(),
              Direction.INVERSE));

      for (int j = 0; j < nestedQuantity2; j++) {
        String innerName = formatString2.formatted(j);
        var builder = new UaVariableNodeBuilder(getNodeContext());
        builder
            .setNodeId(deriveChildNodeId(folder.getNodeId(), innerName))
            .setBrowseName(new QualifiedName(namespaceIndex, innerName))
            .setDisplayName(new LocalizedText(innerName))
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

  private void addFlatNodes(NodeId parentNodeId) {
    var flatFolder =
        new UaFolderNode(
            getNodeContext(),
            deriveChildNodeId(parentNodeId, "Flat"),
            new QualifiedName(namespaceIndex, "Flat"),
            new LocalizedText("Flat"));

    getNodeManager().addNode(flatFolder);

    flatFolder.addReference(
        new Reference(
            flatFolder.getNodeId(),
            ReferenceTypes.HasComponent,
            parentNodeId.expanded(),
            Direction.INVERSE));

    int flatQuantity = namespace.getConfig().getInt("address-space.mass.flat-quantity");
    var formatString = "%%0%dd".formatted((int) Math.log10(flatQuantity - 1) + 1);

    for (int i = 0; i < flatQuantity; i++) {
      String name = formatString.formatted(i);
      var builder = new UaObjectNodeBuilder(getNodeContext());
      builder
          .setNodeId(deriveChildNodeId(flatFolder.getNodeId(), name))
          .setBrowseName(new QualifiedName(namespaceIndex, name))
          .setDisplayName(new LocalizedText(name));

      UaObjectNode objectNode = builder.build();

      getNodeManager().addNode(objectNode);

      objectNode.addReference(
          new Reference(
              objectNode.getNodeId(),
              ReferenceTypes.HasComponent,
              flatFolder.getNodeId().expanded(),
              Direction.INVERSE));
    }
  }
}

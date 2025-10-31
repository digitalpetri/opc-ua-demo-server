package com.digitalpetri.opcua.server.namespace.demo.ctt;

import static com.digitalpetri.opcua.server.namespace.demo.Util.deriveChildNodeId;

import java.util.List;
import org.eclipse.milo.opcua.sdk.core.Reference;
import org.eclipse.milo.opcua.sdk.core.Reference.Direction;
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
import org.eclipse.milo.opcua.stack.core.ReferenceTypes;
import org.eclipse.milo.opcua.stack.core.types.builtin.DataValue;
import org.eclipse.milo.opcua.stack.core.types.builtin.LocalizedText;
import org.eclipse.milo.opcua.stack.core.types.builtin.NodeId;
import org.eclipse.milo.opcua.stack.core.types.builtin.QualifiedName;
import org.eclipse.milo.opcua.stack.core.types.builtin.Variant;
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.UShort;

public class ReferencesFragment extends ManagedAddressSpaceFragmentWithLifecycle {

  private final SimpleAddressSpaceFilter filter;
  private final SubscriptionModel subscriptionModel;

  private final NodeId parentNodeId;
  private final UShort namespaceIndex;

  public ReferencesFragment(
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
}

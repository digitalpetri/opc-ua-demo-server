package com.digitalpetri.opcua.server.namespace.debug;

import static com.digitalpetri.opcua.server.namespace.Util.deriveChildNodeId;
import static org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.Unsigned.ubyte;
import static org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.Unsigned.uint;

import com.digitalpetri.opcua.server.namespace.DemoNamespace;
import java.util.List;
import org.eclipse.milo.opcua.sdk.core.Reference;
import org.eclipse.milo.opcua.sdk.core.Reference.Direction;
import org.eclipse.milo.opcua.sdk.server.AddressSpaceFilter;
import org.eclipse.milo.opcua.sdk.server.ManagedAddressSpaceFragmentWithLifecycle;
import org.eclipse.milo.opcua.sdk.server.OpcUaServer;
import org.eclipse.milo.opcua.sdk.server.SimpleAddressSpaceFilter;
import org.eclipse.milo.opcua.sdk.server.items.DataItem;
import org.eclipse.milo.opcua.sdk.server.items.MonitoredItem;
import org.eclipse.milo.opcua.sdk.server.nodes.UaMethodNode;
import org.eclipse.milo.opcua.sdk.server.nodes.UaObjectNode;
import org.eclipse.milo.opcua.sdk.server.util.SubscriptionModel;
import org.eclipse.milo.opcua.stack.core.NodeIds;
import org.eclipse.milo.opcua.stack.core.ReferenceTypes;
import org.eclipse.milo.opcua.stack.core.types.builtin.LocalizedText;
import org.eclipse.milo.opcua.stack.core.types.builtin.NodeId;
import org.eclipse.milo.opcua.stack.core.types.builtin.QualifiedName;
import org.eclipse.milo.opcua.stack.core.types.structured.Argument;

public class DebugNodesFragment extends ManagedAddressSpaceFragmentWithLifecycle {

  private final AddressSpaceFilter filter;
  private final SubscriptionModel subscriptionModel;

  private final DemoNamespace namespace;

  public DebugNodesFragment(OpcUaServer server, DemoNamespace namespace) {
    super(server, namespace);

    this.namespace = namespace;

    filter = SimpleAddressSpaceFilter.create(getNodeManager()::containsNode);

    subscriptionModel = new SubscriptionModel(server, this);
    getLifecycleManager().addLifecycle(subscriptionModel);

    getLifecycleManager().addStartupTask(this::addDebugNodes);
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

  private void addDebugNodes() {
    UaObjectNode debugNode =
        new UaObjectNode(
            getNodeContext(),
            new NodeId(namespace.getNamespaceIndex(), "Debug"),
            new QualifiedName(namespace.getNamespaceIndex(), "Debug"),
            LocalizedText.english("Debug"),
            LocalizedText.NULL_VALUE,
            uint(0),
            uint(0),
            ubyte(0));

    getNodeManager().addNode(debugNode);

    debugNode.addReference(
        new Reference(
            debugNode.getNodeId(),
            ReferenceTypes.HasComponent,
            NodeIds.ObjectsFolder.expanded(),
            Direction.INVERSE));

    addDeleteSubscriptionMethod(debugNode.getNodeId());
  }

  private void addDeleteSubscriptionMethod(NodeId parentNodeId) {
    UaMethodNode deleteSubscriptionNode =
        new UaMethodNode(
            getNodeContext(),
            deriveChildNodeId(parentNodeId, "DeleteSubscription"),
            new QualifiedName(namespace.getNamespaceIndex(), "DeleteSubscription"),
            LocalizedText.english("DeleteSubscription"),
            LocalizedText.NULL_VALUE,
            uint(0),
            uint(0),
            true,
            true);

    deleteSubscriptionNode.setInputArguments(
        new Argument[] {DeleteSubscriptionMethod.SUBSCRIPTION_ID});
    deleteSubscriptionNode.setOutputArguments(new Argument[0]);
    deleteSubscriptionNode.setInvocationHandler(
        new DeleteSubscriptionMethod(deleteSubscriptionNode));

    getNodeManager().addNode(deleteSubscriptionNode);

    deleteSubscriptionNode.addReference(
        new Reference(
            deleteSubscriptionNode.getNodeId(),
            NodeIds.HasComponent,
            parentNodeId.expanded(),
            Direction.INVERSE));
  }
}

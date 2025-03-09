package com.digitalpetri.opcua.server.namespace;

import static com.digitalpetri.opcua.server.namespace.Util.deriveChildNodeId;

import java.util.List;
import org.eclipse.milo.opcua.sdk.core.AccessLevel;
import org.eclipse.milo.opcua.sdk.core.Reference;
import org.eclipse.milo.opcua.sdk.core.Reference.Direction;
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

public class RbacNodesFragment extends ManagedAddressSpaceFragmentWithLifecycle {

  private final AddressSpaceFilter filter =
      SimpleAddressSpaceFilter.create(getNodeManager()::containsNode);

  private final SubscriptionModel subscriptionModel;

  private final DemoNamespace namespace;

  public RbacNodesFragment(OpcUaServer server, DemoNamespace namespace) {
    super(server, namespace);

    this.namespace = namespace;

    subscriptionModel = new SubscriptionModel(server, this);
    getLifecycleManager().addLifecycle(subscriptionModel);

    getLifecycleManager().addStartupTask(this::addRbacNodes);
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

  private void addRbacNodes() {
    var rbacFolder =
        new UaFolderNode(
            getNodeContext(),
            deriveChildNodeId(namespace.getDemoFolder().getNodeId(), "RBAC"),
            new QualifiedName(namespace.getNamespaceIndex(), "RBAC"),
            new LocalizedText("RBAC"));

    getNodeManager().addNode(rbacFolder);

    rbacFolder.addReference(
        new Reference(
            rbacFolder.getNodeId(),
            ReferenceTypes.Organizes,
            namespace.getDemoFolder().getNodeId().expanded(),
            Direction.INVERSE));

    addSiteNode(rbacFolder.getNodeId(), "SiteA", "rbac.site-a");
    addSiteNode(rbacFolder.getNodeId(), "SiteB", "rbac.site-b");
  }

  private void addSiteNode(NodeId parentNodeId, String site, String key) {
    var siteFolder =
        new UaFolderNode(
            getNodeContext(),
            deriveChildNodeId(parentNodeId, site),
            new QualifiedName(namespace.getNamespaceIndex(), site),
            new LocalizedText(site));

    getNodeManager().addNode(siteFolder);

    siteFolder.addReference(
        new Reference(
            siteFolder.getNodeId(),
            ReferenceTypes.Organizes,
            parentNodeId.expanded(),
            Direction.INVERSE));

    for (int i = 0; i < 5; i++) {
      var builder = new UaVariableNodeBuilder(getNodeContext());
      builder
          .setNodeId(deriveChildNodeId(siteFolder.getNodeId(), "Variable" + i))
          .setBrowseName(new QualifiedName(namespace.getNamespaceIndex(), "Variable" + i))
          .setDisplayName(new LocalizedText("Variable" + i))
          .setDataType(NodeIds.Int32)
          .setAccessLevel(AccessLevel.toValue(AccessLevel.READ_WRITE))
          .setMinimumSamplingInterval(100.0);

      builder.setValue(new DataValue(Variant.ofInt32(i)));

      UaVariableNode variableNode = builder.build();

      variableNode.getFilterChain().addLast(new AccessControlFilter(namespace.getConfig(), key));

      getNodeManager().addNode(variableNode);

      variableNode.addReference(
          new Reference(
              variableNode.getNodeId(),
              ReferenceTypes.HasComponent,
              siteFolder.getNodeId().expanded(),
              Direction.INVERSE));
    }
  }
}

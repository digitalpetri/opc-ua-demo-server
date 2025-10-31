package com.digitalpetri.opcua.server.namespace.demo.ctt;

import com.digitalpetri.opcua.server.namespace.demo.DemoNamespace;
import java.util.List;
import org.eclipse.milo.opcua.sdk.core.Reference;
import org.eclipse.milo.opcua.sdk.core.Reference.Direction;
import org.eclipse.milo.opcua.sdk.server.AddressSpaceComposite;
import org.eclipse.milo.opcua.sdk.server.AddressSpaceFilter;
import org.eclipse.milo.opcua.sdk.server.Lifecycle;
import org.eclipse.milo.opcua.sdk.server.LifecycleManager;
import org.eclipse.milo.opcua.sdk.server.ManagedAddressSpaceFragmentWithLifecycle;
import org.eclipse.milo.opcua.sdk.server.OpcUaServer;
import org.eclipse.milo.opcua.sdk.server.SimpleAddressSpaceFilter;
import org.eclipse.milo.opcua.sdk.server.items.DataItem;
import org.eclipse.milo.opcua.sdk.server.items.MonitoredItem;
import org.eclipse.milo.opcua.sdk.server.nodes.UaFolderNode;
import org.eclipse.milo.opcua.sdk.server.util.SubscriptionModel;
import org.eclipse.milo.opcua.stack.core.NodeIds;
import org.eclipse.milo.opcua.stack.core.ReferenceTypes;
import org.eclipse.milo.opcua.stack.core.types.builtin.LocalizedText;
import org.eclipse.milo.opcua.stack.core.types.builtin.NodeId;
import org.eclipse.milo.opcua.stack.core.types.builtin.QualifiedName;
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.UShort;

public class CttNodes extends AddressSpaceComposite implements Lifecycle {

  private final LifecycleManager lifecycleManager = new LifecycleManager();

  private final CttRootFragment cttRootFragment;

  public CttNodes(OpcUaServer server, DemoNamespace namespace) {
    super(server);

    UShort namespaceIndex = namespace.getNamespaceIndex();

    // Create root CTT and Static folders
    cttRootFragment = new CttRootFragment(server, this, namespaceIndex);
    lifecycleManager.addLifecycle(cttRootFragment);

    // Register all CTT fragments
    register(new AllProfilesFragment(server, this, cttRootFragment.getStaticFolderNodeId(), namespaceIndex));
    register(new DataAccessProfileFragment(server, this, cttRootFragment.getStaticFolderNodeId(), namespaceIndex));
    register(new ReferencesFragment(server, this, cttRootFragment.getStaticFolderNodeId(), namespaceIndex));
    register(new PathsFragment(server, this, cttRootFragment.getStaticFolderNodeId(), namespaceIndex));
    register(new MethodsFragment(server, this, cttRootFragment.getCttFolderNodeId(), namespaceIndex));
    register(new SecurityAccessFragment(server, this, cttRootFragment.getCttFolderNodeId(), namespaceIndex));
  }

  @Override
  public void startup() {
    lifecycleManager.startup();
  }

  @Override
  public void shutdown() {
    lifecycleManager.shutdown();
  }

  /**
   * Private fragment that creates the root CTT folder and Static subfolder.
   */
  private static class CttRootFragment extends ManagedAddressSpaceFragmentWithLifecycle {

    private final AddressSpaceFilter filter =
        SimpleAddressSpaceFilter.create(getNodeManager()::containsNode);

    private final NodeId cttFolderNodeId;
    private final NodeId staticFolderNodeId;

    private final SubscriptionModel subscriptionModel;

    public CttRootFragment(
        OpcUaServer server, AddressSpaceComposite composite, UShort namespaceIndex) {

      super(server, composite);

      this.cttFolderNodeId = new NodeId(namespaceIndex, "CTT");
      this.staticFolderNodeId = new NodeId(namespaceIndex, "CTT.Static");

      subscriptionModel = new SubscriptionModel(server, composite);
      getLifecycleManager().addLifecycle(subscriptionModel);

      getLifecycleManager().addStartupTask(this::addRootFolders);
    }

    private void addRootFolders() {
      // Create CTT root folder
      var cttFolder =
          new UaFolderNode(
              getNodeContext(),
              cttFolderNodeId,
              new QualifiedName(cttFolderNodeId.getNamespaceIndex(), "CTT"),
              new LocalizedText("CTT"));

      getNodeManager().addNode(cttFolder);

      cttFolder.addReference(
          new Reference(
              cttFolder.getNodeId(),
              ReferenceTypes.HasComponent,
              NodeIds.ObjectsFolder.expanded(),
              Direction.INVERSE));

      // Create Static subfolder under CTT
      var staticFolder =
          new UaFolderNode(
              getNodeContext(),
              staticFolderNodeId,
              new QualifiedName(staticFolderNodeId.getNamespaceIndex(), "Static"),
              new LocalizedText("Static"));

      getNodeManager().addNode(staticFolder);

      staticFolder.addReference(
          new Reference(
              staticFolder.getNodeId(),
              ReferenceTypes.HasComponent,
              cttFolder.getNodeId().expanded(),
              Direction.INVERSE));
    }

    public NodeId getCttFolderNodeId() {
      return cttFolderNodeId;
    }

    public NodeId getStaticFolderNodeId() {
      return staticFolderNodeId;
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
  }
}

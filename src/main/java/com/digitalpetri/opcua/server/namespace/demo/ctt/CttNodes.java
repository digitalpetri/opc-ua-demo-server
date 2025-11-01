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

  private final RootFragment rootFragment;

  public CttNodes(OpcUaServer server, DemoNamespace namespace) {
    super(server);

    UShort namespaceIndex = namespace.getNamespaceIndex();

    // Create and add the root fragment that manages CTT and Static folders
    rootFragment = new RootFragment(server, this, namespaceIndex);
    lifecycleManager.addLifecycle(rootFragment);

    // Create and add all child fragments
    var allProfilesFragment =
        new AllProfilesFragment(
            server, this, rootFragment.getStaticFolderNodeId(), namespaceIndex);
    lifecycleManager.addLifecycle(allProfilesFragment);

    var dataAccessProfileFragment =
        new DataAccessProfileFragment(
            server, this, rootFragment.getStaticFolderNodeId(), namespaceIndex);
    lifecycleManager.addLifecycle(dataAccessProfileFragment);

    var referencesFragment =
        new ReferencesFragment(
            server, this, rootFragment.getStaticFolderNodeId(), namespaceIndex);
    lifecycleManager.addLifecycle(referencesFragment);

    var pathsFragment =
        new PathsFragment(server, this, rootFragment.getStaticFolderNodeId(), namespaceIndex);
    lifecycleManager.addLifecycle(pathsFragment);

    var methodsFragment =
        new MethodsFragment(server, this, rootFragment.getCttFolderNodeId(), namespaceIndex);
    lifecycleManager.addLifecycle(methodsFragment);

    var securityAccessFragment =
        new SecurityAccessFragment(
            server, this, rootFragment.getCttFolderNodeId(), namespaceIndex);
    lifecycleManager.addLifecycle(securityAccessFragment);
  }

  public NodeId getCttFolderNodeId() {
    return rootFragment.getCttFolderNodeId();
  }

  public NodeId getStaticFolderNodeId() {
    return rootFragment.getStaticFolderNodeId();
  }

  @Override
  public void startup() {
    lifecycleManager.startup();
  }

  @Override
  public void shutdown() {
    lifecycleManager.shutdown();
  }

  private static class RootFragment extends ManagedAddressSpaceFragmentWithLifecycle {

    private final AddressSpaceFilter filter =
        SimpleAddressSpaceFilter.create(getNodeManager()::containsNode);

    private final UaFolderNode cttFolder;
    private final UaFolderNode staticFolder;

    private final SubscriptionModel subscriptionModel;

    public RootFragment(
        OpcUaServer server, AddressSpaceComposite composite, UShort namespaceIndex) {

      super(server, composite);

      subscriptionModel = new SubscriptionModel(server, composite);
      getLifecycleManager().addLifecycle(subscriptionModel);

      cttFolder =
          new UaFolderNode(
              getNodeContext(),
              new NodeId(namespaceIndex, "CTT"),
              new QualifiedName(namespaceIndex, "CTT"),
              new LocalizedText("CTT"));

      staticFolder =
          new UaFolderNode(
              getNodeContext(),
              new NodeId(namespaceIndex, "CTT.Static"),
              new QualifiedName(namespaceIndex, "Static"),
              new LocalizedText("Static"));

      getLifecycleManager()
          .addStartupTask(
              () -> {
                getNodeManager().addNode(cttFolder);

                cttFolder.addReference(
                    new Reference(
                        cttFolder.getNodeId(),
                        ReferenceTypes.HasComponent,
                        NodeIds.ObjectsFolder.expanded(),
                        Direction.INVERSE));

                getNodeManager().addNode(staticFolder);

                staticFolder.addReference(
                    new Reference(
                        staticFolder.getNodeId(),
                        ReferenceTypes.HasComponent,
                        cttFolder.getNodeId().expanded(),
                        Direction.INVERSE));
              });
    }

    public NodeId getCttFolderNodeId() {
      return cttFolder.getNodeId();
    }

    public NodeId getStaticFolderNodeId() {
      return staticFolder.getNodeId();
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

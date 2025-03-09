package com.digitalpetri.opcua.server.namespace;

import com.typesafe.config.Config;
import java.util.List;
import org.eclipse.milo.opcua.sdk.core.Reference;
import org.eclipse.milo.opcua.sdk.core.Reference.Direction;
import org.eclipse.milo.opcua.sdk.server.AddressSpaceComposite;
import org.eclipse.milo.opcua.sdk.server.AddressSpaceFilter;
import org.eclipse.milo.opcua.sdk.server.Lifecycle;
import org.eclipse.milo.opcua.sdk.server.LifecycleManager;
import org.eclipse.milo.opcua.sdk.server.ManagedAddressSpaceFragmentWithLifecycle;
import org.eclipse.milo.opcua.sdk.server.Namespace;
import org.eclipse.milo.opcua.sdk.server.OpcUaServer;
import org.eclipse.milo.opcua.sdk.server.SimpleAddressSpaceFilter;
import org.eclipse.milo.opcua.sdk.server.items.DataItem;
import org.eclipse.milo.opcua.sdk.server.items.MonitoredItem;
import org.eclipse.milo.opcua.sdk.server.nodes.UaFolderNode;
import org.eclipse.milo.opcua.sdk.server.util.SubscriptionModel;
import org.eclipse.milo.opcua.stack.core.NodeIds;
import org.eclipse.milo.opcua.stack.core.types.builtin.LocalizedText;
import org.eclipse.milo.opcua.stack.core.types.builtin.NodeId;
import org.eclipse.milo.opcua.stack.core.types.builtin.QualifiedName;
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.UShort;

public class DemoNamespace extends AddressSpaceComposite implements Namespace, Lifecycle {

  public static final String NAMESPACE_URI =
      "urn:opc:eclipse:milo:opc-ua-demo-server:namespace:demo";

  private final LifecycleManager lifecycleManager = new LifecycleManager();

  private final DemoFragment demoFragment;

  private final UShort namespaceIndex;

  private final Config config;

  public DemoNamespace(OpcUaServer server, Config config) {
    super(server);

    this.config = config;

    namespaceIndex = server.getNamespaceTable().add(NAMESPACE_URI);

    lifecycleManager.addLifecycle(
        new Lifecycle() {
          @Override
          public void startup() {
            server.getAddressSpaceManager().register(DemoNamespace.this);
          }

          @Override
          public void shutdown() {
            server.getAddressSpaceManager().unregister(DemoNamespace.this);
          }
        });

    demoFragment = new DemoFragment(server, this, namespaceIndex);
    lifecycleManager.addLifecycle(demoFragment);

    boolean cttEnabled = config.getBoolean("address-space.ctt.enabled");
    if (cttEnabled) {
      var cttFragment = new CttNodesFragment(server, this);
      lifecycleManager.addLifecycle(cttFragment);
    }

    boolean massNodesEnabled = config.getBoolean("address-space.mass.enabled");
    if (massNodesEnabled) {
      var massFragment = new MassNodesFragment(server, this);
      lifecycleManager.addLifecycle(massFragment);
    }

    boolean dataTypeTestEnabled = config.getBoolean("address-space.data-type-test.enabled");
    if (dataTypeTestEnabled) {
      var dataTypeTestFragment = new DataTypeTestNodesFragment(server, this);
      lifecycleManager.addLifecycle(dataTypeTestFragment);
    }

    boolean dynamicNodesEnabled = config.getBoolean("address-space.dynamic.enabled");
    if (dynamicNodesEnabled) {
      var dynamicFragment = new DynamicNodesFragment(server, this);
      lifecycleManager.addLifecycle(dynamicFragment);
    }

    boolean nullNodesEnabled = config.getBoolean("address-space.null.enabled");
    if (nullNodesEnabled) {
      var nullFragment = new NullNodesFragment(server, this);
      lifecycleManager.addLifecycle(nullFragment);
    }

    boolean turtleNodesEnabled = config.getBoolean("address-space.turtles.enabled");
    if (turtleNodesEnabled) {
      var turtleFragment = new TurtleNodesFragment(server, this);
      lifecycleManager.addLifecycle(turtleFragment);
    }

    var rbacFragment = new RbacNodesFragment(server, this);
    lifecycleManager.addLifecycle(rbacFragment);
  }

  @Override
  public UShort getNamespaceIndex() {
    return namespaceIndex;
  }

  @Override
  public String getNamespaceUri() {
    return NAMESPACE_URI;
  }

  @Override
  public void startup() {
    lifecycleManager.startup();
  }

  @Override
  public void shutdown() {
    lifecycleManager.shutdown();
  }

  public Config getConfig() {
    return config;
  }

  public UaFolderNode getDemoFolder() {
    return demoFragment.getDemoFolder();
  }

  private static class DemoFragment extends ManagedAddressSpaceFragmentWithLifecycle {

    private final AddressSpaceFilter filter =
        SimpleAddressSpaceFilter.create(getNodeManager()::containsNode);

    private final UaFolderNode demoFolder;

    private final SubscriptionModel subscriptionModel;

    public DemoFragment(
        OpcUaServer server, AddressSpaceComposite composite, UShort namespaceIndex) {

      super(server, composite);

      subscriptionModel = new SubscriptionModel(server, composite);
      getLifecycleManager().addLifecycle(subscriptionModel);

      demoFolder =
          new UaFolderNode(
              getNodeContext(),
              new NodeId(namespaceIndex, "Demo"),
              new QualifiedName(namespaceIndex, "Demo"),
              new LocalizedText("Demo"));

      getLifecycleManager()
          .addStartupTask(
              () -> {
                getNodeManager().addNode(demoFolder);

                demoFolder.addReference(
                    new Reference(
                        demoFolder.getNodeId(),
                        NodeIds.Organizes,
                        NodeIds.ObjectsFolder.expanded(),
                        Direction.INVERSE));
              });
    }

    public UaFolderNode getDemoFolder() {
      return demoFolder;
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

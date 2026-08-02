package com.digitalpetri.opcua.server.namespace.demo;

import com.digitalpetri.opcua.server.namespace.demo.alarms.AlarmNodesFragment;
import com.digitalpetri.opcua.server.namespace.demo.ctt.CttNodes;
import com.digitalpetri.opcua.server.namespace.demo.debug.DebugNodesFragment;
import com.typesafe.config.Config;
import java.time.Duration;
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
import org.eclipse.milo.opcua.sdk.server.model.objects.NamespaceMetadataTypeNode;
import org.eclipse.milo.opcua.sdk.server.nodes.UaFolderNode;
import org.eclipse.milo.opcua.sdk.server.nodes.instantiation.InstantiationRequest;
import org.eclipse.milo.opcua.sdk.server.util.SubscriptionModel;
import org.eclipse.milo.opcua.stack.core.NodeIds;
import org.eclipse.milo.opcua.stack.core.UaException;
import org.eclipse.milo.opcua.stack.core.types.builtin.DateTime;
import org.eclipse.milo.opcua.stack.core.types.builtin.ExpandedNodeId;
import org.eclipse.milo.opcua.stack.core.types.builtin.LocalizedText;
import org.eclipse.milo.opcua.stack.core.types.builtin.NodeId;
import org.eclipse.milo.opcua.stack.core.types.builtin.QualifiedName;
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.UShort;
import org.eclipse.milo.opcua.stack.core.types.enumerated.IdType;

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

    boolean alarmsEnabled = config.getBoolean("address-space.alarms.enabled");
    if (alarmsEnabled) {
      Duration tickInterval = config.getDuration("address-space.alarms.tick-interval");
      lifecycleManager.addLifecycle(new AlarmNodesFragment(server, this, tickInterval));
    }

    boolean cttEnabled = config.getBoolean("address-space.ctt.enabled");
    if (cttEnabled) {
      var cttNodes = new CttNodes(server, this);
      register(cttNodes);
      lifecycleManager.addLifecycle(cttNodes);
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

    var debugFragment = new DebugNodesFragment(server, this);
    lifecycleManager.addLifecycle(debugFragment);

    var variantFragment = new VariantNodesFragment(server, this);
    lifecycleManager.addLifecycle(variantFragment);
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

    private final UShort namespaceIndex;
    private final UaFolderNode demoFolder;

    private final SubscriptionModel subscriptionModel;

    public DemoFragment(
        OpcUaServer server, AddressSpaceComposite composite, UShort namespaceIndex) {

      super(server, composite);
      this.namespaceIndex = namespaceIndex;

      subscriptionModel = new SubscriptionModel(server, composite);
      getLifecycleManager().addLifecycle(subscriptionModel);

      getLifecycleManager().addStartupTask(this::addNamespaceMetadataNodes);

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

                demoFolder.addReference(
                    new Reference(
                        demoFolder.getNodeId(),
                        NodeIds.HasComponent,
                        ExpandedNodeId.parse("ns=2;s=CTT.Static.AllProfiles.Scalar"),
                        Direction.FORWARD));
                demoFolder.addReference(
                    new Reference(
                        demoFolder.getNodeId(),
                        NodeIds.HasComponent,
                        ExpandedNodeId.parse("ns=2;s=CTT.Static.AllProfiles.Array"),
                        Direction.FORWARD));
                demoFolder.addReference(
                    new Reference(
                        demoFolder.getNodeId(),
                        NodeIds.HasComponent,
                        ExpandedNodeId.parse("ns=2;s=CTT.Static.AllProfiles.Matrix"),
                        Direction.FORWARD));
              });
    }

    private void addNamespaceMetadataNodes() {
      String applicationUri = getServer().getConfig().getApplicationUri();
      UShort applicationNamespaceIndex = getServer().getNamespaceTable().getIndex(applicationUri);

      if (applicationNamespaceIndex == null) {
        throw new IllegalStateException(
            "application namespace is not registered: " + applicationUri);
      }

      try {
        addDynamicNamespaceMetadata(
            "NamespaceMetadata.Application", applicationNamespaceIndex, applicationUri, false);
        addDynamicNamespaceMetadata("NamespaceMetadata.Demo", namespaceIndex, NAMESPACE_URI, true);
      } catch (UaException e) {
        throw new IllegalStateException("failed to instantiate namespace metadata", e);
      }
    }

    /**
     * Add complete metadata for a dynamic namespace.
     *
     * <p>Part 5 §6.3.13 requires all seven Properties to have readable values. These namespaces
     * have no formal version, publication date, or declared static NodeIds, so the corresponding
     * values are null or empty while still carrying a Good StatusCode. The subset flag reflects
     * whether configuration can omit Nodes belonging to the represented namespace.
     */
    private void addDynamicNamespaceMetadata(
        String nodeIdentifier,
        UShort representedNamespaceIndex,
        String namespaceUri,
        boolean isNamespaceSubset)
        throws UaException {

      InstantiationRequest<NamespaceMetadataTypeNode> request =
          InstantiationRequest.of(NamespaceMetadataTypeNode.class, NodeIds.NamespaceMetadataType)
              .nodeId(new NodeId(namespaceIndex, nodeIdentifier))
              .browseName(new QualifiedName(representedNamespaceIndex, namespaceUri))
              .displayName(new LocalizedText(namespaceUri))
              .parent(NodeIds.Server_Namespaces, NodeIds.HasComponent)
              .target(getNodeManager())
              .build();

      NamespaceMetadataTypeNode metadataNode =
          getServer().getNodeInstantiator().instantiate(request).root();

      metadataNode.setNamespaceUri(namespaceUri);
      metadataNode.setNamespaceVersion(null);
      metadataNode.setNamespacePublicationDate(DateTime.NULL_VALUE);
      metadataNode.setIsNamespaceSubset(isNamespaceSubset);
      metadataNode.setStaticNodeIdTypes(new IdType[0]);
      metadataNode.setStaticNumericNodeIdRange(new String[0]);
      metadataNode.setStaticStringNodeIdPattern("");
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

package com.digitalpetri.opcua.server.namespace.load;

import static com.digitalpetri.opcua.server.namespace.demo.Util.deriveChildNodeId;

import com.typesafe.config.Config;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
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

public class LoadNamespace extends AddressSpaceComposite implements Namespace, Lifecycle {

  public static final String NAMESPACE_URI =
      "urn:opc:eclipse:milo:opc-ua-demo-server:namespace:load";

  private final LifecycleManager lifecycleManager = new LifecycleManager();

  private final UShort namespaceIndex;

  public LoadNamespace(OpcUaServer server, Config config) {
    super(server);

    namespaceIndex = server.getNamespaceTable().add(NAMESPACE_URI);

    lifecycleManager.addLifecycle(
        new Lifecycle() {
          @Override
          public void startup() {
            server.getAddressSpaceManager().register(LoadNamespace.this);
          }

          @Override
          public void shutdown() {
            server.getAddressSpaceManager().unregister(LoadNamespace.this);
          }
        });

    var fragment = new LoadFragment(server, this, namespaceIndex, config);
    lifecycleManager.addLifecycle(fragment);
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

  private static class LoadFragment extends ManagedAddressSpaceFragmentWithLifecycle {

    private final AddressSpaceFilter filter =
        SimpleAddressSpaceFilter.create(getNodeManager()::containsNode);

    private final SubscriptionModel subscriptionModel;

    private final UShort namespaceIndex;
    private final Config config;

    private final List<UaVariableNode> variableNodes = new ArrayList<>();
    private final Random random = new Random();

    LoadFragment(
        OpcUaServer server,
        AddressSpaceComposite composite,
        UShort namespaceIndex,
        Config config) {

      super(server, composite);

      this.namespaceIndex = namespaceIndex;
      this.config = config;

      subscriptionModel = new SubscriptionModel(server, composite);
      getLifecycleManager().addLifecycle(subscriptionModel);

      getLifecycleManager().addStartupTask(this::addLoadNodes);
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

    private void addLoadNodes() {
      String rootName = config.getString("load.name");
      int branchCount = config.getInt("load.branch-count");
      int depthCount = config.getInt("load.depth-count");
      int doublesPerFolder = config.getInt("load.doubles-per-folder");
      int boolsPerFolder = config.getInt("load.bools-per-folder");
      int intsPerFolder = config.getInt("load.ints-per-folder");
      int stringsPerFolder = config.getInt("load.strings-per-folder");
      long updateIntervalMs = config.getLong("load.update-interval-ms");

      NodeId rootNodeId = new NodeId(namespaceIndex, rootName);

      var rootFolder =
          new UaFolderNode(
              getNodeContext(),
              rootNodeId,
              new QualifiedName(namespaceIndex, rootName),
              new LocalizedText(rootName));

      getNodeManager().addNode(rootFolder);

      rootFolder.addReference(
          new Reference(
              rootFolder.getNodeId(),
              NodeIds.Organizes,
              NodeIds.ObjectsFolder.expanded(),
              Direction.INVERSE));

      String branchFormat =
          "%%0%dd".formatted((int) Math.log10(Math.max(branchCount - 1, 1)) + 1);
      String depthFormat =
          "%%0%dd".formatted((int) Math.log10(Math.max(depthCount - 1, 1)) + 1);

      for (int b = 0; b < branchCount; b++) {
        String branchName = "Branch_" + branchFormat.formatted(b);
        NodeId branchNodeId = deriveChildNodeId(rootNodeId, branchName);

        UaFolderNode branchFolder =
            new UaFolderNode(
                getNodeContext(),
                branchNodeId,
                new QualifiedName(namespaceIndex, branchName),
                new LocalizedText(branchName));

        getNodeManager().addNode(branchFolder);

        branchFolder.addReference(
            new Reference(
                branchFolder.getNodeId(),
                ReferenceTypes.HasComponent,
                rootFolder.getNodeId().expanded(),
                Direction.INVERSE));

        for (int d = 0; d < depthCount; d++) {
          String depthName = "Depth_" + depthFormat.formatted(d);
          NodeId depthNodeId = deriveChildNodeId(branchNodeId, depthName);

          UaFolderNode depthFolder =
              new UaFolderNode(
                  getNodeContext(),
                  depthNodeId,
                  new QualifiedName(namespaceIndex, depthName),
                  new LocalizedText(depthName));

          getNodeManager().addNode(depthFolder);

          depthFolder.addReference(
              new Reference(
                  depthFolder.getNodeId(),
                  ReferenceTypes.HasComponent,
                  branchFolder.getNodeId().expanded(),
                  Direction.INVERSE));

          addVariables(depthNodeId, "Double", NodeIds.Double, doublesPerFolder);
          addVariables(depthNodeId, "Bool", NodeIds.Boolean, boolsPerFolder);
          addVariables(depthNodeId, "Int", NodeIds.Int32, intsPerFolder);
          addVariables(depthNodeId, "String", NodeIds.String, stringsPerFolder);
        }
      }

      ScheduledFuture<?> scheduledFuture =
          getServer()
              .getConfig()
              .getScheduledExecutorService()
              .scheduleAtFixedRate(
                  () -> getServer().getConfig().getExecutor().execute(this::updateValues),
                  0,
                  updateIntervalMs,
                  TimeUnit.MILLISECONDS);

      getLifecycleManager().addShutdownTask(() -> scheduledFuture.cancel(true));
    }

    private void addVariables(NodeId parentNodeId, String prefix, NodeId dataType, int count) {
      if (count <= 0) return;

      NodeId typeFolderNodeId = deriveChildNodeId(parentNodeId, prefix);

      UaFolderNode typeFolder =
          new UaFolderNode(
              getNodeContext(),
              typeFolderNodeId,
              new QualifiedName(namespaceIndex, prefix),
              new LocalizedText(prefix));

      getNodeManager().addNode(typeFolder);

      typeFolder.addReference(
          new Reference(
              typeFolder.getNodeId(),
              ReferenceTypes.HasComponent,
              parentNodeId.expanded(),
              Direction.INVERSE));

      String format = "%%0%dd".formatted((int) Math.log10(Math.max(count - 1, 1)) + 1);

      for (int i = 0; i < count; i++) {
        String name = prefix + "_" + format.formatted(i);

        var builder = new UaVariableNodeBuilder(getNodeContext());
        builder
            .setNodeId(deriveChildNodeId(typeFolderNodeId, name))
            .setBrowseName(new QualifiedName(namespaceIndex, name))
            .setDisplayName(new LocalizedText(name))
            .setDataType(dataType);

        UaVariableNode node = builder.build();

        getNodeManager().addNode(node);
        variableNodes.add(node);

        node.addReference(
            new Reference(
                node.getNodeId(),
                ReferenceTypes.HasComponent,
                typeFolderNodeId.expanded(),
                Direction.INVERSE));
      }
    }

    private void updateValues() {
      for (UaVariableNode node : variableNodes) {
        NodeId dataType = node.getDataType();

        DataValue value;
        if (NodeIds.Double.equals(dataType)) {
          value = new DataValue(Variant.of(random.nextDouble() * 1000));
        } else if (NodeIds.Boolean.equals(dataType)) {
          value = new DataValue(Variant.of(random.nextBoolean()));
        } else if (NodeIds.Int32.equals(dataType)) {
          value = new DataValue(Variant.ofInt32(random.nextInt()));
        } else if (NodeIds.String.equals(dataType)) {
          value = new DataValue(Variant.of(UUID.randomUUID().toString().substring(0, 8)));
        } else {
          continue;
        }

        node.setValue(value);
      }
    }
  }
}

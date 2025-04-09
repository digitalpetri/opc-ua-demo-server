package com.digitalpetri.opcua.server.namespace.demo;

import static com.digitalpetri.opcua.server.namespace.demo.Util.deriveChildNodeId;

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
import org.eclipse.milo.opcua.sdk.server.nodes.UaVariableNode.UaVariableNodeBuilder;
import org.eclipse.milo.opcua.sdk.server.util.SubscriptionModel;
import org.eclipse.milo.opcua.stack.core.OpcUaDataType;
import org.eclipse.milo.opcua.stack.core.ReferenceTypes;
import org.eclipse.milo.opcua.stack.core.types.builtin.DataValue;
import org.eclipse.milo.opcua.stack.core.types.builtin.LocalizedText;
import org.eclipse.milo.opcua.stack.core.types.builtin.QualifiedName;
import org.eclipse.milo.opcua.stack.core.types.builtin.Variant;

public class NullNodesFragment extends ManagedAddressSpaceFragmentWithLifecycle {

  private final SimpleAddressSpaceFilter filter;
  private final SubscriptionModel subscriptionModel;

  private final DemoNamespace namespace;

  public NullNodesFragment(OpcUaServer server, DemoNamespace namespace) {
    super(server, namespace);

    this.namespace = namespace;

    filter = SimpleAddressSpaceFilter.create(getNodeManager()::containsNode);

    subscriptionModel = new SubscriptionModel(server, this);
    getLifecycleManager().addLifecycle(subscriptionModel);

    getLifecycleManager().addStartupTask(this::addNullNodes);
  }

  private void addNullNodes() {
    var nullFolder =
        new UaFolderNode(
            getNodeContext(),
            deriveChildNodeId(namespace.getDemoFolder().getNodeId(), "Null"),
            new QualifiedName(namespace.getNamespaceIndex(), "Null"),
            new LocalizedText("Null"));

    getNodeManager().addNode(nullFolder);

    nullFolder.addReference(
        new Reference(
            nullFolder.getNodeId(),
            ReferenceTypes.Organizes,
            namespace.getDemoFolder().getNodeId().expanded(),
            Direction.INVERSE));

    for (OpcUaDataType dataType : OpcUaDataType.values()) {
      if (dataType == OpcUaDataType.DiagnosticInfo) continue;

      var builder = new UaVariableNodeBuilder(getNodeContext());
      builder
          .setNodeId(deriveChildNodeId(nullFolder.getNodeId(), dataType.name()))
          .setBrowseName(new QualifiedName(namespace.getNamespaceIndex(), dataType.name()))
          .setDisplayName(new LocalizedText(dataType.name()))
          .setDataType(dataType.getNodeId())
          .setAccessLevel(AccessLevel.toValue(AccessLevel.READ_ONLY))
          .setUserAccessLevel(AccessLevel.toValue(AccessLevel.READ_ONLY))
          .setMinimumSamplingInterval(100.0);

      var variableNode = builder.build();

      variableNode.setValue(new DataValue(Variant.NULL_VALUE));

      getNodeManager().addNode(variableNode);

      variableNode.addReference(
          new Reference(
              variableNode.getNodeId(),
              ReferenceTypes.HasComponent,
              nullFolder.getNodeId().expanded(),
              Direction.INVERSE));
    }
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

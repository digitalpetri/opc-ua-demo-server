package com.digitalpetri.opcua.server.namespace.demo.ctt;

import static com.digitalpetri.opcua.server.namespace.demo.Util.deriveChildNodeId;

import java.util.List;
import org.eclipse.milo.opcua.sdk.core.AccessLevel;
import org.eclipse.milo.opcua.sdk.core.Reference;
import org.eclipse.milo.opcua.sdk.core.Reference.Direction;
import org.eclipse.milo.opcua.sdk.core.ValueRanks;
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

public class SecurityAccessFragment extends ManagedAddressSpaceFragmentWithLifecycle {

  private final SimpleAddressSpaceFilter filter;
  private final SubscriptionModel subscriptionModel;

  private final NodeId parentNodeId;
  private final UShort namespaceIndex;

  public SecurityAccessFragment(
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
    var securityAccessFolder =
        new UaFolderNode(
            getNodeContext(),
            deriveChildNodeId(parentNodeId, "SecurityAccess"),
            new QualifiedName(namespaceIndex, "SecurityAccess"),
            new LocalizedText("SecurityAccess"));

    getNodeManager().addNode(securityAccessFolder);

    securityAccessFolder.addReference(
        new Reference(
            securityAccessFolder.getNodeId(),
            ReferenceTypes.HasComponent,
            parentNodeId.expanded(),
            Direction.INVERSE));

    // AccessLevel_CurrentRead
    {
      var builder = new UaVariableNodeBuilder(getNodeContext());
      builder
          .setNodeId(deriveChildNodeId(securityAccessFolder.getNodeId(), "AccessLevel_CurrentRead"))
          .setBrowseName(new QualifiedName(namespaceIndex, "AccessLevel_CurrentRead"))
          .setDisplayName(new LocalizedText("AccessLevel_CurrentRead"))
          .setDataType(NodeIds.Int32)
          .setValueRank(ValueRanks.Scalar)
          .setAccessLevel(AccessLevel.toValue(AccessLevel.CurrentRead))
          .setUserAccessLevel(AccessLevel.toValue(AccessLevel.CurrentRead))
          .setMinimumSamplingInterval(0.0);

      builder.setValue(new DataValue(Variant.ofInt32(0)));

      UaVariableNode variableNode = builder.build();

      getNodeManager().addNode(variableNode);

      variableNode.addReference(
          new Reference(
              variableNode.getNodeId(),
              ReferenceTypes.HasComponent,
              securityAccessFolder.getNodeId().expanded(),
              Direction.INVERSE));
    }

    // AccessLevel_CurrentWrite
    {
      var builder = new UaVariableNodeBuilder(getNodeContext());
      builder
          .setNodeId(
              deriveChildNodeId(securityAccessFolder.getNodeId(), "AccessLevel_CurrentWrite"))
          .setBrowseName(new QualifiedName(namespaceIndex, "AccessLevel_CurrentWrite"))
          .setDisplayName(new LocalizedText("AccessLevel_CurrentWrite"))
          .setDataType(NodeIds.Int32)
          .setValueRank(ValueRanks.Scalar)
          .setAccessLevel(AccessLevel.toValue(AccessLevel.CurrentWrite))
          .setUserAccessLevel(AccessLevel.toValue(AccessLevel.CurrentWrite))
          .setMinimumSamplingInterval(0.0);

      builder.setValue(new DataValue(Variant.ofInt32(0)));

      UaVariableNode variableNode = builder.build();

      getNodeManager().addNode(variableNode);

      variableNode.addReference(
          new Reference(
              variableNode.getNodeId(),
              ReferenceTypes.HasComponent,
              securityAccessFolder.getNodeId().expanded(),
              Direction.INVERSE));
    }

    // AccessLevel_CurrentRead_NotUser
    {
      var builder = new UaVariableNodeBuilder(getNodeContext());
      builder
          .setNodeId(
              deriveChildNodeId(
                  securityAccessFolder.getNodeId(), "AccessLevel_CurrentRead_NotUser"))
          .setBrowseName(new QualifiedName(namespaceIndex, "AccessLevel_CurrentRead_NotUser"))
          .setDisplayName(new LocalizedText("AccessLevel_CurrentRead_NotUser"))
          .setDataType(NodeIds.Int32)
          .setValueRank(ValueRanks.Scalar)
          .setAccessLevel(AccessLevel.toValue(AccessLevel.CurrentRead))
          .setUserAccessLevel(AccessLevel.toValue())
          .setMinimumSamplingInterval(0.0);

      builder.setValue(new DataValue(Variant.ofInt32(0)));

      UaVariableNode variableNode = builder.build();

      getNodeManager().addNode(variableNode);

      variableNode.addReference(
          new Reference(
              variableNode.getNodeId(),
              ReferenceTypes.HasComponent,
              securityAccessFolder.getNodeId().expanded(),
              Direction.INVERSE));
    }

    // AccessLevel_CurrentWrite_NotUser
    {
      var builder = new UaVariableNodeBuilder(getNodeContext());
      builder
          .setNodeId(
              deriveChildNodeId(
                  securityAccessFolder.getNodeId(), "AccessLevel_CurrentWrite_NotUser"))
          .setBrowseName(new QualifiedName(namespaceIndex, "AccessLevel_CurrentWrite_NotUser"))
          .setDisplayName(new LocalizedText("AccessLevel_CurrentWrite_NotUser"))
          .setDataType(NodeIds.Int32)
          .setValueRank(ValueRanks.Scalar)
          .setAccessLevel(AccessLevel.toValue(AccessLevel.CurrentWrite))
          .setUserAccessLevel(AccessLevel.toValue())
          .setMinimumSamplingInterval(0.0);

      builder.setValue(new DataValue(Variant.ofInt32(0)));

      UaVariableNode variableNode = builder.build();

      getNodeManager().addNode(variableNode);

      variableNode.addReference(
          new Reference(
              variableNode.getNodeId(),
              ReferenceTypes.HasComponent,
              securityAccessFolder.getNodeId().expanded(),
              Direction.INVERSE));
    }

    // AccessLevel_CurrentRead_NotCurrentWrite
    {
      var builder = new UaVariableNodeBuilder(getNodeContext());
      builder
          .setNodeId(
              deriveChildNodeId(
                  securityAccessFolder.getNodeId(), "AccessLevel_CurrentRead_NotCurrentWrite"))
          .setBrowseName(
              new QualifiedName(namespaceIndex, "AccessLevel_CurrentRead_NotCurrentWrite"))
          .setDisplayName(new LocalizedText("AccessLevel_CurrentRead_NotCurrentWrite"))
          .setDataType(NodeIds.Int32)
          .setValueRank(ValueRanks.Scalar)
          .setAccessLevel(AccessLevel.toValue(AccessLevel.CurrentRead, AccessLevel.CurrentWrite))
          .setUserAccessLevel(AccessLevel.toValue(AccessLevel.CurrentRead))
          .setMinimumSamplingInterval(0.0);

      builder.setValue(new DataValue(Variant.ofInt32(0)));

      UaVariableNode variableNode = builder.build();

      getNodeManager().addNode(variableNode);

      variableNode.addReference(
          new Reference(
              variableNode.getNodeId(),
              ReferenceTypes.HasComponent,
              securityAccessFolder.getNodeId().expanded(),
              Direction.INVERSE));
    }

    // AccessLevel_CurrentWrite_NotCurrentRead
    {
      var builder = new UaVariableNodeBuilder(getNodeContext());
      builder
          .setNodeId(
              deriveChildNodeId(
                  securityAccessFolder.getNodeId(), "AccessLevel_CurrentWrite_NotCurrentRead"))
          .setBrowseName(
              new QualifiedName(namespaceIndex, "AccessLevel_CurrentWrite_NotCurrentRead"))
          .setDisplayName(new LocalizedText("AccessLevel_CurrentWrite_NotCurrentRead"))
          .setDataType(NodeIds.Int32)
          .setValueRank(ValueRanks.Scalar)
          .setAccessLevel(AccessLevel.toValue(AccessLevel.CurrentWrite, AccessLevel.CurrentRead))
          .setUserAccessLevel(AccessLevel.toValue(AccessLevel.CurrentWrite))
          .setMinimumSamplingInterval(0.0);

      builder.setValue(new DataValue(Variant.ofInt32(0)));

      UaVariableNode variableNode = builder.build();

      getNodeManager().addNode(variableNode);

      variableNode.addReference(
          new Reference(
              variableNode.getNodeId(),
              ReferenceTypes.HasComponent,
              securityAccessFolder.getNodeId().expanded(),
              Direction.INVERSE));
    }
  }
}

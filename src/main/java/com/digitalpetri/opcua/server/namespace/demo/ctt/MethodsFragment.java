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
import org.eclipse.milo.opcua.sdk.server.methods.AbstractMethodInvocationHandler;
import org.eclipse.milo.opcua.sdk.server.nodes.UaFolderNode;
import org.eclipse.milo.opcua.sdk.server.nodes.UaMethodNode;
import org.eclipse.milo.opcua.sdk.server.nodes.UaMethodNode.UaMethodNodeBuilder;
import org.eclipse.milo.opcua.sdk.server.util.SubscriptionModel;
import org.eclipse.milo.opcua.stack.core.NodeIds;
import org.eclipse.milo.opcua.stack.core.ReferenceTypes;
import org.eclipse.milo.opcua.stack.core.types.builtin.LocalizedText;
import org.eclipse.milo.opcua.stack.core.types.builtin.NodeId;
import org.eclipse.milo.opcua.stack.core.types.builtin.QualifiedName;
import org.eclipse.milo.opcua.stack.core.types.builtin.Variant;
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.UShort;
import org.eclipse.milo.opcua.stack.core.types.structured.Argument;

public class MethodsFragment extends ManagedAddressSpaceFragmentWithLifecycle {

  private static final Argument[] INPUT_ARGUMENTS = {
    new Argument("i", NodeIds.Int32, -1, null, null)
  };

  private static final Argument[] OUTPUT_ARGUMENTS = {
    new Argument("o", NodeIds.Int32, -1, null, null)
  };

  private final SimpleAddressSpaceFilter filter;
  private final SubscriptionModel subscriptionModel;

  private final NodeId parentNodeId;
  private final UShort namespaceIndex;

  public MethodsFragment(
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
    var methodFolder =
        new UaFolderNode(
            getNodeContext(),
            deriveChildNodeId(parentNodeId, "Methods"),
            new QualifiedName(namespaceIndex, "Methods"),
            new LocalizedText("Methods"));

    getNodeManager().addNode(methodFolder);

    methodFolder.addReference(
        new Reference(
            methodFolder.getNodeId(),
            ReferenceTypes.HasComponent,
            parentNodeId.expanded(),
            Direction.INVERSE));

    addMethodNoArgs(methodFolder.getNodeId());
    addMethodI(methodFolder.getNodeId());
    addMethodO(methodFolder.getNodeId());
    addMethodIO(methodFolder.getNodeId());
  }

  private void addMethodNoArgs(NodeId parentNodeId) {
    var builder = new UaMethodNodeBuilder(getNodeContext());
    builder.setNodeId(deriveChildNodeId(parentNodeId, "MethodNoArgs"));
    builder.setBrowseName(new QualifiedName(namespaceIndex, "MethodNoArgs"));
    builder.setDisplayName(new LocalizedText("MethodNoArgs"));

    UaMethodNode methodNode = builder.build();

    methodNode.setInvocationHandler(
        new AbstractMethodInvocationHandler(methodNode) {
          @Override
          public Argument[] getInputArguments() {
            Argument[] inputArguments = methodNode.getInputArguments();
            return inputArguments != null ? inputArguments : new Argument[0];
          }

          @Override
          public Argument[] getOutputArguments() {
            Argument[] outputArguments = methodNode.getOutputArguments();
            return outputArguments != null ? outputArguments : new Argument[0];
          }

          @Override
          protected Variant[] invoke(InvocationContext invocationContext, Variant[] inputValues) {
            return new Variant[0];
          }
        });

    getNodeManager().addNode(methodNode);

    methodNode.addReference(
        new Reference(
            methodNode.getNodeId(),
            ReferenceTypes.HasComponent,
            parentNodeId.expanded(),
            Direction.INVERSE));
  }

  private void addMethodI(NodeId parentNodeId) {
    var builder = new UaMethodNodeBuilder(getNodeContext());
    builder.setNodeId(deriveChildNodeId(parentNodeId, "MethodI"));
    builder.setBrowseName(new QualifiedName(namespaceIndex, "MethodI"));
    builder.setDisplayName(new LocalizedText("MethodI"));

    UaMethodNode methodNode = builder.build();
    methodNode.setInputArguments(INPUT_ARGUMENTS);

    methodNode.setInvocationHandler(
        new AbstractMethodInvocationHandler(methodNode) {
          @Override
          public Argument[] getInputArguments() {
            Argument[] inputArguments = methodNode.getInputArguments();
            return inputArguments != null ? inputArguments : new Argument[0];
          }

          @Override
          public Argument[] getOutputArguments() {
            Argument[] outputArguments = methodNode.getOutputArguments();
            return outputArguments != null ? outputArguments : new Argument[0];
          }

          @Override
          protected Variant[] invoke(InvocationContext invocationContext, Variant[] inputValues) {
            return new Variant[0];
          }
        });

    getNodeManager().addNode(methodNode);

    methodNode.addReference(
        new Reference(
            methodNode.getNodeId(),
            ReferenceTypes.HasComponent,
            parentNodeId.expanded(),
            Direction.INVERSE));
  }

  private void addMethodO(NodeId parentNodeId) {
    var builder = new UaMethodNodeBuilder(getNodeContext());
    builder.setNodeId(deriveChildNodeId(parentNodeId, "MethodO"));
    builder.setBrowseName(new QualifiedName(namespaceIndex, "MethodO"));
    builder.setDisplayName(new LocalizedText("MethodO"));

    UaMethodNode methodNode = builder.build();
    methodNode.setOutputArguments(OUTPUT_ARGUMENTS);

    methodNode.setInvocationHandler(
        new AbstractMethodInvocationHandler(methodNode) {
          @Override
          public Argument[] getInputArguments() {
            Argument[] inputArguments = methodNode.getInputArguments();
            return inputArguments != null ? inputArguments : new Argument[0];
          }

          @Override
          public Argument[] getOutputArguments() {
            Argument[] outputArguments = methodNode.getOutputArguments();
            return outputArguments != null ? outputArguments : new Argument[0];
          }

          @Override
          protected Variant[] invoke(InvocationContext invocationContext, Variant[] inputValues) {
            return new Variant[] {Variant.ofInt32(42)};
          }
        });

    getNodeManager().addNode(methodNode);

    methodNode.addReference(
        new Reference(
            methodNode.getNodeId(),
            ReferenceTypes.HasComponent,
            parentNodeId.expanded(),
            Direction.INVERSE));
  }

  private void addMethodIO(NodeId parentNodeId) {
    var builder = new UaMethodNodeBuilder(getNodeContext());
    builder.setNodeId(deriveChildNodeId(parentNodeId, "MethodIO"));
    builder.setBrowseName(new QualifiedName(namespaceIndex, "MethodIO"));
    builder.setDisplayName(new LocalizedText("MethodIO"));

    UaMethodNode methodNode = builder.build();
    methodNode.setInputArguments(INPUT_ARGUMENTS);
    methodNode.setOutputArguments(OUTPUT_ARGUMENTS);

    methodNode.setInvocationHandler(
        new AbstractMethodInvocationHandler(methodNode) {
          @Override
          public Argument[] getInputArguments() {
            Argument[] inputArguments = methodNode.getInputArguments();
            return inputArguments != null ? inputArguments : new Argument[0];
          }

          @Override
          public Argument[] getOutputArguments() {
            Argument[] outputArguments = methodNode.getOutputArguments();
            return outputArguments != null ? outputArguments : new Argument[0];
          }

          @Override
          protected Variant[] invoke(InvocationContext invocationContext, Variant[] inputValues) {
            return new Variant[] {Variant.ofInt32((Integer) inputValues[0].getValue())};
          }
        });

    getNodeManager().addNode(methodNode);

    methodNode.addReference(
        new Reference(
            methodNode.getNodeId(),
            ReferenceTypes.HasComponent,
            parentNodeId.expanded(),
            Direction.INVERSE));
  }
}

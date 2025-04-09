package com.digitalpetri.opcua.server.namespace.demo;

import static org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.Unsigned.ubyte;
import static org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.Unsigned.uint;

import java.util.ArrayList;
import java.util.List;
import org.eclipse.milo.opcua.sdk.core.Reference;
import org.eclipse.milo.opcua.sdk.core.Reference.Direction;
import org.eclipse.milo.opcua.sdk.core.nodes.ObjectNodeProperties;
import org.eclipse.milo.opcua.sdk.server.AddressSpace.ReferenceResult.ReferenceList;
import org.eclipse.milo.opcua.sdk.server.AddressSpaceFilter;
import org.eclipse.milo.opcua.sdk.server.AttributeReader;
import org.eclipse.milo.opcua.sdk.server.ManagedAddressSpaceFragmentWithLifecycle;
import org.eclipse.milo.opcua.sdk.server.OpcUaServer;
import org.eclipse.milo.opcua.sdk.server.SimpleAddressSpaceFilter;
import org.eclipse.milo.opcua.sdk.server.items.DataItem;
import org.eclipse.milo.opcua.sdk.server.items.MonitoredItem;
import org.eclipse.milo.opcua.sdk.server.nodes.UaFolderNode;
import org.eclipse.milo.opcua.sdk.server.nodes.UaNode;
import org.eclipse.milo.opcua.sdk.server.nodes.UaObjectNode;
import org.eclipse.milo.opcua.sdk.server.util.SubscriptionModel;
import org.eclipse.milo.opcua.stack.core.NodeIds;
import org.eclipse.milo.opcua.stack.core.ReferenceTypes;
import org.eclipse.milo.opcua.stack.core.StatusCodes;
import org.eclipse.milo.opcua.stack.core.types.builtin.ByteString;
import org.eclipse.milo.opcua.stack.core.types.builtin.DataValue;
import org.eclipse.milo.opcua.stack.core.types.builtin.LocalizedText;
import org.eclipse.milo.opcua.stack.core.types.builtin.NodeId;
import org.eclipse.milo.opcua.stack.core.types.builtin.QualifiedName;
import org.eclipse.milo.opcua.stack.core.types.enumerated.TimestampsToReturn;
import org.eclipse.milo.opcua.stack.core.types.structured.ReadValueId;
import org.eclipse.milo.opcua.stack.core.types.structured.ViewDescription;
import org.jspecify.annotations.Nullable;

public class TurtleNodesFragment extends ManagedAddressSpaceFragmentWithLifecycle {

  private final long depth;
  private final AddressSpaceFilter filter;
  private final SubscriptionModel subscriptionModel;

  private final DemoNamespace namespace;

  public TurtleNodesFragment(OpcUaServer server, DemoNamespace namespace) {
    super(server, namespace);

    this.namespace = namespace;

    depth = namespace.getConfig().getLong("address-space.turtles.depth");

    filter =
        SimpleAddressSpaceFilter.create(
            nodeId -> getNodeManager().containsNode(nodeId) || validTurtleNode(nodeId, depth));

    subscriptionModel = new SubscriptionModel(server, this);
    getLifecycleManager().addLifecycle(subscriptionModel);

    getLifecycleManager().addStartupTask(this::addTurtleNodes);
  }

  @Override
  public AddressSpaceFilter getFilter() {
    return filter;
  }

  @Override
  public List<ReferenceResult> browse(
      BrowseContext context, ViewDescription view, List<NodeId> nodeIds) {

    var results = new ArrayList<ReferenceResult>();

    for (NodeId nodeId : nodeIds) {
      UaNode node = getNodeManager().get(nodeId);

      if (node != null) {
        results.add(ReferenceResult.of(node.getReferences()));
      } else if (validTurtleNode(nodeId, depth)) {
        results.add(ReferenceResult.of(turtleReferences(nodeId)));
      } else {
        results.add(ReferenceResult.unknown());
      }
    }

    return results;
  }

  @Override
  public ReferenceList gather(
      BrowseContext context, ViewDescription viewDescription, NodeId nodeId) {

    var references = new ArrayList<Reference>();
    references.addAll(getNodeManager().getReferences(nodeId));
    references.addAll(turtleReferences(nodeId));

    return ReferenceResult.of(references);
  }

  @Override
  public List<DataValue> read(
      ReadContext context,
      Double maxAge,
      TimestampsToReturn timestamps,
      List<ReadValueId> readValueIds) {

    var values = new ArrayList<DataValue>();

    for (ReadValueId readValueId : readValueIds) {
      UaNode node = getNodeManager().get(readValueId.getNodeId());
      if (node == null) {
        node = turtleNode(readValueId.getNodeId());
      }

      if (node != null) {
        DataValue value =
            AttributeReader.readAttribute(
                context,
                node,
                readValueId.getAttributeId(),
                timestamps,
                readValueId.getIndexRange(),
                readValueId.getDataEncoding());

        values.add(value);
      } else {
        values.add(new DataValue(StatusCodes.Bad_NodeIdUnknown));
      }
    }

    return values;
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

  private void addTurtleNodes() {
    var turtlesFolder =
        new UaFolderNode(
            getNodeContext(),
            new NodeId(namespace.getNamespaceIndex(), "[turtles]"),
            new QualifiedName(namespace.getNamespaceIndex(), "Turtles"),
            new LocalizedText("Turtles"));

    turtlesFolder.setDescription(new LocalizedText("Turtles all the way down!"));

    try (var inputStream = DemoNamespace.class.getResourceAsStream("/turtle-icon.png")) {
      if (inputStream != null) {
        turtlesFolder.setIcon(ByteString.of(inputStream.readAllBytes()));
        turtlesFolder
            .getPropertyNode(ObjectNodeProperties.Icon)
            .ifPresent(node -> node.setDataType(NodeIds.ImagePNG));
      }
    } catch (Exception ignored) {
    }

    getNodeManager().addNode(turtlesFolder);

    turtlesFolder.addReference(
        new Reference(
            turtlesFolder.getNodeId(),
            ReferenceTypes.Organizes,
            namespace.getDemoFolder().getNodeId().expanded(),
            Direction.INVERSE));

    turtlesFolder.addReference(
        new Reference(
            turtlesFolder.getNodeId(),
            ReferenceTypes.Organizes,
            new NodeId(namespace.getNamespaceIndex(), "[turtles]0").expanded(),
            Direction.FORWARD));
  }

  private @Nullable UaObjectNode turtleNode(NodeId nodeId) {
    try {
      long turtleNumber = Long.parseLong(nodeId.getIdentifier().toString().substring(9));

      if (turtleNumber < depth) {
        return new UaObjectNode(
            getNodeContext(),
            new NodeId(namespace.getNamespaceIndex(), "[turtles]" + turtleNumber),
            new QualifiedName(namespace.getNamespaceIndex(), "Turtle" + turtleNumber),
            new LocalizedText("Turtle" + turtleNumber),
            LocalizedText.NULL_VALUE,
            uint(0),
            uint(0),
            ubyte(0));
      }
    } catch (Exception ignored) {
    }

    return null;
  }

  private List<Reference> turtleReferences(NodeId nodeId) {
    try {
      long turtleNumber = Long.parseLong(nodeId.getIdentifier().toString().substring(9));
      long previousTurtle = turtleNumber - 1;
      long nextTurtle = turtleNumber + 1;
      var references = new ArrayList<Reference>();

      if (previousTurtle >= 0) {
        references.add(
            new Reference(
                nodeId,
                ReferenceTypes.Organizes,
                new NodeId(namespace.getNamespaceIndex(), "[turtles]" + previousTurtle).expanded(),
                Direction.INVERSE));
      }
      if (nextTurtle < depth) {
        references.add(
            new Reference(
                nodeId,
                ReferenceTypes.Organizes,
                new NodeId(namespace.getNamespaceIndex(), "[turtles]" + nextTurtle).expanded(),
                Direction.FORWARD));
      }

      return references;
    } catch (Exception e) {
      return List.of();
    }
  }

  private static boolean validTurtleNode(NodeId nodeId, long depth) {
    String id = nodeId.getIdentifier().toString();

    if (id.startsWith("[turtles]")) {
      String idWithoutPrefix = id.substring(9);
      try {
        return Long.parseLong(idWithoutPrefix) < depth;
      } catch (NumberFormatException ignored) {
      }
    }
    return false;
  }
}

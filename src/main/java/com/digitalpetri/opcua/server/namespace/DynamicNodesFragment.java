package com.digitalpetri.opcua.server.namespace;

import static com.digitalpetri.opcua.server.namespace.Util.deriveChildNodeId;
import static org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.Unsigned.ubyte;
import static org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.Unsigned.uint;
import static org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.Unsigned.ulong;
import static org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.Unsigned.ushort;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
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
import org.eclipse.milo.opcua.sdk.server.nodes.filters.AttributeFilters;
import org.eclipse.milo.opcua.sdk.server.util.SubscriptionModel;
import org.eclipse.milo.opcua.stack.core.OpcUaDataType;
import org.eclipse.milo.opcua.stack.core.ReferenceTypes;
import org.eclipse.milo.opcua.stack.core.types.builtin.ByteString;
import org.eclipse.milo.opcua.stack.core.types.builtin.DataValue;
import org.eclipse.milo.opcua.stack.core.types.builtin.DateTime;
import org.eclipse.milo.opcua.stack.core.types.builtin.ExtensionObject;
import org.eclipse.milo.opcua.stack.core.types.builtin.LocalizedText;
import org.eclipse.milo.opcua.stack.core.types.builtin.NodeId;
import org.eclipse.milo.opcua.stack.core.types.builtin.QualifiedName;
import org.eclipse.milo.opcua.stack.core.types.builtin.StatusCode;
import org.eclipse.milo.opcua.stack.core.types.builtin.Variant;
import org.eclipse.milo.opcua.stack.core.types.builtin.XmlElement;

public class DynamicNodesFragment extends ManagedAddressSpaceFragmentWithLifecycle {

  private final Map<OpcUaDataType, DataValue> randomValues = new ConcurrentHashMap<>();

  private final AddressSpaceFilter filter;
  private final SubscriptionModel subscriptionModel;

  private final DemoNamespace namespace;

  public DynamicNodesFragment(OpcUaServer server, DemoNamespace namespace) {
    super(server, namespace);

    this.namespace = namespace;

    filter = SimpleAddressSpaceFilter.create(getNodeManager()::containsNode);

    subscriptionModel = new SubscriptionModel(server, this);
    getLifecycleManager().addLifecycle(subscriptionModel);

    ScheduledFuture<?> scheduledFuture =
        server
            .getConfig()
            .getScheduledExecutorService()
            .scheduleAtFixedRate(
                () -> getServer().getConfig().getExecutor().execute(this::updateRandomValues),
                0,
                100,
                TimeUnit.MILLISECONDS);

    getLifecycleManager().addShutdownTask(() -> scheduledFuture.cancel(true));

    getLifecycleManager().addStartupTask(this::addDynamicNodes);
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

  private void addDynamicNodes() {
    var dynamicFolder =
        new UaFolderNode(
            getNodeContext(),
            deriveChildNodeId(namespace.getDemoFolder().getNodeId(), "Dynamic"),
            new QualifiedName(namespace.getNamespaceIndex(), "Dynamic"),
            new LocalizedText("Dynamic"));

    getNodeManager().addNode(dynamicFolder);

    dynamicFolder.addReference(
        new Reference(
            dynamicFolder.getNodeId(),
            ReferenceTypes.Organizes,
            namespace.getDemoFolder().getNodeId().expanded(),
            Direction.INVERSE));

    for (OpcUaDataType dataType : OpcUaDataType.values()) {
      if (dataType == OpcUaDataType.DiagnosticInfo) continue;

      var builder = new UaVariableNodeBuilder(getNodeContext());
      builder
          .setNodeId(deriveChildNodeId(dynamicFolder.getNodeId(), dataType.name()))
          .setBrowseName(new QualifiedName(namespace.getNamespaceIndex(), dataType.name()))
          .setDisplayName(new LocalizedText(dataType.name()))
          .setDataType(dataType.getNodeId())
          .setAccessLevel(AccessLevel.toValue(AccessLevel.READ_ONLY))
          .setUserAccessLevel(AccessLevel.toValue(AccessLevel.READ_ONLY))
          .setMinimumSamplingInterval(100.0);

      var variableNode = builder.build();

      variableNode
          .getFilterChain()
          .addLast(
              AttributeFilters.getValue(
                  ctx -> randomValues.getOrDefault(dataType, new DataValue(Variant.NULL_VALUE))));

      getNodeManager().addNode(variableNode);

      variableNode.addReference(
          new Reference(
              variableNode.getNodeId(),
              ReferenceTypes.HasComponent,
              dynamicFolder.getNodeId().expanded(),
              Direction.INVERSE));
    }
  }

  private void updateRandomValues() {
    for (OpcUaDataType dataType : OpcUaDataType.values()) {
      randomValues.put(dataType, getRandomValue(dataType));
    }
  }

  private static DataValue getRandomValue(OpcUaDataType dataType) {
    Object v =
        switch (dataType) {
          case Boolean -> Math.random() > 0.5;
          case SByte -> (byte) (Math.random() * 256 - 128);
          case Int16 -> (short) (Math.random() * 65536 - 32768);
          case Int32 -> (int) (Math.random() * Integer.MAX_VALUE * 2 - Integer.MAX_VALUE);
          case Int64 -> (long) (Math.random() * Long.MAX_VALUE);
          case Byte -> ubyte((short) (Math.random() * 256));
          case UInt16 -> ushort((int) (Math.random() * 65536));
          case UInt32 -> uint((long) (Math.random() * Integer.MAX_VALUE));
          case UInt64 -> ulong(Math.round(Math.random() * Long.MAX_VALUE));
          case Float -> (float) Math.random() * 1000;
          case Double -> Math.random() * 1000;
          case String -> UUID.randomUUID().toString();
          case DateTime -> new DateTime();
          case Guid -> UUID.randomUUID();
          case ByteString -> {
            byte[] bytes = new byte[16];
            new java.util.Random().nextBytes(bytes);
            yield ByteString.of(bytes);
          }
          case XmlElement -> new XmlElement("<random>" + UUID.randomUUID() + "</random>");
          case NodeId -> new NodeId(1, (int) (Math.random() * 1000));
          case ExpandedNodeId -> new NodeId(1, (int) (Math.random() * 1000)).expanded();
          case StatusCode -> new StatusCode((int) (Math.random() * 0xFFFF));
          case QualifiedName ->
              new QualifiedName(1, "Random-" + UUID.randomUUID().toString().substring(0, 8));
          case LocalizedText ->
              new LocalizedText("en", "Random-" + UUID.randomUUID().toString().substring(0, 8));
          case ExtensionObject -> {
            byte[] bytes = new byte[8];
            new java.util.Random().nextBytes(bytes);
            yield ExtensionObject.of(ByteString.of(bytes), NodeId.NULL_VALUE);
          }
          case DataValue -> new DataValue(Variant.of(Math.random() * 100));
          case Variant -> Variant.of(Math.random() * 100);
          case DiagnosticInfo -> null;
        };

    if (v instanceof Variant variant) {
      return new DataValue(variant);
    } else {
      return new DataValue(Variant.of(v));
    }
  }
}

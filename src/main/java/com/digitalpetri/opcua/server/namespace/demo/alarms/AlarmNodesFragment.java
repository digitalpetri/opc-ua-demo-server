package com.digitalpetri.opcua.server.namespace.demo.alarms;

import static com.digitalpetri.opcua.server.namespace.demo.Util.deriveChildNodeId;
import static org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.Unsigned.ubyte;

import com.digitalpetri.opcua.server.namespace.demo.DemoNamespace;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import org.eclipse.milo.opcua.sdk.core.Reference;
import org.eclipse.milo.opcua.sdk.core.Reference.Direction;
import org.eclipse.milo.opcua.sdk.server.AddressSpaceFilter;
import org.eclipse.milo.opcua.sdk.server.ManagedAddressSpaceFragmentWithLifecycle;
import org.eclipse.milo.opcua.sdk.server.NodeManager;
import org.eclipse.milo.opcua.sdk.server.OpcUaServer;
import org.eclipse.milo.opcua.sdk.server.SimpleAddressSpaceFilter;
import org.eclipse.milo.opcua.sdk.server.conditions.Condition;
import org.eclipse.milo.opcua.sdk.server.items.DataItem;
import org.eclipse.milo.opcua.sdk.server.items.MonitoredItem;
import org.eclipse.milo.opcua.sdk.server.nodes.UaFolderNode;
import org.eclipse.milo.opcua.sdk.server.nodes.UaNode;
import org.eclipse.milo.opcua.sdk.server.nodes.UaObjectNode;
import org.eclipse.milo.opcua.sdk.server.util.SubscriptionModel;
import org.eclipse.milo.opcua.stack.core.NodeIds;
import org.eclipse.milo.opcua.stack.core.ReferenceTypes;
import org.eclipse.milo.opcua.stack.core.UaException;
import org.eclipse.milo.opcua.stack.core.types.builtin.LocalizedText;
import org.eclipse.milo.opcua.stack.core.types.builtin.NodeId;
import org.eclipse.milo.opcua.stack.core.types.builtin.QualifiedName;
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.UByte;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A simulated plant under {@code Demo/Alarms} exercising the server-side Alarms and Conditions
 * implementation against something shaped like real equipment.
 *
 * <p>The plant is an area-based event notifier hierarchy — {@code Server → Plant → area →
 * equipment} — so an alarm view narrows as a client subscribes further down the tree, and
 * ConditionRefresh replays only the conditions in scope. Each area holds equipment models whose
 * deterministic simulations drive their alarms through stories a plant operator would recognize.
 *
 * <p>This is deliberately separate from {@link
 * com.digitalpetri.opcua.server.namespace.demo.ctt.AlarmsAndConditionsFragment}, which stays flat
 * and boring for CTT conformance runs.
 */
public final class AlarmNodesFragment extends ManagedAddressSpaceFragmentWithLifecycle {

  public static final String ROOT_ID = "Demo.Alarms";
  public static final String PLANT_ID = ROOT_ID + ".Plant";
  public static final String POWER_HOUSE_ID = PLANT_ID + ".PowerHouse";
  public static final String TANK_FARM_ID = PLANT_ID + ".TankFarm";

  private static final Logger LOGGER = LoggerFactory.getLogger(AlarmNodesFragment.class);

  /** The SubscribeToEvents bit of the EventNotifier attribute (Part 3 §8.59). */
  private static final int SUBSCRIBE_TO_EVENTS = 0x01;

  private final AddressSpaceFilter filter;
  private final SubscriptionModel subscriptionModel;

  private final DemoNamespace namespace;
  private final Duration tickInterval;

  private final List<EquipmentModel> models = new ArrayList<>();

  /** Guards the models and Conditions, making a tick and shutdown mutually exclusive. */
  private final ReentrantLock tickLock = new ReentrantLock();

  private @Nullable ScheduledFuture<?> tickFuture;
  private @Nullable Reference serverNotifierReference;
  private volatile boolean running;
  private long tick;

  public AlarmNodesFragment(OpcUaServer server, DemoNamespace namespace, Duration tickInterval) {
    super(server, namespace);

    if (tickInterval.isZero() || tickInterval.isNegative()) {
      throw new IllegalArgumentException("alarm tick interval must be positive: " + tickInterval);
    }

    this.namespace = namespace;
    this.tickInterval = tickInterval;

    filter = SimpleAddressSpaceFilter.create(getNodeManager()::containsNode);

    subscriptionModel = new SubscriptionModel(server, namespace);
    getLifecycleManager().addLifecycle(subscriptionModel);

    getLifecycleManager().addStartupTask(this::startPlant);
    getLifecycleManager().addShutdownTask(this::stopPlant);
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

  private void startPlant() {
    try {
      buildPlant();

      conditions().forEach(getServer().getConditionManager()::register);

      running = true;

      // The scheduler only dispatches: a tick evaluates fifteen conditions and fans their events
      // out synchronously, which is far too much to run on the shared scheduled executor that
      // every publish timer and sampling task in the server shares.
      long intervalNanos = tickInterval.toNanos();
      tickFuture =
          getServer()
              .getConfig()
              .getScheduledExecutorService()
              .scheduleWithFixedDelay(
                  () -> getServer().getExecutorService().execute(this::advanceSafely),
                  intervalNanos,
                  intervalNanos,
                  TimeUnit.NANOSECONDS);
    } catch (UaException e) {
      throw new IllegalStateException("failed to create the Demo/Alarms plant", e);
    }
  }

  /**
   * Stop the simulation and unregister its Conditions, without interrupting a tick that is midway
   * through driving a Condition's state machine.
   *
   * <p>Cancelling without interruption stops further dispatches and lets any tick in flight finish;
   * taking the tick lock then waits for it. Clearing {@code running} under that lock closes the
   * remaining race, where a tick dispatched to the executor just before the cancel would otherwise
   * run and evaluate alarms whose Conditions have just been unregistered.
   *
   * <p>Finally, the one Reference pair this fragment planted outside its own NodeManager is
   * removed, since nothing else will drop it when the fragment's Nodes go away.
   */
  private void stopPlant() {
    ScheduledFuture<?> future = tickFuture;
    if (future != null) {
      future.cancel(false);
      tickFuture = null;
    }

    tickLock.lock();
    try {
      running = false;

      conditions().forEach(getServer().getConditionManager()::unregister);
      models.clear();
    } finally {
      tickLock.unlock();
    }

    unregisterServerNotifier();
  }

  /**
   * Build the plant, in an order that is load-bearing: the notifier hierarchy and each area's
   * HasEventSource Reference to its equipment must exist <i>before</i> any Condition names that
   * equipment as its condition source.
   *
   * <p>Condition wiring adds a {@code Server → HasEventSource → source} shortcut only when the
   * source is not already the target of a HasEventSource-or-subtype Reference. Wiring the area
   * first suppresses that shortcut, which is what keeps area-scoped subscriptions and
   * ConditionRefresh from seeing the whole plant.
   */
  private void buildPlant() throws UaException {
    UaFolderNode alarmsFolder =
        new UaFolderNode(
            getNodeContext(),
            deriveChildNodeId(namespace.getDemoFolder().getNodeId(), "Alarms"),
            new QualifiedName(namespace.getNamespaceIndex(), "Alarms"),
            LocalizedText.english("Alarms"));

    getNodeManager().addNode(alarmsFolder);

    alarmsFolder.addReference(
        new Reference(
            alarmsFolder.getNodeId(),
            ReferenceTypes.Organizes,
            namespace.getDemoFolder().getNodeId().expanded(),
            Direction.INVERSE));

    var factory = new AlarmNodeFactory(getNodeContext(), namespace.getNamespaceIndex());

    // The Plant hangs off the Alarms folder in the browse tree and off the Server Object in the
    // notifier hierarchy, so its two parent References point at different Nodes. Areas below it
    // take their single hierarchical parent from HasNotifier alone.
    UaObjectNode plant = factory.areaNode(alarmsFolder.getNodeId(), "Plant");
    factory.addComponent(plant, alarmsFolder.getNodeId());
    registerEventNotifier(plant, null);

    UaObjectNode powerHouse = factory.areaNode(plant.getNodeId(), "PowerHouse");
    registerEventNotifier(powerHouse, plant);

    UaObjectNode tankFarm = factory.areaNode(plant.getNodeId(), "TankFarm");
    registerEventNotifier(tankFarm, plant);

    models.add(new BoilerModel(factory, powerHouse));
    models.add(new PumpModel(factory, powerHouse));

    var tank = new TankModel(factory, tankFarm);
    models.add(tank);
    models.add(new PlcModel(factory, tankFarm, tank));

    // HasCondition is not hierarchical, so Condition wiring alone leaves the alarm Nodes with no
    // parent in a client's browse tree. Making each one a component of the equipment it reports
    // for gives it the single hierarchical parent every other Node in the plant has.
    for (Condition condition : conditions()) {
      NodeId sourceNodeId = condition.getNode().getSourceNode();
      if (sourceNodeId != null) {
        factory.addComponent(condition.getNode(), sourceNodeId);
      }
    }
  }

  /** The Conditions of every model currently in the plant. */
  private List<Condition> conditions() {
    return models.stream().flatMap(model -> model.conditions().stream()).toList();
  }

  /**
   * Register {@code area} as an event notifier, replicating {@code
   * ManagedNamespace.registerEventNotifier} — which is protected on ManagedNamespace and so out of
   * reach of a fragment.
   *
   * <p>Sets the SubscribeToEvents bit of the EventNotifier attribute and adds a HasNotifier
   * Reference pair from the parent area, or from the Server Object when {@code parent} is null.
   *
   * @param area the area {@link UaObjectNode} to register.
   * @param parent the parent area, or null to attach the area directly to the Server Object.
   */
  private void registerEventNotifier(UaObjectNode area, @Nullable UaObjectNode parent) {
    UByte eventNotifier = area.getEventNotifier();
    int bits = eventNotifier != null ? eventNotifier.intValue() : 0;
    area.setEventNotifier(ubyte(bits | SUBSCRIBE_TO_EVENTS));

    NodeId parentNodeId = parent != null ? parent.getNodeId() : NodeIds.Server;

    // The Reference pair has to live in the NodeManager that owns its source Node, so a Reference
    // rooted at the Server Object goes into the core NodeManager rather than this fragment's.
    NodeManager<UaNode> nodeManager =
        parent != null
            ? getNodeManager()
            : getServer()
                .getAddressSpaceManager()
                .getManagedNode(NodeIds.Server)
                .orElseThrow(() -> new IllegalStateException("Server Object Node not found"))
                .getNodeManager();

    var reference =
        new Reference(parentNodeId, NodeIds.HasNotifier, area.getNodeId().expanded(), true);

    nodeManager.addReferences(reference, getServer().getNamespaceTable());

    if (parent == null) {
      // This pair lives in a NodeManager that outlives the fragment, so shutdown has to take it
      // back out; the fragment's own References go away with its NodeManager.
      serverNotifierReference = reference;
    }
  }

  /**
   * Remove the {@code Server → HasNotifier → Plant} Reference pair this fragment added to the core
   * NodeManager, so a shut down plant does not leave the Server Object advertising a notifier whose
   * Nodes no longer exist.
   */
  private void unregisterServerNotifier() {
    Reference reference = serverNotifierReference;
    if (reference == null) {
      return;
    }

    serverNotifierReference = null;

    getServer()
        .getAddressSpaceManager()
        .getManagedNode(NodeIds.Server)
        .ifPresent(
            serverNode ->
                serverNode
                    .getNodeManager()
                    .removeReferences(reference, getServer().getNamespaceTable()));
  }

  private void advanceSafely() {
    // A scheduled task never runs concurrently with itself, but the executor tasks it dispatches
    // do, so skip this tick if one is still in flight rather than letting ticks queue up behind
    // the lock and replay the backlog later. A shutdown holding the lock skips it for the same
    // reason: the plant is going away.
    if (!tickLock.tryLock()) {
      return;
    }

    try {
      // Re-checked under the lock: this tick may have been dispatched before a shutdown that has
      // since unregistered the Conditions the models are about to drive.
      if (!running) {
        return;
      }

      long current = tick++;
      for (EquipmentModel model : models) {
        model.tick(current);
      }
    } catch (RuntimeException e) {
      // A failing model must not kill the scheduled task and freeze the whole plant.
      LOGGER.error("Error advancing the Demo/Alarms plant simulation", e);
    } finally {
      tickLock.unlock();
    }
  }
}

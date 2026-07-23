package com.digitalpetri.opcua.server.namespace.demo.alarms;

import static org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.Unsigned.uint;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.digitalpetri.opcua.server.OpcUaDemoServer;
import com.digitalpetri.opcua.server.OpcUaTestClient;
import com.digitalpetri.opcua.server.OpcUaTestServerBuilder;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Stream;
import org.eclipse.milo.opcua.sdk.client.OpcUaClient;
import org.eclipse.milo.opcua.sdk.client.subscriptions.OpcUaMonitoredItem;
import org.eclipse.milo.opcua.sdk.client.subscriptions.OpcUaSubscription;
import org.eclipse.milo.opcua.sdk.core.Reference;
import org.eclipse.milo.opcua.sdk.server.OpcUaServer;
import org.eclipse.milo.opcua.sdk.server.conditions.AlarmCondition;
import org.eclipse.milo.opcua.sdk.server.conditions.Condition;
import org.eclipse.milo.opcua.sdk.server.conditions.DiscreteAlarm;
import org.eclipse.milo.opcua.sdk.server.conditions.ExclusiveDeviationAlarm;
import org.eclipse.milo.opcua.sdk.server.conditions.ExclusiveLevelAlarm;
import org.eclipse.milo.opcua.sdk.server.conditions.ExclusiveRateOfChangeAlarm;
import org.eclipse.milo.opcua.sdk.server.conditions.InstrumentDiagnosticAlarm;
import org.eclipse.milo.opcua.sdk.server.conditions.NonExclusiveDeviationAlarm;
import org.eclipse.milo.opcua.sdk.server.conditions.NonExclusiveLevelAlarm;
import org.eclipse.milo.opcua.sdk.server.conditions.NonExclusiveRateOfChangeAlarm;
import org.eclipse.milo.opcua.sdk.server.conditions.OffNormalAlarm;
import org.eclipse.milo.opcua.sdk.server.conditions.SystemDiagnosticAlarm;
import org.eclipse.milo.opcua.sdk.server.conditions.SystemOffNormalAlarm;
import org.eclipse.milo.opcua.sdk.server.conditions.TripAlarm;
import org.eclipse.milo.opcua.sdk.server.model.objects.AcknowledgeableConditionTypeNode;
import org.eclipse.milo.opcua.sdk.server.model.objects.ConditionTypeNode;
import org.eclipse.milo.opcua.sdk.server.model.objects.DiscreteAlarmTypeNode;
import org.eclipse.milo.opcua.sdk.server.model.objects.ExclusiveDeviationAlarmTypeNode;
import org.eclipse.milo.opcua.sdk.server.model.objects.ExclusiveLevelAlarmTypeNode;
import org.eclipse.milo.opcua.sdk.server.model.objects.ExclusiveRateOfChangeAlarmTypeNode;
import org.eclipse.milo.opcua.sdk.server.model.objects.InstrumentDiagnosticAlarmTypeNode;
import org.eclipse.milo.opcua.sdk.server.model.objects.NonExclusiveDeviationAlarmTypeNode;
import org.eclipse.milo.opcua.sdk.server.model.objects.NonExclusiveLevelAlarmTypeNode;
import org.eclipse.milo.opcua.sdk.server.model.objects.NonExclusiveRateOfChangeAlarmTypeNode;
import org.eclipse.milo.opcua.sdk.server.model.objects.OffNormalAlarmTypeNode;
import org.eclipse.milo.opcua.sdk.server.model.objects.ShelvedStateMachineTypeNode;
import org.eclipse.milo.opcua.sdk.server.model.objects.SystemDiagnosticAlarmTypeNode;
import org.eclipse.milo.opcua.sdk.server.model.objects.SystemOffNormalAlarmTypeNode;
import org.eclipse.milo.opcua.sdk.server.model.objects.TripAlarmTypeNode;
import org.eclipse.milo.opcua.sdk.server.nodes.UaNode;
import org.eclipse.milo.opcua.sdk.server.nodes.UaObjectNode;
import org.eclipse.milo.opcua.sdk.server.nodes.UaVariableNode;
import org.eclipse.milo.opcua.stack.core.AttributeId;
import org.eclipse.milo.opcua.stack.core.NodeIds;
import org.eclipse.milo.opcua.stack.core.StatusCodes;
import org.eclipse.milo.opcua.stack.core.types.builtin.ByteString;
import org.eclipse.milo.opcua.stack.core.types.builtin.DataValue;
import org.eclipse.milo.opcua.stack.core.types.builtin.LocalizedText;
import org.eclipse.milo.opcua.stack.core.types.builtin.NodeId;
import org.eclipse.milo.opcua.stack.core.types.builtin.QualifiedName;
import org.eclipse.milo.opcua.stack.core.types.builtin.StatusCode;
import org.eclipse.milo.opcua.stack.core.types.builtin.Variant;
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.UInteger;
import org.eclipse.milo.opcua.stack.core.types.enumerated.BrowseDirection;
import org.eclipse.milo.opcua.stack.core.types.enumerated.BrowseResultMask;
import org.eclipse.milo.opcua.stack.core.types.structured.BrowseDescription;
import org.eclipse.milo.opcua.stack.core.types.structured.BrowseResult;
import org.eclipse.milo.opcua.stack.core.types.structured.CallMethodRequest;
import org.eclipse.milo.opcua.stack.core.types.structured.CallMethodResult;
import org.eclipse.milo.opcua.stack.core.types.structured.ContentFilter;
import org.eclipse.milo.opcua.stack.core.types.structured.ContentFilterElement;
import org.eclipse.milo.opcua.stack.core.types.structured.EventFilter;
import org.eclipse.milo.opcua.stack.core.types.structured.ReferenceDescription;
import org.eclipse.milo.opcua.stack.core.types.structured.SimpleAttributeOperand;
import org.eclipse.milo.opcua.stack.core.types.structured.WriteValue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Integration tests for the {@code Demo/Alarms} plant, covering the notifier-scope and
 * ConditionRefresh code paths that the flat CTT fixture cannot reach.
 */
class AlarmNodesIT {

  private static final int NAMESPACE_INDEX = 2;
  private static final Duration TIMEOUT = Duration.ofSeconds(15);

  /**
   * A fast tick so the simulations reach their interesting states inside a test window. Each tick
   * is still one simulated second, so the models behave exactly as they do at the default rate.
   */
  private static final String TICK_INTERVAL = "20 ms";

  private static final String BOILER = AlarmNodesFragment.POWER_HOUSE_ID + ".B-100";
  private static final String PUMP = AlarmNodesFragment.POWER_HOUSE_ID + ".P-101";
  private static final String TANK = AlarmNodesFragment.TANK_FARM_ID + ".TK-200";
  private static final String PLC = AlarmNodesFragment.TANK_FARM_ID + ".PLC-01";

  private static final List<AlarmSpec> POWER_HOUSE_ALARMS =
      List.of(
          new AlarmSpec(
              BOILER,
              "DrumLevelAlarm",
              NonExclusiveLevelAlarm.class,
              NonExclusiveLevelAlarmTypeNode.class,
              NodeIds.NonExclusiveLevelAlarmType),
          new AlarmSpec(
              BOILER,
              "SteamPressureAlarm",
              ExclusiveLevelAlarm.class,
              ExclusiveLevelAlarmTypeNode.class,
              NodeIds.ExclusiveLevelAlarmType),
          new AlarmSpec(
              BOILER,
              "OutletTemperatureAlarm",
              ExclusiveDeviationAlarm.class,
              ExclusiveDeviationAlarmTypeNode.class,
              NodeIds.ExclusiveDeviationAlarmType),
          new AlarmSpec(
              BOILER,
              "PilotFlameAlarm",
              OffNormalAlarm.class,
              OffNormalAlarmTypeNode.class,
              NodeIds.OffNormalAlarmType),
          new AlarmSpec(
              PUMP,
              "BearingTemperatureAlarm",
              ExclusiveLevelAlarm.class,
              ExclusiveLevelAlarmTypeNode.class,
              NodeIds.ExclusiveLevelAlarmType),
          new AlarmSpec(
              PUMP,
              "VibrationAlarm",
              NonExclusiveLevelAlarm.class,
              NonExclusiveLevelAlarmTypeNode.class,
              NodeIds.NonExclusiveLevelAlarmType),
          new AlarmSpec(
              PUMP,
              "WindingTemperatureAlarm",
              ExclusiveRateOfChangeAlarm.class,
              ExclusiveRateOfChangeAlarmTypeNode.class,
              NodeIds.ExclusiveRateOfChangeAlarmType),
          new AlarmSpec(
              PUMP,
              "OverloadTripAlarm",
              TripAlarm.class,
              TripAlarmTypeNode.class,
              NodeIds.TripAlarmType),
          new AlarmSpec(
              PUMP,
              "CommandedStateAlarm",
              DiscreteAlarm.class,
              DiscreteAlarmTypeNode.class,
              NodeIds.DiscreteAlarmType));

  private static final List<AlarmSpec> TANK_FARM_ALARMS =
      List.of(
          new AlarmSpec(
              TANK,
              "LevelAlarm",
              NonExclusiveLevelAlarm.class,
              NonExclusiveLevelAlarmTypeNode.class,
              NodeIds.NonExclusiveLevelAlarmType),
          new AlarmSpec(
              TANK,
              "LevelRateAlarm",
              NonExclusiveRateOfChangeAlarm.class,
              NonExclusiveRateOfChangeAlarmTypeNode.class,
              NodeIds.NonExclusiveRateOfChangeAlarmType),
          new AlarmSpec(
              TANK,
              "TemperatureAlarm",
              NonExclusiveDeviationAlarm.class,
              NonExclusiveDeviationAlarmTypeNode.class,
              NodeIds.NonExclusiveDeviationAlarmType),
          new AlarmSpec(
              TANK,
              "LevelTransmitterAlarm",
              InstrumentDiagnosticAlarm.class,
              InstrumentDiagnosticAlarmTypeNode.class,
              NodeIds.InstrumentDiagnosticAlarmType),
          new AlarmSpec(
              PLC,
              "CommunicationAlarm",
              SystemOffNormalAlarm.class,
              SystemOffNormalAlarmTypeNode.class,
              NodeIds.SystemOffNormalAlarmType),
          new AlarmSpec(
              PLC,
              "CpuDiagnosticAlarm",
              SystemDiagnosticAlarm.class,
              SystemDiagnosticAlarmTypeNode.class,
              NodeIds.SystemDiagnosticAlarmType));

  private static final List<AlarmSpec> ALL_ALARMS =
      Stream.concat(POWER_HOUSE_ALARMS.stream(), TANK_FARM_ALARMS.stream()).toList();

  @Test
  void buildsNotifierHierarchyWithoutServerShortcutWiring(@TempDir Path tempDir) throws Exception {
    withServer(
        tempDir,
        server -> {
          NodeId plantId = nodeId(AlarmNodesFragment.PLANT_ID);
          NodeId powerHouseId = nodeId(AlarmNodesFragment.POWER_HOUSE_ID);
          NodeId tankFarmId = nodeId(AlarmNodesFragment.TANK_FARM_ID);

          // Structure: the plant, both areas, and every equipment node exist.
          assertNotNull(managedNode(server, nodeId(AlarmNodesFragment.ROOT_ID)));
          for (NodeId areaId : List.of(plantId, powerHouseId, tankFarmId)) {
            UaNode area = managedNode(server, areaId);
            assertTrue(area instanceof UaObjectNode, () -> areaId + " is not a UaObjectNode");

            // Each area is subscribable for events.
            assertEquals(
                0x01,
                ((UaObjectNode) area).getEventNotifier().intValue() & 0x01,
                () -> "SubscribeToEvents not set on " + areaId);
          }

          for (String equipmentId : List.of(BOILER, PUMP, TANK, PLC)) {
            assertNotNull(managedNode(server, nodeId(equipmentId)));
          }

          // HasNotifier pairs exist in both directions: Server -> Plant -> area.
          assertReferencePair(server, NodeIds.Server, NodeIds.HasNotifier, plantId);
          assertReferencePair(server, plantId, NodeIds.HasNotifier, powerHouseId);
          assertReferencePair(server, plantId, NodeIds.HasNotifier, tankFarmId);

          // HasEventSource pairs exist in both directions: area -> equipment.
          assertReferencePair(server, powerHouseId, NodeIds.HasEventSource, nodeId(BOILER));
          assertReferencePair(server, powerHouseId, NodeIds.HasEventSource, nodeId(PUMP));
          assertReferencePair(server, tankFarmId, NodeIds.HasEventSource, nodeId(TANK));
          assertReferencePair(server, tankFarmId, NodeIds.HasEventSource, nodeId(PLC));

          // The regression guard: condition wiring must not have added a Server -> HasEventSource
          // shortcut to any equipment node, which would leak every event past the area scope.
          Set<NodeId> equipmentIds =
              Set.of(nodeId(BOILER), nodeId(PUMP), nodeId(TANK), nodeId(PLC));

          List<NodeId> shortcuts =
              server.getAddressSpaceManager().getManagedReferences(NodeIds.Server).stream()
                  .filter(Reference::isForward)
                  .filter(r -> NodeIds.HasEventSource.equals(r.getReferenceTypeId()))
                  .flatMap(r -> localNodeId(server, r).stream())
                  .filter(equipmentIds::contains)
                  .toList();

          assertTrue(
              shortcuts.isEmpty(),
              () -> "Server has direct HasEventSource shortcuts to equipment: " + shortcuts);
        });
  }

  @Test
  void registersEveryAlarmWithTheExpectedBehaviorAndType(@TempDir Path tempDir) throws Exception {
    withServer(
        tempDir,
        server -> {
          for (AlarmSpec spec : ALL_ALARMS) {
            NodeId conditionId = spec.conditionId();
            Condition condition = condition(server, conditionId);

            assertEquals(spec.conditionClass(), condition.getClass(), spec.conditionName());
            assertEquals(spec.nodeClass(), condition.getNode().getClass(), spec.conditionName());
            assertEquals(
                spec.eventType(), condition.getNode().getEventType(), spec.conditionName());
            assertSame(condition.getNode(), managedNode(server, conditionId));

            // The condition source is the equipment node, which is what puts its events in the
            // area's scope.
            assertEquals(
                nodeId(spec.equipmentId()),
                condition.getNode().getSourceNode(),
                spec.conditionName());
          }
        });
  }

  @Test
  void exposesThePlantToABrowsingClient(@TempDir Path tempDir) throws Exception {
    withServer(
        tempDir,
        (server, client) -> {
          // Walk the path a user takes in a client, which is the one way of reaching the plant that
          // resolving NodeIds directly never exercises.
          NodeId demoFolder = nodeId("Demo");

          assertBrowsableChild(client, demoFolder, "Alarms", nodeId(AlarmNodesFragment.ROOT_ID));
          assertBrowsableChild(
              client,
              nodeId(AlarmNodesFragment.ROOT_ID),
              "Plant",
              nodeId(AlarmNodesFragment.PLANT_ID));
          assertBrowsableChild(
              client,
              nodeId(AlarmNodesFragment.PLANT_ID),
              "PowerHouse",
              nodeId(AlarmNodesFragment.POWER_HOUSE_ID));
          assertBrowsableChild(
              client,
              nodeId(AlarmNodesFragment.PLANT_ID),
              "TankFarm",
              nodeId(AlarmNodesFragment.TANK_FARM_ID));
          assertBrowsableChild(
              client, nodeId(AlarmNodesFragment.POWER_HOUSE_ID), "B-100", nodeId(BOILER));
          assertBrowsableChild(
              client, nodeId(AlarmNodesFragment.POWER_HOUSE_ID), "P-101", nodeId(PUMP));
          assertBrowsableChild(
              client, nodeId(AlarmNodesFragment.TANK_FARM_ID), "TK-200", nodeId(TANK));
          assertBrowsableChild(
              client, nodeId(AlarmNodesFragment.TANK_FARM_ID), "PLC-01", nodeId(PLC));

          // A process variable and its alarm are both reachable from the equipment: the variable
          // hierarchically, the condition over HasCondition.
          assertBrowsableChild(client, nodeId(BOILER), "DrumLevel", nodeId(BOILER + ".DrumLevel"));

          BrowseResult conditions =
              client.browse(
                  new BrowseDescription(
                      nodeId(BOILER),
                      BrowseDirection.Forward,
                      NodeIds.HasCondition,
                      true,
                      uint(0),
                      uint(BrowseResultMask.All.getValue())));

          assertTrue(conditions.getStatusCode().isGood());
          assertTrue(
              references(conditions).stream()
                  .anyMatch(
                      r ->
                          r.getNodeId()
                              .toNodeId(server.getNamespaceTable())
                              .filter(nodeId(BOILER + ".DrumLevelAlarm")::equals)
                              .isPresent()),
              "the boiler exposes no HasCondition reference to its drum level alarm");
        });
  }

  @Test
  void scopesEventsToTheSubscribedArea(@TempDir Path tempDir) throws Exception {
    withServer(
        tempDir,
        (server, client) -> {
          Set<NodeId> powerHouseSources = Set.of(nodeId(BOILER), nodeId(PUMP));
          Set<NodeId> tankFarmSources = Set.of(nodeId(TANK), nodeId(PLC));

          OpcUaSubscription subscription = new OpcUaSubscription(client, 10.0);
          subscription.create();

          try {
            var powerHouseEvents = new CopyOnWriteArrayList<NodeId>();
            var tankFarmEvents = new CopyOnWriteArrayList<NodeId>();
            var serverEvents = new CopyOnWriteArrayList<NodeId>();

            OpcUaMonitoredItem powerHouseItem =
                eventItem(nodeId(AlarmNodesFragment.POWER_HOUSE_ID), powerHouseEvents::add);
            OpcUaMonitoredItem tankFarmItem =
                eventItem(nodeId(AlarmNodesFragment.TANK_FARM_ID), tankFarmEvents::add);
            OpcUaMonitoredItem serverItem = eventItem(NodeIds.Server, serverEvents::add);

            subscription.addMonitoredItems(List.of(powerHouseItem, tankFarmItem, serverItem));
            subscription.synchronizeMonitoredItems();

            assertTrue(powerHouseItem.getCreateResult().orElseThrow().isGood());
            assertTrue(tankFarmItem.getCreateResult().orElseThrow().isGood());
            assertTrue(serverItem.getCreateResult().orElseThrow().isGood());

            // Wait until both areas have produced events, then assert neither saw the other's.
            awaitTrue(
                () ->
                    powerHouseEvents.stream().anyMatch(powerHouseSources::contains)
                        && tankFarmEvents.stream().anyMatch(tankFarmSources::contains),
                "did not receive alarm events from both areas");

            assertTrue(
                powerHouseEvents.stream().allMatch(powerHouseSources::contains),
                () -> "PowerHouse item received out-of-scope events: " + powerHouseEvents);
            assertTrue(
                tankFarmEvents.stream().allMatch(tankFarmSources::contains),
                () -> "TankFarm item received out-of-scope events: " + tankFarmEvents);

            // The Server node sits above both areas, so it sees everything they see.
            awaitTrue(
                () ->
                    serverEvents.stream().anyMatch(powerHouseSources::contains)
                        && serverEvents.stream().anyMatch(tankFarmSources::contains),
                "Server-scoped item did not receive events from both areas");
          } finally {
            subscription.delete();
          }
        });
  }

  @Test
  void scopesConditionRefreshToTheSubscribedArea(@TempDir Path tempDir) throws Exception {
    withServer(
        tempDir,
        (server, client) -> {
          Set<NodeId> tankFarmSources = Set.of(nodeId(TANK), nodeId(PLC));

          OpcUaSubscription subscription = new OpcUaSubscription(client, 10.0);
          subscription.create();

          try {
            var events = new CopyOnWriteArrayList<RefreshEvent>();

            OpcUaMonitoredItem tankFarmItem =
                OpcUaMonitoredItem.newEventItem(
                    nodeId(AlarmNodesFragment.TANK_FARM_ID), eventTypeAndSourceFilter());
            tankFarmItem.setQueueSize(uint(1000));
            tankFarmItem.setEventValueListener(
                (_, fields) ->
                    events.add(
                        new RefreshEvent(
                            fields[0].value() instanceof NodeId eventType ? eventType : null,
                            fields[1].value() instanceof NodeId sourceNode ? sourceNode : null)));

            subscription.addMonitoredItems(List.of(tankFarmItem));
            subscription.synchronizeMonitoredItems();
            assertTrue(tankFarmItem.getCreateResult().orElseThrow().isGood());

            // Let the tank farm retain at least one condition worth replaying.
            awaitTrue(
                () -> retainedConditions(server, TANK_FARM_ALARMS) > 0,
                "no TankFarm condition became retained");

            UInteger subscriptionId = subscription.getSubscriptionId().orElseThrow();
            UInteger monitoredItemId = tankFarmItem.getMonitoredItemId().orElseThrow();

            events.clear();

            // ConditionRefresh2 is invoked on the ConditionType Object, which is where the server
            // installs the handler, not on the Server Object.
            CallMethodResult result =
                call(
                    client,
                    NodeIds.ConditionType,
                    NodeIds.ConditionType_ConditionRefresh2,
                    Variant.of(subscriptionId),
                    Variant.of(monitoredItemId));

            assertTrue(
                result.getStatusCode().isGood(),
                () -> "ConditionRefresh2 failed: " + result.getStatusCode());

            awaitTrue(
                () ->
                    events.stream()
                        .anyMatch(e -> NodeIds.RefreshEndEventType.equals(e.eventType())),
                "did not receive a RefreshEndEventType");

            // The replay is bracketed by RefreshStart/RefreshEnd...
            int start = indexOf(events, e -> NodeIds.RefreshStartEventType.equals(e.eventType()));
            int end = indexOf(events, e -> NodeIds.RefreshEndEventType.equals(e.eventType()));

            assertTrue(start >= 0, "did not receive a RefreshStartEventType");
            assertTrue(start < end, "RefreshStart must precede RefreshEnd");

            // ...and everything replayed between them belongs to the subscribed area.
            List<NodeId> replayed =
                events.subList(start + 1, end).stream().map(RefreshEvent::sourceNode).toList();

            assertFalse(replayed.isEmpty(), "ConditionRefresh2 replayed no retained conditions");
            assertTrue(
                // Null-tolerant on purpose: an event with no SourceNode is a failure to report,
                // not an NPE out of Set.of(...).contains(null).
                replayed.stream().allMatch(id -> id != null && tankFarmSources.contains(id)),
                () -> "ConditionRefresh2 replayed out-of-scope conditions: " + replayed);
          } finally {
            subscription.delete();
          }
        });
  }

  @Test
  void supportsOperatorAcknowledgeConfirmAndShelving(@TempDir Path tempDir) throws Exception {
    withServer(
        tempDir,
        (server, client) -> {
          // Force the pump to trip rather than waiting for the story loop to reach it.
          write(client, nodeId(PUMP + ".ForceTrip"), Variant.ofBoolean(true));

          NodeId tripId = nodeId(PUMP + ".OverloadTripAlarm");
          var trip = (TripAlarm) condition(server, tripId);

          awaitTrue(trip::isActive, "the pump overload trip never became active");

          // Freeze the pump's story loop. Its TRIPPED stage is only 60 simulated seconds, which at
          // the test tick rate is barely a second before the simulation clears the trip on the
          // operator's behalf and the EventIds acknowledged below go stale.
          write(client, nodeId(PUMP + ".SimulationEnabled"), Variant.ofBoolean(false));

          awaitTrue(() -> !trip.isAcked(), "the trip alarm never became unacknowledged");

          var tripNode = (AcknowledgeableConditionTypeNode) trip.getNode();
          ByteString eventId = trip.currentBranch().getLastEventId();

          CallMethodResult acknowledge =
              call(
                  client,
                  tripId,
                  tripNode.getAcknowledgeMethodNode().getNodeId(),
                  Variant.of(eventId),
                  Variant.of(LocalizedText.english("acked by test")));

          assertTrue(
              acknowledge.getStatusCode().isGood(),
              () -> "Acknowledge failed: " + acknowledge.getStatusCode());
          awaitTrue(trip::isAcked, "AckedState did not flip after Acknowledge");

          // Acknowledging a condition that supports Confirm leaves it unconfirmed.
          awaitTrue(() -> !trip.isConfirmed(), "the trip alarm never became unconfirmed");

          ByteString confirmEventId = trip.currentBranch().getLastEventId();

          CallMethodResult confirm =
              call(
                  client,
                  tripId,
                  tripNode.getConfirmMethodNode().getNodeId(),
                  Variant.of(confirmEventId),
                  Variant.of(LocalizedText.english("confirmed by test")));

          assertTrue(
              confirm.getStatusCode().isGood(), () -> "Confirm failed: " + confirm.getStatusCode());
          awaitTrue(trip::isConfirmed, "ConfirmedState did not flip after Confirm");

          // The interceptor recorded both operator actions against the calling user.
          var ackLog = (UaVariableNode) managedNode(server, nodeId(PUMP + ".AckLog"));
          String log = (String) ackLog.getValue().getValue().getValue();

          assertNotNull(log);
          assertTrue(
              log.contains("Acknowledge:"), () -> "AckLog missing Acknowledge entry: " + log);
          assertTrue(log.contains("Confirm:"), () -> "AckLog missing Confirm entry: " + log);

          // Shelving, on the alarm that opted into it.
          NodeId vibrationId = nodeId(PUMP + ".VibrationAlarm");
          var vibration = (AlarmCondition) condition(server, vibrationId);
          ShelvedStateMachineTypeNode shelvingState = vibration.getShelvingState();

          assertNotNull(shelvingState, "the vibration alarm has no ShelvingState");
          assertEquals("Unshelved", shelvingState.getCurrentState().text());

          CallMethodResult timedShelve =
              call(
                  client,
                  shelvingState.getNodeId(),
                  shelvingState.getTimedShelveMethodNode().getNodeId(),
                  Variant.ofDouble(Duration.ofMinutes(5).toMillis()));

          assertTrue(
              timedShelve.getStatusCode().isGood(),
              () -> "TimedShelve failed: " + timedShelve.getStatusCode());
          awaitTrue(
              () -> "TimedShelved".equals(shelvingState.getCurrentState().text()),
              "ShelvingState did not become TimedShelved");

          CallMethodResult unshelve =
              call(
                  client,
                  shelvingState.getNodeId(),
                  shelvingState.getUnshelveMethodNode().getNodeId());

          assertTrue(
              unshelve.getStatusCode().isGood(),
              () -> "Unshelve failed: " + unshelve.getStatusCode());
          awaitTrue(
              () -> "Unshelved".equals(shelvingState.getCurrentState().text()),
              "ShelvingState did not return to Unshelved");
        });
  }

  @Test
  void propagatesCommunicationQualityFromThePlcToTheTank(@TempDir Path tempDir) throws Exception {
    withServer(
        tempDir,
        (server, client) -> {
          List<Condition> tankConditions =
              TANK_FARM_ALARMS.stream()
                  .filter(spec -> spec.equipmentId().equals(TANK))
                  .map(spec -> condition(server, spec.conditionId()))
                  .toList();

          // Wait out any scripted outage in progress so we start from Good.
          awaitTrue(
              () -> tankConditions.stream().allMatch(c -> quality(c).isGood()),
              "the tank conditions never started from Good quality");

          write(client, nodeId(PLC + ".ForceCommsLoss"), Variant.ofBoolean(true));

          StatusCode communicationError = StatusCode.of(StatusCodes.Bad_CommunicationError);

          awaitTrue(
              () -> tankConditions.stream().allMatch(c -> communicationError.equals(quality(c))),
              "the tank conditions did not report Bad_CommunicationError");

          // The tank's process variables report the bad status too, rather than stale values.
          awaitTrue(
              () -> {
                var level = (UaVariableNode) managedNode(server, nodeId(TANK + ".Level"));
                return level.getValue().getStatusCode().isBad();
              },
              "the tank level did not report a Bad status during the outage");

          write(client, nodeId(PLC + ".ForceCommsLoss"), Variant.ofBoolean(false));

          awaitTrue(
              () -> tankConditions.stream().allMatch(c -> quality(c).isGood()),
              "the tank conditions did not recover to Good quality");
        });
  }

  @Test
  void omitsThePlantWhenDisabled(@TempDir Path tempDir) throws Exception {
    withServer(
        tempDir,
        ConfigFactory.parseMap(Map.of("address-space.alarms.enabled", false)),
        server -> {
          assertTrue(
              server
                  .getAddressSpaceManager()
                  .getManagedNode(nodeId(AlarmNodesFragment.ROOT_ID))
                  .isEmpty(),
              "Demo/Alarms exists even though the feature is disabled");

          for (AlarmSpec spec : ALL_ALARMS) {
            assertTrue(
                server.getConditionManager().findCondition(spec.conditionId()).isEmpty(),
                () ->
                    "condition registered even though the feature is disabled: "
                        + spec.conditionId());
          }
        });
  }

  // ---------------------------------------------------------------------------------------------
  // Harness
  // ---------------------------------------------------------------------------------------------

  @FunctionalInterface
  private interface ServerTest {
    void accept(OpcUaServer server) throws Exception;
  }

  @FunctionalInterface
  private interface ClientTest {
    void accept(OpcUaServer server, OpcUaClient client) throws Exception;
  }

  private static void withServer(Path tempDir, ServerTest test) throws Exception {
    withServer(tempDir, plantConfig(), test);
  }

  private static void withServer(Path tempDir, Config config, ServerTest test) throws Exception {
    OpcUaDemoServer demoServer = startServer(tempDir, config);
    try {
      test.accept(demoServer.getServer());
    } finally {
      demoServer.shutdown();
    }
  }

  private static void withServer(Path tempDir, ClientTest test) throws Exception {
    OpcUaDemoServer demoServer = startServer(tempDir, plantConfig());
    try {
      OpcUaClient client = OpcUaTestClient.create(demoServer.getServer());
      client.connect();
      try {
        test.accept(demoServer.getServer(), client);
      } finally {
        client.disconnect();
      }
    } finally {
      demoServer.shutdown();
    }
  }

  private static Config plantConfig() {
    return ConfigFactory.parseMap(
        Map.of(
            "address-space.alarms.enabled",
            true,
            "address-space.alarms.tick-interval",
            TICK_INTERVAL));
  }

  private static OpcUaDemoServer startServer(Path tempDir, Config config) throws Exception {
    OpcUaDemoServer demoServer =
        OpcUaTestServerBuilder.builder().withDataDir(tempDir).withConfig(config).build();

    demoServer.startup();

    return demoServer;
  }

  private static OpcUaMonitoredItem eventItem(NodeId notifierId, Consumer<NodeId> onSourceNode) {

    OpcUaMonitoredItem item =
        OpcUaMonitoredItem.newEventItem(notifierId, eventTypeAndSourceFilter());
    item.setQueueSize(uint(1000));
    item.setEventValueListener(
        (_, fields) -> {
          if (fields.length > 1 && fields[1].value() instanceof NodeId sourceNode) {
            onSourceNode.accept(sourceNode);
          }
        });

    return item;
  }

  /**
   * An event filter selecting EventType and SourceNode.
   *
   * <p>Both fields are declared on BaseEventType, which is also what the operands must name: select
   * operand validation resolves the browse path from the named type through forward hierarchical
   * References only, so naming a subtype like AlarmConditionType fails to resolve an inherited
   * field and yields a null value rather than an error.
   */
  private static EventFilter eventTypeAndSourceFilter() {
    return new EventFilter(
        new SimpleAttributeOperand[] {
          eventField(NodeIds.BaseEventType, "EventType"),
          eventField(NodeIds.BaseEventType, "SourceNode")
        },
        acceptAll());
  }

  /** An empty where clause, which accepts every event. A null one cannot be encoded. */
  private static ContentFilter acceptAll() {
    return new ContentFilter(new ContentFilterElement[] {});
  }

  private static SimpleAttributeOperand eventField(NodeId typeDefinitionId, String browseName) {
    return new SimpleAttributeOperand(
        typeDefinitionId,
        new QualifiedName[] {new QualifiedName(0, browseName)},
        AttributeId.Value.uid(),
        null);
  }

  /**
   * Assert that browsing {@code parentId} hierarchically finds {@code expectedId} by BrowseName.
   */
  private static void assertBrowsableChild(
      OpcUaClient client, NodeId parentId, String browseName, NodeId expectedId) throws Exception {

    BrowseResult result =
        client.browse(
            new BrowseDescription(
                parentId,
                BrowseDirection.Forward,
                NodeIds.HierarchicalReferences,
                true,
                uint(0),
                uint(BrowseResultMask.All.getValue())));

    assertTrue(
        result.getStatusCode().isGood(),
        () -> "browse of " + parentId + " failed: " + result.getStatusCode());

    List<ReferenceDescription> matches =
        references(result).stream()
            .filter(r -> browseName.equals(r.getBrowseName().name()))
            .toList();

    assertEquals(
        1,
        matches.size(),
        () ->
            "expected exactly one '%s' child of %s, browse returned: %s"
                .formatted(
                    browseName,
                    parentId,
                    references(result).stream().map(r -> r.getBrowseName().name()).toList()));

    assertEquals(
        expectedId,
        matches.getFirst().getNodeId().toNodeId(client.getNamespaceTable()).orElse(null),
        () -> "'%s' child of %s has an unexpected NodeId".formatted(browseName, parentId));
  }

  private static List<ReferenceDescription> references(BrowseResult result) {
    ReferenceDescription[] references = result.getReferences();
    return references != null ? List.of(references) : List.of();
  }

  private static CallMethodResult call(
      OpcUaClient client, NodeId objectId, NodeId methodId, Variant... arguments) throws Exception {

    return client.call(List.of(new CallMethodRequest(objectId, methodId, arguments)))
        .getResults()[0];
  }

  private static void write(OpcUaClient client, NodeId nodeId, Variant value) throws Exception {
    StatusCode status =
        client.write(
                List.of(
                    new WriteValue(
                        nodeId, AttributeId.Value.uid(), null, DataValue.valueOnly(value))))
            .getResults()[0];

    assertTrue(status.isGood(), () -> "write to " + nodeId + " failed: " + status);
  }

  private static long retainedConditions(OpcUaServer server, List<AlarmSpec> specs) {
    return specs.stream()
        .map(spec -> server.getConditionManager().findCondition(spec.conditionId()))
        .flatMap(Optional::stream)
        .filter(Condition::isRetained)
        .count();
  }

  private static StatusCode quality(Condition condition) {
    Object value = condition.getNode().getQuality();
    return value instanceof StatusCode statusCode ? statusCode : StatusCode.BAD;
  }

  private static void awaitTrue(BooleanSupplier condition, String message)
      throws InterruptedException {

    long deadline = System.nanoTime() + TIMEOUT.toNanos();
    while (System.nanoTime() < deadline) {
      if (condition.getAsBoolean()) {
        return;
      }
      Thread.sleep(20);
    }

    throw new AssertionError(message + " (waited " + TIMEOUT + ")");
  }

  private static <T> int indexOf(List<T> list, Predicate<T> predicate) {
    for (int i = 0; i < list.size(); i++) {
      if (predicate.test(list.get(i))) {
        return i;
      }
    }
    return -1;
  }

  private static void assertReferencePair(
      OpcUaServer server, NodeId sourceId, NodeId referenceTypeId, NodeId targetId) {

    boolean forward =
        server.getAddressSpaceManager().getManagedReferences(sourceId).stream()
            .filter(Reference::isForward)
            .filter(r -> referenceTypeId.equals(r.getReferenceTypeId()))
            .anyMatch(r -> localNodeId(server, r).filter(targetId::equals).isPresent());

    boolean inverse =
        server.getAddressSpaceManager().getManagedReferences(targetId).stream()
            .filter(Reference::isInverse)
            .filter(r -> referenceTypeId.equals(r.getReferenceTypeId()))
            .anyMatch(r -> localNodeId(server, r).filter(sourceId::equals).isPresent());

    assertTrue(
        forward,
        () -> "missing forward %s: %s -> %s".formatted(referenceTypeId, sourceId, targetId));
    assertTrue(
        inverse,
        () -> "missing inverse %s: %s <- %s".formatted(referenceTypeId, sourceId, targetId));
  }

  private static Optional<NodeId> localNodeId(OpcUaServer server, Reference reference) {
    return reference.getTargetNodeId().toNodeId(server.getNamespaceTable());
  }

  private static Condition condition(OpcUaServer server, NodeId conditionId) {
    return server
        .getConditionManager()
        .findCondition(conditionId)
        .orElseThrow(() -> new AssertionError("condition not registered: " + conditionId));
  }

  private static UaNode managedNode(OpcUaServer server, NodeId nodeId) {
    return server
        .getAddressSpaceManager()
        .getManagedNode(nodeId)
        .orElseThrow(() -> new AssertionError("node not found: " + nodeId));
  }

  private static NodeId nodeId(String identifier) {
    return new NodeId(NAMESPACE_INDEX, identifier);
  }

  private record AlarmSpec(
      String equipmentId,
      String conditionName,
      Class<? extends Condition> conditionClass,
      Class<? extends ConditionTypeNode> nodeClass,
      NodeId eventType) {

    NodeId conditionId() {
      return nodeId(equipmentId + "." + conditionName);
    }
  }

  private record RefreshEvent(NodeId eventType, NodeId sourceNode) {}
}

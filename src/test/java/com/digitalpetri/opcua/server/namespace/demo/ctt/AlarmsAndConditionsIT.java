package com.digitalpetri.opcua.server.namespace.demo.ctt;

import static org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.Unsigned.uint;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
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
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import org.eclipse.milo.opcua.sdk.client.OpcUaClient;
import org.eclipse.milo.opcua.sdk.client.subscriptions.OpcUaMonitoredItem;
import org.eclipse.milo.opcua.sdk.client.subscriptions.OpcUaSubscription;
import org.eclipse.milo.opcua.sdk.server.EventListener;
import org.eclipse.milo.opcua.sdk.server.OpcUaServer;
import org.eclipse.milo.opcua.sdk.server.conditions.Condition;
import org.eclipse.milo.opcua.sdk.server.conditions.ExclusiveLevelAlarm;
import org.eclipse.milo.opcua.sdk.server.conditions.ExclusiveLimitAlarm;
import org.eclipse.milo.opcua.sdk.server.conditions.NonExclusiveLevelAlarm;
import org.eclipse.milo.opcua.sdk.server.conditions.NonExclusiveLimitAlarm;
import org.eclipse.milo.opcua.sdk.server.conditions.OffNormalAlarm;
import org.eclipse.milo.opcua.sdk.server.model.objects.AcknowledgeableConditionTypeNode;
import org.eclipse.milo.opcua.sdk.server.model.objects.AlarmConditionTypeNode;
import org.eclipse.milo.opcua.sdk.server.model.objects.ConditionTypeNode;
import org.eclipse.milo.opcua.sdk.server.model.objects.ExclusiveLevelAlarmTypeNode;
import org.eclipse.milo.opcua.sdk.server.model.objects.ExclusiveLimitAlarmTypeNode;
import org.eclipse.milo.opcua.sdk.server.model.objects.NonExclusiveLevelAlarmTypeNode;
import org.eclipse.milo.opcua.sdk.server.model.objects.NonExclusiveLimitAlarmTypeNode;
import org.eclipse.milo.opcua.sdk.server.model.objects.OffNormalAlarmTypeNode;
import org.eclipse.milo.opcua.sdk.server.nodes.UaNode;
import org.eclipse.milo.opcua.sdk.server.nodes.UaVariableNode;
import org.eclipse.milo.opcua.stack.core.AttributeId;
import org.eclipse.milo.opcua.stack.core.NodeIds;
import org.eclipse.milo.opcua.stack.core.encoding.DefaultEncodingContext;
import org.eclipse.milo.opcua.stack.core.types.builtin.DateTime;
import org.eclipse.milo.opcua.stack.core.types.builtin.ExtensionObject;
import org.eclipse.milo.opcua.stack.core.types.builtin.LocalizedText;
import org.eclipse.milo.opcua.stack.core.types.builtin.NodeId;
import org.eclipse.milo.opcua.stack.core.types.builtin.QualifiedName;
import org.eclipse.milo.opcua.stack.core.types.builtin.Variant;
import org.eclipse.milo.opcua.stack.core.types.enumerated.FilterOperator;
import org.eclipse.milo.opcua.stack.core.types.structured.CallMethodRequest;
import org.eclipse.milo.opcua.stack.core.types.structured.CallMethodResult;
import org.eclipse.milo.opcua.stack.core.types.structured.ContentFilter;
import org.eclipse.milo.opcua.stack.core.types.structured.ContentFilterElement;
import org.eclipse.milo.opcua.stack.core.types.structured.EventFilter;
import org.eclipse.milo.opcua.stack.core.types.structured.LiteralOperand;
import org.eclipse.milo.opcua.stack.core.types.structured.SimpleAttributeOperand;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AlarmsAndConditionsIT {

  private static final int NAMESPACE_INDEX = 2;
  private static final Duration EVENT_TIMEOUT = Duration.ofSeconds(3);
  private static final long MAX_TIMESTAMP_DELTA_100NS = Duration.ofMillis(25).toNanos() / 100;

  /** A dwell short enough that every fixture input transitions twenty times a second. */
  private static final Duration FAST_DWELL_TIME = Duration.ofMillis(50);

  /** A sampling interval an order of magnitude slower than {@link #FAST_DWELL_TIME}. */
  private static final double SLOW_SAMPLING_INTERVAL = 500.0;

  /** How long notifications are counted for, once the subscription has settled. */
  private static final Duration SAMPLE_WINDOW = Duration.ofSeconds(2);

  /** Long enough to disable every retained fixture before the trajectory advances. */
  private static final Duration ENABLE_HOLD_DWELL_TIME = Duration.ofMillis(500);

  /**
   * The fewest events a {@link #SAMPLE_WINDOW} of {@link #FAST_DWELL_TIME} dwells has to produce
   * before a low value count over the same window says anything about sampling. The fixture should
   * manage forty.
   */
  private static final int MIN_EVENTS_IN_WINDOW = 20;

  private static final List<FixtureSpec> FIXTURES =
      List.of(
          new FixtureSpec(
              "Discrete",
              AlarmsAndConditionsFragment.DISCRETE_INPUT_ID,
              AlarmsAndConditionsFragment.DISCRETE_CONDITION_ID,
              OffNormalAlarm.class,
              OffNormalAlarmTypeNode.class,
              NodeIds.OffNormalAlarmType),
          new FixtureSpec(
              "ExclusiveLimit",
              AlarmsAndConditionsFragment.EXCLUSIVE_LIMIT_INPUT_ID,
              AlarmsAndConditionsFragment.EXCLUSIVE_LIMIT_CONDITION_ID,
              ExclusiveLimitAlarm.class,
              ExclusiveLimitAlarmTypeNode.class,
              NodeIds.ExclusiveLimitAlarmType),
          new FixtureSpec(
              "ExclusiveLevel",
              AlarmsAndConditionsFragment.EXCLUSIVE_LEVEL_INPUT_ID,
              AlarmsAndConditionsFragment.EXCLUSIVE_LEVEL_CONDITION_ID,
              ExclusiveLevelAlarm.class,
              ExclusiveLevelAlarmTypeNode.class,
              NodeIds.ExclusiveLevelAlarmType),
          new FixtureSpec(
              "NonExclusiveLimit",
              AlarmsAndConditionsFragment.NON_EXCLUSIVE_LIMIT_INPUT_ID,
              AlarmsAndConditionsFragment.NON_EXCLUSIVE_LIMIT_CONDITION_ID,
              NonExclusiveLimitAlarm.class,
              NonExclusiveLimitAlarmTypeNode.class,
              NodeIds.NonExclusiveLimitAlarmType),
          new FixtureSpec(
              "NonExclusiveLevel",
              AlarmsAndConditionsFragment.NON_EXCLUSIVE_LEVEL_INPUT_ID,
              AlarmsAndConditionsFragment.NON_EXCLUSIVE_LEVEL_CONDITION_ID,
              NonExclusiveLevelAlarm.class,
              NonExclusiveLevelAlarmTypeNode.class,
              NodeIds.NonExclusiveLevelAlarmType));

  @Test
  void registersStableFixturesAndEmitsTimestampCorrelatedEvents(@TempDir Path tempDir)
      throws Exception {

    Config config =
        ConfigFactory.parseMap(
            Map.of(
                "address-space.ctt.enabled", true,
                "address-space.ctt.alarms-and-conditions.enabled", true,
                "address-space.ctt.alarms-and-conditions.dwell-time", "50 ms"));

    OpcUaDemoServer demoServer =
        OpcUaTestServerBuilder.builder().withDataDir(tempDir).withConfig(config).build();

    try {
      demoServer.startup();
      OpcUaServer server = demoServer.getServer();

      assertNotNull(managedNode(server, nodeId(AlarmsAndConditionsFragment.ROOT_ID)));

      Map<NodeId, UaVariableNode> inputsByCondition = new ConcurrentHashMap<>();

      for (FixtureSpec fixture : FIXTURES) {
        NodeId sourceId = nodeId(AlarmsAndConditionsFragment.ROOT_ID + "." + fixture.name());
        NodeId inputId = nodeId(fixture.inputIdentifier());
        NodeId conditionId = nodeId(fixture.conditionIdentifier());

        assertNotNull(managedNode(server, sourceId));
        UaVariableNode input = (UaVariableNode) managedNode(server, inputId);
        Condition condition =
            server
                .getConditionManager()
                .findCondition(conditionId)
                .orElseThrow(() -> new AssertionError("condition not registered: " + conditionId));

        assertEquals(fixture.conditionClass(), condition.getClass());
        assertEquals(fixture.nodeClass(), condition.getNode().getClass());
        assertEquals(fixture.eventType(), condition.getNode().getEventType());
        assertEquals(NodeIds.TestingConditionClassType, condition.getNode().getConditionClassId());
        assertEquals(
            new LocalizedText("", "TestingConditionClassType"),
            condition.getNode().getConditionClassName());
        assertSame(condition.getNode(), managedNode(server, conditionId));
        assertEquals(inputId, fixtureInputNode(condition));
        assertTrue(input.getValue().getStatusCode().isGood());
        assertNotNull(input.getValue().getValue().getValue());

        AcknowledgeableConditionTypeNode conditionNode =
            (AcknowledgeableConditionTypeNode) condition.getNode();
        assertNull(conditionNode.getConfirmMethodNode());
        assertNull(conditionNode.getConfirmedStateNode());
        assertTrue(
            server
                .getConditionManager()
                .findMethodNode(conditionId, NodeIds.AcknowledgeableConditionType_Confirm)
                .isEmpty());

        inputsByCondition.put(conditionId, input);
      }

      assertEmittedEvents(server, inputsByCondition);
    } finally {
      demoServer.shutdown();
    }
  }

  @Test
  void disabledFixturesHoldTheirActiveInputAndEmitWhenReenabled(@TempDir Path tempDir)
      throws Exception {

    Config config =
        ConfigFactory.parseMap(
            Map.of(
                "address-space.ctt.enabled",
                true,
                "address-space.ctt.alarms-and-conditions.enabled",
                true,
                "address-space.ctt.alarms-and-conditions.dwell-time",
                ENABLE_HOLD_DWELL_TIME.toMillis() + " ms"));

    OpcUaDemoServer demoServer =
        OpcUaTestServerBuilder.builder().withDataDir(tempDir).withConfig(config).build();

    try {
      demoServer.startup();
      OpcUaServer server = demoServer.getServer();
      Map<NodeId, Condition> conditions = new ConcurrentHashMap<>();
      Map<NodeId, UaVariableNode> inputs = new ConcurrentHashMap<>();

      for (FixtureSpec fixture : FIXTURES) {
        NodeId conditionId = nodeId(fixture.conditionIdentifier());
        conditions.put(
            conditionId,
            server
                .getConditionManager()
                .findCondition(conditionId)
                .orElseThrow(() -> new AssertionError("condition not registered: " + conditionId)));
        inputs.put(
            conditionId, (UaVariableNode) managedNode(server, nodeId(fixture.inputIdentifier())));
      }

      awaitTrue(
          () -> conditions.values().stream().allMatch(Condition::isRetained),
          "fixtures never entered their shared retained state");

      OpcUaClient client = OpcUaTestClient.create(server);
      try {
        client.connect();

        for (NodeId conditionId : conditions.keySet()) {
          CallMethodResult result = call(client, conditionId, NodeIds.ConditionType_Disable);
          assertTrue(
              result.getStatusCode().isGood(),
              () -> "Disable failed for " + conditionId + ": " + result.getStatusCode());
        }

        Map<NodeId, Object> heldValues = new ConcurrentHashMap<>();
        inputs.forEach(
            (conditionId, input) ->
                heldValues.put(conditionId, input.getValue().getValue().getValue()));

        Thread.sleep(ENABLE_HOLD_DWELL_TIME.multipliedBy(3).toMillis());

        for (NodeId conditionId : conditions.keySet()) {
          Condition condition = conditions.get(conditionId);
          assertFalse(condition.isEnabled(), "fixture unexpectedly enabled: " + conditionId);
          assertFalse(condition.isRetained(), "disabled fixture retained: " + conditionId);
          assertEquals(
              heldValues.get(conditionId),
              inputs.get(conditionId).getValue().getValue().getValue(),
              "disabled fixture input advanced: " + conditionId);
        }

        Set<NodeId> enabledEvents = ConcurrentHashMap.newKeySet();
        CountDownLatch enabledEventLatch = new CountDownLatch(FIXTURES.size());
        EventListener listener =
            event -> {
              NodeId conditionId = event.getNodeId();
              Condition condition = conditions.get(conditionId);
              if (condition != null
                  && condition.isEnabled()
                  && condition.isRetained()
                  && enabledEvents.add(conditionId)) {
                enabledEventLatch.countDown();
              }
            };

        server.getEventNotifier().register(listener);
        try {
          for (NodeId conditionId : conditions.keySet()) {
            CallMethodResult result = call(client, conditionId, NodeIds.ConditionType_Enable);
            assertTrue(
                result.getStatusCode().isGood(),
                () -> "Enable failed for " + conditionId + ": " + result.getStatusCode());
          }

          assertTrue(
              enabledEventLatch.await(EVENT_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS),
              () ->
                  "missing retained Enable events for "
                      + missingConditionIds(conditions, enabledEvents));
        } finally {
          server.getEventNotifier().unregister(listener);
        }
      } finally {
        client.disconnect();
      }
    } finally {
      demoServer.shutdown();
    }
  }

  /**
   * A client is never queued values faster than the sampling interval it asked for, however fast
   * the fixture underneath is changing them.
   *
   * <p>The event stream is what makes the value count mean anything: it witnesses that the input
   * really was transitioning throughout the window, so a low value count is decimation by the
   * sampling interval rather than a stalled fixture.
   *
   * <p>This once failed. The fixture pushed every value it produced straight into matching data
   * items, so that the value explaining an alarm always preceded the event reporting it — a
   * correlation the server is not allowed to buy by sampling ahead of what the client asked for.
   * Correlating the two by timestamp instead costs the client nothing and is still covered, by
   * {@link #registersStableFixturesAndEmitsTimestampCorrelatedEvents(Path)}.
   */
  @Test
  void doesNotQueueValuesFasterThanTheRevisedSamplingInterval(@TempDir Path tempDir)
      throws Exception {

    Config config =
        ConfigFactory.parseMap(
            Map.of(
                "address-space.ctt.enabled",
                true,
                "address-space.ctt.alarms-and-conditions.enabled",
                true,
                "address-space.ctt.alarms-and-conditions.dwell-time",
                FAST_DWELL_TIME.toMillis() + " ms"));

    OpcUaDemoServer demoServer =
        OpcUaTestServerBuilder.builder().withDataDir(tempDir).withConfig(config).build();

    try {
      demoServer.startup();

      OpcUaClient client = OpcUaTestClient.create(demoServer.getServer());

      try {
        client.connect();

        OpcUaSubscription subscription = new OpcUaSubscription(client, 50.0);

        try {
          subscription.create();

          var valueNotifications = new AtomicInteger();
          var eventNotifications = new AtomicInteger();
          var initialValueReceived = new CountDownLatch(1);

          OpcUaMonitoredItem inputItem =
              OpcUaMonitoredItem.newDataItem(
                  nodeId(AlarmsAndConditionsFragment.EXCLUSIVE_LIMIT_INPUT_ID));
          inputItem.setSamplingInterval(SLOW_SAMPLING_INTERVAL);
          inputItem.setQueueSize(uint(1000));
          inputItem.setDataValueListener(
              (_, _) -> {
                valueNotifications.incrementAndGet();
                initialValueReceived.countDown();
              });

          OpcUaMonitoredItem eventItem =
              OpcUaMonitoredItem.newEventItem(NodeIds.Server, exclusiveLimitEventFilter());
          eventItem.setQueueSize(uint(1000));
          eventItem.setEventValueListener((_, _) -> eventNotifications.incrementAndGet());

          subscription.addMonitoredItems(List.of(inputItem, eventItem));
          subscription.synchronizeMonitoredItems();

          assertTrue(inputItem.getCreateResult().orElseThrow().isGood());
          assertTrue(eventItem.getCreateResult().orElseThrow().isGood());
          assertTrue(
              initialValueReceived.await(EVENT_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS),
              "did not receive the initial ExclusiveLimit input DataValue");

          // The revised interval rather than the requested one: a server may sample slower than a
          // client asked, and is then held to the rate it actually agreed to.
          double revisedSamplingInterval = inputItem.getRevisedSamplingInterval().orElseThrow();
          assertTrue(
              revisedSamplingInterval >= SLOW_SAMPLING_INTERVAL,
              () -> "sampling interval revised faster than requested: " + revisedSamplingInterval);

          // Counted from here, so the initial value delivered on item creation is excluded.
          valueNotifications.set(0);
          eventNotifications.set(0);
          Thread.sleep(SAMPLE_WINDOW.toMillis());

          int values = valueNotifications.get();
          int events = eventNotifications.get();

          assertTrue(
              events >= MIN_EVENTS_IN_WINDOW,
              () ->
                  "the fixture produced too few events for the value count to mean much: "
                      + events);

          // Twice the sample count the window holds, plus the samples straddling either end of it:
          // enough slack for scheduling jitter, not enough for a second value per interval.
          int maxValues =
              (int) Math.ceil(SAMPLE_WINDOW.toMillis() / revisedSamplingInterval) * 2 + 2;

          assertTrue(
              values <= maxValues,
              () ->
                  "queued %d values in %s at a %.0f ms sampling interval, expected at most %d (%d events fired in the same window)"
                      .formatted(
                          values, SAMPLE_WINDOW, revisedSamplingInterval, maxValues, events));
        } finally {
          subscription.delete();
        }
      } finally {
        client.disconnect();
      }
    } finally {
      demoServer.shutdown();
    }
  }

  @Test
  void omitsFixturesWhenChildFeatureIsDisabled(@TempDir Path tempDir) throws Exception {
    Config config =
        ConfigFactory.parseMap(
            Map.of(
                "address-space.ctt.enabled", true,
                "address-space.ctt.alarms-and-conditions.enabled", false));

    OpcUaDemoServer demoServer =
        OpcUaTestServerBuilder.builder().withDataDir(tempDir).withConfig(config).build();

    try {
      demoServer.startup();
      OpcUaServer server = demoServer.getServer();

      assertFalse(
          server
              .getAddressSpaceManager()
              .getManagedNode(nodeId(AlarmsAndConditionsFragment.ROOT_ID))
              .isPresent());

      for (FixtureSpec fixture : FIXTURES) {
        assertTrue(
            server
                .getConditionManager()
                .findCondition(nodeId(fixture.conditionIdentifier()))
                .isEmpty());
      }
    } finally {
      demoServer.shutdown();
    }
  }

  private static void assertEmittedEvents(
      OpcUaServer server, Map<NodeId, UaVariableNode> inputsByCondition)
      throws InterruptedException {

    var snapshots = new ConcurrentHashMap<NodeId, EventSnapshot>();
    var receivedAllConditions = new CountDownLatch(FIXTURES.size());

    EventListener listener =
        event -> {
          UaVariableNode input = inputsByCondition.get(event.getNodeId());
          if (input == null) {
            return;
          }

          EventSnapshot snapshot =
              new EventSnapshot(
                  event.getSourceNode(), event.getTime(), input.getValue().getSourceTime());

          if (snapshots.putIfAbsent(event.getNodeId(), snapshot) == null) {
            receivedAllConditions.countDown();
          }
        };

    server.getEventNotifier().register(listener);
    try {
      assertTrue(
          receivedAllConditions.await(EVENT_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS),
          () -> "did not receive events from conditions: " + missingConditions(snapshots));
    } finally {
      server.getEventNotifier().unregister(listener);
    }

    for (FixtureSpec fixture : FIXTURES) {
      NodeId conditionId = nodeId(fixture.conditionIdentifier());
      EventSnapshot snapshot = snapshots.get(conditionId);
      assertNotNull(snapshot, "missing event for " + conditionId);
      assertEquals(
          nodeId(AlarmsAndConditionsFragment.ROOT_ID + "." + fixture.name()),
          snapshot.sourceNode());
      assertNotNull(snapshot.eventTime());
      assertNotNull(snapshot.sourceTime());

      long timestampDelta = snapshot.eventTime().getUtcTime() - snapshot.sourceTime().getUtcTime();
      assertTrue(timestampDelta >= 0, "source timestamp must not follow event Time");
      assertTrue(
          timestampDelta <= MAX_TIMESTAMP_DELTA_100NS,
          () -> "source timestamp differs from event Time by more than 25 ms: " + timestampDelta);
    }
  }

  private static List<NodeId> missingConditions(Map<NodeId, EventSnapshot> snapshots) {
    return FIXTURES.stream()
        .map(fixture -> nodeId(fixture.conditionIdentifier()))
        .filter(conditionId -> !snapshots.containsKey(conditionId))
        .toList();
  }

  private static List<NodeId> missingConditionIds(
      Map<NodeId, Condition> conditions, Set<NodeId> receivedConditionIds) {

    return conditions.keySet().stream()
        .filter(conditionId -> !receivedConditionIds.contains(conditionId))
        .toList();
  }

  private static CallMethodResult call(
      OpcUaClient client, NodeId objectId, NodeId methodId, Variant... arguments) throws Exception {

    return client.call(List.of(new CallMethodRequest(objectId, methodId, arguments)))
        .getResults()[0];
  }

  private static void awaitTrue(BooleanSupplier condition, String message)
      throws InterruptedException {

    long deadline = System.nanoTime() + EVENT_TIMEOUT.toNanos();
    while (System.nanoTime() < deadline) {
      if (condition.getAsBoolean()) {
        return;
      }
      Thread.sleep(20);
    }

    throw new AssertionError(message + " (waited " + EVENT_TIMEOUT + ")");
  }

  private static EventFilter exclusiveLimitEventFilter() {
    SimpleAttributeOperand eventTypeField = eventField(NodeIds.BaseEventType, "EventType");
    SimpleAttributeOperand eventTimeField = eventField(NodeIds.BaseEventType, "Time");
    ContentFilter whereClause =
        new ContentFilter(
            new ContentFilterElement[] {
              new ContentFilterElement(
                  FilterOperator.Equals,
                  new ExtensionObject[] {
                    ExtensionObject.encode(DefaultEncodingContext.INSTANCE, eventTypeField),
                    ExtensionObject.encode(
                        DefaultEncodingContext.INSTANCE,
                        new LiteralOperand(new Variant(NodeIds.ExclusiveLimitAlarmType)))
                  })
            });

    return new EventFilter(new SimpleAttributeOperand[] {eventTimeField}, whereClause);
  }

  private static SimpleAttributeOperand eventField(NodeId typeDefinitionId, String browseName) {
    return new SimpleAttributeOperand(
        typeDefinitionId,
        new QualifiedName[] {new QualifiedName(0, browseName)},
        AttributeId.Value.uid(),
        null);
  }

  private static NodeId fixtureInputNode(Condition condition) {
    return ((AlarmConditionTypeNode) condition.getNode()).getInputNode();
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

  private record FixtureSpec(
      String name,
      String inputIdentifier,
      String conditionIdentifier,
      Class<? extends Condition> conditionClass,
      Class<? extends ConditionTypeNode> nodeClass,
      NodeId eventType) {}

  private record EventSnapshot(NodeId sourceNode, DateTime eventTime, DateTime sourceTime) {}
}

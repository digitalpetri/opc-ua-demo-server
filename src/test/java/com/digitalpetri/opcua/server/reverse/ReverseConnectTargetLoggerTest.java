package com.digitalpetri.opcua.server.reverse;

import static org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.Unsigned.uint;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.eclipse.milo.opcua.sdk.server.reverse.ReverseConnectAttemptEvent;
import org.eclipse.milo.opcua.sdk.server.reverse.ReverseConnectAttemptState;
import org.eclipse.milo.opcua.sdk.server.reverse.ReverseConnectTargetSnapshot;
import org.eclipse.milo.opcua.stack.core.StatusCodes;
import org.eclipse.milo.opcua.stack.core.types.builtin.StatusCode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

class ReverseConnectTargetLoggerTest {

  private static final UUID TARGET_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
  private static final String CLIENT_LISTENER_URL = "opc.tcp://client.example.com:48060";
  private static final String ENDPOINT_URL = "opc.tcp://localhost:4840/milo";

  private final ReverseConnectTargetLogger listener = new ReverseConnectTargetLogger();

  private ch.qos.logback.classic.Logger logbackLogger;
  private Level originalLevel;
  private ListAppender<ILoggingEvent> appender;

  @BeforeEach
  void attachListAppender() {
    logbackLogger =
        (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(ReverseConnectTargetLogger.class);
    originalLevel = logbackLogger.getLevel();
    logbackLogger.setLevel(Level.DEBUG);

    appender = new ListAppender<>();
    appender.start();
    logbackLogger.addAppender(appender);
  }

  @AfterEach
  void detachListAppender() {
    logbackLogger.detachAppender(appender);
    appender.stop();
    logbackLogger.setLevel(originalLevel);
  }

  // region onTargetAdded

  @Test
  void onTargetAddedLogsEnabledTarget() {
    listener.onTargetAdded(snapshot(true, null, 0, null, null));

    ILoggingEvent event = singleEvent();
    assertEquals(Level.INFO, event.getLevel());
    assertTrue(event.getFormattedMessage().contains("Reverse Connect target registered"));
    assertTrue(event.getFormattedMessage().contains(TARGET_ID.toString()));
    assertTrue(event.getFormattedMessage().contains(CLIENT_LISTENER_URL));
    assertTrue(event.getFormattedMessage().contains(ENDPOINT_URL));
    assertTrue(event.getFormattedMessage().contains("enabled"));
    assertFalse(event.getFormattedMessage().contains("disabled"));
  }

  @Test
  void onTargetAddedLogsDisabledTarget() {
    listener.onTargetAdded(snapshot(false, null, 0, null, null));

    ILoggingEvent event = singleEvent();
    assertEquals(Level.INFO, event.getLevel());
    assertTrue(
        event.getFormattedMessage().contains("disabled (no outbound attempts will be scheduled)"));
  }

  // endregion

  // region onAttemptEvent

  @Test
  void onAttemptEventConnectingLogsAttemptingAtInfo() {
    listener.onAttemptEvent(attemptEvent(ReverseConnectAttemptState.CONNECTING, null, null, null));

    ILoggingEvent event = singleEvent();
    assertEquals(Level.INFO, event.getLevel());
    assertTrue(event.getFormattedMessage().contains("attempting connection"));
    assertTrue(event.getFormattedMessage().contains(TARGET_ID.toString()));
    assertTrue(event.getFormattedMessage().contains("#1"));
  }

  @Test
  void onAttemptEventHandoffLogsConnectedAtInfo() {
    listener.onAttemptEvent(attemptEvent(ReverseConnectAttemptState.HANDOFF, null, null, null));

    ILoggingEvent event = singleEvent();
    assertEquals(Level.INFO, event.getLevel());
    assertTrue(
        event
            .getFormattedMessage()
            .contains("connected: reverse channel handed off to server UASC path"));
  }

  @Test
  void onAttemptEventProgressStatesLogAtDebug() {
    List<ReverseConnectAttemptState> progressStates =
        List.of(
            ReverseConnectAttemptState.CONNECTED,
            ReverseConnectAttemptState.REVERSE_HELLO_SENT,
            ReverseConnectAttemptState.HELLO_HANDLER_INSTALLED);

    for (ReverseConnectAttemptState state : progressStates) {
      listener.onAttemptEvent(attemptEvent(state, null, null, null));
    }

    assertEquals(progressStates.size(), appender.list.size());

    for (int i = 0; i < progressStates.size(); i++) {
      ILoggingEvent event = appender.list.get(i);
      assertEquals(Level.DEBUG, event.getLevel());
      assertTrue(event.getFormattedMessage().contains(progressStates.get(i).name()));
    }
  }

  @Test
  void onAttemptEventTerminalFailureStatesLogAtWarnWithStatusCodeAndMessage() {
    List<ReverseConnectAttemptState> failureStates =
        List.of(
            ReverseConnectAttemptState.CLIENT_ERROR,
            ReverseConnectAttemptState.FAILED,
            ReverseConnectAttemptState.CANCELLED,
            ReverseConnectAttemptState.CLOSED);

    var statusCode = new StatusCode(StatusCodes.Bad_ConnectionRejected);

    for (ReverseConnectAttemptState state : failureStates) {
      listener.onAttemptEvent(attemptEvent(state, statusCode, null, "connection refused"));
    }

    assertEquals(failureStates.size(), appender.list.size());

    for (int i = 0; i < failureStates.size(); i++) {
      ILoggingEvent event = appender.list.get(i);
      assertEquals(Level.WARN, event.getLevel());
      assertTrue(event.getFormattedMessage().contains(failureStates.get(i).name()));
      assertTrue(event.getFormattedMessage().contains(statusCode.toString()));
      assertTrue(event.getFormattedMessage().contains("connection refused"));
    }
  }

  @Test
  void onAttemptEventWithNullStatusCodeExceptionAndMessageDoesNotThrow() {
    for (ReverseConnectAttemptState state : ReverseConnectAttemptState.values()) {
      assertDoesNotThrow(() -> listener.onAttemptEvent(attemptEvent(state, null, null, null)));
    }
  }

  @Test
  void onAttemptEventWithExceptionButNullStatusCodeDoesNotThrow() {
    var exception = new RuntimeException("boom");

    for (ReverseConnectAttemptState state : ReverseConnectAttemptState.values()) {
      assertDoesNotThrow(() -> listener.onAttemptEvent(attemptEvent(state, null, exception, null)));
    }
  }

  // endregion

  // region onTargetUpdated

  @Test
  void onTargetUpdatedLogsRetryWithLastStatusCode() {
    var nextAttemptTime = Instant.parse("2026-07-21T12:00:00Z");
    var lastStatusCode = new StatusCode(StatusCodes.Bad_Timeout);

    listener.onTargetUpdated(snapshot(true, nextAttemptTime, 0, lastStatusCode, null));

    ILoggingEvent event = singleEvent();
    assertEquals(Level.INFO, event.getLevel());
    assertTrue(event.getFormattedMessage().contains("retrying at " + nextAttemptTime));
    assertTrue(event.getFormattedMessage().contains(lastStatusCode.toString()));
  }

  @Test
  void onTargetUpdatedLogsRetryWithLastErrorOnly() {
    var nextAttemptTime = Instant.parse("2026-07-21T12:00:00Z");
    var lastError = new RuntimeException("connect timed out");

    listener.onTargetUpdated(snapshot(true, nextAttemptTime, 0, null, lastError));

    ILoggingEvent event = singleEvent();
    assertEquals(Level.INFO, event.getLevel());
    assertTrue(event.getFormattedMessage().contains("retrying at " + nextAttemptTime));
    assertTrue(event.getFormattedMessage().contains("connect timed out"));
  }

  @Test
  void onTargetUpdatedWithoutNextAttemptTimeLogsNoRetry() {
    var lastStatusCode = new StatusCode(StatusCodes.Bad_Timeout);

    listener.onTargetUpdated(snapshot(true, null, 0, lastStatusCode, null));

    assertTrue(appender.list.isEmpty());
  }

  @Test
  void onTargetUpdatedWithNextAttemptTimeButNoFailureLogsNoRetry() {
    // Initial scheduling: an attempt is scheduled but nothing has failed yet.
    listener.onTargetUpdated(snapshot(true, Instant.parse("2026-07-21T12:00:00Z"), 0, null, null));

    assertTrue(appender.list.isEmpty());
  }

  @Test
  void onTargetUpdatedLogsActiveChannelCountAtDebug() {
    listener.onTargetUpdated(snapshot(true, null, 2, null, null));

    ILoggingEvent event = singleEvent();
    assertEquals(Level.DEBUG, event.getLevel());
    assertTrue(event.getFormattedMessage().contains("2 active reverse channel(s)"));
  }

  @Test
  void onTargetUpdatedWithAllNullableFieldsAbsentDoesNotThrowOrLog() {
    assertDoesNotThrow(() -> listener.onTargetUpdated(snapshot(true, null, 0, null, null)));

    assertTrue(appender.list.isEmpty());
  }

  // endregion

  private ILoggingEvent singleEvent() {
    assertEquals(1, appender.list.size());
    return appender.list.get(0);
  }

  private static ReverseConnectTargetSnapshot snapshot(
      boolean enabled,
      Instant nextAttemptTime,
      int activeChannelCount,
      StatusCode lastStatusCode,
      Throwable lastError) {

    return new ReverseConnectTargetSnapshot(
        TARGET_ID,
        CLIENT_LISTENER_URL,
        ENDPOINT_URL,
        uint(30_000),
        uint(5_000),
        enabled,
        false,
        nextAttemptTime,
        null,
        null,
        activeChannelCount,
        lastStatusCode,
        lastError);
  }

  private static ReverseConnectAttemptEvent attemptEvent(
      ReverseConnectAttemptState state,
      StatusCode statusCode,
      Throwable exception,
      String message) {

    return new ReverseConnectAttemptEvent(
        TARGET_ID,
        1L,
        state,
        Instant.parse("2026-07-21T12:00:00Z"),
        statusCode,
        exception,
        message);
  }
}

package com.digitalpetri.opcua.server.reverse;

import java.time.Instant;
import org.eclipse.milo.opcua.sdk.server.reverse.ReverseConnectAttemptEvent;
import org.eclipse.milo.opcua.sdk.server.reverse.ReverseConnectTargetListener;
import org.eclipse.milo.opcua.sdk.server.reverse.ReverseConnectTargetSnapshot;
import org.eclipse.milo.opcua.stack.core.types.builtin.StatusCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A {@link ReverseConnectTargetListener} that logs each configured Reverse Connect target's
 * lifecycle: registered (enabled or disabled), attempting connection, connected, and retrying.
 *
 * <p>Callbacks are dispatched asynchronously on the server executor, so this listener only logs and
 * never blocks.
 */
public final class ReverseConnectTargetLogger implements ReverseConnectTargetListener {

  private static final Logger LOGGER = LoggerFactory.getLogger(ReverseConnectTargetLogger.class);

  @Override
  public void onTargetAdded(ReverseConnectTargetSnapshot snapshot) {
    LOGGER.info(
        "Reverse Connect target registered: targetId={}, clientListenerUrl={}, endpointUrl={},"
            + " registrationPeriod={}ms, connectTimeout={}ms, {}",
        snapshot.targetId(),
        snapshot.clientListenerUrl(),
        snapshot.endpointUrl(),
        snapshot.registrationPeriod(),
        snapshot.connectTimeout(),
        snapshot.enabled() ? "enabled" : "disabled (no outbound attempts will be scheduled)");
  }

  @Override
  public void onAttemptEvent(ReverseConnectAttemptEvent event) {
    switch (event.state()) {
      case CONNECTING ->
          LOGGER.info(
              "Reverse Connect target {} attempting connection (attempt #{})",
              event.targetId(),
              event.attemptNumber());

      case CONNECTED, REVERSE_HELLO_SENT, HELLO_HANDLER_INSTALLED ->
          LOGGER.debug(
              "Reverse Connect target {} attempt #{} progressed to {}",
              event.targetId(),
              event.attemptNumber(),
              event.state());

      case HANDOFF ->
          LOGGER.info(
              "Reverse Connect target {} connected: reverse channel handed off to server UASC path"
                  + " (attempt #{})",
              event.targetId(),
              event.attemptNumber());

      case CLIENT_ERROR, FAILED, CANCELLED, CLOSED ->
          LOGGER.warn(
              "Reverse Connect target {} attempt #{} ended in {}: statusCode={}, message={}",
              event.targetId(),
              event.attemptNumber(),
              event.state(),
              event.statusCode(),
              event.message());
    }
  }

  @Override
  public void onTargetUpdated(ReverseConnectTargetSnapshot snapshot) {
    Instant nextAttemptTime = snapshot.nextAttemptTime();
    StatusCode lastStatusCode = snapshot.lastStatusCode();
    Throwable lastError = snapshot.lastError();

    if (nextAttemptTime != null && (lastStatusCode != null || lastError != null)) {
      LOGGER.info(
          "Reverse Connect target {} retrying at {}: lastStatusCode={}, lastError={}",
          snapshot.targetId(),
          nextAttemptTime,
          lastStatusCode,
          lastError != null ? lastError.toString() : null);
    }

    if (snapshot.activeChannelCount() > 0) {
      LOGGER.debug(
          "Reverse Connect target {} has {} active reverse channel(s)",
          snapshot.targetId(),
          snapshot.activeChannelCount());
    }
  }
}

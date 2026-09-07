package com.digitalpetri.opcua.server.objects;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.digitalpetri.opcua.server.objects.PushTransaction.TrustListUpdate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.eclipse.milo.opcua.stack.core.StatusCodes;
import org.eclipse.milo.opcua.stack.core.UaException;
import org.eclipse.milo.opcua.stack.core.types.builtin.DateTime;
import org.eclipse.milo.opcua.stack.core.types.builtin.NodeId;
import org.eclipse.milo.opcua.stack.core.types.builtin.StatusCode;
import org.junit.jupiter.api.Test;

class PushTransactionManagerTest {

  private static final NodeId SESSION_A = new NodeId(0, "session-a");
  private static final NodeId SESSION_B = new NodeId(0, "session-b");
  private static final NodeId TRUST_LIST = new NodeId(0, "trust-list");

  private final PushTransactionManager manager = new PushTransactionManager();

  // Part 12 §7.10.2: once a transaction has started in one Session, every other Session is refused
  // with Bad_TransactionPending until it completes, while the owner keeps continuing it.
  @Test
  void otherSessionsAreRefusedWhileTransactionPending() throws UaException {
    PushTransaction first = manager.beginOrContinue(SESSION_A);

    assertSame(first, manager.beginOrContinue(SESSION_A), "owner continues the same transaction");
    assertStatus(StatusCodes.Bad_TransactionPending, () -> manager.beginOrContinue(SESSION_B));
    assertStatus(
        StatusCodes.Bad_TransactionPending, () -> manager.requireNotPendingForOthers(SESSION_B));
    assertDoesNotThrow(() -> manager.requireNotPendingForOthers(SESSION_A));
  }

  // Part 12 §7.10.9: ApplyChanges reports Bad_NothingToDo without a transaction and
  // Bad_SessionIdInvalid from a Session that does not own it.
  @Test
  void sealRequiresTheOwningSession() throws UaException {
    assertStatus(StatusCodes.Bad_NothingToDo, () -> manager.seal(SESSION_A));

    PushTransaction transaction = manager.beginOrContinue(SESSION_A);

    assertStatus(StatusCodes.Bad_SessionIdInvalid, () -> manager.seal(SESSION_B));
    assertSame(transaction, manager.seal(SESSION_A));
  }

  // Part 12 §7.10.11: CancelChanges reports Bad_NothingToDo without a transaction and
  // Bad_SessionIdInvalid from a Session that does not own it.
  @Test
  void cancelRequiresTheOwningSession() throws UaException {
    assertStatus(StatusCodes.Bad_NothingToDo, () -> manager.cancel(SESSION_A));

    PushTransaction transaction = manager.beginOrContinue(SESSION_A);

    assertStatus(StatusCodes.Bad_SessionIdInvalid, () -> manager.cancel(SESSION_B));
    assertSame(transaction, manager.cancel(SESSION_A));
    assertFalse(manager.isActive(), "cancelling ends the transaction");
    assertEquals(
        StatusCodes.Bad_RequestCancelledByClient, manager.getDiagnostics().result().getValue());
  }

  // Part 12 §7.10.2 allows exactly one active transaction. While ApplyChanges is applying one it
  // must still count as active, so nothing else can modify the targets mid-apply, and the owner
  // cannot stage into or cancel a transaction that is already being applied.
  @Test
  void sealedTransactionStaysActiveUntilRecorded() throws UaException {
    PushTransaction transaction = manager.beginOrContinue(SESSION_A);
    manager.seal(SESSION_A);

    assertTrue(manager.isActive(), "sealed transaction is still active");
    assertTrue(transaction.isSealed());
    assertStatus(StatusCodes.Bad_TransactionPending, () -> manager.beginOrContinue(SESSION_B));
    assertStatus(StatusCodes.Bad_InvalidState, () -> manager.beginOrContinue(SESSION_A));
    assertStatus(
        StatusCodes.Bad_InvalidState,
        () -> transaction.stage(new TrustListUpdate(TRUST_LIST, null, null)));
    assertStatus(StatusCodes.Bad_InvalidState, () -> manager.seal(SESSION_A));
    assertStatus(StatusCodes.Bad_InvalidState, () -> manager.cancel(SESSION_A));
    assertTrue(
        manager.cancelIfOwned(SESSION_A).isEmpty(),
        "the owner closing mid-apply leaves the outcome to ApplyChanges");
    assertEquals(StatusCodes.Bad_TransactionPending, manager.getDiagnostics().result().getValue());

    manager.record(transaction, StatusCode.GOOD, List.of());

    assertFalse(manager.isActive(), "recording the outcome ends the transaction");
    assertEquals(StatusCode.GOOD, manager.getDiagnostics().result());
    assertDoesNotThrow(() -> manager.beginOrContinue(SESSION_B));
  }

  // Part 12 §7.10.2: a transaction is cancelled automatically when the Session that created it
  // closes. Another Session closing must not disturb it.
  @Test
  void owningSessionCloseCancelsTransaction() throws UaException {
    manager.beginOrContinue(SESSION_A);

    assertTrue(manager.cancelIfOwned(SESSION_B).isEmpty(), "non-owner close is ignored");
    assertTrue(manager.isActive());

    assertTrue(manager.cancelIfOwned(SESSION_A).isPresent());
    assertFalse(manager.isActive());
    assertEquals(
        StatusCodes.Bad_SessionClosed,
        manager.getDiagnostics().result().getValue(),
        "TransactionDiagnostics reports why the transaction ended");
  }

  // Session-closed notifications arrive asynchronously, so a change can be staged by a Session that
  // has already closed, after its cancellation ran. A transaction owned by a Session that no longer
  // exists could never be completed and would block every other Session until restart.
  @Test
  void transactionOwnedByVanishedSessionExpires() throws UaException {
    Set<NodeId> liveSessions = new HashSet<>(Set.of(SESSION_A, SESSION_B));
    var manager = new PushTransactionManager(liveSessions::contains);

    manager.beginOrContinue(SESSION_A);
    liveSessions.remove(SESSION_A);

    assertDoesNotThrow(() -> manager.requireNotPendingForOthers(SESSION_B));
    assertFalse(manager.isActive());
    assertEquals(StatusCodes.Bad_SessionClosed, manager.getDiagnostics().result().getValue());
    assertDoesNotThrow(() -> manager.beginOrContinue(SESSION_B));
  }

  // Part 12 §7.10.17: TransactionDiagnostics describes the current transaction while it is active
  // and the most recently completed one afterwards, including the targets it touched.
  @Test
  void diagnosticsFollowTheTransactionLifecycle() throws UaException {
    assertEquals(PushTransactionManager.Diagnostics.NONE, manager.getDiagnostics());

    PushTransaction transaction = manager.beginOrContinue(SESSION_A);
    transaction.stage(new TrustListUpdate(TRUST_LIST, null, null));
    transaction.stage(new TrustListUpdate(TRUST_LIST, null, null));

    PushTransactionManager.Diagnostics active = manager.getDiagnostics();
    assertEquals(StatusCodes.Bad_TransactionPending, active.result().getValue());
    assertEquals(DateTime.MIN_VALUE, active.endTime());
    assertEquals(List.of(TRUST_LIST), active.affectedTrustLists(), "targets are reported once");

    manager.record(manager.seal(SESSION_A), StatusCode.GOOD, List.of());

    PushTransactionManager.Diagnostics completed = manager.getDiagnostics();
    assertEquals(StatusCode.GOOD, completed.result());
    assertEquals(transaction.getStartTime(), completed.startTime());
    assertTrue(
        completed.endTime().getJavaInstant().isAfter(DateTime.MIN_VALUE.getJavaInstant()),
        "completion time is recorded");
    assertEquals(List.of(TRUST_LIST), completed.affectedTrustLists());
  }

  private static void assertStatus(long expected, ThrowingCall call) {
    UaException exception = assertThrows(UaException.class, call::run);
    assertEquals(expected, exception.getStatusCode().getValue());
  }

  @FunctionalInterface
  private interface ThrowingCall {
    void run() throws Exception;
  }
}

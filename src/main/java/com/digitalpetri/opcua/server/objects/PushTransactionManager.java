package com.digitalpetri.opcua.server.objects;

import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import org.eclipse.milo.opcua.stack.core.StatusCodes;
import org.eclipse.milo.opcua.stack.core.UaException;
import org.eclipse.milo.opcua.stack.core.types.builtin.DateTime;
import org.eclipse.milo.opcua.stack.core.types.builtin.NodeId;
import org.eclipse.milo.opcua.stack.core.types.builtin.StatusCode;
import org.eclipse.milo.opcua.stack.core.types.structured.TransactionErrorType;
import org.jspecify.annotations.Nullable;

/**
 * Owns the single active PushManagement transaction described by OPC 10000-12 §7.10.2.
 *
 * <p>A transaction is created implicitly by the first Method call that stages a change and belongs
 * to the Session that made it. While it is active every other Session is refused with
 * Bad_TransactionPending. It ends when the owning Session calls ApplyChanges or CancelChanges, or
 * when that Session closes.
 *
 * <p>ApplyChanges {@link #seal seals} the transaction, applies its changes, and then {@link #record
 * records} the outcome. The transaction stays active throughout so nothing else can modify the
 * targets while they are being applied.
 *
 * <p>The manager also remembers the outcome of the most recently completed transaction so the
 * TransactionDiagnostics Object (§7.10.17) can report it.
 */
public final class PushTransactionManager {

  private final Object lock = new Object();

  private @Nullable PushTransaction active;
  private Diagnostics lastCompleted = Diagnostics.NONE;

  private final Predicate<NodeId> sessionExists;

  /** Create a manager that trusts every owning Session to still exist. */
  public PushTransactionManager() {
    this(sessionId -> true);
  }

  /**
   * Create a manager that expires a transaction whose owning Session no longer exists.
   *
   * <p>Session-closed notifications are delivered asynchronously, so a change can be staged by a
   * Session that has already closed, after its cancellation ran. Such a transaction could never be
   * completed or cancelled and would refuse every other Session until restart.
   *
   * @param sessionExists reports whether the Session with the given id still exists.
   */
  public PushTransactionManager(Predicate<NodeId> sessionExists) {
    this.sessionExists = sessionExists;
  }

  /**
   * Continue the transaction owned by {@code sessionId}, creating one if no transaction is active.
   *
   * @param sessionId the id of the Session staging a change.
   * @return the transaction owned by {@code sessionId}.
   * @throws UaException with Bad_TransactionPending if another Session owns the active transaction,
   *     or Bad_InvalidState if the owner's transaction is being applied.
   */
  public PushTransaction beginOrContinue(NodeId sessionId) throws UaException {
    synchronized (lock) {
      expireIfOwnerGone();

      if (active == null) {
        active = new PushTransaction(sessionId, DateTime.now());
      } else if (!active.getSessionId().equals(sessionId)) {
        throw new UaException(
            StatusCodes.Bad_TransactionPending, "transaction pending for another Session");
      } else if (active.isSealed()) {
        throw new UaException(StatusCodes.Bad_InvalidState, "ApplyChanges in progress");
      }
      return active;
    }
  }

  /**
   * Check that no other Session owns the active transaction.
   *
   * @param sessionId the id of the Session about to act.
   * @throws UaException with Bad_TransactionPending if another Session owns the active transaction.
   */
  public void requireNotPendingForOthers(NodeId sessionId) throws UaException {
    synchronized (lock) {
      expireIfOwnerGone();

      if (active != null && !active.getSessionId().equals(sessionId)) {
        throw new UaException(
            StatusCodes.Bad_TransactionPending, "transaction pending for another Session");
      }
    }
  }

  /**
   * @return {@code true} if any Session has an active transaction, including one being applied.
   */
  public boolean isActive() {
    synchronized (lock) {
      expireIfOwnerGone();

      return active != null;
    }
  }

  /**
   * Get the active transaction, which must be owned by {@code sessionId}.
   *
   * @param sessionId the id of the Session that must own the transaction.
   * @return the active transaction.
   * @throws UaException with Bad_NothingToDo if no transaction is active, or Bad_SessionIdInvalid
   *     if another Session owns it.
   */
  public PushTransaction requireOwned(NodeId sessionId) throws UaException {
    synchronized (lock) {
      expireIfOwnerGone();

      if (active == null) {
        throw new UaException(StatusCodes.Bad_NothingToDo, "no active transaction");
      }
      if (!active.getSessionId().equals(sessionId)) {
        throw new UaException(
            StatusCodes.Bad_SessionIdInvalid, "transaction owned by another Session");
      }
      return active;
    }
  }

  /**
   * Seal the active transaction, which must be owned by {@code sessionId}, so ApplyChanges can
   * apply it.
   *
   * <p>The transaction stays active until {@link #record} is called. Staging by the owner and every
   * action by other Sessions is refused meanwhile.
   *
   * @param sessionId the id of the Session that must own the transaction.
   * @return the sealed transaction.
   * @throws UaException with Bad_NothingToDo if no transaction is active, Bad_SessionIdInvalid if
   *     another Session owns it, or Bad_InvalidState if it is already being applied.
   */
  public PushTransaction seal(NodeId sessionId) throws UaException {
    synchronized (lock) {
      PushTransaction transaction = requireOwned(sessionId);
      transaction.seal();
      return transaction;
    }
  }

  /**
   * Record the outcome of a sealed transaction for TransactionDiagnostics and end it.
   *
   * @param transaction the completed transaction.
   * @param result the result reported by TransactionDiagnostics.
   * @param errors the per-target errors reported by TransactionDiagnostics.
   */
  public void record(
      PushTransaction transaction, StatusCode result, List<TransactionErrorType> errors) {

    synchronized (lock) {
      if (active == transaction) {
        active = null;
      }
      lastCompleted =
          new Diagnostics(
              transaction.getStartTime(),
              DateTime.now(),
              result,
              transaction.getAffectedTrustLists(),
              transaction.getAffectedCertificateGroups(),
              List.copyOf(errors));
    }
  }

  /**
   * Discard the active transaction, which must be owned by {@code sessionId}, without applying it.
   *
   * @param sessionId the id of the Session that must own the transaction.
   * @return the discarded transaction.
   * @throws UaException with Bad_NothingToDo if no transaction is active, Bad_SessionIdInvalid if
   *     another Session owns it, or Bad_InvalidState if it is being applied.
   */
  public PushTransaction cancel(NodeId sessionId) throws UaException {
    synchronized (lock) {
      PushTransaction transaction = requireOwned(sessionId);
      if (transaction.isSealed()) {
        throw new UaException(StatusCodes.Bad_InvalidState, "ApplyChanges in progress");
      }
      record(transaction, new StatusCode(StatusCodes.Bad_RequestCancelledByClient), List.of());
      return transaction;
    }
  }

  /**
   * Discard the transaction owned by {@code sessionId}, if there is one and it is not being
   * applied.
   *
   * <p>Used when the owning Session closes; §7.10.2 cancels its transaction automatically. A sealed
   * transaction is left to ApplyChanges, which records its outcome when it finishes.
   *
   * @param sessionId the id of the closing Session.
   * @return the discarded transaction, or empty if none was discarded.
   */
  public Optional<PushTransaction> cancelIfOwned(NodeId sessionId) {
    synchronized (lock) {
      if (active == null || !active.getSessionId().equals(sessionId) || active.isSealed()) {
        return Optional.empty();
      }
      PushTransaction transaction = active;
      record(transaction, new StatusCode(StatusCodes.Bad_SessionClosed), List.of());
      return Optional.of(transaction);
    }
  }

  /**
   * @return the diagnostics of the active transaction, or of the most recently completed one.
   */
  public Diagnostics getDiagnostics() {
    synchronized (lock) {
      expireIfOwnerGone();

      if (active != null) {
        return new Diagnostics(
            active.getStartTime(),
            DateTime.MIN_VALUE,
            new StatusCode(StatusCodes.Bad_TransactionPending),
            active.getAffectedTrustLists(),
            active.getAffectedCertificateGroups(),
            List.of());
      }
      return lastCompleted;
    }
  }

  /** Caller holds {@code lock}. */
  private void expireIfOwnerGone() {
    if (active != null && !active.isSealed() && !sessionExists.test(active.getSessionId())) {
      record(active, new StatusCode(StatusCodes.Bad_SessionClosed), List.of());
    }
  }

  /**
   * The values reported by a TransactionDiagnosticsType Object (OPC 10000-12 §7.10.17).
   *
   * <p>While a transaction is active {@code endTime} is {@link DateTime#MIN_VALUE} and {@code
   * result} is Bad_TransactionPending. After completion {@code result} is the StatusCode returned
   * by ApplyChanges, Bad_RequestCancelledByClient after CancelChanges, or Bad_SessionClosed when
   * the owning Session closed first.
   *
   * @param startTime when the transaction was created.
   * @param endTime when the transaction completed.
   * @param result the outcome of the transaction.
   * @param affectedTrustLists the TrustLists with staged changes.
   * @param affectedCertificateGroups the CertificateGroups with staged changes.
   * @param errors the per-target errors raised while applying changes.
   */
  public record Diagnostics(
      DateTime startTime,
      DateTime endTime,
      StatusCode result,
      List<NodeId> affectedTrustLists,
      List<NodeId> affectedCertificateGroups,
      List<TransactionErrorType> errors) {

    /** The diagnostics reported before any transaction has been created. */
    public static final Diagnostics NONE =
        new Diagnostics(
            DateTime.MIN_VALUE,
            DateTime.MIN_VALUE,
            StatusCode.GOOD,
            List.of(),
            List.of(),
            List.of());
  }
}

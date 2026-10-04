package com.digitalpetri.opcua.server.objects;

import java.security.KeyPair;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;
import org.eclipse.milo.opcua.stack.core.StatusCodes;
import org.eclipse.milo.opcua.stack.core.UaException;
import org.eclipse.milo.opcua.stack.core.security.CertificateGroup;
import org.eclipse.milo.opcua.stack.core.types.builtin.DateTime;
import org.eclipse.milo.opcua.stack.core.types.builtin.NodeId;

/**
 * The changes a Session has staged through PushManagement Methods but not yet applied.
 *
 * <p>OPC 10000-12 §7.10.2 requires a Server to queue changes in the order they were requested and
 * apply them together when ApplyChanges is called, so staged changes are kept as an ordered list
 * rather than collapsed per target.
 *
 * <p>A transaction is sealed while ApplyChanges is applying it. A sealed transaction accepts no
 * further changes but still counts as active, so other Sessions stay refused until the outcome has
 * been recorded.
 */
public final class PushTransaction {

  private final NodeId sessionId;
  private final DateTime startTime;
  private final List<StagedChange> changes = new ArrayList<>();
  private boolean sealed;

  PushTransaction(NodeId sessionId, DateTime startTime) {
    this.sessionId = sessionId;
    this.startTime = startTime;
  }

  /**
   * @return the id of the Session that created and owns this transaction.
   */
  public NodeId getSessionId() {
    return sessionId;
  }

  /**
   * @return the time the transaction was created.
   */
  public DateTime getStartTime() {
    return startTime;
  }

  /**
   * Queue a change to be applied when the transaction completes.
   *
   * @param change the change to queue.
   * @throws UaException with Bad_InvalidState if the transaction is being applied.
   */
  public synchronized void stage(StagedChange change) throws UaException {
    if (sealed) {
      throw new UaException(StatusCodes.Bad_InvalidState, "ApplyChanges in progress");
    }
    changes.add(change);
  }

  /**
   * Stop accepting changes; ApplyChanges is about to apply the ones staged so far.
   *
   * @throws UaException with Bad_InvalidState if the transaction is already sealed.
   */
  synchronized void seal() throws UaException {
    if (sealed) {
      throw new UaException(StatusCodes.Bad_InvalidState, "ApplyChanges in progress");
    }
    sealed = true;
  }

  /**
   * @return {@code true} if ApplyChanges is applying this transaction.
   */
  public synchronized boolean isSealed() {
    return sealed;
  }

  /**
   * @return the staged changes in the order they were requested.
   */
  public synchronized List<StagedChange> getChanges() {
    return List.copyOf(changes);
  }

  /**
   * @return the number of staged changes.
   */
  public synchronized int size() {
    return changes.size();
  }

  /**
   * @return the distinct CertificateGroup ids with a staged certificate update, in staging order.
   */
  public List<NodeId> getAffectedCertificateGroups() {
    return affectedTargets(CertificateUpdate.class);
  }

  /**
   * @return the distinct TrustList ids with a staged update, in staging order.
   */
  public List<NodeId> getAffectedTrustLists() {
    return affectedTargets(TrustListUpdate.class);
  }

  private synchronized List<NodeId> affectedTargets(Class<? extends StagedChange> type) {
    return changes.stream()
        .filter(type::isInstance)
        .map(StagedChange::targetId)
        .distinct()
        .toList();
  }

  /** A change queued in a {@link PushTransaction}. */
  public sealed interface StagedChange permits CertificateUpdate, TrustListUpdate {

    /**
     * @return the id of the Node the change targets, reported in TransactionDiagnostics.
     */
    NodeId targetId();

    /**
     * Apply the change.
     *
     * @throws Exception if the change cannot be applied.
     */
    void apply() throws Exception;
  }

  /**
   * A staged UpdateCertificate: a new key pair and certificate chain for one certificate type slot
   * of a {@link CertificateGroup}.
   *
   * @param certificateGroupId the id of the CertificateGroup Node.
   * @param certificateGroup the group whose store receives the update.
   * @param certificateTypeId the certificate type slot being replaced.
   * @param keyPair the key pair the certificate chain's leaf belongs to.
   * @param certificateChain the new certificate chain, leaf first.
   */
  public record CertificateUpdate(
      NodeId certificateGroupId,
      CertificateGroup certificateGroup,
      NodeId certificateTypeId,
      KeyPair keyPair,
      X509Certificate[] certificateChain)
      implements StagedChange {

    @Override
    public NodeId targetId() {
      return certificateGroupId;
    }

    @Override
    public void apply() throws Exception {
      certificateGroup.updateCertificate(certificateTypeId, keyPair, certificateChain);
    }
  }

  /**
   * A staged TrustList CloseAndUpdate.
   *
   * @param trustListId the id of the TrustList Node.
   * @param trustListObject the object that applies the contents to its TrustListManager.
   * @param contents the decoded and validated TrustList contents.
   */
  public record TrustListUpdate(
      NodeId trustListId, TrustListObject trustListObject, TrustListObject.Contents contents)
      implements StagedChange {

    @Override
    public NodeId targetId() {
      return trustListId;
    }

    @Override
    public void apply() {
      trustListObject.apply(contents);
    }
  }
}

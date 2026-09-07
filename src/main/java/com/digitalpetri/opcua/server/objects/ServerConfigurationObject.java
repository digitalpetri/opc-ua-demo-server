package com.digitalpetri.opcua.server.objects;

import static org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.Unsigned.uint;

import com.digitalpetri.opcua.server.DemoCertificateFactory;
import com.digitalpetri.opcua.server.objects.PushTransaction.CertificateUpdate;
import com.digitalpetri.opcua.server.objects.PushTransaction.StagedChange;
import com.digitalpetri.opcua.server.objects.PushTransactionManager.Diagnostics;
import java.io.ByteArrayInputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.cert.CertificateEncodingException;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.bouncycastle.asn1.pkcs.PrivateKeyInfo;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x500.style.IETFUtils;
import org.bouncycastle.asn1.x500.style.RFC4519Style;
import org.bouncycastle.cert.jcajce.JcaX509CertificateHolder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openssl.PEMKeyPair;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter;
import org.eclipse.milo.opcua.sdk.server.AbstractLifecycle;
import org.eclipse.milo.opcua.sdk.server.OpcUaServer;
import org.eclipse.milo.opcua.sdk.server.Session;
import org.eclipse.milo.opcua.sdk.server.SessionListener;
import org.eclipse.milo.opcua.sdk.server.methods.MethodInvocationHandler;
import org.eclipse.milo.opcua.sdk.server.methods.Out;
import org.eclipse.milo.opcua.sdk.server.model.objects.CertificateGroupTypeNode;
import org.eclipse.milo.opcua.sdk.server.model.objects.ServerConfigurationType;
import org.eclipse.milo.opcua.sdk.server.model.objects.ServerConfigurationTypeNode;
import org.eclipse.milo.opcua.sdk.server.model.objects.TransactionDiagnosticsTypeNode;
import org.eclipse.milo.opcua.sdk.server.nodes.UaMethodNode;
import org.eclipse.milo.opcua.sdk.server.nodes.UaNode;
import org.eclipse.milo.opcua.sdk.server.nodes.UaVariableNode;
import org.eclipse.milo.opcua.sdk.server.nodes.filters.AttributeFilters;
import org.eclipse.milo.opcua.stack.core.NodeIds;
import org.eclipse.milo.opcua.stack.core.StatusCodes;
import org.eclipse.milo.opcua.stack.core.UaException;
import org.eclipse.milo.opcua.stack.core.security.CertificateGroup;
import org.eclipse.milo.opcua.stack.core.security.CertificateManager;
import org.eclipse.milo.opcua.stack.core.types.builtin.ByteString;
import org.eclipse.milo.opcua.stack.core.types.builtin.DataValue;
import org.eclipse.milo.opcua.stack.core.types.builtin.LocalizedText;
import org.eclipse.milo.opcua.stack.core.types.builtin.NodeId;
import org.eclipse.milo.opcua.stack.core.types.builtin.StatusCode;
import org.eclipse.milo.opcua.stack.core.types.builtin.Variant;
import org.eclipse.milo.opcua.stack.core.types.enumerated.ApplicationType;
import org.eclipse.milo.opcua.stack.core.types.enumerated.MessageSecurityMode;
import org.eclipse.milo.opcua.stack.core.types.structured.TransactionErrorType;
import org.eclipse.milo.opcua.stack.core.util.CertificateUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Implementation behavior for an instance of the {@link ServerConfigurationType} Object.
 *
 * <p>Certificate and TrustList updates follow the PushManagement transaction model of OPC 10000-12
 * §7.10.2. UpdateCertificate and TrustList CloseAndUpdate stage their changes in the calling
 * Session's transaction and return {@code ApplyChangesRequired = true}. ApplyChanges then applies
 * every staged change, re-resolves the advertised endpoints so they present the new certificates,
 * and closes Sessions whose SecureChannel was established with a certificate that was replaced.
 * CancelChanges, or the owning Session closing, discards the staged changes.
 *
 * <p>Staging rather than applying immediately keeps the caller's own SecureChannel fully usable
 * (renewal, CreateSession, ActivateSession all resolve the server key by certificate thumbprint)
 * while several certificate types and the TrustList are pushed one call at a time.
 */
public class ServerConfigurationObject extends AbstractLifecycle {

  /**
   * How long ApplyChanges waits before closing Sessions bound to a replaced certificate.
   *
   * <p>The caller is usually one of those Sessions. The delay lets its ApplyChanges response, and a
   * prompt CloseSession, complete over the old SecureChannel before the Session is removed.
   */
  static final Duration SESSION_CLOSE_GRACE = Duration.ofSeconds(2);

  private final Logger logger = LoggerFactory.getLogger(getClass());

  /**
   * Temporary storage of PrivateKeys generated during CreateSigningRequest, for subsequent use in
   * UpdateCertificate, keyed by the affected CertificateGroup and CertificateType slot.
   */
  private final Map<CertificateSlot, PrivateKey> regeneratedPrivateKeys = new ConcurrentHashMap<>();

  private final PushTransactionManager transactions;
  private final List<TrustListObject> trustListObjects = new ArrayList<>();

  private final SessionListener sessionListener =
      new SessionListener() {
        @Override
        public void onSessionClosed(Session session) {
          // OPC 10000-12 §7.10.2: a transaction is cancelled when the Session that created it
          // closes. Staged changes are discarded; nothing was applied.
          transactions
              .cancelIfOwned(session.getSessionId())
              .ifPresent(
                  transaction ->
                      logger.info(
                          "Discarded {} staged change(s); owning Session {} closed",
                          transaction.size(),
                          session.getSessionId()));
        }
      };

  private final OpcUaServer server;
  private final ServerConfigurationTypeNode serverConfigurationTypeNode;
  private final DemoCertificateFactory certificateFactory;

  private record CertificateSlot(NodeId certificateGroupId, NodeId certificateTypeId) {}

  public ServerConfigurationObject(
      OpcUaServer server,
      ServerConfigurationTypeNode serverConfigurationTypeNode,
      DemoCertificateFactory certificateFactory) {

    this.server = server;
    this.serverConfigurationTypeNode = serverConfigurationTypeNode;
    this.certificateFactory = certificateFactory;

    transactions = new PushTransactionManager(this::sessionExists);
  }

  private boolean sessionExists(NodeId sessionId) {
    return server.getSessionManager().getAllSessions().stream()
        .anyMatch(session -> session.getSessionId().equals(sessionId));
  }

  @Override
  protected void onStartup() {
    // OPC 10000-12 7.10.3 defines these as optional. The generated standard instance includes
    // them, so publish the configured application identity instead of leaving null values.
    serverConfigurationTypeNode.setApplicationUri(server.getConfig().getApplicationUri());
    serverConfigurationTypeNode.setProductUri(server.getConfig().getProductUri());
    serverConfigurationTypeNode.setApplicationType(ApplicationType.Server);

    { // UpdateCertificateMethod
      UaMethodNode methodNode = serverConfigurationTypeNode.getUpdateCertificateMethodNode();
      methodNode.getFilterChain().addLast(new SecurityAdminFilter());
      methodNode.setInvocationHandler(new UpdateCertificateMethodImpl(methodNode));
    }

    { // ApplyChangesMethod
      UaMethodNode methodNode = serverConfigurationTypeNode.getApplyChangesMethodNode();
      methodNode.getFilterChain().addLast(new SecurityAdminFilter());
      methodNode.setInvocationHandler(new ApplyChangesMethodImpl(methodNode));
    }

    { // CancelChangesMethod
      UaMethodNode methodNode = serverConfigurationTypeNode.getCancelChangesMethodNode();
      methodNode.getFilterChain().addLast(new SecurityAdminFilter());
      methodNode.setInvocationHandler(new CancelChangesMethodImpl(methodNode));
    }

    { // CreateSigningRequestMethod
      UaMethodNode methodNode = serverConfigurationTypeNode.getCreateSigningRequestMethodNode();
      methodNode.getFilterChain().addLast(new SecurityAdminFilter());
      methodNode.setInvocationHandler(new CreateSigningRequestMethodImpl(methodNode));
    }

    { // GetRejectedListMethod
      UaMethodNode methodNode = serverConfigurationTypeNode.getGetRejectedListMethodNode();
      methodNode.getFilterChain().addLast(new SecurityAdminFilter());
      methodNode.setInvocationHandler(new GetRejectedListMethodImpl(methodNode));
    }

    // OPC 10000-12 Annex D: this demo provides current data and live Alarms & Conditions. RCP is
    // for Client/ClientAndServer applications that accept Server-initiated reverse connections;
    // this pure Server only initiates its configured outbound targets.
    serverConfigurationTypeNode.setServerCapabilities(new String[] {"DA", "AC"});
    serverConfigurationTypeNode.setSupportedPrivateKeyFormats(new String[] {"PEM", "PFX"});
    // OPC 10000-12 7.10.3 defines 0 as no TrustList-specific size limit.
    serverConfigurationTypeNode.setMaxTrustListSize(uint(0));
    serverConfigurationTypeNode.setMulticastDnsEnabled(false);
    // The demo stores PrivateKeys in software-backed keystores, not hardware secure storage.
    serverConfigurationTypeNode.setHasSecureElement(false);
    // OPC 10000-12 7.10.3: TRUE declares the 7.10.2 transaction lifecycle, which UpdateCertificate,
    // TrustList writes, ApplyChanges and CancelChanges implement here.
    serverConfigurationTypeNode.setSupportsTransactions(true);

    installTransactionDiagnostics(serverConfigurationTypeNode.getTransactionDiagnosticsNode());

    // These optional components are generated into the standard instance but the demo does not
    // implement restoring defaults or a remotely editable application configuration file.
    // UaNode.delete() removes descendants too.
    deleteIfPresent(NodeIds.ServerConfiguration_ResetToServerDefaults);
    deleteIfPresent(NodeIds.ServerConfiguration_ConfigurationFile);

    server.getSessionManager().addSessionListener(sessionListener);

    CertificateManager certificateManager = server.getConfig().getCertificateManager();
    List<CertificateGroup> certificateGroups = certificateManager.getCertificateGroups();

    Set<NodeId> supportedGroups =
        certificateGroups.stream()
            .flatMap(group -> certificateManager.getCertificateGroupId(group).stream())
            .collect(Collectors.toSet());

    if (!supportedGroups.contains(
        NodeIds.ServerConfiguration_CertificateGroups_DefaultUserTokenGroup)) {

      server
          .getAddressSpaceManager()
          .getManagedNode(NodeIds.ServerConfiguration_CertificateGroups_DefaultUserTokenGroup)
          .ifPresent(UaNode::delete);
    }

    if (!supportedGroups.contains(
        NodeIds.ServerConfiguration_CertificateGroups_DefaultHttpsGroup)) {

      server
          .getAddressSpaceManager()
          .getManagedNode(NodeIds.ServerConfiguration_CertificateGroups_DefaultHttpsGroup)
          .ifPresent(UaNode::delete);
    }

    for (CertificateGroup group : certificateGroups) {
      NodeId certificateGroupId = certificateManager.getCertificateGroupId(group).orElseThrow();

      CertificateGroupTypeNode groupNode =
          server
              .getAddressSpaceManager()
              .getManagedNode(certificateGroupId)
              .filter(node -> node instanceof CertificateGroupTypeNode)
              .map(CertificateGroupTypeNode.class::cast)
              .orElse(null);

      if (groupNode != null) {
        var trustListObject =
            new TrustListObject(
                group.getCertificateQuarantine(),
                group.getTrustListManager(),
                groupNode.getTrustListNode(),
                transactions);
        trustListObject.startup();
        trustListObjects.add(trustListObject);

        groupNode
            .getCertificateTypesNode()
            .getFilterChain()
            .addLast(
                AttributeFilters.getValue(
                    ctx -> {
                      NodeId[] certificateTypeIds =
                          group.getSupportedCertificateTypeIds().toArray(NodeId[]::new);
                      return new DataValue(new Variant(certificateTypeIds));
                    }));
      }
    }

    logger.debug("ServerConfigurationObject started: {}", serverConfigurationTypeNode.getNodeId());
  }

  /**
   * Report the current or most recently completed transaction (OPC 10000-12 §7.10.17) by reading
   * the {@link PushTransactionManager} on every access, so the Object never lags behind staging,
   * apply, or cancel.
   */
  private void installTransactionDiagnostics(TransactionDiagnosticsTypeNode diagnosticsNode) {
    report(diagnosticsNode.getStartTimeNode(), Diagnostics::startTime);
    report(diagnosticsNode.getEndTimeNode(), Diagnostics::endTime);
    report(diagnosticsNode.getResultNode(), Diagnostics::result);
    report(
        diagnosticsNode.getAffectedTrustListsNode(),
        diagnostics -> diagnostics.affectedTrustLists().toArray(NodeId[]::new));
    report(
        diagnosticsNode.getAffectedCertificateGroupsNode(),
        diagnostics -> diagnostics.affectedCertificateGroups().toArray(NodeId[]::new));
    report(
        diagnosticsNode.getErrorsNode(),
        diagnostics -> diagnostics.errors().toArray(TransactionErrorType[]::new));
  }

  private void report(UaVariableNode node, Function<Diagnostics, Object> value) {
    node.getFilterChain()
        .addLast(
            AttributeFilters.getValue(
                ctx -> new DataValue(new Variant(value.apply(transactions.getDiagnostics())))));
  }

  private void deleteIfPresent(NodeId nodeId) {
    server.getAddressSpaceManager().getManagedNode(nodeId).ifPresent(UaNode::delete);
  }

  @Override
  protected void onShutdown() {
    server.getSessionManager().removeSessionListener(sessionListener);

    trustListObjects.forEach(TrustListObject::shutdown);
    trustListObjects.clear();

    serverConfigurationTypeNode
        .getUpdateCertificateMethodNode()
        .setInvocationHandler(MethodInvocationHandler.NOT_IMPLEMENTED);
    serverConfigurationTypeNode
        .getApplyChangesMethodNode()
        .setInvocationHandler(MethodInvocationHandler.NOT_IMPLEMENTED);
    serverConfigurationTypeNode
        .getCancelChangesMethodNode()
        .setInvocationHandler(MethodInvocationHandler.NOT_IMPLEMENTED);
    serverConfigurationTypeNode
        .getCreateSigningRequestMethodNode()
        .setInvocationHandler(MethodInvocationHandler.NOT_IMPLEMENTED);
    serverConfigurationTypeNode
        .getGetRejectedListMethodNode()
        .setInvocationHandler(MethodInvocationHandler.NOT_IMPLEMENTED);

    logger.debug("ServerConfigurationObject stopped: {}", serverConfigurationTypeNode.getNodeId());
  }

  /**
   * Validates the new certificate and key material and stages the update in the calling Session's
   * transaction. The CertificateGroup is not modified until ApplyChanges.
   *
   * @see <a href="https://reference.opcfoundation.org/specs/OPC-10000-12/7.10.5/">
   *     https://reference.opcfoundation.org/specs/OPC-10000-12/7.10.5/</a>
   */
  public class UpdateCertificateMethodImpl
      extends ServerConfigurationTypeNode.UpdateCertificateMethod {

    public UpdateCertificateMethodImpl(UaMethodNode node) {
      super(node);
    }

    @Override
    protected void invoke(
        InvocationContext context,
        NodeId certificateGroupId,
        NodeId certificateTypeId,
        ByteString certificate,
        ByteString[] issuerCertificates,
        String privateKeyFormat,
        ByteString privateKey,
        Out<Boolean> applyChangesRequired)
        throws UaException {

      Session session = context.getSession().orElseThrow();

      requireEncryptedChannel(session);

      transactions.requireNotPendingForOthers(session.getSessionId());

      if (certificateGroupId == null || certificateGroupId.isNull()) {
        certificateGroupId = NodeIds.ServerConfiguration_CertificateGroups_DefaultApplicationGroup;
      }

      CertificateGroup certificateGroup =
          server
              .getConfig()
              .getCertificateManager()
              .getCertificateGroup(certificateGroupId)
              .orElseThrow(
                  () -> new UaException(StatusCodes.Bad_InvalidArgument, "certificateGroupId"));

      if (certificateTypeId == null
          || !certificateGroup.getSupportedCertificateTypeIds().contains(certificateTypeId)) {
        throw new UaException(StatusCodes.Bad_InvalidArgument, "certificateTypeId");
      }

      CertificateSlot certificateSlot = new CertificateSlot(certificateGroupId, certificateTypeId);

      var certificateChain = new ArrayList<X509Certificate>();

      try {
        certificateChain.add(CertificateUtil.decodeCertificate(certificate.bytesOrEmpty()));
      } catch (Exception e) {
        throw new UaException(StatusCodes.Bad_CertificateInvalid, "certificate", e);
      }

      try {
        if (issuerCertificates != null) {
          for (ByteString bs : issuerCertificates) {
            certificateChain.add(CertificateUtil.decodeCertificate(bs.bytesOrEmpty()));
          }
        }
      } catch (Exception e) {
        throw new UaException(StatusCodes.Bad_CertificateInvalid, "issuerCertificates", e);
      }

      KeyPair newKeyPair;
      if (privateKey == null || privateKey.isNullOrEmpty()) {
        PrivateKey key;
        if ((key = regeneratedPrivateKeys.get(certificateSlot)) != null) {
          // Use previously generated PrivateKey + new certificate PublicKey. The key stays
          // available until the staged update is applied, so a cancelled or abandoned transaction
          // does not strand the certificate that was issued for it.
          newKeyPair = new KeyPair(certificateChain.get(0).getPublicKey(), key);
        } else {
          // Use current PrivateKey + new certificate PublicKey
          KeyPair keyPair =
              certificateGroup
                  .getKeyPair(certificateTypeId)
                  .orElseThrow(
                      () -> new UaException(StatusCodes.Bad_InvalidArgument, "certificateTypeId"));

          newKeyPair = new KeyPair(certificateChain.get(0).getPublicKey(), keyPair.getPrivate());
        }
      } else {
        // Use new PrivateKey + new certificate PublicKey
        if (!"PEM".equals(privateKeyFormat) && !"PFX".equals(privateKeyFormat)) {
          throw new UaException(StatusCodes.Bad_NotSupported, "privateKeyFormat");
        }

        try {
          PrivateKey newPrivateKey =
              "PEM".equals(privateKeyFormat)
                  ? readPemEncodedPrivateKey(privateKey)
                  : readPfxEncodedPrivateKey(privateKey);

          newKeyPair = new KeyPair(certificateChain.get(0).getPublicKey(), newPrivateKey);
        } catch (Exception e) {
          throw new UaException(StatusCodes.Bad_NotSupported, "privateKey", e);
        }
      }

      // A certificate issued for some other key would pass every decode step and only fail once
      // ApplyChanges installed it, taking the endpoint down with it. Refuse it here instead.
      verifyPrivateKeyMatchesCertificate(newKeyPair);

      PushTransaction transaction = transactions.beginOrContinue(session.getSessionId());
      transaction.stage(
          new CertificateUpdate(
              certificateGroupId,
              certificateGroup,
              certificateTypeId,
              newKeyPair,
              certificateChain.toArray(new X509Certificate[0])));

      logger.info(
          "Staged certificate update for group={} type={} subject={}",
          certificateGroupId.toParseableString(),
          certificateTypeId.toParseableString(),
          certificateChain.get(0).getSubjectX500Principal().getName());

      applyChangesRequired.set(true);
    }

    private static PrivateKey readPemEncodedPrivateKey(ByteString privateKey) throws Exception {
      var reader =
          new InputStreamReader(
              new ByteArrayInputStream(privateKey.bytesOrEmpty()), StandardCharsets.US_ASCII);

      try (var parser = new PEMParser(reader)) {
        Object pemObject = parser.readObject();
        var converter = new JcaPEMKeyConverter();

        // PKCS#8 "PRIVATE KEY" carries its own algorithm identifier, so RSA and every ECC key type
        // decode through the same path. Legacy PKCS#1 "RSA PRIVATE KEY" arrives as a PEMKeyPair.
        return switch (pemObject) {
          case PrivateKeyInfo info -> converter.getPrivateKey(info);
          case PEMKeyPair keyPair -> converter.getPrivateKey(keyPair.getPrivateKeyInfo());
          case null -> throw new Exception("no PEM object found");
          default ->
              throw new Exception(
                  "unsupported PEM object: " + pemObject.getClass().getSimpleName());
        };
      }
    }

    private static PrivateKey readPfxEncodedPrivateKey(ByteString privateKey) throws Exception {
      var keyStore = KeyStore.getInstance("PKCS12");
      keyStore.load(new ByteArrayInputStream(privateKey.bytesOrEmpty()), null);

      while (keyStore.aliases().hasMoreElements()) {
        String alias = keyStore.aliases().nextElement();
        if (keyStore.isKeyEntry(alias)) {
          Key key = keyStore.getKey(alias, null);
          if (key instanceof PrivateKey) {
            return (PrivateKey) key;
          }
        }
      }

      throw new Exception("no PrivateKey found in PKCS12 keystore");
    }
  }

  /**
   * Prove that the private key can produce a signature the certificate's public key verifies.
   *
   * @throws UaException with Bad_SecurityChecksFailed if the keys do not belong together, or
   *     Bad_NotSupported if the key algorithm cannot be checked.
   */
  static void verifyPrivateKeyMatchesCertificate(KeyPair keyPair) throws UaException {
    String publicAlgorithm = keyPair.getPublic().getAlgorithm();

    String signatureAlgorithm =
        switch (publicAlgorithm) {
          case "RSA" -> "SHA256withRSA";
          case "EC" -> "SHA256withECDSA";
          case "Ed25519", "Ed448", "EdDSA" -> publicAlgorithm;
          default -> throw new UaException(StatusCodes.Bad_NotSupported, "privateKey");
        };

    byte[] probe = "UpdateCertificate key check".getBytes(StandardCharsets.US_ASCII);

    try {
      Signature signer = newSignature(signatureAlgorithm, keyPair.getPrivate());
      signer.update(probe);
      byte[] signature = signer.sign();

      Signature verifier = newSignature(signatureAlgorithm, keyPair.getPublic());
      verifier.update(probe);

      if (!verifier.verify(signature)) {
        throw new UaException(
            StatusCodes.Bad_SecurityChecksFailed, "privateKey does not match certificate");
      }
    } catch (GeneralSecurityException e) {
      throw new UaException(
          StatusCodes.Bad_SecurityChecksFailed, "privateKey does not match certificate", e);
    }
  }

  /**
   * Create a {@link Signature} initialized for signing with a {@link PrivateKey} or verifying with
   * a {@link PublicKey}, falling back to Bouncy Castle for keys the default providers reject, such
   * as Brainpool curves.
   */
  private static Signature newSignature(String algorithm, Key key) throws GeneralSecurityException {
    try {
      return initialize(Signature.getInstance(algorithm), key);
    } catch (InvalidKeyException e) {
      return initialize(Signature.getInstance(algorithm, bouncyCastleProvider()), key);
    }
  }

  private static Signature initialize(Signature signature, Key key) throws InvalidKeyException {
    if (key instanceof PrivateKey privateKey) {
      signature.initSign(privateKey);
    } else {
      signature.initVerify((PublicKey) key);
    }
    return signature;
  }

  private static Provider bouncyCastleProvider() {
    Provider registered = Security.getProvider(BouncyCastleProvider.PROVIDER_NAME);
    return registered != null ? registered : FallbackProvider.BOUNCY_CASTLE;
  }

  /** Created on first use, and only when the application has not registered Bouncy Castle. */
  private static final class FallbackProvider {
    static final Provider BOUNCY_CASTLE = new BouncyCastleProvider();
  }

  /**
   * Applies the calling Session's transaction: every staged change in order, then a reset of the
   * advertised endpoints so they present the new certificates, then closure of Sessions whose
   * SecureChannel was established with a replaced certificate.
   *
   * @see <a href="https://reference.opcfoundation.org/specs/OPC-10000-12/7.10.9/">
   *     https://reference.opcfoundation.org/specs/OPC-10000-12/7.10.9/</a>
   */
  public class ApplyChangesMethodImpl extends ServerConfigurationTypeNode.ApplyChangesMethod {

    public ApplyChangesMethodImpl(UaMethodNode node) {
      super(node);
    }

    @Override
    protected void invoke(InvocationContext context) throws UaException {
      Session session = context.getSession().orElseThrow();

      requireAuthenticatedChannel(session);

      NodeId sessionId = session.getSessionId();

      // Bad_NothingToDo or Bad_SessionIdInvalid before anything else.
      transactions.requireOwned(sessionId);

      // §7.10.9: nothing is applied while a TrustList is open for writing, and the transaction
      // survives so ApplyChanges can be retried once it is closed. Only the owner can hold a write
      // handle, because opening for write joins its transaction.
      if (trustListObjects.stream().anyMatch(TrustListObject::isOpenForWriting)) {
        throw new UaException(StatusCodes.Bad_InvalidState, "TrustList is open for writing");
      }

      // The transaction stays active, but sealed, until its outcome is recorded below, so no other
      // Session can modify the TrustLists or CertificateGroups while they are being applied.
      PushTransaction transaction = transactions.seal(sessionId);
      List<StagedChange> changes = transaction.getChanges();

      Set<ByteString> replacedThumbprints = thumbprintsReplacedBy(changes);

      var errors = new ArrayList<TransactionErrorType>();
      boolean certificatesChanged = false;

      for (StagedChange change : changes) {
        try {
          change.apply();

          if (change instanceof CertificateUpdate update) {
            certificatesChanged = true;
            regeneratedPrivateKeys.remove(
                new CertificateSlot(update.certificateGroupId(), update.certificateTypeId()),
                update.keyPair().getPrivate());
          }
        } catch (Exception e) {
          logger.error("Failed to apply staged change to {}", change.targetId(), e);

          long statusCode =
              e instanceof UaException ue
                  ? ue.getStatusCode().getValue()
                  : StatusCodes.Bad_UnexpectedError;

          errors.add(
              new TransactionErrorType(
                  change.targetId(),
                  new StatusCode(statusCode),
                  LocalizedText.english(String.valueOf(e.getMessage()))));
        }
      }

      if (certificatesChanged) {
        // Endpoint resolution, including the certificate each endpoint advertises, is memoized by
        // the SDK. New SecureChannels look their certificate up by the thumbprint the client took
        // from GetEndpoints, so the advertised set has to change for the new certificates to be
        // reachable at all.
        server.resetEndpointDescriptionCache();

        // A certificate that is still installed, because its update failed or re-installed the
        // same certificate, still resolves by thumbprint; Sessions bound to it keep working.
        CertificateManager certificateManager = server.getConfig().getCertificateManager();
        replacedThumbprints.removeIf(
            thumbprint -> certificateManager.getCertificate(thumbprint).isPresent());

        closeSessionsBoundTo(replacedThumbprints);
      }

      StatusCode result =
          errors.isEmpty() ? StatusCode.GOOD : new StatusCode(StatusCodes.Bad_UnexpectedError);

      transactions.record(transaction, result, errors);

      logger.info(
          "Applied {} staged change(s) from Session {}; {} error(s)",
          changes.size(),
          sessionId,
          errors.size());

      if (!errors.isEmpty()) {
        throw new UaException(
            StatusCodes.Bad_UnexpectedError,
            errors.size() + " change(s) failed; see TransactionDiagnostics");
      }
    }

    /** Thumbprints of the certificates currently installed in the slots the changes replace. */
    private Set<ByteString> thumbprintsReplacedBy(List<StagedChange> changes) {
      var thumbprints = new HashSet<ByteString>();

      for (StagedChange change : changes) {
        if (change instanceof CertificateUpdate update) {
          update
              .certificateGroup()
              .getCertificateChain(update.certificateTypeId())
              .map(chain -> chain[0])
              .ifPresent(
                  current -> {
                    try {
                      thumbprints.add(CertificateUtil.thumbprint(current));
                    } catch (UaException e) {
                      logger.warn("Unable to compute thumbprint of replaced certificate", e);
                    }
                  });
        }
      }

      return thumbprints;
    }

    /**
     * Close Sessions whose SecureChannel presents a certificate that is no longer installed.
     *
     * <p>Such a channel can still exchange symmetrically secured messages, but the SDK resolves the
     * server key by thumbprint for channel renewal, CreateSession and ActivateSession, so every one
     * of those now fails. Closing the Sessions promptly gives clients a clear signal to rediscover
     * endpoints and reconnect instead of failing at the next renewal. Sessions on
     * SecurityPolicy.None channels present no certificate and are left alone.
     */
    private void closeSessionsBoundTo(Set<ByteString> replacedThumbprints) {
      if (replacedThumbprints.isEmpty()) {
        return;
      }

      server
          .getScheduledExecutorService()
          .schedule(
              () -> {
                for (Session session : server.getSessionManager().getAllSessions()) {
                  if (isBoundTo(session, replacedThumbprints)) {
                    logger.info(
                        "Closing Session {} ({}); its SecureChannel certificate was replaced",
                        session.getSessionId(),
                        session.getSessionName());

                    server.getSessionManager().killSession(session.getSessionId(), false);
                  }
                }
              },
              SESSION_CLOSE_GRACE.toMillis(),
              TimeUnit.MILLISECONDS);
    }

    private boolean isBoundTo(Session session, Set<ByteString> thumbprints) {
      // Sessions on SecurityPolicy.None channels have no server certificate.
      X509Certificate channelCertificate =
          session.getSecurityConfiguration().getServerCertificate();
      if (channelCertificate == null) {
        return false;
      }

      try {
        return thumbprints.contains(CertificateUtil.thumbprint(channelCertificate));
      } catch (UaException e) {
        return false;
      }
    }
  }

  /**
   * Discards the calling Session's transaction without applying anything.
   *
   * @see <a href="https://reference.opcfoundation.org/specs/OPC-10000-12/7.10.11/">
   *     https://reference.opcfoundation.org/specs/OPC-10000-12/7.10.11/</a>
   */
  public class CancelChangesMethodImpl extends ServerConfigurationTypeNode.CancelChangesMethod {

    public CancelChangesMethodImpl(UaMethodNode node) {
      super(node);
    }

    @Override
    protected void invoke(InvocationContext context) throws UaException {
      Session session = context.getSession().orElseThrow();

      requireAuthenticatedChannel(session);

      NodeId sessionId = session.getSessionId();

      PushTransaction transaction = transactions.cancel(sessionId);

      // A TrustList the owner opened for writing belongs to the cancelled transaction. Closing it
      // discards what was written and lets the next transaction open the TrustList again.
      trustListObjects.forEach(trustList -> trustList.closeHandles(sessionId, true));

      logger.info(
          "Cancelled transaction with {} staged change(s) from Session {}",
          transaction.size(),
          sessionId);
    }
  }

  /** UpdateCertificate and CreateSigningRequest carry key material and require encryption. */
  private static void requireEncryptedChannel(Session session) throws UaException {
    if (session.getSecurityConfiguration().getSecurityMode()
        != MessageSecurityMode.SignAndEncrypt) {
      throw new UaException(StatusCodes.Bad_SecurityModeInsufficient);
    }
  }

  /** ApplyChanges and CancelChanges only require the SecureChannel to be authenticated. */
  private static void requireAuthenticatedChannel(Session session) throws UaException {
    MessageSecurityMode securityMode = session.getSecurityConfiguration().getSecurityMode();

    if (securityMode != MessageSecurityMode.Sign
        && securityMode != MessageSecurityMode.SignAndEncrypt) {
      throw new UaException(StatusCodes.Bad_SecurityModeInsufficient);
    }
  }

  /**
   * @see <a href="https://reference.opcfoundation.org/specs/OPC-10000-12/7.10.10/">
   *     https://reference.opcfoundation.org/specs/OPC-10000-12/7.10.10/</a>
   */
  public class CreateSigningRequestMethodImpl
      extends ServerConfigurationTypeNode.CreateSigningRequestMethod {

    public CreateSigningRequestMethodImpl(UaMethodNode node) {
      super(node);
    }

    @Override
    protected void invoke(
        InvocationContext context,
        NodeId certificateGroupId,
        NodeId certificateTypeId,
        String subjectName,
        Boolean regeneratePrivateKey,
        ByteString nonce,
        Out<ByteString> certificateRequest)
        throws UaException {

      Session session = context.getSession().orElseThrow();

      requireEncryptedChannel(session);

      // §7.10.10: Bad_TransactionPending while another Session's transaction is active.
      transactions.requireNotPendingForOthers(session.getSessionId());

      validateRegeneratePrivateKeyNonce(regeneratePrivateKey, nonce);

      if (certificateGroupId == null || certificateGroupId.isNull()) {
        certificateGroupId = NodeIds.ServerConfiguration_CertificateGroups_DefaultApplicationGroup;
      }

      CertificateGroup certificateGroup =
          server
              .getConfig()
              .getCertificateManager()
              .getCertificateGroup(certificateGroupId)
              .orElseThrow(
                  () -> new UaException(StatusCodes.Bad_InvalidArgument, "certificateGroupId"));

      try {
        KeyPair keyPair =
            certificateGroup
                .getKeyPair(certificateTypeId)
                .orElseThrow(
                    () -> new UaException(StatusCodes.Bad_InvalidArgument, "certificateTypeId"));

        X509Certificate certificate =
            certificateGroup
                .getCertificateChain(certificateTypeId)
                .map(certificateChain -> certificateChain[0])
                .orElseThrow(
                    () -> new UaException(StatusCodes.Bad_InvalidArgument, "certificateTypeId"));

        if (regeneratePrivateKey) {
          try {
            keyPair = certificateFactory.createKeyPair(certificateTypeId, nonce.bytesOrEmpty());

            var certificateSlot = new CertificateSlot(certificateGroupId, certificateTypeId);
            regeneratedPrivateKeys.put(certificateSlot, keyPair.getPrivate());
          } catch (UnsupportedOperationException e) {
            throw new UaException(StatusCodes.Bad_NotSupported, e);
          } catch (Exception e) {
            throw new UaException(StatusCodes.Bad_UnexpectedError, e);
          }
        }

        X500Name subject;
        if (subjectName == null || subjectName.isEmpty()) {
          subject = new JcaX509CertificateHolder(certificate).getSubject();
        } else {
          subject = new X500Name(IETFUtils.rDNsFromString(subjectName, RFC4519Style.INSTANCE));
        }

        ByteString csr =
            certificateFactory.createSigningRequest(
                certificateTypeId,
                keyPair,
                subject,
                CertificateUtil.getSanUri(certificate)
                    .orElse(server.getConfig().getApplicationUri()),
                CertificateUtil.getSanDnsNames(certificate),
                CertificateUtil.getSanIpAddresses(certificate));

        certificateRequest.set(csr);
      } catch (UaException e) {
        throw e;
      } catch (Exception e) {
        throw new UaException(StatusCodes.Bad_UnexpectedError, e);
      }
    }
  }

  static void validateRegeneratePrivateKeyNonce(Boolean regeneratePrivateKey, ByteString nonce)
      throws UaException {

    if (regeneratePrivateKey == null) {
      throw new UaException(StatusCodes.Bad_InvalidArgument, "regeneratePrivateKey");
    }

    if (regeneratePrivateKey && (nonce == null || nonce.length() < 32)) {
      throw new UaException(StatusCodes.Bad_InvalidArgument, "nonce");
    }
  }

  /**
   * @see <a href="https://reference.opcfoundation.org/specs/OPC-10000-12/7.10.12/">
   *     https://reference.opcfoundation.org/specs/OPC-10000-12/7.10.12/</a>
   */
  public class GetRejectedListMethodImpl extends ServerConfigurationTypeNode.GetRejectedListMethod {

    public GetRejectedListMethodImpl(UaMethodNode node) {
      super(node);
    }

    @Override
    protected void invoke(InvocationContext context, Out<ByteString[]> certificates)
        throws UaException {

      Session session = context.getSession().orElseThrow();

      requireAuthenticatedChannel(session);

      var certificateBytes = new ArrayList<ByteString>();

      // The server-wide rejected list is the union of every registered group's quarantine.
      List<X509Certificate> rejectedCertificates =
          server.getConfig().getCertificateManager().getRejectedCertificates();

      for (X509Certificate certificate : rejectedCertificates) {
        try {
          certificateBytes.add(ByteString.of(certificate.getEncoded()));
        } catch (CertificateEncodingException e) {
          throw new UaException(StatusCodes.Bad_UnexpectedError, e);
        }
      }

      certificates.set(certificateBytes.toArray(new ByteString[0]));
    }
  }
}

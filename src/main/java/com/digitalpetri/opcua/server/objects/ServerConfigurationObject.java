package com.digitalpetri.opcua.server.objects;

import static org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.Unsigned.uint;

import com.digitalpetri.opcua.server.DemoCertificateFactory;
import java.io.ByteArrayInputStream;
import java.io.InputStreamReader;
import java.security.*;
import java.security.cert.CertificateEncodingException;
import java.security.cert.X509Certificate;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x500.style.IETFUtils;
import org.bouncycastle.asn1.x500.style.RFC4519Style;
import org.bouncycastle.cert.jcajce.JcaX509CertificateHolder;
import org.bouncycastle.util.io.pem.PemReader;
import org.eclipse.milo.opcua.sdk.server.AbstractLifecycle;
import org.eclipse.milo.opcua.sdk.server.OpcUaServer;
import org.eclipse.milo.opcua.sdk.server.Session;
import org.eclipse.milo.opcua.sdk.server.methods.MethodInvocationHandler;
import org.eclipse.milo.opcua.sdk.server.methods.Out;
import org.eclipse.milo.opcua.sdk.server.model.objects.CertificateGroupTypeNode;
import org.eclipse.milo.opcua.sdk.server.model.objects.ServerConfigurationType;
import org.eclipse.milo.opcua.sdk.server.model.objects.ServerConfigurationTypeNode;
import org.eclipse.milo.opcua.sdk.server.nodes.UaMethodNode;
import org.eclipse.milo.opcua.sdk.server.nodes.UaNode;
import org.eclipse.milo.opcua.sdk.server.nodes.filters.AttributeFilters;
import org.eclipse.milo.opcua.stack.core.NodeIds;
import org.eclipse.milo.opcua.stack.core.StatusCodes;
import org.eclipse.milo.opcua.stack.core.UaException;
import org.eclipse.milo.opcua.stack.core.security.CertificateGroup;
import org.eclipse.milo.opcua.stack.core.security.CertificateManager;
import org.eclipse.milo.opcua.stack.core.types.builtin.ByteString;
import org.eclipse.milo.opcua.stack.core.types.builtin.DataValue;
import org.eclipse.milo.opcua.stack.core.types.builtin.NodeId;
import org.eclipse.milo.opcua.stack.core.types.builtin.Variant;
import org.eclipse.milo.opcua.stack.core.types.enumerated.ApplicationType;
import org.eclipse.milo.opcua.stack.core.types.enumerated.MessageSecurityMode;
import org.eclipse.milo.opcua.stack.core.util.CertificateUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Implementation behavior for an instance of the {@link ServerConfigurationType} Object. */
public class ServerConfigurationObject extends AbstractLifecycle {

  private final Logger logger = LoggerFactory.getLogger(getClass());

  /**
   * Temporary storage of PrivateKeys generated during CreateSigningRequest, for subsequent use in
   * UpdateCertificate, keyed by the affected CertificateGroup and CertificateType slot.
   */
  private final Map<CertificateSlot, PrivateKey> regeneratedPrivateKeys = new ConcurrentHashMap<>();

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

    // These optional components are generated into the standard instance but the demo does not
    // implement transaction cancellation, restoring defaults, transaction diagnostics, or a
    // remotely editable application configuration file. UaNode.delete() removes descendants too.
    deleteIfPresent(NodeIds.ServerConfiguration_CancelChanges);
    deleteIfPresent(NodeIds.ServerConfiguration_ResetToServerDefaults);
    deleteIfPresent(NodeIds.ServerConfiguration_TransactionDiagnostics);
    deleteIfPresent(NodeIds.ServerConfiguration_ConfigurationFile);

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
                groupNode.getTrustListNode());
        trustListObject.startup();

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

  private void deleteIfPresent(NodeId nodeId) {
    server.getAddressSpaceManager().getManagedNode(nodeId).ifPresent(UaNode::delete);
  }

  @Override
  protected void onShutdown() {
    serverConfigurationTypeNode
        .getUpdateCertificateMethodNode()
        .setInvocationHandler(MethodInvocationHandler.NOT_IMPLEMENTED);
    serverConfigurationTypeNode
        .getApplyChangesMethodNode()
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

      if (session.getSecurityConfiguration().getSecurityMode()
          != MessageSecurityMode.SignAndEncrypt) {
        throw new UaException(StatusCodes.Bad_SecurityModeInsufficient);
      }

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

      CertificateSlot certificateSlot = new CertificateSlot(certificateGroupId, certificateTypeId);

      var certificateChain = new ArrayList<X509Certificate>();

      try {
        certificateChain.add(CertificateUtil.decodeCertificate(certificate.bytesOrEmpty()));
      } catch (Exception e) {
        throw new UaException(StatusCodes.Bad_InvalidArgument, "certificate", e);
      }

      try {
        if (issuerCertificates != null) {
          for (ByteString bs : issuerCertificates) {
            certificateChain.add(CertificateUtil.decodeCertificate(bs.bytesOrEmpty()));
          }
        }
      } catch (Exception e) {
        throw new UaException(StatusCodes.Bad_InvalidArgument, "issuerCertificates", e);
      }

      KeyPair newKeyPair;
      PrivateKey regeneratedPrivateKey = null;
      if (privateKey == null || privateKey.isNullOrEmpty()) {
        PrivateKey key;
        if ((key = regeneratedPrivateKeys.get(certificateSlot)) != null) {
          // Use previously generated PrivateKey + new certificate PublicKey
          regeneratedPrivateKey = key;
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
        try {
          PrivateKey newPrivateKey =
              switch (privateKeyFormat) {
                case "PEM" -> readPemEncodedPrivateKey(privateKey);
                case "PFX" -> readPfxEncodedPrivateKey(privateKey);
                default ->
                    throw new UaException(StatusCodes.Bad_InvalidArgument, "privateKeyFormat");
              };

          newKeyPair = new KeyPair(certificateChain.get(0).getPublicKey(), newPrivateKey);
        } catch (Exception e) {
          throw new UaException(StatusCodes.Bad_InvalidArgument, "privateKey", e);
        }
      }

      try {
        certificateGroup.updateCertificate(
            certificateTypeId, newKeyPair, certificateChain.toArray(new X509Certificate[0]));
      } catch (Exception e) {
        throw new UaException(StatusCodes.Bad_InvalidArgument, "certificateTypeId", e);
      }

      if (regeneratedPrivateKey != null) {
        regeneratedPrivateKeys.remove(certificateSlot, regeneratedPrivateKey);
      }

      // TODO force existing clients to reconnect?

      applyChangesRequired.set(false);
    }

    private static PrivateKey readPemEncodedPrivateKey(ByteString privateKey) throws Exception {
      var reader =
          new PemReader(new InputStreamReader(new ByteArrayInputStream(privateKey.bytesOrEmpty())));

      byte[] encodedKey = reader.readPemObject().getContent();
      var keySpec = new PKCS8EncodedKeySpec(encodedKey);
      var keyFactory = KeyFactory.getInstance("RSA");

      return keyFactory.generatePrivate(keySpec);
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
   * @see <a href="https://reference.opcfoundation.org/specs/OPC-10000-12/7.10.9/">
   *     https://reference.opcfoundation.org/specs/OPC-10000-12/7.10.9/</a>
   */
  public static class ApplyChangesMethodImpl
      extends ServerConfigurationTypeNode.ApplyChangesMethod {

    public ApplyChangesMethodImpl(UaMethodNode node) {
      super(node);
    }

    @Override
    protected void invoke(InvocationContext context) throws UaException {
      Session session = context.getSession().orElseThrow();

      MessageSecurityMode securityMode = session.getSecurityConfiguration().getSecurityMode();

      if (securityMode != MessageSecurityMode.Sign
          && securityMode != MessageSecurityMode.SignAndEncrypt) {
        throw new UaException(StatusCodes.Bad_SecurityModeInsufficient);
      }

      // UpdateCertificate and TrustList CloseAndUpdate apply changes immediately and return
      // ApplyChangesRequired=false, so the demo never creates an active transaction.
      throw new UaException(StatusCodes.Bad_NothingToDo);
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

      if (session.getSecurityConfiguration().getSecurityMode()
          != MessageSecurityMode.SignAndEncrypt) {
        throw new UaException(StatusCodes.Bad_SecurityModeInsufficient);
      }

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

      MessageSecurityMode securityMode = session.getSecurityConfiguration().getSecurityMode();

      if (securityMode != MessageSecurityMode.Sign
          && securityMode != MessageSecurityMode.SignAndEncrypt) {
        throw new UaException(StatusCodes.Bad_SecurityModeInsufficient);
      }

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

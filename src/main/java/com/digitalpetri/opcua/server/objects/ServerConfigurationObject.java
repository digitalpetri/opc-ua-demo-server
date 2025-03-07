package com.digitalpetri.opcua.server.objects;

import static org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.Unsigned.uint;

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
import org.eclipse.milo.opcua.stack.core.security.CertificateQuarantine;
import org.eclipse.milo.opcua.stack.core.types.builtin.ByteString;
import org.eclipse.milo.opcua.stack.core.types.builtin.DataValue;
import org.eclipse.milo.opcua.stack.core.types.builtin.NodeId;
import org.eclipse.milo.opcua.stack.core.types.builtin.Variant;
import org.eclipse.milo.opcua.stack.core.types.enumerated.MessageSecurityMode;
import org.eclipse.milo.opcua.stack.core.util.CertificateUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Implementation behavior for an instance of the {@link ServerConfigurationType} Object. */
public class ServerConfigurationObject extends AbstractLifecycle {

  private final Logger logger = LoggerFactory.getLogger(getClass());

  /**
   * Temporary storage of PrivateKeys generated during CreateSigningRequest, for subsequent use in
   * UpdateCertificate.
   */
  private final Map<NodeId, PrivateKey> regeneratedPrivateKeys = new ConcurrentHashMap<>();

  private final OpcUaServer server;
  private final ServerConfigurationTypeNode serverConfigurationTypeNode;

  public ServerConfigurationObject(
      OpcUaServer server, ServerConfigurationTypeNode serverConfigurationTypeNode) {

    this.server = server;
    this.serverConfigurationTypeNode = serverConfigurationTypeNode;
  }

  @Override
  protected void onStartup() {
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

    serverConfigurationTypeNode.setServerCapabilities(new String[] {""});
    serverConfigurationTypeNode.setSupportedPrivateKeyFormats(new String[] {"PEM", "PFX"});
    serverConfigurationTypeNode.setMaxTrustListSize(uint(0));
    serverConfigurationTypeNode.setMulticastDnsEnabled(false);
    serverConfigurationTypeNode.setHasSecureElement(false);

    List<CertificateGroup> certificateGroups =
        server.getConfig().getCertificateManager().getCertificateGroups();

    Set<NodeId> supportedGroups =
        certificateGroups.stream()
            .map(CertificateGroup::getCertificateGroupId)
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

    certificateGroups.forEach(
        group -> {
          CertificateGroupTypeNode groupNode =
              server
                  .getAddressSpaceManager()
                  .getManagedNode(group.getCertificateGroupId())
                  .filter(node -> node instanceof CertificateGroupTypeNode)
                  .map(CertificateGroupTypeNode.class::cast)
                  .orElse(null);

          if (groupNode != null) {
            var trustListObject =
                new TrustListObject(
                    server.getConfig().getCertificateManager().getCertificateQuarantine(),
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
        });

    logger.debug("ServerConfigurationObject started: {}", serverConfigurationTypeNode.getNodeId());
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
   * @see <a href="https://reference.opcfoundation.org/GDS/v105/docs/7.10.4">
   *     https://reference.opcfoundation.org/GDS/v105/docs/7.10.4</a>
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
      if (privateKey == null || privateKey.isNullOrEmpty()) {
        PrivateKey key;
        if ((key = regeneratedPrivateKeys.remove(certificateTypeId)) != null) {
          // Use previously generated PrivateKey + new certificate PublicKey
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
   * @see <a href="https://reference.opcfoundation.org/GDS/v105/docs/7.10.6">
   *     https://reference.opcfoundation.org/GDS/v105/docs/7.10.6</a>
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

      // nothing else to do here; changes are applied immediately.
    }
  }

  /**
   * @see <a href="https://reference.opcfoundation.org/GDS/v105/docs/7.10.7">h
   *     ttps://reference.opcfoundation.org/GDS/v105/docs/7.10.7</a>
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
            keyPair = certificateGroup.getCertificateFactory().createKeyPair(certificateTypeId);

            regeneratedPrivateKeys.put(certificateTypeId, keyPair.getPrivate());
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
            certificateGroup
                .getCertificateFactory()
                .createSigningRequest(
                    certificateTypeId,
                    keyPair,
                    subject,
                    CertificateUtil.getSanUri(certificate)
                        .orElse(server.getConfig().getApplicationUri()),
                    CertificateUtil.getSanDnsNames(certificate),
                    CertificateUtil.getSanIpAddresses(certificate));

        certificateRequest.set(csr);
      } catch (Exception e) {
        throw new UaException(StatusCodes.Bad_UnexpectedError, e);
      }
    }
  }

  /**
   * @see <a href="https://reference.opcfoundation.org/GDS/v105/docs/7.10.9">
   *     https://reference.opcfoundation.org/GDS/v105/docs/7.10.9</a>
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

      CertificateQuarantine certificateQuarantine =
          server.getConfig().getCertificateManager().getCertificateQuarantine();

      for (X509Certificate certificate : certificateQuarantine.getRejectedCertificates()) {
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

package com.digitalpetri.opcua.server.objects;

import static java.util.Objects.requireNonNullElse;
import static org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.Unsigned.ubyte;
import static org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.Unsigned.uint;

import java.io.*;
import java.security.cert.*;
import java.util.ArrayList;
import java.util.Collection;
import org.bouncycastle.util.encoders.Hex;
import org.eclipse.milo.opcua.sdk.server.Session;
import org.eclipse.milo.opcua.sdk.server.methods.MethodInvocationHandler;
import org.eclipse.milo.opcua.sdk.server.methods.Out;
import org.eclipse.milo.opcua.sdk.server.model.objects.FileType;
import org.eclipse.milo.opcua.sdk.server.model.objects.TrustListType;
import org.eclipse.milo.opcua.sdk.server.model.objects.TrustListTypeNode;
import org.eclipse.milo.opcua.sdk.server.nodes.UaMethodNode;
import org.eclipse.milo.opcua.sdk.server.nodes.filters.AttributeFilter;
import org.eclipse.milo.opcua.sdk.server.nodes.filters.AttributeFilters;
import org.eclipse.milo.opcua.stack.core.StatusCodes;
import org.eclipse.milo.opcua.stack.core.UaException;
import org.eclipse.milo.opcua.stack.core.encoding.DefaultEncodingContext;
import org.eclipse.milo.opcua.stack.core.encoding.binary.OpcUaDefaultBinaryEncoding;
import org.eclipse.milo.opcua.stack.core.security.CertificateQuarantine;
import org.eclipse.milo.opcua.stack.core.security.TrustListManager;
import org.eclipse.milo.opcua.stack.core.types.builtin.ByteString;
import org.eclipse.milo.opcua.stack.core.types.builtin.DataValue;
import org.eclipse.milo.opcua.stack.core.types.builtin.DateTime;
import org.eclipse.milo.opcua.stack.core.types.builtin.Variant;
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.UByte;
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.UInteger;
import org.eclipse.milo.opcua.stack.core.types.enumerated.TrustListMasks;
import org.eclipse.milo.opcua.stack.core.types.structured.TrustListDataType;
import org.eclipse.milo.opcua.stack.core.util.CertificateUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Implementation behavior for an instance of the {@link TrustListType} Object.
 *
 * @see <a href="https://reference.opcfoundation.org/GDS/v105/docs/7.8.2.1">
 *     https://reference.opcfoundation.org/GDS/v105/docs/7.8.2.1</a>
 */
public class TrustListObject extends FileObject {

  private static final int MASK_TRUSTED_CERTIFICATES =
      TrustListMasks.TrustedCertificates.getValue();
  private static final int MASK_TRUSTED_CRLS = TrustListMasks.TrustedCrls.getValue();
  private static final int MASK_ISSUER_CERTIFICATES = TrustListMasks.IssuerCertificates.getValue();
  private static final int MASK_ISSUER_CRLS = TrustListMasks.IssuerCrls.getValue();
  private static final int MASK_ALL = TrustListMasks.All.getValue();

  private final Logger logger = LoggerFactory.getLogger(getClass());

  private final CertificateQuarantine certificateQuarantine;
  private final TrustListManager trustListManager;
  private final TrustListTypeNode trustListTypeNode;

  public TrustListObject(
      CertificateQuarantine certificateQuarantine,
      TrustListManager trustListManager,
      TrustListTypeNode fileNode) {

    super(fileNode, () -> newTemporaryTrustListFile(trustListManager, MASK_ALL));

    this.certificateQuarantine = certificateQuarantine;
    this.trustListManager = trustListManager;
    this.trustListTypeNode = fileNode;
  }

  @Override
  protected void onStartup() {
    super.onStartup();

    { // OpenMethod
      UaMethodNode methodNode = trustListTypeNode.getOpenMethodNode();
      methodNode.getFilterChain().addLast(new SecurityAdminFilter());
      methodNode.setInvocationHandler(new OpenMethodImpl(methodNode));
    }

    { // OpenWithMasksMethod
      UaMethodNode methodNode = trustListTypeNode.getOpenWithMasksMethodNode();
      methodNode.getFilterChain().addLast(new SecurityAdminFilter());
      methodNode.setInvocationHandler(new OpenWithMasksMethodImpl(methodNode));
    }

    { // CloseAndUpdateMethod
      UaMethodNode methodNode = trustListTypeNode.getCloseAndUpdateMethodNode();
      methodNode.getFilterChain().addLast(new SecurityAdminFilter());
      methodNode.setInvocationHandler(new CloseAndUpdateMethodImpl(methodNode));
    }

    { // AddCertificateMethod
      UaMethodNode methodNode = trustListTypeNode.getAddCertificateMethodNode();
      methodNode.getFilterChain().addLast(new SecurityAdminFilter());
      methodNode.setInvocationHandler(new AddCertificateMethodImpl(methodNode));
    }

    { // RemoveCertificateMethod
      UaMethodNode methodNode = trustListTypeNode.getRemoveCertificateMethodNode();
      methodNode.getFilterChain().addLast(new SecurityAdminFilter());
      methodNode.setInvocationHandler(new RemoveCertificateMethodImpl(methodNode));
    }

    trustListTypeNode
        .getLastUpdateTimeNode()
        .getFilterChain()
        .addLast(
            AttributeFilters.getValue(
                ctx -> {
                  DateTime lastUpdateTime = trustListManager.getLastUpdateTime();

                  return new DataValue(new Variant(lastUpdateTime));
                }));

    logger.debug("TrustListObject started: {}", trustListTypeNode.getNodeId());
  }

  @Override
  protected void onShutdown() {
    trustListTypeNode
        .getOpenMethodNode()
        .setInvocationHandler(MethodInvocationHandler.NOT_IMPLEMENTED);
    trustListTypeNode
        .getOpenWithMasksMethodNode()
        .setInvocationHandler(MethodInvocationHandler.NOT_IMPLEMENTED);
    trustListTypeNode
        .getCloseAndUpdateMethodNode()
        .setInvocationHandler(MethodInvocationHandler.NOT_IMPLEMENTED);
    trustListTypeNode
        .getAddCertificateMethodNode()
        .setInvocationHandler(MethodInvocationHandler.NOT_IMPLEMENTED);
    trustListTypeNode
        .getRemoveCertificateMethodNode()
        .setInvocationHandler(MethodInvocationHandler.NOT_IMPLEMENTED);

    logger.debug("TrustListObject stopped: {}", trustListTypeNode.getNodeId());

    super.onShutdown();
  }

  @Override
  protected FileType.OpenMethod newOpenMethod(UaMethodNode methodNode) {
    return new OpenMethodImpl(methodNode);
  }

  @Override
  protected AttributeFilter newSizeAttributeFilter() {
    // creating a temporary TrustList file just to calculate the size is expensive, so let's just
    // tell the client don't support it.
    return AttributeFilters.getValue(ctx -> new DataValue(StatusCodes.Bad_NotSupported));
  }

  /**
   * Restricts the implementation of {@link FileObject.OpenMethodImpl} to only allow {@link
   * #MASK_READ} or {@link #MASK_WRITE} + {@link #MASK_ERASE_EXISTING}.
   */
  class OpenMethodImpl extends FileObject.OpenMethodImpl {

    public OpenMethodImpl(UaMethodNode node) {
      super(node);
    }

    @Override
    protected void invoke(InvocationContext context, UByte mode, Out<UInteger> fileHandle)
        throws UaException {

      if (mode.intValue() != MASK_READ && mode.intValue() != (MASK_WRITE | MASK_ERASE_EXISTING)) {
        throw new UaException(
            StatusCodes.Bad_InvalidArgument, "mode must be Read or Write+EraseExisting");
      }

      super.invoke(context, mode, fileHandle);
    }
  }

  /**
   * Allows a Client to read only a portion of the Trust List.
   *
   * <p>This Method can only be used to read.
   *
   * @see <a href="https://reference.opcfoundation.org/GDS/v105/docs/7.8.2.2">
   *     https://reference.opcfoundation.org/GDS/v105/docs/7.8.2.2</a>
   */
  class OpenWithMasksMethodImpl extends TrustListType.OpenWithMasksMethod {

    public OpenWithMasksMethodImpl(UaMethodNode node) {
      super(node);
    }

    @Override
    protected void invoke(InvocationContext context, UInteger masks, Out<UInteger> fileHandle)
        throws UaException {

      Session session = context.getSession().orElseThrow();

      // TODO For PullManagement, this Method shall be called from an authenticated SecureChannel
      //  and from a Client that has access to the CertificateAuthorityAdmin Role, the
      //  ApplicationSelfAdmin Privilege, or the ApplicationAdmin Privilege.

      // TODO For PushManagement, this Method shall be called from an authenticated SecureChannel
      //  and from a Client that has access to the SecurityAdmin Role.

      try {
        File file = newTemporaryTrustListFile(trustListManager, masks.intValue());
        file.deleteOnExit();

        var handle = new FileHandle(ubyte(MASK_READ), new RandomAccessFile(file, "r"));
        handles.put(session.getSessionId(), handle.handle, handle);

        fileHandle.set(handle.handle);
      } catch (IOException e) {
        throw new UaException(StatusCodes.Bad_UnexpectedError, e);
      }
    }
  }

  /**
   * @see <a href="https://reference.opcfoundation.org/GDS/v105/docs/7.8.2.3">
   *     https://reference.opcfoundation.org/GDS/v105/docs/7.8.2.3</a>
   */
  class CloseAndUpdateMethodImpl extends TrustListType.CloseAndUpdateMethod {

    public CloseAndUpdateMethodImpl(UaMethodNode node) {
      super(node);
    }

    @Override
    protected void invoke(
        InvocationContext context, UInteger fileHandle, Out<Boolean> applyChangesRequired)
        throws UaException {

      Session session = context.getSession().orElseThrow();

      FileHandle handle = handles.remove(session.getSessionId(), fileHandle);

      if (handle == null) {
        throw new UaException(StatusCodes.Bad_InvalidArgument);
      }

      try (RandomAccessFile file = handle.file) {
        if ((MASK_WRITE & handle.mode.intValue()) != MASK_WRITE) {
          throw new UaException(StatusCodes.Bad_InvalidState);
        }

        file.seek(0L);
        byte[] bs = new byte[(int) file.length()];
        file.readFully(bs);

        var decoded =
            OpcUaDefaultBinaryEncoding.getInstance()
                .decode(
                    DefaultEncodingContext.INSTANCE,
                    ByteString.of(bs),
                    TrustListDataType.BINARY_ENCODING_ID);

        if (decoded instanceof TrustListDataType trustList) {
          int specifiedLists = trustList.getSpecifiedLists().intValue();

          if ((specifiedLists & MASK_TRUSTED_CERTIFICATES) != 0) {
            updateTrustedCertificates(trustList, trustListManager);
          }

          if ((specifiedLists & MASK_TRUSTED_CRLS) != 0) {
            updateTrustedCrls(trustList, trustListManager);
          }

          if ((specifiedLists & MASK_ISSUER_CERTIFICATES) != 0) {
            updateIssuerCertificates(trustList, trustListManager);
          }

          if ((specifiedLists & MASK_ISSUER_CRLS) != 0) {
            updateIssuerCrls(trustList, trustListManager);
          }

          trustListTypeNode.setLastUpdateTime(DateTime.now());

          // TODO force existing clients to reconnect?

          applyChangesRequired.set(false);
        } else {
          throw new UaException(StatusCodes.Bad_InvalidArgument);
        }
      } catch (IOException e) {
        throw new UaException(StatusCodes.Bad_UnexpectedError, e);
      }
    }

    private static void updateTrustedCertificates(
        TrustListDataType trustList, TrustListManager trustListManager) throws UaException {

      var trustedCertificates = new ArrayList<X509Certificate>();

      for (ByteString certificateBytes :
          requireNonNullElse(trustList.getTrustedCertificates(), new ByteString[0])) {

        try {
          X509Certificate certificate =
              CertificateUtil.decodeCertificate(certificateBytes.bytesOrEmpty());
          trustedCertificates.add(certificate);
        } catch (UaException e) {
          throw new UaException(StatusCodes.Bad_InvalidArgument, e);
        }
      }

      trustListManager.setTrustedCertificates(trustedCertificates);
    }

    private static void updateTrustedCrls(
        TrustListDataType trustList, TrustListManager trustListManager) throws UaException {

      try {
        var factory = CertificateFactory.getInstance("X.509");

        var trustedCrls = new ArrayList<X509CRL>();

        for (ByteString crlBytes :
            requireNonNullElse(trustList.getTrustedCrls(), new ByteString[0])) {

          try {
            Collection<? extends CRL> crls =
                factory.generateCRLs(new ByteArrayInputStream(crlBytes.bytesOrEmpty()));
            crls.forEach(
                crl -> {
                  if (crl instanceof X509CRL x509CRL) {
                    trustedCrls.add(x509CRL);
                  }
                });
          } catch (CRLException e) {
            throw new UaException(StatusCodes.Bad_InvalidArgument, e);
          }
        }

        trustListManager.setTrustedCrls(trustedCrls);
      } catch (CertificateException e) {
        throw new UaException(StatusCodes.Bad_UnexpectedError, e);
      }
    }

    private static void updateIssuerCertificates(
        TrustListDataType trustList, TrustListManager trustListManager) throws UaException {

      var issuerCertificates = new ArrayList<X509Certificate>();

      for (ByteString certificateBytes :
          requireNonNullElse(trustList.getIssuerCertificates(), new ByteString[0])) {

        try {
          X509Certificate certificate =
              CertificateUtil.decodeCertificate(certificateBytes.bytesOrEmpty());
          issuerCertificates.add(certificate);
        } catch (UaException e) {
          throw new UaException(StatusCodes.Bad_InvalidArgument, e);
        }
      }

      trustListManager.setIssuerCertificates(issuerCertificates);
    }

    private static void updateIssuerCrls(
        TrustListDataType trustList, TrustListManager trustListManager) throws UaException {

      try {
        var factory = CertificateFactory.getInstance("X.509");

        var issuerCrls = new ArrayList<X509CRL>();

        for (ByteString crlBytes :
            requireNonNullElse(trustList.getIssuerCrls(), new ByteString[0])) {

          try {
            Collection<? extends CRL> crls =
                factory.generateCRLs(new ByteArrayInputStream(crlBytes.bytesOrEmpty()));
            crls.forEach(
                crl -> {
                  if (crl instanceof X509CRL x509CRL) {
                    issuerCrls.add(x509CRL);
                  }
                });
          } catch (CRLException e) {
            throw new UaException(StatusCodes.Bad_InvalidArgument, e);
          }
        }

        trustListManager.setIssuerCrls(issuerCrls);
      } catch (CertificateException e) {
        throw new UaException(StatusCodes.Bad_UnexpectedError, e);
      }
    }
  }

  /**
   * @see <a href="https://reference.opcfoundation.org/GDS/v105/docs/7.8.2.4">
   *     https://reference.opcfoundation.org/GDS/v105/docs/7.8.2.4</a>
   */
  class AddCertificateMethodImpl extends TrustListType.AddCertificateMethod {

    public AddCertificateMethodImpl(UaMethodNode node) {
      super(node);
    }

    @Override
    protected void invoke(
        InvocationContext context, ByteString certificate, Boolean isTrustedCertificate)
        throws UaException {

      try {
        X509Certificate x509Certificate =
            CertificateUtil.decodeCertificate(certificate.bytesOrEmpty());

        if (isTrustedCertificate) {
          trustListManager.addTrustedCertificate(x509Certificate);
        } else {
          trustListManager.addIssuerCertificate(x509Certificate);
        }

        certificateQuarantine.removeRejectedCertificate(x509Certificate);
      } catch (Exception e) {
        throw new UaException(StatusCodes.Bad_InvalidArgument, e);
      }
    }
  }

  /**
   * @see <a href="https://reference.opcfoundation.org/GDS/v105/docs/7.8.2.5">
   *     https://reference.opcfoundation.org/GDS/v105/docs/7.8.2.5</a>
   */
  class RemoveCertificateMethodImpl extends TrustListType.RemoveCertificateMethod {

    public RemoveCertificateMethodImpl(UaMethodNode node) {
      super(node);
    }

    @Override
    protected void invoke(
        InvocationContext context, String thumbprint, Boolean isTrustedCertificate)
        throws UaException {

      ByteString thumbprintBytes = ByteString.of(Hex.decode(thumbprint));

      if (isTrustedCertificate) {
        if (!trustListManager.removeTrustedCertificate(thumbprintBytes)) {
          throw new UaException(StatusCodes.Bad_InvalidArgument);
        }
      } else {
        if (!trustListManager.removeIssuerCertificate(thumbprintBytes)) {
          throw new UaException(StatusCodes.Bad_InvalidArgument);
        }
      }
    }
  }

  private static File newTemporaryTrustListFile(TrustListManager trustListManager, int masks)
      throws IOException {

    var trustedCertificates = new ArrayList<ByteString>();
    if ((masks & MASK_TRUSTED_CERTIFICATES) != 0) {
      for (X509Certificate certificate : trustListManager.getTrustedCertificates()) {
        try {
          trustedCertificates.add(ByteString.of(certificate.getEncoded()));
        } catch (CertificateEncodingException e) {
          throw new IOException(e);
        }
      }
    }

    var trustedCrls = new ArrayList<ByteString>();
    if ((masks & MASK_TRUSTED_CRLS) != 0) {
      for (X509CRL crl : trustListManager.getTrustedCrls()) {
        try {
          trustedCrls.add(ByteString.of(crl.getEncoded()));
        } catch (CRLException e) {
          throw new IOException(e);
        }
      }
    }

    var issuerCertificates = new ArrayList<ByteString>();
    if ((masks & MASK_ISSUER_CERTIFICATES) != 0) {
      for (X509Certificate certificate : trustListManager.getIssuerCertificates()) {
        try {
          issuerCertificates.add(ByteString.of(certificate.getEncoded()));
        } catch (CertificateEncodingException e) {
          throw new IOException(e);
        }
      }
    }

    var issuerCrls = new ArrayList<ByteString>();
    if ((masks & MASK_ISSUER_CRLS) != 0) {
      for (X509CRL crl : trustListManager.getIssuerCrls()) {
        try {
          issuerCrls.add(ByteString.of(crl.getEncoded()));
        } catch (CRLException e) {
          throw new IOException(e);
        }
      }
    }

    var trustList =
        new TrustListDataType(
            uint(masks),
            trustedCertificates.toArray(new ByteString[0]),
            trustedCrls.toArray(new ByteString[0]),
            issuerCertificates.toArray(new ByteString[0]),
            issuerCrls.toArray(new ByteString[0]));

    ByteString encoded =
        (ByteString)
            OpcUaDefaultBinaryEncoding.getInstance()
                .encode(
                    DefaultEncodingContext.INSTANCE,
                    trustList,
                    TrustListDataType.BINARY_ENCODING_ID);

    File file = File.createTempFile("TrustListDataType", ".bin");

    try (FileOutputStream fos = new FileOutputStream(file)) {
      fos.write(encoded.bytesOrEmpty());
    }

    return file;
  }
}

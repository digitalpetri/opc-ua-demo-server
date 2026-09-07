package com.digitalpetri.opcua.server.objects;

import static java.util.Objects.requireNonNullElse;
import static org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.Unsigned.ubyte;
import static org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.Unsigned.uint;

import com.digitalpetri.opcua.server.objects.PushTransaction.TrustListUpdate;
import java.io.*;
import java.security.cert.*;
import java.util.ArrayList;
import java.util.List;
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
import org.eclipse.milo.opcua.stack.core.security.CertificateQuarantine;
import org.eclipse.milo.opcua.stack.core.security.TrustListManager;
import org.eclipse.milo.opcua.stack.core.types.UaStructuredType;
import org.eclipse.milo.opcua.stack.core.types.builtin.ByteString;
import org.eclipse.milo.opcua.stack.core.types.builtin.DataValue;
import org.eclipse.milo.opcua.stack.core.types.builtin.DateTime;
import org.eclipse.milo.opcua.stack.core.types.builtin.ExtensionObject;
import org.eclipse.milo.opcua.stack.core.types.builtin.NodeId;
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
 * <p>Writes follow the PushManagement transaction model of OPC 10000-12 §7.10.2: opening the
 * TrustList for writing starts or continues the calling Session's transaction, CloseAndUpdate
 * stages the decoded contents, and nothing reaches the {@link TrustListManager} until the
 * ServerConfiguration ApplyChanges Method applies the transaction.
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
  private final PushTransactionManager transactions;

  public TrustListObject(
      CertificateQuarantine certificateQuarantine,
      TrustListManager trustListManager,
      TrustListTypeNode fileNode,
      PushTransactionManager transactions) {

    super(fileNode, () -> newTemporaryTrustListFile(trustListManager, MASK_ALL));

    this.certificateQuarantine = certificateQuarantine;
    this.trustListManager = trustListManager;
    this.trustListTypeNode = fileNode;
    this.transactions = transactions;
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

  /**
   * Replace the lists selected by {@code contents} in the {@link TrustListManager}.
   *
   * <p>Called when the transaction that staged {@code contents} is applied.
   *
   * @param contents the decoded contents staged by CloseAndUpdate.
   */
  void apply(Contents contents) {
    if ((contents.specifiedLists() & MASK_TRUSTED_CERTIFICATES) != 0) {
      trustListManager.setTrustedCertificates(contents.trustedCertificates());
    }

    if ((contents.specifiedLists() & MASK_TRUSTED_CRLS) != 0) {
      trustListManager.setTrustedCrls(contents.trustedCrls());
    }

    if ((contents.specifiedLists() & MASK_ISSUER_CERTIFICATES) != 0) {
      trustListManager.setIssuerCertificates(contents.issuerCertificates());
    }

    if ((contents.specifiedLists() & MASK_ISSUER_CRLS) != 0) {
      trustListManager.setIssuerCrls(contents.issuerCrls());
    }

    trustListTypeNode.setLastUpdateTime(DateTime.now());

    logger.info("TrustList {} updated", trustListTypeNode.getNodeId());
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
   *
   * <p>OPC 10000-12 §7.8.2.2: opening with the Write bit set starts or continues the calling
   * Session's transaction, and fails with Bad_TransactionPending while another Session's
   * transaction is active.
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

      boolean write = (mode.intValue() & MASK_WRITE) == MASK_WRITE;
      NodeId sessionId = context.getSession().orElseThrow().getSessionId();

      if (write) {
        transactions.requireNotPendingForOthers(sessionId);
      }

      super.invoke(context, mode, fileHandle);

      // Only a successful Open starts the transaction; a rejected one must leave nothing behind.
      if (write) {
        try {
          transactions.beginOrContinue(sessionId);
        } catch (UaException e) {
          closeHandles(sessionId, true);
          throw e;
        }
      }
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
   * Closes the file handle, decodes and validates the written TrustList, and stages it in the
   * calling Session's transaction. The TrustListManager is not touched until ApplyChanges.
   *
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

        NodeId encodingId =
            TrustListDataType.BINARY_ENCODING_ID
                .toNodeId(context.getServer().getNamespaceTable())
                .orElseThrow();

        ExtensionObject xo = ExtensionObject.of(ByteString.of(bs), encodingId);

        UaStructuredType decoded = xo.decode(DefaultEncodingContext.INSTANCE);

        if (decoded instanceof TrustListDataType trustList) {
          Contents contents = Contents.decode(trustList);

          PushTransaction transaction = transactions.beginOrContinue(session.getSessionId());
          transaction.stage(
              new TrustListUpdate(trustListTypeNode.getNodeId(), TrustListObject.this, contents));

          applyChangesRequired.set(true);
        } else {
          throw new UaException(StatusCodes.Bad_InvalidArgument);
        }
      } catch (IOException e) {
        throw new UaException(StatusCodes.Bad_UnexpectedError, e);
      }
    }
  }

  /**
   * Applies immediately when no transaction is active. OPC 10000-12 §7.8.2.6 returns
   * Bad_TransactionPending while a transaction has started and ApplyChanges or CancelChanges has
   * not been called.
   *
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

      requireNoActiveTransaction();

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
   * Applies immediately when no transaction is active. OPC 10000-12 §7.8.2.7 returns
   * Bad_TransactionPending while a transaction has started and ApplyChanges or CancelChanges has
   * not been called.
   *
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

      requireNoActiveTransaction();

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

  private void requireNoActiveTransaction() throws UaException {
    if (transactions.isActive()) {
      throw new UaException(
          StatusCodes.Bad_TransactionPending,
          "transaction has started; call ApplyChanges or CancelChanges first");
    }
  }

  /**
   * The decoded, validated contents of a written {@link TrustListDataType}.
   *
   * @param specifiedLists the {@link TrustListMasks} bits selecting which lists to replace.
   * @param trustedCertificates the trusted certificates, empty unless selected.
   * @param trustedCrls the trusted CRLs, empty unless selected.
   * @param issuerCertificates the issuer certificates, empty unless selected.
   * @param issuerCrls the issuer CRLs, empty unless selected.
   */
  public record Contents(
      int specifiedLists,
      List<X509Certificate> trustedCertificates,
      List<X509CRL> trustedCrls,
      List<X509Certificate> issuerCertificates,
      List<X509CRL> issuerCrls) {

    /**
     * Decode every list selected by {@code SpecifiedLists} so that malformed input is rejected in
     * CloseAndUpdate rather than discovered when the transaction is applied.
     *
     * @param trustList the written TrustList.
     * @return the decoded contents.
     * @throws UaException with Bad_InvalidArgument if a certificate or CRL cannot be decoded.
     */
    static Contents decode(TrustListDataType trustList) throws UaException {
      int specifiedLists = trustList.getSpecifiedLists().intValue();

      List<X509Certificate> trustedCertificates = List.of();
      List<X509CRL> trustedCrls = List.of();
      List<X509Certificate> issuerCertificates = List.of();
      List<X509CRL> issuerCrls = List.of();

      if ((specifiedLists & MASK_TRUSTED_CERTIFICATES) != 0) {
        trustedCertificates = decodeCertificates(trustList.getTrustedCertificates());
      }

      if ((specifiedLists & MASK_TRUSTED_CRLS) != 0) {
        trustedCrls = decodeCrls(trustList.getTrustedCrls());
      }

      if ((specifiedLists & MASK_ISSUER_CERTIFICATES) != 0) {
        issuerCertificates = decodeCertificates(trustList.getIssuerCertificates());
      }

      if ((specifiedLists & MASK_ISSUER_CRLS) != 0) {
        issuerCrls = decodeCrls(trustList.getIssuerCrls());
      }

      return new Contents(
          specifiedLists, trustedCertificates, trustedCrls, issuerCertificates, issuerCrls);
    }

    private static List<X509Certificate> decodeCertificates(ByteString[] encodedCertificates)
        throws UaException {

      var certificates = new ArrayList<X509Certificate>();

      for (ByteString certificateBytes :
          requireNonNullElse(encodedCertificates, new ByteString[0])) {
        try {
          certificates.add(CertificateUtil.decodeCertificate(certificateBytes.bytesOrEmpty()));
        } catch (UaException e) {
          throw new UaException(StatusCodes.Bad_InvalidArgument, e);
        }
      }

      return List.copyOf(certificates);
    }

    private static List<X509CRL> decodeCrls(ByteString[] encodedCrls) throws UaException {
      var crls = new ArrayList<X509CRL>();

      for (ByteString crlBytes : requireNonNullElse(encodedCrls, new ByteString[0])) {
        try {
          crls.addAll(CertificateUtil.decodeCrls(crlBytes.bytesOrEmpty()));
        } catch (UaException e) {
          throw new UaException(StatusCodes.Bad_InvalidArgument, e);
        }
      }

      return List.copyOf(crls);
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

    ExtensionObject encoded = ExtensionObject.encode(DefaultEncodingContext.INSTANCE, trustList);

    ByteString encodedBytes = (ByteString) encoded.getBody();

    File file = File.createTempFile("TrustListDataType", ".bin");

    try (FileOutputStream fos = new FileOutputStream(file)) {
      fos.write(encodedBytes.bytesOrEmpty());
    }

    return file;
  }
}

package com.digitalpetri.opcua.server.objects;

import static org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.Unsigned.ubyte;
import static org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.Unsigned.uint;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.digitalpetri.opcua.server.DemoCertificateFactory;
import com.digitalpetri.opcua.server.OpcUaDemoServer;
import com.digitalpetri.opcua.server.OpcUaTestClient;
import com.digitalpetri.opcua.server.OpcUaTestServerBuilder;
import com.digitalpetri.opcua.server.TestCertificateAuthority;
import com.typesafe.config.ConfigFactory;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.eclipse.milo.opcua.sdk.client.DiscoveryClient;
import org.eclipse.milo.opcua.sdk.client.OpcUaClient;
import org.eclipse.milo.opcua.sdk.client.identity.UsernameProvider;
import org.eclipse.milo.opcua.sdk.server.OpcUaServer;
import org.eclipse.milo.opcua.sdk.server.Session;
import org.eclipse.milo.opcua.sdk.server.SessionListener;
import org.eclipse.milo.opcua.sdk.server.model.objects.ServerConfigurationTypeNode;
import org.eclipse.milo.opcua.sdk.server.model.objects.TransactionDiagnosticsTypeNode;
import org.eclipse.milo.opcua.sdk.server.nodes.UaVariableNode;
import org.eclipse.milo.opcua.stack.core.NodeIds;
import org.eclipse.milo.opcua.stack.core.StatusCodes;
import org.eclipse.milo.opcua.stack.core.encoding.DefaultEncodingContext;
import org.eclipse.milo.opcua.stack.core.security.CertificateGroup;
import org.eclipse.milo.opcua.stack.core.security.CertificateValidator;
import org.eclipse.milo.opcua.stack.core.security.DefaultCertificateGroup;
import org.eclipse.milo.opcua.stack.core.security.MemoryCertificateQuarantine;
import org.eclipse.milo.opcua.stack.core.security.MemoryCertificateStore;
import org.eclipse.milo.opcua.stack.core.security.MemoryTrustListManager;
import org.eclipse.milo.opcua.stack.core.security.SecurityPolicy;
import org.eclipse.milo.opcua.stack.core.security.TrustListManager;
import org.eclipse.milo.opcua.stack.core.types.builtin.ByteString;
import org.eclipse.milo.opcua.stack.core.types.builtin.DataValue;
import org.eclipse.milo.opcua.stack.core.types.builtin.ExtensionObject;
import org.eclipse.milo.opcua.stack.core.types.builtin.NodeId;
import org.eclipse.milo.opcua.stack.core.types.builtin.StatusCode;
import org.eclipse.milo.opcua.stack.core.types.builtin.Variant;
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.UInteger;
import org.eclipse.milo.opcua.stack.core.types.enumerated.ApplicationType;
import org.eclipse.milo.opcua.stack.core.types.enumerated.MessageSecurityMode;
import org.eclipse.milo.opcua.stack.core.types.enumerated.TimestampsToReturn;
import org.eclipse.milo.opcua.stack.core.types.enumerated.TrustListMasks;
import org.eclipse.milo.opcua.stack.core.types.structured.CallMethodRequest;
import org.eclipse.milo.opcua.stack.core.types.structured.CallMethodResult;
import org.eclipse.milo.opcua.stack.core.types.structured.EndpointDescription;
import org.eclipse.milo.opcua.stack.core.types.structured.TrustListDataType;
import org.eclipse.milo.opcua.stack.core.util.CertificateUtil;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ServerConfigurationObjectIT {

  private static final String CLIENT_APPLICATION_URI = "urn:eclipse:milo:test:push-client";

  private static final NodeId DEFAULT_APPLICATION_GROUP =
      NodeIds.ServerConfiguration_CertificateGroups_DefaultApplicationGroup;
  private static final NodeId TRUST_LIST =
      NodeIds.ServerConfiguration_CertificateGroups_DefaultApplicationGroup_TrustList;

  private static final Duration TIMEOUT = Duration.ofSeconds(10);

  private static CertificateGroup clientCertificateGroup;
  private static TestCertificateAuthority certificateAuthority;

  private OpcUaDemoServer demoServer;
  private OpcUaServer server;
  private ServerConfigurationTypeNode serverConfiguration;
  private final List<OpcUaClient> clients = new ArrayList<>();

  @BeforeAll
  static void createClientIdentity() throws Exception {
    var certificateGroup =
        new DefaultCertificateGroup(
            new MemoryTrustListManager(),
            new MemoryCertificateStore(),
            new MemoryCertificateQuarantine(),
            new CertificateValidator.InsecureCertificateValidator(),
            List.of(
                NodeIds.RsaSha256ApplicationCertificateType,
                NodeIds.EccNistP256ApplicationCertificateType));

    new DemoCertificateFactory(CLIENT_APPLICATION_URI, () -> Set.of("localhost"))
        .createMissingCertificates(certificateGroup);

    clientCertificateGroup = certificateGroup;
    certificateAuthority = TestCertificateAuthority.create("Test GDS CA");
  }

  @BeforeEach
  void setUp(@TempDir Path tempDir) throws Exception {
    var config =
        ConfigFactory.parseMap(
            Map.of(
                "gds-push-enabled",
                true,
                "security-policy-list",
                List.of("None", "Basic256Sha256", "ECC_nistP256_AesGcm"),
                "security-mode-list",
                List.of("None", "SignAndEncrypt"),
                "trust-all-certificates",
                true));

    demoServer = OpcUaTestServerBuilder.builder().withDataDir(tempDir).withConfig(config).build();
    demoServer.startup();
    server = demoServer.getServer();

    serverConfiguration =
        assertInstanceOf(
            ServerConfigurationTypeNode.class,
            server
                .getAddressSpaceManager()
                .getManagedNode(NodeIds.ServerConfiguration)
                .orElseThrow());
  }

  @AfterEach
  void tearDown() {
    for (OpcUaClient client : clients) {
      try {
        client.disconnect();
      } catch (Exception ignored) {
        // The session may already have been closed by the server.
      }
    }
    clients.clear();

    if (demoServer != null) {
      demoServer.shutdown();
    }
  }

  // Part 12 §7.10.3: optional application identity values must describe this application when
  // present, ServerCapabilities must use Annex D identifiers supported by the application, and
  // SupportsTransactions must be TRUE because UpdateCertificate returns ApplyChangesRequired=TRUE.
  @Test
  void publishesTruthfulApplicationIdentityAndCapabilities() {
    assertEquals(server.getConfig().getApplicationUri(), serverConfiguration.getApplicationUri());
    assertEquals(server.getConfig().getProductUri(), serverConfiguration.getProductUri());
    assertEquals(ApplicationType.Server, serverConfiguration.getApplicationType());
    assertArrayEquals(new String[] {"DA", "AC"}, serverConfiguration.getServerCapabilities());
    assertEquals(Boolean.TRUE, serverConfiguration.getSupportsTransactions());
  }

  // Part 12 §7.10.3 marks these components optional; exposing executable or readable nodes
  // without their reset or configuration-file behavior is misleading.
  @Test
  void unsupportedOptionalComponentsAreNotExposed() {
    assertNodeRemoved(NodeIds.ServerConfiguration_ResetToServerDefaults);
    assertNodeRemoved(NodeIds.ServerConfiguration_ConfigurationFile);
  }

  // Part 12 §7.10.2: a Server that implements the transaction model must support CancelChanges,
  // and TransactionDiagnostics is how it reports the outcome of the last transaction.
  @Test
  void transactionComponentsAreExposed() {
    assertNodePresent(NodeIds.ServerConfiguration_CancelChanges);
    assertNodePresent(NodeIds.ServerConfiguration_TransactionDiagnostics);
  }

  private void assertNodeRemoved(NodeId nodeId) {
    assertTrue(
        server.getAddressSpaceManager().getManagedNode(nodeId).isEmpty(),
        () -> nodeId + " should have been removed");
  }

  private void assertNodePresent(NodeId nodeId) {
    assertTrue(
        server.getAddressSpaceManager().getManagedNode(nodeId).isPresent(),
        () -> nodeId + " should be present");
  }

  @Nested
  class Transactions {

    // Part 12 §7.10.9: ApplyChanges without an active transaction returns Bad_NothingToDo. A GDS
    // push client that tolerates this code must not have anything applied behind its back.
    @Test
    void applyChangesWithoutTransactionReturnsBadNothingToDo() throws Exception {
      OpcUaClient admin = connectSecurityAdmin(SecurityPolicy.Basic256Sha256);

      assertStatus(
          StatusCodes.Bad_NothingToDo,
          call(admin, NodeIds.ServerConfiguration, NodeIds.ServerConfiguration_ApplyChanges));
    }

    // Part 12 §7.10.11: CancelChanges without an active transaction returns Bad_NothingToDo.
    @Test
    void cancelChangesWithoutTransactionReturnsBadNothingToDo() throws Exception {
      OpcUaClient admin = connectSecurityAdmin(SecurityPolicy.Basic256Sha256);

      assertStatus(
          StatusCodes.Bad_NothingToDo,
          call(admin, NodeIds.ServerConfiguration, NodeIds.ServerConfiguration_CancelChanges));
    }

    /**
     * The GDS push provisioning scenario, end to end: CreateSigningRequest and UpdateCertificate
     * for the RSA and ECC slots, then ApplyChanges. Part 12 §7.10.2 requires the new certificates
     * to stay pending until ApplyChanges, and §7.10.5 requires them to be in use afterwards, which
     * means GetEndpoints must advertise them and new SecureChannels must be established with them.
     * Advertising a certificate the server no longer holds, or holding one it does not advertise,
     * makes every secure connection fail until a restart.
     */
    @Test
    void certificatesReplacedThroughApplyChangesAreAdvertisedAndUsedByNewChannels()
        throws Exception {

      X509Certificate oldRsa = installedCertificate(NodeIds.RsaSha256ApplicationCertificateType);
      X509Certificate oldEcc = installedCertificate(NodeIds.EccNistP256ApplicationCertificateType);

      OpcUaClient admin = connectSecurityAdmin(SecurityPolicy.Basic256Sha256);
      NodeId adminSessionId = admin.getSession().getSessionId();

      X509Certificate newRsa =
          provisionCertificate(admin, NodeIds.RsaSha256ApplicationCertificateType);
      X509Certificate newEcc =
          provisionCertificate(admin, NodeIds.EccNistP256ApplicationCertificateType);

      assertEquals(
          oldRsa,
          installedCertificate(NodeIds.RsaSha256ApplicationCertificateType),
          "staged RSA certificate must not reach the store before ApplyChanges");
      assertEquals(
          oldEcc,
          installedCertificate(NodeIds.EccNistP256ApplicationCertificateType),
          "staged ECC certificate must not reach the store before ApplyChanges");
      assertEquals(
          oldRsa,
          advertisedCertificate(SecurityPolicy.Basic256Sha256),
          "endpoints must keep advertising the old certificate before ApplyChanges");

      CompletableFuture<Void> adminClosed = sessionClosed(adminSessionId);

      assertGood(
          call(admin, NodeIds.ServerConfiguration, NodeIds.ServerConfiguration_ApplyChanges));

      assertEquals(newRsa, installedCertificate(NodeIds.RsaSha256ApplicationCertificateType));
      assertEquals(newEcc, installedCertificate(NodeIds.EccNistP256ApplicationCertificateType));

      assertEquals(
          newRsa,
          advertisedCertificate(SecurityPolicy.Basic256Sha256),
          "GetEndpoints must advertise the new RSA certificate");
      assertEquals(
          newEcc,
          advertisedCertificate(SecurityPolicy.ECC_nistP256_AesGcm),
          "GetEndpoints must advertise the new ECC certificate");
      assertEquals(
          newRsa,
          advertisedCertificate(SecurityPolicy.None),
          "unsecured endpoints must advertise the new RSA certificate for encrypted user tokens");

      assertNewChannelUsesCertificate(SecurityPolicy.Basic256Sha256, newRsa);
      assertNewChannelUsesCertificate(SecurityPolicy.ECC_nistP256_AesGcm, newEcc);

      // The admin's SecureChannel was established with the old RSA certificate, which the server no
      // longer holds, so its Session is closed after the grace period instead of failing later at
      // channel renewal.
      adminClosed.get(TIMEOUT.toSeconds(), TimeUnit.SECONDS);

      TransactionDiagnosticsTypeNode diagnostics =
          serverConfiguration.getTransactionDiagnosticsNode();
      assertEquals(StatusCode.GOOD, readStatusCode(diagnostics.getResultNode()));
      assertArrayEquals(
          new NodeId[] {DEFAULT_APPLICATION_GROUP},
          (NodeId[]) readValue(diagnostics.getAffectedCertificateGroupsNode()));
    }

    // Part 12 §7.10.11: CancelChanges discards staged changes; nothing reaches the store or the
    // endpoints, and TransactionDiagnostics records Bad_RequestCancelledByClient.
    @Test
    void cancelChangesDiscardsStagedCertificate() throws Exception {
      X509Certificate oldRsa = installedCertificate(NodeIds.RsaSha256ApplicationCertificateType);

      OpcUaClient admin = connectSecurityAdmin(SecurityPolicy.Basic256Sha256);

      X509Certificate newRsa =
          provisionCertificate(admin, NodeIds.RsaSha256ApplicationCertificateType);
      assertNotEquals(oldRsa, newRsa, "control: a new certificate was staged");

      assertGood(
          call(admin, NodeIds.ServerConfiguration, NodeIds.ServerConfiguration_CancelChanges));

      assertEquals(oldRsa, installedCertificate(NodeIds.RsaSha256ApplicationCertificateType));
      assertEquals(oldRsa, advertisedCertificate(SecurityPolicy.Basic256Sha256));
      assertStatus(
          StatusCodes.Bad_NothingToDo,
          call(admin, NodeIds.ServerConfiguration, NodeIds.ServerConfiguration_ApplyChanges));
      assertEquals(
          StatusCodes.Bad_RequestCancelledByClient,
          readStatusCode(serverConfiguration.getTransactionDiagnosticsNode().getResultNode())
              .getValue());
    }

    // Part 12 §7.10.2: a transaction is cancelled automatically when the Session that created it
    // closes, so a client that disconnects mid-provisioning leaves the server unchanged.
    @Test
    void closingOwningSessionCancelsTransaction() throws Exception {
      X509Certificate oldRsa = installedCertificate(NodeIds.RsaSha256ApplicationCertificateType);

      OpcUaClient first = connectSecurityAdmin(SecurityPolicy.Basic256Sha256);
      provisionCertificate(first, NodeIds.RsaSha256ApplicationCertificateType);
      first.disconnect();

      OpcUaClient second = connectSecurityAdmin(SecurityPolicy.Basic256Sha256);
      assertStatus(
          StatusCodes.Bad_NothingToDo,
          call(second, NodeIds.ServerConfiguration, NodeIds.ServerConfiguration_ApplyChanges));
      assertEquals(oldRsa, installedCertificate(NodeIds.RsaSha256ApplicationCertificateType));
    }

    // Part 12 §7.10.2: exactly one transaction is active at a time. Other Sessions get
    // Bad_TransactionPending when they try to stage, and Bad_SessionIdInvalid when they try to
    // complete a transaction they do not own.
    @Test
    void otherSessionsAreRefusedWhileTransactionPending() throws Exception {
      OpcUaClient owner = connectSecurityAdmin(SecurityPolicy.Basic256Sha256);
      OpcUaClient other = connectSecurityAdmin(SecurityPolicy.Basic256Sha256);

      X509Certificate issued = issueCertificate(owner, NodeIds.RsaSha256ApplicationCertificateType);
      assertTrue(updateCertificate(owner, NodeIds.RsaSha256ApplicationCertificateType, issued));

      assertStatus(
          StatusCodes.Bad_TransactionPending,
          call(
              other,
              NodeIds.ServerConfiguration,
              NodeIds.ServerConfiguration_CreateSigningRequest,
              new Variant(DEFAULT_APPLICATION_GROUP),
              new Variant(NodeIds.RsaSha256ApplicationCertificateType),
              new Variant(null),
              new Variant(false),
              new Variant(null)));
      assertStatus(
          StatusCodes.Bad_TransactionPending,
          call(
              other,
              TRUST_LIST,
              NodeIds.ServerConfiguration_CertificateGroups_DefaultApplicationGroup_TrustList_Open,
              new Variant(ubyte(WRITE_ERASE_EXISTING))));
      assertStatus(
          StatusCodes.Bad_SessionIdInvalid,
          call(other, NodeIds.ServerConfiguration, NodeIds.ServerConfiguration_ApplyChanges));
      assertStatus(
          StatusCodes.Bad_SessionIdInvalid,
          call(other, NodeIds.ServerConfiguration, NodeIds.ServerConfiguration_CancelChanges));

      assertGood(
          call(owner, NodeIds.ServerConfiguration, NodeIds.ServerConfiguration_CancelChanges));
    }

    // Part 12 §7.8.2.5: with transactions supported, CloseAndUpdate returns
    // ApplyChangesRequired=TRUE and the TrustList is not updated until ApplyChanges is called.
    @Test
    void trustListWriteIsStagedUntilApplyChanges() throws Exception {
      TrustListManager trustListManager = serverTrustListManager();
      X509Certificate issuer = certificateAuthority.getCertificate();
      assertFalse(
          trustListManager.getIssuerCertificates().contains(issuer),
          "control: the issuer is not trusted yet");

      OpcUaClient admin = connectSecurityAdmin(SecurityPolicy.Basic256Sha256);

      assertTrue(writeTrustList(admin, issuer), "CloseAndUpdate must report ApplyChangesRequired");
      assertFalse(
          trustListManager.getIssuerCertificates().contains(issuer),
          "TrustList must not change before ApplyChanges");

      assertGood(
          call(admin, NodeIds.ServerConfiguration, NodeIds.ServerConfiguration_ApplyChanges));

      assertTrue(trustListManager.getIssuerCertificates().contains(issuer));
      assertArrayEquals(
          new NodeId[] {TRUST_LIST},
          (NodeId[])
              readValue(
                  serverConfiguration.getTransactionDiagnosticsNode().getAffectedTrustListsNode()));
    }

    // Part 12 §7.10.9: ApplyChanges returns Bad_InvalidState while a TrustList is open for writing
    // and applies nothing, and can be called again after the TrustList is closed. A transaction
    // with no pending changes then completes with Good.
    @Test
    void applyChangesIsRefusedWhileTrustListIsOpenForWriting() throws Exception {
      OpcUaClient admin = connectSecurityAdmin(SecurityPolicy.Basic256Sha256);

      CallMethodResult opened =
          call(
              admin,
              TRUST_LIST,
              NodeIds.ServerConfiguration_CertificateGroups_DefaultApplicationGroup_TrustList_Open,
              new Variant(ubyte(WRITE_ERASE_EXISTING)));
      assertGood(opened);
      UInteger fileHandle = (UInteger) opened.getOutputArguments()[0].getValue();

      assertStatus(
          StatusCodes.Bad_InvalidState,
          call(admin, NodeIds.ServerConfiguration, NodeIds.ServerConfiguration_ApplyChanges));

      assertGood(
          call(
              admin,
              TRUST_LIST,
              NodeIds.ServerConfiguration_CertificateGroups_DefaultApplicationGroup_TrustList_Close,
              new Variant(fileHandle)));

      assertGood(
          call(admin, NodeIds.ServerConfiguration, NodeIds.ServerConfiguration_ApplyChanges));
    }

    // Part 12 §7.8.2.2: Open with the Write bit starts a transaction. An Open the FileType rejects,
    // here because another Session holds a read handle, must not start one; otherwise an empty
    // transaction would refuse every other Session until the caller happened to cancel it.
    @Test
    void rejectedTrustListOpenDoesNotStartTransaction() throws Exception {
      OpcUaClient reader = connectSecurityAdmin(SecurityPolicy.Basic256Sha256);
      OpcUaClient admin = connectSecurityAdmin(SecurityPolicy.Basic256Sha256);

      assertGood(
          call(
              reader,
              TRUST_LIST,
              NodeIds.ServerConfiguration_CertificateGroups_DefaultApplicationGroup_TrustList_Open,
              new Variant(ubyte(READ))));

      assertStatus(
          StatusCodes.Bad_NotWritable,
          call(
              admin,
              TRUST_LIST,
              NodeIds.ServerConfiguration_CertificateGroups_DefaultApplicationGroup_TrustList_Open,
              new Variant(ubyte(WRITE_ERASE_EXISTING))));

      assertStatus(
          StatusCodes.Bad_NothingToDo,
          call(admin, NodeIds.ServerConfiguration, NodeIds.ServerConfiguration_ApplyChanges));
    }

    // Part 12 §7.10.11: CancelChanges discards the transaction. A TrustList the owner had opened
    // for writing belongs to that transaction, so it must be closed too; otherwise the stale
    // handle blocks every later Open(Write) and ApplyChanges with Bad_NotWritable/Bad_InvalidState.
    @Test
    void cancelChangesClosesTheOwnersTrustListWriteHandle() throws Exception {
      OpcUaClient admin = connectSecurityAdmin(SecurityPolicy.Basic256Sha256);

      assertGood(
          call(
              admin,
              TRUST_LIST,
              NodeIds.ServerConfiguration_CertificateGroups_DefaultApplicationGroup_TrustList_Open,
              new Variant(ubyte(WRITE_ERASE_EXISTING))));
      assertGood(
          call(admin, NodeIds.ServerConfiguration, NodeIds.ServerConfiguration_CancelChanges));

      assertGood(
          call(
              admin,
              TRUST_LIST,
              NodeIds.ServerConfiguration_CertificateGroups_DefaultApplicationGroup_TrustList_Open,
              new Variant(ubyte(WRITE_ERASE_EXISTING))));
    }

    // Part 12 §7.10.10 and §7.10.5: the key generated by CreateSigningRequest is what the issued
    // certificate belongs to. It must survive CancelChanges (or a dropped Session) so the client
    // can push the same certificate again instead of starting over with a new signing request.
    @Test
    void regeneratedPrivateKeySurvivesCancelChanges() throws Exception {
      OpcUaClient admin = connectSecurityAdmin(SecurityPolicy.Basic256Sha256);

      X509Certificate issued = issueCertificate(admin, NodeIds.RsaSha256ApplicationCertificateType);
      assertTrue(updateCertificate(admin, NodeIds.RsaSha256ApplicationCertificateType, issued));
      assertGood(
          call(admin, NodeIds.ServerConfiguration, NodeIds.ServerConfiguration_CancelChanges));

      assertTrue(
          updateCertificate(admin, NodeIds.RsaSha256ApplicationCertificateType, issued),
          "the certificate issued for the regenerated key must still be accepted");
      assertGood(
          call(admin, NodeIds.ServerConfiguration, NodeIds.ServerConfiguration_ApplyChanges));

      assertEquals(issued, installedCertificate(NodeIds.RsaSha256ApplicationCertificateType));
      assertNewChannelUsesCertificate(SecurityPolicy.Basic256Sha256, issued);
    }

    // Part 12 §7.7.2 lets ApplyChanges close Endpoints and force reconnects, but only a replaced
    // certificate makes a SecureChannel unusable. Re-installing the certificate already in use, as
    // a
    // GDS retry does, must leave Sessions bound to it alone.
    @Test
    void reinstallingTheCurrentCertificateKeepsSessionsOpen() throws Exception {
      X509Certificate current = installedCertificate(NodeIds.RsaSha256ApplicationCertificateType);

      OpcUaClient admin = connectSecurityAdmin(SecurityPolicy.Basic256Sha256);
      CompletableFuture<Void> adminClosed = sessionClosed(admin.getSession().getSessionId());

      CallMethodResult result =
          call(
              admin,
              NodeIds.ServerConfiguration,
              NodeIds.ServerConfiguration_UpdateCertificate,
              new Variant(DEFAULT_APPLICATION_GROUP),
              new Variant(NodeIds.RsaSha256ApplicationCertificateType),
              new Variant(ByteString.of(current.getEncoded())),
              new Variant(new ByteString[0]),
              new Variant(null),
              new Variant(null));
      assertGood(result);
      assertGood(
          call(admin, NodeIds.ServerConfiguration, NodeIds.ServerConfiguration_ApplyChanges));

      Duration wait = ServerConfigurationObject.SESSION_CLOSE_GRACE.plusSeconds(1);
      assertThrows(
          TimeoutException.class,
          () -> adminClosed.get(wait.toMillis(), TimeUnit.MILLISECONDS),
          "Session on a certificate that is still installed must not be closed");
      assertTrue(
          admin
              .readValue(0.0, TimestampsToReturn.Neither, NodeIds.Server_ServerStatus_State)
              .statusCode()
              .isGood());
    }
  }

  private static final int READ = 0b0001;
  private static final int WRITE_ERASE_EXISTING = 0b0110;

  private OpcUaClient connectSecurityAdmin(SecurityPolicy securityPolicy) throws Exception {
    return connect(securityPolicy, new UsernameProvider("SecurityAdmin", "password"));
  }

  private OpcUaClient connect(SecurityPolicy securityPolicy, @Nullable UsernameProvider identity)
      throws Exception {

    OpcUaClient client =
        OpcUaTestClient.create(
            server,
            securityPolicy,
            MessageSecurityMode.SignAndEncrypt,
            builder -> {
              builder
                  .setApplicationUri(CLIENT_APPLICATION_URI)
                  .setCertificateGroup(clientCertificateGroup)
                  .setCertificateValidator(new CertificateValidator.InsecureCertificateValidator());

              if (identity != null) {
                builder.setIdentityProvider(identity);
              }
            });

    clients.add(client);
    client.connect();
    return client;
  }

  /** CreateSigningRequest with a regenerated key, signed by the test CA, then UpdateCertificate. */
  private X509Certificate provisionCertificate(OpcUaClient admin, NodeId certificateTypeId)
      throws Exception {

    X509Certificate issued = issueCertificate(admin, certificateTypeId);

    assertTrue(
        updateCertificate(admin, certificateTypeId, issued),
        "UpdateCertificate must report ApplyChangesRequired for " + certificateTypeId);

    return issued;
  }

  private X509Certificate issueCertificate(OpcUaClient admin, NodeId certificateTypeId)
      throws Exception {

    byte[] nonce = new byte[32];
    new SecureRandom().nextBytes(nonce);

    CallMethodResult result =
        call(
            admin,
            NodeIds.ServerConfiguration,
            NodeIds.ServerConfiguration_CreateSigningRequest,
            new Variant(DEFAULT_APPLICATION_GROUP),
            new Variant(certificateTypeId),
            new Variant(null),
            new Variant(true),
            new Variant(ByteString.of(nonce)));
    assertGood(result);

    ByteString csr = (ByteString) result.getOutputArguments()[0].getValue();

    return certificateAuthority.issue(csr.bytesOrEmpty());
  }

  private boolean updateCertificate(
      OpcUaClient admin, NodeId certificateTypeId, X509Certificate certificate) throws Exception {

    CallMethodResult result =
        call(
            admin,
            NodeIds.ServerConfiguration,
            NodeIds.ServerConfiguration_UpdateCertificate,
            new Variant(DEFAULT_APPLICATION_GROUP),
            new Variant(certificateTypeId),
            new Variant(ByteString.of(certificate.getEncoded())),
            new Variant(
                new ByteString[] {
                  ByteString.of(certificateAuthority.getCertificate().getEncoded())
                }),
            new Variant(null),
            new Variant(null));
    assertGood(result);

    return (Boolean) result.getOutputArguments()[0].getValue();
  }

  private boolean writeTrustList(OpcUaClient admin, X509Certificate issuer) throws Exception {
    var trustList =
        new TrustListDataType(
            uint(TrustListMasks.IssuerCertificates.getValue()),
            new ByteString[0],
            new ByteString[0],
            new ByteString[] {ByteString.of(issuer.getEncoded())},
            new ByteString[0]);

    ByteString encoded =
        (ByteString) ExtensionObject.encode(DefaultEncodingContext.INSTANCE, trustList).getBody();

    CallMethodResult opened =
        call(
            admin,
            TRUST_LIST,
            NodeIds.ServerConfiguration_CertificateGroups_DefaultApplicationGroup_TrustList_Open,
            new Variant(ubyte(WRITE_ERASE_EXISTING)));
    assertGood(opened);
    UInteger fileHandle = (UInteger) opened.getOutputArguments()[0].getValue();

    assertGood(
        call(
            admin,
            TRUST_LIST,
            NodeIds.ServerConfiguration_CertificateGroups_DefaultApplicationGroup_TrustList_Write,
            new Variant(fileHandle),
            new Variant(encoded)));

    CallMethodResult closed =
        call(
            admin,
            TRUST_LIST,
            NodeIds
                .ServerConfiguration_CertificateGroups_DefaultApplicationGroup_TrustList_CloseAndUpdate,
            new Variant(fileHandle));
    assertGood(closed);

    return (Boolean) closed.getOutputArguments()[0].getValue();
  }

  private static CallMethodResult call(
      OpcUaClient client, NodeId objectId, NodeId methodId, Variant... inputs) throws Exception {

    return client.call(List.of(new CallMethodRequest(objectId, methodId, inputs))).getResults()[0];
  }

  private static void assertGood(CallMethodResult result) {
    assertTrue(
        result.getStatusCode().isGood(), () -> "method call failed: " + result.getStatusCode());
  }

  private static void assertStatus(long expected, CallMethodResult result) {
    assertEquals(new StatusCode(expected), result.getStatusCode());
  }

  private X509Certificate installedCertificate(NodeId certificateTypeId) {
    return serverCertificateGroup().getCertificateChain(certificateTypeId).orElseThrow()[0];
  }

  private CertificateGroup serverCertificateGroup() {
    return server
        .getConfig()
        .getCertificateManager()
        .getCertificateGroup(DEFAULT_APPLICATION_GROUP)
        .orElseThrow();
  }

  private TrustListManager serverTrustListManager() {
    return serverCertificateGroup().getTrustListManager();
  }

  /** The certificate GetEndpoints advertises for {@code securityPolicy}, read over the wire. */
  private X509Certificate advertisedCertificate(SecurityPolicy securityPolicy) throws Exception {
    String endpointUrl = server.getConfig().getEndpoints().iterator().next().getEndpointUrl();

    List<EndpointDescription> endpoints =
        DiscoveryClient.getEndpoints(endpointUrl).get(TIMEOUT.toSeconds(), TimeUnit.SECONDS);

    List<X509Certificate> certificates = new ArrayList<>();
    for (EndpointDescription endpoint : endpoints) {
      if (securityPolicy.getUri().equals(endpoint.getSecurityPolicyUri())) {
        certificates.add(
            CertificateUtil.decodeCertificate(endpoint.getServerCertificate().bytesOrEmpty()));
      }
    }

    assertFalse(certificates.isEmpty(), () -> "no endpoint advertised for " + securityPolicy);
    assertEquals(
        1,
        certificates.stream().distinct().count(),
        () -> "endpoints for " + securityPolicy + " must agree on one certificate");

    return certificates.get(0);
  }

  /** Connect a fresh client and check the CreateSession response names {@code expected}. */
  private void assertNewChannelUsesCertificate(
      SecurityPolicy securityPolicy, X509Certificate expected) throws Exception {

    OpcUaClient client = connect(securityPolicy, null);

    DataValue value =
        client.readValue(0.0, TimestampsToReturn.Neither, NodeIds.Server_ServerStatus_State);
    assertTrue(value.statusCode().isGood(), () -> "read failed over " + securityPolicy);

    assertArrayEquals(
        expected.getEncoded(),
        client.getSession().getServerCertificate().bytesOrEmpty(),
        () -> "new SecureChannel over " + securityPolicy + " must use the new certificate");

    client.disconnect();
  }

  /** A future completed when the server closes the Session with {@code sessionId}. */
  private CompletableFuture<Void> sessionClosed(NodeId sessionId) {
    var closed = new CompletableFuture<Void>();

    server
        .getSessionManager()
        .addSessionListener(
            new SessionListener() {
              @Override
              public void onSessionClosed(Session session) {
                if (session.getSessionId().equals(sessionId)) {
                  closed.complete(null);
                }
              }
            });

    return closed;
  }

  /** Read through the node's filter chain, which is where the diagnostics values come from. */
  private static Object readValue(UaVariableNode node) {
    return node.getValue().getValue().getValue();
  }

  private static StatusCode readStatusCode(UaVariableNode node) {
    return (StatusCode) readValue(node);
  }
}

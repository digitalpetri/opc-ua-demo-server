package com.digitalpetri.opcua.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.security.KeyPair;
import java.security.cert.X509CRL;
import java.security.cert.X509Certificate;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import org.bouncycastle.asn1.x500.X500Name;
import org.eclipse.milo.opcua.sdk.client.OpcUaClient;
import org.eclipse.milo.opcua.sdk.client.identity.X509IdentityProvider;
import org.eclipse.milo.opcua.stack.core.NodeIds;
import org.eclipse.milo.opcua.stack.core.StatusCodes;
import org.eclipse.milo.opcua.stack.core.UaException;
import org.eclipse.milo.opcua.stack.core.security.CertificateValidator;
import org.eclipse.milo.opcua.stack.core.security.DefaultCertificateGroup;
import org.eclipse.milo.opcua.stack.core.security.FileBasedTrustListManager;
import org.eclipse.milo.opcua.stack.core.security.MemoryCertificateQuarantine;
import org.eclipse.milo.opcua.stack.core.security.MemoryCertificateStore;
import org.eclipse.milo.opcua.stack.core.security.MemoryTrustListManager;
import org.eclipse.milo.opcua.stack.core.security.SecurityPolicy;
import org.eclipse.milo.opcua.stack.core.types.builtin.ByteString;
import org.eclipse.milo.opcua.stack.core.types.enumerated.MessageSecurityMode;
import org.eclipse.milo.opcua.stack.core.types.enumerated.TimestampsToReturn;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class UserCertificateRevocationIT {
  // Removing the deprecated optional REVOCATION flag must not allow a revoked trusted user
  // certificate to authenticate. The same certificate and key authenticate with an empty CRL.
  @Test
  void rejectsRevokedUserCertificateWithoutOptionalRevocationFlag(@TempDir Path directory)
      throws Exception {
    String clientUri = "urn:test:user-revocation";
    var factory = new DemoCertificateFactory(clientUri, () -> Set.of("localhost"));
    var clientGroup =
        new DefaultCertificateGroup(
            new MemoryTrustListManager(),
            new MemoryCertificateStore(),
            new MemoryCertificateQuarantine(),
            new CertificateValidator.InsecureCertificateValidator(),
            List.of(NodeIds.RsaSha256ApplicationCertificateType));
    factory.createMissingCertificates(clientGroup);
    KeyPair keyPair =
        clientGroup.getKeyPair(NodeIds.RsaSha256ApplicationCertificateType).orElseThrow();
    ByteString csr =
        factory.createSigningRequest(
            NodeIds.RsaSha256ApplicationCertificateType,
            keyPair,
            new X500Name("CN=Test User"),
            clientUri,
            List.of("localhost"),
            List.of());
    TestCertificateAuthority ca = TestCertificateAuthority.create("User CA");
    X509Certificate user = ca.issue(csr.bytesOrEmpty());
    assertUserLogin(
        directory.resolve("valid"),
        clientUri,
        clientGroup,
        keyPair,
        user,
        ca.getCertificate(),
        ca.createCrl(),
        false);
    assertUserLogin(
        directory.resolve("revoked"),
        clientUri,
        clientGroup,
        keyPair,
        user,
        ca.getCertificate(),
        ca.createCrl(user),
        true);
  }

  private static void assertUserLogin(
      Path directory,
      String clientUri,
      DefaultCertificateGroup clientGroup,
      KeyPair keyPair,
      X509Certificate user,
      X509Certificate ca,
      X509CRL crl,
      boolean revoked)
      throws Exception {
    try (FileBasedTrustListManager trust =
        FileBasedTrustListManager.createAndInitialize(directory.resolve("security/pki-user"))) {
      trust.addTrustedCertificate(ca);
      trust.setTrustedCrls(List.of(crl));
    }
    OpcUaDemoServer demo = OpcUaTestServerBuilder.builder().withDataDir(directory).build();
    demo.startup();
    try {
      OpcUaClient client =
          OpcUaTestClient.create(
              demo.getServer(),
              SecurityPolicy.None,
              MessageSecurityMode.None,
              builder ->
                  builder
                      .setApplicationUri(clientUri)
                      .setCertificateGroup(clientGroup)
                      .setCertificateValidator(
                          new CertificateValidator.InsecureCertificateValidator())
                      .setIdentityProvider(new X509IdentityProvider(user, keyPair.getPrivate())));
      try {
        if (revoked) {
          ExecutionException error =
              assertThrows(
                  ExecutionException.class, () -> client.connectAsync().get(5, TimeUnit.SECONDS));
          assertEquals(
              StatusCodes.Bad_IdentityTokenRejected,
              UaException.extract(error).orElseThrow().getStatusCode().getValue());
        } else {
          client.connectAsync().get(5, TimeUnit.SECONDS);
          assertTrue(
              client
                  .readValue(0, TimestampsToReturn.Neither, NodeIds.Server_ServerStatus_State)
                  .statusCode()
                  .isGood());
        }
      } finally {
        client.disconnectAsync().get(5, TimeUnit.SECONDS);
      }
    } finally {
      demo.shutdown();
    }
  }
}

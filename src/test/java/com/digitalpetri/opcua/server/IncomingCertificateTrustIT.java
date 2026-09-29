package com.digitalpetri.opcua.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.typesafe.config.ConfigFactory;
import java.nio.file.Path;
import java.security.InvalidAlgorithmParameterException;
import java.security.cert.X509Certificate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import org.eclipse.milo.opcua.sdk.client.OpcUaClient;
import org.eclipse.milo.opcua.stack.core.NodeIds;
import org.eclipse.milo.opcua.stack.core.StatusCodes;
import org.eclipse.milo.opcua.stack.core.UaException;
import org.eclipse.milo.opcua.stack.core.security.CertificateGroup;
import org.eclipse.milo.opcua.stack.core.security.CertificateValidator;
import org.eclipse.milo.opcua.stack.core.security.DefaultCertificateGroup;
import org.eclipse.milo.opcua.stack.core.security.DefaultClientCertificateValidator;
import org.eclipse.milo.opcua.stack.core.security.MemoryCertificateQuarantine;
import org.eclipse.milo.opcua.stack.core.security.MemoryCertificateStore;
import org.eclipse.milo.opcua.stack.core.security.MemoryTrustListManager;
import org.eclipse.milo.opcua.stack.core.security.SecurityPolicy;
import org.eclipse.milo.opcua.stack.core.types.enumerated.MessageSecurityMode;
import org.eclipse.milo.opcua.stack.core.types.enumerated.TimestampsToReturn;
import org.eclipse.milo.opcua.stack.core.util.validation.ValidationCheck;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class IncomingCertificateTrustIT {
  private static final String PEER_APPLICATION_URI = "urn:eclipse:milo:test:untrusted-peer";

  // An incoming connection accepted by trust-all must not bootstrap trust for an outgoing GDS.
  @Test
  void acceptingIncomingCertificateDoesNotTrustItForOutgoingConnections(@TempDir Path directory)
      throws Exception {
    OpcUaDemoServer server =
        OpcUaTestServerBuilder.builder()
            .withDataDir(directory)
            .withConfig(
                ConfigFactory.parseMap(
                    Map.of(
                        "security-policy-list", List.of("Basic256Sha256"),
                        "security-mode-list", List.of("SignAndEncrypt"),
                        "trust-all-certificates", true)))
            .build();
    server.startup();
    try {
      CertificateValidator insecure = new CertificateValidator.InsecureCertificateValidator();
      var peerGroup =
          new DefaultCertificateGroup(
              new MemoryTrustListManager(),
              new MemoryCertificateStore(),
              new MemoryCertificateQuarantine(),
              insecure,
              List.of(NodeIds.RsaSha256ApplicationCertificateType));
      new DemoCertificateFactory(PEER_APPLICATION_URI, () -> Set.of("localhost"))
          .createMissingCertificates(peerGroup);
      List<X509Certificate> peerChain =
          List.of(
              peerGroup
                  .getCertificateChain(NodeIds.RsaSha256ApplicationCertificateType)
                  .orElseThrow());
      CertificateGroup serverGroup =
          server
              .getServer()
              .getConfig()
              .getCertificateManager()
              .getDefaultApplicationGroup()
              .orElseThrow();
      var outgoing =
          new DefaultClientCertificateValidator(
              serverGroup.getTrustListManager(),
              ValidationCheck.ALL_OPTIONAL_CHECKS,
              new MemoryCertificateQuarantine());
      assertUntrusted(outgoing, peerChain);

      OpcUaClient client =
          OpcUaTestClient.create(
              server.getServer(),
              SecurityPolicy.Basic256Sha256,
              MessageSecurityMode.SignAndEncrypt,
              builder ->
                  builder
                      .setApplicationUri(PEER_APPLICATION_URI)
                      .setCertificateGroup(peerGroup)
                      .setCertificateValidator(insecure));
      try {
        client.connectAsync().get(5, TimeUnit.SECONDS);
        assertTrue(
            client
                .readValue(0, TimestampsToReturn.Neither, NodeIds.Server_ServerStatus_State)
                .statusCode()
                .isGood());
      } finally {
        client.disconnectAsync().get(5, TimeUnit.SECONDS);
      }

      assertUntrusted(outgoing, peerChain);
      serverGroup.getTrustListManager().addTrustedCertificate(peerChain.getFirst());
      outgoing.validateCertificateChain(
          peerChain, PEER_APPLICATION_URI, new String[] {"localhost"});
    } finally {
      server.shutdown();
    }
  }

  private static void assertUntrusted(CertificateValidator validator, List<X509Certificate> chain) {
    UaException error =
        assertThrows(
            UaException.class,
            () ->
                validator.validateCertificateChain(
                    chain, PEER_APPLICATION_URI, new String[] {"localhost"}));
    // With no trust anchors, PKIX path construction fails before Milo can report Untrusted.
    assertEquals(StatusCodes.Bad_SecurityChecksFailed, error.getStatusCode().value());
    InvalidAlgorithmParameterException cause =
        assertInstanceOf(InvalidAlgorithmParameterException.class, error.getCause());
    assertTrue(cause.getMessage().contains("trustAnchors"));
  }
}

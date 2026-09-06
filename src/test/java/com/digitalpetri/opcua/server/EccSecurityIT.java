package com.digitalpetri.opcua.server;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.eclipse.milo.opcua.sdk.client.OpcUaClient;
import org.eclipse.milo.opcua.sdk.client.identity.UsernameProvider;
import org.eclipse.milo.opcua.stack.core.NodeIds;
import org.eclipse.milo.opcua.stack.core.security.CertificateGroup;
import org.eclipse.milo.opcua.stack.core.security.CertificateValidator;
import org.eclipse.milo.opcua.stack.core.security.DefaultCertificateGroup;
import org.eclipse.milo.opcua.stack.core.security.MemoryCertificateQuarantine;
import org.eclipse.milo.opcua.stack.core.security.MemoryCertificateStore;
import org.eclipse.milo.opcua.stack.core.security.MemoryTrustListManager;
import org.eclipse.milo.opcua.stack.core.security.SecurityPolicy;
import org.eclipse.milo.opcua.stack.core.types.builtin.DataValue;
import org.eclipse.milo.opcua.stack.core.types.builtin.NodeId;
import org.eclipse.milo.opcua.stack.core.types.enumerated.MessageSecurityMode;
import org.eclipse.milo.opcua.stack.core.types.enumerated.TimestampsToReturn;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class EccSecurityIT {

  private static final String CLIENT_APPLICATION_URI = "urn:eclipse:milo:test:ecc-client";

  private static final List<SecurityPolicy> SECURITY_POLICIES =
      List.of(SecurityPolicy.ECC_nistP256_AesGcm, SecurityPolicy.ECC_curve25519_ChaChaPoly);

  private static final List<MessageSecurityMode> SECURITY_MODES =
      List.of(MessageSecurityMode.Sign, MessageSecurityMode.SignAndEncrypt);

  private OpcUaDemoServer server;

  @AfterEach
  void tearDown() {
    if (server != null) {
      server.shutdown();
    }
  }

  @Test
  void connectsAndReadsOverEccEndpoints(@TempDir Path tempDir) throws Exception {
    Config config =
        ConfigFactory.parseMap(
            Map.of(
                "security-policy-list",
                List.of("None", "ECC_nistP256_AesGcm", "ECC_curve25519_ChaChaPoly"),
                "security-mode-list",
                List.of("None", "Sign", "SignAndEncrypt"),
                "trust-all-certificates",
                true));

    server = OpcUaTestServerBuilder.builder().withDataDir(tempDir).withConfig(config).build();
    server.startup();

    CertificateValidator certificateValidator =
        new CertificateValidator.InsecureCertificateValidator();
    CertificateGroup certificateGroup = createClientCertificateGroup(certificateValidator);

    for (SecurityPolicy securityPolicy : SECURITY_POLICIES) {
      for (MessageSecurityMode securityMode : SECURITY_MODES) {
        OpcUaClient client =
            createClient(
                securityPolicy, securityMode, certificateGroup, certificateValidator, false);

        try {
          assertServerStateReadable(client, securityPolicy, securityMode);
        } finally {
          client.disconnect();
        }
      }
    }

    OpcUaClient client =
        createClient(
            SecurityPolicy.ECC_nistP256_AesGcm,
            MessageSecurityMode.SignAndEncrypt,
            certificateGroup,
            certificateValidator,
            true);

    try {
      assertServerStateReadable(
          client, SecurityPolicy.ECC_nistP256_AesGcm, MessageSecurityMode.SignAndEncrypt);
    } finally {
      client.disconnect();
    }
  }

  private OpcUaClient createClient(
      SecurityPolicy securityPolicy,
      MessageSecurityMode securityMode,
      CertificateGroup certificateGroup,
      CertificateValidator certificateValidator,
      boolean useUsername)
      throws Exception {

    OpcUaClient client =
        OpcUaTestClient.create(
            server.getServer(),
            securityPolicy,
            securityMode,
            builder -> {
              builder
                  .setApplicationUri(CLIENT_APPLICATION_URI)
                  .setCertificateGroup(certificateGroup)
                  .setCertificateValidator(certificateValidator);

              if (useUsername) {
                builder.setIdentityProvider(new UsernameProvider("User", "password"));
              }
            });

    client.connect();
    return client;
  }

  private static CertificateGroup createClientCertificateGroup(
      CertificateValidator certificateValidator) throws Exception {

    List<NodeId> certificateTypeIds =
        List.of(
            NodeIds.EccNistP256ApplicationCertificateType,
            NodeIds.EccCurve25519ApplicationCertificateType);

    var certificateGroup =
        new DefaultCertificateGroup(
            new MemoryTrustListManager(),
            new MemoryCertificateStore(),
            new MemoryCertificateQuarantine(),
            certificateValidator,
            certificateTypeIds);

    new DemoCertificateFactory(CLIENT_APPLICATION_URI, () -> Set.of("localhost"))
        .createMissingCertificates(certificateGroup);

    return certificateGroup;
  }

  private static void assertServerStateReadable(
      OpcUaClient client, SecurityPolicy securityPolicy, MessageSecurityMode securityMode)
      throws Exception {

    DataValue value =
        client.readValue(0.0, TimestampsToReturn.Neither, NodeIds.Server_ServerStatus_State);

    assertTrue(
        value.statusCode().isGood(),
        () ->
            "read failed over " + securityPolicy + "/" + securityMode + ": " + value.statusCode());
    assertNotNull(value.value().value());
  }
}

package com.digitalpetri.opcua.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import org.eclipse.milo.opcua.sdk.client.OpcUaClient;
import org.eclipse.milo.opcua.sdk.client.identity.IdentityProvider;
import org.eclipse.milo.opcua.sdk.client.identity.UsernameProvider;
import org.eclipse.milo.opcua.sdk.client.identity.X509IdentityProvider;
import org.eclipse.milo.opcua.stack.core.NodeIds;
import org.eclipse.milo.opcua.stack.core.security.CertificateIdentity;
import org.eclipse.milo.opcua.stack.core.security.CertificateValidator;
import org.eclipse.milo.opcua.stack.core.security.DefaultCertificateGroup;
import org.eclipse.milo.opcua.stack.core.security.FileBasedTrustListManager;
import org.eclipse.milo.opcua.stack.core.security.MemoryCertificateQuarantine;
import org.eclipse.milo.opcua.stack.core.security.MemoryCertificateStore;
import org.eclipse.milo.opcua.stack.core.security.MemoryTrustListManager;
import org.eclipse.milo.opcua.stack.core.security.SecurityPolicy;
import org.eclipse.milo.opcua.stack.core.types.builtin.DataValue;
import org.eclipse.milo.opcua.stack.core.types.enumerated.MessageSecurityMode;
import org.eclipse.milo.opcua.stack.core.types.enumerated.TimestampsToReturn;
import org.eclipse.milo.opcua.stack.core.types.enumerated.UserTokenType;
import org.eclipse.milo.opcua.stack.core.types.structured.UserTokenPolicy;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.io.TempDir;

class RsaSecurityIT {

  private static final String CLIENT_APPLICATION_URI = "urn:eclipse:milo:test:rsa-client";

  // Part 4 §7.41 recommends matching the username policy to the secured endpoint. RSA-DH also
  // needs the Part 6 §6.8.2 key exchange. Login alone would miss a Basic256Sha256 token fallback.
  @TestFactory
  Stream<DynamicTest> connectsWithMatchingUsernameTokenPolicy(@TempDir Path tempDir) {
    return rsaEndpoints(tempDir, UserTokenType.UserName);
  }

  // Certificate authentication must use the advertised signature policy, including the enhanced
  // channel-bound signature format on RSA-DH endpoints (Part 4 §6.1.8).
  @TestFactory
  Stream<DynamicTest> connectsWithMatchingCertificateTokenPolicy(@TempDir Path tempDir) {
    return rsaEndpoints(tempDir, UserTokenType.Certificate);
  }

  private static Stream<DynamicTest> rsaEndpoints(Path tempDir, UserTokenType tokenType) {
    return Stream.of(
            SecurityPolicy.None,
            SecurityPolicy.Basic256Sha256,
            SecurityPolicy.Aes128_Sha256_RsaOaep,
            SecurityPolicy.Aes256_Sha256_RsaPss,
            SecurityPolicy.RSA_DH_AesGcm,
            SecurityPolicy.RSA_DH_ChaChaPoly)
        .flatMap(
            policy -> {
              Stream<MessageSecurityMode> modes =
                  policy == SecurityPolicy.None
                      ? Stream.of(MessageSecurityMode.None)
                      : Stream.of(MessageSecurityMode.Sign, MessageSecurityMode.SignAndEncrypt);
              return modes.map(
                  mode ->
                      DynamicTest.dynamicTest(
                          policy + "/" + mode,
                          () ->
                              assertTokenConnection(
                                  policy, mode, tokenType, tempDir.resolve(policy + "-" + mode))));
            });
  }

  private static void assertTokenConnection(
      SecurityPolicy securityPolicy,
      MessageSecurityMode securityMode,
      UserTokenType tokenType,
      Path tempDir)
      throws Exception {
    Config config =
        ConfigFactory.parseMap(
            Map.of(
                "security-policy-list", List.of(securityPolicy.name()),
                "security-mode-list", List.of(securityMode.name()),
                "trust-all-certificates", true));
    CertificateValidator certificateValidator =
        new CertificateValidator.InsecureCertificateValidator();
    var certificateGroup =
        new DefaultCertificateGroup(
            new MemoryTrustListManager(),
            new MemoryCertificateStore(),
            new MemoryCertificateQuarantine(),
            certificateValidator,
            List.of(NodeIds.RsaSha256ApplicationCertificateType));
    new DemoCertificateFactory(CLIENT_APPLICATION_URI, () -> Set.of("localhost"))
        .createMissingCertificates(certificateGroup);

    CertificateIdentity identity = certificateGroup.getCertificateIdentities().getFirst();
    try (FileBasedTrustListManager userTrustList =
        FileBasedTrustListManager.createAndInitialize(tempDir.resolve("security/pki-user"))) {
      userTrustList.addTrustedCertificate(identity.certificate());
    }

    IdentityProvider identityProvider =
        tokenType == UserTokenType.UserName
            ? new UsernameProvider("User", "password")
            : new X509IdentityProvider(identity.certificate(), identity.keyPair().getPrivate());

    OpcUaDemoServer server =
        OpcUaTestServerBuilder.builder().withDataDir(tempDir).withConfig(config).build();
    server.startup();

    try {
      OpcUaClient client =
          OpcUaTestClient.create(
              server.getServer(),
              securityPolicy,
              securityMode,
              builder ->
                  builder
                      .setApplicationUri(CLIENT_APPLICATION_URI)
                      .setCertificateGroup(certificateGroup)
                      .setCertificateValidator(certificateValidator)
                      .setIdentityProvider(identityProvider));

      try {
        UserTokenPolicy[] tokenPolicies = client.getConfig().getEndpoint().getUserIdentityTokens();
        assertNotNull(tokenPolicies);
        UserTokenPolicy tokenPolicy =
            Arrays.stream(tokenPolicies)
                .filter(policy -> policy.getTokenType() == tokenType)
                .findFirst()
                .orElseThrow();
        SecurityPolicy expectedTokenPolicy =
            securityPolicy == SecurityPolicy.None ? SecurityPolicy.Basic256Sha256 : securityPolicy;
        assertEquals(expectedTokenPolicy.getUri(), tokenPolicy.getSecurityPolicyUri());

        client.connect();
        DataValue value =
            client.readValue(0.0, TimestampsToReturn.Neither, NodeIds.Server_ServerStatus_State);
        assertTrue(value.statusCode().isGood());
        assertNotNull(value.value().value());
      } finally {
        client.disconnect();
      }
    } finally {
      server.shutdown();
    }
  }
}

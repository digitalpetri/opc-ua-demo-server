package com.digitalpetri.opcua.server.gds;

import static org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.Unsigned.ubyte;
import static org.junit.jupiter.api.Assertions.*;

import com.typesafe.config.ConfigFactory;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import org.eclipse.milo.opcua.sdk.server.OpcUaServer;
import org.eclipse.milo.opcua.sdk.server.OpcUaServerConfig;
import org.eclipse.milo.opcua.stack.core.Stack;
import org.eclipse.milo.opcua.stack.core.StatusCodes;
import org.eclipse.milo.opcua.stack.core.UaException;
import org.eclipse.milo.opcua.stack.core.gds.types.ApplicationRecordDataType;
import org.eclipse.milo.opcua.stack.core.security.DefaultCertificateManager;
import org.eclipse.milo.opcua.stack.core.security.SecurityPolicy;
import org.eclipse.milo.opcua.stack.core.types.builtin.LocalizedText;
import org.eclipse.milo.opcua.stack.core.types.builtin.NodeId;
import org.eclipse.milo.opcua.stack.core.types.enumerated.ApplicationType;
import org.eclipse.milo.opcua.stack.core.types.enumerated.MessageSecurityMode;
import org.eclipse.milo.opcua.stack.core.types.enumerated.UserTokenType;
import org.eclipse.milo.opcua.stack.core.types.structured.EndpointDescription;
import org.eclipse.milo.opcua.stack.core.types.structured.UserTokenPolicy;
import org.eclipse.milo.opcua.stack.transport.server.ServerApplicationContext;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GdsRegistrationServiceTest {
  @Test
  void comparisonIgnoresOrderingAndDuplicatesButReportsMeaningfulFields() throws Exception {
    var desired =
        record(
            "urn:demo",
            ApplicationType.Server,
            "urn:product",
            new String[] {"one", "two"},
            new String[] {"DA", "AC"});
    var existing =
        record(
            "urn:demo",
            ApplicationType.Server,
            "urn:product",
            new String[] {"two", "one", "one"},
            new String[] {"AC", "DA"});
    assertTrue(GdsRegistrationService.differences(desired, existing).isEmpty());
    assertEquals(
        List.of("DiscoveryUrls", "ServerCapabilities"),
        GdsRegistrationService.differences(
            desired, record("urn:demo", ApplicationType.Server, "urn:product", null, null)));
    assertTrue(
        GdsRegistrationService.differences(
                record("urn:demo", ApplicationType.Server, "urn:product", null, null),
                record(
                    "urn:demo",
                    ApplicationType.Server,
                    "urn:product",
                    new String[0],
                    new String[0]))
            .isEmpty());
  }

  @Test
  void identityConflictsCannotBeUpdated() {
    var desired = record("urn:demo", ApplicationType.Server, "urn:product", null, null);
    var error =
        assertThrows(
            GdsRegistrationService.RegistrationException.class,
            () ->
                GdsRegistrationService.differences(
                    desired,
                    record("urn:other", ApplicationType.Client, "urn:other-product", null, null)));
    assertEquals(
        "Conflicting identity fields: ApplicationUri, ApplicationType, ProductUri",
        error.getMessage());
  }

  @Test
  void endpointSelectionCannotDowngradeOrFallBackToAnonymous() throws Exception {
    var weak = endpoint(SecurityPolicy.None, MessageSecurityMode.None, UserTokenType.UserName);
    var anonymous =
        endpoint(
            SecurityPolicy.Basic256Sha256,
            MessageSecurityMode.SignAndEncrypt,
            UserTokenType.Anonymous);
    var signed =
        endpoint(SecurityPolicy.Basic256Sha256, MessageSecurityMode.Sign, UserTokenType.UserName);
    var secure =
        endpoint(
            SecurityPolicy.Basic256Sha256,
            MessageSecurityMode.SignAndEncrypt,
            UserTokenType.UserName);
    assertSame(
        secure,
        GdsRegistrationService.selectEndpoint(
            List.of(weak, anonymous, signed, secure),
            SecurityPolicy.Basic256Sha256,
            UserTokenType.UserName));
    assertThrows(
        GdsRegistrationService.RegistrationException.class,
        () ->
            GdsRegistrationService.selectEndpoint(
                List.of(weak, anonymous, signed),
                SecurityPolicy.Basic256Sha256,
                UserTokenType.UserName));
  }

  @Test
  void anonymousEndpointSelectionStillRequiresConfiguredPolicyAndSignAndEncrypt() throws Exception {
    var weak = endpoint(SecurityPolicy.None, MessageSecurityMode.None, UserTokenType.Anonymous);
    var signed =
        endpoint(SecurityPolicy.Basic256Sha256, MessageSecurityMode.Sign, UserTokenType.Anonymous);
    var otherPolicy =
        endpoint(
            SecurityPolicy.Aes256_Sha256_RsaPss,
            MessageSecurityMode.SignAndEncrypt,
            UserTokenType.Anonymous);
    var username =
        endpoint(
            SecurityPolicy.Basic256Sha256,
            MessageSecurityMode.SignAndEncrypt,
            UserTokenType.UserName);
    var secure =
        endpoint(
            SecurityPolicy.Basic256Sha256,
            MessageSecurityMode.SignAndEncrypt,
            UserTokenType.Anonymous);
    assertSame(
        secure,
        GdsRegistrationService.selectEndpoint(
            List.of(weak, signed, otherPolicy, username, secure),
            SecurityPolicy.Basic256Sha256,
            UserTokenType.Anonymous));
    var error =
        assertThrows(
            GdsRegistrationService.RegistrationException.class,
            () ->
                GdsRegistrationService.selectEndpoint(
                    List.of(weak, signed, otherPolicy, username),
                    SecurityPolicy.Basic256Sha256,
                    UserTokenType.Anonymous));
    assertTrue(error.getMessage().contains("anonymous authentication"));
  }

  @Test
  void usernameTokenPolicyUsesMiloCompatibilityRulesWithoutChangingEndpointPolicy() {
    EndpointDescription endpoint =
        endpoint(
            SecurityPolicy.Basic256Sha256,
            MessageSecurityMode.SignAndEncrypt,
            UserTokenType.UserName);
    assertTrue(
        GdsRegistrationService.compatibleToken(
            new UserTokenPolicy(
                "legacy-rsa", UserTokenType.UserName, null, null, SecurityPolicy.Basic256.getUri()),
            endpoint,
            UserTokenType.UserName));
    assertFalse(
        GdsRegistrationService.compatibleToken(
            new UserTokenPolicy(
                "ecc",
                UserTokenType.UserName,
                null,
                null,
                SecurityPolicy.ECC_nistP256_AesGcm.getUri()),
            endpoint,
            UserTokenType.UserName));
    assertFalse(
        GdsRegistrationService.compatibleToken(
            new UserTokenPolicy(
                "unknown", UserTokenType.UserName, null, null, "urn:unsupported-policy"),
            endpoint,
            UserTokenType.UserName));
  }

  @Test
  void retriesOnlyRecoverableFailures() {
    assertFalse(
        GdsRegistrationService.retryable(
            new LinkageError("missing codec", new java.io.IOException("wrapped I/O failure"))));
    assertTrue(GdsRegistrationService.retryable(new UaException(new java.net.ConnectException())));
    for (long code :
        List.of(
            StatusCodes.Bad_Timeout,
            StatusCodes.Bad_ConnectionRejected,
            StatusCodes.Bad_OutOfService,
            StatusCodes.Bad_ServerTooBusy,
            StatusCodes.Bad_TooManySessions,
            StatusCodes.Bad_CertificateUntrusted,
            StatusCodes.Bad_ServerHalted)) {
      assertTrue(
          GdsRegistrationService.retryable(new UaException(code)),
          new UaException(code).toString());
    }
    for (long code :
        List.of(
            StatusCodes.Bad_UserAccessDenied,
            StatusCodes.Bad_IdentityTokenRejected,
            StatusCodes.Bad_NotSupported,
            StatusCodes.Bad_MethodInvalid)) {
      assertFalse(
          GdsRegistrationService.retryable(new UaException(code)),
          new UaException(code).toString());
    }
  }

  // A linkage failure is captured by the scheduled task Future unless the worker reports it.
  // Completion must fail instead of leaving callers waiting forever for registration.
  @Test
  void workerReportsLinkageFailure(@TempDir Path directory) throws Exception {
    var failure = new NoClassDefFoundError("missing GDS codec");
    OpcUaServer server =
        new OpcUaServer(
            OpcUaServerConfig.builder()
                .setCertificateManager(new DefaultCertificateManager())
                .build(),
            _ -> null) {
          @Override
          public ServerApplicationContext getApplicationContext() {
            throw failure;
          }
        };
    GdsRegistrationConfig config =
        GdsRegistrationConfig.fromConfig(
                ConfigFactory.parseString(
                    """
                    gds.registration {
                      enabled = true
                      endpoint-url = "opc.tcp://localhost:4840/gds"
                      identity { username = "admin", password = "password" }
                    }
                    """))
            .orElseThrow();
    try (var service = new GdsRegistrationService(server, config, directory)) {
      service.start();
      ExecutionException error =
          assertThrows(
              ExecutionException.class, () -> service.completion().get(2, TimeUnit.SECONDS));
      assertSame(failure, error.getCause());
      assertFalse(GdsRegistrationService.retryable(failure));
    }
  }

  private static ApplicationRecordDataType record(
      String uri,
      ApplicationType type,
      String product,
      String @Nullable [] urls,
      String @Nullable [] capabilities) {
    return new ApplicationRecordDataType(
        NodeId.NULL_VALUE,
        uri,
        type,
        new LocalizedText[] {LocalizedText.english("Demo")},
        product,
        urls,
        capabilities);
  }

  private static EndpointDescription endpoint(
      SecurityPolicy policy, MessageSecurityMode mode, UserTokenType tokenType) {
    return new EndpointDescription(
        "opc.tcp://localhost:1234",
        null,
        null,
        mode,
        policy.getUri(),
        new UserTokenPolicy[] {new UserTokenPolicy("token", tokenType, null, null, null)},
        Stack.TCP_UASC_UABINARY_TRANSPORT_URI,
        ubyte(0));
  }
}

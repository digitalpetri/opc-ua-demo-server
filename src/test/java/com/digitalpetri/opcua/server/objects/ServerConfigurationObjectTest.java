package com.digitalpetri.opcua.server.objects;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.eclipse.milo.opcua.stack.core.StatusCodes;
import org.eclipse.milo.opcua.stack.core.UaException;
import org.eclipse.milo.opcua.stack.core.types.builtin.ByteString;
import org.junit.jupiter.api.Test;

class ServerConfigurationObjectTest {

  // Part 12 §7.10.10 requires at least 32 bytes of caller-supplied entropy only when a new
  // PrivateKey is requested and specifies Bad_InvalidArgument below that boundary.
  @Test
  void privateKeyRegenerationEnforcesNonceRequirement() {
    UaException exception =
        assertThrows(
            UaException.class,
            () ->
                ServerConfigurationObject.validateRegeneratePrivateKeyNonce(
                    true, ByteString.of(new byte[31])));

    assertEquals(StatusCodes.Bad_InvalidArgument, exception.getStatusCode().getValue());
    assertDoesNotThrow(
        () ->
            ServerConfigurationObject.validateRegeneratePrivateKeyNonce(
                true, ByteString.of(new byte[32])));
    assertDoesNotThrow(
        () -> ServerConfigurationObject.validateRegeneratePrivateKeyNonce(false, null));
  }
}

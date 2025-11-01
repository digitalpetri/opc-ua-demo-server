package com.digitalpetri.opcua.server;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import org.eclipse.milo.opcua.sdk.client.OpcUaClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Integration tests for OpcUaDemoServer.
 *
 * <p>These tests verify that the server can be started and stopped correctly, with automatic port
 * selection to avoid conflicts.
 */
class OpcUaDemoServerIT {

  private static final Logger logger = LoggerFactory.getLogger(OpcUaDemoServerIT.class);

  private OpcUaDemoServer server;

  @AfterEach
  void tearDown() {
    if (server != null) {
      server.shutdown();
    }
  }

  @Test
  void testClientCanConnect(@TempDir Path tempDir) throws Exception {
    // Given: A running server
    server = OpcUaTestServerBuilder.builder().withDataDir(tempDir).build();
    server.startup();

    // When: A client connects to the server
    OpcUaClient client = OpcUaTestClient.create(server.getServer());

    try {
      client.connect();

      // Then: The client should be connected and have an active session
      assertNotNull(client.getSession(), "Client should have an active session");
      logger.info("Client connected successfully to server");

    } finally {
      // Clean up
      client.disconnect();
    }
  }

  @Test
  void testMultipleServersWithConnectedClients(@TempDir Path tempDir1, @TempDir Path tempDir2)
      throws Exception {

    // Given: Two servers both using automatic port selection
    OpcUaDemoServer server1 = OpcUaTestServerBuilder.builder().withDataDir(tempDir1).build();
    OpcUaDemoServer server2 = OpcUaTestServerBuilder.builder().withDataDir(tempDir2).build();

    try {
      // When: Both servers are started
      server1.startup();
      server2.startup();

      // Then: Both servers should start successfully without port conflicts
      logger.info("Both servers started successfully with automatic port selection");

      // And: Clients can connect to both servers
      OpcUaClient client1 = OpcUaTestClient.create(server1.getServer());
      OpcUaClient client2 = OpcUaTestClient.create(server2.getServer());

      try {
        client1.connect();
        client2.connect();

        // Verify both clients have active sessions
        assertNotNull(client1.getSession(), "Client 1 should have an active session");
        assertNotNull(client2.getSession(), "Client 2 should have an active session");

        logger.info("Clients connected successfully to both servers");

      } finally {
        // Clean up clients
        client1.disconnect();
        client2.disconnect();
      }

    } finally {
      // Clean up both servers
      server1.shutdown();
      server2.shutdown();
    }
  }
}

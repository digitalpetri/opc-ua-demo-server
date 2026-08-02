package com.digitalpetri.opcua.server.objects;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.digitalpetri.opcua.server.OpcUaDemoServer;
import com.digitalpetri.opcua.server.OpcUaTestServerBuilder;
import com.typesafe.config.ConfigFactory;
import java.nio.file.Path;
import java.util.Map;
import org.eclipse.milo.opcua.sdk.server.model.objects.ServerConfigurationTypeNode;
import org.eclipse.milo.opcua.stack.core.NodeIds;
import org.eclipse.milo.opcua.stack.core.types.builtin.NodeId;
import org.eclipse.milo.opcua.stack.core.types.enumerated.ApplicationType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ServerConfigurationObjectIT {

  private OpcUaDemoServer demoServer;
  private ServerConfigurationTypeNode serverConfiguration;

  @BeforeEach
  void setUp(@TempDir Path tempDir) throws Exception {
    var config = ConfigFactory.parseMap(Map.of("gds-push-enabled", true));

    demoServer = OpcUaTestServerBuilder.builder().withDataDir(tempDir).withConfig(config).build();
    demoServer.startup();

    serverConfiguration =
        assertInstanceOf(
            ServerConfigurationTypeNode.class,
            demoServer
                .getServer()
                .getAddressSpaceManager()
                .getManagedNode(NodeIds.ServerConfiguration)
                .orElseThrow());
  }

  @AfterEach
  void tearDown() {
    if (demoServer != null) {
      demoServer.shutdown();
    }
  }

  // Part 12 §7.10.3: optional application identity values must describe this application when
  // present, and ServerCapabilities must use Annex D identifiers supported by the application.
  @Test
  void publishesTruthfulApplicationIdentityAndCapabilities() {
    var server = demoServer.getServer();

    assertEquals(server.getConfig().getApplicationUri(), serverConfiguration.getApplicationUri());
    assertEquals(server.getConfig().getProductUri(), serverConfiguration.getProductUri());
    assertEquals(ApplicationType.Server, serverConfiguration.getApplicationType());
    assertArrayEquals(new String[] {"DA", "AC"}, serverConfiguration.getServerCapabilities());
  }

  // Part 12 §7.10.3 marks these components optional; exposing executable or readable nodes
  // without their transaction, reset, diagnostics, or configuration-file behavior is misleading.
  @Test
  void unsupportedOptionalComponentsAreNotExposed() {
    assertNodeRemoved(NodeIds.ServerConfiguration_CancelChanges);
    assertNodeRemoved(NodeIds.ServerConfiguration_ResetToServerDefaults);
    assertNodeRemoved(NodeIds.ServerConfiguration_TransactionDiagnostics);
    assertNodeRemoved(NodeIds.ServerConfiguration_ConfigurationFile);
  }

  private void assertNodeRemoved(NodeId nodeId) {
    assertTrue(
        demoServer.getServer().getAddressSpaceManager().getManagedNode(nodeId).isEmpty(),
        () -> nodeId + " should have been removed");
  }
}

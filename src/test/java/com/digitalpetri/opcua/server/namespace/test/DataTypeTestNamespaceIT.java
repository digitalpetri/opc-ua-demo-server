package com.digitalpetri.opcua.server.namespace.test;

import static org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.Unsigned.uint;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.digitalpetri.opcua.server.OpcUaDemoServer;
import com.digitalpetri.opcua.server.OpcUaTestClient;
import com.digitalpetri.opcua.server.OpcUaTestServerBuilder;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import org.eclipse.milo.opcua.sdk.client.OpcUaClient;
import org.eclipse.milo.opcua.stack.core.NodeIds;
import org.eclipse.milo.opcua.stack.core.types.builtin.DataValue;
import org.eclipse.milo.opcua.stack.core.types.builtin.NodeId;
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.UShort;
import org.eclipse.milo.opcua.stack.core.types.enumerated.BrowseDirection;
import org.eclipse.milo.opcua.stack.core.types.enumerated.BrowseResultMask;
import org.eclipse.milo.opcua.stack.core.types.enumerated.IdType;
import org.eclipse.milo.opcua.stack.core.types.enumerated.NodeClass;
import org.eclipse.milo.opcua.stack.core.types.enumerated.TimestampsToReturn;
import org.eclipse.milo.opcua.stack.core.types.structured.BrowseDescription;
import org.eclipse.milo.opcua.stack.core.types.structured.BrowseResult;
import org.eclipse.milo.opcua.stack.core.types.structured.ReferenceDescription;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DataTypeTestNamespaceIT {

  private static final int NAMESPACE_METADATA_NODE_ID = 5010;
  private static final int STATIC_NODE_ID_TYPES_NODE_ID = 6015;
  private static final int STATIC_NUMERIC_NODE_ID_RANGE_NODE_ID = 6016;
  private static final int STATIC_STRING_NODE_ID_PATTERN_NODE_ID = 6017;

  private OpcUaDemoServer server;
  private OpcUaClient client;

  @BeforeEach
  void setUp(@TempDir Path tempDir) throws Exception {
    Map<String, Object> configMap = new HashMap<>();
    configMap.put("address-space.data-type-test.enabled", true);
    Config customConfig = ConfigFactory.parseMap(configMap);

    server = OpcUaTestServerBuilder.builder().withDataDir(tempDir).withConfig(customConfig).build();
    server.startup();

    client = OpcUaTestClient.create(server.getServer());
    client.connect();
  }

  @AfterEach
  void tearDown() throws Exception {
    if (client != null) {
      client.disconnect();
    }
    if (server != null) {
      server.shutdown();
    }
  }

  // Part 5 §§6.3.13–6.3.14: the generated metadata entry must remain exposed after the demo fills
  // the three static-NodeId declarations that previously returned Bad_NoValue.
  @Test
  void generatedNamespaceMetadataIsCompleteAndReadable() throws Exception {
    UShort namespaceIndex =
        server.getServer().getNamespaceTable().getIndex(DataTypeTestNamespace.NAMESPACE_URI);
    assertNotNull(namespaceIndex, "DataTypeTest namespace should be registered");

    NodeId metadataNodeId = new NodeId(namespaceIndex, NAMESPACE_METADATA_NODE_ID);
    assertTrue(
        server.getServer().getAddressSpaceManager().getManagedNode(metadataNodeId).isPresent(),
        "generated NamespaceMetadataType node should be exposed");
    assertTrue(
        serverNamespacesContains(namespaceIndex, DataTypeTestNamespace.NAMESPACE_URI),
        "Server.Namespaces should reference the generated NamespaceMetadataType node");

    assertArrayEquals(
        new Integer[] {IdType.Numeric.getValue()},
        (Integer[])
            readGoodValue(namespaceIndex, STATIC_NODE_ID_TYPES_NODE_ID).getValue().getValue());
    assertArrayEquals(
        new String[] {"3003:6070"},
        (String[])
            readGoodValue(namespaceIndex, STATIC_NUMERIC_NODE_ID_RANGE_NODE_ID)
                .getValue()
                .getValue());
    assertEquals(
        "",
        readGoodValue(namespaceIndex, STATIC_STRING_NODE_ID_PATTERN_NODE_ID).getValue().getValue());
  }

  private DataValue readGoodValue(UShort namespaceIndex, int identifier) throws Exception {
    NodeId nodeId = new NodeId(namespaceIndex, identifier);
    DataValue value = client.readValue(0.0, TimestampsToReturn.Neither, nodeId);

    assertTrue(value.getStatusCode().isGood(), () -> nodeId + " should have a Good value");

    return value;
  }

  private boolean serverNamespacesContains(UShort namespaceIndex, String browseName)
      throws Exception {

    BrowseDescription browseDescription =
        new BrowseDescription(
            NodeIds.Server_Namespaces,
            BrowseDirection.Forward,
            NodeIds.HasComponent,
            true,
            uint(NodeClass.Object.getValue()),
            uint(BrowseResultMask.All.getValue()));

    BrowseResult browseResult = client.browse(browseDescription);
    ReferenceDescription[] references = browseResult.getReferences();

    return references != null
        && Arrays.stream(references)
            .anyMatch(
                ref ->
                    Objects.equals(ref.getBrowseName().getNamespaceIndex(), namespaceIndex)
                        && Objects.equals(ref.getBrowseName().getName(), browseName));
  }
}

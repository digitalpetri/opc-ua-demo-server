package com.digitalpetri.opcua.server.namespace.demo;

import static org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.Unsigned.uint;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.digitalpetri.opcua.server.OpcUaDemoServer;
import com.digitalpetri.opcua.server.OpcUaTestClient;
import com.digitalpetri.opcua.server.OpcUaTestServerBuilder;
import java.lang.reflect.Array;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.eclipse.milo.opcua.sdk.client.OpcUaClient;
import org.eclipse.milo.opcua.stack.core.NodeIds;
import org.eclipse.milo.opcua.stack.core.types.builtin.DataValue;
import org.eclipse.milo.opcua.stack.core.types.builtin.DateTime;
import org.eclipse.milo.opcua.stack.core.types.builtin.NodeId;
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.UShort;
import org.eclipse.milo.opcua.stack.core.types.enumerated.BrowseDirection;
import org.eclipse.milo.opcua.stack.core.types.enumerated.BrowseResultMask;
import org.eclipse.milo.opcua.stack.core.types.enumerated.NodeClass;
import org.eclipse.milo.opcua.stack.core.types.enumerated.TimestampsToReturn;
import org.eclipse.milo.opcua.stack.core.types.structured.BrowseDescription;
import org.eclipse.milo.opcua.stack.core.types.structured.BrowseResult;
import org.eclipse.milo.opcua.stack.core.types.structured.ReferenceDescription;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class NamespaceMetadataIT {

  private static final Set<String> MANDATORY_PROPERTIES =
      Set.of(
          "NamespaceUri",
          "NamespaceVersion",
          "NamespacePublicationDate",
          "IsNamespaceSubset",
          "StaticNodeIdTypes",
          "StaticNumericNodeIdRange",
          "StaticStringNodeIdPattern");

  private OpcUaDemoServer server;
  private OpcUaClient client;

  @BeforeEach
  void setUp(@TempDir Path tempDir) throws Exception {
    server = OpcUaTestServerBuilder.builder().withDataDir(tempDir).build();
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

  // Part 5 §§6.3.13–6.3.14: each dynamic namespace entry must expose all mandatory metadata with
  // truthful readable values.
  @Test
  void dynamicNamespacesPublishCompleteMetadata() throws Exception {
    assertCompleteDynamicMetadata(server.getServer().getConfig().getApplicationUri(), false);
    assertCompleteDynamicMetadata(DemoNamespace.NAMESPACE_URI, true);
  }

  private void assertCompleteDynamicMetadata(String namespaceUri, boolean isNamespaceSubset)
      throws Exception {
    UShort namespaceIndex = client.getNamespaceTable().getIndex(namespaceUri);
    assertNotNull(namespaceIndex, () -> "namespace should be registered: " + namespaceUri);

    ReferenceDescription metadataReference = findMetadataReference(namespaceIndex, namespaceUri);
    assertEquals(namespaceIndex, metadataReference.getBrowseName().getNamespaceIndex());

    NodeId metadataNodeId =
        metadataReference
            .getNodeId()
            .toNodeId(client.getNamespaceTable())
            .orElseThrow(() -> new AssertionError("metadata NodeId is not local"));
    Map<String, NodeId> properties = browseProperties(metadataNodeId);

    assertTrue(
        properties.keySet().containsAll(MANDATORY_PROPERTIES),
        () -> "missing mandatory metadata Properties: " + properties.keySet());

    Map<String, DataValue> values =
        MANDATORY_PROPERTIES.stream()
            .collect(
                Collectors.toMap(
                    Function.identity(),
                    name -> readGoodValue(properties.get(name), namespaceUri)));

    assertEquals(namespaceUri, scalarValue(values, "NamespaceUri"));
    assertNull(scalarValue(values, "NamespaceVersion"));
    assertTrue(((DateTime) scalarValue(values, "NamespacePublicationDate")).isNull());
    assertEquals(isNamespaceSubset, scalarValue(values, "IsNamespaceSubset"));
    assertEmptyArray(scalarValue(values, "StaticNodeIdTypes"), "StaticNodeIdTypes");
    assertEmptyArray(scalarValue(values, "StaticNumericNodeIdRange"), "StaticNumericNodeIdRange");
    assertEquals("", scalarValue(values, "StaticStringNodeIdPattern"));
  }

  private ReferenceDescription findMetadataReference(UShort namespaceIndex, String namespaceUri)
      throws Exception {

    BrowseResult result =
        client.browse(
            new BrowseDescription(
                NodeIds.Server_Namespaces,
                BrowseDirection.Forward,
                NodeIds.HasComponent,
                true,
                uint(NodeClass.Object.getValue()),
                uint(BrowseResultMask.All.getValue())));

    assertTrue(result.getStatusCode().isGood(), "browsing Server.Namespaces should succeed");

    ReferenceDescription[] references = result.getReferences();
    assertNotNull(references, "Server.Namespaces should have metadata entries");

    return Arrays.stream(references)
        .filter(ref -> namespaceIndex.equals(ref.getBrowseName().getNamespaceIndex()))
        .filter(ref -> namespaceUri.equals(ref.getBrowseName().getName()))
        .findFirst()
        .orElseThrow(() -> new AssertionError("missing metadata entry: " + namespaceUri));
  }

  private Map<String, NodeId> browseProperties(NodeId metadataNodeId) throws Exception {
    BrowseResult result =
        client.browse(
            new BrowseDescription(
                metadataNodeId,
                BrowseDirection.Forward,
                NodeIds.HasProperty,
                true,
                uint(NodeClass.Variable.getValue()),
                uint(BrowseResultMask.All.getValue())));

    assertTrue(result.getStatusCode().isGood(), () -> "browsing should succeed: " + metadataNodeId);

    ReferenceDescription[] references = result.getReferences();
    assertNotNull(references, () -> metadataNodeId + " should have metadata Properties");

    return Arrays.stream(references)
        .collect(
            Collectors.toMap(
                ref -> ref.getBrowseName().getName(),
                ref ->
                    ref.getNodeId()
                        .toNodeId(client.getNamespaceTable())
                        .orElseThrow(() -> new AssertionError("Property NodeId is not local"))));
  }

  private DataValue readGoodValue(NodeId nodeId, String namespaceUri) {
    try {
      DataValue value = client.readValue(0.0, TimestampsToReturn.Neither, nodeId);
      assertTrue(
          value.getStatusCode().isGood(),
          () -> namespaceUri + " metadata Property should have a Good value: " + nodeId);
      return value;
    } catch (Exception e) {
      throw new AssertionError("failed to read metadata Property: " + nodeId, e);
    }
  }

  private static Object scalarValue(Map<String, DataValue> values, String propertyName) {
    return values.get(propertyName).getValue().getValue();
  }

  private static void assertEmptyArray(Object value, String propertyName) {
    assertNotNull(value, () -> propertyName + " should be an empty array, not null");
    assertTrue(value.getClass().isArray(), () -> propertyName + " should be an array");
    assertEquals(0, Array.getLength(value), () -> propertyName + " should be empty");
  }
}

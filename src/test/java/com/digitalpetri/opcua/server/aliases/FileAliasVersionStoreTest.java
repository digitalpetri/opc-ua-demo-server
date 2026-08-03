package com.digitalpetri.opcua.server.aliases;

import static org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.Unsigned.uint;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.digitalpetri.opcua.server.namespace.demo.DemoNamespace;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.eclipse.milo.opcua.stack.core.NamespaceTable;
import org.eclipse.milo.opcua.stack.core.StatusCodes;
import org.eclipse.milo.opcua.stack.core.UaException;
import org.eclipse.milo.opcua.stack.core.types.builtin.ExpandedNodeId;
import org.eclipse.milo.opcua.stack.core.types.builtin.NodeId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FileAliasVersionStoreTest {

  private final NamespaceTable namespaceTable =
      new NamespaceTable("urn:test:application", DemoNamespace.NAMESPACE_URI);

  @Test
  void missingFileLoadsEmpty(@TempDir Path tempDir) throws Exception {
    var store =
        new FileAliasVersionStore(tempDir.resolve("aliases/versions.properties"), namespaceTable);

    assertTrue(store.load().isEmpty());
  }

  // Part 17 clients cache alias results by LastChange, so a process restart must recover exactly
  // the versions that the previous store instance persisted.
  @Test
  void savedVersionsRoundTripThroughFreshStore(@TempDir Path tempDir) throws Exception {
    Path filePath = tempDir.resolve("aliases/versions.properties");
    var firstStore = new FileAliasVersionStore(filePath, namespaceTable);

    ExpandedNodeId rootCategoryId = NodeId.parse("i=23470").expanded(namespaceTable);
    ExpandedNodeId staticCategoryId =
        NodeId.parse("ns=2;s=Aliases/MiloDemoStatic").expanded(namespaceTable);

    firstStore.load();
    firstStore.save(rootCategoryId, uint(100));
    firstStore.save(staticCategoryId, uint(101));

    var secondStore = new FileAliasVersionStore(filePath, namespaceTable);
    assertEquals(
        Map.of(rootCategoryId, uint(100), staticCategoryId, uint(101)), secondStore.load());
  }

  // A failed save aborts the corresponding address-space mutation; retaining its version in the
  // next successful snapshot would persist a LastChange for content the manager never accepted.
  @Test
  void failedSaveDoesNotLeakRejectedVersionIntoLaterSnapshot(@TempDir Path tempDir)
      throws Exception {

    Path parentPath = tempDir.resolve("aliases");
    Path filePath = parentPath.resolve("versions.properties");
    Files.createDirectory(parentPath);

    var store = new FileAliasVersionStore(filePath, namespaceTable);
    ExpandedNodeId rejectedCategoryId =
        NodeId.parse("ns=2;s=Aliases/Rejected").expanded(namespaceTable);
    ExpandedNodeId savedCategoryId = NodeId.parse("ns=2;s=Aliases/Saved").expanded(namespaceTable);

    assertTrue(store.load().isEmpty());

    Files.delete(parentPath);
    Files.writeString(parentPath, "blocks directory creation");
    UaException exception =
        assertThrows(UaException.class, () -> store.save(rejectedCategoryId, uint(100)));
    assertEquals(StatusCodes.Bad_UnexpectedError, exception.getStatusCode().value());

    Files.delete(parentPath);
    Files.createDirectory(parentPath);
    store.save(savedCategoryId, uint(101));

    var reloadedStore = new FileAliasVersionStore(filePath, namespaceTable);
    assertEquals(Map.of(savedCategoryId, uint(101)), reloadedStore.load());
  }

  @Test
  void legacyNamespaceIndexKeyMigratesToNamespaceUri(@TempDir Path tempDir) throws Exception {
    Path filePath = tempDir.resolve("versions.properties");
    Files.writeString(filePath, "ns\\=2;s\\=Aliases/MiloDemoStatic=101\n");

    var store = new FileAliasVersionStore(filePath, namespaceTable);
    ExpandedNodeId staticCategoryId =
        NodeId.parse("ns=2;s=Aliases/MiloDemoStatic").expanded(namespaceTable);

    assertEquals(Map.of(staticCategoryId, uint(101)), store.load());

    store.save(NodeId.parse("i=23470").expanded(namespaceTable), uint(102));

    assertEquals(
        Map.of(
            staticCategoryId,
            uint(101),
            NodeId.parse("i=23470").expanded(namespaceTable),
            uint(102)),
        new FileAliasVersionStore(filePath, namespaceTable).load());
  }

  // A partial load would silently reset or mix LastChange state and can leave client caches stale.
  @Test
  void malformedEntryFailsEntireLoad(@TempDir Path tempDir) throws Exception {
    Path filePath = tempDir.resolve("versions.properties");
    Files.writeString(filePath, "invalid-node-id=1\n");
    var store = new FileAliasVersionStore(filePath, namespaceTable);

    UaException exception = assertThrows(UaException.class, store::load);

    assertEquals(StatusCodes.Bad_DecodingError, exception.getStatusCode().value());
  }

  @Test
  void outOfRangeVersionFailsLoad(@TempDir Path tempDir) throws Exception {
    Path filePath = tempDir.resolve("versions.properties");
    Files.writeString(filePath, "ns\\=2;s\\=Aliases/MiloDemoStatic=4294967296\n");
    var store = new FileAliasVersionStore(filePath, namespaceTable);

    UaException exception = assertThrows(UaException.class, store::load);

    assertEquals(StatusCodes.Bad_DecodingError, exception.getStatusCode().value());
  }
}

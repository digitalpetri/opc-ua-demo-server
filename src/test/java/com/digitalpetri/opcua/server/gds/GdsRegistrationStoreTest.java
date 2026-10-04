package com.digitalpetri.opcua.server.gds;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import org.eclipse.milo.opcua.stack.core.NamespaceTable;
import org.eclipse.milo.opcua.stack.core.types.builtin.NodeId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GdsRegistrationStoreTest {
  @TempDir Path directory;

  @Test
  void persistsNamespaceUriAcrossIndexChangesAndInvalidatesChangedIdentities() throws Exception {
    Path path = directory.resolve("gds/registration.json");
    var store = new GdsRegistrationStore(path);
    var original = new NamespaceTable();
    original.set(2, "urn:gds:applications");
    var id = new NodeId(2, "demo").expanded().absolute(original).orElseThrow();
    var state =
        new GdsRegistrationStore.State(
            "opc.tcp://localhost:58810", "urn:gds", "urn:demo", id, Instant.now());
    store.save(state);
    assertEquals(state, store.load(state.endpointUrl(), "urn:gds", "urn:demo").orElseThrow());
    var changed = new NamespaceTable();
    changed.set(5, "urn:gds:applications");
    assertEquals(
        new NodeId(5, "demo"),
        store
            .load(state.endpointUrl(), "urn:gds", "urn:demo")
            .orElseThrow()
            .applicationId()
            .toNodeIdOrThrow(changed));
    assertTrue(store.load("opc.tcp://elsewhere:58810", "urn:gds", "urn:demo").isEmpty());
    assertTrue(store.load(state.endpointUrl(), "urn:other-gds", "urn:demo").isEmpty());
    assertTrue(store.load(state.endpointUrl(), "urn:gds", "urn:other-demo").isEmpty());
    assertFalse(Files.readString(path).contains("password"));
  }

  @Test
  void missingMalformedAndIndexOnlyStateDoNotPreventLookup() throws Exception {
    Path path = directory.resolve("registration.json");
    var store = new GdsRegistrationStore(path);
    assertTrue(store.load("a", "b", "c").isEmpty());
    Files.writeString(path, "{broken");
    assertTrue(store.load("a", "b", "c").isEmpty());
    Files.writeString(
        path,
        """
        {"version":1,"endpointUrl":"a","gdsApplicationUri":"b","applicationUri":"c",
        "applicationId":"ns=3;i=8","registeredAt":"2026-09-28T00:00:00Z"}
        """);
    assertTrue(store.load("a", "b", "c").isEmpty());
  }

  @Test
  void failedReplacementPreservesPreviousStateAndRemovesTemporaryFile() throws Exception {
    Path path = directory.resolve("registration.json");
    Files.createDirectory(path);
    Files.writeString(path.resolve("keep"), "previous state");
    var store = new GdsRegistrationStore(path);
    var id = new NodeId(0, "demo").expanded().absolute(new NamespaceTable()).orElseThrow();
    assertThrows(
        java.io.IOException.class,
        () -> store.save(new GdsRegistrationStore.State("a", "b", "c", id, Instant.now())));
    assertEquals("previous state", Files.readString(path.resolve("keep")));
    try (var files = Files.list(directory)) {
      assertEquals(1, files.count());
    }
  }
}

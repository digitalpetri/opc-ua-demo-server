package com.digitalpetri.opcua.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.typesafe.config.ConfigFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class OpcUaDemoServerConstructionTest {
  // Construction never reaches the lifecycle's startup/shutdown cleanup on failure.
  @Test
  void laterConstructionFailureClosesBothWatchers(@TempDir Path directory) {
    Set<Thread> before = watchers();
    assertThrows(
        IllegalArgumentException.class,
        () ->
            OpcUaTestServerBuilder.builder()
                .withDataDir(directory)
                .withConfig(ConfigFactory.parseString("security-policy-list=[Bogus]"))
                .build());
    assertEquals(before, watchers(), "failed construction must not leak watcher threads");
  }

  // If the second manager cannot initialize, the first already owns a watcher.
  @Test
  void secondManagerFailureClosesFirstWatcher(@TempDir Path directory) throws Exception {
    Path userPki = directory.resolve("security/pki-user");
    Files.createDirectories(userPki);
    Files.createFile(userPki.resolve("issuer"));
    Set<Thread> before = watchers();
    assertThrows(
        Exception.class, () -> OpcUaTestServerBuilder.builder().withDataDir(directory).build());
    assertEquals(before, watchers(), "failed second manager must not leak the first watcher");
  }

  private static Set<Thread> watchers() {
    return Thread.getAllStackTraces().keySet().stream()
        .filter(t -> t.isAlive() && t.getName().equals("milo-trust-list-watcher"))
        .collect(Collectors.toSet());
  }
}

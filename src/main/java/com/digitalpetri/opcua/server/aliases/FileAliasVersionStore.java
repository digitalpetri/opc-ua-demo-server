package com.digitalpetri.opcua.server.aliases;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import org.eclipse.milo.opcua.sdk.server.aliases.AliasVersionStore;
import org.eclipse.milo.opcua.stack.core.NamespaceTable;
import org.eclipse.milo.opcua.stack.core.StatusCodes;
import org.eclipse.milo.opcua.stack.core.UaException;
import org.eclipse.milo.opcua.stack.core.types.builtin.ExpandedNodeId;
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.UInteger;

/**
 * Persists OPC UA Part 17 category {@code LastChange} values in a properties file.
 *
 * <p>The {@link AliasVersionStore} contract serializes access, so this class does not add its own
 * locking. Each save flushes a complete temporary sibling to stable storage before replacing the
 * prior file atomically where supported. Filesystems without atomic move support receive the same
 * fully written temporary file, but cannot guarantee atomic replacement. The first successful store
 * creation also forces its ancestor directories where supported, repairing directory entries that
 * an earlier failed creation attempt may have left unsynchronized.
 */
final class FileAliasVersionStore implements AliasVersionStore {

  private final Path filePath;
  private final NamespaceTable namespaceTable;
  private final Map<ExpandedNodeId, UInteger> versions = new HashMap<>();

  /**
   * Creates a version store backed by {@code filePath}.
   *
   * @param filePath the properties file to load and replace.
   * @param namespaceTable the server namespace table used to migrate legacy index-based keys.
   */
  FileAliasVersionStore(Path filePath, NamespaceTable namespaceTable) {
    this.filePath = filePath;
    this.namespaceTable = namespaceTable;
  }

  @Override
  public Map<ExpandedNodeId, UInteger> load() throws UaException {
    if (Files.notExists(filePath)) {
      versions.clear();
      return Map.of();
    }

    var properties = new Properties();

    try (InputStream inputStream = Files.newInputStream(filePath)) {
      properties.load(inputStream);
    } catch (IOException | IllegalArgumentException e) {
      throw new UaException(
          StatusCodes.Bad_DecodingError, "failed to load alias version store: " + filePath, e);
    }

    var loaded = new HashMap<ExpandedNodeId, UInteger>();

    for (String key : properties.stringPropertyNames()) {
      String value = properties.getProperty(key);

      try {
        ExpandedNodeId categoryId = ExpandedNodeId.parse(key);
        if (categoryId.isRelative()) {
          categoryId =
              categoryId
                  .absolute(namespaceTable)
                  .orElseThrow(
                      () ->
                          new IllegalArgumentException(
                              "unregistered namespace index in legacy alias version key: " + key));
        }
        loaded.put(categoryId, UInteger.valueOf(value));
      } catch (RuntimeException e) {
        throw new UaException(
            StatusCodes.Bad_DecodingError,
            "invalid alias version entry %s=%s in %s".formatted(key, value, filePath),
            e);
      }
    }

    versions.clear();
    versions.putAll(loaded);

    return Map.copyOf(versions);
  }

  @Override
  public void save(ExpandedNodeId categoryId, UInteger value) throws UaException {
    if (categoryId.isRelative()) {
      throw new UaException(
          StatusCodes.Bad_InvalidArgument,
          "alias version category key must be namespace-URI-qualified: "
              + categoryId.toParseableString());
    }

    Map<ExpandedNodeId, UInteger> candidateVersions = new HashMap<>(versions);
    candidateVersions.put(categoryId, value);

    Path parentPath = filePath.toAbsolutePath().getParent();
    Path temporaryPath = null;

    try {
      boolean initializingStore = Files.notExists(filePath);
      Files.createDirectories(parentPath);
      temporaryPath = Files.createTempFile(parentPath, filePath.getFileName().toString(), ".tmp");

      var properties = new Properties();
      candidateVersions.forEach(
          (nodeId, version) ->
              properties.setProperty(nodeId.toParseableString(), version.toString()));

      try (FileChannel fileChannel =
              FileChannel.open(
                  temporaryPath, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING);
          OutputStream outputStream = Channels.newOutputStream(fileChannel)) {
        properties.store(outputStream, "OPC UA Alias Names LastChange versions");
        outputStream.flush();
        fileChannel.force(true);
      }

      replaceFile(temporaryPath);
      temporaryPath = null;
      forceDirectory(parentPath);
      if (initializingStore) {
        forceAncestorDirectories(parentPath);
      }

      versions.clear();
      versions.putAll(candidateVersions);
    } catch (IOException e) {
      throw new UaException(
          StatusCodes.Bad_UnexpectedError, "failed to save alias version store: " + filePath, e);
    } finally {
      if (temporaryPath != null) {
        try {
          Files.deleteIfExists(temporaryPath);
        } catch (IOException ignored) {
          // The original write/move failure is the actionable error.
        }
      }
    }
  }

  private void replaceFile(Path temporaryPath) throws IOException {
    try {
      Files.move(
          temporaryPath,
          filePath,
          StandardCopyOption.ATOMIC_MOVE,
          StandardCopyOption.REPLACE_EXISTING);
    } catch (AtomicMoveNotSupportedException e) {
      Files.move(temporaryPath, filePath, StandardCopyOption.REPLACE_EXISTING);
    }
  }

  private static void forceAncestorDirectories(Path directoryPath) {
    for (Path ancestor = directoryPath.getParent();
        ancestor != null;
        ancestor = ancestor.getParent()) {
      forceDirectory(ancestor);
    }
  }

  private static void forceDirectory(Path directoryPath) {
    try (FileChannel directoryChannel = FileChannel.open(directoryPath, StandardOpenOption.READ)) {
      directoryChannel.force(true);
    } catch (IOException | UnsupportedOperationException ignored) {
      // Some filesystems and platforms do not support opening or forcing a directory. The data
      // file itself was still forced before replacement.
    }
  }
}

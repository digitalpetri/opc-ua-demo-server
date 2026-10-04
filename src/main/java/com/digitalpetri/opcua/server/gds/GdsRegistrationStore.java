package com.digitalpetri.opcua.server.gds;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import com.typesafe.config.ConfigParseOptions;
import com.typesafe.config.ConfigRenderOptions;
import com.typesafe.config.ConfigSyntax;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import org.eclipse.milo.opcua.stack.core.types.builtin.ExpandedNodeId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Remembers a successful registration. Remote lookup remains authoritative. */
final class GdsRegistrationStore {
  private static final Logger LOGGER = LoggerFactory.getLogger(GdsRegistrationStore.class);
  private final Path path;
  private boolean warned;

  record State(
      String endpointUrl,
      String gdsApplicationUri,
      String applicationUri,
      ExpandedNodeId applicationId,
      Instant registeredAt) {}

  GdsRegistrationStore(Path path) {
    this.path = path;
  }

  Optional<State> load(String endpointUrl, String gdsApplicationUri, String applicationUri) {
    try {
      Config json =
          ConfigFactory.parseString(
              Files.readString(path), ConfigParseOptions.defaults().setSyntax(ConfigSyntax.JSON));
      if (json.getInt("version") != 1) throw new IllegalArgumentException();
      var state =
          new State(
              json.getString("endpointUrl"),
              json.getString("gdsApplicationUri"),
              json.getString("applicationUri"),
              ExpandedNodeId.parse(json.getString("applicationId")),
              Instant.parse(json.getString("registeredAt")));
      if (state.applicationId().isRelative() || state.applicationId().isNull()) {
        throw new IllegalArgumentException();
      }
      if (!endpointUrl.equals(state.endpointUrl())
          || !gdsApplicationUri.equals(state.gdsApplicationUri())
          || !applicationUri.equals(state.applicationUri())) {
        warn(
            "GDS remembered registration belongs to a different GDS or application; looking up again");
        return Optional.empty();
      }
      warned = false;
      return Optional.of(state);
    } catch (IOException | RuntimeException e) {
      warn("GDS remembered registration is missing or malformed; looking up again");
      return Optional.empty();
    }
  }

  private void warn(String message) {
    if (warned) LOGGER.debug(message);
    else LOGGER.warn(message);
    warned = true;
  }

  void save(State state) throws IOException {
    if (state.applicationId().isRelative() || state.applicationId().isNull()) {
      throw new IllegalArgumentException(
          "ApplicationId must be namespace-URI-qualified and nonempty");
    }
    Path parent = path.toAbsolutePath().getParent();
    Files.createDirectories(parent);
    Path temporary = Files.createTempFile(parent, "registration-", ".json.tmp");
    try {
      String json =
          ConfigFactory.parseMap(
                  Map.of(
                      "version",
                      1,
                      "endpointUrl",
                      state.endpointUrl(),
                      "gdsApplicationUri",
                      state.gdsApplicationUri(),
                      "applicationUri",
                      state.applicationUri(),
                      "applicationId",
                      state.applicationId().toParseableString(),
                      "registeredAt",
                      state.registeredAt().toString()))
              .root()
              .render(ConfigRenderOptions.concise());
      try (FileChannel channel = FileChannel.open(temporary, StandardOpenOption.WRITE)) {
        ByteBuffer bytes = StandardCharsets.UTF_8.encode(json);
        while (bytes.hasRemaining()) {
          if (channel.write(bytes) == 0) {
            throw new IOException("Unable to make progress writing GDS registration state");
          }
        }
        channel.force(true);
      }
      // Never replace good state with a partial file, even on filesystems without atomic moves.
      Files.move(
          temporary, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
    } finally {
      Files.deleteIfExists(temporary);
    }
  }
}

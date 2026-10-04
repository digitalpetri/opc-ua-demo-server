package com.digitalpetri.opcua.server.reverse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.digitalpetri.opcua.server.OpcUaDemoServer;
import com.digitalpetri.opcua.server.OpcUaTestServerBuilder;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import java.io.DataInputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.eclipse.milo.opcua.sdk.server.reverse.ReverseConnectTargetSnapshot;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;

/**
 * Integration tests for server-side Reverse Connect.
 *
 * <p>A plain {@link ServerSocket} stands in for an OPC UA client reverse listener. The tests
 * assert, at the wire level, that enabled targets dial out and advertise the configured
 * endpoint-url in {@code ReverseHello}, that failed attempts retry after the registration period,
 * that disabled targets never connect, and that invalid targets fail startup.
 */
class ReverseConnectIT {

  private static final int ACCEPT_TIMEOUT_MILLIS = 15_000;
  private static final int READ_TIMEOUT_MILLIS = 10_000;

  /**
   * How long the disabled-target test waits before concluding no outbound attempt was made. An
   * enabled target with the configured 500 ms registration period would have connected well within
   * this window.
   */
  private static final int NO_CONNECT_TIMEOUT_MILLIS = 3_000;

  private OpcUaDemoServer server;

  @AfterEach
  void tearDown() {
    if (server != null) {
      server.shutdown();
      server = null;
    }
  }

  @Test
  void enabledTargetConnectsAndAdvertisesEndpointUrl(@TempDir Path tempDir) throws Exception {
    try (ServerSocket clientListener = newClientListener()) {
      int serverPort = findFreePort();
      String endpointUrl = "opc.tcp://localhost:%d/milo".formatted(serverPort);

      server =
          buildServer(
              tempDir, serverPort, targetConfig(clientListener.getLocalPort(), endpointUrl, ""));
      server.startup();

      try (Socket accepted = clientListener.accept()) {
        accepted.setSoTimeout(READ_TIMEOUT_MILLIS);

        UascFrame frame = readFrame(accepted);

        assertEquals("RHE", frame.messageType(), "expected a ReverseHello message");
        assertEquals('F', frame.chunkType(), "expected a final chunk");
        assertTrue(
            frame.bodyText().contains(endpointUrl),
            "ReverseHello body should contain the configured endpoint-url \"%s\" but was: %s"
                .formatted(endpointUrl, frame.bodyText()));
      }
    }
  }

  @Test
  void failedAttemptRetriesAfterRegistrationPeriod(@TempDir Path tempDir) throws Exception {
    try (ServerSocket clientListener = newClientListener()) {
      int serverPort = findFreePort();
      String endpointUrl = "opc.tcp://localhost:%d/milo".formatted(serverPort);

      server =
          buildServer(
              tempDir, serverPort, targetConfig(clientListener.getLocalPort(), endpointUrl, ""));
      server.startup();

      // First attempt: read the ReverseHello, then close without replying to fail the attempt.
      try (Socket firstAttempt = clientListener.accept()) {
        firstAttempt.setSoTimeout(READ_TIMEOUT_MILLIS);

        UascFrame frame = readFrame(firstAttempt);
        assertEquals("RHE", frame.messageType(), "expected a ReverseHello message");
      }

      // The failed attempt records lastAttemptTime, and the retry scheduled after the
      // registration period exposes nextAttemptTime until the next attempt starts.
      awaitAttemptAndRetryTimes(Duration.ofSeconds(10));

      // Second attempt: the retry dials the listener again and re-sends ReverseHello.
      try (Socket secondAttempt = clientListener.accept()) {
        secondAttempt.setSoTimeout(READ_TIMEOUT_MILLIS);

        UascFrame frame = readFrame(secondAttempt);
        assertEquals("RHE", frame.messageType(), "expected a ReverseHello message on retry");
        assertTrue(
            frame.bodyText().contains(endpointUrl),
            "retried ReverseHello body should contain the configured endpoint-url: " + endpointUrl);
      }
    }
  }

  @Test
  void disabledTargetDoesNotConnect(@TempDir Path tempDir) throws Exception {
    // Capture ReverseConnectTargetLogger output: registration of a disabled target is only
    // observable via the log, because disabled targets emit no listener events after startup.
    ch.qos.logback.classic.Logger logbackLogger =
        (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(ReverseConnectTargetLogger.class);
    var appender = new ListAppender<ILoggingEvent>();
    appender.start();
    logbackLogger.addAppender(appender);

    try (ServerSocket clientListener = newClientListener()) {
      int serverPort = findFreePort();
      String endpointUrl = "opc.tcp://localhost:%d/milo".formatted(serverPort);

      server =
          buildServer(
              tempDir,
              serverPort,
              targetConfig(clientListener.getLocalPort(), endpointUrl, "enabled = false"));
      server.startup();

      List<ReverseConnectTargetSnapshot> snapshots =
          server.getServer().getReverseConnectTargetSnapshots();
      assertEquals(1, snapshots.size(), "the disabled target should still be registered");

      ReverseConnectTargetSnapshot snapshot = snapshots.get(0);
      assertFalse(snapshot.enabled());
      assertNull(snapshot.nextAttemptTime(), "disabled target must not schedule attempts");

      assertTrue(
          appender.list.stream()
              .map(ILoggingEvent::getFormattedMessage)
              .anyMatch(
                  message ->
                      message.contains("Reverse Connect target registered")
                          && message.contains("disabled")),
          "registration of the disabled target should be logged");

      clientListener.setSoTimeout(NO_CONNECT_TIMEOUT_MILLIS);
      assertThrows(
          SocketTimeoutException.class,
          clientListener::accept,
          "disabled target must not dial the client listener");
    } finally {
      logbackLogger.detachAppender(appender);
      appender.stop();
    }
  }

  @Test
  void invalidEndpointUrlFailsStartup(@TempDir Path tempDir) throws Exception {
    int serverPort = findFreePort();
    String endpointUrl = "opc.tcp://localhost:%d/nomatch".formatted(serverPort);

    Config config = targetConfig(findFreePort(), endpointUrl, "");

    IllegalArgumentException e =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                OpcUaTestServerBuilder.builder()
                    .withDataDir(tempDir)
                    .withPort(serverPort)
                    .withConfig(config)
                    .build());

    assertTrue(
        e.getMessage().contains("reverse-connect.target-list[0].endpoint-url"),
        "error message should identify the target index and field but was: " + e.getMessage());
    assertTrue(
        e.getMessage().contains(endpointUrl),
        "error message should include the offending endpoint-url but was: " + e.getMessage());
  }

  /**
   * Poll target snapshots until one has been observed with {@code lastAttemptTime} populated and
   * one with {@code nextAttemptTime} populated, failing if {@code timeout} elapses first.
   *
   * <p>The two fields are polled independently because {@code nextAttemptTime} is only non-null
   * between scheduling a retry and starting the next attempt.
   *
   * @param timeout the maximum time to wait for both observations.
   * @throws InterruptedException if the polling sleep is interrupted.
   */
  private void awaitAttemptAndRetryTimes(Duration timeout) throws InterruptedException {
    Instant deadline = Instant.now().plus(timeout);
    boolean sawLastAttemptTime = false;
    boolean sawNextAttemptTime = false;

    while (Instant.now().isBefore(deadline)) {
      List<ReverseConnectTargetSnapshot> snapshots =
          server.getServer().getReverseConnectTargetSnapshots();

      for (ReverseConnectTargetSnapshot snapshot : snapshots) {
        sawLastAttemptTime |= snapshot.lastAttemptTime() != null;
        sawNextAttemptTime |= snapshot.nextAttemptTime() != null;
      }

      if (sawLastAttemptTime && sawNextAttemptTime) {
        return;
      }

      Thread.sleep(5);
    }

    fail(
        "timed out waiting for snapshot times: sawLastAttemptTime=%s, sawNextAttemptTime=%s"
            .formatted(sawLastAttemptTime, sawNextAttemptTime));
  }

  private static OpcUaDemoServer buildServer(Path dataDir, int serverPort, Config config)
      throws Exception {

    return OpcUaTestServerBuilder.builder()
        .withDataDir(dataDir)
        .withPort(serverPort)
        .withConfig(config)
        .build();
  }

  private static Config targetConfig(int listenerPort, String endpointUrl, String extraFields) {
    return ConfigFactory.parseString(
        """
        reverse-connect {
          target-list = [
            {
              client-listener-url = "opc.tcp://127.0.0.1:%d"
              endpoint-url = "%s"
              registration-period = 500 ms
              connect-timeout = 2 seconds
              %s
            }
          ]
        }
        """
            .formatted(listenerPort, endpointUrl, extraFields));
  }

  /**
   * Create a fake client reverse listener bound to the IPv4 loopback address, matching the {@code
   * opc.tcp://127.0.0.1:port} client-listener-url the tests configure.
   *
   * @return the listening socket, with an accept timeout already applied.
   * @throws IOException if the socket cannot be created.
   */
  private static ServerSocket newClientListener() throws IOException {
    var clientListener = new ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"));
    clientListener.setSoTimeout(ACCEPT_TIMEOUT_MILLIS);
    return clientListener;
  }

  private static int findFreePort() throws IOException {
    try (var socket = new ServerSocket(0)) {
      return socket.getLocalPort();
    }
  }

  /**
   * Read one complete UA-TCP message from {@code socket}.
   *
   * <p>The 8-byte UACP header is a 3-byte ASCII message type, a 1-byte chunk type, and a 4-byte
   * little-endian message size that includes the header itself.
   *
   * @param socket the socket to read from.
   * @return the decoded frame.
   * @throws IOException if the stream ends or times out before a complete message is read.
   */
  private static UascFrame readFrame(Socket socket) throws IOException {
    var in = new DataInputStream(socket.getInputStream());

    byte[] header = new byte[8];
    in.readFully(header);

    String messageType = new String(header, 0, 3, StandardCharsets.US_ASCII);
    char chunkType = (char) (header[3] & 0xFF);

    int messageSize =
        (header[4] & 0xFF)
            | ((header[5] & 0xFF) << 8)
            | ((header[6] & 0xFF) << 16)
            | ((header[7] & 0xFF) << 24);

    byte[] body = new byte[messageSize - 8];
    in.readFully(body);

    return new UascFrame(messageType, chunkType, body);
  }

  /**
   * One decoded UA-TCP message.
   *
   * @param messageType the 3-character ASCII message type, e.g. {@code RHE}.
   * @param chunkType the chunk type character, {@code F} for final.
   * @param body the message body following the 8-byte header.
   */
  private record UascFrame(String messageType, char chunkType, byte[] body) {

    /**
     * Decode the body with ISO-8859-1 so each byte maps to one char, allowing {@code contains} to
     * act as a byte-subsequence check for ASCII values such as the endpoint URL.
     *
     * @return the body decoded as ISO-8859-1 text.
     */
    String bodyText() {
      return new String(body, StandardCharsets.ISO_8859_1);
    }
  }
}

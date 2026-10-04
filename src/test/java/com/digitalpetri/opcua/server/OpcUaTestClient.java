package com.digitalpetri.opcua.server;

import static org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.Unsigned.uint;

import java.util.Objects;
import java.util.function.Consumer;
import org.eclipse.milo.opcua.sdk.client.OpcUaClient;
import org.eclipse.milo.opcua.sdk.client.OpcUaClientConfigBuilder;
import org.eclipse.milo.opcua.sdk.server.EndpointConfig;
import org.eclipse.milo.opcua.sdk.server.OpcUaServer;
import org.eclipse.milo.opcua.stack.core.UaException;
import org.eclipse.milo.opcua.stack.core.security.SecurityPolicy;
import org.eclipse.milo.opcua.stack.core.types.builtin.LocalizedText;
import org.eclipse.milo.opcua.stack.core.types.enumerated.MessageSecurityMode;

/**
 * Utility for creating test OPC UA clients that connect to test servers.
 *
 * <p>This class simplifies client creation in integration tests by automatically configuring
 * clients to connect to test servers with appropriate settings.
 *
 * <p>Example usage:
 *
 * <pre>{@code
 * OpcUaServer server = ...;
 * server.startup();
 *
 * OpcUaClient client = OpcUaTestClient.create(server, configBuilder -> {
 *   // Optional: customize client configuration
 *   configBuilder.setSessionTimeout(uint(60_000));
 * });
 *
 * client.connect().get();
 * }</pre>
 */
public final class OpcUaTestClient {

  private OpcUaTestClient() {}

  /**
   * Create an OPC UA client configured to connect to the given server.
   *
   * @param server the server to connect to.
   * @param configCustomizer optional customizer for client configuration.
   * @return a configured OPC UA client.
   * @throws UaException if client creation fails.
   */
  public static OpcUaClient create(
      OpcUaServer server, Consumer<OpcUaClientConfigBuilder> configCustomizer) throws UaException {

    EndpointConfig endpoint = server.getConfig().getEndpoints().iterator().next();

    return create(
        server, endpoint.getSecurityPolicy(), endpoint.getSecurityMode(), configCustomizer);
  }

  /**
   * Create an OPC UA client configured for a specific endpoint security policy and mode.
   *
   * @param server the server to connect to.
   * @param securityPolicy the endpoint security policy to select.
   * @param securityMode the endpoint message security mode to select.
   * @param configCustomizer optional customizer for client configuration.
   * @return a configured OPC UA client.
   * @throws UaException if client creation fails.
   */
  public static OpcUaClient create(
      OpcUaServer server,
      SecurityPolicy securityPolicy,
      MessageSecurityMode securityMode,
      Consumer<OpcUaClientConfigBuilder> configCustomizer)
      throws UaException {

    EndpointConfig endpoint =
        server.getConfig().getEndpoints().stream()
            .filter(e -> e.getSecurityPolicy() == securityPolicy)
            .filter(e -> e.getSecurityMode() == securityMode)
            .filter(e -> !e.getPath().endsWith("/discovery"))
            .findFirst()
            .orElseThrow(
                () ->
                    new IllegalArgumentException(
                        "no endpoint configured for " + securityPolicy + "/" + securityMode));

    return OpcUaClient.create(
        endpoint.getEndpointUrl(),
        endpoints ->
            endpoints.stream()
                .filter(
                    e ->
                        Objects.equals(
                            e.getSecurityPolicyUri(), endpoint.getSecurityPolicy().getUri()))
                .filter(e -> e.getSecurityMode() == endpoint.getSecurityMode())
                .filter(
                    e -> e.getEndpointUrl() == null || !e.getEndpointUrl().endsWith("/discovery"))
                .findFirst(),
        _ -> {},
        clientConfigBuilder -> {
          clientConfigBuilder
              .setApplicationName(LocalizedText.english("eclipse milo test client"))
              .setApplicationUri("urn:eclipse:milo:test:client")
              .setRequestTimeout(uint(5_000));

          configCustomizer.accept(clientConfigBuilder);
        });
  }

  /**
   * Create an OPC UA client configured to connect to the given server with default settings.
   *
   * @param server the server to connect to.
   * @return a configured OPC UA client.
   * @throws UaException if client creation fails.
   */
  public static OpcUaClient create(OpcUaServer server) throws UaException {
    return create(server, _ -> {});
  }
}

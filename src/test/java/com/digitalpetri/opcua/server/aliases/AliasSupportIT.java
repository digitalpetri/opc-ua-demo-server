package com.digitalpetri.opcua.server.aliases;

import static java.util.Objects.requireNonNull;
import static java.util.Objects.requireNonNullElse;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.digitalpetri.opcua.server.OpcUaDemoServer;
import com.digitalpetri.opcua.server.OpcUaTestClient;
import com.digitalpetri.opcua.server.OpcUaTestServerBuilder;
import com.typesafe.config.ConfigFactory;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.eclipse.milo.opcua.sdk.client.OpcUaClient;
import org.eclipse.milo.opcua.sdk.server.AddressSpaceManager;
import org.eclipse.milo.opcua.sdk.server.nodes.UaMethodNode;
import org.eclipse.milo.opcua.stack.core.AttributeId;
import org.eclipse.milo.opcua.stack.core.NodeIds;
import org.eclipse.milo.opcua.stack.core.UaException;
import org.eclipse.milo.opcua.stack.core.transport.TransportProfile;
import org.eclipse.milo.opcua.stack.core.types.builtin.DataValue;
import org.eclipse.milo.opcua.stack.core.types.builtin.ExpandedNodeId;
import org.eclipse.milo.opcua.stack.core.types.builtin.ExtensionObject;
import org.eclipse.milo.opcua.stack.core.types.builtin.LocalizedText;
import org.eclipse.milo.opcua.stack.core.types.builtin.NodeId;
import org.eclipse.milo.opcua.stack.core.types.builtin.QualifiedName;
import org.eclipse.milo.opcua.stack.core.types.builtin.StatusCode;
import org.eclipse.milo.opcua.stack.core.types.builtin.Variant;
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.UInteger;
import org.eclipse.milo.opcua.stack.core.types.enumerated.TimestampsToReturn;
import org.eclipse.milo.opcua.stack.core.types.structured.AliasNameDataType;
import org.eclipse.milo.opcua.stack.core.types.structured.AliasNameVerboseDataType;
import org.eclipse.milo.opcua.stack.core.types.structured.CallMethodRequest;
import org.eclipse.milo.opcua.stack.core.types.structured.CallMethodResult;
import org.eclipse.milo.opcua.stack.core.types.structured.ReadValueId;
import org.eclipse.milo.opcua.stack.transport.server.ServerApplicationContext;
import org.eclipse.milo.opcua.stack.transport.server.tcp.OpcTcpServerTransport;
import org.eclipse.milo.opcua.stack.transport.server.tcp.OpcTcpServerTransportConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AliasSupportIT {

  private OpcUaDemoServer server;
  private OpcUaClient client;

  @AfterEach
  void tearDown() throws Exception {
    if (client != null) {
      client.disconnect();
    }
    if (server != null && server.isRunning()) {
      server.shutdown();
    }
  }

  // AliasManager's documented post-server-start lifecycle would expose a live transport before
  // alias initialization. Registering AliasSupport as an SDK lifecycle participant instead
  // completes the alias address space before the first endpoint binds.
  @Test
  void aliasAddressSpaceIsCompleteBeforeTransportBinding(@TempDir Path tempDir) throws Exception {
    var serverReference = new AtomicReference<OpcUaDemoServer>();
    var bindObserved = new AtomicBoolean();
    var aliasesCompleteAtBind = new AtomicBoolean();

    server =
        OpcUaTestServerBuilder.builder()
            .withDataDir(tempDir)
            .withConfig(ConfigFactory.parseMap(Map.of("address-space.dynamic.enabled", true)))
            .build(
                transportProfile -> {
                  if (transportProfile != TransportProfile.TCP_UASC_UABINARY) {
                    return null;
                  }

                  OpcTcpServerTransportConfig transportConfig =
                      OpcTcpServerTransportConfig.newBuilder().build();

                  return new OpcTcpServerTransport(transportConfig) {
                    @Override
                    public synchronized void bind(
                        ServerApplicationContext applicationContext, InetSocketAddress bindAddress)
                        throws Exception {

                      bindObserved.set(true);
                      OpcUaDemoServer startingServer = serverReference.get();
                      aliasesCompleteAtBind.set(
                          startingServer != null && aliasAddressSpaceIsComplete(startingServer));

                      super.bind(applicationContext, bindAddress);
                    }
                  };
                });
    serverReference.set(server);

    server.startup();

    assertTrue(bindObserved.get(), "expected the OPC TCP transport to bind");
    assertTrue(
        aliasesCompleteAtBind.get(),
        "alias Methods, categories, and aliases must be complete before transport binding");
  }

  /**
   * Part 17 §6.3.2/§6.3.3: lookup from Aliases recursively discovers aliases in TagVariables
   * subcategories and returns usable targets in deterministic name order.
   */
  @Test
  void findMethodsResolveReadableDemoTargetsAndPersistVersions(@TempDir Path tempDir)
      throws Exception {

    startServer(tempDir);

    AliasNameDataType[] aliases = findAliases(NodeIds.Aliases_FindAlias, "Demo.%");
    Map<String, NodeId> expectedTargets = expectedTargets();
    assertTrue(
        server
            .getServer()
            .getAddressSpaceManager()
            .getManagedNode(NodeId.parse("ns=2;s=" + AliasSupport.STATIC_CATEGORY_IDENTIFIER))
            .isPresent());
    assertTrue(
        server
            .getServer()
            .getAddressSpaceManager()
            .getManagedNode(NodeId.parse("ns=2;s=" + AliasSupport.DYNAMIC_CATEGORY_IDENTIFIER))
            .isPresent());
    assertArrayEquals(
        expectedTargets.keySet().stream().sorted().toArray(String[]::new),
        Arrays.stream(aliases).map(alias -> alias.getAliasName().name()).toArray(String[]::new));

    for (AliasNameDataType alias : aliases) {
      ExpandedNodeId[] targets = requireNonNull(alias.getReferencedNodes());
      assertEquals(1, targets.length);

      NodeId targetNodeId = requireNonNull(expectedTargets.get(alias.getAliasName().name()));
      assertEquals(targetNodeId.expanded(), targets[0]);

      DataValue value = client.readValue(0.0, TimestampsToReturn.Neither, targetNodeId);
      assertTrue(value.getStatusCode().isGood());
      assertNotNull(value.value().value());
    }

    NodeId verboseMethodId = NodeId.parse("ns=2;s=Aliases/FindAliasVerbose");
    AliasNameVerboseDataType[] verboseAliases = findVerboseAliases(verboseMethodId, "Demo.%");
    assertEquals(expectedTargets.size(), verboseAliases.length);
    for (AliasNameVerboseDataType alias : verboseAliases) {
      String categoryIdentifier =
          alias.getAliasName().name().startsWith("Demo.Static.")
              ? AliasSupport.STATIC_CATEGORY_IDENTIFIER
              : AliasSupport.DYNAMIC_CATEGORY_IDENTIFIER;
      assertEquals(NodeId.parse("ns=2;s=" + categoryIdentifier), alias.getAliasNameCategoryId());
    }

    // Part 17 §6.2 requires the Alias Object DisplayName to have no LocaleId.
    for (String aliasName : expectedTargets.keySet()) {
      DataValue displayNameValue =
          readAttribute(NodeId.parse("ns=2;s=Aliases/" + aliasName), AttributeId.DisplayName);
      LocalizedText displayName =
          assertInstanceOf(LocalizedText.class, displayNameValue.value().value());
      assertEquals(new LocalizedText(null, aliasName), displayName);
    }

    assertFalse(
        server
            .getServer()
            .getAddressSpaceManager()
            .getManagedNode(NodeId.parse("ns=2;s=Aliases/AddAliasesToCategory"))
            .isPresent());
    assertFalse(
        server
            .getServer()
            .getAddressSpaceManager()
            .getManagedNode(NodeId.parse("ns=2;s=Aliases/DeleteAliasesFromCategory"))
            .isPresent());

    UInteger firstLastChange =
        assertInstanceOf(
            UInteger.class,
            client
                .readValue(0.0, TimestampsToReturn.Neither, NodeIds.Aliases_LastChange)
                .value()
                .value());

    stopServer();
    startServer(tempDir);

    UInteger secondLastChange =
        assertInstanceOf(
            UInteger.class,
            client
                .readValue(0.0, TimestampsToReturn.Neither, NodeIds.Aliases_LastChange)
                .value()
                .value());

    assertTrue(
        secondLastChange.longValue() > firstLastChange.longValue(),
        () ->
            "expected persisted LastChange to advance from %s but was %s"
                .formatted(firstLastChange, secondLastChange));
    assertEquals(expectedTargets.size(), findAliases(NodeIds.Aliases_FindAlias, "Demo.%").length);
  }

  @Test
  void disablingAliasSupportPrunesStandardEntryPoint(@TempDir Path tempDir) throws Exception {
    server =
        OpcUaTestServerBuilder.builder()
            .withDataDir(tempDir)
            .withConfig(ConfigFactory.parseMap(Map.of("address-space.aliases.enabled", false)))
            .build();

    assertTrue(
        server.getServer().getAddressSpaceManager().getManagedNode(NodeIds.Aliases).isEmpty());
    assertFalse(Files.exists(tempDir.resolve(AliasSupport.VERSION_STORE_RELATIVE_PATH)));
  }

  @Test
  void disablingDynamicNodesOmitsTheirAliasCategory(@TempDir Path tempDir) throws Exception {
    server = OpcUaTestServerBuilder.builder().withDataDir(tempDir).build();
    server.startup();

    client = OpcUaTestClient.create(server.getServer());
    client.connect();

    AliasNameDataType[] aliases = findAliases(NodeIds.Aliases_FindAlias, "Demo.%");
    assertEquals(6, aliases.length);
    assertTrue(
        Arrays.stream(aliases)
            .allMatch(alias -> alias.getAliasName().name().startsWith("Demo.Static.")));
    assertTrue(
        server
            .getServer()
            .getAddressSpaceManager()
            .getManagedNode(NodeId.parse("ns=2;s=" + AliasSupport.DYNAMIC_CATEGORY_IDENTIFIER))
            .isEmpty());
  }

  @Test
  void malformedVersionStoreRollsBackStartedNamespacesAndServer(@TempDir Path tempDir)
      throws Exception {

    Path versionStorePath = tempDir.resolve(AliasSupport.VERSION_STORE_RELATIVE_PATH);
    Files.createDirectories(versionStorePath.getParent());
    Files.writeString(versionStorePath, "invalid-node-id=1\n");

    server = OpcUaTestServerBuilder.builder().withDataDir(tempDir).build();

    RuntimeException exception = assertThrows(RuntimeException.class, server::startup);
    // The SDK attaches rollback failures to the original startup failure as suppressed exceptions.
    Throwable startupFailure = requireNonNull(exception.getCause());
    assertEquals(
        0,
        startupFailure.getSuppressed().length,
        "pre-bind alias failure rollback must not introduce a secondary SDK lifecycle failure");
    assertFalse(server.isRunning());
    assertTrue(server.getServer().getBoundEndpoints().isEmpty());
    assertTrue(
        server
            .getServer()
            .getAddressSpaceManager()
            .getManagedNode(NodeId.parse("ns=2;s=Demo.Variants.Scalar.Double"))
            .isEmpty());

    // AbstractLifecycle is stopped after the failed startup and a second shutdown would be a no-op.
    server = null;
  }

  private void startServer(Path tempDir) throws Exception {
    server =
        OpcUaTestServerBuilder.builder()
            .withDataDir(tempDir)
            .withConfig(ConfigFactory.parseMap(Map.of("address-space.dynamic.enabled", true)))
            .build();
    server.startup();

    client = OpcUaTestClient.create(server.getServer());
    client.connect();
  }

  private void stopServer() throws Exception {
    if (client != null) {
      client.disconnect();
      client = null;
    }
    if (server != null) {
      server.shutdown();
      server = null;
    }
  }

  private AliasNameDataType[] findAliases(NodeId methodId, String pattern) throws UaException {
    ExtensionObject[] encodedAliases = callFindMethod(methodId, pattern);
    var aliases = new AliasNameDataType[encodedAliases.length];
    for (int i = 0; i < encodedAliases.length; i++) {
      aliases[i] = (AliasNameDataType) encodedAliases[i].decode(client.getStaticEncodingContext());
    }
    return aliases;
  }

  private AliasNameVerboseDataType[] findVerboseAliases(NodeId methodId, String pattern)
      throws UaException {

    ExtensionObject[] encodedAliases = callFindMethod(methodId, pattern);
    var aliases = new AliasNameVerboseDataType[encodedAliases.length];
    for (int i = 0; i < encodedAliases.length; i++) {
      aliases[i] =
          (AliasNameVerboseDataType) encodedAliases[i].decode(client.getStaticEncodingContext());
    }
    return aliases;
  }

  private ExtensionObject[] callFindMethod(NodeId methodId, String pattern) throws UaException {
    var request =
        new CallMethodRequest(
            NodeIds.Aliases,
            methodId,
            new Variant[] {new Variant(pattern), new Variant(NodeId.NULL_VALUE)});

    CallMethodResult result = requireNonNull(client.call(List.of(request)).getResults())[0];
    assertEquals(StatusCode.GOOD, result.getStatusCode());

    Variant[] outputs = requireNonNull(result.getOutputArguments());
    return requireNonNullElse((ExtensionObject[]) outputs[0].value(), new ExtensionObject[0]);
  }

  private DataValue readAttribute(NodeId nodeId, AttributeId attributeId) throws UaException {
    var request = new ReadValueId(nodeId, attributeId.uid(), null, QualifiedName.NULL_VALUE);

    return requireNonNull(
        client.read(0.0, TimestampsToReturn.Neither, List.of(request)).getResults())[0];
  }

  private static Map<String, NodeId> expectedTargets() {
    var targets = new LinkedHashMap<String, NodeId>();
    for (String dataType : List.of("Boolean", "Int32", "UInt32", "Double", "String", "DateTime")) {
      targets.put(
          "Demo.Static." + dataType, NodeId.parse("ns=2;s=Demo.Variants.Scalar." + dataType));
      targets.put("Demo.Dynamic." + dataType, NodeId.parse("ns=2;s=Demo.Dynamic." + dataType));
    }
    return targets;
  }

  private static boolean aliasAddressSpaceIsComplete(OpcUaDemoServer server) {
    AddressSpaceManager addressSpaceManager = server.getServer().getAddressSpaceManager();

    boolean findAliasReady =
        addressSpaceManager
            .getManagedNode(NodeIds.Aliases_FindAlias)
            .filter(UaMethodNode.class::isInstance)
            .map(UaMethodNode.class::cast)
            .filter(UaMethodNode::isExecutable)
            .isPresent();

    boolean categoriesPresent =
        addressSpaceManager
                .getManagedNode(NodeId.parse("ns=2;s=" + AliasSupport.STATIC_CATEGORY_IDENTIFIER))
                .isPresent()
            && addressSpaceManager
                .getManagedNode(NodeId.parse("ns=2;s=" + AliasSupport.DYNAMIC_CATEGORY_IDENTIFIER))
                .isPresent();

    boolean aliasesPresent =
        expectedTargets().keySet().stream()
            .map(aliasName -> NodeId.parse("ns=2;s=Aliases/" + aliasName))
            .allMatch(aliasId -> addressSpaceManager.getManagedNode(aliasId).isPresent());

    return findAliasReady && categoriesPresent && aliasesPresent;
  }
}

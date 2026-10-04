package com.digitalpetri.opcua.server;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.TimeZone;
import org.eclipse.milo.opcua.sdk.server.nodes.UaVariableNode;
import org.eclipse.milo.opcua.stack.core.NodeIds;
import org.eclipse.milo.opcua.stack.core.types.builtin.NodeId;
import org.eclipse.milo.opcua.stack.core.types.structured.TimeZoneDataType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.ResourceLock;

class StandardServerObjectIT {

  private OpcUaDemoServer server;

  @AfterEach
  void tearDown() {
    if (server != null) {
      server.shutdown();
    }
  }

  // Parts 5, 8, 11, 12, 14, 19, 22, and 26: the default address space must retain mandatory and
  // implemented Server functionality without advertising unsupported optional entry points.
  @Test
  void defaultServerObjectExposesOnlySupportedNodes(@TempDir Path tempDir) throws Exception {
    startServer(tempDir, ConfigFactory.empty());

    List<NodeId> unsupportedNodeIds =
        List.of(
            // OPC 10000-5 §6.3.1.
            NodeIds.Server_UrisVersion,
            NodeIds.Server_EstimatedReturnTime,
            NodeIds.Server_SetSubscriptionDurable,
            NodeIds.Server_RequestServerStateChange,
            // OPC 10000-19 §8.1.
            NodeIds.Dictionaries,
            // OPC 10000-8 §6.2.
            NodeIds.Quantities,
            // OPC 10000-11 §5.7.3.
            NodeIds.DefaultHAConfiguration,
            NodeIds.DefaultHEConfiguration,
            // OPC 10000-14 §9.1.3.1.
            NodeIds.PublishSubscribe,
            // OPC 10000-22 §5.4.1.
            NodeIds.Resources,
            // OPC 10000-26 §7.2.
            NodeIds.ServerLog);

    assertAll(
        "unsupported Server Object nodes",
        unsupportedNodeIds.stream()
            .<Executable>map(
                nodeId ->
                    () ->
                        assertTrue(
                            server
                                .getServer()
                                .getAddressSpaceManager()
                                .getManagedNode(nodeId)
                                .isEmpty(),
                            () -> nodeId + " should not be exposed")));

    assertTrue(
        server
            .getServer()
            .getAddressSpaceManager()
            .getManagedNode(NodeIds.PublishSubscribe_PublishedDataSets)
            .isEmpty(),
        "an unsupported descendant must not remain directly addressable");

    List<NodeId> retainedNodeIds =
        List.of(
            NodeIds.Server_ServerCapabilities,
            NodeIds.Server_ServerDiagnostics,
            NodeIds.Server_VendorServerInfo,
            NodeIds.Server_ServerRedundancy,
            NodeIds.Server_GetMonitoredItems,
            NodeIds.Server_ResendData);

    assertAll(
        "supported Server Object nodes",
        retainedNodeIds.stream()
            .<Executable>map(
                nodeId ->
                    () ->
                        assertTrue(
                            server
                                .getServer()
                                .getAddressSpaceManager()
                                .getManagedNode(nodeId)
                                .isPresent(),
                            () -> nodeId + " should remain exposed")));

    assertTrue(
        server
            .getServer()
            .getAddressSpaceManager()
            .getManagedNode(NodeIds.ServerConfiguration)
            .isEmpty());
    assertTrue(
        server
            .getServer()
            .getAddressSpaceManager()
            .getManagedNode(NodeIds.ServerConfiguration_CertificateGroups)
            .isEmpty());
  }

  // OPC 10000-5 §6.3.1 and OPC 10000-3 §8.28: Offset includes the current DST adjustment and
  // the flag reports that inclusion; winter and summer exercise both sides of that contract.
  @Test
  void localTimeReportsOffsetAndDaylightSavingForTheRequestedInstant() {
    ZoneId losAngeles = ZoneId.of("America/Los_Angeles");

    assertEquals(
        new TimeZoneDataType((short) -480, false),
        OpcUaDemoServer.localTime(losAngeles, Instant.parse("2026-01-15T12:00:00Z")));
    assertEquals(
        new TimeZoneDataType((short) -420, true),
        OpcUaDemoServer.localTime(losAngeles, Instant.parse("2026-07-15T12:00:00Z")));
  }

  // LocalTime is current server-location data, so reads must not retain a value captured during
  // server construction when the JVM default time zone changes.
  @Test
  @ResourceLock("jvm-default-time-zone")
  void localTimeReadsUseTheCurrentJvmDefaultTimeZone(@TempDir Path tempDir) throws Exception {
    TimeZone originalTimeZone = TimeZone.getDefault();

    try {
      startServer(tempDir, ConfigFactory.empty());

      TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
      assertEquals(new TimeZoneDataType((short) 0, false), readLocalTime());

      TimeZone.setDefault(TimeZone.getTimeZone("Asia/Kathmandu"));
      assertEquals(new TimeZoneDataType((short) 345, false), readLocalTime());
    } finally {
      TimeZone.setDefault(originalTimeZone);
    }
  }

  private void startServer(Path tempDir, Config config) throws Exception {
    server = OpcUaTestServerBuilder.builder().withDataDir(tempDir).withConfig(config).build();
    server.startup();
  }

  private TimeZoneDataType readLocalTime() {
    UaVariableNode node =
        server
            .getServer()
            .getAddressSpaceManager()
            .getManagedNode(NodeIds.Server_LocalTime)
            .map(UaVariableNode.class::cast)
            .orElseThrow();

    return assertInstanceOf(
        TimeZoneDataType.class, node.getValue().getValue().getValue(), "LocalTime value");
  }
}

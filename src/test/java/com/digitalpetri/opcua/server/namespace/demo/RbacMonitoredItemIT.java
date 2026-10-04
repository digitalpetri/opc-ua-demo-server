package com.digitalpetri.opcua.server.namespace.demo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import com.digitalpetri.opcua.server.OpcUaDemoServer;
import com.digitalpetri.opcua.server.OpcUaTestClient;
import com.digitalpetri.opcua.server.OpcUaTestServerBuilder;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.eclipse.milo.opcua.sdk.client.OpcUaClient;
import org.eclipse.milo.opcua.sdk.client.identity.UsernameProvider;
import org.eclipse.milo.opcua.sdk.client.subscriptions.OpcUaMonitoredItem;
import org.eclipse.milo.opcua.sdk.client.subscriptions.OpcUaSubscription;
import org.eclipse.milo.opcua.sdk.core.AccessLevel;
import org.eclipse.milo.opcua.sdk.server.Session;
import org.eclipse.milo.opcua.sdk.server.access.AccessController.AccessResult;
import org.eclipse.milo.opcua.sdk.server.access.ReadAccessCache;
import org.eclipse.milo.opcua.sdk.server.nodes.UaVariableNode;
import org.eclipse.milo.opcua.sdk.server.nodes.filters.AttributeFilter;
import org.eclipse.milo.opcua.sdk.server.nodes.filters.AttributeFilterContext;
import org.eclipse.milo.opcua.stack.core.AttributeId;
import org.eclipse.milo.opcua.stack.core.StatusCodes;
import org.eclipse.milo.opcua.stack.core.security.CertificateValidator;
import org.eclipse.milo.opcua.stack.core.types.builtin.DataValue;
import org.eclipse.milo.opcua.stack.core.types.builtin.NodeId;
import org.eclipse.milo.opcua.stack.core.types.builtin.QualifiedName;
import org.eclipse.milo.opcua.stack.core.types.structured.ReadValueId;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Integration tests for read access on data MonitoredItems over the RBAC demo nodes.
 *
 * <p>UserA holds the SiteA_Read and SiteA_Write roles, so it may read the Site A Variables and not
 * the Site B ones.
 */
class RbacMonitoredItemIT {

  private static final NodeId SITE_A_VARIABLE = new NodeId(2, "Demo.RBAC.SiteA.Variable0");
  private static final NodeId SITE_B_VARIABLE = new NodeId(2, "Demo.RBAC.SiteB.Variable0");

  private static final long TIMEOUT_MILLIS = 5_000;

  private OpcUaDemoServer server;
  private OpcUaClient client;
  private OpcUaSubscription subscription;

  @BeforeEach
  void setUp(@TempDir Path tempDir) throws Exception {
    server = OpcUaTestServerBuilder.builder().withDataDir(tempDir).build();
    server.startup();

    client =
        OpcUaTestClient.create(
            server.getServer(),
            builder ->
                builder
                    .setCertificateValidator(
                        new CertificateValidator.InsecureCertificateValidator())
                    .setIdentityProvider(new UsernameProvider("UserA", "password")));
    client.connect();

    subscription = new OpcUaSubscription(client, 100.0);
    subscription.create();
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

  /**
   * Part 4 §5.13.2.1: an item the user may not read is still created, and the denial arrives as the
   * status of its value in the Publish response, while a readable item delivers its value.
   */
  @Test
  void deniedItemIsCreatedAndReportsUserAccessDeniedThroughPublish() throws Exception {
    BlockingQueue<DataValue> siteAValues = new LinkedBlockingQueue<>();
    BlockingQueue<DataValue> siteBValues = new LinkedBlockingQueue<>();

    OpcUaMonitoredItem siteAItem = monitor(SITE_A_VARIABLE, siteAValues);
    OpcUaMonitoredItem siteBItem = monitor(SITE_B_VARIABLE, siteBValues);
    subscription.addMonitoredItems(List.of(siteAItem, siteBItem));
    subscription.synchronizeMonitoredItems();

    assertTrue(siteAItem.getCreateResult().orElseThrow().isGood());
    assertTrue(siteBItem.getCreateResult().orElseThrow().isGood());

    DataValue siteAValue = siteAValues.poll(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
    assertNotNull(siteAValue, "no value for the Site A Variable");
    assertTrue(siteAValue.statusCode().isGood());
    assertEquals(0, siteAValue.value().value());

    DataValue siteBValue = siteBValues.poll(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
    assertNotNull(siteBValue, "no denial for the Site B Variable");
    assertEquals(StatusCodes.Bad_UserAccessDenied, siteBValue.statusCode().value());
  }

  /**
   * The demo fragments answer read access from the server's cache, which is only correct while
   * permissions stay fixed after startup. A permission change made without invalidating the cache
   * must not reach the item, and must reach it once the cache is invalidated.
   */
  @Test
  void permissionChangeReachesItemOnlyAfterReadAccessInvalidation() throws Exception {
    BlockingQueue<DataValue> values = new LinkedBlockingQueue<>();

    OpcUaMonitoredItem item = monitor(SITE_B_VARIABLE, values);
    subscription.addMonitoredItems(List.of(item));
    subscription.synchronizeMonitoredItems();

    DataValue denial = values.poll(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
    assertNotNull(denial, "no denial for the Site B Variable");
    assertEquals(StatusCodes.Bad_UserAccessDenied, denial.statusCode().value());

    // The denial above comes from the check CreateMonitoredItems makes, which bypasses the cache.
    // Wait for a sampling cycle to store it, so the grant below changes an answer already cached.
    awaitCachedDenial(SITE_B_VARIABLE);

    UaVariableNode node =
        server
            .getServer()
            .getAddressSpaceManager()
            .getManagedNode(SITE_B_VARIABLE)
            .map(UaVariableNode.class::cast)
            .orElseThrow();
    node.getFilterChain().addFirst(new GrantReadFilter());

    // The item samples at 100 ms, so a per-cycle check would have delivered a value by now.
    assertNull(
        nextGoodValue(values, 1_000),
        "the permission change reached the item without an invalidation");

    server.getServer().getAccessControlManager().invalidateReadAccess(SITE_B_VARIABLE);

    DataValue value = nextGoodValue(values, TIMEOUT_MILLIS);
    assertNotNull(value, "no value after the invalidation");
    assertEquals(0, value.value().value());
  }

  private static OpcUaMonitoredItem monitor(NodeId nodeId, BlockingQueue<DataValue> values) {
    OpcUaMonitoredItem item = OpcUaMonitoredItem.newDataItem(nodeId);
    item.setSamplingInterval(100.0);
    item.setDataValueListener((_, value) -> values.add(value));
    return item;
  }

  private void awaitCachedDenial(NodeId nodeId) throws InterruptedException {
    Session session = server.getServer().getSessionManager().getAllSessions().getFirst();
    ReadAccessCache cache = server.getServer().getAccessControlManager().getReadAccessCache();
    var readValueId =
        new ReadValueId(nodeId, AttributeId.Value.uid(), null, QualifiedName.NULL_VALUE);

    long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(TIMEOUT_MILLIS);
    while (cache.get(session, readValueId).filter(AccessResult::isDenied).isEmpty()) {
      if (System.nanoTime() - deadline > 0) {
        fail("the read access cache never held a denial for " + nodeId);
      }
      Thread.sleep(10);
    }
  }

  private static @Nullable DataValue nextGoodValue(BlockingQueue<DataValue> values, long millis)
      throws InterruptedException {

    long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(millis);
    long remaining;
    while ((remaining = deadline - System.nanoTime()) > 0) {
      DataValue value = values.poll(remaining, TimeUnit.NANOSECONDS);
      if (value != null && value.statusCode().isGood()) {
        return value;
      }
    }
    return null;
  }

  /** Grants every user read and write access by answering UserAccessLevel ahead of RBAC. */
  private static final class GrantReadFilter implements AttributeFilter {

    @Override
    public @Nullable Object getAttribute(AttributeFilterContext ctx, AttributeId attributeId) {
      if (attributeId == AttributeId.UserAccessLevel) {
        return AccessLevel.toValue(AccessLevel.READ_WRITE);
      }
      return ctx.getAttribute(attributeId);
    }
  }
}

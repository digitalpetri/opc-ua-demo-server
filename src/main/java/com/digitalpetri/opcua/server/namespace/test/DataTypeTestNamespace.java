package com.digitalpetri.opcua.server.namespace.test;

import com.digitalpetri.opcua.test.DataTypeInitializer;
import com.digitalpetri.opcua.uanodeset.namespace.NodeSetNamespace;
import java.io.InputStream;
import java.util.List;
import org.eclipse.milo.opcua.sdk.server.OpcUaServer;
import org.eclipse.milo.opcua.sdk.server.nodes.UaNode;
import org.eclipse.milo.opcua.stack.core.encoding.EncodingContext;
import org.eclipse.milo.opcua.stack.core.types.builtin.NodeId;

public class DataTypeTestNamespace extends NodeSetNamespace {

  public static final String NAMESPACE_URI = "https://github.com/digitalpetri/DataTypeTest";
  private static final int NAMESPACE_METADATA_NODE_ID = 5010;

  public DataTypeTestNamespace(OpcUaServer server) {
    super(server, NAMESPACE_URI);

    // OPC UA Part 5 permits omitting namespace metadata entries when mandatory
    // NamespaceMetadataType properties cannot be populated from the namespace.
    getLifecycleManager().addStartupTask(this::deleteNamespaceMetadataNode);
  }

  @Override
  protected EncodingContext getEncodingContext() {
    return getServer().getStaticEncodingContext();
  }

  @Override
  protected List<InputStream> getNodeSetInputStreams() {
    InputStream inputStream =
        DataTypeTestNamespace.class.getResourceAsStream("/DataTypeTest.NodeSet.xml");
    assert inputStream != null;

    return List.of(inputStream);
  }

  public static DataTypeTestNamespace create(OpcUaServer server) {
    var namespace = new DataTypeTestNamespace(server);

    new DataTypeInitializer()
        .initialize(server.getNamespaceTable(), server.getStaticDataTypeManager());

    return namespace;
  }

  private void deleteNamespaceMetadataNode() {
    NodeId nodeId = new NodeId(getNamespaceIndex(), NAMESPACE_METADATA_NODE_ID);

    getNodeManager().getNode(nodeId).ifPresent(UaNode::delete);
  }
}

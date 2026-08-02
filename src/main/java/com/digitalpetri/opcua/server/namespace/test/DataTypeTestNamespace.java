package com.digitalpetri.opcua.server.namespace.test;

import com.digitalpetri.opcua.test.DataTypeInitializer;
import com.digitalpetri.opcua.uanodeset.namespace.NodeSetNamespace;
import java.io.InputStream;
import java.util.List;
import org.eclipse.milo.opcua.sdk.server.OpcUaServer;
import org.eclipse.milo.opcua.sdk.server.model.objects.NamespaceMetadataTypeNode;
import org.eclipse.milo.opcua.stack.core.encoding.EncodingContext;
import org.eclipse.milo.opcua.stack.core.types.builtin.NodeId;
import org.eclipse.milo.opcua.stack.core.types.enumerated.IdType;

public class DataTypeTestNamespace extends NodeSetNamespace {

  public static final String NAMESPACE_URI = "https://github.com/digitalpetri/DataTypeTest";
  private static final int NAMESPACE_METADATA_NODE_ID = 5010;

  public DataTypeTestNamespace(OpcUaServer server) {
    super(server, NAMESPACE_URI);

    getLifecycleManager().addStartupTask(this::configureNamespaceMetadataNode);
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

  private void configureNamespaceMetadataNode() {
    NodeId nodeId = new NodeId(getNamespaceIndex(), NAMESPACE_METADATA_NODE_ID);

    NamespaceMetadataTypeNode metadataNode =
        getNodeManager()
            .getNode(nodeId)
            .map(NamespaceMetadataTypeNode.class::cast)
            .orElseThrow(() -> new IllegalStateException("missing namespace metadata: " + nodeId));

    // Part 5 §6.3.13: this generated NodeSet contains only static numeric NodeIds. The declared
    // range covers every numeric NodeId in DataTypeTest.NodeSet.xml; the String pattern is ignored
    // but remains a readable mandatory Property.
    metadataNode.setStaticNodeIdTypes(new IdType[] {IdType.Numeric});
    metadataNode.setStaticNumericNodeIdRange(new String[] {"3003:6070"});
    metadataNode.setStaticStringNodeIdPattern("");
  }
}

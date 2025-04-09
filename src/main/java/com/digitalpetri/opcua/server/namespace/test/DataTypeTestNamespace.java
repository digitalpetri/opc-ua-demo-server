package com.digitalpetri.opcua.server.namespace.test;

import com.digitalpetri.opcua.test.DataTypeInitializer;
import com.digitalpetri.opcua.uanodeset.namespace.NodeSetNamespace;
import java.io.InputStream;
import java.util.List;
import org.eclipse.milo.opcua.sdk.server.OpcUaServer;
import org.eclipse.milo.opcua.stack.core.encoding.EncodingContext;

public class DataTypeTestNamespace extends NodeSetNamespace {

  public static final String NAMESPACE_URI = "https://github.com/eclipse/milo/DataTypeTest";

  public DataTypeTestNamespace(OpcUaServer server) {
    super(server, NAMESPACE_URI);
  }

  @Override
  protected EncodingContext getEncodingContext() {
    return getServer().getStaticEncodingContext();
  }

  @Override
  protected List<InputStream> getNodeSetInputStreams() {
    InputStream inputStream = DataTypeTestNamespace.class.getResourceAsStream("/datatypetest.xml");
    assert inputStream != null;

    return List.of(inputStream);
  }

  public static DataTypeTestNamespace create(OpcUaServer server) {
    var namespace = new DataTypeTestNamespace(server);

    new DataTypeInitializer()
        .initialize(server.getNamespaceTable(), server.getStaticDataTypeManager());

    return namespace;
  }
}

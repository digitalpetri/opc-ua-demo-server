package org.eclipse.milo.opcua.test;

import com.digitalpetri.opcua.uanodeset.namespace.NodeSetNamespace;
import java.io.InputStream;
import org.eclipse.milo.opcua.sdk.server.OpcUaServer;

public class DataTypeTestNamespace extends NodeSetNamespace {

  public static final String NAMESPACE_URI = "https://github.com/eclipse/milo/DataTypeTest";

  public DataTypeTestNamespace(OpcUaServer server) {
    super(server, NAMESPACE_URI);
  }

  @Override
  protected InputStream newNodeSetInputStream() {
    return DataTypeTestNamespace.class.getResourceAsStream("/DataTypeTest.xml");
  }

  public static DataTypeTestNamespace create(OpcUaServer server) {
    var namespace = new DataTypeTestNamespace(server);

    new DataTypeInitializer()
        .initialize(server.getNamespaceTable(), server.getStaticDataTypeManager());
    ObjectTypeInitializer.initialize(server.getNamespaceTable(), server.getObjectTypeManager());
    VariableTypeInitializer.initialize(server.getNamespaceTable(), server.getVariableTypeManager());

    return namespace;
  }
}

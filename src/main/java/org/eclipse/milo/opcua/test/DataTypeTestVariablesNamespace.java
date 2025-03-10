package org.eclipse.milo.opcua.test;

import com.digitalpetri.opcua.uanodeset.namespace.NodeSetNamespace;
import java.io.InputStream;
import java.util.List;
import org.eclipse.milo.opcua.sdk.server.OpcUaServer;
import org.eclipse.milo.opcua.stack.core.encoding.EncodingContext;

public class DataTypeTestVariablesNamespace extends NodeSetNamespace {

  public static final String NAMESPACE_URI =
      "https://github.com/eclipse/milo/DataTypeTestVariables";

  public DataTypeTestVariablesNamespace(OpcUaServer server) {
    super(server, NAMESPACE_URI);
  }

  @Override
  protected EncodingContext getEncodingContext() {
    return getServer().getDynamicEncodingContext();
  }

  @Override
  protected List<InputStream> getNodeSetInputStreams() {
    InputStream inputStream1 =
        DataTypeTestVariablesNamespace.class.getResourceAsStream("/datatypetestvariables.xml");
    assert inputStream1 != null;

    InputStream inputStream2 = DataTypeTestNamespace.class.getResourceAsStream("/datatypetest.xml");
    assert inputStream2 != null;

    return List.of(inputStream2, inputStream1);
  }
}

package com.digitalpetri.opcua.server.namespace.demo.ctt;

import static org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.Unsigned.uint;
import static org.junit.jupiter.api.Assertions.*;

import com.digitalpetri.opcua.server.OpcUaDemoServer;
import com.digitalpetri.opcua.server.OpcUaTestClient;
import com.digitalpetri.opcua.server.OpcUaTestServerBuilder;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.eclipse.milo.opcua.sdk.client.OpcUaClient;
import org.eclipse.milo.opcua.stack.core.NodeIds;
import org.eclipse.milo.opcua.stack.core.types.builtin.DataValue;
import org.eclipse.milo.opcua.stack.core.types.builtin.NodeId;
import org.eclipse.milo.opcua.stack.core.types.builtin.Variant;
import org.eclipse.milo.opcua.stack.core.types.enumerated.BrowseDirection;
import org.eclipse.milo.opcua.stack.core.types.enumerated.BrowseResultMask;
import org.eclipse.milo.opcua.stack.core.types.enumerated.NodeClass;
import org.eclipse.milo.opcua.stack.core.types.enumerated.TimestampsToReturn;
import org.eclipse.milo.opcua.stack.core.types.structured.BrowseDescription;
import org.eclipse.milo.opcua.stack.core.types.structured.BrowseResult;
import org.eclipse.milo.opcua.stack.core.types.structured.CallMethodRequest;
import org.eclipse.milo.opcua.stack.core.types.structured.CallMethodResult;
import org.eclipse.milo.opcua.stack.core.types.structured.CallResponse;
import org.eclipse.milo.opcua.stack.core.types.structured.ReferenceDescription;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Integration tests for CttNodes and its fragments.
 *
 * <p>These tests verify that nodes from CttNodes are properly created and accessible by browsing
 * from the Objects folder, and that their values can be read.
 */
class CttNodesIT {

  private static final Logger logger = LoggerFactory.getLogger(CttNodesIT.class);

  private OpcUaDemoServer server;
  private OpcUaClient client;

  @BeforeEach
  void setUp(@TempDir Path tempDir) throws Exception {
    // Create test configuration with CTT enabled
    Map<String, Object> configMap = new HashMap<>();
    configMap.put("address-space.ctt.enabled", true);
    Config customConfig = ConfigFactory.parseMap(configMap);

    // Start server with CTT enabled
    server = OpcUaTestServerBuilder.builder().withDataDir(tempDir).withConfig(customConfig).build();
    server.startup();

    // Connect client
    client = OpcUaTestClient.create(server.getServer());
    client.connect();

    logger.info("Test server started and client connected");
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

  @Test
  void testAllProfilesFragmentScalarNode() throws Exception {
    // Given: Path to a scalar node in AllProfiles fragment
    // Objects -> CTT -> Static -> AllProfiles -> Scalar -> Boolean

    // When: Browse to CTT folder
    NodeId cttFolderNodeId = browseForNode(NodeIds.ObjectsFolder, "CTT");
    assertNotNull(cttFolderNodeId, "CTT folder should be found");
    logger.info("Found CTT folder: {}", cttFolderNodeId);

    // Then: Browse to Static folder
    NodeId staticFolderNodeId = browseForNode(cttFolderNodeId, "Static");
    assertNotNull(staticFolderNodeId, "Static folder should be found");
    logger.info("Found Static folder: {}", staticFolderNodeId);

    // And: Browse to AllProfiles folder
    NodeId allProfilesFolderNodeId = browseForNode(staticFolderNodeId, "AllProfiles");
    assertNotNull(allProfilesFolderNodeId, "AllProfiles folder should be found");
    logger.info("Found AllProfiles folder: {}", allProfilesFolderNodeId);

    // And: Browse to Scalar folder
    NodeId scalarFolderNodeId = browseForNode(allProfilesFolderNodeId, "Scalar");
    assertNotNull(scalarFolderNodeId, "Scalar folder should be found");
    logger.info("Found Scalar folder: {}", scalarFolderNodeId);

    // And: Browse to Boolean variable
    NodeId booleanNodeId = browseForNode(scalarFolderNodeId, "Boolean");
    assertNotNull(booleanNodeId, "Boolean variable should be found");
    logger.info("Found Boolean variable: {}", booleanNodeId);

    // And: Read the value
    DataValue value = client.readValue(0.0, TimestampsToReturn.Neither, booleanNodeId);
    assertNotNull(value, "Value should be readable");
    assertNotNull(value.getValue(), "Value should not be null");
    logger.info("Read Boolean value: {}", value.getValue().getValue());
  }

  @Test
  void testAllProfilesFragmentArrayNode() throws Exception {
    // Given: Path to an array node in AllProfiles fragment
    // Objects -> CTT -> Static -> AllProfiles -> Array -> Int32Array

    NodeId cttFolderNodeId = browseForNode(NodeIds.ObjectsFolder, "CTT");
    NodeId staticFolderNodeId = browseForNode(cttFolderNodeId, "Static");
    NodeId allProfilesFolderNodeId = browseForNode(staticFolderNodeId, "AllProfiles");

    // When: Browse to Array folder
    NodeId arrayFolderNodeId = browseForNode(allProfilesFolderNodeId, "Array");
    assertNotNull(arrayFolderNodeId, "Array folder should be found");
    logger.info("Found Array folder: {}", arrayFolderNodeId);

    // Then: Browse to Int32Array variable
    NodeId int32ArrayNodeId = browseForNode(arrayFolderNodeId, "Int32Array");
    assertNotNull(int32ArrayNodeId, "Int32Array variable should be found");
    logger.info("Found Int32Array variable: {}", int32ArrayNodeId);

    // And: Read the value
    DataValue value = client.readValue(0.0, TimestampsToReturn.Neither, int32ArrayNodeId);
    assertNotNull(value, "Value should be readable");
    assertNotNull(value.getValue(), "Value should not be null");
    logger.info("Read Int32Array value: {}", value.getValue().getValue());
  }

  @Test
  void testAllProfilesFragmentMatrixNode() throws Exception {
    // Given: Path to a matrix node in AllProfiles fragment
    // Objects -> CTT -> Static -> AllProfiles -> Matrix -> DoubleMatrix

    NodeId cttFolderNodeId = browseForNode(NodeIds.ObjectsFolder, "CTT");
    NodeId staticFolderNodeId = browseForNode(cttFolderNodeId, "Static");
    NodeId allProfilesFolderNodeId = browseForNode(staticFolderNodeId, "AllProfiles");

    // When: Browse to Matrix folder
    NodeId matrixFolderNodeId = browseForNode(allProfilesFolderNodeId, "Matrix");
    assertNotNull(matrixFolderNodeId, "Matrix folder should be found");
    logger.info("Found Matrix folder: {}", matrixFolderNodeId);

    // Then: Browse to DoubleMatrix variable
    NodeId doubleMatrixNodeId = browseForNode(matrixFolderNodeId, "DoubleMatrix");
    assertNotNull(doubleMatrixNodeId, "DoubleMatrix variable should be found");
    logger.info("Found DoubleMatrix variable: {}", doubleMatrixNodeId);

    // And: Read the value
    DataValue value = client.readValue(0.0, TimestampsToReturn.Neither, doubleMatrixNodeId);
    assertNotNull(value, "Value should be readable");
    assertNotNull(value.getValue(), "Value should not be null");
    logger.info("Read DoubleMatrix value: {}", value.getValue().getValue());
  }

  @Test
  void testDataAccessProfileFragmentAnalogItemNode() throws Exception {
    // Given: Path to an AnalogItemType node in DataAccessProfile fragment
    // Objects -> CTT -> Static -> DataAccessProfile -> AnalogItemType -> DoubleAnalog

    NodeId cttFolderNodeId = browseForNode(NodeIds.ObjectsFolder, "CTT");
    NodeId staticFolderNodeId = browseForNode(cttFolderNodeId, "Static");
    NodeId dataAccessProfileFolderNodeId = browseForNode(staticFolderNodeId, "DataAccessProfile");
    assertNotNull(dataAccessProfileFolderNodeId, "DataAccessProfile folder should be found");
    logger.info("Found DataAccessProfile folder: {}", dataAccessProfileFolderNodeId);

    // When: Browse to AnalogItemType folder
    NodeId analogItemTypeFolderNodeId =
        browseForNode(dataAccessProfileFolderNodeId, "AnalogItemType");
    assertNotNull(analogItemTypeFolderNodeId, "AnalogItemType folder should be found");
    logger.info("Found AnalogItemType folder: {}", analogItemTypeFolderNodeId);

    // Then: Browse to DoubleAnalog variable
    NodeId doubleAnalogNodeId = browseForNode(analogItemTypeFolderNodeId, "DoubleAnalog");
    assertNotNull(doubleAnalogNodeId, "DoubleAnalog variable should be found");
    logger.info("Found DoubleAnalog variable: {}", doubleAnalogNodeId);

    // And: Read the value
    DataValue value = client.readValue(0.0, TimestampsToReturn.Neither, doubleAnalogNodeId);
    assertNotNull(value, "Value should be readable");
    assertNotNull(value.getValue(), "Value should not be null");
    logger.info("Read DoubleAnalog value: {}", value.getValue().getValue());
  }

  @Test
  void testDataAccessProfileFragmentDataItemNode() throws Exception {
    // Given: Path to a DataItemType node in DataAccessProfile fragment
    // Objects -> CTT -> Static -> DataAccessProfile -> DataItemType -> Int32

    NodeId cttFolderNodeId = browseForNode(NodeIds.ObjectsFolder, "CTT");
    NodeId staticFolderNodeId = browseForNode(cttFolderNodeId, "Static");
    NodeId dataAccessProfileFolderNodeId = browseForNode(staticFolderNodeId, "DataAccessProfile");

    // When: Browse to DataItemType folder
    NodeId dataItemTypeFolderNodeId = browseForNode(dataAccessProfileFolderNodeId, "DataItemType");
    assertNotNull(dataItemTypeFolderNodeId, "DataItemType folder should be found");
    logger.info("Found DataItemType folder: {}", dataItemTypeFolderNodeId);

    // Then: Browse to Int32 variable
    NodeId int32NodeId = browseForNode(dataItemTypeFolderNodeId, "Int32");
    assertNotNull(int32NodeId, "Int32 variable should be found");
    logger.info("Found Int32 variable: {}", int32NodeId);

    // And: Read the value
    DataValue value = client.readValue(0.0, TimestampsToReturn.Neither, int32NodeId);
    assertNotNull(value, "Value should be readable");
    assertNotNull(value.getValue(), "Value should not be null");
    logger.info("Read Int32 value: {}", value.getValue().getValue());
  }

  @Test
  void testReferencesFragmentNode() throws Exception {
    // Given: Path to a variable in References fragment
    // Objects -> CTT -> Static -> References -> Has3ForwardReferences1 -> Variable0

    NodeId cttFolderNodeId = browseForNode(NodeIds.ObjectsFolder, "CTT");
    NodeId staticFolderNodeId = browseForNode(cttFolderNodeId, "Static");
    NodeId referencesFolderNodeId = browseForNode(staticFolderNodeId, "References");
    assertNotNull(referencesFolderNodeId, "References folder should be found");
    logger.info("Found References folder: {}", referencesFolderNodeId);

    // When: Browse to Has3ForwardReferences1 folder
    NodeId has3ForwardReferences1NodeId =
        browseForNode(referencesFolderNodeId, "Has3ForwardReferences1");
    assertNotNull(has3ForwardReferences1NodeId, "Has3ForwardReferences1 folder should be found");
    logger.info("Found Has3ForwardReferences1 folder: {}", has3ForwardReferences1NodeId);

    // Then: Browse to Variable0
    NodeId variable0NodeId = browseForNode(has3ForwardReferences1NodeId, "Variable0");
    assertNotNull(variable0NodeId, "Variable0 should be found");
    logger.info("Found Variable0: {}", variable0NodeId);

    // And: Read the value
    DataValue value = client.readValue(0.0, TimestampsToReturn.Neither, variable0NodeId);
    assertNotNull(value, "Value should be readable");
    assertNotNull(value.getValue(), "Value should not be null");
    logger.info("Read Variable0 value: {}", value.getValue().getValue());
  }

  @Test
  void testPathsFragmentNode() throws Exception {
    // Given: Path to a deeply nested folder in Paths fragment
    // Objects -> CTT -> Static -> Paths -> Folder0 -> Folder1

    NodeId cttFolderNodeId = browseForNode(NodeIds.ObjectsFolder, "CTT");
    NodeId staticFolderNodeId = browseForNode(cttFolderNodeId, "Static");
    NodeId pathsFolderNodeId = browseForNode(staticFolderNodeId, "Paths");
    assertNotNull(pathsFolderNodeId, "Paths folder should be found");
    logger.info("Found Paths folder: {}", pathsFolderNodeId);

    // When: Browse to Folder0
    NodeId folder0NodeId = browseForNode(pathsFolderNodeId, "Folder0");
    assertNotNull(folder0NodeId, "Folder0 should be found");
    logger.info("Found Folder0: {}", folder0NodeId);

    // Then: Browse to Folder1
    NodeId folder1NodeId = browseForNode(folder0NodeId, "Folder1");
    assertNotNull(folder1NodeId, "Folder1 should be found");
    logger.info("Found Folder1: {}", folder1NodeId);
  }

  @Test
  void testMethodsFragmentNode() throws Exception {
    // Given: Path to a method in Methods fragment
    // Objects -> CTT -> Methods -> MethodNoArgs

    NodeId cttFolderNodeId = browseForNode(NodeIds.ObjectsFolder, "CTT");
    NodeId methodsFolderNodeId = browseForNode(cttFolderNodeId, "Methods");
    assertNotNull(methodsFolderNodeId, "Methods folder should be found");
    logger.info("Found Methods folder: {}", methodsFolderNodeId);

    // When: Browse to MethodNoArgs
    NodeId methodNoArgsNodeId = browseForNode(methodsFolderNodeId, "MethodNoArgs");
    assertNotNull(methodNoArgsNodeId, "MethodNoArgs should be found");
    logger.info("Found MethodNoArgs: {}", methodNoArgsNodeId);

    // Then: Call the method to verify it's callable
    Variant[] inputArguments = new Variant[0]; // MethodNoArgs has no input arguments
    CallMethodRequest request =
        new CallMethodRequest(methodsFolderNodeId, methodNoArgsNodeId, inputArguments);

    CallResponse response = client.call(List.of(request));

    // And: Verify the method executed successfully
    assertNotNull(response, "Method call should return a response");
    assertNotNull(response.getResults(), "Response should contain results");
    assertEquals(1, response.getResults().length, "Should have one result");

    CallMethodResult result = response.getResults()[0];
    assertTrue(
        result.getStatusCode().isGood(), "Method call should succeed: " + result.getStatusCode());

    Variant[] outputValues = result.getOutputArguments();
    assertNotNull(outputValues, "Output values should not be null");
    assertEquals(0, outputValues.length, "MethodNoArgs should return no output values");

    logger.info("Successfully called MethodNoArgs");
  }

  @Test
  void testMethodsFragmentNodeIO() throws Exception {
    // Given: Path to a method with both input and output in Methods fragment
    // Objects -> CTT -> Methods -> MethodIO

    NodeId cttFolderNodeId = browseForNode(NodeIds.ObjectsFolder, "CTT");
    NodeId methodsFolderNodeId = browseForNode(cttFolderNodeId, "Methods");
    assertNotNull(methodsFolderNodeId, "Methods folder should be found");
    logger.info("Found Methods folder: {}", methodsFolderNodeId);

    // When: Browse to MethodIO
    NodeId methodIONodeId = browseForNode(methodsFolderNodeId, "MethodIO");
    assertNotNull(methodIONodeId, "MethodIO should be found");
    logger.info("Found MethodIO: {}", methodIONodeId);

    // Then: Call the method with an Int32 input and expect the same value as output
    int input = 123;
    Variant[] inputArguments = new Variant[] {Variant.ofInt32(input)};
    CallMethodRequest request =
        new CallMethodRequest(methodsFolderNodeId, methodIONodeId, inputArguments);

    CallResponse response = client.call(List.of(request));

    // And: Verify the method executed successfully and echoed the input
    assertNotNull(response, "Method call should return a response");
    assertNotNull(response.getResults(), "Response should contain results");
    assertEquals(1, response.getResults().length, "Should have one result");

    CallMethodResult result = response.getResults()[0];
    assertTrue(
        result.getStatusCode().isGood(), "Method call should succeed: " + result.getStatusCode());

    Variant[] outputValues = result.getOutputArguments();
    assertNotNull(outputValues, "Output values should not be null");
    assertEquals(1, outputValues.length, "MethodIO should return one output value");

    Integer output = (Integer) outputValues[0].getValue();
    assertNotNull(output, "Output value should not be null");
    assertEquals(input, output.intValue(), "MethodIO should echo the input value");

    logger.info("Successfully called MethodIO with input {} and received output {}", input, output);
  }

  @Test
  void testSecurityAccessFragmentNode() throws Exception {
    // Given: Path to a variable in SecurityAccess fragment
    // Objects -> CTT -> SecurityAccess -> AccessLevel_CurrentRead

    NodeId cttFolderNodeId = browseForNode(NodeIds.ObjectsFolder, "CTT");
    NodeId securityAccessFolderNodeId = browseForNode(cttFolderNodeId, "SecurityAccess");
    assertNotNull(securityAccessFolderNodeId, "SecurityAccess folder should be found");
    logger.info("Found SecurityAccess folder: {}", securityAccessFolderNodeId);

    // When: Browse to AccessLevel_CurrentRead variable
    NodeId accessLevelCurrentReadNodeId =
        browseForNode(securityAccessFolderNodeId, "AccessLevel_CurrentRead");
    assertNotNull(accessLevelCurrentReadNodeId, "AccessLevel_CurrentRead should be found");
    logger.info("Found AccessLevel_CurrentRead: {}", accessLevelCurrentReadNodeId);

    // Then: Read the value
    DataValue value =
        client.readValue(0.0, TimestampsToReturn.Neither, accessLevelCurrentReadNodeId);
    assertNotNull(value, "Value should be readable");
    assertNotNull(value.getValue(), "Value should not be null");
    logger.info("Read AccessLevel_CurrentRead value: {}", value.getValue().getValue());
  }

  /**
   * Browse for a node with the given browse name starting from the parent node.
   *
   * @param parentNodeId the parent node to start browsing from.
   * @param browseName the browse name to search for.
   * @return the NodeId of the found node, or null if not found.
   */
  private NodeId browseForNode(NodeId parentNodeId, String browseName) throws Exception {
    BrowseDescription browseDescription =
        new BrowseDescription(
            parentNodeId,
            BrowseDirection.Forward,
            null,
            true,
            uint(
                NodeClass.Object.getValue()
                    | NodeClass.Variable.getValue()
                    | NodeClass.Method.getValue()),
            uint(BrowseResultMask.All.getValue()));

    BrowseResult browseResult = client.browse(browseDescription);

    ReferenceDescription[] references = browseResult.getReferences();
    if (references != null) {
      for (ReferenceDescription ref : references) {
        if (ref.getBrowseName().getName().equals(browseName)) {
          return ref.getNodeId().toNodeId(client.getNamespaceTable()).orElse(null);
        }
      }
    }

    return null;
  }
}

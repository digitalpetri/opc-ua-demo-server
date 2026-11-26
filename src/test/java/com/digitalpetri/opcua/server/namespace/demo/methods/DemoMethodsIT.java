package com.digitalpetri.opcua.server.namespace.demo.methods;

import static org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.Unsigned.uint;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.digitalpetri.opcua.server.OpcUaDemoServer;
import com.digitalpetri.opcua.server.OpcUaTestClient;
import com.digitalpetri.opcua.server.OpcUaTestServerBuilder;
import com.digitalpetri.opcua.server.namespace.demo.DemoNamespace;
import com.digitalpetri.opcua.server.namespace.demo.types.DemoRecord;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.eclipse.milo.opcua.sdk.client.OpcUaClient;
import org.eclipse.milo.opcua.stack.core.NamespaceTable;
import org.eclipse.milo.opcua.stack.core.NodeIds;
import org.eclipse.milo.opcua.stack.core.StatusCodes;
import org.eclipse.milo.opcua.stack.core.encoding.EncodingContext;
import org.eclipse.milo.opcua.stack.core.types.UaStructuredType;
import org.eclipse.milo.opcua.stack.core.types.builtin.ExtensionObject;
import org.eclipse.milo.opcua.stack.core.types.builtin.Matrix;
import org.eclipse.milo.opcua.stack.core.types.builtin.NodeId;
import org.eclipse.milo.opcua.stack.core.types.builtin.StatusCode;
import org.eclipse.milo.opcua.stack.core.types.builtin.Variant;
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.UShort;
import org.eclipse.milo.opcua.stack.core.types.enumerated.BrowseDirection;
import org.eclipse.milo.opcua.stack.core.types.enumerated.BrowseResultMask;
import org.eclipse.milo.opcua.stack.core.types.structured.BrowseDescription;
import org.eclipse.milo.opcua.stack.core.types.structured.BrowseResult;
import org.eclipse.milo.opcua.stack.core.types.structured.CallMethodRequest;
import org.eclipse.milo.opcua.stack.core.types.structured.CallMethodResult;
import org.eclipse.milo.opcua.stack.core.types.structured.CallResponse;
import org.eclipse.milo.opcua.stack.core.types.structured.ReferenceDescription;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.io.TempDir;

/**
 * End-to-end integration tests for {@link DemoMethodsFragment} with only {@code
 * address-space.methods.enabled = true}, verifying all nine callable Methods under {@code
 * Demo/Methods} over {@code SecurityPolicy.None} with an anonymous session.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DemoMethodsIT {

  private OpcUaDemoServer server;
  private OpcUaClient client;
  private UShort demoNsIndex;
  private EncodingContext encodingContext;

  @BeforeAll
  void setUp(@TempDir Path tempDir) throws Exception {
    Config customConfig = ConfigFactory.parseMap(Map.of("address-space.methods.enabled", true));

    server = OpcUaTestServerBuilder.builder().withDataDir(tempDir).withConfig(customConfig).build();
    server.startup();

    client = OpcUaTestClient.create(server.getServer());
    client.connect();

    NamespaceTable nsTable = server.getServer().getNamespaceTable();
    demoNsIndex = nsTable.getIndex(DemoNamespace.NAMESPACE_URI);
    assertNotNull(demoNsIndex);
    encodingContext = server.getServer().getStaticEncodingContext();
  }

  @AfterAll
  void tearDown() throws Exception {
    if (client != null) {
      client.disconnect();
    }
    if (server != null) {
      server.shutdown();
    }
  }

  @Test
  void noArgsExecutesWithZeroInputsAndOutputs() throws Exception {
    CallMethodResult result = callMethod(demoNode("Demo.Methods"), demoNode("Demo.Methods.NoArgs"));
    assertTrue(result.getStatusCode().isGood());
    Variant[] outputs = result.getOutputArguments();
    assertTrue(outputs == null || outputs.length == 0);
  }

  @Test
  void echoRecordSumArrayAndGetRecordsSucceedAndDecodeDynamically() throws Exception {
    NodeId methodsFolder = demoNode("Demo.Methods");

    DemoRecord inputRecord = new DemoRecord(42, "Alpha", 19.75, true);
    CallMethodResult echoResult =
        callMethod(
            methodsFolder,
            demoNode("Demo.Methods.EchoRecord"),
            Variant.ofExtensionObject(encode(inputRecord)));
    assertTrue(echoResult.getStatusCode().isGood());
    DemoRecord echoed =
        (DemoRecord)
            ((ExtensionObject) echoResult.getOutputArguments()[0].value()).decode(encodingContext);
    assertEquals(inputRecord, echoed);

    CallMethodResult sumResult =
        callMethod(
            methodsFolder,
            demoNode("Demo.Methods.SumInt32Array"),
            Variant.ofInt32Array(new Integer[] {10, 20, -5, 15}));
    assertTrue(sumResult.getStatusCode().isGood());
    assertEquals(40L, sumResult.getOutputArguments()[0].value());
    assertEquals(4, sumResult.getOutputArguments()[1].value());

    CallMethodResult getResult =
        callMethod(methodsFolder, demoNode("Demo.Methods.GetRecords"), Variant.ofInt32(3));
    assertTrue(getResult.getStatusCode().isGood());
    ExtensionObject.Binary primaryXo =
        assertInstanceOf(ExtensionObject.Binary.class, getResult.getOutputArguments()[0].value());
    ExtensionObject freshCopy =
        ExtensionObject.of(primaryXo.getBody(), primaryXo.getEncodingOrTypeId());
    assertNotNull(freshCopy.decode(client.getDynamicEncodingContext()));
    assertEquals("Primary", ((DemoRecord) primaryXo.decode(encodingContext)).getName());

    ExtensionObject[] records = (ExtensionObject[]) getResult.getOutputArguments()[1].value();
    assertEquals(3, records.length);
    assertEquals("Record-3", ((DemoRecord) records[2].decode(encodingContext)).getName());
  }

  @Test
  void formatMessageSupportsOptionalTrailingInputsAndDescriptionReferences() throws Exception {
    NodeId methodsFolder = demoNode("Demo.Methods");
    NodeId formatMethod = demoNode("Demo.Methods.FormatMessage");

    Set<NodeId> optionalDescs =
        browseForwardTargets(formatMethod, NodeIds.HasOptionalInputArgumentDescription);
    assertEquals(
        Set.of(
            demoNode("Demo.Methods.FormatMessage.repeatCount"),
            demoNode("Demo.Methods.FormatMessage.uppercase")),
        optionalDescs);

    CallMethodResult missing = callMethod(methodsFolder, formatMethod);
    assertEquals(StatusCodes.Bad_ArgumentsMissing, missing.getStatusCode().getValue());

    CallMethodResult oneArg = callMethod(methodsFolder, formatMethod, Variant.ofString("hello"));
    assertTrue(oneArg.getStatusCode().isGood());
    assertEquals("hello", oneArg.getOutputArguments()[0].value());

    CallMethodResult twoArgs =
        callMethod(methodsFolder, formatMethod, Variant.ofString("hi"), Variant.ofInt32(3));
    assertTrue(twoArgs.getStatusCode().isGood());
    assertEquals("hi hi hi", twoArgs.getOutputArguments()[0].value());

    CallMethodResult threeArgs =
        callMethod(
            methodsFolder,
            formatMethod,
            Variant.ofString("ok"),
            Variant.ofInt32(2),
            Variant.ofBoolean(true));
    assertTrue(threeArgs.getStatusCode().isGood());
    assertEquals("OK OK", threeArgs.getOutputArguments()[0].value());
  }

  @Test
  void inspectAbstractAndDescribeRankAcceptPolymorphicInputs() throws Exception {
    NodeId methodsFolder = demoNode("Demo.Methods");

    CallMethodResult abstractResult =
        callMethod(
            methodsFolder,
            demoNode("Demo.Methods.InspectAbstract"),
            Variant.ofDouble(2.5),
            Variant.ofExtensionObject(encode(new DemoRecord(1, "Pay", 3.0, false))));
    assertTrue(abstractResult.getStatusCode().isGood());
    assertEquals("Double", abstractResult.getOutputArguments()[0].value());
    assertTrue(((String) abstractResult.getOutputArguments()[1].value()).contains("DemoRecord"));
    assertEquals(2.5, abstractResult.getOutputArguments()[2].value());

    NodeId describeRank = demoNode("Demo.Methods.DescribeRank");

    CallMethodResult scalarCall =
        callMethod(methodsFolder, describeRank, Variant.ofString("abc"), Variant.ofInt32(5));
    assertTrue(scalarCall.getStatusCode().isGood());
    assertEquals("scalar:String", scalarCall.getOutputArguments()[0].value());
    assertEquals(1, scalarCall.getOutputArguments()[1].value());

    CallMethodResult matrixCall =
        callMethod(
            methodsFolder,
            describeRank,
            Variant.ofMatrix(Matrix.ofInt32(new Integer[][] {{1, 2}, {3, 4}})),
            Variant.ofInt32Array(new Integer[] {7, 8, 9}));
    assertTrue(matrixCall.getStatusCode().isGood());
    assertEquals("matrix[2, 2]:Integer", matrixCall.getOutputArguments()[0].value());
    assertEquals(3, matrixCall.getOutputArguments()[1].value());
  }

  @Test
  void configureControllerExposesArgumentDescriptionsAndValidateInputsReportsPerArgumentErrors()
      throws Exception {

    NodeId methodsFolder = demoNode("Demo.Methods");
    NodeId configureMethod = demoNode("Demo.Methods.ConfigureController");

    Set<NodeId> descriptions =
        browseForwardTargets(configureMethod, NodeIds.HasArgumentDescription);
    assertEquals(
        Set.of(
            demoNode("Demo.Methods.ConfigureController.setpoint"),
            demoNode("Demo.Methods.ConfigureController.retries"),
            demoNode("Demo.Methods.ConfigureController.enabled"),
            demoNode("Demo.Methods.ConfigureController.mode")),
        descriptions);

    CallMethodResult okConfig =
        callMethod(
            methodsFolder,
            configureMethod,
            Variant.ofDouble(45.5),
            Variant.ofInt32(2),
            Variant.ofBoolean(true),
            Variant.ofUInt32(uint(2)));
    assertTrue(okConfig.getStatusCode().isGood());
    assertEquals(
        "setpoint=45.5 °C, retries=2, enabled=Enabled, mode=Manual",
        okConfig.getOutputArguments()[0].value());

    NodeId validateMethod = demoNode("Demo.Methods.ValidateInputs");
    CallMethodResult invalid =
        callMethod(
            methodsFolder,
            validateMethod,
            Variant.ofInt32(0),
            Variant.ofString("valid-label"),
            Variant.ofDouble(150.0));
    assertEquals(StatusCodes.Bad_InvalidArgument, invalid.getStatusCode().getValue());
    StatusCode[] argResults = invalid.getInputArgumentResults();
    assertEquals(3, argResults.length);
    assertEquals(StatusCodes.Bad_OutOfRange, argResults[0].getValue());
    assertTrue(argResults[1].isGood());
    assertEquals(StatusCodes.Bad_OutOfRange, argResults[2].getValue());

    CallMethodResult valid =
        callMethod(
            methodsFolder,
            validateMethod,
            Variant.ofInt32(5),
            Variant.ofString("valid-label"),
            Variant.ofDouble(75.0));
    assertTrue(valid.getStatusCode().isGood());
    assertEquals("valid-label: 5 (75.0%)", valid.getOutputArguments()[0].value());
    assertFalse(valid.getOutputArguments()[0].isNull());
  }

  // region Helpers

  private NodeId demoNode(String identifier) {
    return new NodeId(demoNsIndex, identifier);
  }

  private ExtensionObject encode(UaStructuredType struct) {
    return ExtensionObject.encode(encodingContext, struct);
  }

  private CallMethodResult callMethod(NodeId objectId, NodeId methodId, Variant... inputs)
      throws Exception {

    CallMethodRequest request = new CallMethodRequest(objectId, methodId, inputs);
    CallResponse response = client.call(List.of(request));
    return response.getResults()[0];
  }

  private Set<NodeId> browseForwardTargets(NodeId sourceId, NodeId referenceTypeId)
      throws Exception {

    BrowseDescription bd =
        new BrowseDescription(
            sourceId,
            BrowseDirection.Forward,
            referenceTypeId,
            true,
            uint(0),
            uint(BrowseResultMask.All.getValue()));

    BrowseResult result = client.browse(bd);
    assertTrue(result.getStatusCode().isGood());
    ReferenceDescription[] refs = result.getReferences();
    if (refs == null) {
      return Set.of();
    }

    NamespaceTable nsTable = client.getNamespaceTable();
    return List.of(refs).stream()
        .map(ref -> ref.getNodeId().toNodeId(nsTable).orElseThrow())
        .collect(Collectors.toSet());
  }

  // endregion
}

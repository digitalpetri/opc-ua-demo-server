package com.digitalpetri.opcua.server.namespace.demo.constraints;

import static org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.Unsigned.uint;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.digitalpetri.opcua.server.OpcUaDemoServer;
import com.digitalpetri.opcua.server.OpcUaTestClient;
import com.digitalpetri.opcua.server.OpcUaTestServerBuilder;
import com.digitalpetri.opcua.server.namespace.demo.DemoNamespace;
import com.digitalpetri.opcua.server.namespace.demo.DemoValueFilters;
import com.digitalpetri.opcua.server.namespace.demo.types.NestedNode;
import com.digitalpetri.opcua.server.namespace.demo.types.NullableFieldsStruct;
import com.digitalpetri.opcua.server.namespace.demo.types.RefinableStruct;
import com.digitalpetri.opcua.test.types.ConcreteTestType;
import com.digitalpetri.opcua.test.types.ConcreteTestTypeEx;
import com.digitalpetri.opcua.test.types.UnionOfScalar;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.eclipse.milo.opcua.sdk.client.OpcUaClient;
import org.eclipse.milo.opcua.stack.core.AttributeId;
import org.eclipse.milo.opcua.stack.core.NamespaceTable;
import org.eclipse.milo.opcua.stack.core.NodeIds;
import org.eclipse.milo.opcua.stack.core.StatusCodes;
import org.eclipse.milo.opcua.stack.core.encoding.EncodingContext;
import org.eclipse.milo.opcua.stack.core.types.UaStructuredType;
import org.eclipse.milo.opcua.stack.core.types.builtin.ByteString;
import org.eclipse.milo.opcua.stack.core.types.builtin.DataValue;
import org.eclipse.milo.opcua.stack.core.types.builtin.ExtensionObject;
import org.eclipse.milo.opcua.stack.core.types.builtin.LocalizedText;
import org.eclipse.milo.opcua.stack.core.types.builtin.Matrix;
import org.eclipse.milo.opcua.stack.core.types.builtin.NodeId;
import org.eclipse.milo.opcua.stack.core.types.builtin.QualifiedName;
import org.eclipse.milo.opcua.stack.core.types.builtin.StatusCode;
import org.eclipse.milo.opcua.stack.core.types.builtin.Variant;
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.UShort;
import org.eclipse.milo.opcua.stack.core.types.enumerated.BrowseDirection;
import org.eclipse.milo.opcua.stack.core.types.enumerated.BrowseResultMask;
import org.eclipse.milo.opcua.stack.core.types.enumerated.TimestampsToReturn;
import org.eclipse.milo.opcua.stack.core.types.structured.BrowseDescription;
import org.eclipse.milo.opcua.stack.core.types.structured.BrowseResult;
import org.eclipse.milo.opcua.stack.core.types.structured.ReadValueId;
import org.eclipse.milo.opcua.stack.core.types.structured.ReferenceDescription;
import org.eclipse.milo.opcua.stack.core.types.structured.WriteResponse;
import org.eclipse.milo.opcua.stack.core.types.structured.WriteValue;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.io.TempDir;

/**
 * End-to-end integration tests for {@link DemoConstraintsFragment} with only {@code
 * address-space.constraints.enabled = true}, verifying null semantics, limits, access levels,
 * subtype restrictions, data-type refinements, and scale fixtures.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DemoConstraintsIT {

  private OpcUaDemoServer server;
  private OpcUaClient client;
  private UShort demoNsIndex;
  private EncodingContext encodingContext;

  @BeforeAll
  void setUp(@TempDir Path tempDir) throws Exception {
    Config customConfig = ConfigFactory.parseMap(Map.of("address-space.constraints.enabled", true));

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
    Throwable disconnectFailure = null;
    try {
      if (client != null) {
        client.disconnect();
      }
    } catch (Throwable t) {
      disconnectFailure = t;
    } finally {
      if (server != null) {
        try {
          server.shutdown();
        } catch (Throwable t) {
          if (disconnectFailure != null) {
            disconnectFailure.addSuppressed(t);
          } else {
            throw t;
          }
        }
      }
    }

    if (disconnectFailure instanceof Exception e) {
      throw e;
    } else if (disconnectFailure instanceof Error e) {
      throw e;
    } else if (disconnectFailure != null) {
      throw new RuntimeException(disconnectFailure);
    }
  }

  @Nested
  class Nulls {

    @Test
    void writableNullsAcceptConcreteAndNullWrites() throws Exception {
      NodeId stringNull = demoNode("Demo.Constraints.Nulls.Writable.String");
      assertNull(client.readValue(0.0, TimestampsToReturn.Neither, stringNull).value().value());
      assertTrue(writeValue(stringNull, Variant.ofString("filled")).isGood());
      assertTrue(writeValue(stringNull, Variant.NULL_VALUE).isGood());

      NodeId unionNull = demoNode("Demo.Constraints.Nulls.Writable.Union");
      UnionOfScalar initialUnion =
          (UnionOfScalar)
              ((ExtensionObject)
                      client.readValue(0.0, TimestampsToReturn.Neither, unionNull).value().value())
                  .decode(encodingContext);
      assertTrue(DemoValueFilters.isNullUnion(initialUnion));
      assertTrue(
          writeValue(unionNull, Variant.ofExtensionObject(encode(UnionOfScalar.ofBoolean(true))))
              .isGood());
      assertTrue(
          writeValue(unionNull, Variant.ofExtensionObject(encode(UnionOfScalar.ofNull())))
              .isGood());
    }

    @Test
    void pairedOutcomesDistinguishRootNullTypedNullAndEmptyValue() throws Exception {
      NodeId strAllow = demoNode("Demo.Constraints.Nulls.Paired.StringAllowNulls");
      NodeId strDisallow = demoNode("Demo.Constraints.Nulls.Paired.StringDisallowNulls");
      assertTrue(writeValue(strAllow, Variant.NULL_VALUE).isGood());
      assertEquals(
          StatusCodes.Bad_TypeMismatch, writeValue(strDisallow, Variant.NULL_VALUE).getValue());
      assertTrue(writeValue(strDisallow, Variant.ofString("")).isGood());

      NodeId bsAllow = demoNode("Demo.Constraints.Nulls.Paired.ByteStringAllowNulls");
      NodeId bsDisallow = demoNode("Demo.Constraints.Nulls.Paired.ByteStringDisallowNulls");
      assertTrue(writeValue(bsAllow, Variant.NULL_VALUE).isGood());
      assertTrue(writeValue(bsAllow, Variant.ofByteString(ByteString.NULL_VALUE)).isGood());
      assertEquals(
          StatusCodes.Bad_TypeMismatch,
          writeValue(bsDisallow, Variant.ofByteString(ByteString.NULL_VALUE)).getValue());
      assertTrue(writeValue(bsDisallow, Variant.ofByteString(ByteString.of(new byte[0]))).isGood());

      NodeId unionAllow = demoNode("Demo.Constraints.Nulls.Paired.UnionAllowNulls");
      NodeId unionDisallow = demoNode("Demo.Constraints.Nulls.Paired.UnionDisallowNulls");
      assertTrue(
          writeValue(unionAllow, Variant.ofExtensionObject(encode(UnionOfScalar.ofNull())))
              .isGood());
      assertEquals(
          StatusCodes.Bad_TypeMismatch,
          writeValue(unionDisallow, Variant.ofExtensionObject(encode(UnionOfScalar.ofNull())))
              .getValue());
      assertTrue(
          writeValue(
                  unionDisallow, Variant.ofExtensionObject(encode(UnionOfScalar.ofSByte((byte) 5))))
              .isGood());

      NodeId nullableFields = demoNode("Demo.Constraints.Nulls.Paired.NullableFieldsStruct");
      assertEquals(
          StatusCodes.Bad_TypeMismatch, writeValue(nullableFields, Variant.NULL_VALUE).getValue());
      NullableFieldsStruct withNullFields =
          new NullableFieldsStruct(
              null, ByteString.of(new byte[0]), LocalizedText.NULL_VALUE, NodeId.NULL_VALUE, null);
      assertTrue(
          writeValue(nullableFields, Variant.ofExtensionObject(encode(withNullFields))).isGood());
    }
  }

  @Nested
  class Limits {

    @Test
    void limitPropertiesAndArrayDimensionsEnforceBounds() throws Exception {
      NodeId maxStr = demoNode("Demo.Constraints.Limits.MaxStringLength");
      assertTrue(writeValue(maxStr, Variant.ofString("1234567890123456")).isGood());
      assertEquals(
          StatusCodes.Bad_OutOfRange,
          writeValue(maxStr, Variant.ofString("12345678901234567")).getValue());

      // "cafééééé" = 8 Unicode code points, 13 UTF-8 bytes -> accepted by MaxCharacters=8.
      NodeId maxChars = demoNode("Demo.Constraints.Limits.MaxCharacters");
      assertTrue(
          writeValue(maxChars, Variant.ofString("caf\u00E9\u00E9\u00E9\u00E9\u00E9")).isGood());
      assertEquals(
          StatusCodes.Bad_OutOfRange,
          writeValue(maxChars, Variant.ofString("123456789")).getValue());

      NodeId maxBs = demoNode("Demo.Constraints.Limits.MaxByteStringLength");
      assertTrue(writeValue(maxBs, Variant.ofByteString(ByteString.of(new byte[8]))).isGood());
      assertEquals(
          StatusCodes.Bad_OutOfRange,
          writeValue(maxBs, Variant.ofByteString(ByteString.of(new byte[9]))).getValue());

      NodeId maxArr = demoNode("Demo.Constraints.Limits.MaxArrayLength");
      assertTrue(writeValue(maxArr, Variant.ofInt32Array(new Integer[] {1, 2, 3, 4, 5})).isGood());
      assertEquals(
          StatusCodes.Bad_OutOfRange,
          writeValue(maxArr, Variant.ofInt32Array(new Integer[] {1, 2, 3, 4, 5, 6})).getValue());

      NodeId bounded1D = demoNode("Demo.Constraints.Limits.BoundedArray");
      assertTrue(writeValue(bounded1D, Variant.ofInt32Array(new Integer[] {1, 2})).isGood());
      assertEquals(
          StatusCodes.Bad_TypeMismatch,
          writeValue(bounded1D, Variant.ofInt32Array(new Integer[] {1, 2, 3, 4, 5, 6})).getValue());

      NodeId bounded2D = demoNode("Demo.Constraints.Limits.BoundedMatrix");
      Matrix tooWide = Matrix.ofDouble(new Double[][] {{1.0, 2.0, 3.0, 4.0}, {5.0, 6.0, 7.0, 8.0}});
      assertEquals(
          StatusCodes.Bad_TypeMismatch,
          writeValue(bounded2D, Variant.ofMatrix(tooWide)).getValue());

      NodeId fixedSize = demoNode("Demo.Constraints.Limits.FixedSizeArray");
      assertTrue(writeValue(fixedSize, Variant.ofInt32Array(new Integer[] {9, 8, 7, 6})).isGood());
      assertEquals(
          StatusCodes.Bad_OutOfRange,
          writeValue(fixedSize, Variant.ofInt32Array(new Integer[] {9, 8})).getValue());
      assertEquals(
          StatusCodes.Bad_TypeMismatch,
          writeValue(fixedSize, Variant.ofInt32Array(new Integer[] {1, 2, 3, 4, 5})).getValue());
    }
  }

  @Nested
  class Access {

    @Test
    void writeFullArrayOnlyRejectsIndexRangeAndAcceptsFullWrite() throws Exception {
      NodeId nodeId = demoNode("Demo.Constraints.Access.WriteFullArrayOnly");

      StatusCode rangeStatus =
          writeValueWithIndexRange(nodeId, "0:1", Variant.ofInt32Array(new Integer[] {100, 200}));
      assertEquals(StatusCodes.Bad_WriteNotSupported, rangeStatus.getValue());

      StatusCode fullStatus =
          writeValue(nodeId, Variant.ofInt32Array(new Integer[] {50, 60, 70, 80}));
      assertTrue(fullStatus.isGood());
    }

    @Test
    void noSubDataTypesRejectsConcreteTestTypeExWhileSubtypableAcceptsIt() throws Exception {
      NodeId noSubId = demoNode("Demo.Constraints.Access.NoSubDataTypes");
      NodeId subtypableId = demoNode("Demo.Constraints.Access.Subtypable");

      ConcreteTestType base = new ConcreteTestType((short) 10, 1.5, "exact", true);
      ConcreteTestTypeEx derived =
          new ConcreteTestTypeEx((short) 11, 2.5, "derived", false, uint(3));

      assertTrue(writeValue(noSubId, Variant.ofExtensionObject(encode(base))).isGood());
      assertEquals(
          StatusCodes.Bad_TypeMismatch,
          writeValue(noSubId, Variant.ofExtensionObject(encode(derived))).getValue());

      assertTrue(writeValue(subtypableId, Variant.ofExtensionObject(encode(derived))).isGood());
      assertTrue(writeValue(subtypableId, Variant.ofExtensionObject(encode(base))).isGood());

      NullableFieldsStruct unrelated =
          new NullableFieldsStruct(
              "wrong", ByteString.NULL_VALUE, LocalizedText.NULL_VALUE, NodeId.NULL_VALUE, null);
      assertEquals(
          StatusCodes.Bad_TypeMismatch,
          writeValue(subtypableId, Variant.ofExtensionObject(encode(unrelated))).getValue());

      // Matching encoding ID with null or undecodable binary body must also be rejected.
      NodeId concreteEncodingId =
          ConcreteTestType.BINARY_ENCODING_ID
              .toNodeId(server.getServer().getNamespaceTable())
              .orElseThrow();
      ExtensionObject nullBody = ExtensionObject.of(ByteString.NULL_VALUE, concreteEncodingId);
      ExtensionObject truncatedBody =
          ExtensionObject.of(ByteString.of(new byte[] {0x01}), concreteEncodingId);

      assertEquals(
          StatusCodes.Bad_TypeMismatch,
          writeValue(noSubId, Variant.ofExtensionObject(nullBody)).getValue());
      assertEquals(
          StatusCodes.Bad_TypeMismatch,
          writeValue(noSubId, Variant.ofExtensionObject(truncatedBody)).getValue());
      assertEquals(
          StatusCodes.Bad_TypeMismatch,
          writeValue(subtypableId, Variant.ofExtensionObject(nullBody)).getValue());
      assertEquals(
          StatusCodes.Bad_TypeMismatch,
          writeValue(subtypableId, Variant.ofExtensionObject(truncatedBody)).getValue());
    }

    @Test
    void writeOnlyAndReadOnlyVariablesEnforceAccessLevels() throws Exception {
      NodeId writeOnlyId = demoNode("Demo.Constraints.Access.WriteOnly");
      DataValue valueRead = client.readValue(0.0, TimestampsToReturn.Neither, writeOnlyId);
      assertEquals(StatusCodes.Bad_NotReadable, valueRead.statusCode().getValue());
      assertEquals(NodeIds.Int32, readAttribute(writeOnlyId, AttributeId.DataType).value().value());
      assertTrue(writeValue(writeOnlyId, Variant.ofInt32(77)).isGood());

      NodeId writeOnlyUserId = demoNode("Demo.Constraints.Access.WriteOnlyUser");
      DataValue userRead = client.readValue(0.0, TimestampsToReturn.Neither, writeOnlyUserId);
      assertEquals(StatusCodes.Bad_UserAccessDenied, userRead.statusCode().getValue());
      assertTrue(writeValue(writeOnlyUserId, Variant.ofInt32(88)).isGood());

      NodeId readOnlyId = demoNode("Demo.Constraints.Access.ReadOnly");
      DataValue readOnlyVal = client.readValue(0.0, TimestampsToReturn.Neither, readOnlyId);
      assertTrue(readOnlyVal.statusCode().isGood());
      assertEquals(42, readOnlyVal.value().value());
      assertEquals(
          StatusCodes.Bad_NotWritable, writeValue(readOnlyId, Variant.ofInt32(99)).getValue());
    }
  }

  @Nested
  class Restrictions {

    @Test
    void subtypeRestrictionBrowsesAndEnforcesAllowedSubtypes() throws Exception {
      NodeId restrictedId = demoNode("Demo.Constraints.Restrictions.RestrictedNumber");
      Set<NodeId> restrictionTargets =
          browseForwardTargets(restrictedId, NodeIds.UsesSubtypeRestriction);
      assertEquals(
          Set.of(demoNode("Demo.Constraints.Restrictions.NumberRestriction")), restrictionTargets);

      assertTrue(writeValue(restrictedId, Variant.ofInt32(100)).isGood());
      assertTrue(writeValue(restrictedId, Variant.ofDouble(12.5)).isGood());
      assertEquals(
          StatusCodes.Bad_TypeMismatch, writeValue(restrictedId, Variant.ofFloat(1.5f)).getValue());
    }

    @Test
    void dataTypeRefinementBrowsesAndEnforcesFieldConstraints() throws Exception {
      NodeId refinedId = demoNode("Demo.Constraints.Restrictions.RefinedStruct");
      Set<NodeId> refinementTargets =
          browseForwardTargets(refinedId, NodeIds.UsesDataTypeRefinement);
      assertEquals(
          Set.of(demoNode("Demo.Constraints.Restrictions.RefinableStructProfile")),
          refinementTargets);

      RefinableStruct valid =
          new RefinableStruct("Sensor-2", "Within 32 bytes", null, Variant.ofDouble(36.6));
      assertTrue(writeValue(refinedId, Variant.ofExtensionObject(encode(valid))).isGood());

      RefinableStruct missingNote =
          new RefinableStruct("Sensor-2", null, null, Variant.ofDouble(36.6));
      assertEquals(
          StatusCodes.Bad_OutOfRange,
          writeValue(refinedId, Variant.ofExtensionObject(encode(missingNote))).getValue());

      RefinableStruct withDisabled =
          new RefinableStruct("Sensor-2", "Note", false, Variant.ofDouble(36.6));
      assertEquals(
          StatusCodes.Bad_OutOfRange,
          writeValue(refinedId, Variant.ofExtensionObject(encode(withDisabled))).getValue());

      RefinableStruct nonDoubleReading =
          new RefinableStruct("Sensor-2", "Note", null, Variant.ofInt32(36));
      assertEquals(
          StatusCodes.Bad_TypeMismatch,
          writeValue(refinedId, Variant.ofExtensionObject(encode(nonDoubleReading))).getValue());
    }
  }

  @Nested
  class Scale {

    @Test
    void largeInt32ArrayReadsHundredThousandElements() throws Exception {
      NodeId largeArrayId = demoNode("Demo.Constraints.Scale.LargeInt32Array");
      DataValue dataValue = client.readValue(0.0, TimestampsToReturn.Neither, largeArrayId);
      assertTrue(dataValue.statusCode().isGood());

      Integer[] values = assertInstanceOf(Integer[].class, dataValue.value().value());
      assertEquals(DemoConstraintsFragment.LARGE_ARRAY_LENGTH, values.length);
      assertEquals(0, values[0]);
      assertEquals(DemoConstraintsFragment.LARGE_ARRAY_LENGTH - 1, values[values.length - 1]);
    }

    @Test
    void deeplyNestedStructDecodesTwentyFiveLevelsStaticallyAndDynamically() throws Exception {
      NodeId nestedId = demoNode("Demo.Constraints.Scale.DeeplyNestedStruct");
      DataValue dataValue = client.readValue(0.0, TimestampsToReturn.Neither, nestedId);
      assertTrue(dataValue.statusCode().isGood());

      ExtensionObject.Binary xo =
          assertInstanceOf(ExtensionObject.Binary.class, dataValue.value().value());
      ExtensionObject freshCopy = ExtensionObject.of(xo.getBody(), xo.getEncodingOrTypeId());
      assertNotNull(freshCopy.decode(client.getDynamicEncodingContext()));

      NestedNode root = (NestedNode) xo.decode(encodingContext);
      int count = 0;
      NestedNode cursor = root;
      while (cursor != null) {
        count++;
        assertEquals(count, cursor.getDepth());
        cursor = cursor.getChild();
      }
      assertEquals(DemoConstraintsFragment.NESTED_CHAIN_DEPTH, count);
    }
  }

  // region Helpers

  private NodeId demoNode(String identifier) {
    return new NodeId(demoNsIndex, identifier);
  }

  private ExtensionObject encode(UaStructuredType struct) {
    return ExtensionObject.encode(encodingContext, struct);
  }

  private DataValue readAttribute(NodeId nodeId, AttributeId attributeId) throws Exception {
    ReadValueId rvid = new ReadValueId(nodeId, attributeId.uid(), null, QualifiedName.NULL_VALUE);
    return client.read(0.0, TimestampsToReturn.Neither, List.of(rvid)).getResults()[0];
  }

  private StatusCode writeValue(NodeId nodeId, Variant value) throws Exception {
    return writeValueWithIndexRange(nodeId, null, value);
  }

  private StatusCode writeValueWithIndexRange(
      NodeId nodeId, @Nullable String indexRange, Variant value) throws Exception {

    WriteValue writeValue =
        new WriteValue(nodeId, AttributeId.Value.uid(), indexRange, DataValue.valueOnly(value));
    WriteResponse response = client.write(List.of(writeValue));
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

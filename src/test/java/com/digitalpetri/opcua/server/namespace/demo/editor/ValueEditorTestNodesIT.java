package com.digitalpetri.opcua.server.namespace.demo.editor;

import static org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.Unsigned.uint;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.digitalpetri.opcua.server.OpcUaDemoServer;
import com.digitalpetri.opcua.server.OpcUaTestClient;
import com.digitalpetri.opcua.server.OpcUaTestServerBuilder;
import com.digitalpetri.opcua.server.namespace.demo.DemoNamespace;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import java.math.BigDecimal;
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
import org.eclipse.milo.opcua.stack.core.types.builtin.ByteString;
import org.eclipse.milo.opcua.stack.core.types.builtin.DataValue;
import org.eclipse.milo.opcua.stack.core.types.builtin.ExtensionObject;
import org.eclipse.milo.opcua.stack.core.types.builtin.LocalizedText;
import org.eclipse.milo.opcua.stack.core.types.builtin.Matrix;
import org.eclipse.milo.opcua.stack.core.types.builtin.NodeId;
import org.eclipse.milo.opcua.stack.core.types.builtin.QualifiedName;
import org.eclipse.milo.opcua.stack.core.types.builtin.StatusCode;
import org.eclipse.milo.opcua.stack.core.types.builtin.Variant;
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.UInteger;
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.UShort;
import org.eclipse.milo.opcua.stack.core.types.enumerated.BrowseDirection;
import org.eclipse.milo.opcua.stack.core.types.enumerated.BrowseResultMask;
import org.eclipse.milo.opcua.stack.core.types.enumerated.TimestampsToReturn;
import org.eclipse.milo.opcua.stack.core.types.structured.BrowseDescription;
import org.eclipse.milo.opcua.stack.core.types.structured.BrowseResult;
import org.eclipse.milo.opcua.stack.core.types.structured.CallMethodRequest;
import org.eclipse.milo.opcua.stack.core.types.structured.CallMethodResult;
import org.eclipse.milo.opcua.stack.core.types.structured.CallResponse;
import org.eclipse.milo.opcua.stack.core.types.structured.EnumDefinition;
import org.eclipse.milo.opcua.stack.core.types.structured.ReadValueId;
import org.eclipse.milo.opcua.stack.core.types.structured.ReferenceDescription;
import org.eclipse.milo.opcua.stack.core.types.structured.WriteResponse;
import org.eclipse.milo.opcua.stack.core.types.structured.WriteValue;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.io.TempDir;

/**
 * End-to-end integration tests for {@link ValueEditorTestNodesFragment} over {@code
 * SecurityPolicy.None} with an anonymous client, verifying every value shape, write restriction,
 * scale fixture, and callable Method under {@code Demo/ValueEditorTest}.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ValueEditorTestNodesIT {

  private OpcUaDemoServer server;
  private OpcUaClient client;
  private UShort demoNsIndex;
  private EncodingContext encodingContext;

  @BeforeAll
  void setUp(@TempDir Path tempDir) throws Exception {
    Config customConfig =
        ConfigFactory.parseMap(Map.of("address-space.value-editor-test.enabled", true));

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

  @Nested
  class OptionSets {

    @Test
    void optionSetScalarAppliesValidBitsMaskAndRejectsUndefinedBit() throws Exception {
      NodeId nodeId = demoNode("Demo.ValueEditorTest.OptionSets.OptionSetScalar");

      DataValue initial = client.readValue(0.0, TimestampsToReturn.Neither, nodeId);
      assertTrue(initial.statusCode().isGood());
      DemoOptionSet before =
          (DemoOptionSet) ((ExtensionObject) initial.value().value()).decode(encodingContext);
      assertArrayEquals(new byte[] {0x03, 0x01}, before.getValue().bytesOrEmpty());

      // Part 3 §8.40: partial ValidBits mask updates only bit 0 (clear) and bit 2 (set),
      // preserving bit 1 (Write) and bit 8 (Audit).
      DemoOptionSet partial =
          new DemoOptionSet(
              ByteString.of(new byte[] {0x04, 0x02}), ByteString.of(new byte[] {0x05, 0x02}));
      StatusCode writeStatus = writeValue(nodeId, Variant.ofExtensionObject(encode(partial)));
      assertTrue(writeStatus.isGood(), "partial OptionSet write failed: " + writeStatus);

      DemoOptionSet after =
          (DemoOptionSet)
              ((ExtensionObject)
                      client.readValue(0.0, TimestampsToReturn.Neither, nodeId).value().value())
                  .decode(encodingContext);
      assertArrayEquals(new byte[] {0x06, 0x03}, after.getValue().bytesOrEmpty());
      assertArrayEquals(DemoOptionSet.DEFINED_VALID_BITS, after.getValidBits().bytesOrEmpty());

      // Setting undefined gap bit 4 (0x10) in ValidBits must fail with Bad_OutOfRange.
      DemoOptionSet invalid =
          new DemoOptionSet(
              ByteString.of(new byte[] {0x10, 0x00}), ByteString.of(new byte[] {0x10, 0x00}));
      StatusCode invalidStatus = writeValue(nodeId, Variant.ofExtensionObject(encode(invalid)));
      assertEquals(StatusCodes.Bad_OutOfRange, invalidStatus.getValue());
    }

    @Test
    void optionSetArrayAndStructWithOptionSetReadAndWrite() throws Exception {
      NodeId arrayId = demoNode("Demo.ValueEditorTest.OptionSets.OptionSetArray");
      ExtensionObject[] elements =
          (ExtensionObject[])
              client.readValue(0.0, TimestampsToReturn.Neither, arrayId).value().value();
      assertEquals(2, elements.length);

      DemoOptionSet[] updatedArray = {DemoOptionSet.of(0x0F, 0x01), DemoOptionSet.of(0x02, 0x02)};
      StatusCode arrayWrite =
          writeValue(
              arrayId,
              Variant.ofExtensionObjectArray(
                  ExtensionObject.encodeArray(encodingContext, updatedArray)));
      assertTrue(arrayWrite.isGood());

      NodeId structId = demoNode("Demo.ValueEditorTest.OptionSets.StructWithOptionSet");
      StructWithOptionSet updatedStruct =
          new StructWithOptionSet("UpdatedProfile", DemoOptionSet.of(0x09, 0x03));
      StatusCode structWrite =
          writeValue(structId, Variant.ofExtensionObject(encode(updatedStruct)));
      assertTrue(structWrite.isGood());

      StructWithOptionSet readBack =
          (StructWithOptionSet)
              ((ExtensionObject)
                      client.readValue(0.0, TimestampsToReturn.Neither, structId).value().value())
                  .decode(encodingContext);
      assertEquals("UpdatedProfile", readBack.getName());
      assertArrayEquals(new byte[] {0x09, 0x03}, readBack.getOptions().getValue().bytesOrEmpty());
    }

    @Test
    void integerOptionSetStandaloneAndArrayVariablesAreWritable() throws Exception {
      NodeId customScalar = demoNode("Demo.ValueEditorTest.OptionSets.DemoOptionSetUInt32Scalar");
      assertTrue(writeValue(customScalar, Variant.ofUInt32(uint(0b11001))).isGood());
      assertEquals(
          uint(0b11001),
          client.readValue(0.0, TimestampsToReturn.Neither, customScalar).value().value());

      NodeId customArray = demoNode("Demo.ValueEditorTest.OptionSets.DemoOptionSetUInt32Array");
      UInteger[] newBits = {uint(0b00011), uint(0b10000)};
      assertTrue(writeValue(customArray, Variant.ofUInt32Array(newBits)).isGood());

      NodeId permScalar = demoNode("Demo.ValueEditorTest.OptionSets.PermissionTypeScalar");
      assertTrue(writeValue(permScalar, Variant.ofUInt32(uint(0x0013))).isGood());

      NodeId permArray = demoNode("Demo.ValueEditorTest.OptionSets.PermissionTypeArray");
      assertTrue(
          writeValue(permArray, Variant.ofUInt32Array(new UInteger[] {uint(0x0001)})).isGood());
    }
  }

  @Nested
  class Enumerations {

    @Test
    void sparseEnumExposesEnumDefinitionAndRejectsGapValues() throws Exception {
      NodeId typeId = demoNode(ValueEditorTestNodesFragment.SPARSE_STATUS_ENUM_IDENTIFIER);
      DataValue defVal = readAttribute(typeId, AttributeId.DataTypeDefinition);
      assertTrue(defVal.statusCode().isGood());
      EnumDefinition def =
          (EnumDefinition) ((ExtensionObject) defVal.value().value()).decode(encodingContext);
      assertEquals(5, def.getFields().length);

      NodeId scalarId = demoNode("Demo.ValueEditorTest.Enumerations.SparseEnumScalar");
      assertTrue(writeValue(scalarId, Variant.ofInt32(16)).isGood());
      assertEquals(16, client.readValue(0.0, TimestampsToReturn.Neither, scalarId).value().value());

      // Value 3 falls in the gap between 2 (Running) and 4 (Paused).
      StatusCode gapStatus = writeValue(scalarId, Variant.ofInt32(3));
      assertEquals(StatusCodes.Bad_OutOfRange, gapStatus.getValue());
    }

    @Test
    void enumSubtypeRejectsValuesRemovedFromSupertype() throws Exception {
      NodeId restrictedId = demoNode("Demo.ValueEditorTest.Enumerations.RestrictedEnumScalar");

      assertTrue(writeValue(restrictedId, Variant.ofInt32(4)).isGood());
      // 8 (Faulted) is valid in SparseStatusEnum but removed in OperationalStatusEnum.
      StatusCode removedStatus = writeValue(restrictedId, Variant.ofInt32(8));
      assertEquals(StatusCodes.Bad_OutOfRange, removedStatus.getValue());
    }
  }

  @Nested
  class SpecialTypes {

    @Test
    void decimalReadsWritesAndRejectsInvalidPayloads() throws Exception {
      NodeId decimalId = demoNode("Demo.ValueEditorTest.SpecialTypes.DecimalValue");

      DataValue initial = client.readValue(0.0, TimestampsToReturn.Neither, decimalId);
      assertTrue(initial.statusCode().isGood());
      assertEquals(
          new BigDecimal("123.45"),
          DecimalEncoding.decode((ExtensionObject) initial.value().value()));

      BigDecimal next = new BigDecimal("-4200.750");
      StatusCode okWrite =
          writeValue(decimalId, Variant.ofExtensionObject(DecimalEncoding.encode(next)));
      assertTrue(okWrite.isGood(), "Decimal write failed: " + okWrite);

      DataValue readBack = client.readValue(0.0, TimestampsToReturn.Neither, decimalId);
      assertEquals(next, DecimalEncoding.decode((ExtensionObject) readBack.value().value()));

      // Body length <= 2 is invalid per Part 6 §5.1.10.
      ExtensionObject tooShort =
          ExtensionObject.of(ByteString.of(new byte[] {0x02, 0x00}), NodeIds.Decimal);
      assertEquals(
          StatusCodes.Bad_OutOfRange,
          writeValue(decimalId, Variant.ofExtensionObject(tooShort)).getValue());

      // Non-ExtensionObject value fails with Bad_TypeMismatch.
      assertEquals(
          StatusCodes.Bad_TypeMismatch, writeValue(decimalId, Variant.ofDouble(1.5)).getValue());
    }

    @Test
    void opaqueAndUnknownEncodingStructuresAreReadableAndWritable() throws Exception {
      NodeId opaqueTypeId = demoNode(ValueEditorTestNodesFragment.OPAQUE_STRUCT_IDENTIFIER);
      DataValue defAttr = readAttribute(opaqueTypeId, AttributeId.DataTypeDefinition);
      assertEquals(StatusCodes.Bad_AttributeIdInvalid, defAttr.statusCode().getValue());

      NodeId opaqueVarId = demoNode("Demo.ValueEditorTest.SpecialTypes.OpaqueStructure");
      ExtensionObject.Binary initialOpaque =
          (ExtensionObject.Binary)
              client.readValue(0.0, TimestampsToReturn.Neither, opaqueVarId).value().value();
      assertEquals(8, initialOpaque.getBody().length());

      ExtensionObject replacement =
          ExtensionObject.of(
              ByteString.of(new byte[] {0x11, 0x22, 0x33}), initialOpaque.getEncodingOrTypeId());
      assertTrue(writeValue(opaqueVarId, Variant.ofExtensionObject(replacement)).isGood());

      NodeId unknownVarId = demoNode("Demo.ValueEditorTest.SpecialTypes.UnknownEncodingStructure");
      ExtensionObject.Binary initialUnknown =
          (ExtensionObject.Binary)
              client.readValue(0.0, TimestampsToReturn.Neither, unknownVarId).value().value();
      assertEquals(
          demoNode(ValueEditorTestNodesFragment.UNKNOWN_ENCODING_IDENTIFIER),
          initialUnknown.getEncodingOrTypeId());
    }

    @Test
    void emptyAbstractVariablesStartNullAndAcceptConcreteSubtypes() throws Exception {
      NodeId emptyNumberId = demoNode("Demo.ValueEditorTest.SpecialTypes.EmptyNumber");
      assertNull(client.readValue(0.0, TimestampsToReturn.Neither, emptyNumberId).value().value());
      assertTrue(writeValue(emptyNumberId, Variant.ofDouble(3.14)).isGood());
      assertEquals(
          3.14, client.readValue(0.0, TimestampsToReturn.Neither, emptyNumberId).value().value());
      assertTrue(writeValue(emptyNumberId, Variant.NULL_VALUE).isGood());

      NodeId emptyStructId = demoNode("Demo.ValueEditorTest.SpecialTypes.EmptyStructure");
      assertNull(client.readValue(0.0, TimestampsToReturn.Neither, emptyStructId).value().value());
      DemoRecord record = new DemoRecord(7, "Concrete", 9.5, true);
      assertTrue(writeValue(emptyStructId, Variant.ofExtensionObject(encode(record))).isGood());
      assertTrue(writeValue(emptyStructId, Variant.NULL_VALUE).isGood());
    }
  }

  @Nested
  class NullsAndPairedOutcomes {

    @Test
    void writableNullsAcceptConcreteAndNullWrites() throws Exception {
      NodeId stringNull = demoNode("Demo.ValueEditorTest.Nulls.WritableNulls.StringNull");
      assertNull(client.readValue(0.0, TimestampsToReturn.Neither, stringNull).value().value());
      assertTrue(writeValue(stringNull, Variant.ofString("filled")).isGood());
      assertTrue(writeValue(stringNull, Variant.NULL_VALUE).isGood());

      NodeId unionNull = demoNode("Demo.ValueEditorTest.Nulls.WritableNulls.UnionNull");
      DemoUnion initialUnion =
          (DemoUnion)
              ((ExtensionObject)
                      client.readValue(0.0, TimestampsToReturn.Neither, unionNull).value().value())
                  .decode(encodingContext);
      assertTrue(initialUnion.isNull());
      assertTrue(
          writeValue(unionNull, Variant.ofExtensionObject(encode(DemoUnion.ofInt32(99)))).isGood());
      assertTrue(
          writeValue(unionNull, Variant.ofExtensionObject(encode(DemoUnion.ofNull()))).isGood());
    }

    @Test
    void pairedOutcomesDistinguishRootNullTypedNullAndEmptyValue() throws Exception {
      NodeId strAllow = demoNode("Demo.ValueEditorTest.Nulls.PairedOutcomes.StringAllowNulls");
      NodeId strDisallow =
          demoNode("Demo.ValueEditorTest.Nulls.PairedOutcomes.StringDisallowNulls");
      assertTrue(writeValue(strAllow, Variant.NULL_VALUE).isGood());
      assertEquals(
          StatusCodes.Bad_TypeMismatch, writeValue(strDisallow, Variant.NULL_VALUE).getValue());
      assertTrue(writeValue(strDisallow, Variant.ofString("")).isGood());

      NodeId bsAllow = demoNode("Demo.ValueEditorTest.Nulls.PairedOutcomes.ByteStringAllowNulls");
      NodeId bsDisallow =
          demoNode("Demo.ValueEditorTest.Nulls.PairedOutcomes.ByteStringDisallowNulls");
      assertTrue(writeValue(bsAllow, Variant.NULL_VALUE).isGood());
      assertTrue(writeValue(bsAllow, Variant.ofByteString(ByteString.NULL_VALUE)).isGood());
      assertEquals(
          StatusCodes.Bad_TypeMismatch,
          writeValue(bsDisallow, Variant.ofByteString(ByteString.NULL_VALUE)).getValue());
      assertTrue(writeValue(bsDisallow, Variant.ofByteString(ByteString.of(new byte[0]))).isGood());

      NodeId nullableFields =
          demoNode("Demo.ValueEditorTest.Nulls.PairedOutcomes.NullableFieldsStruct");
      // Root null is rejected, while a non-null structure with null fields is accepted.
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
      NodeId maxStr = demoNode("Demo.ValueEditorTest.Limits.MaxStringLengthString");
      assertTrue(writeValue(maxStr, Variant.ofString("1234567890123456")).isGood());
      assertEquals(
          StatusCodes.Bad_OutOfRange,
          writeValue(maxStr, Variant.ofString("12345678901234567")).getValue());

      // "cafééééé" = 8 Unicode code points, 13 UTF-8 bytes -> accepted by MaxCharacters=8.
      NodeId maxChars = demoNode("Demo.ValueEditorTest.Limits.MaxCharactersString");
      assertTrue(
          writeValue(maxChars, Variant.ofString("caf\u00E9\u00E9\u00E9\u00E9\u00E9")).isGood());
      assertEquals(
          StatusCodes.Bad_OutOfRange,
          writeValue(maxChars, Variant.ofString("123456789")).getValue());

      NodeId maxBs = demoNode("Demo.ValueEditorTest.Limits.MaxByteStringLengthValue");
      assertTrue(writeValue(maxBs, Variant.ofByteString(ByteString.of(new byte[8]))).isGood());
      assertEquals(
          StatusCodes.Bad_OutOfRange,
          writeValue(maxBs, Variant.ofByteString(ByteString.of(new byte[9]))).getValue());

      NodeId maxArr = demoNode("Demo.ValueEditorTest.Limits.MaxArrayLengthInt32");
      assertTrue(writeValue(maxArr, Variant.ofInt32Array(new Integer[] {1, 2, 3, 4, 5})).isGood());
      assertEquals(
          StatusCodes.Bad_OutOfRange,
          writeValue(maxArr, Variant.ofInt32Array(new Integer[] {1, 2, 3, 4, 5, 6})).getValue());

      NodeId bounded1D = demoNode("Demo.ValueEditorTest.Limits.BoundedOneDimArray");
      assertTrue(writeValue(bounded1D, Variant.ofInt32Array(new Integer[] {1, 2})).isGood());
      assertEquals(
          StatusCodes.Bad_TypeMismatch,
          writeValue(bounded1D, Variant.ofInt32Array(new Integer[] {1, 2, 3, 4, 5, 6})).getValue());

      NodeId bounded2D = demoNode("Demo.ValueEditorTest.Limits.BoundedTwoDimMatrix");
      Matrix tooWide = Matrix.ofDouble(new Double[][] {{1.0, 2.0, 3.0, 4.0}, {5.0, 6.0, 7.0, 8.0}});
      assertEquals(
          StatusCodes.Bad_TypeMismatch,
          writeValue(bounded2D, Variant.ofMatrix(tooWide)).getValue());

      NodeId fixedSize = demoNode("Demo.ValueEditorTest.Limits.FixedSizeInt32Array");
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
  class AccessAndRestrictions {

    @Test
    void writeFullArrayOnlyRejectsIndexRangeAndAcceptsFullWrite() throws Exception {
      NodeId nodeId =
          demoNode("Demo.ValueEditorTest.AccessAndRestrictions.WriteFullArrayOnlyInt32Array");

      // Index-range write must be refused with Bad_WriteNotSupported.
      StatusCode rangeStatus =
          writeValueWithIndexRange(nodeId, "0:1", Variant.ofInt32Array(new Integer[] {100, 200}));
      assertEquals(StatusCodes.Bad_WriteNotSupported, rangeStatus.getValue());

      // Full-array write succeeds.
      StatusCode fullStatus =
          writeValue(nodeId, Variant.ofInt32Array(new Integer[] {50, 60, 70, 80}));
      assertTrue(fullStatus.isGood());
    }

    @Test
    void noSubDataTypesRejectsDerivedRecordWhileSubtypableRecordAcceptsIt() throws Exception {
      NodeId noSubId = demoNode("Demo.ValueEditorTest.AccessAndRestrictions.NoSubDataTypesRecord");
      NodeId subtypableId = demoNode("Demo.ValueEditorTest.AccessAndRestrictions.SubtypableRecord");

      BaseRecord base = new BaseRecord(10, "exact");
      DerivedRecord derived = new DerivedRecord(11, "derived", 3);

      assertTrue(writeValue(noSubId, Variant.ofExtensionObject(encode(base))).isGood());
      assertEquals(
          StatusCodes.Bad_TypeMismatch,
          writeValue(noSubId, Variant.ofExtensionObject(encode(derived))).getValue());

      assertTrue(writeValue(subtypableId, Variant.ofExtensionObject(encode(derived))).isGood());
      assertTrue(writeValue(subtypableId, Variant.ofExtensionObject(encode(base))).isGood());
    }

    @Test
    void subtypeRestrictionBrowsesAndEnforcesAllowedSubtypes() throws Exception {
      NodeId restrictedId = demoNode("Demo.ValueEditorTest.AccessAndRestrictions.RestrictedNumber");
      Set<NodeId> restrictionTargets =
          browseForwardTargets(restrictedId, NodeIds.UsesSubtypeRestriction);
      assertEquals(
          Set.of(demoNode("Demo.ValueEditorTest.AccessAndRestrictions.NumberRestriction")),
          restrictionTargets);

      assertTrue(writeValue(restrictedId, Variant.ofInt32(100)).isGood());
      assertTrue(writeValue(restrictedId, Variant.ofDouble(12.5)).isGood());
      assertEquals(
          StatusCodes.Bad_TypeMismatch, writeValue(restrictedId, Variant.ofFloat(1.5f)).getValue());
    }

    @Test
    void dataTypeRefinementBrowsesAndEnforcesFieldConstraints() throws Exception {
      NodeId refinedId = demoNode("Demo.ValueEditorTest.AccessAndRestrictions.RefinedStruct");
      Set<NodeId> refinementTargets =
          browseForwardTargets(refinedId, NodeIds.UsesDataTypeRefinement);
      assertEquals(
          Set.of(demoNode("Demo.ValueEditorTest.AccessAndRestrictions.RefinableStructProfile")),
          refinementTargets);

      RefinableStruct valid =
          new RefinableStruct("Sensor-2", "Within 32 bytes", null, Variant.ofDouble(36.6));
      assertTrue(writeValue(refinedId, Variant.ofExtensionObject(encode(valid))).isGood());

      // Omitting promoted mandatory OptionalNote is rejected.
      RefinableStruct missingNote =
          new RefinableStruct("Sensor-2", null, null, Variant.ofDouble(36.6));
      assertEquals(
          StatusCodes.Bad_OutOfRange,
          writeValue(refinedId, Variant.ofExtensionObject(encode(missingNote))).getValue());

      // Populating disabled DisabledFlag is rejected.
      RefinableStruct withDisabled =
          new RefinableStruct("Sensor-2", "Note", false, Variant.ofDouble(36.6));
      assertEquals(
          StatusCodes.Bad_OutOfRange,
          writeValue(refinedId, Variant.ofExtensionObject(encode(withDisabled))).getValue());

      // Non-Double Reading is rejected.
      RefinableStruct nonDoubleReading =
          new RefinableStruct("Sensor-2", "Note", null, Variant.ofInt32(36));
      assertEquals(
          StatusCodes.Bad_TypeMismatch,
          writeValue(refinedId, Variant.ofExtensionObject(encode(nonDoubleReading))).getValue());
    }

    @Test
    void writeOnlyVariablesRefuseValueReadExposeMetadataAndAcceptTypedWrites() throws Exception {
      NodeId writeOnlyId = demoNode("Demo.ValueEditorTest.AccessAndRestrictions.WriteOnlyInt32");
      DataValue valueRead = client.readValue(0.0, TimestampsToReturn.Neither, writeOnlyId);
      assertEquals(StatusCodes.Bad_NotReadable, valueRead.statusCode().getValue());
      assertEquals(NodeIds.Int32, readAttribute(writeOnlyId, AttributeId.DataType).value().value());
      assertEquals(-1, readAttribute(writeOnlyId, AttributeId.ValueRank).value().value());
      assertTrue(writeValue(writeOnlyId, Variant.ofInt32(77)).isGood());

      NodeId writeOnlyUserId =
          demoNode("Demo.ValueEditorTest.AccessAndRestrictions.WriteOnlyUserInt32");
      DataValue userRead = client.readValue(0.0, TimestampsToReturn.Neither, writeOnlyUserId);
      assertEquals(StatusCodes.Bad_UserAccessDenied, userRead.statusCode().getValue());
      assertEquals(
          NodeIds.Int32, readAttribute(writeOnlyUserId, AttributeId.DataType).value().value());
      assertTrue(writeValue(writeOnlyUserId, Variant.ofInt32(88)).isGood());
    }
  }

  @Nested
  class Scale {

    @Test
    void largeInt32ArrayReadsHundredThousandElements() throws Exception {
      NodeId largeArrayId = demoNode("Demo.ValueEditorTest.Scale.LargeInt32Array");
      DataValue dataValue = client.readValue(0.0, TimestampsToReturn.Neither, largeArrayId);
      assertTrue(dataValue.statusCode().isGood());

      Integer[] values = assertInstanceOf(Integer[].class, dataValue.value().value());
      assertEquals(ValueEditorTestNodesFragment.LARGE_ARRAY_LENGTH, values.length);
      assertEquals(0, values[0]);
      assertEquals(ValueEditorTestNodesFragment.LARGE_ARRAY_LENGTH - 1, values[values.length - 1]);
    }

    @Test
    void deeplyNestedStructDecodesTwentyFiveLevels() throws Exception {
      NodeId nestedId = demoNode("Demo.ValueEditorTest.Scale.DeeplyNestedStruct");
      DataValue dataValue = client.readValue(0.0, TimestampsToReturn.Neither, nestedId);
      assertTrue(dataValue.statusCode().isGood());

      NestedNode root =
          (NestedNode) ((ExtensionObject) dataValue.value().value()).decode(encodingContext);
      int count = 0;
      NestedNode cursor = root;
      while (cursor != null) {
        count++;
        assertEquals(count, cursor.getDepth());
        cursor = cursor.getChild();
      }
      assertEquals(ValueEditorTestNodesFragment.NESTED_CHAIN_DEPTH, count);
    }
  }

  @Nested
  class Methods {

    @Test
    void echoRecordSumArrayAndGetRecordsSucceedOverNoneAnonymous() throws Exception {
      NodeId methodsFolder = demoNode("Demo.ValueEditorTest.Methods");

      // EchoRecord
      DemoRecord inputRecord = new DemoRecord(42, "Alpha", 19.75, true);
      CallMethodResult echoResult =
          callMethod(
              methodsFolder,
              demoNode("Demo.ValueEditorTest.Methods.EchoRecord"),
              Variant.ofExtensionObject(encode(inputRecord)));
      assertTrue(echoResult.getStatusCode().isGood());
      DemoRecord echoed =
          (DemoRecord)
              ((ExtensionObject) echoResult.getOutputArguments()[0].value())
                  .decode(encodingContext);
      assertEquals(inputRecord, echoed);

      // SumInt32Array
      CallMethodResult sumResult =
          callMethod(
              methodsFolder,
              demoNode("Demo.ValueEditorTest.Methods.SumInt32Array"),
              Variant.ofInt32Array(new Integer[] {10, 20, -5, 15}));
      assertTrue(sumResult.getStatusCode().isGood());
      assertEquals(40L, sumResult.getOutputArguments()[0].value());
      assertEquals(4, sumResult.getOutputArguments()[1].value());

      // GetRecords
      CallMethodResult getResult =
          callMethod(
              methodsFolder,
              demoNode("Demo.ValueEditorTest.Methods.GetRecords"),
              Variant.ofInt32(3));
      assertTrue(getResult.getStatusCode().isGood());
      DemoRecord primary =
          (DemoRecord)
              ((ExtensionObject) getResult.getOutputArguments()[0].value()).decode(encodingContext);
      assertEquals("Primary", primary.getName());
      ExtensionObject[] records = (ExtensionObject[]) getResult.getOutputArguments()[1].value();
      assertEquals(3, records.length);
      assertEquals("Record-3", ((DemoRecord) records[2].decode(encodingContext)).getName());
    }

    @Test
    void formatMessageSupportsOptionalTrailingInputsAndDescriptionReferences() throws Exception {
      NodeId methodsFolder = demoNode("Demo.ValueEditorTest.Methods");
      NodeId formatMethod = demoNode("Demo.ValueEditorTest.Methods.FormatMessage");

      Set<NodeId> optionalDescs =
          browseForwardTargets(formatMethod, NodeIds.HasOptionalInputArgumentDescription);
      assertEquals(
          Set.of(
              demoNode("Demo.ValueEditorTest.Methods.FormatMessage.repeatCount"),
              demoNode("Demo.ValueEditorTest.Methods.FormatMessage.uppercase")),
          optionalDescs);

      // 0 arguments -> Bad_ArgumentsMissing
      CallMethodResult missing = callMethod(methodsFolder, formatMethod);
      assertEquals(StatusCodes.Bad_ArgumentsMissing, missing.getStatusCode().getValue());

      // 1 argument -> uses defaults repeatCount=1, uppercase=false
      CallMethodResult oneArg = callMethod(methodsFolder, formatMethod, Variant.ofString("hello"));
      assertTrue(oneArg.getStatusCode().isGood());
      assertEquals("hello", oneArg.getOutputArguments()[0].value());

      // 2 arguments -> uses default uppercase=false
      CallMethodResult twoArgs =
          callMethod(methodsFolder, formatMethod, Variant.ofString("hi"), Variant.ofInt32(3));
      assertTrue(twoArgs.getStatusCode().isGood());
      assertEquals("hi hi hi", twoArgs.getOutputArguments()[0].value());

      // 3 arguments -> all supplied
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
      NodeId methodsFolder = demoNode("Demo.ValueEditorTest.Methods");

      CallMethodResult abstractResult =
          callMethod(
              methodsFolder,
              demoNode("Demo.ValueEditorTest.Methods.InspectAbstract"),
              Variant.ofDouble(2.5),
              Variant.ofExtensionObject(encode(new DemoRecord(1, "Pay", 3.0, false))));
      assertTrue(abstractResult.getStatusCode().isGood());
      assertEquals("Double", abstractResult.getOutputArguments()[0].value());
      assertTrue(((String) abstractResult.getOutputArguments()[1].value()).contains("DemoRecord"));
      assertEquals(2.5, abstractResult.getOutputArguments()[2].value());

      NodeId describeRank = demoNode("Demo.ValueEditorTest.Methods.DescribeRank");

      // Scalar + scalar
      CallMethodResult scalarCall =
          callMethod(methodsFolder, describeRank, Variant.ofString("abc"), Variant.ofInt32(5));
      assertTrue(scalarCall.getStatusCode().isGood());
      assertEquals("scalar:String", scalarCall.getOutputArguments()[0].value());
      assertEquals(1, scalarCall.getOutputArguments()[1].value());

      // Matrix + 1-D array
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

      NodeId methodsFolder = demoNode("Demo.ValueEditorTest.Methods");
      NodeId configureMethod = demoNode("Demo.ValueEditorTest.Methods.ConfigureController");

      Set<NodeId> descriptions =
          browseForwardTargets(configureMethod, NodeIds.HasArgumentDescription);
      assertEquals(
          Set.of(
              demoNode("Demo.ValueEditorTest.Methods.ConfigureController.setpoint"),
              demoNode("Demo.ValueEditorTest.Methods.ConfigureController.retries"),
              demoNode("Demo.ValueEditorTest.Methods.ConfigureController.enabled"),
              demoNode("Demo.ValueEditorTest.Methods.ConfigureController.mode")),
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

      // ValidateInputs: arg 0 invalid (<= 0), arg 1 valid, arg 2 invalid (> 100)
      NodeId validateMethod = demoNode("Demo.ValueEditorTest.Methods.ValidateInputs");
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

      // Valid inputs succeed
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
  }

  @Nested
  class DynamicClientInteroperability {

    @Test
    void customStructuresDecodeDynamicallyFromDataTypeDefinition() throws Exception {
      // Simulates a generic OPC UA client (such as Nova, Prosys, or UaExpert) that has no
      // compiled codecs for the demo server's custom types and relies solely on DataTypeDefinition
      // and HasEncoding references.
      EncodingContext dynamicContext = client.getDynamicEncodingContext();

      String[] structuredVariables = {
        "Demo.ValueEditorTest.OptionSets.OptionSetScalar",
        "Demo.ValueEditorTest.OptionSets.StructWithOptionSet",
        "Demo.ValueEditorTest.Nulls.WritableNulls.UnionNull",
        "Demo.ValueEditorTest.Nulls.PairedOutcomes.NullableFieldsStruct",
        "Demo.ValueEditorTest.AccessAndRestrictions.NoSubDataTypesRecord",
        "Demo.ValueEditorTest.AccessAndRestrictions.SubtypableRecord",
        "Demo.ValueEditorTest.AccessAndRestrictions.RefinedStruct",
        "Demo.ValueEditorTest.Scale.DeeplyNestedStruct"
      };

      for (String identifier : structuredVariables) {
        DataValue dv = client.readValue(0.0, TimestampsToReturn.Neither, demoNode(identifier));
        assertTrue(dv.statusCode().isGood(), () -> identifier + " read failed: " + dv.statusCode());
        ExtensionObject xo = assertInstanceOf(ExtensionObject.class, dv.value().value());
        Object decoded = xo.decode(dynamicContext);
        assertNotNull(decoded, () -> identifier + " should decode dynamically");
      }

      // Also verify DemoRecord returned by GetRecords decodes dynamically.
      CallMethodResult records =
          callMethod(
              demoNode("Demo.ValueEditorTest.Methods"),
              demoNode("Demo.ValueEditorTest.Methods.GetRecords"),
              Variant.ofInt32(2));
      assertTrue(records.getStatusCode().isGood());
      ExtensionObject primaryXo =
          assertInstanceOf(ExtensionObject.class, records.getOutputArguments()[0].value());
      assertNotNull(primaryXo.decode(dynamicContext));
    }
  }

  // region Helpers

  private NodeId demoNode(String identifier) {
    return new NodeId(demoNsIndex, identifier);
  }

  private ExtensionObject encode(org.eclipse.milo.opcua.stack.core.types.UaStructuredType struct) {
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
      NodeId nodeId, @org.jspecify.annotations.Nullable String indexRange, Variant value)
      throws Exception {

    WriteValue writeValue =
        new WriteValue(nodeId, AttributeId.Value.uid(), indexRange, DataValue.valueOnly(value));
    WriteResponse response = client.write(List.of(writeValue));
    return response.getResults()[0];
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
            false,
            uint(0),
            uint(BrowseResultMask.All.getValue()));

    BrowseResult result = client.browse(bd);
    assertTrue(result.getStatusCode().isGood());

    ReferenceDescription[] refs = result.getReferences();
    if (refs == null) {
      return Set.of();
    }

    NamespaceTable nsTable = server.getServer().getNamespaceTable();
    return java.util.Arrays.stream(refs)
        .map(r -> r.getNodeId().toNodeId(nsTable).orElseThrow())
        .collect(Collectors.toSet());
  }

  // endregion
}

package com.digitalpetri.opcua.server.namespace.demo;

import static org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.Unsigned.uint;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.digitalpetri.opcua.server.namespace.demo.types.DecimalEncoding;
import com.digitalpetri.opcua.server.namespace.demo.types.DemoOptionSet;
import com.digitalpetri.opcua.server.namespace.demo.types.NestedNode;
import com.digitalpetri.opcua.server.namespace.demo.types.RefinableStruct;
import com.digitalpetri.opcua.test.types.UnionOfArray;
import com.digitalpetri.opcua.test.types.UnionOfMatrix;
import com.digitalpetri.opcua.test.types.UnionOfScalar;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.Set;
import java.util.UUID;
import org.eclipse.milo.opcua.sdk.core.typetree.DataType;
import org.eclipse.milo.opcua.sdk.core.typetree.DataTypeTree;
import org.eclipse.milo.opcua.stack.core.NodeIds;
import org.eclipse.milo.opcua.stack.core.OpcUaDataType;
import org.eclipse.milo.opcua.stack.core.StatusCodes;
import org.eclipse.milo.opcua.stack.core.UaException;
import org.eclipse.milo.opcua.stack.core.UaSerializationException;
import org.eclipse.milo.opcua.stack.core.encoding.DefaultEncodingContext;
import org.eclipse.milo.opcua.stack.core.encoding.EncodingContext;
import org.eclipse.milo.opcua.stack.core.encoding.binary.OpcUaBinaryDecoder;
import org.eclipse.milo.opcua.stack.core.encoding.binary.OpcUaBinaryEncoder;
import org.eclipse.milo.opcua.stack.core.types.builtin.ByteString;
import org.eclipse.milo.opcua.stack.core.types.builtin.DateTime;
import org.eclipse.milo.opcua.stack.core.types.builtin.ExpandedNodeId;
import org.eclipse.milo.opcua.stack.core.types.builtin.ExtensionObject;
import org.eclipse.milo.opcua.stack.core.types.builtin.LocalizedText;
import org.eclipse.milo.opcua.stack.core.types.builtin.Matrix;
import org.eclipse.milo.opcua.stack.core.types.builtin.NodeId;
import org.eclipse.milo.opcua.stack.core.types.builtin.QualifiedName;
import org.eclipse.milo.opcua.stack.core.types.builtin.Variant;
import org.eclipse.milo.opcua.stack.core.types.builtin.XmlElement;
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.UInteger;
import org.eclipse.milo.opcua.stack.core.types.structured.AnonymousIdentityToken;
import org.eclipse.milo.opcua.stack.core.types.structured.DataTypeDefinition;
import org.eclipse.milo.opcua.stack.core.types.structured.Range;
import org.eclipse.milo.opcua.stack.core.types.structured.UserNameIdentityToken;
import org.eclipse.milo.opcua.stack.core.types.structured.XVType;
import org.eclipse.milo.opcua.stack.core.util.Tree;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link DemoOptionSet} Part 3 §8.40 write merging, {@link DecimalEncoding} Part 6
 * §5.1.10 binary encoding, and {@link DemoValueFilters} validation rules.
 */
class DemoValueFiltersTest {

  @Nested
  class OptionSetMerge {

    // Part 3 §8.40: bits whose ValidBits bit is 0 must retain their existing server value.
    @Test
    void partialValidBitsUpdatePreservesUntouchedBits() throws UaException {
      DemoOptionSet current = DemoOptionSet.of(0x03, 0x01); // Read | Write | Audit
      DemoOptionSet written =
          new DemoOptionSet(
              ByteString.of(new byte[] {0x04, 0x02}), // Execute | Diagnostics
              ByteString.of(new byte[] {0x05, 0x02})); // update Read(0), Execute(1), Diagnostics(1)

      DemoOptionSet merged = DemoOptionSet.mergeWrite(current, written);

      assertArrayEquals(new byte[] {0x06, 0x03}, merged.getValue().bytesOrEmpty());
      assertArrayEquals(DemoOptionSet.DEFINED_VALID_BITS, merged.getValidBits().bytesOrEmpty());
    }

    // Part 3 §8.40 requires Value and ValidBits to have identical byte lengths.
    @Test
    void mismatchedValueAndValidBitsLengthThrowsBadOutOfRange() {
      DemoOptionSet current = DemoOptionSet.of(0x01, 0x00);
      DemoOptionSet written =
          new DemoOptionSet(
              ByteString.of(new byte[] {0x01, 0x00}), ByteString.of(new byte[] {0x01}));

      UaException ex =
          assertThrows(UaException.class, () -> DemoOptionSet.mergeWrite(current, written));
      assertEquals(StatusCodes.Bad_OutOfRange, ex.getStatusCode().getValue());
    }

    // DemoOptionSet declares OptionSetLength = 2 bytes.
    @Test
    void wrongByteLengthThrowsBadOutOfRange() {
      DemoOptionSet current = DemoOptionSet.of(0x01, 0x00);
      DemoOptionSet written =
          new DemoOptionSet(ByteString.of(new byte[] {0x01}), ByteString.of(new byte[] {0x01}));

      UaException ex =
          assertThrows(UaException.class, () -> DemoOptionSet.mergeWrite(current, written));
      assertEquals(StatusCodes.Bad_OutOfRange, ex.getStatusCode().getValue());
    }

    // Part 3 §8.40: setting a ValidBits bit for an undefined gap bit (bit 4 = 0x10) must fail
    // the write with Bad_OutOfRange.
    @Test
    void undefinedBitInValidBitsThrowsBadOutOfRange() {
      DemoOptionSet current = DemoOptionSet.of(0x01, 0x00);
      DemoOptionSet written =
          new DemoOptionSet(
              ByteString.of(new byte[] {0x11, 0x00}), ByteString.of(new byte[] {0x11, 0x00}));

      UaException ex =
          assertThrows(UaException.class, () -> DemoOptionSet.mergeWrite(current, written));
      assertEquals(StatusCodes.Bad_OutOfRange, ex.getStatusCode().getValue());
    }

    // Matrix width changes must map each incoming coordinate to the same coordinate in the
    // previous matrix rather than reusing flat indices across differing row strides.
    @Test
    void matrixCoordinateMappingPreservesCoordinatesAcrossWidthChanges() {
      int[] current2x2 = {2, 2};
      int[] incoming2x3 = {2, 3};

      assertEquals(0, DemoValueFilters.mapMatrixCoordinate(0, incoming2x3, current2x2)); // [0,0]
      assertEquals(1, DemoValueFilters.mapMatrixCoordinate(1, incoming2x3, current2x2)); // [0,1]
      assertEquals(-1, DemoValueFilters.mapMatrixCoordinate(2, incoming2x3, current2x2)); // [0,2]
      assertEquals(2, DemoValueFilters.mapMatrixCoordinate(3, incoming2x3, current2x2)); // [1,0]
      assertEquals(3, DemoValueFilters.mapMatrixCoordinate(4, incoming2x3, current2x2)); // [1,1]
      assertEquals(-1, DemoValueFilters.mapMatrixCoordinate(5, incoming2x3, current2x2)); // [1,2]

      // 3-D coordinate [1,1,1] in 2x3x2 (flat 9) maps to [1,1,1] in 2x2x2 (flat 7).
      assertEquals(
          7, DemoValueFilters.mapMatrixCoordinate(9, new int[] {2, 3, 2}, new int[] {2, 2, 2}));
    }
  }

  @Nested
  class DecimalBinaryEncoding {

    @Test
    void positiveNegativeZeroAndLargeDecimalsRoundTrip() throws UaException {
      BigDecimal[] cases = {
        new BigDecimal("123.45"),
        new BigDecimal("-98765432109876543210.123456"),
        BigDecimal.ZERO,
        new BigDecimal(new BigInteger("123456789012345678901234567890"), -5)
      };

      for (BigDecimal expected : cases) {
        ExtensionObject xo = DecimalEncoding.encode(expected);
        assertEquals(NodeIds.Decimal, xo.getEncodingOrTypeId());
        assertEquals(expected, DecimalEncoding.decode(xo));
      }
    }

    // Part 6 §5.1.10: 123.45 has Scale=2 (0x02, 0x00) and unscaled 12345 = 0x3039 -> LE 39 30.
    @Test
    void encodedBytesMatchPart6LittleEndianLayout() throws UaException {
      ExtensionObject.Binary xo =
          (ExtensionObject.Binary) DecimalEncoding.encode(new BigDecimal("123.45"));

      assertArrayEquals(new byte[] {0x02, 0x00, 0x39, 0x30}, xo.getBody().bytesOrEmpty());
      assertEquals(new BigDecimal("123.45"), DecimalEncoding.decode(xo));
    }

    // Part 6 §5.1.10: "If the Length is <= 2 then the Decimal is invalid."
    @Test
    void bodyOfTwoBytesOrFewerIsRejectedAsOutOfRange() {
      ExtensionObject twoBytes =
          ExtensionObject.of(ByteString.of(new byte[] {0x02, 0x00}), NodeIds.Decimal);
      UaException ex = assertThrows(UaException.class, () -> DecimalEncoding.decode(twoBytes));
      assertEquals(StatusCodes.Bad_OutOfRange, ex.getStatusCode().getValue());
    }

    @Test
    void wrongTypeIdIsRejectedAsTypeMismatch() {
      ExtensionObject wrongType =
          ExtensionObject.of(ByteString.of(new byte[] {0x02, 0x00, 0x01}), NodeIds.Structure);
      UaException ex = assertThrows(UaException.class, () -> DecimalEncoding.decode(wrongType));
      assertEquals(StatusCodes.Bad_TypeMismatch, ex.getStatusCode().getValue());
    }
  }

  @Nested
  class DisallowNullsValidation {

    @Test
    void typedNullSentinelsAreRejected() {
      Object[] nulls = {
        null,
        ByteString.NULL_VALUE,
        XmlElement.NULL_VALUE,
        DateTime.NULL_VALUE,
        new UUID(0L, 0L),
        NodeId.NULL_VALUE,
        ExpandedNodeId.NULL_VALUE,
        QualifiedName.NULL_VALUE,
        LocalizedText.NULL_VALUE,
        ExtensionObject.of(ByteString.NULL_VALUE, NodeId.NULL_VALUE),
        UnionOfScalar.ofNull(),
        UnionOfArray.ofNull(),
        UnionOfMatrix.ofNull()
      };

      for (Object value : nulls) {
        UaException ex =
            assertThrows(UaException.class, () -> DemoValueFilters.validateNotNull(value));
        assertEquals(StatusCodes.Bad_TypeMismatch, ex.getStatusCode().getValue());
      }
    }

    // OPC UA distinguishes empty values (length 0) from null values (length -1).
    @Test
    void emptyNonNullValuesAreAccepted() {
      assertDoesNotThrow(() -> DemoValueFilters.validateNotNull(""));
      assertDoesNotThrow(() -> DemoValueFilters.validateNotNull(ByteString.of(new byte[0])));
      assertDoesNotThrow(() -> DemoValueFilters.validateNotNull(XmlElement.of("")));
      assertDoesNotThrow(() -> DemoValueFilters.validateNotNull(new Integer[0]));
      assertDoesNotThrow(() -> DemoValueFilters.validateNotNull(UnionOfScalar.ofBoolean(false)));
    }
  }

  @Nested
  class ValueLimitsValidation {

    // Part 3 §5.6.2: MaxStringLength measures UTF-8 bytes; MaxCharacters measures Unicode code
    // points.
    @Test
    void maxStringLengthCountsUtf8BytesWhileMaxCharactersCountsCodePoints() throws UaException {
      // "cafééé" = 6 code points, 9 UTF-8 bytes (each 'é' is 2 UTF-8 bytes).
      String sixCodePointsNineBytes = "caf\u00E9\u00E9\u00E9";

      DemoValueFilters.validateValueLimits(
          sixCodePointsNineBytes, null, uint(6), null, null, null, null);

      UaException byteEx =
          assertThrows(
              UaException.class,
              () ->
                  DemoValueFilters.validateValueLimits(
                      sixCodePointsNineBytes, uint(8), null, null, null, null, null));
      assertEquals(StatusCodes.Bad_OutOfRange, byteEx.getStatusCode().getValue());

      UaException charEx =
          assertThrows(
              UaException.class,
              () ->
                  DemoValueFilters.validateValueLimits(
                      sixCodePointsNineBytes, uint(32), uint(5), null, null, null, null));
      assertEquals(StatusCodes.Bad_OutOfRange, charEx.getStatusCode().getValue());
    }

    @Test
    void maxByteStringLengthRejectsLongerPayload() throws UaException {
      DemoValueFilters.validateValueLimits(
          ByteString.of(new byte[8]), null, null, uint(8), null, null, null);

      UaException ex =
          assertThrows(
              UaException.class,
              () ->
                  DemoValueFilters.validateValueLimits(
                      ByteString.of(new byte[9]), null, null, uint(8), null, null, null));
      assertEquals(StatusCodes.Bad_OutOfRange, ex.getStatusCode().getValue());
    }

    // Part 3 §5.6.2: MaxArrayLength applies to the overall element count of a matrix.
    @Test
    void maxArrayLengthCountsTotalElementsAcrossMatrixDimensions() throws UaException {
      Matrix twoByThree = Matrix.ofInt32(new Integer[][] {{1, 2, 3}, {4, 5, 6}});
      DemoValueFilters.validateValueLimits(twoByThree, null, null, null, uint(6), null, null);

      Matrix twoByFour = Matrix.ofInt32(new Integer[][] {{1, 2, 3, 4}, {5, 6, 7, 8}});
      UaException ex =
          assertThrows(
              UaException.class,
              () ->
                  DemoValueFilters.validateValueLimits(
                      twoByFour, null, null, null, uint(6), null, null));
      assertEquals(StatusCodes.Bad_OutOfRange, ex.getStatusCode().getValue());
    }

    @Test
    void fixedSizeArrayRejectsBothShorterAndLongerArrays() throws UaException {
      UInteger[] dims = new UInteger[] {uint(4)};
      DemoValueFilters.validateValueLimits(
          new Integer[] {1, 2, 3, 4}, null, null, null, null, true, dims);

      UaException shorterEx =
          assertThrows(
              UaException.class,
              () ->
                  DemoValueFilters.validateValueLimits(
                      new Integer[] {1, 2}, null, null, null, null, true, dims));
      assertEquals(StatusCodes.Bad_OutOfRange, shorterEx.getStatusCode().getValue());

      UaException longerEx =
          assertThrows(
              UaException.class,
              () ->
                  DemoValueFilters.validateValueLimits(
                      new Integer[] {1, 2, 3, 4, 5}, null, null, null, null, true, dims));
      assertEquals(StatusCodes.Bad_OutOfRange, longerEx.getStatusCode().getValue());
    }
  }

  @Nested
  class EnumAndRefinementValidation {

    @Test
    void enumValuesCheckRejectsGapAndRemovedSubtypeValuesAcrossArities() throws UaException {
      Set<Integer> allowed = Set.of(1, 2, 4);
      DemoValueFilters.validateEnumValues(2, allowed);
      DemoValueFilters.validateEnumValues(new Integer[] {1, 4}, allowed);
      DemoValueFilters.validateEnumValues(
          Matrix.ofInt32(new Integer[][] {{1, 2}, {4, 1}}), allowed);

      UaException gapEx =
          assertThrows(UaException.class, () -> DemoValueFilters.validateEnumValues(3, allowed));
      assertEquals(StatusCodes.Bad_OutOfRange, gapEx.getStatusCode().getValue());

      UaException removedEx =
          assertThrows(
              UaException.class,
              () ->
                  DemoValueFilters.validateEnumValues(
                      Matrix.ofInt32(new Integer[][] {{1, 8}, {2, 4}}), allowed));
      assertEquals(StatusCodes.Bad_OutOfRange, removedEx.getStatusCode().getValue());
    }

    @Test
    void refinedStructValidationEnforcesMandatoryDisabledAndReadingRules() throws UaException {
      RefinableStruct valid = new RefinableStruct("S1", "Note", null, Variant.ofDouble(21.5));
      DemoValueFilters.validateRefinedStruct(valid, 32);

      UaException missingNote =
          assertThrows(
              UaException.class,
              () ->
                  DemoValueFilters.validateRefinedStruct(
                      new RefinableStruct("S1", null, null, Variant.ofDouble(21.5)), 32));
      assertEquals(StatusCodes.Bad_OutOfRange, missingNote.getStatusCode().getValue());

      UaException disabledFlag =
          assertThrows(
              UaException.class,
              () ->
                  DemoValueFilters.validateRefinedStruct(
                      new RefinableStruct("S1", "Note", true, Variant.ofDouble(21.5)), 32));
      assertEquals(StatusCodes.Bad_OutOfRange, disabledFlag.getStatusCode().getValue());

      UaException wrongReading =
          assertThrows(
              UaException.class,
              () ->
                  DemoValueFilters.validateRefinedStruct(
                      new RefinableStruct("S1", "Note", null, Variant.ofInt32(21)), 32));
      assertEquals(StatusCodes.Bad_TypeMismatch, wrongReading.getStatusCode().getValue());
    }

    // Part 6 §5.2.7: optional-field codecs must reject nonzero unassigned EncodingMask bits.
    @Test
    void optionalFieldCodecsRejectUnassignedEncodingMaskBits() {
      EncodingContext context = new DefaultEncodingContext();
      NestedNode.Codec nestedCodec = new NestedNode.Codec();
      RefinableStruct.Codec refinableCodec = new RefinableStruct.Codec();

      // NestedNode only assigns bit 0 (Child); mask 0 decodes cleanly, mask 2 must fail.
      ByteBuf validNestedBuf = Unpooled.buffer();
      try {
        OpcUaBinaryEncoder encoder = new OpcUaBinaryEncoder(context).setBuffer(validNestedBuf);
        nestedCodec.encodeType(context, encoder, new NestedNode(1, "Leaf", null));
        NestedNode decoded =
            nestedCodec.decodeType(
                context, new OpcUaBinaryDecoder(context).setBuffer(validNestedBuf));
        assertEquals(new NestedNode(1, "Leaf", null), decoded);
      } finally {
        validNestedBuf.release();
      }

      ByteBuf badNestedBuf = Unpooled.buffer();
      try {
        OpcUaBinaryEncoder encoder = new OpcUaBinaryEncoder(context).setBuffer(badNestedBuf);
        encoder.encodeUInt32("EncodingMask", uint(2));
        encoder.encodeInt32("Depth", 1);
        encoder.encodeString("Label", "Bad");
        UaSerializationException ex =
            assertThrows(
                UaSerializationException.class,
                () ->
                    nestedCodec.decodeType(
                        context, new OpcUaBinaryDecoder(context).setBuffer(badNestedBuf)));
        assertEquals(StatusCodes.Bad_DecodingError, ex.getStatusCode().getValue());
      } finally {
        badNestedBuf.release();
      }

      // RefinableStruct only assigns bits 0..1; mask 3 decodes cleanly, mask 4 must fail.
      ByteBuf validRefinableBuf = Unpooled.buffer();
      try {
        RefinableStruct sample = new RefinableStruct("S1", "Note", true, Variant.ofDouble(12.5));
        OpcUaBinaryEncoder encoder = new OpcUaBinaryEncoder(context).setBuffer(validRefinableBuf);
        refinableCodec.encodeType(context, encoder, sample);
        RefinableStruct decoded =
            refinableCodec.decodeType(
                context, new OpcUaBinaryDecoder(context).setBuffer(validRefinableBuf));
        assertEquals(sample, decoded);
      } finally {
        validRefinableBuf.release();
      }

      ByteBuf badRefinableBuf = Unpooled.buffer();
      try {
        OpcUaBinaryEncoder encoder = new OpcUaBinaryEncoder(context).setBuffer(badRefinableBuf);
        encoder.encodeUInt32("EncodingMask", uint(4));
        encoder.encodeString("Label", "Bad");
        encoder.encodeVariant("Reading", Variant.ofDouble(1.0));
        UaSerializationException ex =
            assertThrows(
                UaSerializationException.class,
                () ->
                    refinableCodec.decodeType(
                        context, new OpcUaBinaryDecoder(context).setBuffer(badRefinableBuf)));
        assertEquals(StatusCodes.Bad_DecodingError, ex.getStatusCode().getValue());
      } finally {
        badRefinableBuf.release();
      }
    }

    @Test
    void xyArrayEuRangeValidationEnforcesXvTypeElementsAndBounds() throws UaException {
      EncodingContext context = new DefaultEncodingContext();
      NodeId xvEncodingId =
          XVType.BINARY_ENCODING_ID.toNodeId(context.getNamespaceTable()).orElseThrow();
      Range euRange = new Range(0.0, 100.0);

      DemoValueFilters.validateXyArrayEuRange(
          new XVType[] {new XVType(1.0, 0.0f), new XVType(2.0, 100.0f)},
          euRange,
          context,
          xvEncodingId);

      UaException outOfRange =
          assertThrows(
              UaException.class,
              () ->
                  DemoValueFilters.validateXyArrayEuRange(
                      new XVType[] {new XVType(1.0, 100.5f)}, euRange, context, xvEncodingId));
      assertEquals(StatusCodes.Bad_OutOfRange, outOfRange.getStatusCode().getValue());

      UaException nanValue =
          assertThrows(
              UaException.class,
              () ->
                  DemoValueFilters.validateXyArrayEuRange(
                      new XVType[] {new XVType(1.0, Float.NaN)}, euRange, context, xvEncodingId));
      assertEquals(StatusCodes.Bad_OutOfRange, nanValue.getStatusCode().getValue());

      UaException wrongPayload =
          assertThrows(
              UaException.class,
              () ->
                  DemoValueFilters.validateXyArrayEuRange(
                      new Double[] {1.0, 10.0}, euRange, context, xvEncodingId));
      assertEquals(StatusCodes.Bad_TypeMismatch, wrongPayload.getStatusCode().getValue());
    }
  }

  @Nested
  class StructureTypeValidation {

    private final EncodingContext context = new DefaultEncodingContext();

    // Structure -> UserIdentityToken -> {AnonymousIdentityToken, UserNameIdentityToken}; Range is
    // an unrelated Structure subtype.
    private final DataTypeTree dataTypeTree = identityTokenTree();

    private final ExtensionObject anonymous =
        ExtensionObject.encode(context, new AnonymousIdentityToken("anonymous"));
    private final ExtensionObject userName =
        ExtensionObject.encode(
            context, new UserNameIdentityToken("user", "user", ByteString.of(new byte[0]), null));
    private final ExtensionObject range = ExtensionObject.encode(context, new Range(0.0, 1.0));

    @Test
    void declaredTypeAndSubtypesAreAcceptedAcrossArities() {
      assertDoesNotThrow(() -> validate(anonymous, NodeIds.AnonymousIdentityToken));
      assertDoesNotThrow(() -> validate(anonymous, NodeIds.UserIdentityToken));
      assertDoesNotThrow(
          () -> validate(new ExtensionObject[] {anonymous, userName}, NodeIds.UserIdentityToken));
      assertDoesNotThrow(() ->
          validate(
              Matrix.ofExtensionObject(
                  new ExtensionObject[][] {{anonymous, userName}, {userName, anonymous}}),
              NodeIds.UserIdentityToken));

      // Values written in-process may already be decoded.
      assertDoesNotThrow(
          () -> validate(new AnonymousIdentityToken("decoded"), NodeIds.UserIdentityToken));
    }

    @Test
    void unrelatedSupertypeAndSiblingEncodingsAreRejectedAcrossArities() {
      assertTypeMismatch(range, NodeIds.UserIdentityToken);
      assertTypeMismatch(new ExtensionObject[] {anonymous, range}, NodeIds.UserIdentityToken);
      assertTypeMismatch(
          Matrix.ofExtensionObject(
              new ExtensionObject[][] {{anonymous, anonymous}, {range, range}}),
          NodeIds.UserIdentityToken);

      // A sibling subtype does not conform, and neither does the declared type's supertype.
      assertTypeMismatch(userName, NodeIds.AnonymousIdentityToken);
      assertTypeMismatch(anonymous, NodeIds.Range);
    }

    @Test
    void malformedNullAndNonStructureElementsAreRejected() {
      ExtensionObject.Binary rangeBinary = (ExtensionObject.Binary) range;
      NodeId rangeEncodingId = rangeBinary.getEncodingOrTypeId();
      byte[] truncatedBody = Arrays.copyOf(rangeBinary.getBody().bytesOrEmpty(), 3);

      assertTypeMismatch(
          ExtensionObject.of(ByteString.of(truncatedBody), rangeEncodingId), NodeIds.Range);
      assertTypeMismatch(ExtensionObject.of(ByteString.NULL_VALUE, rangeEncodingId), NodeIds.Range);
      assertTypeMismatch(
          ExtensionObject.of(ByteString.of(new byte[] {1, 2, 3, 4}), new NodeId(0, 999_999)),
          NodeIds.Range);
      assertTypeMismatch(new ExtensionObject[] {range, null}, NodeIds.Range);
      assertTypeMismatch(1.5, NodeIds.Range);
    }

    // Nulls are governed by AllowNulls; empty arrays and matrices have no elements to check.
    @Test
    void rootNullAndEmptyArraysAndMatricesAreAccepted() {
      assertDoesNotThrow(() -> validate(null, NodeIds.Range));
      assertDoesNotThrow(() -> validate(new ExtensionObject[0], NodeIds.Range));
      assertDoesNotThrow(() ->
          validate(
              new Matrix(new ExtensionObject[0], new int[] {0, 0}, OpcUaDataType.ExtensionObject),
              NodeIds.Range));
    }

    private void validate(@Nullable Object value, NodeId declaredDataTypeId) throws UaException {
      DemoValueFilters.validateStructureType(value, context, dataTypeTree, declaredDataTypeId);
    }

    private void assertTypeMismatch(Object value, NodeId declaredDataTypeId) {
      UaException ex = assertThrows(UaException.class, () -> validate(value, declaredDataTypeId));
      assertEquals(StatusCodes.Bad_TypeMismatch, ex.getStatusCode().getValue());
    }

    private static DataTypeTree identityTokenTree() {
      Tree<DataType> structure = new Tree<>(null, new TestDataType(NodeIds.Structure, null));
      Tree<DataType> identityToken =
          structure.addChild(new TestDataType(NodeIds.UserIdentityToken, null));
      identityToken.addChild(
          new TestDataType(
              NodeIds.AnonymousIdentityToken, ns0(AnonymousIdentityToken.BINARY_ENCODING_ID)));
      identityToken.addChild(
          new TestDataType(
              NodeIds.UserNameIdentityToken, ns0(UserNameIdentityToken.BINARY_ENCODING_ID)));
      structure.addChild(new TestDataType(NodeIds.Range, ns0(Range.BINARY_ENCODING_ID)));

      return new DataTypeTree(structure);
    }

    private static NodeId ns0(ExpandedNodeId nodeId) {
      return nodeId.toNodeId(new DefaultEncodingContext().getNamespaceTable()).orElseThrow();
    }

    private record TestDataType(NodeId nodeId, @Nullable NodeId binaryEncodingId)
        implements DataType {

      @Override
      public QualifiedName getBrowseName() {
        return new QualifiedName(0, nodeId.toParseableString());
      }

      @Override
      public NodeId getNodeId() {
        return nodeId;
      }

      @Override
      public @Nullable NodeId getBinaryEncodingId() {
        return binaryEncodingId;
      }

      @Override
      public @Nullable NodeId getXmlEncodingId() {
        return null;
      }

      @Override
      public @Nullable NodeId getJsonEncodingId() {
        return null;
      }

      @Override
      public @Nullable DataTypeDefinition getDataTypeDefinition() {
        return null;
      }

      @Override
      public Boolean isAbstract() {
        return false;
      }
    }
  }

  @Nested
  class MultiStateDiscreteValidation {

    private final LocalizedText[] enumStrings = {
      LocalizedText.english("Off"),
      LocalizedText.english("Auto"),
      LocalizedText.english("Manual"),
      LocalizedText.english("Fault")
    };

    // Part 8 §5.3.3.3: the value is an index into EnumStrings.
    @Test
    void definedIndicesAreAccepted() {
      for (int i = 0; i < enumStrings.length; i++) {
        UInteger index = uint(i);
        assertDoesNotThrow(() -> DemoValueFilters.validateMultiStateIndex(index, enumStrings));
      }
    }

    @Test
    void undefinedIndicesAndNullAreRejectedAsOutOfRange() {
      Object[] invalid = {uint(4), UInteger.MAX, null};

      for (Object value : invalid) {
        UaException ex =
            assertThrows(
                UaException.class,
                () -> DemoValueFilters.validateMultiStateIndex(value, enumStrings));
        assertEquals(StatusCodes.Bad_OutOfRange, ex.getStatusCode().getValue());
      }

      UaException noStates =
          assertThrows(
              UaException.class, () -> DemoValueFilters.validateMultiStateIndex(uint(0), null));
      assertEquals(StatusCodes.Bad_OutOfRange, noStates.getStatusCode().getValue());
    }
  }
}

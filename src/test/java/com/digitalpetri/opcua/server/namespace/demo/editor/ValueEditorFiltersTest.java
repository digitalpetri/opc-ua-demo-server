package com.digitalpetri.opcua.server.namespace.demo.editor;

import static org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.Unsigned.uint;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Set;
import java.util.UUID;
import org.eclipse.milo.opcua.stack.core.NodeIds;
import org.eclipse.milo.opcua.stack.core.StatusCodes;
import org.eclipse.milo.opcua.stack.core.UaException;
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
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link DemoOptionSet} Part 3 §8.40 write merging, {@link DecimalEncoding} Part 6
 * §5.1.10 binary encoding, and {@link ValueEditorFilters} validation rules.
 */
class ValueEditorFiltersTest {

  @Nested
  class OptionSetMerge {

    @Test
    void partialValidBitsUpdatePreservesUntouchedBits() throws UaException {
      // Part 3 §8.40: bits whose ValidBits bit is 0 must keep their existing server value.
      DemoOptionSet current = DemoOptionSet.of(0x03, 0x01); // Read | Write | Audit
      DemoOptionSet written =
          new DemoOptionSet(
              ByteString.of(new byte[] {0x04, 0x02}), // Execute | Diagnostics
              ByteString.of(new byte[] {0x05, 0x02})); // update Read(0->0), Execute(0->1), Diag(1)

      DemoOptionSet merged = DemoOptionSet.mergeWrite(current, written);

      // Read cleared, Write preserved (bit 1), Execute set (bit 2); Audit preserved (bit 8),
      // Diagnostics set (bit 9).
      assertArrayEquals(new byte[] {0x06, 0x03}, merged.getValue().bytesOrEmpty());
      assertArrayEquals(DemoOptionSet.DEFINED_VALID_BITS, merged.getValidBits().bytesOrEmpty());
    }

    @Test
    void mismatchedValueAndValidBitsLengthThrowsBadOutOfRange() {
      // Part 3 §8.40 requires Value and ValidBits to have identical byte lengths.
      DemoOptionSet current = DemoOptionSet.of(0x01, 0x00);
      DemoOptionSet written =
          new DemoOptionSet(
              ByteString.of(new byte[] {0x01, 0x00}), ByteString.of(new byte[] {0x01}));

      UaException ex =
          assertThrows(UaException.class, () -> DemoOptionSet.mergeWrite(current, written));
      assertEquals(StatusCodes.Bad_OutOfRange, ex.getStatusCode().getValue());
    }

    @Test
    void wrongByteLengthThrowsBadOutOfRange() {
      // DemoOptionSet declares OptionSetLength = 2 bytes.
      DemoOptionSet current = DemoOptionSet.of(0x01, 0x00);
      DemoOptionSet written =
          new DemoOptionSet(ByteString.of(new byte[] {0x01}), ByteString.of(new byte[] {0x01}));

      UaException ex =
          assertThrows(UaException.class, () -> DemoOptionSet.mergeWrite(current, written));
      assertEquals(StatusCodes.Bad_OutOfRange, ex.getStatusCode().getValue());
    }

    @Test
    void undefinedBitInValidBitsThrowsBadOutOfRange() {
      // Part 3 §8.40: setting a ValidBits bit for an undefined gap bit (bit 4 = 0x10) must fail
      // the whole write with Bad_OutOfRange.
      DemoOptionSet current = DemoOptionSet.of(0x01, 0x00);
      DemoOptionSet written =
          new DemoOptionSet(
              ByteString.of(new byte[] {0x11, 0x00}), ByteString.of(new byte[] {0x11, 0x00}));

      UaException ex =
          assertThrows(UaException.class, () -> DemoOptionSet.mergeWrite(current, written));
      assertEquals(StatusCodes.Bad_OutOfRange, ex.getStatusCode().getValue());
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

    @Test
    void encodedBytesMatchPart6LittleEndianLayout() throws UaException {
      // Part 6 §5.1.10: 123.45 has Scale=2 (0x02, 0x00) and unscaled 12345 = 0x3039 -> LE 39 30.
      ExtensionObject.Binary xo =
          (ExtensionObject.Binary) DecimalEncoding.encode(new BigDecimal("123.45"));

      assertArrayEquals(new byte[] {0x02, 0x00, 0x39, 0x30}, xo.getBody().bytesOrEmpty());
      assertEquals(new BigDecimal("123.45"), DecimalEncoding.decode(xo));
    }

    @Test
    void bodyOfTwoBytesOrFewerIsRejectedAsOutOfRange() {
      // Part 6 §5.1.10: "If the Length is <= 2 then the Decimal is invalid."
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
        DemoUnion.ofNull()
      };

      for (Object value : nulls) {
        UaException ex =
            assertThrows(UaException.class, () -> ValueEditorFilters.validateNotNull(value));
        assertEquals(StatusCodes.Bad_TypeMismatch, ex.getStatusCode().getValue());
      }
    }

    @Test
    void emptyNonNullValuesAreAccepted() {
      // OPC UA distinguishes empty values (length 0) from null values (length -1).
      assertDoesNotThrow(() -> ValueEditorFilters.validateNotNull(""));
      assertDoesNotThrow(() -> ValueEditorFilters.validateNotNull(ByteString.of(new byte[0])));
      assertDoesNotThrow(() -> ValueEditorFilters.validateNotNull(XmlElement.of("")));
      assertDoesNotThrow(() -> ValueEditorFilters.validateNotNull(new Integer[0]));
      assertDoesNotThrow(() -> ValueEditorFilters.validateNotNull(DemoUnion.ofInt32(0)));
    }
  }

  @Nested
  class ValueLimitsValidation {

    @Test
    void maxStringLengthCountsUtf8BytesWhileMaxCharactersCountsCodePoints() throws UaException {
      // "cafééé" = 6 code points, 9 UTF-8 bytes (each 'é' is 2 UTF-8 bytes).
      String sixCodePointsNineBytes = "caf\u00E9\u00E9\u00E9";

      // Passes MaxCharacters = 6, fails MaxStringLength = 8.
      ValueEditorFilters.validateValueLimits(
          sixCodePointsNineBytes, null, uint(6), null, null, null, null);

      UaException byteEx =
          assertThrows(
              UaException.class,
              () ->
                  ValueEditorFilters.validateValueLimits(
                      sixCodePointsNineBytes, uint(8), null, null, null, null, null));
      assertEquals(StatusCodes.Bad_OutOfRange, byteEx.getStatusCode().getValue());

      // Fails MaxCharacters = 5 even when UTF-8 byte limit is generous.
      UaException charEx =
          assertThrows(
              UaException.class,
              () ->
                  ValueEditorFilters.validateValueLimits(
                      sixCodePointsNineBytes, uint(32), uint(5), null, null, null, null));
      assertEquals(StatusCodes.Bad_OutOfRange, charEx.getStatusCode().getValue());
    }

    @Test
    void maxByteStringLengthRejectsLongerPayload() throws UaException {
      ValueEditorFilters.validateValueLimits(
          ByteString.of(new byte[8]), null, null, uint(8), null, null, null);

      UaException ex =
          assertThrows(
              UaException.class,
              () ->
                  ValueEditorFilters.validateValueLimits(
                      ByteString.of(new byte[9]), null, null, uint(8), null, null, null));
      assertEquals(StatusCodes.Bad_OutOfRange, ex.getStatusCode().getValue());
    }

    @Test
    void maxArrayLengthCountsTotalElementsAcrossMatrixDimensions() throws UaException {
      // Part 3 §5.6.2: MaxArrayLength applies to the overall element count of a matrix.
      Matrix twoByThree = Matrix.ofInt32(new Integer[][] {{1, 2, 3}, {4, 5, 6}});
      ValueEditorFilters.validateValueLimits(twoByThree, null, null, null, uint(6), null, null);

      Matrix twoByFour = Matrix.ofInt32(new Integer[][] {{1, 2, 3, 4}, {5, 6, 7, 8}});
      UaException ex =
          assertThrows(
              UaException.class,
              () ->
                  ValueEditorFilters.validateValueLimits(
                      twoByFour, null, null, null, uint(6), null, null));
      assertEquals(StatusCodes.Bad_OutOfRange, ex.getStatusCode().getValue());
    }

    @Test
    void fixedSizeArrayRejectsBothShorterAndLongerArrays() throws UaException {
      UInteger[] dims = new UInteger[] {uint(4)};
      ValueEditorFilters.validateValueLimits(
          new Integer[] {1, 2, 3, 4}, null, null, null, null, true, dims);

      UaException shorterEx =
          assertThrows(
              UaException.class,
              () ->
                  ValueEditorFilters.validateValueLimits(
                      new Integer[] {1, 2}, null, null, null, null, true, dims));
      assertEquals(StatusCodes.Bad_OutOfRange, shorterEx.getStatusCode().getValue());

      UaException longerEx =
          assertThrows(
              UaException.class,
              () ->
                  ValueEditorFilters.validateValueLimits(
                      new Integer[] {1, 2, 3, 4, 5}, null, null, null, null, true, dims));
      assertEquals(StatusCodes.Bad_OutOfRange, longerEx.getStatusCode().getValue());
    }
  }

  @Nested
  class EnumAndRefinementValidation {

    @Test
    void enumValuesCheckRejectsGapAndRemovedSubtypeValues() throws UaException {
      Set<Integer> allowed = Set.of(1, 2, 4);
      ValueEditorFilters.validateEnumValues(2, allowed);
      ValueEditorFilters.validateEnumValues(new Integer[] {1, 4}, allowed);

      UaException gapEx =
          assertThrows(UaException.class, () -> ValueEditorFilters.validateEnumValues(3, allowed));
      assertEquals(StatusCodes.Bad_OutOfRange, gapEx.getStatusCode().getValue());

      UaException removedEx =
          assertThrows(UaException.class, () -> ValueEditorFilters.validateEnumValues(8, allowed));
      assertEquals(StatusCodes.Bad_OutOfRange, removedEx.getStatusCode().getValue());
    }

    @Test
    void refinedStructValidationEnforcesMandatoryDisabledAndReadingRules() throws UaException {
      RefinableStruct valid = new RefinableStruct("S1", "Note", null, Variant.ofDouble(21.5));
      ValueEditorFilters.validateRefinedStruct(valid, 32);

      // Missing mandatory OptionalNote
      UaException missingNote =
          assertThrows(
              UaException.class,
              () ->
                  ValueEditorFilters.validateRefinedStruct(
                      new RefinableStruct("S1", null, null, Variant.ofDouble(21.5)), 32));
      assertEquals(StatusCodes.Bad_OutOfRange, missingNote.getStatusCode().getValue());

      // DisabledFlag populated
      UaException disabledFlag =
          assertThrows(
              UaException.class,
              () ->
                  ValueEditorFilters.validateRefinedStruct(
                      new RefinableStruct("S1", "Note", true, Variant.ofDouble(21.5)), 32));
      assertEquals(StatusCodes.Bad_OutOfRange, disabledFlag.getStatusCode().getValue());

      // Reading wrong type (Int32 instead of Double)
      UaException wrongReading =
          assertThrows(
              UaException.class,
              () ->
                  ValueEditorFilters.validateRefinedStruct(
                      new RefinableStruct("S1", "Note", null, Variant.ofInt32(21)), 32));
      assertEquals(StatusCodes.Bad_TypeMismatch, wrongReading.getStatusCode().getValue());
    }
  }
}

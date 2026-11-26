package com.digitalpetri.opcua.server.namespace.demo.editor;

import java.lang.reflect.Array;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.UUID;
import org.eclipse.milo.opcua.sdk.core.QualifiedProperty;
import org.eclipse.milo.opcua.sdk.core.ValueRanks;
import org.eclipse.milo.opcua.sdk.server.nodes.UaVariableNode;
import org.eclipse.milo.opcua.sdk.server.nodes.filters.AttributeFilter;
import org.eclipse.milo.opcua.sdk.server.nodes.filters.AttributeFilterContext;
import org.eclipse.milo.opcua.stack.core.AttributeId;
import org.eclipse.milo.opcua.stack.core.NodeIds;
import org.eclipse.milo.opcua.stack.core.StatusCodes;
import org.eclipse.milo.opcua.stack.core.UaException;
import org.eclipse.milo.opcua.stack.core.UaSerializationException;
import org.eclipse.milo.opcua.stack.core.encoding.EncodingContext;
import org.eclipse.milo.opcua.stack.core.types.UaStructuredType;
import org.eclipse.milo.opcua.stack.core.types.builtin.ByteString;
import org.eclipse.milo.opcua.stack.core.types.builtin.DataValue;
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
import org.eclipse.milo.opcua.stack.core.util.ArrayUtil;
import org.eclipse.milo.opcua.stack.core.util.Namespaces;
import org.jspecify.annotations.Nullable;

/**
 * {@link AttributeFilter} implementations enforcing OPC UA value-write rules that Milo 1.2.0-RC3's
 * {@code AttributeWriter} does not enforce yet: {@code OptionSet} bit-mask merging, typed null
 * rejection under {@code AllowNulls = false}, Variable limit Properties, Enumeration membership,
 * {@code NoSubDataTypes}, {@code SubtypeRestriction}, and {@code DataTypeRefinement}.
 */
public final class ValueEditorFilters {

  public static final QualifiedProperty<UInteger> MAX_BYTE_STRING_LENGTH =
      new QualifiedProperty<>(
          Namespaces.OPC_UA,
          "MaxByteStringLength",
          NodeIds.UInt32.expanded(),
          ValueRanks.Scalar,
          UInteger.class);

  public static final QualifiedProperty<UInteger> MAX_CHARACTERS =
      new QualifiedProperty<>(
          Namespaces.OPC_UA,
          "MaxCharacters",
          NodeIds.UInt32.expanded(),
          ValueRanks.Scalar,
          UInteger.class);

  public static final QualifiedProperty<Boolean> FIXED_SIZE_ARRAY =
      new QualifiedProperty<>(
          Namespaces.OPC_UA,
          "FixedSizeArray",
          NodeIds.Boolean.expanded(),
          ValueRanks.Scalar,
          Boolean.class);

  public static final QualifiedProperty<UInteger> OPTION_SET_LENGTH =
      new QualifiedProperty<>(
          Namespaces.OPC_UA,
          "OptionSetLength",
          NodeIds.UInt32.expanded(),
          ValueRanks.Scalar,
          UInteger.class);

  private static final UUID NULL_UUID = new UUID(0L, 0L);

  private ValueEditorFilters() {}

  /** Filter rejecting root null and OPC UA typed null sentinels when {@code AllowNulls = false}. */
  public static final AttributeFilter DISALLOW_NULLS =
      new AttributeFilter() {
        @Override
        public void writeAttribute(
            AttributeFilterContext ctx, AttributeId attributeId, @Nullable Object value)
            throws UaException {

          if (attributeId == AttributeId.Value && value instanceof DataValue dataValue) {
            validateNotNull(dataValue.value().value());
          }
          ctx.writeAttribute(attributeId, value);
        }
      };

  /**
   * Filter enforcing {@code MaxStringLength} (UTF-8 bytes), {@code MaxCharacters} (Unicode code
   * points), {@code MaxByteStringLength}, {@code MaxArrayLength}, and {@code FixedSizeArray}.
   */
  public static final AttributeFilter VALUE_LIMITS =
      new AttributeFilter() {
        @Override
        public void writeAttribute(
            AttributeFilterContext ctx, AttributeId attributeId, @Nullable Object value)
            throws UaException {

          if (attributeId == AttributeId.Value
              && value instanceof DataValue dataValue
              && ctx.getNode() instanceof UaVariableNode variableNode) {

            validateValueLimits(
                dataValue.value().value(),
                variableNode.getMaxStringLength(),
                variableNode.getProperty(MAX_CHARACTERS).orElse(null),
                variableNode.getProperty(MAX_BYTE_STRING_LENGTH).orElse(null),
                variableNode.getMaxArrayLength(),
                variableNode.getProperty(FIXED_SIZE_ARRAY).orElse(null),
                variableNode.getArrayDimensions());
          }
          ctx.writeAttribute(attributeId, value);
        }
      };

  /**
   * Creates a filter enforcing Part 3 §8.40 {@code OptionSet} write validation and {@code
   * ValidBits}-masked merging on a scalar {@link DemoOptionSet} variable.
   *
   * @param encodingContext the server's static {@link EncodingContext}.
   * @param expectedBinaryEncodingId the resolved {@code Default Binary} encoding NodeId of {@link
   *     DemoOptionSet}.
   * @return a new {@link AttributeFilter}.
   */
  public static AttributeFilter optionSetScalar(
      EncodingContext encodingContext, NodeId expectedBinaryEncodingId) {

    return new AttributeFilter() {
      @Override
      public void writeAttribute(
          AttributeFilterContext ctx, AttributeId attributeId, @Nullable Object value)
          throws UaException {

        if (attributeId == AttributeId.Value
            && value instanceof DataValue dataValue
            && ctx.getNode() instanceof UaVariableNode variableNode) {

          DemoOptionSet written =
              decodeExpectedStruct(
                  encodingContext,
                  expectedBinaryEncodingId,
                  DemoOptionSet.class,
                  dataValue.value().value());

          DemoOptionSet current =
              decodeCurrentOptionSet(encodingContext, variableNode.getValue().value().value());

          DemoOptionSet merged = DemoOptionSet.mergeWrite(current, written);
          ExtensionObject encoded = ExtensionObject.encode(encodingContext, merged);

          DataValue updated =
              new DataValue(
                  Variant.ofExtensionObject(encoded),
                  dataValue.statusCode(),
                  dataValue.sourceTime(),
                  dataValue.serverTime());

          ctx.writeAttribute(attributeId, updated);
          return;
        }
        ctx.writeAttribute(attributeId, value);
      }
    };
  }

  /**
   * Creates a filter enforcing Part 3 §8.40 {@code OptionSet} write validation and {@code
   * ValidBits}-masked merging on a one-dimensional {@link DemoOptionSet} array variable.
   *
   * @param encodingContext the server's static {@link EncodingContext}.
   * @param expectedBinaryEncodingId the resolved {@code Default Binary} encoding NodeId of {@link
   *     DemoOptionSet}.
   * @return a new {@link AttributeFilter}.
   */
  public static AttributeFilter optionSetArray(
      EncodingContext encodingContext, NodeId expectedBinaryEncodingId) {

    return new AttributeFilter() {
      @Override
      public void writeAttribute(
          AttributeFilterContext ctx, AttributeId attributeId, @Nullable Object value)
          throws UaException {

        if (attributeId == AttributeId.Value
            && value instanceof DataValue dataValue
            && ctx.getNode() instanceof UaVariableNode variableNode) {

          Object raw = dataValue.value().value();
          if (!(raw instanceof Object[] elements)) {
            throw new UaException(StatusCodes.Bad_TypeMismatch, "Expected OptionSet array");
          }

          DemoOptionSet[] currentArray =
              decodeCurrentOptionSetArray(encodingContext, variableNode.getValue().value().value());

          ExtensionObject[] encoded = new ExtensionObject[elements.length];
          for (int i = 0; i < elements.length; i++) {
            DemoOptionSet written =
                decodeExpectedStruct(
                    encodingContext, expectedBinaryEncodingId, DemoOptionSet.class, elements[i]);
            DemoOptionSet current =
                i < currentArray.length ? currentArray[i] : DemoOptionSet.of(0, 0);
            DemoOptionSet merged = DemoOptionSet.mergeWrite(current, written);
            encoded[i] = ExtensionObject.encode(encodingContext, merged);
          }

          DataValue updated =
              new DataValue(
                  Variant.ofExtensionObjectArray(encoded),
                  dataValue.statusCode(),
                  dataValue.sourceTime(),
                  dataValue.serverTime());

          ctx.writeAttribute(attributeId, updated);
          return;
        }
        ctx.writeAttribute(attributeId, value);
      }
    };
  }

  /**
   * Creates a filter enforcing Part 3 §8.40 {@code OptionSet} write validation and merging on the
   * {@code Options} field of a {@link StructWithOptionSet} variable.
   *
   * @param encodingContext the server's static {@link EncodingContext}.
   * @param expectedBinaryEncodingId the resolved {@code Default Binary} encoding NodeId of {@link
   *     StructWithOptionSet}.
   * @return a new {@link AttributeFilter}.
   */
  public static AttributeFilter structWithOptionSet(
      EncodingContext encodingContext, NodeId expectedBinaryEncodingId) {

    return new AttributeFilter() {
      @Override
      public void writeAttribute(
          AttributeFilterContext ctx, AttributeId attributeId, @Nullable Object value)
          throws UaException {

        if (attributeId == AttributeId.Value
            && value instanceof DataValue dataValue
            && ctx.getNode() instanceof UaVariableNode variableNode) {

          StructWithOptionSet written =
              decodeExpectedStruct(
                  encodingContext,
                  expectedBinaryEncodingId,
                  StructWithOptionSet.class,
                  dataValue.value().value());

          DemoOptionSet currentOptions = DemoOptionSet.of(0, 0);
          Object currentRaw = variableNode.getValue().value().value();
          if (currentRaw instanceof ExtensionObject xo && !xo.isNull()) {
            UaStructuredType decoded = xo.decode(encodingContext);
            if (decoded instanceof StructWithOptionSet currentStruct) {
              currentOptions = currentStruct.getOptions();
            }
          } else if (currentRaw instanceof StructWithOptionSet currentStruct) {
            currentOptions = currentStruct.getOptions();
          }

          DemoOptionSet mergedOptions =
              DemoOptionSet.mergeWrite(currentOptions, written.getOptions());
          StructWithOptionSet mergedStruct =
              new StructWithOptionSet(written.getName(), mergedOptions);

          ExtensionObject encoded = ExtensionObject.encode(encodingContext, mergedStruct);
          DataValue updated =
              new DataValue(
                  Variant.ofExtensionObject(encoded),
                  dataValue.statusCode(),
                  dataValue.sourceTime(),
                  dataValue.serverTime());

          ctx.writeAttribute(attributeId, updated);
          return;
        }
        ctx.writeAttribute(attributeId, value);
      }
    };
  }

  /**
   * Creates a filter verifying that written {@code Int32} enumeration values belong to {@code
   * allowedValues}, rejecting unlisted values with {@code Bad_OutOfRange}.
   *
   * @param allowedValues the permitted numeric enumeration values.
   * @return a new {@link AttributeFilter}.
   */
  public static AttributeFilter enumValuesCheck(Set<Integer> allowedValues) {
    return new AttributeFilter() {
      @Override
      public void writeAttribute(
          AttributeFilterContext ctx, AttributeId attributeId, @Nullable Object value)
          throws UaException {

        if (attributeId == AttributeId.Value && value instanceof DataValue dataValue) {
          validateEnumValues(dataValue.value().value(), allowedValues);
        }
        ctx.writeAttribute(attributeId, value);
      }
    };
  }

  /**
   * Creates a filter enforcing {@code AccessLevelEx.NoSubDataTypes} by accepting only values whose
   * binary encoding matches {@code expectedBinaryEncodingId} exactly.
   *
   * @param expectedBinaryEncodingId the exact allowed {@code Default Binary} encoding NodeId.
   * @return a new {@link AttributeFilter}.
   */
  public static AttributeFilter noSubDataTypes(NodeId expectedBinaryEncodingId) {
    return new AttributeFilter() {
      @Override
      public void writeAttribute(
          AttributeFilterContext ctx, AttributeId attributeId, @Nullable Object value)
          throws UaException {

        if (attributeId == AttributeId.Value && value instanceof DataValue dataValue) {
          Object raw = dataValue.value().value();
          if (raw instanceof ExtensionObject xo) {
            if (!(xo instanceof ExtensionObject.Binary)
                || !expectedBinaryEncodingId.equals(xo.getEncodingOrTypeId())) {
              throw new UaException(
                  StatusCodes.Bad_TypeMismatch,
                  "NoSubDataTypes forbids subtype encoding " + xo.getEncodingOrTypeId());
            }
          } else {
            throw new UaException(
                StatusCodes.Bad_TypeMismatch, "NoSubDataTypes requires exact structure type");
          }
        }
        ctx.writeAttribute(attributeId, value);
      }
    };
  }

  /**
   * Creates a filter enforcing a {@code SubtypeRestriction} allowing only values whose runtime
   * class is in {@code allowedClasses}.
   *
   * @param allowedClasses the exact permitted Java classes for scalar values.
   * @return a new {@link AttributeFilter}.
   */
  public static AttributeFilter subtypeRestriction(Set<Class<?>> allowedClasses) {
    return new AttributeFilter() {
      @Override
      public void writeAttribute(
          AttributeFilterContext ctx, AttributeId attributeId, @Nullable Object value)
          throws UaException {

        if (attributeId == AttributeId.Value && value instanceof DataValue dataValue) {
          Object raw = dataValue.value().value();
          if (raw == null || !allowedClasses.contains(raw.getClass())) {
            throw new UaException(
                StatusCodes.Bad_TypeMismatch,
                "Value type is not permitted by SubtypeRestriction: "
                    + (raw == null ? "null" : raw.getClass().getSimpleName()));
          }
        }
        ctx.writeAttribute(attributeId, value);
      }
    };
  }

  /**
   * Creates a filter enforcing the {@code DataTypeRefinement} profile on {@link RefinableStruct}:
   * {@code OptionalNote} is mandatory and bounded to {@code maxOptionalNoteBytes} UTF-8 bytes,
   * {@code DisabledFlag} must be absent, and {@code Reading} must hold a scalar {@code Double}.
   *
   * @param encodingContext the server's static {@link EncodingContext}.
   * @param expectedBinaryEncodingId the resolved {@code Default Binary} encoding NodeId of {@link
   *     RefinableStruct}.
   * @param maxOptionalNoteBytes the maximum UTF-8 byte length for {@code OptionalNote}.
   * @return a new {@link AttributeFilter}.
   */
  public static AttributeFilter dataTypeRefinement(
      EncodingContext encodingContext, NodeId expectedBinaryEncodingId, int maxOptionalNoteBytes) {

    return new AttributeFilter() {
      @Override
      public void writeAttribute(
          AttributeFilterContext ctx, AttributeId attributeId, @Nullable Object value)
          throws UaException {

        if (attributeId == AttributeId.Value && value instanceof DataValue dataValue) {
          RefinableStruct written =
              decodeExpectedStruct(
                  encodingContext,
                  expectedBinaryEncodingId,
                  RefinableStruct.class,
                  dataValue.value().value());

          validateRefinedStruct(written, maxOptionalNoteBytes);
        }
        ctx.writeAttribute(attributeId, value);
      }
    };
  }

  static void validateNotNull(@Nullable Object value) throws UaException {
    if (value == null || isTypedNull(value)) {
      throw new UaException(
          StatusCodes.Bad_TypeMismatch, "Null values are not allowed when AllowNulls is false");
    }
  }

  static boolean isTypedNull(Object value) {
    return switch (value) {
      case ByteString bs -> bs.isNull();
      case XmlElement xml -> xml.isNull();
      case DateTime dt -> dt.isNull();
      case UUID uuid -> NULL_UUID.equals(uuid);
      case NodeId nodeId -> nodeId.isNull();
      case ExpandedNodeId expandedNodeId -> expandedNodeId.isNull();
      case QualifiedName qualifiedName -> qualifiedName.isNull();
      case LocalizedText localizedText -> localizedText.isNull();
      case ExtensionObject xo -> xo.isNull();
      case DemoUnion union -> union.isNull();
      default -> false;
    };
  }

  static void validateValueLimits(
      @Nullable Object value,
      @Nullable UInteger maxStringLength,
      @Nullable UInteger maxCharacters,
      @Nullable UInteger maxByteStringLength,
      @Nullable UInteger maxArrayLength,
      @Nullable Boolean fixedSizeArray,
      UInteger @Nullable [] arrayDimensions)
      throws UaException {

    if (value == null) {
      return;
    }

    if (maxStringLength != null || maxCharacters != null) {
      validateStringLimits(value, maxStringLength, maxCharacters);
    }

    if (maxByteStringLength != null) {
      validateByteStringLimit(value, maxByteStringLength.longValue());
    }

    if (maxArrayLength != null) {
      int totalElements = totalElementCount(value);
      if (totalElements >= 0 && totalElements > maxArrayLength.longValue()) {
        throw new UaException(
            StatusCodes.Bad_OutOfRange,
            "Array length %d exceeds MaxArrayLength %s".formatted(totalElements, maxArrayLength));
      }
    }

    if (Boolean.TRUE.equals(fixedSizeArray)
        && arrayDimensions != null
        && arrayDimensions.length > 0) {
      int[] actualDims = actualDimensions(value);
      if (actualDims == null || actualDims.length != arrayDimensions.length) {
        throw new UaException(
            StatusCodes.Bad_OutOfRange, "Value dimensions do not match FixedSizeArray");
      }
      for (int i = 0; i < arrayDimensions.length; i++) {
        int expected = arrayDimensions[i].intValue();
        if (expected > 0 && actualDims[i] != expected) {
          throw new UaException(
              StatusCodes.Bad_OutOfRange,
              "Dimension %d length %d does not match FixedSizeArray dimension %d"
                  .formatted(i, actualDims[i], expected));
        }
      }
    }
  }

  static void validateEnumValues(@Nullable Object value, Set<Integer> allowedValues)
      throws UaException {

    if (value == null) {
      return;
    }

    if (value instanceof Integer i) {
      if (!allowedValues.contains(i)) {
        throw new UaException(
            StatusCodes.Bad_OutOfRange, "Enumeration value %d is not defined".formatted(i));
      }
    } else if (value instanceof Integer[] array) {
      for (Integer element : array) {
        if (element != null && !allowedValues.contains(element)) {
          throw new UaException(
              StatusCodes.Bad_OutOfRange, "Enumeration value %d is not defined".formatted(element));
        }
      }
    } else if (value instanceof int[] array) {
      for (int element : array) {
        if (!allowedValues.contains(element)) {
          throw new UaException(
              StatusCodes.Bad_OutOfRange, "Enumeration value %d is not defined".formatted(element));
        }
      }
    } else if (value instanceof Matrix matrix) {
      validateEnumValues(matrix.getElements(), allowedValues);
    } else {
      throw new UaException(StatusCodes.Bad_TypeMismatch, "Expected Int32 enumeration value");
    }
  }

  static void validateRefinedStruct(RefinableStruct struct, int maxOptionalNoteBytes)
      throws UaException {

    String optionalNote = struct.getOptionalNote();
    if (optionalNote == null) {
      throw new UaException(
          StatusCodes.Bad_OutOfRange, "OptionalNote is mandatory under DataTypeRefinement");
    }

    int utf8Bytes = optionalNote.getBytes(StandardCharsets.UTF_8).length;
    if (utf8Bytes > maxOptionalNoteBytes) {
      throw new UaException(
          StatusCodes.Bad_OutOfRange,
          "OptionalNote UTF-8 length (%d) exceeds refined MaxStringLength (%d)"
              .formatted(utf8Bytes, maxOptionalNoteBytes));
    }

    if (struct.getDisabledFlag() != null) {
      throw new UaException(
          StatusCodes.Bad_OutOfRange, "DisabledFlag is disabled under DataTypeRefinement");
    }

    Variant reading = struct.getReading();
    if (reading == null || !(reading.value() instanceof Double)) {
      throw new UaException(
          StatusCodes.Bad_TypeMismatch, "Reading must be a scalar Double under DataTypeRefinement");
    }
  }

  private static void validateStringLimits(
      Object value, @Nullable UInteger maxStringLength, @Nullable UInteger maxCharacters)
      throws UaException {

    if (value instanceof String s) {
      checkSingleString(s, maxStringLength, maxCharacters);
    } else if (value instanceof String[] array) {
      for (String s : array) {
        if (s != null) {
          checkSingleString(s, maxStringLength, maxCharacters);
        }
      }
    } else if (value instanceof Matrix matrix) {
      Object elements = matrix.getElements();
      if (elements != null) {
        validateStringLimits(elements, maxStringLength, maxCharacters);
      }
    }
  }

  private static void checkSingleString(
      String s, @Nullable UInteger maxStringLength, @Nullable UInteger maxCharacters)
      throws UaException {

    if (maxStringLength != null) {
      int byteLength = s.getBytes(StandardCharsets.UTF_8).length;
      if (byteLength > maxStringLength.longValue()) {
        throw new UaException(
            StatusCodes.Bad_OutOfRange,
            "String UTF-8 byte length %d exceeds MaxStringLength %s"
                .formatted(byteLength, maxStringLength));
      }
    }

    if (maxCharacters != null) {
      int codePoints = s.codePointCount(0, s.length());
      if (codePoints > maxCharacters.longValue()) {
        throw new UaException(
            StatusCodes.Bad_OutOfRange,
            "String code point count %d exceeds MaxCharacters %s"
                .formatted(codePoints, maxCharacters));
      }
    }
  }

  private static void validateByteStringLimit(Object value, long maxBytes) throws UaException {
    if (value instanceof ByteString bs) {
      if (!bs.isNull() && bs.length() > maxBytes) {
        throw new UaException(
            StatusCodes.Bad_OutOfRange,
            "ByteString length %d exceeds MaxByteStringLength %d".formatted(bs.length(), maxBytes));
      }
    } else if (value instanceof ByteString[] array) {
      for (ByteString bs : array) {
        if (bs != null && !bs.isNull() && bs.length() > maxBytes) {
          throw new UaException(
              StatusCodes.Bad_OutOfRange,
              "ByteString element length %d exceeds MaxByteStringLength %d"
                  .formatted(bs.length(), maxBytes));
        }
      }
    } else if (value instanceof Matrix matrix) {
      Object elements = matrix.getElements();
      if (elements != null) {
        validateByteStringLimit(elements, maxBytes);
      }
    }
  }

  private static int totalElementCount(Object value) {
    if (value instanceof Matrix matrix) {
      Object elements = matrix.getElements();
      return elements != null && elements.getClass().isArray() ? Array.getLength(elements) : 0;
    } else if (value.getClass().isArray()) {
      return Array.getLength(value);
    }
    return -1;
  }

  private static int @Nullable [] actualDimensions(Object value) {
    if (value instanceof Matrix matrix) {
      return matrix.getDimensions();
    } else if (value.getClass().isArray()) {
      return ArrayUtil.getDimensions(value);
    }
    return null;
  }

  private static <T extends UaStructuredType> T decodeExpectedStruct(
      EncodingContext encodingContext,
      NodeId expectedBinaryEncodingId,
      Class<T> expectedType,
      @Nullable Object raw)
      throws UaException {

    if (expectedType.isInstance(raw)) {
      return expectedType.cast(raw);
    }

    if (!(raw instanceof ExtensionObject xo) || xo.isNull()) {
      throw new UaException(
          StatusCodes.Bad_TypeMismatch,
          "Expected " + expectedType.getSimpleName() + " ExtensionObject");
    }

    if (!(xo instanceof ExtensionObject.Binary)
        || !expectedBinaryEncodingId.equals(xo.getEncodingOrTypeId())) {
      throw new UaException(
          StatusCodes.Bad_TypeMismatch,
          "Unexpected ExtensionObject encoding: " + xo.getEncodingOrTypeId());
    }

    try {
      UaStructuredType decoded = xo.decode(encodingContext);
      if (expectedType.isInstance(decoded)) {
        return expectedType.cast(decoded);
      }
      throw new UaException(
          StatusCodes.Bad_TypeMismatch,
          "Decoded type mismatch: expected " + expectedType.getSimpleName());
    } catch (UaSerializationException e) {
      throw new UaException(StatusCodes.Bad_TypeMismatch, e);
    }
  }

  private static DemoOptionSet decodeCurrentOptionSet(
      EncodingContext encodingContext, @Nullable Object raw) {

    if (raw instanceof DemoOptionSet optionSet) {
      return optionSet;
    }
    if (raw instanceof ExtensionObject xo && !xo.isNull()) {
      UaStructuredType decoded = xo.decode(encodingContext);
      if (decoded instanceof DemoOptionSet optionSet) {
        return optionSet;
      }
    }
    return DemoOptionSet.of(0, 0);
  }

  private static DemoOptionSet[] decodeCurrentOptionSetArray(
      EncodingContext encodingContext, @Nullable Object raw) {

    if (raw instanceof DemoOptionSet[] array) {
      return array;
    }
    if (raw instanceof ExtensionObject[] xos) {
      DemoOptionSet[] result = new DemoOptionSet[xos.length];
      for (int i = 0; i < xos.length; i++) {
        result[i] = decodeCurrentOptionSet(encodingContext, xos[i]);
      }
      return result;
    }
    return new DemoOptionSet[0];
  }
}

package com.digitalpetri.opcua.server.namespace.demo;

import com.digitalpetri.opcua.server.namespace.demo.types.DemoOptionSet;
import com.digitalpetri.opcua.server.namespace.demo.types.RefinableStruct;
import com.digitalpetri.opcua.server.namespace.demo.types.StructWithOptionSet;
import com.digitalpetri.opcua.test.types.UnionOfArray;
import com.digitalpetri.opcua.test.types.UnionOfMatrix;
import com.digitalpetri.opcua.test.types.UnionOfScalar;
import java.lang.reflect.Array;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.eclipse.milo.opcua.sdk.core.QualifiedProperty;
import org.eclipse.milo.opcua.sdk.core.ValueRanks;
import org.eclipse.milo.opcua.sdk.server.model.variables.MultiStateValueDiscreteTypeNode;
import org.eclipse.milo.opcua.sdk.server.model.variables.XYArrayItemTypeNode;
import org.eclipse.milo.opcua.sdk.server.nodes.UaVariableNode;
import org.eclipse.milo.opcua.sdk.server.nodes.filters.AttributeFilter;
import org.eclipse.milo.opcua.sdk.server.nodes.filters.AttributeFilterContext;
import org.eclipse.milo.opcua.stack.core.AttributeId;
import org.eclipse.milo.opcua.stack.core.NodeIds;
import org.eclipse.milo.opcua.stack.core.OpcUaDataType;
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
import org.eclipse.milo.opcua.stack.core.types.structured.Range;
import org.eclipse.milo.opcua.stack.core.types.structured.XVType;
import org.eclipse.milo.opcua.stack.core.util.ArrayUtil;
import org.eclipse.milo.opcua.stack.core.util.Namespaces;
import org.jspecify.annotations.Nullable;

/**
 * {@link AttributeFilter} implementations enforcing OPC UA value-write rules that Milo 1.2.0-RC3's
 * {@code AttributeWriter} does not enforce yet: {@code OptionSet} bit-mask merging (scalar, array,
 * and matrix), typed null rejection under {@code AllowNulls = false}, Variable limit Properties,
 * Enumeration and {@code MultiStateValueDiscrete} membership, {@code NoSubDataTypes}, {@code
 * SubtypeRestriction}, and {@code DataTypeRefinement}.
 */
public final class DemoValueFilters {

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

  private DemoValueFilters() {}

  /** Filter rejecting root null and OPC UA typed null sentinels when {@code AllowNulls = false}. */
  public static final AttributeFilter DISALLOW_NULLS = disallowNulls(null);

  /**
   * Creates a filter rejecting root null and OPC UA typed null sentinels when {@code AllowNulls =
   * false}. When {@code encodingContext} is non-null, encoded {@link ExtensionObject} values are
   * also decoded to reject null {@code Union} values ({@code SwitchField = 0}).
   *
   * @param encodingContext optional {@link EncodingContext} for decoding Union ExtensionObjects.
   * @return a new {@link AttributeFilter}.
   */
  public static AttributeFilter disallowNulls(@Nullable EncodingContext encodingContext) {
    return new AttributeFilter() {
      @Override
      public void writeAttribute(
          AttributeFilterContext ctx, AttributeId attributeId, @Nullable Object value)
          throws UaException {

        if (attributeId == AttributeId.Value && value instanceof DataValue dataValue) {
          validateNotNull(dataValue.value().value(), encodingContext);
        }
        ctx.writeAttribute(attributeId, value);
      }
    };
  }

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

          synchronized (variableNode) {
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
          }
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

          synchronized (variableNode) {
            DemoOptionSet[] currentArray =
                decodeCurrentOptionSetElements(
                    encodingContext, variableNode.getValue().value().value());

            ExtensionObject[] encoded =
                mergeOptionSetElements(
                    encodingContext, expectedBinaryEncodingId, currentArray, elements);

            DataValue updated =
                new DataValue(
                    Variant.ofExtensionObjectArray(encoded),
                    dataValue.statusCode(),
                    dataValue.sourceTime(),
                    dataValue.serverTime());

            ctx.writeAttribute(attributeId, updated);
          }
          return;
        }
        ctx.writeAttribute(attributeId, value);
      }
    };
  }

  /**
   * Creates a filter enforcing Part 3 §8.40 {@code OptionSet} write validation and {@code
   * ValidBits}-masked merging on a multi-dimensional {@link DemoOptionSet} matrix variable.
   *
   * @param encodingContext the server's static {@link EncodingContext}.
   * @param expectedBinaryEncodingId the resolved {@code Default Binary} encoding NodeId of {@link
   *     DemoOptionSet}.
   * @return a new {@link AttributeFilter}.
   */
  public static AttributeFilter optionSetMatrix(
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
          if (!(raw instanceof Matrix matrix)
              || !(matrix.getElements() instanceof Object[] elements)) {
            throw new UaException(StatusCodes.Bad_TypeMismatch, "Expected OptionSet matrix");
          }

          synchronized (variableNode) {
            DemoOptionSet[] currentFlat =
                decodeCurrentOptionSetElements(
                    encodingContext, variableNode.getValue().value().value());

            ExtensionObject[] encodedFlat =
                mergeOptionSetElements(
                    encodingContext, expectedBinaryEncodingId, currentFlat, elements);

            Matrix updatedMatrix =
                new Matrix(encodedFlat, matrix.getDimensions(), OpcUaDataType.ExtensionObject);
            DataValue updated =
                new DataValue(
                    Variant.ofMatrix(updatedMatrix),
                    dataValue.statusCode(),
                    dataValue.sourceTime(),
                    dataValue.serverTime());

            ctx.writeAttribute(attributeId, updated);
          }
          return;
        }
        ctx.writeAttribute(attributeId, value);
      }
    };
  }

  /**
   * Creates a filter enforcing Part 3 §8.40 {@code OptionSet} write validation and merging on the
   * {@code Options} field of a scalar {@link StructWithOptionSet} variable.
   *
   * @param encodingContext the server's static {@link EncodingContext}.
   * @param expectedBinaryEncodingId the resolved {@code Default Binary} encoding NodeId of {@link
   *     StructWithOptionSet}.
   * @return a new {@link AttributeFilter}.
   */
  public static AttributeFilter structWithOptionSetScalar(
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

          synchronized (variableNode) {
            DemoOptionSet currentOptions =
                decodeCurrentStructOptions(
                    encodingContext, variableNode.getValue().value().value());

            StructWithOptionSet mergedStruct =
                new StructWithOptionSet(
                    written.getName(),
                    DemoOptionSet.mergeWrite(currentOptions, written.getOptions()));

            ExtensionObject encoded = ExtensionObject.encode(encodingContext, mergedStruct);
            DataValue updated =
                new DataValue(
                    Variant.ofExtensionObject(encoded),
                    dataValue.statusCode(),
                    dataValue.sourceTime(),
                    dataValue.serverTime());

            ctx.writeAttribute(attributeId, updated);
          }
          return;
        }
        ctx.writeAttribute(attributeId, value);
      }
    };
  }

  /**
   * Creates a filter enforcing Part 3 §8.40 {@code OptionSet} write validation and merging on the
   * {@code Options} field of each element in a {@link StructWithOptionSet} 1-D array variable.
   *
   * @param encodingContext the server's static {@link EncodingContext}.
   * @param expectedBinaryEncodingId the resolved {@code Default Binary} encoding NodeId of {@link
   *     StructWithOptionSet}.
   * @return a new {@link AttributeFilter}.
   */
  public static AttributeFilter structWithOptionSetArray(
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
            throw new UaException(
                StatusCodes.Bad_TypeMismatch, "Expected StructWithOptionSet array");
          }

          synchronized (variableNode) {
            DemoOptionSet[] currentOptions =
                decodeCurrentStructOptionsElements(
                    encodingContext, variableNode.getValue().value().value());

            ExtensionObject[] encoded =
                mergeStructWithOptionSetElements(
                    encodingContext, expectedBinaryEncodingId, currentOptions, elements);

            DataValue updated =
                new DataValue(
                    Variant.ofExtensionObjectArray(encoded),
                    dataValue.statusCode(),
                    dataValue.sourceTime(),
                    dataValue.serverTime());

            ctx.writeAttribute(attributeId, updated);
          }
          return;
        }
        ctx.writeAttribute(attributeId, value);
      }
    };
  }

  /**
   * Creates a filter enforcing Part 3 §8.40 {@code OptionSet} write validation and merging on the
   * {@code Options} field of each element in a {@link StructWithOptionSet} matrix variable.
   *
   * @param encodingContext the server's static {@link EncodingContext}.
   * @param expectedBinaryEncodingId the resolved {@code Default Binary} encoding NodeId of {@link
   *     StructWithOptionSet}.
   * @return a new {@link AttributeFilter}.
   */
  public static AttributeFilter structWithOptionSetMatrix(
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
          if (!(raw instanceof Matrix matrix)
              || !(matrix.getElements() instanceof Object[] elements)) {
            throw new UaException(
                StatusCodes.Bad_TypeMismatch, "Expected StructWithOptionSet matrix");
          }

          synchronized (variableNode) {
            DemoOptionSet[] currentOptions =
                decodeCurrentStructOptionsElements(
                    encodingContext, variableNode.getValue().value().value());

            ExtensionObject[] encodedFlat =
                mergeStructWithOptionSetElements(
                    encodingContext, expectedBinaryEncodingId, currentOptions, elements);

            Matrix updatedMatrix =
                new Matrix(encodedFlat, matrix.getDimensions(), OpcUaDataType.ExtensionObject);
            DataValue updated =
                new DataValue(
                    Variant.ofMatrix(updatedMatrix),
                    dataValue.statusCode(),
                    dataValue.sourceTime(),
                    dataValue.serverTime());

            ctx.writeAttribute(attributeId, updated);
          }
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
   * Creates a filter validating written values on a {@link MultiStateValueDiscreteTypeNode} against
   * its defined {@code EnumValues} and updating its {@code ValueAsText} property to match.
   *
   * @param valueTexts mapping from permitted integer values to their display labels.
   * @return a new {@link AttributeFilter}.
   */
  public static AttributeFilter multiStateValueDiscrete(Map<Integer, LocalizedText> valueTexts) {
    return new AttributeFilter() {
      @Override
      public void writeAttribute(
          AttributeFilterContext ctx, AttributeId attributeId, @Nullable Object value)
          throws UaException {

        if (attributeId == AttributeId.Value && value instanceof DataValue dataValue) {
          Object raw = dataValue.value().value();
          if (!(raw instanceof Integer intValue) || !valueTexts.containsKey(intValue)) {
            throw new UaException(
                StatusCodes.Bad_OutOfRange,
                "MultiStateValueDiscrete value is not defined in EnumValues: " + raw);
          }

          ctx.writeAttribute(attributeId, value);

          if (ctx.getNode() instanceof MultiStateValueDiscreteTypeNode msvNode) {
            msvNode.setValueAsText(valueTexts.get(intValue));
          }
          return;
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
    return allowedBinaryEncodings(Set.of(expectedBinaryEncodingId));
  }

  /**
   * Creates a filter accepting only scalar binary {@link ExtensionObject} values whose encoding
   * NodeId belongs to {@code allowedBinaryEncodingIds}, rejecting all other values with {@code
   * Bad_TypeMismatch}.
   *
   * @param allowedBinaryEncodingIds the permitted {@code Default Binary} encoding NodeIds.
   * @return a new {@link AttributeFilter}.
   */
  public static AttributeFilter allowedBinaryEncodings(Set<NodeId> allowedBinaryEncodingIds) {
    Set<NodeId> allowed = Set.copyOf(allowedBinaryEncodingIds);

    return new AttributeFilter() {
      @Override
      public void writeAttribute(
          AttributeFilterContext ctx, AttributeId attributeId, @Nullable Object value)
          throws UaException {

        if (attributeId == AttributeId.Value && value instanceof DataValue dataValue) {
          Object raw = dataValue.value().value();
          if (raw instanceof ExtensionObject xo) {
            if (!(xo instanceof ExtensionObject.Binary)
                || !allowed.contains(xo.getEncodingOrTypeId())) {
              throw new UaException(
                  StatusCodes.Bad_TypeMismatch,
                  "Structure encoding is not permitted: " + xo.getEncodingOrTypeId());
            }
          } else {
            throw new UaException(
                StatusCodes.Bad_TypeMismatch, "Expected an allowed structured binary value");
          }
        }
        ctx.writeAttribute(attributeId, value);
      }
    };
  }

  /**
   * Creates a filter validating Part 8 {@code XYArrayItemType} writes as 1-D {@link XVType} arrays
   * whose {@code value} (Y coordinate) components fall within the node's {@code EURange}.
   *
   * @param encodingContext the server's static {@link EncodingContext}.
   * @return a new {@link AttributeFilter}.
   */
  public static AttributeFilter xyArrayEuRangeCheck(EncodingContext encodingContext) {
    NodeId xvBinaryEncodingId =
        XVType.BINARY_ENCODING_ID.toNodeId(encodingContext.getNamespaceTable()).orElseThrow();

    return new AttributeFilter() {
      @Override
      public void writeAttribute(
          AttributeFilterContext ctx, AttributeId attributeId, @Nullable Object value)
          throws UaException {

        if (attributeId == AttributeId.Value
            && value instanceof DataValue dataValue
            && ctx.getNode() instanceof XYArrayItemTypeNode xyArrayItem) {

          validateXyArrayEuRange(
              dataValue.value().value(),
              xyArrayItem.getEuRange(),
              encodingContext,
              xvBinaryEncodingId);
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
    validateNotNull(value, null);
  }

  static void validateNotNull(@Nullable Object value, @Nullable EncodingContext encodingContext)
      throws UaException {

    if (value == null || isTypedNull(value, encodingContext)) {
      throw new UaException(
          StatusCodes.Bad_TypeMismatch, "Null values are not allowed when AllowNulls is false");
    }
  }

  static boolean isTypedNull(Object value, @Nullable EncodingContext encodingContext) {
    return switch (value) {
      case ByteString bs -> bs.isNull();
      case XmlElement xml -> xml.isNull();
      case DateTime dt -> dt.isNull();
      case UUID uuid -> NULL_UUID.equals(uuid);
      case NodeId nodeId -> nodeId.isNull();
      case ExpandedNodeId expandedNodeId -> expandedNodeId.isNull();
      case QualifiedName qualifiedName -> qualifiedName.isNull();
      case LocalizedText localizedText -> localizedText.isNull();
      case ExtensionObject xo -> {
        if (xo.isNull()) {
          yield true;
        }
        if (encodingContext != null) {
          try {
            UaStructuredType decoded = xo.decode(encodingContext);
            yield isNullUnion(decoded);
          } catch (UaSerializationException e) {
            yield false;
          }
        }
        yield false;
      }
      case UnionOfScalar u -> isNullUnion(u);
      case UnionOfArray u -> isNullUnion(u);
      case UnionOfMatrix u -> isNullUnion(u);
      default -> false;
    };
  }

  /**
   * Returns {@code true} if {@code value} is one of the {@code DataTypeTest} Union types whose
   * {@code SwitchField} is {@code 0} (the OPC UA typed null union).
   *
   * @param value the candidate object to inspect.
   * @return {@code true} when {@code value} is a null Union instance.
   */
  public static boolean isNullUnion(@Nullable Object value) {
    return switch (value) {
      case UnionOfScalar u -> !u.isBoolean() && !u.isSByte() && !u.isByte();
      case UnionOfArray u -> !u.isBoolean() && !u.isSByte() && !u.isByte();
      case UnionOfMatrix u -> !u.isBoolean() && !u.isSByte() && !u.isByte();
      case null, default -> false;
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

  static void validateXyArrayEuRange(
      @Nullable Object value,
      @Nullable Range euRange,
      EncodingContext encodingContext,
      NodeId xvBinaryEncodingId)
      throws UaException {

    if (!(value instanceof Object[] elements)) {
      throw new UaException(StatusCodes.Bad_TypeMismatch, "Expected 1-D XVType array");
    }

    for (int i = 0; i < elements.length; i++) {
      XVType xv =
          decodeExpectedStruct(encodingContext, xvBinaryEncodingId, XVType.class, elements[i]);

      if (euRange != null) {
        Float y = xv.getValue();
        double low = euRange.getLow();
        double high = euRange.getHigh();
        if (y == null || y.isNaN() || y < low || y > high) {
          throw new UaException(
              StatusCodes.Bad_OutOfRange,
              "XVType element [%d] value %s is out of range [%s, %s]".formatted(i, y, low, high));
        }
      }
    }
  }

  private static ExtensionObject[] mergeOptionSetElements(
      EncodingContext encodingContext,
      NodeId expectedBinaryEncodingId,
      DemoOptionSet[] currentElements,
      Object[] incomingElements)
      throws UaException {

    ExtensionObject[] encoded = new ExtensionObject[incomingElements.length];
    for (int i = 0; i < incomingElements.length; i++) {
      DemoOptionSet written =
          decodeExpectedStruct(
              encodingContext, expectedBinaryEncodingId, DemoOptionSet.class, incomingElements[i]);
      DemoOptionSet current =
          i < currentElements.length ? currentElements[i] : DemoOptionSet.of(0, 0);
      DemoOptionSet merged = DemoOptionSet.mergeWrite(current, written);
      encoded[i] = ExtensionObject.encode(encodingContext, merged);
    }
    return encoded;
  }

  private static ExtensionObject[] mergeStructWithOptionSetElements(
      EncodingContext encodingContext,
      NodeId expectedBinaryEncodingId,
      DemoOptionSet[] currentOptions,
      Object[] incomingElements)
      throws UaException {

    ExtensionObject[] encoded = new ExtensionObject[incomingElements.length];
    for (int i = 0; i < incomingElements.length; i++) {
      StructWithOptionSet written =
          decodeExpectedStruct(
              encodingContext,
              expectedBinaryEncodingId,
              StructWithOptionSet.class,
              incomingElements[i]);
      DemoOptionSet current =
          i < currentOptions.length ? currentOptions[i] : DemoOptionSet.of(0, 0);
      StructWithOptionSet merged =
          new StructWithOptionSet(
              written.getName(), DemoOptionSet.mergeWrite(current, written.getOptions()));
      encoded[i] = ExtensionObject.encode(encodingContext, merged);
    }
    return encoded;
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

  private static DemoOptionSet[] decodeCurrentOptionSetElements(
      EncodingContext encodingContext, @Nullable Object raw) {

    Object candidate = raw instanceof Matrix matrix ? matrix.getElements() : raw;
    if (candidate instanceof DemoOptionSet[] array) {
      return array;
    }
    if (candidate instanceof ExtensionObject[] xos) {
      DemoOptionSet[] result = new DemoOptionSet[xos.length];
      for (int i = 0; i < xos.length; i++) {
        result[i] = decodeCurrentOptionSet(encodingContext, xos[i]);
      }
      return result;
    }
    return new DemoOptionSet[0];
  }

  private static DemoOptionSet decodeCurrentStructOptions(
      EncodingContext encodingContext, @Nullable Object raw) {

    if (raw instanceof StructWithOptionSet currentStruct) {
      return currentStruct.getOptions();
    }
    if (raw instanceof ExtensionObject xo && !xo.isNull()) {
      UaStructuredType decoded = xo.decode(encodingContext);
      if (decoded instanceof StructWithOptionSet currentStruct) {
        return currentStruct.getOptions();
      }
    }
    return DemoOptionSet.of(0, 0);
  }

  private static DemoOptionSet[] decodeCurrentStructOptionsElements(
      EncodingContext encodingContext, @Nullable Object raw) {

    Object candidate = raw instanceof Matrix matrix ? matrix.getElements() : raw;
    if (candidate instanceof StructWithOptionSet[] array) {
      DemoOptionSet[] result = new DemoOptionSet[array.length];
      for (int i = 0; i < array.length; i++) {
        result[i] = array[i].getOptions();
      }
      return result;
    }
    if (candidate instanceof ExtensionObject[] xos) {
      DemoOptionSet[] result = new DemoOptionSet[xos.length];
      for (int i = 0; i < xos.length; i++) {
        result[i] = decodeCurrentStructOptions(encodingContext, xos[i]);
      }
      return result;
    }
    return new DemoOptionSet[0];
  }
}

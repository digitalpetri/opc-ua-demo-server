package com.digitalpetri.opcua.server.namespace.demo.editor;

import static org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.Unsigned.uint;

import com.digitalpetri.opcua.server.namespace.demo.DemoNamespace;
import java.util.Objects;
import org.eclipse.milo.opcua.sdk.core.ValueRanks;
import org.eclipse.milo.opcua.stack.core.NamespaceTable;
import org.eclipse.milo.opcua.stack.core.NodeIds;
import org.eclipse.milo.opcua.stack.core.StatusCodes;
import org.eclipse.milo.opcua.stack.core.UaSerializationException;
import org.eclipse.milo.opcua.stack.core.encoding.EncodingContext;
import org.eclipse.milo.opcua.stack.core.encoding.GenericDataTypeCodec;
import org.eclipse.milo.opcua.stack.core.encoding.UaDecoder;
import org.eclipse.milo.opcua.stack.core.encoding.UaEncoder;
import org.eclipse.milo.opcua.stack.core.types.builtin.ExpandedNodeId;
import org.eclipse.milo.opcua.stack.core.types.builtin.LocalizedText;
import org.eclipse.milo.opcua.stack.core.types.builtin.NodeId;
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.UInteger;
import org.eclipse.milo.opcua.stack.core.types.enumerated.StructureType;
import org.eclipse.milo.opcua.stack.core.types.structured.StructureDefinition;
import org.eclipse.milo.opcua.stack.core.types.structured.StructureField;
import org.eclipse.milo.opcua.stack.core.types.structured.Union;
import org.jspecify.annotations.Nullable;

/**
 * A concrete {@link Union} subtype whose {@code SwitchField = 0} branch represents a typed null
 * union, used by the writable null fixtures.
 */
public final class DemoUnion extends Union {

  public static final String TYPE_IDENTIFIER = "Demo.ValueEditorTest.Types.DemoUnion";
  public static final String BINARY_ENCODING_IDENTIFIER = TYPE_IDENTIFIER + ".DefaultBinary";

  public static final ExpandedNodeId TYPE_ID =
      ExpandedNodeId.of(DemoNamespace.NAMESPACE_URI, TYPE_IDENTIFIER);
  public static final ExpandedNodeId BINARY_ENCODING_ID =
      ExpandedNodeId.of(DemoNamespace.NAMESPACE_URI, BINARY_ENCODING_IDENTIFIER);

  public enum Field {
    NULL,
    INT32_VALUE,
    STRING_VALUE,
    DOUBLE_VALUE
  }

  private final Field field;
  private final @Nullable Object value;

  private DemoUnion(Field field, @Nullable Object value) {
    this.field = field;
    this.value = value;
  }

  public static DemoUnion ofNull() {
    return new DemoUnion(Field.NULL, null);
  }

  public static DemoUnion ofInt32(Integer value) {
    return new DemoUnion(Field.INT32_VALUE, value);
  }

  public static DemoUnion ofString(@Nullable String value) {
    return new DemoUnion(Field.STRING_VALUE, value);
  }

  public static DemoUnion ofDouble(Double value) {
    return new DemoUnion(Field.DOUBLE_VALUE, value);
  }

  public Field getField() {
    return field;
  }

  public boolean isNull() {
    return field == Field.NULL;
  }

  public @Nullable Integer getInt32Value() {
    return field == Field.INT32_VALUE ? (Integer) value : null;
  }

  public @Nullable String getStringValue() {
    return field == Field.STRING_VALUE ? (String) value : null;
  }

  public @Nullable Double getDoubleValue() {
    return field == Field.DOUBLE_VALUE ? (Double) value : null;
  }

  @Override
  public ExpandedNodeId getTypeId() {
    return TYPE_ID;
  }

  @Override
  public ExpandedNodeId getBinaryEncodingId() {
    return BINARY_ENCODING_ID;
  }

  @Override
  public ExpandedNodeId getXmlEncodingId() {
    return ExpandedNodeId.NULL_VALUE;
  }

  @Override
  public ExpandedNodeId getJsonEncodingId() {
    return ExpandedNodeId.NULL_VALUE;
  }

  public static StructureDefinition definition(NamespaceTable namespaceTable) {
    NodeId encodingId = BINARY_ENCODING_ID.toNodeId(namespaceTable).orElseThrow();

    return new StructureDefinition(
        encodingId,
        NodeIds.Union,
        StructureType.Union,
        new StructureField[] {
          new StructureField(
              "Int32Value",
              LocalizedText.english("Int32 branch"),
              NodeIds.Int32,
              ValueRanks.Scalar,
              null,
              uint(0),
              false),
          new StructureField(
              "StringValue",
              LocalizedText.english("String branch"),
              NodeIds.String,
              ValueRanks.Scalar,
              null,
              uint(0),
              false),
          new StructureField(
              "DoubleValue",
              LocalizedText.english("Double branch"),
              NodeIds.Double,
              ValueRanks.Scalar,
              null,
              uint(0),
              false)
        });
  }

  @Override
  public boolean equals(@Nullable Object o) {
    if (this == o) return true;
    if (!(o instanceof DemoUnion that)) return false;
    return field == that.field && Objects.equals(value, that.value);
  }

  @Override
  public int hashCode() {
    return Objects.hash(field, value);
  }

  @Override
  public String toString() {
    return switch (field) {
      case NULL -> "DemoUnion[null]";
      case INT32_VALUE -> "DemoUnion[Int32Value=" + value + "]";
      case STRING_VALUE -> "DemoUnion[StringValue='" + value + "']";
      case DOUBLE_VALUE -> "DemoUnion[DoubleValue=" + value + "]";
    };
  }

  public static final class Codec extends GenericDataTypeCodec<DemoUnion> {

    @Override
    public Class<DemoUnion> getType() {
      return DemoUnion.class;
    }

    @Override
    public DemoUnion decodeType(EncodingContext context, UaDecoder decoder) {
      UInteger switchField = decoder.decodeUInt32("SwitchField");
      return switch (switchField.intValue()) {
        case 0 -> DemoUnion.ofNull();
        case 1 -> DemoUnion.ofInt32(decoder.decodeInt32("Int32Value"));
        case 2 -> DemoUnion.ofString(decoder.decodeString("StringValue"));
        case 3 -> DemoUnion.ofDouble(decoder.decodeDouble("DoubleValue"));
        default ->
            throw new UaSerializationException(
                StatusCodes.Bad_DecodingError, "unknown SwitchField in DemoUnion: " + switchField);
      };
    }

    @Override
    public void encodeType(EncodingContext context, UaEncoder encoder, DemoUnion value) {
      encoder.encodeUInt32("SwitchField", uint(value.field.ordinal()));
      switch (value.field) {
        case NULL -> {}
        case INT32_VALUE -> encoder.encodeInt32("Int32Value", (Integer) value.value);
        case STRING_VALUE -> encoder.encodeString("StringValue", (String) value.value);
        case DOUBLE_VALUE -> encoder.encodeDouble("DoubleValue", (Double) value.value);
      }
    }
  }
}

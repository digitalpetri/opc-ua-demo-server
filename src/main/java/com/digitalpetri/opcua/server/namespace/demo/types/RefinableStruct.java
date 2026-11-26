package com.digitalpetri.opcua.server.namespace.demo.types;

import static org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.Unsigned.uint;

import com.digitalpetri.opcua.server.namespace.demo.DemoNamespace;
import java.util.Objects;
import java.util.StringJoiner;
import org.eclipse.milo.opcua.sdk.core.ValueRanks;
import org.eclipse.milo.opcua.stack.core.NamespaceTable;
import org.eclipse.milo.opcua.stack.core.NodeIds;
import org.eclipse.milo.opcua.stack.core.StatusCodes;
import org.eclipse.milo.opcua.stack.core.UaSerializationException;
import org.eclipse.milo.opcua.stack.core.encoding.EncodingContext;
import org.eclipse.milo.opcua.stack.core.encoding.GenericDataTypeCodec;
import org.eclipse.milo.opcua.stack.core.encoding.UaDecoder;
import org.eclipse.milo.opcua.stack.core.encoding.UaEncoder;
import org.eclipse.milo.opcua.stack.core.types.UaStructuredType;
import org.eclipse.milo.opcua.stack.core.types.builtin.ExpandedNodeId;
import org.eclipse.milo.opcua.stack.core.types.builtin.LocalizedText;
import org.eclipse.milo.opcua.stack.core.types.builtin.NodeId;
import org.eclipse.milo.opcua.stack.core.types.builtin.Variant;
import org.eclipse.milo.opcua.stack.core.types.enumerated.StructureType;
import org.eclipse.milo.opcua.stack.core.types.structured.Structure;
import org.eclipse.milo.opcua.stack.core.types.structured.StructureDefinition;
import org.eclipse.milo.opcua.stack.core.types.structured.StructureField;
import org.jspecify.annotations.Nullable;

/**
 * A structure with optional fields and a {@code BaseDataType} reading field, used to demonstrate
 * {@code DataTypeRefinement} (OPC UA Part 3 §5.8.5.2).
 */
public final class RefinableStruct extends Structure implements UaStructuredType {

  public static final String TYPE_IDENTIFIER = "Demo.Types.RefinableStruct";
  public static final String BINARY_ENCODING_IDENTIFIER = TYPE_IDENTIFIER + ".DefaultBinary";

  public static final ExpandedNodeId TYPE_ID =
      ExpandedNodeId.of(DemoNamespace.NAMESPACE_URI, TYPE_IDENTIFIER);
  public static final ExpandedNodeId BINARY_ENCODING_ID =
      ExpandedNodeId.of(DemoNamespace.NAMESPACE_URI, BINARY_ENCODING_IDENTIFIER);

  private final @Nullable String label;
  private final @Nullable String optionalNote;
  private final @Nullable Boolean disabledFlag;
  private final Variant reading;

  public RefinableStruct(
      @Nullable String label,
      @Nullable String optionalNote,
      @Nullable Boolean disabledFlag,
      Variant reading) {

    this.label = label;
    this.optionalNote = optionalNote;
    this.disabledFlag = disabledFlag;
    this.reading = reading;
  }

  public @Nullable String getLabel() {
    return label;
  }

  public @Nullable String getOptionalNote() {
    return optionalNote;
  }

  public @Nullable Boolean getDisabledFlag() {
    return disabledFlag;
  }

  public Variant getReading() {
    return reading;
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
        NodeIds.Structure,
        StructureType.StructureWithOptionalFields,
        new StructureField[] {
          new StructureField(
              "Label",
              LocalizedText.english("Mandatory label"),
              NodeIds.String,
              ValueRanks.Scalar,
              null,
              uint(0),
              false),
          new StructureField(
              "OptionalNote",
              LocalizedText.english("Optional note promoted to mandatory by refinement"),
              NodeIds.String,
              ValueRanks.Scalar,
              null,
              uint(0),
              true),
          new StructureField(
              "DisabledFlag",
              LocalizedText.english("Optional flag disabled by refinement"),
              NodeIds.Boolean,
              ValueRanks.Scalar,
              null,
              uint(0),
              true),
          new StructureField(
              "Reading",
              LocalizedText.english("BaseDataType reading refined to Double in Celsius"),
              NodeIds.BaseDataType,
              ValueRanks.Scalar,
              null,
              uint(0),
              false)
        });
  }

  @Override
  public boolean equals(@Nullable Object o) {
    if (this == o) return true;
    if (!(o instanceof RefinableStruct that)) return false;
    return Objects.equals(label, that.label)
        && Objects.equals(optionalNote, that.optionalNote)
        && Objects.equals(disabledFlag, that.disabledFlag)
        && Objects.equals(reading, that.reading);
  }

  @Override
  public int hashCode() {
    return Objects.hash(label, optionalNote, disabledFlag, reading);
  }

  @Override
  public String toString() {
    return new StringJoiner(", ", RefinableStruct.class.getSimpleName() + "[", "]")
        .add("label='" + label + "'")
        .add("optionalNote='" + optionalNote + "'")
        .add("disabledFlag=" + disabledFlag)
        .add("reading=" + reading)
        .toString();
  }

  public static final class Codec extends GenericDataTypeCodec<RefinableStruct> {

    @Override
    public Class<RefinableStruct> getType() {
      return RefinableStruct.class;
    }

    @Override
    public RefinableStruct decodeType(EncodingContext context, UaDecoder decoder) {
      long encodingMask = decoder.decodeUInt32("EncodingMask").longValue();
      if ((encodingMask & ~3L) != 0L) {
        throw new UaSerializationException(
            StatusCodes.Bad_DecodingError,
            "Unexpected EncodingMask bits in RefinableStruct: 0x%x".formatted(encodingMask));
      }

      String label = decoder.decodeString("Label");

      String optionalNote =
          (encodingMask & (1L << 0)) != 0 ? decoder.decodeString("OptionalNote") : null;
      Boolean disabledFlag =
          (encodingMask & (1L << 1)) != 0 ? decoder.decodeBoolean("DisabledFlag") : null;

      Variant reading = decoder.decodeVariant("Reading");

      return new RefinableStruct(label, optionalNote, disabledFlag, reading);
    }

    @Override
    public void encodeType(EncodingContext context, UaEncoder encoder, RefinableStruct value) {
      long encodingMask = 0L;
      if (value.getOptionalNote() != null) {
        encodingMask |= 1L << 0;
      }
      if (value.getDisabledFlag() != null) {
        encodingMask |= 1L << 1;
      }

      encoder.encodeUInt32("EncodingMask", uint(encodingMask));
      encoder.encodeString("Label", value.getLabel());
      if (value.getOptionalNote() != null) {
        encoder.encodeString("OptionalNote", value.getOptionalNote());
      }
      if (value.getDisabledFlag() != null) {
        encoder.encodeBoolean("DisabledFlag", value.getDisabledFlag());
      }
      encoder.encodeVariant("Reading", value.getReading());
    }
  }
}

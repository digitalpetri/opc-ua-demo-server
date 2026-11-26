package com.digitalpetri.opcua.server.namespace.demo.editor;

import static org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.Unsigned.uint;

import com.digitalpetri.opcua.server.namespace.demo.DemoNamespace;
import java.util.Arrays;
import java.util.Objects;
import java.util.StringJoiner;
import org.eclipse.milo.opcua.sdk.core.ValueRanks;
import org.eclipse.milo.opcua.stack.core.NamespaceTable;
import org.eclipse.milo.opcua.stack.core.NodeIds;
import org.eclipse.milo.opcua.stack.core.encoding.EncodingContext;
import org.eclipse.milo.opcua.stack.core.encoding.GenericDataTypeCodec;
import org.eclipse.milo.opcua.stack.core.encoding.UaDecoder;
import org.eclipse.milo.opcua.stack.core.encoding.UaEncoder;
import org.eclipse.milo.opcua.stack.core.types.UaStructuredType;
import org.eclipse.milo.opcua.stack.core.types.builtin.ByteString;
import org.eclipse.milo.opcua.stack.core.types.builtin.ExpandedNodeId;
import org.eclipse.milo.opcua.stack.core.types.builtin.LocalizedText;
import org.eclipse.milo.opcua.stack.core.types.builtin.NodeId;
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.UInteger;
import org.eclipse.milo.opcua.stack.core.types.enumerated.StructureType;
import org.eclipse.milo.opcua.stack.core.types.structured.Structure;
import org.eclipse.milo.opcua.stack.core.types.structured.StructureDefinition;
import org.eclipse.milo.opcua.stack.core.types.structured.StructureField;
import org.jspecify.annotations.Nullable;

/**
 * A concrete structure exposing nullable built-in scalar and array fields for testing typed null
 * fields versus empty values.
 */
public final class NullableFieldsStruct extends Structure implements UaStructuredType {

  public static final String TYPE_IDENTIFIER = "Demo.ValueEditorTest.Types.NullableFieldsStruct";
  public static final String BINARY_ENCODING_IDENTIFIER = TYPE_IDENTIFIER + ".DefaultBinary";

  public static final ExpandedNodeId TYPE_ID =
      ExpandedNodeId.of(DemoNamespace.NAMESPACE_URI, TYPE_IDENTIFIER);
  public static final ExpandedNodeId BINARY_ENCODING_ID =
      ExpandedNodeId.of(DemoNamespace.NAMESPACE_URI, BINARY_ENCODING_IDENTIFIER);

  private final @Nullable String stringField;
  private final ByteString byteStringField;
  private final LocalizedText localizedTextField;
  private final NodeId nodeIdField;
  private final Integer @Nullable [] int32ArrayField;

  public NullableFieldsStruct(
      @Nullable String stringField,
      ByteString byteStringField,
      LocalizedText localizedTextField,
      NodeId nodeIdField,
      Integer @Nullable [] int32ArrayField) {

    this.stringField = stringField;
    this.byteStringField = byteStringField;
    this.localizedTextField = localizedTextField;
    this.nodeIdField = nodeIdField;
    this.int32ArrayField = int32ArrayField;
  }

  public @Nullable String getStringField() {
    return stringField;
  }

  public ByteString getByteStringField() {
    return byteStringField;
  }

  public LocalizedText getLocalizedTextField() {
    return localizedTextField;
  }

  public NodeId getNodeIdField() {
    return nodeIdField;
  }

  public Integer @Nullable [] getInt32ArrayField() {
    return int32ArrayField;
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
        StructureType.Structure,
        new StructureField[] {
          new StructureField(
              "StringField",
              LocalizedText.english("Nullable String field"),
              NodeIds.String,
              ValueRanks.Scalar,
              null,
              uint(0),
              false),
          new StructureField(
              "ByteStringField",
              LocalizedText.english("Nullable ByteString field"),
              NodeIds.ByteString,
              ValueRanks.Scalar,
              null,
              uint(0),
              false),
          new StructureField(
              "LocalizedTextField",
              LocalizedText.english("Nullable LocalizedText field"),
              NodeIds.LocalizedText,
              ValueRanks.Scalar,
              null,
              uint(0),
              false),
          new StructureField(
              "NodeIdField",
              LocalizedText.english("Nullable NodeId field"),
              NodeIds.NodeId,
              ValueRanks.Scalar,
              null,
              uint(0),
              false),
          new StructureField(
              "Int32ArrayField",
              LocalizedText.english("Nullable Int32 array field"),
              NodeIds.Int32,
              ValueRanks.OneDimension,
              new UInteger[] {uint(0)},
              uint(0),
              false)
        });
  }

  @Override
  public boolean equals(@Nullable Object o) {
    if (this == o) return true;
    if (!(o instanceof NullableFieldsStruct that)) return false;
    return Objects.equals(stringField, that.stringField)
        && Objects.equals(byteStringField, that.byteStringField)
        && Objects.equals(localizedTextField, that.localizedTextField)
        && Objects.equals(nodeIdField, that.nodeIdField)
        && Arrays.equals(int32ArrayField, that.int32ArrayField);
  }

  @Override
  public int hashCode() {
    int result = Objects.hash(stringField, byteStringField, localizedTextField, nodeIdField);
    return 31 * result + Arrays.hashCode(int32ArrayField);
  }

  @Override
  public String toString() {
    return new StringJoiner(", ", NullableFieldsStruct.class.getSimpleName() + "[", "]")
        .add("stringField=" + stringField)
        .add("byteStringField=" + byteStringField)
        .add("localizedTextField=" + localizedTextField)
        .add("nodeIdField=" + nodeIdField)
        .add("int32ArrayField=" + Arrays.toString(int32ArrayField))
        .toString();
  }

  public static final class Codec extends GenericDataTypeCodec<NullableFieldsStruct> {

    @Override
    public Class<NullableFieldsStruct> getType() {
      return NullableFieldsStruct.class;
    }

    @Override
    public NullableFieldsStruct decodeType(EncodingContext context, UaDecoder decoder) {
      String stringField = decoder.decodeString("StringField");
      ByteString byteStringField = decoder.decodeByteString("ByteStringField");
      LocalizedText localizedTextField = decoder.decodeLocalizedText("LocalizedTextField");
      NodeId nodeIdField = decoder.decodeNodeId("NodeIdField");
      Integer[] int32ArrayField = decoder.decodeInt32Array("Int32ArrayField");

      return new NullableFieldsStruct(
          stringField, byteStringField, localizedTextField, nodeIdField, int32ArrayField);
    }

    @Override
    public void encodeType(EncodingContext context, UaEncoder encoder, NullableFieldsStruct value) {
      encoder.encodeString("StringField", value.getStringField());
      encoder.encodeByteString("ByteStringField", value.getByteStringField());
      encoder.encodeLocalizedText("LocalizedTextField", value.getLocalizedTextField());
      encoder.encodeNodeId("NodeIdField", value.getNodeIdField());
      encoder.encodeInt32Array("Int32ArrayField", value.getInt32ArrayField());
    }
  }
}

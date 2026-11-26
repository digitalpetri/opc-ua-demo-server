package com.digitalpetri.opcua.server.namespace.demo.editor;

import static org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.Unsigned.uint;

import com.digitalpetri.opcua.server.namespace.demo.DemoNamespace;
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
import org.eclipse.milo.opcua.stack.core.types.builtin.ExpandedNodeId;
import org.eclipse.milo.opcua.stack.core.types.builtin.LocalizedText;
import org.eclipse.milo.opcua.stack.core.types.builtin.NodeId;
import org.eclipse.milo.opcua.stack.core.types.enumerated.StructureType;
import org.eclipse.milo.opcua.stack.core.types.structured.Structure;
import org.eclipse.milo.opcua.stack.core.types.structured.StructureDefinition;
import org.eclipse.milo.opcua.stack.core.types.structured.StructureField;
import org.jspecify.annotations.Nullable;

/**
 * A concrete base structure with a concrete subtype ({@link DerivedRecord}), used to exercise the
 * {@code NoSubDataTypes} bit of {@code AccessLevelEx}.
 */
public class BaseRecord extends Structure implements UaStructuredType {

  public static final String TYPE_IDENTIFIER = "Demo.ValueEditorTest.Types.BaseRecord";
  public static final String BINARY_ENCODING_IDENTIFIER = TYPE_IDENTIFIER + ".DefaultBinary";

  public static final ExpandedNodeId TYPE_ID =
      ExpandedNodeId.of(DemoNamespace.NAMESPACE_URI, TYPE_IDENTIFIER);
  public static final ExpandedNodeId BINARY_ENCODING_ID =
      ExpandedNodeId.of(DemoNamespace.NAMESPACE_URI, BINARY_ENCODING_IDENTIFIER);

  private final Integer id;
  private final @Nullable String label;

  public BaseRecord(Integer id, @Nullable String label) {
    this.id = id;
    this.label = label;
  }

  public Integer getId() {
    return id;
  }

  public @Nullable String getLabel() {
    return label;
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
              "Id",
              LocalizedText.english("Record identifier"),
              NodeIds.Int32,
              ValueRanks.Scalar,
              null,
              uint(0),
              false),
          new StructureField(
              "Label",
              LocalizedText.english("Record label"),
              NodeIds.String,
              ValueRanks.Scalar,
              null,
              uint(0),
              false)
        });
  }

  @Override
  public boolean equals(@Nullable Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    BaseRecord that = (BaseRecord) o;
    return Objects.equals(id, that.id) && Objects.equals(label, that.label);
  }

  @Override
  public int hashCode() {
    return Objects.hash(id, label);
  }

  @Override
  public String toString() {
    return new StringJoiner(", ", BaseRecord.class.getSimpleName() + "[", "]")
        .add("id=" + id)
        .add("label='" + label + "'")
        .toString();
  }

  public static final class Codec extends GenericDataTypeCodec<BaseRecord> {

    @Override
    public Class<BaseRecord> getType() {
      return BaseRecord.class;
    }

    @Override
    public BaseRecord decodeType(EncodingContext context, UaDecoder decoder) {
      Integer id = decoder.decodeInt32("Id");
      String label = decoder.decodeString("Label");
      return new BaseRecord(id, label);
    }

    @Override
    public void encodeType(EncodingContext context, UaEncoder encoder, BaseRecord value) {
      encoder.encodeInt32("Id", value.getId());
      encoder.encodeString("Label", value.getLabel());
    }
  }
}

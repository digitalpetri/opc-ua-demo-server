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
import org.eclipse.milo.opcua.stack.core.types.builtin.ExpandedNodeId;
import org.eclipse.milo.opcua.stack.core.types.builtin.LocalizedText;
import org.eclipse.milo.opcua.stack.core.types.builtin.NodeId;
import org.eclipse.milo.opcua.stack.core.types.enumerated.StructureType;
import org.eclipse.milo.opcua.stack.core.types.structured.StructureDefinition;
import org.eclipse.milo.opcua.stack.core.types.structured.StructureField;
import org.jspecify.annotations.Nullable;

/** Concrete subtype of {@link BaseRecord} adding a {@code Priority} field. */
public final class DerivedRecord extends BaseRecord {

  public static final String TYPE_IDENTIFIER = "Demo.ValueEditorTest.Types.DerivedRecord";
  public static final String BINARY_ENCODING_IDENTIFIER = TYPE_IDENTIFIER + ".DefaultBinary";

  public static final ExpandedNodeId TYPE_ID =
      ExpandedNodeId.of(DemoNamespace.NAMESPACE_URI, TYPE_IDENTIFIER);
  public static final ExpandedNodeId BINARY_ENCODING_ID =
      ExpandedNodeId.of(DemoNamespace.NAMESPACE_URI, BINARY_ENCODING_IDENTIFIER);

  private final Integer priority;

  public DerivedRecord(Integer id, @Nullable String label, Integer priority) {
    super(id, label);
    this.priority = priority;
  }

  public Integer getPriority() {
    return priority;
  }

  @Override
  public ExpandedNodeId getTypeId() {
    return TYPE_ID;
  }

  @Override
  public ExpandedNodeId getBinaryEncodingId() {
    return BINARY_ENCODING_ID;
  }

  public static StructureDefinition definition(NamespaceTable namespaceTable) {
    NodeId encodingId = BINARY_ENCODING_ID.toNodeId(namespaceTable).orElseThrow();
    NodeId baseTypeId = BaseRecord.TYPE_ID.toNodeId(namespaceTable).orElseThrow();

    return new StructureDefinition(
        encodingId,
        baseTypeId,
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
              false),
          new StructureField(
              "Priority",
              LocalizedText.english("Record priority"),
              NodeIds.Int32,
              ValueRanks.Scalar,
              null,
              uint(0),
              false)
        });
  }

  @Override
  public boolean equals(@Nullable Object o) {
    if (this == o) return true;
    if (!(o instanceof DerivedRecord that)) return false;
    return super.equals(o) && Objects.equals(priority, that.priority);
  }

  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(), priority);
  }

  @Override
  public String toString() {
    return new StringJoiner(", ", DerivedRecord.class.getSimpleName() + "[", "]")
        .add("id=" + getId())
        .add("label='" + getLabel() + "'")
        .add("priority=" + priority)
        .toString();
  }

  public static final class Codec extends GenericDataTypeCodec<DerivedRecord> {

    @Override
    public Class<DerivedRecord> getType() {
      return DerivedRecord.class;
    }

    @Override
    public DerivedRecord decodeType(EncodingContext context, UaDecoder decoder) {
      Integer id = decoder.decodeInt32("Id");
      String label = decoder.decodeString("Label");
      Integer priority = decoder.decodeInt32("Priority");
      return new DerivedRecord(id, label, priority);
    }

    @Override
    public void encodeType(EncodingContext context, UaEncoder encoder, DerivedRecord value) {
      encoder.encodeInt32("Id", value.getId());
      encoder.encodeString("Label", value.getLabel());
      encoder.encodeInt32("Priority", value.getPriority());
    }
  }
}

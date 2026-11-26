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

/** A concrete structure containing a {@link DemoOptionSet} field. */
public final class StructWithOptionSet extends Structure implements UaStructuredType {

  public static final String TYPE_IDENTIFIER = "Demo.ValueEditorTest.Types.StructWithOptionSet";
  public static final String BINARY_ENCODING_IDENTIFIER = TYPE_IDENTIFIER + ".DefaultBinary";

  public static final ExpandedNodeId TYPE_ID =
      ExpandedNodeId.of(DemoNamespace.NAMESPACE_URI, TYPE_IDENTIFIER);
  public static final ExpandedNodeId BINARY_ENCODING_ID =
      ExpandedNodeId.of(DemoNamespace.NAMESPACE_URI, BINARY_ENCODING_IDENTIFIER);

  private final @Nullable String name;
  private final DemoOptionSet options;

  public StructWithOptionSet(@Nullable String name, DemoOptionSet options) {
    this.name = name;
    this.options = options;
  }

  public @Nullable String getName() {
    return name;
  }

  public DemoOptionSet getOptions() {
    return options;
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
    NodeId optionSetTypeId = DemoOptionSet.TYPE_ID.toNodeId(namespaceTable).orElseThrow();

    return new StructureDefinition(
        encodingId,
        NodeIds.Structure,
        StructureType.Structure,
        new StructureField[] {
          new StructureField(
              "Name",
              LocalizedText.english("Display name for this profile"),
              NodeIds.String,
              ValueRanks.Scalar,
              null,
              uint(0),
              false),
          new StructureField(
              "Options",
              LocalizedText.english("OptionSet bitmask field"),
              optionSetTypeId,
              ValueRanks.Scalar,
              null,
              uint(0),
              false)
        });
  }

  @Override
  public boolean equals(@Nullable Object o) {
    if (this == o) return true;
    if (!(o instanceof StructWithOptionSet that)) return false;
    return Objects.equals(name, that.name) && Objects.equals(options, that.options);
  }

  @Override
  public int hashCode() {
    return Objects.hash(name, options);
  }

  @Override
  public String toString() {
    return new StringJoiner(", ", StructWithOptionSet.class.getSimpleName() + "[", "]")
        .add("name='" + name + "'")
        .add("options=" + options)
        .toString();
  }

  public static final class Codec extends GenericDataTypeCodec<StructWithOptionSet> {

    @Override
    public Class<StructWithOptionSet> getType() {
      return StructWithOptionSet.class;
    }

    @Override
    public StructWithOptionSet decodeType(EncodingContext context, UaDecoder decoder) {
      String name = decoder.decodeString("Name");
      DemoOptionSet options =
          (DemoOptionSet) decoder.decodeStruct("Options", DemoOptionSet.TYPE_ID);
      return new StructWithOptionSet(name, options);
    }

    @Override
    public void encodeType(EncodingContext context, UaEncoder encoder, StructWithOptionSet value) {
      encoder.encodeString("Name", value.getName());
      encoder.encodeStruct("Options", value.getOptions(), DemoOptionSet.TYPE_ID);
    }
  }
}

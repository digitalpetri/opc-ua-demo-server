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
 * A self-referential structure with an optional {@code Child} field, used to build deeply nested
 * values for scale testing.
 */
public final class NestedNode extends Structure implements UaStructuredType {

  public static final String TYPE_IDENTIFIER = "Demo.ValueEditorTest.Types.NestedNode";
  public static final String BINARY_ENCODING_IDENTIFIER = TYPE_IDENTIFIER + ".DefaultBinary";

  public static final ExpandedNodeId TYPE_ID =
      ExpandedNodeId.of(DemoNamespace.NAMESPACE_URI, TYPE_IDENTIFIER);
  public static final ExpandedNodeId BINARY_ENCODING_ID =
      ExpandedNodeId.of(DemoNamespace.NAMESPACE_URI, BINARY_ENCODING_IDENTIFIER);

  private final Integer depth;
  private final @Nullable String label;
  private final @Nullable NestedNode child;

  public NestedNode(Integer depth, @Nullable String label, @Nullable NestedNode child) {
    this.depth = depth;
    this.label = label;
    this.child = child;
  }

  /**
   * Builds a chain of {@code levels} nested nodes numbered {@code 1..levels}.
   *
   * @param levels the number of levels to build, at least {@code 1}.
   * @return the root {@link NestedNode} at depth {@code 1}.
   */
  public static NestedNode chain(int levels) {
    if (levels < 1) {
      throw new IllegalArgumentException("levels must be >= 1: " + levels);
    }

    NestedNode current = null;
    for (int level = levels; level >= 1; level--) {
      current = new NestedNode(level, "Level-" + level, current);
    }
    return Objects.requireNonNull(current);
  }

  public Integer getDepth() {
    return depth;
  }

  public @Nullable String getLabel() {
    return label;
  }

  public @Nullable NestedNode getChild() {
    return child;
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
    NodeId selfTypeId = TYPE_ID.toNodeId(namespaceTable).orElseThrow();

    return new StructureDefinition(
        encodingId,
        NodeIds.Structure,
        StructureType.StructureWithOptionalFields,
        new StructureField[] {
          new StructureField(
              "Depth",
              LocalizedText.english("1-based nesting depth"),
              NodeIds.Int32,
              ValueRanks.Scalar,
              null,
              uint(0),
              false),
          new StructureField(
              "Label",
              LocalizedText.english("Level label"),
              NodeIds.String,
              ValueRanks.Scalar,
              null,
              uint(0),
              false),
          new StructureField(
              "Child",
              LocalizedText.english("Optional nested child node"),
              selfTypeId,
              ValueRanks.Scalar,
              null,
              uint(0),
              true)
        });
  }

  @Override
  public boolean equals(@Nullable Object o) {
    if (this == o) return true;
    if (!(o instanceof NestedNode that)) return false;
    return Objects.equals(depth, that.depth)
        && Objects.equals(label, that.label)
        && Objects.equals(child, that.child);
  }

  @Override
  public int hashCode() {
    return Objects.hash(depth, label, child);
  }

  @Override
  public String toString() {
    return new StringJoiner(", ", NestedNode.class.getSimpleName() + "[", "]")
        .add("depth=" + depth)
        .add("label='" + label + "'")
        .add("hasChild=" + (child != null))
        .toString();
  }

  public static final class Codec extends GenericDataTypeCodec<NestedNode> {

    @Override
    public Class<NestedNode> getType() {
      return NestedNode.class;
    }

    @Override
    public NestedNode decodeType(EncodingContext context, UaDecoder decoder) {
      long encodingMask = decoder.decodeUInt32("EncodingMask").longValue();
      Integer depth = decoder.decodeInt32("Depth");
      String label = decoder.decodeString("Label");
      NestedNode child =
          (encodingMask & 1L) != 0 ? (NestedNode) decoder.decodeStruct("Child", TYPE_ID) : null;

      return new NestedNode(depth, label, child);
    }

    @Override
    public void encodeType(EncodingContext context, UaEncoder encoder, NestedNode value) {
      long encodingMask = value.getChild() != null ? 1L : 0L;

      encoder.encodeUInt32("EncodingMask", uint(encodingMask));
      encoder.encodeInt32("Depth", value.getDepth());
      encoder.encodeString("Label", value.getLabel());
      if (value.getChild() != null) {
        encoder.encodeStruct("Child", value.getChild(), TYPE_ID);
      }
    }
  }
}

package com.digitalpetri.opcua.server.namespace.demo.types;

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
 * A compact server-defined structure used in {@code Demo/Values/Structure} and as input/output for
 * the {@code EchoRecord} and {@code GetRecords} methods under {@code Demo/Methods}.
 */
public final class DemoRecord extends Structure implements UaStructuredType {

  public static final String TYPE_IDENTIFIER = "Demo.Types.DemoRecord";
  public static final String BINARY_ENCODING_IDENTIFIER = TYPE_IDENTIFIER + ".DefaultBinary";

  public static final ExpandedNodeId TYPE_ID =
      ExpandedNodeId.of(DemoNamespace.NAMESPACE_URI, TYPE_IDENTIFIER);
  public static final ExpandedNodeId BINARY_ENCODING_ID =
      ExpandedNodeId.of(DemoNamespace.NAMESPACE_URI, BINARY_ENCODING_IDENTIFIER);

  private final Integer id;
  private final @Nullable String name;
  private final Double value;
  private final Boolean enabled;

  public DemoRecord(Integer id, @Nullable String name, Double value, Boolean enabled) {
    this.id = id;
    this.name = name;
    this.value = value;
    this.enabled = enabled;
  }

  public Integer getId() {
    return id;
  }

  public @Nullable String getName() {
    return name;
  }

  public Double getValue() {
    return value;
  }

  public Boolean getEnabled() {
    return enabled;
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
              "Name",
              LocalizedText.english("Record name"),
              NodeIds.String,
              ValueRanks.Scalar,
              null,
              uint(0),
              false),
          new StructureField(
              "Value",
              LocalizedText.english("Numeric reading"),
              NodeIds.Double,
              ValueRanks.Scalar,
              null,
              uint(0),
              false),
          new StructureField(
              "Enabled",
              LocalizedText.english("Whether the record is active"),
              NodeIds.Boolean,
              ValueRanks.Scalar,
              null,
              uint(0),
              false)
        });
  }

  @Override
  public boolean equals(@Nullable Object o) {
    if (this == o) return true;
    if (!(o instanceof DemoRecord that)) return false;
    return Objects.equals(id, that.id)
        && Objects.equals(name, that.name)
        && Objects.equals(value, that.value)
        && Objects.equals(enabled, that.enabled);
  }

  @Override
  public int hashCode() {
    return Objects.hash(id, name, value, enabled);
  }

  @Override
  public String toString() {
    return new StringJoiner(", ", DemoRecord.class.getSimpleName() + "[", "]")
        .add("id=" + id)
        .add("name='" + name + "'")
        .add("value=" + value)
        .add("enabled=" + enabled)
        .toString();
  }

  public static final class Codec extends GenericDataTypeCodec<DemoRecord> {

    @Override
    public Class<DemoRecord> getType() {
      return DemoRecord.class;
    }

    @Override
    public DemoRecord decodeType(EncodingContext context, UaDecoder decoder) {
      Integer id = decoder.decodeInt32("Id");
      String name = decoder.decodeString("Name");
      Double value = decoder.decodeDouble("Value");
      Boolean enabled = decoder.decodeBoolean("Enabled");
      return new DemoRecord(id, name, value, enabled);
    }

    @Override
    public void encodeType(EncodingContext context, UaEncoder encoder, DemoRecord value) {
      encoder.encodeInt32("Id", value.getId());
      encoder.encodeString("Name", value.getName());
      encoder.encodeDouble("Value", value.getValue());
      encoder.encodeBoolean("Enabled", value.getEnabled());
    }
  }
}

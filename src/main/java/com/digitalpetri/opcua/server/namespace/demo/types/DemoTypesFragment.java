package com.digitalpetri.opcua.server.namespace.demo.types;

import static org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.Unsigned.uint;

import com.digitalpetri.opcua.server.namespace.demo.DemoAddressSpaceFragment;
import com.digitalpetri.opcua.server.namespace.demo.DemoNamespace;
import com.digitalpetri.opcua.server.namespace.demo.DemoValueFilters;
import java.util.Set;
import org.eclipse.milo.opcua.sdk.core.Reference;
import org.eclipse.milo.opcua.sdk.core.Reference.Direction;
import org.eclipse.milo.opcua.sdk.server.AddressSpaceFilter;
import org.eclipse.milo.opcua.sdk.server.OpcUaServer;
import org.eclipse.milo.opcua.sdk.server.SimpleAddressSpaceFilter;
import org.eclipse.milo.opcua.sdk.server.nodes.UaDataTypeNode;
import org.eclipse.milo.opcua.sdk.server.nodes.UaObjectNode;
import org.eclipse.milo.opcua.stack.core.NamespaceTable;
import org.eclipse.milo.opcua.stack.core.NodeIds;
import org.eclipse.milo.opcua.stack.core.ReferenceTypes;
import org.eclipse.milo.opcua.stack.core.encoding.DataTypeCodec;
import org.eclipse.milo.opcua.stack.core.types.DataTypeEncoding;
import org.eclipse.milo.opcua.stack.core.types.builtin.LocalizedText;
import org.eclipse.milo.opcua.stack.core.types.builtin.NodeId;
import org.eclipse.milo.opcua.stack.core.types.builtin.QualifiedName;
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.UByte;
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.UShort;
import org.eclipse.milo.opcua.stack.core.types.structured.DataTypeDefinition;
import org.eclipse.milo.opcua.stack.core.types.structured.EnumDefinition;
import org.eclipse.milo.opcua.stack.core.types.structured.EnumField;
import org.eclipse.milo.opcua.stack.core.types.structured.EnumValueType;
import org.jspecify.annotations.Nullable;

/**
 * Registers the Demo namespace's custom {@code DataType} nodes, binary encoding nodes, and codecs
 * under {@code ns=2;s=Demo.Types.<Name>}, then refreshes the server's {@code DataTypeTree}.
 *
 * <p>Must start before {@code DemoValuesFragment}, {@code DemoConstraintsFragment}, and {@code
 * DemoMethodsFragment} so every custom type is visible in the {@code DataTypeTree} and {@code
 * DataTypeManager} before variables and methods reference them.
 */
public final class DemoTypesFragment extends DemoAddressSpaceFragment {

  public static final String TYPES_PREFIX = "Demo.Types.";

  public static final String DEMO_OPTION_SET_UINT32_IDENTIFIER =
      TYPES_PREFIX + "DemoOptionSetUInt32";
  public static final String OPAQUE_STRUCT_IDENTIFIER = TYPES_PREFIX + "OpaqueStruct";
  public static final String OPAQUE_STRUCT_ENCODING_IDENTIFIER =
      OPAQUE_STRUCT_IDENTIFIER + ".DefaultBinary";
  public static final String UNKNOWN_ENCODING_IDENTIFIER = TYPES_PREFIX + "UnknownEncoding";
  public static final String SPARSE_STATUS_ENUM_IDENTIFIER = TYPES_PREFIX + "SparseStatusEnum";
  public static final String OPERATIONAL_STATUS_ENUM_IDENTIFIER =
      TYPES_PREFIX + "OperationalStatusEnum";

  public static final Set<Integer> SPARSE_ENUM_VALUES = Set.of(1, 2, 4, 8, 16);
  public static final Set<Integer> OPERATIONAL_ENUM_VALUES = Set.of(1, 2, 4);

  private final UShort namespaceIndex;
  private final SimpleAddressSpaceFilter filter;

  public DemoTypesFragment(OpcUaServer server, DemoNamespace namespace) {
    super(server, namespace);

    this.namespaceIndex = namespace.getNamespaceIndex();
    this.filter = SimpleAddressSpaceFilter.create(getNodeManager()::containsNode);

    getLifecycleManager().addStartupTask(this::registerTypes);
  }

  @Override
  public AddressSpaceFilter getFilter() {
    return filter;
  }

  private void registerTypes() {
    NamespaceTable nsTable = getServer().getNamespaceTable();

    registerDemoOptionSetType(nsTable);
    registerStructWithOptionSetType(nsTable);
    registerDemoOptionSetUInt32Type();
    registerOpaqueStructType();
    registerNullableFieldsStructType(nsTable);
    registerRefinableStructType(nsTable);
    registerNestedNodeType(nsTable);
    registerDemoRecordType(nsTable);

    NodeId sparseStatusEnumId = registerSparseStatusEnumType();
    registerOperationalStatusEnumType(sparseStatusEnumId);

    // Refresh DataTypeTree so AttributeWriter and AbstractMethodInvocationHandler see all new
    // DataType subtype chains immediately.
    getServer().updateDataTypeTree();
  }

  private void registerDemoOptionSetType(NamespaceTable nsTable) {
    NodeId typeId = DemoOptionSet.TYPE_ID.toNodeId(nsTable).orElseThrow();
    NodeId encodingId = DemoOptionSet.BINARY_ENCODING_ID.toNodeId(nsTable).orElseThrow();

    UaDataTypeNode typeNode =
        addDataTypeNode(
            typeId,
            "DemoOptionSet",
            "Concrete 2-byte OptionSet subtype with defined bits 0-3 and 8-9",
            NodeIds.OptionSet,
            DemoOptionSet.definition());
    typeNode.setOptionSetValues(DemoOptionSet.optionSetValues());
    typeNode.setProperty(DemoValueFilters.OPTION_SET_LENGTH, DemoOptionSet.OPTION_SET_LENGTH);

    addBinaryEncodingNode(typeId, encodingId);
    registerCodec(typeId, encodingId, new DemoOptionSet.Codec());
  }

  private void registerStructWithOptionSetType(NamespaceTable nsTable) {
    NodeId typeId = StructWithOptionSet.TYPE_ID.toNodeId(nsTable).orElseThrow();
    NodeId encodingId = StructWithOptionSet.BINARY_ENCODING_ID.toNodeId(nsTable).orElseThrow();

    addDataTypeNode(
        typeId,
        "StructWithOptionSet",
        "Structure carrying a DemoOptionSet field",
        NodeIds.Structure,
        StructWithOptionSet.definition(nsTable));

    addBinaryEncodingNode(typeId, encodingId);
    registerCodec(typeId, encodingId, new StructWithOptionSet.Codec());
  }

  private void registerDemoOptionSetUInt32Type() {
    NodeId typeId = new NodeId(namespaceIndex, DEMO_OPTION_SET_UINT32_IDENTIFIER);

    EnumDefinition definition =
        new EnumDefinition(
            new EnumField[] {
              new EnumField(
                  0L,
                  LocalizedText.english("Read"),
                  LocalizedText.english("Read bit (bit 0)"),
                  "Read"),
              new EnumField(
                  1L,
                  LocalizedText.english("Write"),
                  LocalizedText.english("Write bit (bit 1)"),
                  "Write"),
              new EnumField(
                  3L,
                  LocalizedText.english("Execute"),
                  LocalizedText.english("Execute bit (bit 3, after gap at bit 2)"),
                  "Execute"),
              new EnumField(
                  4L,
                  LocalizedText.english("Admin"),
                  LocalizedText.english("Admin bit (bit 4)"),
                  "Admin")
            });

    UaDataTypeNode typeNode =
        addDataTypeNode(
            typeId,
            "DemoOptionSetUInt32",
            "UInt32 bitmask OptionSet with defined bits 0, 1, 3, 4 and a gap at bit 2",
            NodeIds.UInt32,
            definition);

    typeNode.setOptionSetValues(
        new LocalizedText[] {
          LocalizedText.english("Read"),
          LocalizedText.english("Write"),
          LocalizedText.NULL_VALUE,
          LocalizedText.english("Execute"),
          LocalizedText.english("Admin")
        });
  }

  private void registerOpaqueStructType() {
    NodeId typeId = new NodeId(namespaceIndex, OPAQUE_STRUCT_IDENTIFIER);
    NodeId encodingId = new NodeId(namespaceIndex, OPAQUE_STRUCT_ENCODING_IDENTIFIER);

    // Intentionally omit DataTypeDefinition so clients cannot learn the field layout.
    addDataTypeNode(
        typeId,
        "OpaqueStruct",
        "Concrete Structure subtype with no DataTypeDefinition attribute",
        NodeIds.Structure,
        null);

    addBinaryEncodingNode(typeId, encodingId);
  }

  private void registerNullableFieldsStructType(NamespaceTable nsTable) {
    NodeId typeId = NullableFieldsStruct.TYPE_ID.toNodeId(nsTable).orElseThrow();
    NodeId encodingId = NullableFieldsStruct.BINARY_ENCODING_ID.toNodeId(nsTable).orElseThrow();

    addDataTypeNode(
        typeId,
        "NullableFieldsStruct",
        "Structure with nullable String, ByteString, LocalizedText, NodeId, and Int32[] fields",
        NodeIds.Structure,
        NullableFieldsStruct.definition(nsTable));

    addBinaryEncodingNode(typeId, encodingId);
    registerCodec(typeId, encodingId, new NullableFieldsStruct.Codec());
  }

  private void registerRefinableStructType(NamespaceTable nsTable) {
    NodeId typeId = RefinableStruct.TYPE_ID.toNodeId(nsTable).orElseThrow();
    NodeId encodingId = RefinableStruct.BINARY_ENCODING_ID.toNodeId(nsTable).orElseThrow();

    addDataTypeNode(
        typeId,
        "RefinableStruct",
        "StructureWithOptionalFields refined by RefinableStructProfile",
        NodeIds.Structure,
        RefinableStruct.definition(nsTable));

    addBinaryEncodingNode(typeId, encodingId);
    registerCodec(typeId, encodingId, new RefinableStruct.Codec());
  }

  private void registerNestedNodeType(NamespaceTable nsTable) {
    NodeId typeId = NestedNode.TYPE_ID.toNodeId(nsTable).orElseThrow();
    NodeId encodingId = NestedNode.BINARY_ENCODING_ID.toNodeId(nsTable).orElseThrow();

    addDataTypeNode(
        typeId,
        "NestedNode",
        "Self-referential StructureWithOptionalFields for deep-nesting tests",
        NodeIds.Structure,
        NestedNode.definition(nsTable));

    addBinaryEncodingNode(typeId, encodingId);
    registerCodec(typeId, encodingId, new NestedNode.Codec());
  }

  private void registerDemoRecordType(NamespaceTable nsTable) {
    NodeId typeId = DemoRecord.TYPE_ID.toNodeId(nsTable).orElseThrow();
    NodeId encodingId = DemoRecord.BINARY_ENCODING_ID.toNodeId(nsTable).orElseThrow();

    addDataTypeNode(
        typeId,
        "DemoRecord",
        "Compact 4-field Structure used in Demo/Values/Structure and Demo/Methods",
        NodeIds.Structure,
        DemoRecord.definition(nsTable));

    addBinaryEncodingNode(typeId, encodingId);
    registerCodec(typeId, encodingId, new DemoRecord.Codec());
  }

  private NodeId registerSparseStatusEnumType() {
    NodeId typeId = new NodeId(namespaceIndex, SPARSE_STATUS_ENUM_IDENTIFIER);

    EnumValueType[] enumValues = {
      new EnumValueType(1L, LocalizedText.english("Idle"), LocalizedText.english("Equipment idle")),
      new EnumValueType(
          2L, LocalizedText.english("Running"), LocalizedText.english("Equipment running")),
      new EnumValueType(
          4L, LocalizedText.english("Paused"), LocalizedText.english("Equipment paused")),
      new EnumValueType(
          8L, LocalizedText.english("Faulted"), LocalizedText.english("Equipment faulted")),
      new EnumValueType(
          16L,
          LocalizedText.english("Maintenance"),
          LocalizedText.english("Equipment in maintenance"))
    };

    EnumDefinition definition =
        new EnumDefinition(
            new EnumField[] {
              new EnumField(
                  1L,
                  LocalizedText.english("Idle"),
                  LocalizedText.english("Equipment idle"),
                  "Idle"),
              new EnumField(
                  2L,
                  LocalizedText.english("Running"),
                  LocalizedText.english("Equipment running"),
                  "Running"),
              new EnumField(
                  4L,
                  LocalizedText.english("Paused"),
                  LocalizedText.english("Equipment paused"),
                  "Paused"),
              new EnumField(
                  8L,
                  LocalizedText.english("Faulted"),
                  LocalizedText.english("Equipment faulted"),
                  "Faulted"),
              new EnumField(
                  16L,
                  LocalizedText.english("Maintenance"),
                  LocalizedText.english("Equipment in maintenance"),
                  "Maintenance")
            });

    UaDataTypeNode typeNode =
        addDataTypeNode(
            typeId,
            "SparseStatusEnum",
            "Enumeration with non-contiguous values (1, 2, 4, 8, 16) via EnumValues",
            NodeIds.Enumeration,
            definition);
    typeNode.setEnumValues(enumValues);
    return typeId;
  }

  private void registerOperationalStatusEnumType(NodeId sparseStatusEnumId) {
    NodeId typeId = new NodeId(namespaceIndex, OPERATIONAL_STATUS_ENUM_IDENTIFIER);

    EnumValueType[] enumValues = {
      new EnumValueType(1L, LocalizedText.english("Idle"), LocalizedText.english("Equipment idle")),
      new EnumValueType(
          2L, LocalizedText.english("Running"), LocalizedText.english("Equipment running")),
      new EnumValueType(
          4L, LocalizedText.english("Paused"), LocalizedText.english("Equipment paused"))
    };

    EnumDefinition definition =
        new EnumDefinition(
            new EnumField[] {
              new EnumField(
                  1L,
                  LocalizedText.english("Idle"),
                  LocalizedText.english("Equipment idle"),
                  "Idle"),
              new EnumField(
                  2L,
                  LocalizedText.english("Running"),
                  LocalizedText.english("Equipment running"),
                  "Running"),
              new EnumField(
                  4L,
                  LocalizedText.english("Paused"),
                  LocalizedText.english("Equipment paused"),
                  "Paused")
            });

    UaDataTypeNode typeNode =
        addDataTypeNode(
            typeId,
            "OperationalStatusEnum",
            "Subtype of SparseStatusEnum restricting allowed values to (1, 2, 4)",
            sparseStatusEnumId,
            definition);
    typeNode.setEnumValues(enumValues);
  }

  private UaDataTypeNode addDataTypeNode(
      NodeId typeId,
      String name,
      String description,
      NodeId superTypeId,
      @Nullable DataTypeDefinition definition) {

    UaDataTypeNode typeNode =
        new UaDataTypeNode(
            getNodeContext(),
            typeId,
            new QualifiedName(namespaceIndex, name),
            LocalizedText.english(name),
            LocalizedText.english(description),
            uint(0),
            uint(0),
            null,
            null,
            null,
            false,
            definition);

    getNodeManager().addNode(typeNode);
    typeNode.addReference(
        new Reference(
            typeId, ReferenceTypes.HasSubtype, superTypeId.expanded(), Direction.INVERSE));

    return typeNode;
  }

  private void addBinaryEncodingNode(NodeId dataTypeId, NodeId encodingId) {
    UaObjectNode encodingNode =
        new UaObjectNode(
            getNodeContext(),
            encodingId,
            DataTypeEncoding.BINARY_ENCODING_NAME,
            LocalizedText.english("Default Binary"),
            LocalizedText.english("Default binary encoding"),
            uint(0),
            uint(0),
            UByte.MIN);

    getNodeManager().addNode(encodingNode);
    encodingNode.addReference(
        new Reference(
            encodingId,
            ReferenceTypes.HasTypeDefinition,
            NodeIds.DataTypeEncodingType.expanded(),
            Direction.FORWARD));
    encodingNode.addReference(
        new Reference(encodingId, NodeIds.HasEncoding, dataTypeId.expanded(), Direction.INVERSE));
  }

  private void registerCodec(NodeId typeId, NodeId binaryEncodingId, DataTypeCodec codec) {
    getServer()
        .getStaticDataTypeManager()
        .registerType(typeId, codec, binaryEncodingId, null, null);
    getServer()
        .getDynamicDataTypeManager()
        .registerType(typeId, codec, binaryEncodingId, null, null);
  }
}

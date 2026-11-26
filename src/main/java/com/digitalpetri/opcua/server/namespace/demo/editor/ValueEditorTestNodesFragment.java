package com.digitalpetri.opcua.server.namespace.demo.editor;

import static com.digitalpetri.opcua.server.DemoConfigLimits.MIN_SUPPORTED_SAMPLE_RATE;
import static com.digitalpetri.opcua.server.namespace.demo.Util.deriveChildNodeId;
import static org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.Unsigned.uint;

import com.digitalpetri.opcua.server.namespace.demo.DemoAddressSpaceFragment;
import com.digitalpetri.opcua.server.namespace.demo.DemoNamespace;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.eclipse.milo.opcua.sdk.core.AccessLevel;
import org.eclipse.milo.opcua.sdk.core.CefactEngineeringUnits;
import org.eclipse.milo.opcua.sdk.core.Reference;
import org.eclipse.milo.opcua.sdk.core.Reference.Direction;
import org.eclipse.milo.opcua.sdk.core.ValueRanks;
import org.eclipse.milo.opcua.sdk.server.AddressSpaceFilter;
import org.eclipse.milo.opcua.sdk.server.OpcUaServer;
import org.eclipse.milo.opcua.sdk.server.SimpleAddressSpaceFilter;
import org.eclipse.milo.opcua.sdk.server.model.objects.DataTypeRefinementTypeNode;
import org.eclipse.milo.opcua.sdk.server.model.objects.SubtypeRestrictionTypeNode;
import org.eclipse.milo.opcua.sdk.server.model.variables.AnalogUnitTypeNode;
import org.eclipse.milo.opcua.sdk.server.nodes.UaDataTypeNode;
import org.eclipse.milo.opcua.sdk.server.nodes.UaFolderNode;
import org.eclipse.milo.opcua.sdk.server.nodes.UaNode;
import org.eclipse.milo.opcua.sdk.server.nodes.UaObjectNode;
import org.eclipse.milo.opcua.sdk.server.nodes.UaServerNode;
import org.eclipse.milo.opcua.sdk.server.nodes.UaVariableNode;
import org.eclipse.milo.opcua.sdk.server.nodes.UaVariableNode.UaVariableNodeBuilder;
import org.eclipse.milo.opcua.sdk.server.nodes.instantiation.InstantiationRequest;
import org.eclipse.milo.opcua.stack.core.AttributeId;
import org.eclipse.milo.opcua.stack.core.Diagnostic;
import org.eclipse.milo.opcua.stack.core.NamespaceTable;
import org.eclipse.milo.opcua.stack.core.NodeIds;
import org.eclipse.milo.opcua.stack.core.ReferenceTypes;
import org.eclipse.milo.opcua.stack.core.StatusCodes;
import org.eclipse.milo.opcua.stack.core.UaException;
import org.eclipse.milo.opcua.stack.core.encoding.DataTypeCodec;
import org.eclipse.milo.opcua.stack.core.encoding.EncodingContext;
import org.eclipse.milo.opcua.stack.core.types.DataTypeEncoding;
import org.eclipse.milo.opcua.stack.core.types.builtin.ByteString;
import org.eclipse.milo.opcua.stack.core.types.builtin.DataValue;
import org.eclipse.milo.opcua.stack.core.types.builtin.DateTime;
import org.eclipse.milo.opcua.stack.core.types.builtin.ExpandedNodeId;
import org.eclipse.milo.opcua.stack.core.types.builtin.ExtensionObject;
import org.eclipse.milo.opcua.stack.core.types.builtin.LocalizedText;
import org.eclipse.milo.opcua.stack.core.types.builtin.Matrix;
import org.eclipse.milo.opcua.stack.core.types.builtin.NodeId;
import org.eclipse.milo.opcua.stack.core.types.builtin.QualifiedName;
import org.eclipse.milo.opcua.stack.core.types.builtin.StatusCode;
import org.eclipse.milo.opcua.stack.core.types.builtin.Variant;
import org.eclipse.milo.opcua.stack.core.types.builtin.XmlElement;
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.UByte;
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.UInteger;
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.UShort;
import org.eclipse.milo.opcua.stack.core.types.structured.AccessLevelExType;
import org.eclipse.milo.opcua.stack.core.types.structured.DataTypeDefinition;
import org.eclipse.milo.opcua.stack.core.types.structured.EnumDefinition;
import org.eclipse.milo.opcua.stack.core.types.structured.EnumField;
import org.eclipse.milo.opcua.stack.core.types.structured.EnumValueType;
import org.eclipse.milo.opcua.stack.core.types.structured.WriteValue;
import org.jspecify.annotations.Nullable;

/**
 * Address-space fragment hosting {@code Demo/ValueEditorTest}, providing OPC UA value shapes,
 * variable restrictions, scale fixtures, and callable methods for client value-editor testing.
 */
public class ValueEditorTestNodesFragment extends DemoAddressSpaceFragment {

  public static final String ROOT_IDENTIFIER = "Demo.ValueEditorTest";
  public static final String TYPES_PREFIX = ROOT_IDENTIFIER + ".Types.";

  public static final String DEMO_OPTION_SET_UINT32_IDENTIFIER =
      TYPES_PREFIX + "DemoOptionSetUInt32";
  public static final String OPAQUE_STRUCT_IDENTIFIER = TYPES_PREFIX + "OpaqueStruct";
  public static final String OPAQUE_STRUCT_ENCODING_IDENTIFIER =
      OPAQUE_STRUCT_IDENTIFIER + ".DefaultBinary";
  public static final String UNKNOWN_ENCODING_IDENTIFIER = TYPES_PREFIX + "UnknownEncoding";
  public static final String SPARSE_STATUS_ENUM_IDENTIFIER = TYPES_PREFIX + "SparseStatusEnum";
  public static final String OPERATIONAL_STATUS_ENUM_IDENTIFIER =
      TYPES_PREFIX + "OperationalStatusEnum";

  public static final int LARGE_ARRAY_LENGTH = 100_000;
  public static final int NESTED_CHAIN_DEPTH = 25;
  public static final int REFINED_OPTIONAL_NOTE_MAX_BYTES = 32;

  private static final Set<Integer> SPARSE_ENUM_VALUES = Set.of(1, 2, 4, 8, 16);
  private static final Set<Integer> OPERATIONAL_ENUM_VALUES = Set.of(1, 2, 4);

  private final DemoNamespace namespace;
  private final UShort namespaceIndex;
  private final SimpleAddressSpaceFilter filter;

  public ValueEditorTestNodesFragment(OpcUaServer server, DemoNamespace namespace) {
    super(server, namespace);

    this.namespace = namespace;
    this.namespaceIndex = namespace.getNamespaceIndex();
    this.filter = SimpleAddressSpaceFilter.create(getNodeManager()::containsNode);

    getLifecycleManager().addStartupTask(this::addNodes);
  }

  @Override
  public AddressSpaceFilter getFilter() {
    return filter;
  }

  /**
   * Intercepts {@code WriteFullArrayOnly} index-range writes (returning {@code
   * Bad_WriteNotSupported} before {@code AttributeWriter} resolves the range) and {@code Decimal}
   * ({@code i=50}) binary {@link ExtensionObject} writes (working around Milo's {@code
   * DataTypeTree} mapping {@code Decimal} to {@code Number.class}).
   */
  @Override
  public List<StatusCode> write(WriteContext context, List<WriteValue> writeValues) {
    var results = new ArrayList<StatusCode>(writeValues.size());

    for (WriteValue writeValue : writeValues) {
      UaServerNode node = getNodeManager().get(writeValue.getNodeId());
      if (node == null) {
        results.add(new StatusCode(StatusCodes.Bad_NodeIdUnknown));
        continue;
      }

      if (writeValue.getAttributeId().equals(AttributeId.Value.uid())
          && node instanceof UaVariableNode variableNode) {

        AccessLevelExType accessLevelEx = variableNode.getAccessLevelEx();
        String indexRange = writeValue.getIndexRange();
        if (accessLevelEx != null
            && accessLevelEx.getWriteFullArrayOnly()
            && indexRange != null
            && !indexRange.isEmpty()) {

          context
              .getOperationDiagnostics()
              .report(
                  writeValue,
                  Diagnostic.text("WriteFullArrayOnly forbids index-range writes on this node"));
          results.add(new StatusCode(StatusCodes.Bad_WriteNotSupported));
          continue;
        }

        if (NodeIds.Decimal.equals(variableNode.getDataType())) {
          results.add(writeDecimalValue(context, writeValue, variableNode));
          continue;
        }
      }

      results.add(super.write(context, List.of(writeValue)).getFirst());
    }

    return results;
  }

  private StatusCode writeDecimalValue(
      WriteContext context, WriteValue writeValue, UaVariableNode variableNode) {

    AccessLevelExType accessLevelEx = variableNode.getAccessLevelEx();
    boolean writable =
        accessLevelEx != null
            ? accessLevelEx.getCurrentWrite()
            : AccessLevel.fromValue(variableNode.getAccessLevel())
                .contains(AccessLevel.CurrentWrite);
    if (!writable) {
      return new StatusCode(StatusCodes.Bad_NotWritable);
    }

    String indexRange = writeValue.getIndexRange();
    if (indexRange != null && !indexRange.isEmpty()) {
      return new StatusCode(StatusCodes.Bad_IndexRangeNoData);
    }

    DataValue incoming = writeValue.getValue();
    Variant variant = incoming.value();
    Object raw = variant.value();

    if (raw == null) {
      if (!Boolean.TRUE.equals(variableNode.getAllowNulls())) {
        return new StatusCode(StatusCodes.Bad_TypeMismatch);
      }
    } else if (raw instanceof ExtensionObject xo) {
      try {
        DecimalEncoding.decode(xo);
      } catch (UaException e) {
        e.getDiagnostic().ifPresent(d -> context.getOperationDiagnostics().report(writeValue, d));
        return e.getStatusCode();
      }
    } else {
      return new StatusCode(StatusCodes.Bad_TypeMismatch);
    }

    DateTime sourceTime = incoming.sourceTime();
    DateTime serverTime = incoming.serverTime();
    DataValue normalized =
        new DataValue(
            variant,
            incoming.statusCode(),
            (sourceTime == null || sourceTime.isNull()) ? DateTime.now() : sourceTime,
            (serverTime == null || serverTime.isNull()) ? DateTime.now() : serverTime);

    try {
      variableNode.writeAttribute(context, AttributeId.Value, normalized);
      return StatusCode.GOOD;
    } catch (UaException e) {
      e.getDiagnostic().ifPresent(d -> context.getOperationDiagnostics().report(writeValue, d));
      return e.getStatusCode();
    }
  }

  private void addNodes() {
    NamespaceTable nsTable = getServer().getNamespaceTable();

    NodeId demoOptionSetId = registerDemoOptionSetType(nsTable);
    NodeId structWithOptionSetId = registerStructWithOptionSetType(nsTable);
    NodeId demoOptionSetUInt32Id = registerDemoOptionSetUInt32Type();
    NodeId opaqueStructId = registerOpaqueStructType();
    NodeId nullableFieldsStructId = registerNullableFieldsStructType(nsTable);
    NodeId baseRecordId = registerBaseRecordType(nsTable);
    registerDerivedRecordType(nsTable, baseRecordId);
    NodeId refinableStructId = registerRefinableStructType(nsTable);
    NodeId nestedNodeId = registerNestedNodeType(nsTable);
    NodeId demoRecordId = registerDemoRecordType(nsTable);
    NodeId demoUnionId = registerDemoUnionType(nsTable);
    NodeId sparseStatusEnumId = registerSparseStatusEnumType();
    NodeId operationalStatusEnumId = registerOperationalStatusEnumType(sparseStatusEnumId);

    // Refresh DataTypeTree so AttributeWriter and AbstractMethodInvocationHandler see all new
    // DataType subtype chains immediately.
    getServer().updateDataTypeTree();

    UaFolderNode rootFolder =
        addFolder(
            namespace.getDemoFolder().getNodeId(),
            "ValueEditorTest",
            "Value shapes, restrictions, scale fixtures, and methods for OPC UA value editors");

    addOptionSetNodes(
        rootFolder.getNodeId(),
        demoOptionSetId,
        structWithOptionSetId,
        demoOptionSetUInt32Id,
        nsTable);
    addEnumerationNodes(rootFolder.getNodeId(), sparseStatusEnumId, operationalStatusEnumId);
    addSpecialTypeNodes(rootFolder.getNodeId(), opaqueStructId);
    addNullNodes(rootFolder.getNodeId(), demoUnionId, nullableFieldsStructId);
    addLimitNodes(rootFolder.getNodeId());
    addAccessAndRestrictionNodes(rootFolder.getNodeId(), baseRecordId, refinableStructId, nsTable);
    addScaleNodes(rootFolder.getNodeId(), nestedNodeId);

    new ValueEditorMethods(getServer(), getNodeContext(), getNodeManager(), namespaceIndex)
        .addMethods(rootFolder.getNodeId(), demoRecordId);
  }

  // region Custom DataType Registration

  private NodeId registerDemoOptionSetType(NamespaceTable nsTable) {
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
    typeNode.setProperty(ValueEditorFilters.OPTION_SET_LENGTH, DemoOptionSet.OPTION_SET_LENGTH);

    addBinaryEncodingNode(typeId, encodingId);
    registerCodec(typeId, encodingId, new DemoOptionSet.Codec());
    return typeId;
  }

  private NodeId registerStructWithOptionSetType(NamespaceTable nsTable) {
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
    return typeId;
  }

  private NodeId registerDemoOptionSetUInt32Type() {
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

    return typeId;
  }

  private NodeId registerOpaqueStructType() {
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
    return typeId;
  }

  private NodeId registerNullableFieldsStructType(NamespaceTable nsTable) {
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
    return typeId;
  }

  private NodeId registerBaseRecordType(NamespaceTable nsTable) {
    NodeId typeId = BaseRecord.TYPE_ID.toNodeId(nsTable).orElseThrow();
    NodeId encodingId = BaseRecord.BINARY_ENCODING_ID.toNodeId(nsTable).orElseThrow();

    addDataTypeNode(
        typeId,
        "BaseRecord",
        "Concrete base structure with subtype DerivedRecord",
        NodeIds.Structure,
        BaseRecord.definition(nsTable));

    addBinaryEncodingNode(typeId, encodingId);
    registerCodec(typeId, encodingId, new BaseRecord.Codec());
    return typeId;
  }

  private void registerDerivedRecordType(NamespaceTable nsTable, NodeId baseRecordId) {
    NodeId typeId = DerivedRecord.TYPE_ID.toNodeId(nsTable).orElseThrow();
    NodeId encodingId = DerivedRecord.BINARY_ENCODING_ID.toNodeId(nsTable).orElseThrow();

    addDataTypeNode(
        typeId,
        "DerivedRecord",
        "Concrete subtype of BaseRecord adding Priority",
        baseRecordId,
        DerivedRecord.definition(nsTable));

    addBinaryEncodingNode(typeId, encodingId);
    registerCodec(typeId, encodingId, new DerivedRecord.Codec());
  }

  private NodeId registerRefinableStructType(NamespaceTable nsTable) {
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
    return typeId;
  }

  private NodeId registerNestedNodeType(NamespaceTable nsTable) {
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
    return typeId;
  }

  private NodeId registerDemoRecordType(NamespaceTable nsTable) {
    NodeId typeId = DemoRecord.TYPE_ID.toNodeId(nsTable).orElseThrow();
    NodeId encodingId = DemoRecord.BINARY_ENCODING_ID.toNodeId(nsTable).orElseThrow();

    addDataTypeNode(
        typeId,
        "DemoRecord",
        "Structure used as input and output by ValueEditorTest methods",
        NodeIds.Structure,
        DemoRecord.definition(nsTable));

    addBinaryEncodingNode(typeId, encodingId);
    registerCodec(typeId, encodingId, new DemoRecord.Codec());
    return typeId;
  }

  private NodeId registerDemoUnionType(NamespaceTable nsTable) {
    NodeId typeId = DemoUnion.TYPE_ID.toNodeId(nsTable).orElseThrow();
    NodeId encodingId = DemoUnion.BINARY_ENCODING_ID.toNodeId(nsTable).orElseThrow();

    addDataTypeNode(
        typeId,
        "DemoUnion",
        "Union with SwitchField=0 representing a typed null union",
        NodeIds.Union,
        DemoUnion.definition(nsTable));

    addBinaryEncodingNode(typeId, encodingId);
    registerCodec(typeId, encodingId, new DemoUnion.Codec());
    return typeId;
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

  private NodeId registerOperationalStatusEnumType(NodeId sparseStatusEnumId) {
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
    return typeId;
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

  // endregion

  // region Folder 1: OptionSets

  private void addOptionSetNodes(
      NodeId rootFolderId,
      NodeId demoOptionSetId,
      NodeId structWithOptionSetId,
      NodeId demoOptionSetUInt32Id,
      NamespaceTable nsTable) {

    UaFolderNode folder =
        addFolder(
            rootFolderId,
            "OptionSets",
            "Structure OptionSet subtypes and integer bitmask OptionSets");

    EncodingContext encodingContext = getServer().getStaticEncodingContext();
    NodeId demoOptionSetEncodingId =
        DemoOptionSet.BINARY_ENCODING_ID.toNodeId(nsTable).orElseThrow();
    NodeId structWithOptionSetEncodingId =
        StructWithOptionSet.BINARY_ENCODING_ID.toNodeId(nsTable).orElseThrow();

    UaVariableNode optionSetScalar =
        addVariable(
            folder.getNodeId(),
            "OptionSetScalar",
            "Concrete 2-byte OptionSet subtype (bits 0-3, 8-9; gaps 4-7, 10-15)",
            demoOptionSetId,
            ValueRanks.Scalar,
            null,
            Variant.ofExtensionObject(
                ExtensionObject.encode(encodingContext, DemoOptionSet.of(0x03, 0x01))));
    optionSetScalar
        .getFilterChain()
        .addLast(ValueEditorFilters.optionSetScalar(encodingContext, demoOptionSetEncodingId));

    ExtensionObject[] optionSetElements = {
      ExtensionObject.encode(encodingContext, DemoOptionSet.of(0x01, 0x00)),
      ExtensionObject.encode(encodingContext, DemoOptionSet.of(0x0A, 0x03))
    };
    UaVariableNode optionSetArray =
        addVariable(
            folder.getNodeId(),
            "OptionSetArray",
            "One-dimensional array of DemoOptionSet values",
            demoOptionSetId,
            ValueRanks.OneDimension,
            new UInteger[] {uint(0)},
            Variant.ofExtensionObjectArray(optionSetElements));
    optionSetArray
        .getFilterChain()
        .addLast(ValueEditorFilters.optionSetArray(encodingContext, demoOptionSetEncodingId));

    StructWithOptionSet initialStruct =
        new StructWithOptionSet("DefaultProfile", DemoOptionSet.of(0x05, 0x02));
    UaVariableNode structWithOptionSet =
        addVariable(
            folder.getNodeId(),
            "StructWithOptionSet",
            "Structure carrying a DemoOptionSet field",
            structWithOptionSetId,
            ValueRanks.Scalar,
            null,
            Variant.ofExtensionObject(ExtensionObject.encode(encodingContext, initialStruct)));
    structWithOptionSet
        .getFilterChain()
        .addLast(
            ValueEditorFilters.structWithOptionSet(encodingContext, structWithOptionSetEncodingId));

    addVariable(
        folder.getNodeId(),
        "DemoOptionSetUInt32Scalar",
        "Standalone UInt32 OptionSet (bits 0=Read, 1=Write, gap 2, 3=Execute, 4=Admin)",
        demoOptionSetUInt32Id,
        ValueRanks.Scalar,
        null,
        Variant.ofUInt32(uint(0b01011)));

    addVariable(
        folder.getNodeId(),
        "DemoOptionSetUInt32Array",
        "Array of UInt32 OptionSet values",
        demoOptionSetUInt32Id,
        ValueRanks.OneDimension,
        new UInteger[] {uint(0)},
        Variant.ofUInt32Array(new UInteger[] {uint(0b00001), uint(0b11010)}));

    addVariable(
        folder.getNodeId(),
        "PermissionTypeScalar",
        "Standalone standard PermissionType (i=94) integer OptionSet",
        NodeIds.PermissionType,
        ValueRanks.Scalar,
        null,
        Variant.ofUInt32(uint(0x0003)));

    addVariable(
        folder.getNodeId(),
        "PermissionTypeArray",
        "Array of standard PermissionType (i=94) integer OptionSet values",
        NodeIds.PermissionType,
        ValueRanks.OneDimension,
        new UInteger[] {uint(0)},
        Variant.ofUInt32Array(new UInteger[] {uint(0x0001), uint(0x0011)}));
  }

  // endregion

  // region Folder 2: Enumerations

  private void addEnumerationNodes(
      NodeId rootFolderId, NodeId sparseStatusEnumId, NodeId operationalStatusEnumId) {

    UaFolderNode folder =
        addFolder(
            rootFolderId,
            "Enumerations",
            "Non-contiguous EnumValues enumeration and a subtype removing values");

    UaVariableNode sparseScalar =
        addVariable(
            folder.getNodeId(),
            "SparseEnumScalar",
            "SparseStatusEnum scalar (defined values: 1, 2, 4, 8, 16)",
            sparseStatusEnumId,
            ValueRanks.Scalar,
            null,
            Variant.ofInt32(2));
    sparseScalar.getFilterChain().addLast(ValueEditorFilters.enumValuesCheck(SPARSE_ENUM_VALUES));

    UaVariableNode sparseArray =
        addVariable(
            folder.getNodeId(),
            "SparseEnumArray",
            "SparseStatusEnum 1-D array (defined values: 1, 2, 4, 8, 16)",
            sparseStatusEnumId,
            ValueRanks.OneDimension,
            new UInteger[] {uint(0)},
            Variant.ofInt32Array(new Integer[] {1, 4, 16}));
    sparseArray.getFilterChain().addLast(ValueEditorFilters.enumValuesCheck(SPARSE_ENUM_VALUES));

    UaVariableNode restrictedScalar =
        addVariable(
            folder.getNodeId(),
            "RestrictedEnumScalar",
            "OperationalStatusEnum subtype scalar (defined values: 1, 2, 4; 8 and 16 removed)",
            operationalStatusEnumId,
            ValueRanks.Scalar,
            null,
            Variant.ofInt32(1));
    restrictedScalar
        .getFilterChain()
        .addLast(ValueEditorFilters.enumValuesCheck(OPERATIONAL_ENUM_VALUES));
  }

  // endregion

  // region Folder 3: SpecialTypes

  private void addSpecialTypeNodes(NodeId rootFolderId, NodeId opaqueStructId) {
    UaFolderNode folder =
        addFolder(
            rootFolderId,
            "SpecialTypes",
            "Decimal (i=50), opaque/unknown-encoding structures, and empty abstract variables");

    UaVariableNode decimalValue =
        addVariable(
            folder.getNodeId(),
            "DecimalValue",
            "Built-in Decimal (i=50) encoded per Part 6 §5.1.10 (initial 123.45)",
            NodeIds.Decimal,
            ValueRanks.Scalar,
            null,
            Variant.ofExtensionObject(DecimalEncoding.encode(new BigDecimal("123.45"))));
    decimalValue.setAllowNulls(false);

    NodeId opaqueEncodingId = new NodeId(namespaceIndex, OPAQUE_STRUCT_ENCODING_IDENTIFIER);
    addVariable(
        folder.getNodeId(),
        "OpaqueStructure",
        "Structure whose DataType has no DataTypeDefinition attribute",
        opaqueStructId,
        ValueRanks.Scalar,
        null,
        Variant.ofExtensionObject(
            ExtensionObject.of(
                ByteString.of(new byte[] {0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08}),
                opaqueEncodingId)));

    NodeId unknownEncodingId = new NodeId(namespaceIndex, UNKNOWN_ENCODING_IDENTIFIER);
    addVariable(
        folder.getNodeId(),
        "UnknownEncodingStructure",
        "Structure carrying an ExtensionObject whose encoding NodeId does not exist",
        NodeIds.Structure,
        ValueRanks.Scalar,
        null,
        Variant.ofExtensionObject(
            ExtensionObject.of(
                ByteString.of(new byte[] {(byte) 0xDE, (byte) 0xAD, (byte) 0xBE, (byte) 0xEF}),
                unknownEncodingId)));

    UaVariableNode emptyNumber =
        addVariable(
            folder.getNodeId(),
            "EmptyNumber",
            "Writable abstract Number variable initialized to null (AllowNulls=true)",
            NodeIds.Number,
            ValueRanks.Scalar,
            null,
            Variant.NULL_VALUE);
    emptyNumber.setAllowNulls(true);

    UaVariableNode emptyStructure =
        addVariable(
            folder.getNodeId(),
            "EmptyStructure",
            "Writable abstract Structure variable initialized to null (AllowNulls=true)",
            NodeIds.Structure,
            ValueRanks.Scalar,
            null,
            Variant.NULL_VALUE);
    emptyStructure.setAllowNulls(true);
  }

  // endregion

  // region Folder 4: Nulls

  private void addNullNodes(
      NodeId rootFolderId, NodeId demoUnionId, NodeId nullableFieldsStructId) {

    UaFolderNode nullsFolder =
        addFolder(
            rootFolderId,
            "Nulls",
            "Writable null variables per nullable type and AllowNulls paired outcome fixtures");

    EncodingContext encodingContext = getServer().getStaticEncodingContext();

    UaFolderNode writableNulls =
        addFolder(
            nullsFolder.getNodeId(),
            "WritableNulls",
            "Writable AllowNulls=true variables initialized to null for every nullable OPC UA"
                + " type");

    addWritableNull(writableNulls.getNodeId(), "StringNull", NodeIds.String, Variant.NULL_VALUE);
    addWritableNull(
        writableNulls.getNodeId(),
        "ByteStringNull",
        NodeIds.ByteString,
        Variant.ofByteString(ByteString.NULL_VALUE));
    addWritableNull(
        writableNulls.getNodeId(),
        "XmlElementNull",
        NodeIds.XmlElement,
        Variant.ofXmlElement(XmlElement.NULL_VALUE));
    addWritableNull(
        writableNulls.getNodeId(),
        "DateTimeNull",
        NodeIds.DateTime,
        Variant.ofDateTime(DateTime.NULL_VALUE));
    addWritableNull(
        writableNulls.getNodeId(), "GuidNull", NodeIds.Guid, Variant.ofGuid(new UUID(0L, 0L)));
    addWritableNull(
        writableNulls.getNodeId(),
        "NodeIdNull",
        NodeIds.NodeId,
        Variant.ofNodeId(NodeId.NULL_VALUE));
    addWritableNull(
        writableNulls.getNodeId(),
        "ExpandedNodeIdNull",
        NodeIds.ExpandedNodeId,
        Variant.ofExpandedNodeId(ExpandedNodeId.NULL_VALUE));
    addWritableNull(
        writableNulls.getNodeId(),
        "QualifiedNameNull",
        NodeIds.QualifiedName,
        Variant.ofQualifiedName(QualifiedName.NULL_VALUE));
    addWritableNull(
        writableNulls.getNodeId(),
        "LocalizedTextNull",
        NodeIds.LocalizedText,
        Variant.ofLocalizedText(LocalizedText.NULL_VALUE));
    addWritableNull(
        writableNulls.getNodeId(),
        "ExtensionObjectNull",
        NodeIds.Structure,
        Variant.ofExtensionObject(ExtensionObject.of(ByteString.NULL_VALUE, NodeId.NULL_VALUE)));
    addWritableNull(
        writableNulls.getNodeId(), "VariantNull", NodeIds.BaseDataType, Variant.NULL_VALUE);
    addWritableNull(
        writableNulls.getNodeId(),
        "UnionNull",
        demoUnionId,
        Variant.ofExtensionObject(ExtensionObject.encode(encodingContext, DemoUnion.ofNull())));

    UaVariableNode int32ArrayNull =
        addVariable(
            writableNulls.getNodeId(),
            "Int32ArrayNull",
            "Writable Int32[] variable initialized to null (AllowNulls=true)",
            NodeIds.Int32,
            ValueRanks.OneDimension,
            new UInteger[] {uint(0)},
            Variant.NULL_VALUE);
    int32ArrayNull.setAllowNulls(true);

    UaFolderNode paired =
        addFolder(
            nullsFolder.getNodeId(),
            "PairedOutcomes",
            "AllowNulls=true/false pairs and nested null fixtures with predictable write outcomes");

    addAllowNullsPair(
        paired.getNodeId(),
        "String",
        NodeIds.String,
        ValueRanks.Scalar,
        null,
        Variant.ofString("hello"));

    addAllowNullsPair(
        paired.getNodeId(),
        "ByteString",
        NodeIds.ByteString,
        ValueRanks.Scalar,
        null,
        Variant.ofByteString(ByteString.of(new byte[] {1, 2, 3})));

    addAllowNullsPair(
        paired.getNodeId(),
        "BaseDataType",
        NodeIds.BaseDataType,
        ValueRanks.Scalar,
        null,
        Variant.ofInt32(42));

    addAllowNullsPair(
        paired.getNodeId(),
        "Int32Array",
        NodeIds.Int32,
        ValueRanks.OneDimension,
        new UInteger[] {uint(0)},
        Variant.ofInt32Array(new Integer[] {1, 2, 3}));

    UaVariableNode nullableStringArray =
        addVariable(
            paired.getNodeId(),
            "NullableStringArray",
            "Non-null String[] holding a non-empty, null, and empty element (AllowNulls=false)",
            NodeIds.String,
            ValueRanks.OneDimension,
            new UInteger[] {uint(0)},
            Variant.ofStringArray(new String[] {"alpha", null, ""}));
    nullableStringArray.setAllowNulls(false);
    nullableStringArray.getFilterChain().addLast(ValueEditorFilters.DISALLOW_NULLS);

    UaVariableNode nullableByteStringArray =
        addVariable(
            paired.getNodeId(),
            "NullableByteStringArray",
            "Non-null ByteString[] holding non-empty, null, and empty elements (AllowNulls=false)",
            NodeIds.ByteString,
            ValueRanks.OneDimension,
            new UInteger[] {uint(0)},
            Variant.ofByteStringArray(
                new ByteString[] {
                  ByteString.of(new byte[] {1}), ByteString.NULL_VALUE, ByteString.of(new byte[0])
                }));
    nullableByteStringArray.setAllowNulls(false);
    nullableByteStringArray.getFilterChain().addLast(ValueEditorFilters.DISALLOW_NULLS);

    NullableFieldsStruct initialNullableFields =
        new NullableFieldsStruct(
            null, ByteString.NULL_VALUE, LocalizedText.NULL_VALUE, NodeId.NULL_VALUE, null);
    UaVariableNode nullableFieldsNode =
        addVariable(
            paired.getNodeId(),
            "NullableFieldsStruct",
            "Non-null structure containing null scalar and array fields (AllowNulls=false)",
            nullableFieldsStructId,
            ValueRanks.Scalar,
            null,
            Variant.ofExtensionObject(
                ExtensionObject.encode(encodingContext, initialNullableFields)));
    nullableFieldsNode.setAllowNulls(false);
    nullableFieldsNode.getFilterChain().addLast(ValueEditorFilters.DISALLOW_NULLS);
  }

  private void addWritableNull(
      NodeId folderId, String name, NodeId dataTypeId, Variant initialValue) {
    UaVariableNode node =
        addVariable(
            folderId,
            name,
            "Writable " + name + " fixture (AllowNulls=true)",
            dataTypeId,
            ValueRanks.Scalar,
            null,
            initialValue);
    node.setAllowNulls(true);
  }

  private void addAllowNullsPair(
      NodeId folderId,
      String prefix,
      NodeId dataTypeId,
      int valueRank,
      UInteger @Nullable [] arrayDimensions,
      Variant initialValue) {

    UaVariableNode allowNode =
        addVariable(
            folderId,
            prefix + "AllowNulls",
            prefix + " with AllowNulls=true: accepts root null, typed null, and empty values",
            dataTypeId,
            valueRank,
            arrayDimensions,
            initialValue);
    allowNode.setAllowNulls(true);

    UaVariableNode disallowNode =
        addVariable(
            folderId,
            prefix + "DisallowNulls",
            prefix
                + " with AllowNulls=false: rejects root null and typed null with Bad_TypeMismatch,"
                + " accepts empty values",
            dataTypeId,
            valueRank,
            arrayDimensions,
            initialValue);
    disallowNode.setAllowNulls(false);
    disallowNode.getFilterChain().addLast(ValueEditorFilters.DISALLOW_NULLS);
  }

  // endregion

  // region Folder 5: Limits

  private void addLimitNodes(NodeId rootFolderId) {
    UaFolderNode folder =
        addFolder(
            rootFolderId,
            "Limits",
            "MaxStringLength, MaxCharacters, MaxByteStringLength, MaxArrayLength, FixedSizeArray,"
                + " and non-zero ArrayDimensions");

    UaVariableNode maxStringLength =
        addVariable(
            folder.getNodeId(),
            "MaxStringLengthString",
            "String bounded to MaxStringLength = 16 UTF-8 bytes",
            NodeIds.String,
            ValueRanks.Scalar,
            null,
            Variant.ofString("short text"));
    maxStringLength.setMaxStringLength(uint(16));
    maxStringLength.getFilterChain().addLast(ValueEditorFilters.VALUE_LIMITS);

    UaVariableNode maxCharacters =
        addVariable(
            folder.getNodeId(),
            "MaxCharactersString",
            "String bounded to MaxCharacters = 8 Unicode code points",
            NodeIds.String,
            ValueRanks.Scalar,
            null,
            Variant.ofString("café"));
    maxCharacters.setProperty(ValueEditorFilters.MAX_CHARACTERS, uint(8));
    maxCharacters.getFilterChain().addLast(ValueEditorFilters.VALUE_LIMITS);

    UaVariableNode maxByteStringLength =
        addVariable(
            folder.getNodeId(),
            "MaxByteStringLengthValue",
            "ByteString bounded to MaxByteStringLength = 8 bytes",
            NodeIds.ByteString,
            ValueRanks.Scalar,
            null,
            Variant.ofByteString(ByteString.of(new byte[] {1, 2, 3, 4})));
    maxByteStringLength.setProperty(ValueEditorFilters.MAX_BYTE_STRING_LENGTH, uint(8));
    maxByteStringLength.getFilterChain().addLast(ValueEditorFilters.VALUE_LIMITS);

    UaVariableNode maxArrayLength =
        addVariable(
            folder.getNodeId(),
            "MaxArrayLengthInt32",
            "Int32[] bounded to MaxArrayLength = 5 elements",
            NodeIds.Int32,
            ValueRanks.OneDimension,
            new UInteger[] {uint(0)},
            Variant.ofInt32Array(new Integer[] {1, 2, 3}));
    maxArrayLength.setMaxArrayLength(uint(5));
    maxArrayLength.getFilterChain().addLast(ValueEditorFilters.VALUE_LIMITS);

    UaVariableNode maxArrayLengthMatrix =
        addVariable(
            folder.getNodeId(),
            "MaxArrayLengthMatrix",
            "2-D Int32 matrix bounded to MaxArrayLength = 6 total elements",
            NodeIds.Int32,
            2,
            new UInteger[] {uint(0), uint(0)},
            Variant.ofMatrix(Matrix.ofInt32(new Integer[][] {{1, 2, 3}, {4, 5, 6}})));
    maxArrayLengthMatrix.setMaxArrayLength(uint(6));
    maxArrayLengthMatrix.getFilterChain().addLast(ValueEditorFilters.VALUE_LIMITS);

    UaVariableNode bounded1D =
        addVariable(
            folder.getNodeId(),
            "BoundedOneDimArray",
            "Int32[] with non-zero ArrayDimensions = [5] (maximum length 5)",
            NodeIds.Int32,
            ValueRanks.OneDimension,
            new UInteger[] {uint(5)},
            Variant.ofInt32Array(new Integer[] {10, 20, 30}));
    bounded1D.getFilterChain().addLast(ValueEditorFilters.VALUE_LIMITS);

    UaVariableNode bounded2D =
        addVariable(
            folder.getNodeId(),
            "BoundedTwoDimMatrix",
            "2-D Double matrix with non-zero ArrayDimensions = [2, 3]",
            NodeIds.Double,
            2,
            new UInteger[] {uint(2), uint(3)},
            Variant.ofMatrix(Matrix.ofDouble(new Double[][] {{1.0, 2.0, 3.0}, {4.0, 5.0, 6.0}})));
    bounded2D.getFilterChain().addLast(ValueEditorFilters.VALUE_LIMITS);

    UaVariableNode fixedSize =
        addVariable(
            folder.getNodeId(),
            "FixedSizeInt32Array",
            "Int32[] with ArrayDimensions = [4] and FixedSizeArray = true (exact length 4)",
            NodeIds.Int32,
            ValueRanks.OneDimension,
            new UInteger[] {uint(4)},
            Variant.ofInt32Array(new Integer[] {1, 2, 3, 4}));
    fixedSize.setProperty(ValueEditorFilters.FIXED_SIZE_ARRAY, true);
    fixedSize.getFilterChain().addLast(ValueEditorFilters.VALUE_LIMITS);
  }

  // endregion

  // region Folder 6: AccessAndRestrictions

  private void addAccessAndRestrictionNodes(
      NodeId rootFolderId, NodeId baseRecordId, NodeId refinableStructId, NamespaceTable nsTable) {

    UaFolderNode folder =
        addFolder(
            rootFolderId,
            "AccessAndRestrictions",
            "WriteFullArrayOnly, NoSubDataTypes, SubtypeRestriction, DataTypeRefinement, and"
                + " write-only variables");

    EncodingContext encodingContext = getServer().getStaticEncodingContext();
    NodeId baseRecordEncodingId = BaseRecord.BINARY_ENCODING_ID.toNodeId(nsTable).orElseThrow();
    NodeId refinableStructEncodingId =
        RefinableStruct.BINARY_ENCODING_ID.toNodeId(nsTable).orElseThrow();

    // WriteFullArrayOnly (AccessLevelEx bit 10)
    UaVariableNode writeFullArrayOnly =
        addVariable(
            folder.getNodeId(),
            "WriteFullArrayOnlyInt32Array",
            "Int32[] with AccessLevelEx.WriteFullArrayOnly refusing index-range writes with"
                + " Bad_WriteNotSupported",
            NodeIds.Int32,
            ValueRanks.OneDimension,
            new UInteger[] {uint(0)},
            Variant.ofInt32Array(new Integer[] {10, 20, 30, 40}));
    writeFullArrayOnly.setAccessLevelEx(
        AccessLevelExType.of(
            AccessLevelExType.Field.CurrentRead,
            AccessLevelExType.Field.CurrentWrite,
            AccessLevelExType.Field.WriteFullArrayOnly));

    // NoSubDataTypes (AccessLevelEx bit 11) + paired subtypable contrast
    UaVariableNode noSubDataTypes =
        addVariable(
            folder.getNodeId(),
            "NoSubDataTypesRecord",
            "BaseRecord with AccessLevelEx.NoSubDataTypes rejecting DerivedRecord with"
                + " Bad_TypeMismatch",
            baseRecordId,
            ValueRanks.Scalar,
            null,
            Variant.ofExtensionObject(
                ExtensionObject.encode(encodingContext, new BaseRecord(1, "base-only"))));
    noSubDataTypes.setAccessLevelEx(
        AccessLevelExType.of(
            AccessLevelExType.Field.CurrentRead,
            AccessLevelExType.Field.CurrentWrite,
            AccessLevelExType.Field.NoSubDataTypes));
    noSubDataTypes
        .getFilterChain()
        .addLast(ValueEditorFilters.noSubDataTypes(baseRecordEncodingId));

    UaVariableNode subtypableRecord =
        addVariable(
            folder.getNodeId(),
            "SubtypableRecord",
            "BaseRecord without NoSubDataTypes accepting BaseRecord and DerivedRecord",
            baseRecordId,
            ValueRanks.Scalar,
            null,
            Variant.ofExtensionObject(
                ExtensionObject.encode(encodingContext, new DerivedRecord(2, "derived", 5))));
    subtypableRecord.setAccessLevelEx(
        AccessLevelExType.of(
            AccessLevelExType.Field.CurrentRead, AccessLevelExType.Field.CurrentWrite));

    // SubtypeRestriction (Part 3 §5.8.6.1): Number restricted to scalar Int32 or Double
    addSubtypeRestrictionNodes(folder.getNodeId());

    // DataTypeRefinement (Part 3 §5.8.5.2): RefinableStruct with mandatory OptionalNote (<=32B),
    // disabled DisabledFlag, and Reading refined to Double in °C
    addDataTypeRefinementNodes(
        folder.getNodeId(), refinableStructId, refinableStructEncodingId, encodingContext);

    // Write-only variables (design-review fixture)
    UaVariableNode writeOnlyInt32 =
        new UaVariableNodeBuilder(getNodeContext())
            .setNodeId(deriveChildNodeId(folder.getNodeId(), "WriteOnlyInt32"))
            .setBrowseName(new QualifiedName(namespaceIndex, "WriteOnlyInt32"))
            .setDisplayName(LocalizedText.english("WriteOnlyInt32"))
            .setDescription(
                LocalizedText.english(
                    "Write-only Int32 (AccessLevel=CurrentWrite): Value read returns"
                        + " Bad_NotReadable"))
            .setDataType(NodeIds.Int32)
            .setValueRank(ValueRanks.Scalar)
            .setAccessLevel(AccessLevel.toValue(AccessLevel.CurrentWrite))
            .setUserAccessLevel(AccessLevel.toValue(AccessLevel.CurrentWrite))
            .setAccessLevelEx(AccessLevelExType.of(AccessLevelExType.Field.CurrentWrite))
            .setMinimumSamplingInterval(MIN_SUPPORTED_SAMPLE_RATE)
            .setValue(new DataValue(Variant.ofInt32(0)))
            .build();
    addChildNode(folder.getNodeId(), writeOnlyInt32);

    UaVariableNode writeOnlyUserInt32 =
        new UaVariableNodeBuilder(getNodeContext())
            .setNodeId(deriveChildNodeId(folder.getNodeId(), "WriteOnlyUserInt32"))
            .setBrowseName(new QualifiedName(namespaceIndex, "WriteOnlyUserInt32"))
            .setDisplayName(LocalizedText.english("WriteOnlyUserInt32"))
            .setDescription(
                LocalizedText.english(
                    "User-write-only Int32 (AccessLevel=ReadWrite, UserAccessLevel=CurrentWrite):"
                        + " Value read returns Bad_UserAccessDenied"))
            .setDataType(NodeIds.Int32)
            .setValueRank(ValueRanks.Scalar)
            .setAccessLevel(AccessLevel.toValue(AccessLevel.READ_WRITE))
            .setUserAccessLevel(AccessLevel.toValue(AccessLevel.CurrentWrite))
            .setAccessLevelEx(
                AccessLevelExType.of(
                    AccessLevelExType.Field.CurrentRead, AccessLevelExType.Field.CurrentWrite))
            .setMinimumSamplingInterval(MIN_SUPPORTED_SAMPLE_RATE)
            .setValue(new DataValue(Variant.ofInt32(0)))
            .build();
    addChildNode(folder.getNodeId(), writeOnlyUserInt32);
  }

  private void addSubtypeRestrictionNodes(NodeId folderId) {
    SubtypeRestrictionTypeNode restriction =
        new SubtypeRestrictionTypeNode(
            getNodeContext(),
            deriveChildNodeId(folderId, "NumberRestriction"),
            new QualifiedName(namespaceIndex, "NumberRestriction"),
            LocalizedText.english("NumberRestriction"),
            LocalizedText.english("SubtypeRestriction limiting Number to scalar Int32 or Double"),
            uint(0),
            uint(0),
            null,
            null,
            null);
    restriction.addReference(
        new Reference(
            restriction.getNodeId(),
            ReferenceTypes.HasTypeDefinition,
            NodeIds.SubtypeRestrictionType.expanded(),
            Direction.FORWARD));
    addChildNode(folderId, restriction);

    restriction.addReference(
        new Reference(
            restriction.getNodeId(),
            NodeIds.HasDataTypeRefinement,
            NodeIds.Number.expanded(),
            Direction.INVERSE));

    addAllowedSubtypeVariable(restriction.getNodeId(), "AllowedInt32", NodeIds.Int32);
    addAllowedSubtypeVariable(restriction.getNodeId(), "AllowedDouble", NodeIds.Double);

    UaVariableNode restrictedNumber =
        addVariable(
            folderId,
            "RestrictedNumber",
            "Abstract Number restricted via UsesSubtypeRestriction to scalar Int32 or Double",
            NodeIds.Number,
            ValueRanks.Scalar,
            null,
            Variant.ofInt32(42));
    restrictedNumber.setAllowNulls(false);
    // Subtypes must be permitted on the variable using SubtypeRestriction (NoSubDataTypes=false).
    restrictedNumber.setAccessLevelEx(
        AccessLevelExType.of(
            AccessLevelExType.Field.CurrentRead, AccessLevelExType.Field.CurrentWrite));
    restrictedNumber.addReference(
        new Reference(
            restrictedNumber.getNodeId(),
            NodeIds.UsesSubtypeRestriction,
            restriction.getNodeId().expanded(),
            Direction.FORWARD));
    restrictedNumber
        .getFilterChain()
        .addLast(ValueEditorFilters.subtypeRestriction(Set.of(Integer.class, Double.class)));
  }

  private void addAllowedSubtypeVariable(NodeId restrictionNodeId, String name, NodeId dataTypeId) {
    UaVariableNode allowedVar =
        new UaVariableNodeBuilder(getNodeContext())
            .setNodeId(deriveChildNodeId(restrictionNodeId, name))
            .setBrowseName(new QualifiedName(namespaceIndex, name))
            .setDisplayName(LocalizedText.english(name))
            .setDescription(LocalizedText.english("Allowed subtype: " + name))
            .setDataType(dataTypeId)
            .setValueRank(ValueRanks.Scalar)
            .setAccessLevel(AccessLevel.toValue(AccessLevel.CurrentRead))
            .setUserAccessLevel(AccessLevel.toValue(AccessLevel.CurrentRead))
            .setAccessLevelEx(
                AccessLevelExType.of(
                    AccessLevelExType.Field.CurrentRead, AccessLevelExType.Field.NoSubDataTypes))
            .setMinimumSamplingInterval(MIN_SUPPORTED_SAMPLE_RATE)
            .setValue(new DataValue(Variant.NULL_VALUE))
            .build();

    getNodeManager().addNode(allowedVar);
    allowedVar.addReference(
        new Reference(
            allowedVar.getNodeId(),
            NodeIds.AllowedSubtype,
            restrictionNodeId.expanded(),
            Direction.INVERSE));
  }

  private void addDataTypeRefinementNodes(
      NodeId folderId,
      NodeId refinableStructId,
      NodeId refinableStructEncodingId,
      EncodingContext encodingContext) {

    try {
      DataTypeRefinementTypeNode refinement =
          new DataTypeRefinementTypeNode(
              getNodeContext(),
              deriveChildNodeId(folderId, "RefinableStructProfile"),
              new QualifiedName(namespaceIndex, "RefinableStructProfile"),
              LocalizedText.english("RefinableStructProfile"),
              LocalizedText.english(
                  "DataTypeRefinement on RefinableStruct: OptionalNote mandatory (<=32B),"
                      + " DisabledFlag disabled, Reading refined to Double (°C)"),
              uint(0),
              uint(0),
              null,
              null,
              null);
      refinement.addReference(
          new Reference(
              refinement.getNodeId(),
              ReferenceTypes.HasTypeDefinition,
              NodeIds.DataTypeRefinementType.expanded(),
              Direction.FORWARD));
      addChildNode(folderId, refinement);

      refinement.addReference(
          new Reference(
              refinement.getNodeId(),
              NodeIds.HasDataTypeRefinement,
              refinableStructId.expanded(),
              Direction.INVERSE));

      // HasFieldDescriptionSetMandatory -> OptionalNote (with MaxStringLength=32)
      UaVariableNode optionalNoteField =
          new UaVariableNodeBuilder(getNodeContext())
              .setNodeId(deriveChildNodeId(refinement.getNodeId(), "OptionalNote"))
              .setBrowseName(new QualifiedName(namespaceIndex, "OptionalNote"))
              .setDisplayName(LocalizedText.english("OptionalNote"))
              .setDescription(
                  LocalizedText.english("OptionalNote promoted to mandatory, MaxStringLength=32"))
              .setDataType(NodeIds.String)
              .setValueRank(ValueRanks.Scalar)
              .setAccessLevel(AccessLevel.toValue(AccessLevel.CurrentRead))
              .setUserAccessLevel(AccessLevel.toValue(AccessLevel.CurrentRead))
              .setMinimumSamplingInterval(MIN_SUPPORTED_SAMPLE_RATE)
              .setValue(new DataValue(Variant.NULL_VALUE))
              .build();
      optionalNoteField.setMaxStringLength(uint(REFINED_OPTIONAL_NOTE_MAX_BYTES));
      getNodeManager().addNode(optionalNoteField);
      optionalNoteField.addReference(
          new Reference(
              optionalNoteField.getNodeId(),
              NodeIds.HasFieldDescriptionSetMandatory,
              refinement.getNodeId().expanded(),
              Direction.INVERSE));

      // IsDisabledOptionalField -> DisabledFlag
      UaVariableNode disabledFlagField =
          new UaVariableNodeBuilder(getNodeContext())
              .setNodeId(deriveChildNodeId(refinement.getNodeId(), "DisabledFlag"))
              .setBrowseName(new QualifiedName(namespaceIndex, "DisabledFlag"))
              .setDisplayName(LocalizedText.english("DisabledFlag"))
              .setDescription(LocalizedText.english("DisabledFlag disabled by refinement"))
              .setDataType(NodeIds.Boolean)
              .setValueRank(ValueRanks.Scalar)
              .setAccessLevel(AccessLevel.toValue(AccessLevel.CurrentRead))
              .setUserAccessLevel(AccessLevel.toValue(AccessLevel.CurrentRead))
              .setMinimumSamplingInterval(MIN_SUPPORTED_SAMPLE_RATE)
              .setValue(new DataValue(Variant.NULL_VALUE))
              .build();
      getNodeManager().addNode(disabledFlagField);
      disabledFlagField.addReference(
          new Reference(
              disabledFlagField.getNodeId(),
              NodeIds.IsDisabledOptionalField,
              refinement.getNodeId().expanded(),
              Direction.INVERSE));

      // HasFieldDescription -> Reading (AnalogUnitType, Double, °C)
      AnalogUnitTypeNode readingField =
          instantiate(
              InstantiationRequest.of(AnalogUnitTypeNode.class, NodeIds.AnalogUnitType)
                  .nodeId(deriveChildNodeId(refinement.getNodeId(), "Reading"))
                  .browseName(new QualifiedName(namespaceIndex, "Reading"))
                  .displayName(LocalizedText.english("Reading"))
                  .description(
                      LocalizedText.english("Reading refined to Double in degrees Celsius"))
                  .parent(refinement.getNodeId(), NodeIds.HasFieldDescription));
      readingField.setDataType(NodeIds.Double);
      readingField.setValueRank(ValueRanks.Scalar);
      readingField.setAccessLevel(AccessLevel.toValue(AccessLevel.CurrentRead));
      readingField.setUserAccessLevel(AccessLevel.toValue(AccessLevel.CurrentRead));
      readingField.setMinimumSamplingInterval(MIN_SUPPORTED_SAMPLE_RATE);
      readingField.setEngineeringUnits(CefactEngineeringUnits.CODE_CEL);
      readingField.setValue(new DataValue(Variant.NULL_VALUE));

      RefinableStruct initialRefined =
          new RefinableStruct("Sensor-1", "Calibrated", null, Variant.ofDouble(22.5));
      UaVariableNode refinedStruct =
          addVariable(
              folderId,
              "RefinedStruct",
              "RefinableStruct constrained by RefinableStructProfile via UsesDataTypeRefinement",
              refinableStructId,
              ValueRanks.Scalar,
              null,
              Variant.ofExtensionObject(ExtensionObject.encode(encodingContext, initialRefined)));
      refinedStruct.setAllowNulls(false);
      refinedStruct.addReference(
          new Reference(
              refinedStruct.getNodeId(),
              NodeIds.UsesDataTypeRefinement,
              refinement.getNodeId().expanded(),
              Direction.FORWARD));
      refinedStruct
          .getFilterChain()
          .addLast(
              ValueEditorFilters.dataTypeRefinement(
                  encodingContext, refinableStructEncodingId, REFINED_OPTIONAL_NOTE_MAX_BYTES));

      RefinableStruct initialUnrefined =
          new RefinableStruct("Raw-1", null, true, Variant.ofInt32(7));
      addVariable(
          folderId,
          "UnrefinedStruct",
          "Unrefined RefinableStruct for comparison against RefinedStruct",
          refinableStructId,
          ValueRanks.Scalar,
          null,
          Variant.ofExtensionObject(ExtensionObject.encode(encodingContext, initialUnrefined)));
    } catch (UaException e) {
      throw new IllegalStateException("failed to instantiate DataTypeRefinement nodes", e);
    }
  }

  // endregion

  // region Folder 7: Scale

  private void addScaleNodes(NodeId rootFolderId, NodeId nestedNodeId) {
    UaFolderNode folder =
        addFolder(
            rootFolderId,
            "Scale",
            "Large 100,000-element array and 25-level deeply nested structure");

    Integer[] largeValues = new Integer[LARGE_ARRAY_LENGTH];
    for (int i = 0; i < LARGE_ARRAY_LENGTH; i++) {
      largeValues[i] = i;
    }

    addVariable(
        folder.getNodeId(),
        "LargeInt32Array",
        "Writable 100,000-element Int32 array (0..99999)",
        NodeIds.Int32,
        ValueRanks.OneDimension,
        new UInteger[] {uint(0)},
        Variant.ofInt32Array(largeValues));

    EncodingContext encodingContext = getServer().getStaticEncodingContext();
    NestedNode chain = NestedNode.chain(NESTED_CHAIN_DEPTH);
    addVariable(
        folder.getNodeId(),
        "DeeplyNestedStruct",
        "Writable NestedNode structure nested 25 levels deep",
        nestedNodeId,
        ValueRanks.Scalar,
        null,
        Variant.ofExtensionObject(ExtensionObject.encode(encodingContext, chain)));
  }

  // endregion

  // region Helpers

  private UaFolderNode addFolder(NodeId parentId, String name, String description) {
    UaFolderNode folder =
        new UaFolderNode(
            getNodeContext(),
            deriveChildNodeId(parentId, name),
            new QualifiedName(namespaceIndex, name),
            LocalizedText.english(name));
    folder.setDescription(LocalizedText.english(description));

    getNodeManager().addNode(folder);
    folder.addReference(
        new Reference(
            folder.getNodeId(),
            ReferenceTypes.HasComponent,
            parentId.expanded(),
            Direction.INVERSE));

    return folder;
  }

  private UaVariableNode addVariable(
      NodeId parentId,
      String name,
      String description,
      NodeId dataTypeId,
      int valueRank,
      UInteger @Nullable [] arrayDimensions,
      Variant initialValue) {

    UaVariableNodeBuilder builder =
        new UaVariableNodeBuilder(getNodeContext())
            .setNodeId(deriveChildNodeId(parentId, name))
            .setBrowseName(new QualifiedName(namespaceIndex, name))
            .setDisplayName(LocalizedText.english(name))
            .setDescription(LocalizedText.english(description))
            .setDataType(dataTypeId)
            .setValueRank(valueRank)
            .setAccessLevel(AccessLevel.toValue(AccessLevel.READ_WRITE))
            .setUserAccessLevel(AccessLevel.toValue(AccessLevel.READ_WRITE))
            .setMinimumSamplingInterval(MIN_SUPPORTED_SAMPLE_RATE)
            .setValue(new DataValue(initialValue));

    if (arrayDimensions != null) {
      builder.setArrayDimensions(arrayDimensions);
    }

    UaVariableNode variableNode = builder.build();
    addChildNode(parentId, variableNode);
    return variableNode;
  }

  private void addChildNode(NodeId parentId, UaNode node) {
    getNodeManager().addNode(node);
    node.addReference(
        new Reference(
            node.getNodeId(), ReferenceTypes.HasComponent, parentId.expanded(), Direction.INVERSE));
  }

  private <T extends UaNode> T instantiate(InstantiationRequest.Builder<T> request)
      throws UaException {

    return getServer()
        .getNodeInstantiator()
        .instantiate(request.legacyPathStrings().target(getNodeManager()).build())
        .root();
  }

  // endregion
}

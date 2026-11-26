package com.digitalpetri.opcua.server.namespace.demo.constraints;

import static com.digitalpetri.opcua.server.DemoConfigLimits.MIN_SUPPORTED_SAMPLE_RATE;
import static com.digitalpetri.opcua.server.namespace.demo.Util.deriveChildNodeId;
import static org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.Unsigned.uint;

import com.digitalpetri.opcua.server.namespace.demo.DemoAddressSpaceFragment;
import com.digitalpetri.opcua.server.namespace.demo.DemoNamespace;
import com.digitalpetri.opcua.server.namespace.demo.DemoValueFilters;
import com.digitalpetri.opcua.server.namespace.demo.types.NestedNode;
import com.digitalpetri.opcua.server.namespace.demo.types.NullableFieldsStruct;
import com.digitalpetri.opcua.server.namespace.demo.types.RefinableStruct;
import com.digitalpetri.opcua.test.DataTypeTestNodeIds;
import com.digitalpetri.opcua.test.types.ConcreteTestType;
import com.digitalpetri.opcua.test.types.ConcreteTestTypeEx;
import com.digitalpetri.opcua.test.types.UnionOfScalar;
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
import org.eclipse.milo.opcua.sdk.server.nodes.UaFolderNode;
import org.eclipse.milo.opcua.sdk.server.nodes.UaNode;
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
import org.eclipse.milo.opcua.stack.core.encoding.EncodingContext;
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
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.UInteger;
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.UShort;
import org.eclipse.milo.opcua.stack.core.types.structured.AccessLevelExType;
import org.eclipse.milo.opcua.stack.core.types.structured.WriteValue;
import org.jspecify.annotations.Nullable;

/**
 * Address-space fragment hosting {@code Demo/Constraints}, providing OPC UA null-handling fixtures,
 * variable length/dimension limits, access-level constraints, subtype restrictions, data-type
 * refinements, and scale fixtures.
 */
public final class DemoConstraintsFragment extends DemoAddressSpaceFragment {

  public static final int LARGE_ARRAY_LENGTH = 100_000;
  public static final int NESTED_CHAIN_DEPTH = 25;
  public static final int REFINED_OPTIONAL_NOTE_MAX_BYTES = 32;

  private static final UInteger[] ARRAY_DIMS = new UInteger[] {uint(0)};
  private static final UInteger[] MATRIX_DIMS = new UInteger[] {uint(0), uint(0)};

  private final DemoNamespace namespace;
  private final UShort namespaceIndex;
  private final SimpleAddressSpaceFilter filter;

  public DemoConstraintsFragment(OpcUaServer server, DemoNamespace namespace) {
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
   * Bad_WriteNotSupported} before {@code AttributeWriter} resolves the range).
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
      }

      results.add(super.write(context, List.of(writeValue)).getFirst());
    }

    return results;
  }

  private void addNodes() {
    NamespaceTable nsTable = getServer().getNamespaceTable();
    EncodingContext encodingContext = getServer().getStaticEncodingContext();

    UaFolderNode rootFolder =
        addFolder(
            namespace.getDemoFolder().getNodeId(),
            "Constraints",
            "Null semantics, length and array limits, access controls, type restrictions, and"
                + " scale fixtures");

    NodeId rootId = rootFolder.getNodeId();
    addNullNodes(rootId, nsTable, encodingContext);
    addLimitNodes(rootId);
    addAccessNodes(rootId, nsTable, encodingContext);
    addRestrictionNodes(rootId, nsTable, encodingContext);
    addScaleNodes(rootId, nsTable, encodingContext);
  }

  // region 1. Nulls/{Writable, Paired}

  private void addNullNodes(
      NodeId rootId, NamespaceTable nsTable, EncodingContext encodingContext) {

    UaFolderNode nullsFolder =
        addFolder(
            rootId,
            "Nulls",
            "Writable null variables per nullable type and AllowNulls paired outcome fixtures");

    NodeId unionScalarId = DataTypeTestNodeIds.UnionOfScalar.toNodeId(nsTable).orElseThrow();
    NodeId nullableFieldsStructId = NullableFieldsStruct.TYPE_ID.toNodeId(nsTable).orElseThrow();

    UaFolderNode writable =
        addFolder(
            nullsFolder.getNodeId(),
            "Writable",
            "Writable AllowNulls=true variables initialized to null for every nullable OPC UA"
                + " type");

    addWritableNull(writable.getNodeId(), "String", NodeIds.String, Variant.NULL_VALUE);
    addWritableNull(
        writable.getNodeId(),
        "ByteString",
        NodeIds.ByteString,
        Variant.ofByteString(ByteString.NULL_VALUE));
    addWritableNull(
        writable.getNodeId(),
        "XmlElement",
        NodeIds.XmlElement,
        Variant.ofXmlElement(XmlElement.NULL_VALUE));
    addWritableNull(
        writable.getNodeId(),
        "DateTime",
        NodeIds.DateTime,
        Variant.ofDateTime(DateTime.NULL_VALUE));
    addWritableNull(writable.getNodeId(), "Guid", NodeIds.Guid, Variant.ofGuid(new UUID(0L, 0L)));
    addWritableNull(
        writable.getNodeId(), "NodeId", NodeIds.NodeId, Variant.ofNodeId(NodeId.NULL_VALUE));
    addWritableNull(
        writable.getNodeId(),
        "ExpandedNodeId",
        NodeIds.ExpandedNodeId,
        Variant.ofExpandedNodeId(ExpandedNodeId.NULL_VALUE));
    addWritableNull(
        writable.getNodeId(),
        "QualifiedName",
        NodeIds.QualifiedName,
        Variant.ofQualifiedName(QualifiedName.NULL_VALUE));
    addWritableNull(
        writable.getNodeId(),
        "LocalizedText",
        NodeIds.LocalizedText,
        Variant.ofLocalizedText(LocalizedText.NULL_VALUE));
    addWritableNull(
        writable.getNodeId(),
        "ExtensionObject",
        NodeIds.Structure,
        Variant.ofExtensionObject(ExtensionObject.of(ByteString.NULL_VALUE, NodeId.NULL_VALUE)));
    addWritableNull(writable.getNodeId(), "Variant", NodeIds.BaseDataType, Variant.NULL_VALUE);
    addWritableNull(
        writable.getNodeId(),
        "Union",
        unionScalarId,
        Variant.ofExtensionObject(ExtensionObject.encode(encodingContext, UnionOfScalar.ofNull())));

    UaVariableNode int32ArrayNull =
        addVariable(
            writable.getNodeId(),
            "Int32Array",
            "Writable Int32[] variable initialized to null (AllowNulls=true)",
            NodeIds.Int32,
            ValueRanks.OneDimension,
            ARRAY_DIMS,
            Variant.NULL_VALUE);
    int32ArrayNull.setAllowNulls(true);

    UaFolderNode paired =
        addFolder(
            nullsFolder.getNodeId(),
            "Paired",
            "AllowNulls=true/false pairs and nested null fixtures with predictable write outcomes");

    addAllowNullsPair(
        paired.getNodeId(),
        "String",
        NodeIds.String,
        ValueRanks.Scalar,
        null,
        Variant.ofString("hello"),
        null);

    addAllowNullsPair(
        paired.getNodeId(),
        "ByteString",
        NodeIds.ByteString,
        ValueRanks.Scalar,
        null,
        Variant.ofByteString(ByteString.of(new byte[] {1, 2, 3})),
        null);

    addAllowNullsPair(
        paired.getNodeId(),
        "BaseDataType",
        NodeIds.BaseDataType,
        ValueRanks.Scalar,
        null,
        Variant.ofInt32(42),
        null);

    addAllowNullsPair(
        paired.getNodeId(),
        "Int32Array",
        NodeIds.Int32,
        ValueRanks.OneDimension,
        ARRAY_DIMS,
        Variant.ofInt32Array(new Integer[] {1, 2, 3}),
        null);

    addAllowNullsPair(
        paired.getNodeId(),
        "Union",
        unionScalarId,
        ValueRanks.Scalar,
        null,
        Variant.ofExtensionObject(
            ExtensionObject.encode(encodingContext, UnionOfScalar.ofBoolean(true))),
        encodingContext);

    UaVariableNode nullableStringArray =
        addVariable(
            paired.getNodeId(),
            "NullableStringArray",
            "Non-null String[] holding a non-empty, null, and empty element (AllowNulls=false)",
            NodeIds.String,
            ValueRanks.OneDimension,
            ARRAY_DIMS,
            Variant.ofStringArray(new String[] {"alpha", null, ""}));
    nullableStringArray.setAllowNulls(false);
    nullableStringArray.getFilterChain().addLast(DemoValueFilters.DISALLOW_NULLS);

    UaVariableNode nullableByteStringArray =
        addVariable(
            paired.getNodeId(),
            "NullableByteStringArray",
            "Non-null ByteString[] holding non-empty, null, and empty elements (AllowNulls=false)",
            NodeIds.ByteString,
            ValueRanks.OneDimension,
            ARRAY_DIMS,
            Variant.ofByteStringArray(
                new ByteString[] {
                  ByteString.of(new byte[] {1}), ByteString.NULL_VALUE, ByteString.of(new byte[0])
                }));
    nullableByteStringArray.setAllowNulls(false);
    nullableByteStringArray.getFilterChain().addLast(DemoValueFilters.DISALLOW_NULLS);

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
    nullableFieldsNode.getFilterChain().addLast(DemoValueFilters.DISALLOW_NULLS);
  }

  private void addWritableNull(
      NodeId folderId, String name, NodeId dataTypeId, Variant initialValue) {

    UaVariableNode node =
        addVariable(
            folderId,
            name,
            "Writable " + name + " null fixture (AllowNulls=true)",
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
      Variant initialValue,
      @Nullable EncodingContext unionEncodingContext) {

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
    disallowNode
        .getFilterChain()
        .addLast(
            unionEncodingContext != null
                ? DemoValueFilters.disallowNulls(unionEncodingContext)
                : DemoValueFilters.DISALLOW_NULLS);
  }

  // endregion

  // region 2. Limits/

  private void addLimitNodes(NodeId rootId) {
    UaFolderNode folder =
        addFolder(
            rootId,
            "Limits",
            "MaxStringLength, MaxCharacters, MaxByteStringLength, MaxArrayLength, FixedSizeArray,"
                + " and non-zero ArrayDimensions");

    UaVariableNode maxStringLength =
        addVariable(
            folder.getNodeId(),
            "MaxStringLength",
            "String bounded to MaxStringLength = 16 UTF-8 bytes",
            NodeIds.String,
            ValueRanks.Scalar,
            null,
            Variant.ofString("short text"));
    maxStringLength.setMaxStringLength(uint(16));
    maxStringLength.getFilterChain().addLast(DemoValueFilters.VALUE_LIMITS);

    UaVariableNode maxCharacters =
        addVariable(
            folder.getNodeId(),
            "MaxCharacters",
            "String bounded to MaxCharacters = 8 Unicode code points",
            NodeIds.String,
            ValueRanks.Scalar,
            null,
            Variant.ofString("café"));
    maxCharacters.setProperty(DemoValueFilters.MAX_CHARACTERS, uint(8));
    maxCharacters.getFilterChain().addLast(DemoValueFilters.VALUE_LIMITS);

    UaVariableNode maxByteStringLength =
        addVariable(
            folder.getNodeId(),
            "MaxByteStringLength",
            "ByteString bounded to MaxByteStringLength = 8 bytes",
            NodeIds.ByteString,
            ValueRanks.Scalar,
            null,
            Variant.ofByteString(ByteString.of(new byte[] {1, 2, 3, 4})));
    maxByteStringLength.setProperty(DemoValueFilters.MAX_BYTE_STRING_LENGTH, uint(8));
    maxByteStringLength.getFilterChain().addLast(DemoValueFilters.VALUE_LIMITS);

    UaVariableNode maxArrayLength =
        addVariable(
            folder.getNodeId(),
            "MaxArrayLength",
            "Int32[] bounded to MaxArrayLength = 5 elements",
            NodeIds.Int32,
            ValueRanks.OneDimension,
            ARRAY_DIMS,
            Variant.ofInt32Array(new Integer[] {1, 2, 3}));
    maxArrayLength.setMaxArrayLength(uint(5));
    maxArrayLength.getFilterChain().addLast(DemoValueFilters.VALUE_LIMITS);

    UaVariableNode maxArrayLengthMatrix =
        addVariable(
            folder.getNodeId(),
            "MaxArrayLengthMatrix",
            "2-D Int32 matrix bounded to MaxArrayLength = 6 total elements",
            NodeIds.Int32,
            2,
            MATRIX_DIMS,
            Variant.ofMatrix(Matrix.ofInt32(new Integer[][] {{1, 2, 3}, {4, 5, 6}})));
    maxArrayLengthMatrix.setMaxArrayLength(uint(6));
    maxArrayLengthMatrix.getFilterChain().addLast(DemoValueFilters.VALUE_LIMITS);

    UaVariableNode boundedArray =
        addVariable(
            folder.getNodeId(),
            "BoundedArray",
            "Int32[] with non-zero ArrayDimensions = [5] (maximum length 5)",
            NodeIds.Int32,
            ValueRanks.OneDimension,
            new UInteger[] {uint(5)},
            Variant.ofInt32Array(new Integer[] {10, 20, 30}));
    boundedArray.getFilterChain().addLast(DemoValueFilters.VALUE_LIMITS);

    UaVariableNode boundedMatrix =
        addVariable(
            folder.getNodeId(),
            "BoundedMatrix",
            "2-D Double matrix with non-zero ArrayDimensions = [2, 3]",
            NodeIds.Double,
            2,
            new UInteger[] {uint(2), uint(3)},
            Variant.ofMatrix(Matrix.ofDouble(new Double[][] {{1.0, 2.0, 3.0}, {4.0, 5.0, 6.0}})));
    boundedMatrix.getFilterChain().addLast(DemoValueFilters.VALUE_LIMITS);

    UaVariableNode fixedSize =
        addVariable(
            folder.getNodeId(),
            "FixedSizeArray",
            "Int32[] with ArrayDimensions = [4] and FixedSizeArray = true (exact length 4)",
            NodeIds.Int32,
            ValueRanks.OneDimension,
            new UInteger[] {uint(4)},
            Variant.ofInt32Array(new Integer[] {1, 2, 3, 4}));
    fixedSize.setProperty(DemoValueFilters.FIXED_SIZE_ARRAY, true);
    fixedSize.getFilterChain().addLast(DemoValueFilters.VALUE_LIMITS);
  }

  // endregion

  // region 3. Access/

  private void addAccessNodes(
      NodeId rootId, NamespaceTable nsTable, EncodingContext encodingContext) {

    UaFolderNode folder =
        addFolder(
            rootId,
            "Access",
            "WriteFullArrayOnly, NoSubDataTypes vs Subtypable, write-only, and read-only"
                + " variables");

    NodeId concreteTestTypeId =
        DataTypeTestNodeIds.ConcreteTestType.toNodeId(nsTable).orElseThrow();
    NodeId concreteTestTypeEncodingId =
        ConcreteTestType.BINARY_ENCODING_ID.toNodeId(nsTable).orElseThrow();

    // WriteFullArrayOnly (AccessLevelEx bit 10)
    UaVariableNode writeFullArrayOnly =
        addVariable(
            folder.getNodeId(),
            "WriteFullArrayOnly",
            "Int32[] with AccessLevelEx.WriteFullArrayOnly refusing index-range writes with"
                + " Bad_WriteNotSupported",
            NodeIds.Int32,
            ValueRanks.OneDimension,
            ARRAY_DIMS,
            Variant.ofInt32Array(new Integer[] {10, 20, 30, 40}));
    writeFullArrayOnly.setAccessLevelEx(
        AccessLevelExType.of(
            AccessLevelExType.Field.CurrentRead,
            AccessLevelExType.Field.CurrentWrite,
            AccessLevelExType.Field.WriteFullArrayOnly));

    // NoSubDataTypes (AccessLevelEx bit 11) + paired subtypable contrast using ConcreteTestType(Ex)
    UaVariableNode noSubDataTypes =
        addVariable(
            folder.getNodeId(),
            "NoSubDataTypes",
            "ConcreteTestType with AccessLevelEx.NoSubDataTypes rejecting ConcreteTestTypeEx with"
                + " Bad_TypeMismatch",
            concreteTestTypeId,
            ValueRanks.Scalar,
            null,
            Variant.ofExtensionObject(
                ExtensionObject.encode(
                    encodingContext, new ConcreteTestType((short) 1, 1.0, "exact-only", true))));
    noSubDataTypes.setAccessLevelEx(
        AccessLevelExType.of(
            AccessLevelExType.Field.CurrentRead,
            AccessLevelExType.Field.CurrentWrite,
            AccessLevelExType.Field.NoSubDataTypes));
    noSubDataTypes
        .getFilterChain()
        .addLast(DemoValueFilters.noSubDataTypes(concreteTestTypeEncodingId));

    UaVariableNode subtypable =
        addVariable(
            folder.getNodeId(),
            "Subtypable",
            "ConcreteTestType without NoSubDataTypes accepting ConcreteTestType and"
                + " ConcreteTestTypeEx",
            concreteTestTypeId,
            ValueRanks.Scalar,
            null,
            Variant.ofExtensionObject(
                ExtensionObject.encode(
                    encodingContext,
                    new ConcreteTestTypeEx((short) 2, 2.0, "derived", false, uint(42)))));
    subtypable.setAccessLevelEx(
        AccessLevelExType.of(
            AccessLevelExType.Field.CurrentRead, AccessLevelExType.Field.CurrentWrite));

    // WriteOnly (AccessLevel = CurrentWrite -> Bad_NotReadable)
    UaVariableNode writeOnly =
        new UaVariableNodeBuilder(getNodeContext())
            .setNodeId(deriveChildNodeId(folder.getNodeId(), "WriteOnly"))
            .setBrowseName(new QualifiedName(namespaceIndex, "WriteOnly"))
            .setDisplayName(LocalizedText.english("WriteOnly"))
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
    addChildNode(folder.getNodeId(), writeOnly);

    // WriteOnlyUser (AccessLevel = ReadWrite, UserAccessLevel = CurrentWrite ->
    // Bad_UserAccessDenied)
    UaVariableNode writeOnlyUser =
        new UaVariableNodeBuilder(getNodeContext())
            .setNodeId(deriveChildNodeId(folder.getNodeId(), "WriteOnlyUser"))
            .setBrowseName(new QualifiedName(namespaceIndex, "WriteOnlyUser"))
            .setDisplayName(LocalizedText.english("WriteOnlyUser"))
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
    addChildNode(folder.getNodeId(), writeOnlyUser);

    // ReadOnly (AccessLevel = CurrentRead -> Bad_NotWritable)
    UaVariableNode readOnly =
        new UaVariableNodeBuilder(getNodeContext())
            .setNodeId(deriveChildNodeId(folder.getNodeId(), "ReadOnly"))
            .setBrowseName(new QualifiedName(namespaceIndex, "ReadOnly"))
            .setDisplayName(LocalizedText.english("ReadOnly"))
            .setDescription(
                LocalizedText.english(
                    "Read-only Int32 (AccessLevel=CurrentRead): Value write returns"
                        + " Bad_NotWritable"))
            .setDataType(NodeIds.Int32)
            .setValueRank(ValueRanks.Scalar)
            .setAccessLevel(AccessLevel.toValue(AccessLevel.CurrentRead))
            .setUserAccessLevel(AccessLevel.toValue(AccessLevel.CurrentRead))
            .setAccessLevelEx(AccessLevelExType.of(AccessLevelExType.Field.CurrentRead))
            .setMinimumSamplingInterval(MIN_SUPPORTED_SAMPLE_RATE)
            .setValue(new DataValue(Variant.ofInt32(42)))
            .build();
    addChildNode(folder.getNodeId(), readOnly);
  }

  // endregion

  // region 4. Restrictions/

  private void addRestrictionNodes(
      NodeId rootId, NamespaceTable nsTable, EncodingContext encodingContext) {

    UaFolderNode folder =
        addFolder(
            rootId,
            "Restrictions",
            "OPC UA 1.05 SubtypeRestriction (Part 3 §5.8.6) and DataTypeRefinement (Part 3"
                + " §5.8.5)");

    NodeId refinableStructId = RefinableStruct.TYPE_ID.toNodeId(nsTable).orElseThrow();
    NodeId refinableStructEncodingId =
        RefinableStruct.BINARY_ENCODING_ID.toNodeId(nsTable).orElseThrow();

    addSubtypeRestrictionNodes(folder.getNodeId());
    addDataTypeRefinementNodes(
        folder.getNodeId(), refinableStructId, refinableStructEncodingId, encodingContext);
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
        .addLast(DemoValueFilters.subtypeRestriction(Set.of(Integer.class, Double.class)));
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
              DemoValueFilters.dataTypeRefinement(
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

  // region 5. Scale/

  private void addScaleNodes(
      NodeId rootId, NamespaceTable nsTable, EncodingContext encodingContext) {

    UaFolderNode folder =
        addFolder(
            rootId, "Scale", "Large 100,000-element array and 25-level deeply nested structure");

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
        ARRAY_DIMS,
        Variant.ofInt32Array(largeValues));

    NodeId nestedNodeId = NestedNode.TYPE_ID.toNodeId(nsTable).orElseThrow();
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

    UaVariableNode node = builder.build();
    addChildNode(parentId, node);
    return node;
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

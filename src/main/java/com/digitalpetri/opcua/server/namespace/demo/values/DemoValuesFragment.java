package com.digitalpetri.opcua.server.namespace.demo.values;

import static com.digitalpetri.opcua.server.DemoConfigLimits.MIN_SUPPORTED_SAMPLE_RATE;
import static com.digitalpetri.opcua.server.namespace.demo.Util.deriveChildNodeId;
import static com.digitalpetri.opcua.server.namespace.demo.Util.getDefaultArrayValue;
import static com.digitalpetri.opcua.server.namespace.demo.Util.getDefaultMatrixValue;
import static com.digitalpetri.opcua.server.namespace.demo.Util.getDefaultScalarValue;
import static org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.Unsigned.ubyte;
import static org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.Unsigned.uint;
import static org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.Unsigned.ulong;
import static org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.Unsigned.ushort;

import com.digitalpetri.opcua.server.namespace.demo.DemoAddressSpaceFragment;
import com.digitalpetri.opcua.server.namespace.demo.DemoNamespace;
import com.digitalpetri.opcua.server.namespace.demo.DemoValueFilters;
import com.digitalpetri.opcua.server.namespace.demo.EuRangeCheckFilter;
import com.digitalpetri.opcua.server.namespace.demo.types.DecimalEncoding;
import com.digitalpetri.opcua.server.namespace.demo.types.DemoOptionSet;
import com.digitalpetri.opcua.server.namespace.demo.types.DemoRecord;
import com.digitalpetri.opcua.server.namespace.demo.types.DemoTypesFragment;
import com.digitalpetri.opcua.server.namespace.demo.types.NullableFieldsStruct;
import com.digitalpetri.opcua.server.namespace.demo.types.StructWithOptionSet;
import com.digitalpetri.opcua.test.DataTypeTestNodeIds;
import com.digitalpetri.opcua.test.types.ConcreteTestType;
import com.digitalpetri.opcua.test.types.ConcreteTestTypeEx;
import com.digitalpetri.opcua.test.types.StructWithAbstractArrayFields;
import com.digitalpetri.opcua.test.types.StructWithAbstractMatrixFields;
import com.digitalpetri.opcua.test.types.StructWithAbstractScalarFields;
import com.digitalpetri.opcua.test.types.StructWithBuiltinArrayFields;
import com.digitalpetri.opcua.test.types.StructWithBuiltinArrayFieldsEx;
import com.digitalpetri.opcua.test.types.StructWithBuiltinMatrixFields;
import com.digitalpetri.opcua.test.types.StructWithBuiltinMatrixFieldsEx;
import com.digitalpetri.opcua.test.types.StructWithBuiltinScalarFields;
import com.digitalpetri.opcua.test.types.StructWithBuiltinScalarFieldsEx;
import com.digitalpetri.opcua.test.types.StructWithOptionalArrayFields;
import com.digitalpetri.opcua.test.types.StructWithOptionalMatrixFields;
import com.digitalpetri.opcua.test.types.StructWithOptionalScalarFields;
import com.digitalpetri.opcua.test.types.StructWithStructureArrayFields;
import com.digitalpetri.opcua.test.types.StructWithStructureMatrixFields;
import com.digitalpetri.opcua.test.types.StructWithStructureScalarFields;
import com.digitalpetri.opcua.test.types.TestEnumType;
import com.digitalpetri.opcua.test.types.UnionOfArray;
import com.digitalpetri.opcua.test.types.UnionOfMatrix;
import com.digitalpetri.opcua.test.types.UnionOfScalar;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
import org.eclipse.milo.opcua.sdk.core.AccessLevel;
import org.eclipse.milo.opcua.sdk.core.CefactEngineeringUnits;
import org.eclipse.milo.opcua.sdk.core.Reference;
import org.eclipse.milo.opcua.sdk.core.Reference.Direction;
import org.eclipse.milo.opcua.sdk.core.ValueRanks;
import org.eclipse.milo.opcua.sdk.server.AddressSpaceFilter;
import org.eclipse.milo.opcua.sdk.server.OpcUaServer;
import org.eclipse.milo.opcua.sdk.server.SimpleAddressSpaceFilter;
import org.eclipse.milo.opcua.sdk.server.model.variables.AnalogItemTypeNode;
import org.eclipse.milo.opcua.sdk.server.model.variables.AnalogUnitTypeNode;
import org.eclipse.milo.opcua.sdk.server.model.variables.ImageItemTypeNode;
import org.eclipse.milo.opcua.sdk.server.model.variables.MultiStateDiscreteTypeNode;
import org.eclipse.milo.opcua.sdk.server.model.variables.MultiStateValueDiscreteTypeNode;
import org.eclipse.milo.opcua.sdk.server.model.variables.TwoStateDiscreteTypeNode;
import org.eclipse.milo.opcua.sdk.server.model.variables.XYArrayItemTypeNode;
import org.eclipse.milo.opcua.sdk.server.model.variables.YArrayItemTypeNode;
import org.eclipse.milo.opcua.sdk.server.nodes.UaFolderNode;
import org.eclipse.milo.opcua.sdk.server.nodes.UaNode;
import org.eclipse.milo.opcua.sdk.server.nodes.UaServerNode;
import org.eclipse.milo.opcua.sdk.server.nodes.UaVariableNode;
import org.eclipse.milo.opcua.sdk.server.nodes.UaVariableNode.UaVariableNodeBuilder;
import org.eclipse.milo.opcua.sdk.server.nodes.instantiation.InstantiationRequest;
import org.eclipse.milo.opcua.stack.core.AttributeId;
import org.eclipse.milo.opcua.stack.core.NamespaceTable;
import org.eclipse.milo.opcua.stack.core.NodeIds;
import org.eclipse.milo.opcua.stack.core.OpcUaDataType;
import org.eclipse.milo.opcua.stack.core.ReferenceTypes;
import org.eclipse.milo.opcua.stack.core.StatusCodes;
import org.eclipse.milo.opcua.stack.core.UaException;
import org.eclipse.milo.opcua.stack.core.encoding.EncodingContext;
import org.eclipse.milo.opcua.stack.core.types.UaStructuredType;
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
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.ULong;
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.UShort;
import org.eclipse.milo.opcua.stack.core.types.enumerated.ApplicationType;
import org.eclipse.milo.opcua.stack.core.types.enumerated.AxisScaleEnumeration;
import org.eclipse.milo.opcua.stack.core.types.structured.AccessLevelExType;
import org.eclipse.milo.opcua.stack.core.types.structured.AccessLevelType;
import org.eclipse.milo.opcua.stack.core.types.structured.AccessRestrictionType;
import org.eclipse.milo.opcua.stack.core.types.structured.AxisInformation;
import org.eclipse.milo.opcua.stack.core.types.structured.EnumValueType;
import org.eclipse.milo.opcua.stack.core.types.structured.Range;
import org.eclipse.milo.opcua.stack.core.types.structured.WriteValue;
import org.eclipse.milo.opcua.stack.core.types.structured.XVType;
import org.jspecify.annotations.Nullable;

/**
 * Address-space fragment hosting {@code Demo/Values}, organized category-first across every OPC UA
 * data type family and arity ({@code Scalar}, {@code Array}, {@code Matrix}), plus representative
 * Part 8 DataAccess VariableTypes.
 */
public final class DemoValuesFragment extends DemoAddressSpaceFragment {

  private static final UInteger[] ARRAY_DIMS = new UInteger[] {uint(0)};
  private static final UInteger[] MATRIX_DIMS = new UInteger[] {uint(0), uint(0)};
  private static final UUID SAMPLE_UUID = UUID.fromString("12345678-1234-5678-9abc-def012345678");

  private final DemoNamespace namespace;
  private final UShort namespaceIndex;
  private final SimpleAddressSpaceFilter filter;

  public DemoValuesFragment(OpcUaServer server, DemoNamespace namespace) {
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
   * Intercepts {@code Decimal} ({@code i=50}) scalar, array, and matrix binary {@link
   * ExtensionObject} writes, working around Milo's {@code DataTypeTree} mapping {@code Decimal} to
   * {@code Number.class}.
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
          && node instanceof UaVariableNode variableNode
          && NodeIds.Decimal.equals(variableNode.getDataType())) {
        results.add(writeDecimalValue(context, writeValue, variableNode));
        continue;
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
    int valueRank = variableNode.getValueRank();

    if (raw == null) {
      if (!Boolean.TRUE.equals(variableNode.getAllowNulls())) {
        return new StatusCode(StatusCodes.Bad_TypeMismatch);
      }
    } else {
      try {
        validateDecimalPayload(valueRank, raw);
      } catch (UaException e) {
        e.getDiagnostic().ifPresent(d -> context.getOperationDiagnostics().report(writeValue, d));
        return e.getStatusCode();
      }
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

  private static void validateDecimalPayload(int valueRank, Object raw) throws UaException {
    if (valueRank == ValueRanks.Scalar) {
      if (!(raw instanceof ExtensionObject xo)) {
        throw new UaException(StatusCodes.Bad_TypeMismatch, "Expected scalar Decimal value");
      }
      DecimalEncoding.decode(xo);
    } else if (valueRank == ValueRanks.OneDimension) {
      if (!(raw instanceof ExtensionObject[] elements)) {
        throw new UaException(StatusCodes.Bad_TypeMismatch, "Expected 1-D Decimal array");
      }
      for (ExtensionObject xo : elements) {
        if (xo == null) {
          throw new UaException(StatusCodes.Bad_TypeMismatch, "Null Decimal array element");
        }
        DecimalEncoding.decode(xo);
      }
    } else if (valueRank >= 2) {
      if (!(raw instanceof Matrix matrix)
          || matrix.getValueRank() != valueRank
          || !(matrix.getElements() instanceof ExtensionObject[] elements)) {
        throw new UaException(
            StatusCodes.Bad_TypeMismatch, "Expected " + valueRank + "-D Decimal matrix");
      }
      for (ExtensionObject xo : elements) {
        if (xo == null) {
          throw new UaException(StatusCodes.Bad_TypeMismatch, "Null Decimal matrix element");
        }
        DecimalEncoding.decode(xo);
      }
    } else {
      throw new UaException(StatusCodes.Bad_TypeMismatch, "Unsupported Decimal ValueRank");
    }
  }

  private void addNodes() {
    NamespaceTable nsTable = getServer().getNamespaceTable();
    EncodingContext encodingContext = getServer().getStaticEncodingContext();

    UaFolderNode valuesFolder =
        addFolder(
            namespace.getDemoFolder().getNodeId(),
            "Values",
            "Writable OPC UA values organized by type family and arity (Scalar, Array, Matrix)");

    NodeId valuesId = valuesFolder.getNodeId();
    addBuiltinNodes(valuesId);
    addVariantNodes(valuesId);
    addEnumerationNodes(valuesId, nsTable);
    addOptionSetNodes(valuesId, nsTable, encodingContext);
    addUnionNodes(valuesId, nsTable, encodingContext);
    addStructureNodes(valuesId, nsTable, encodingContext);
    addAbstractNodes(valuesId, nsTable, encodingContext);
    addDataAccessNodes(valuesId);
  }

  // region 1. Builtin/{Scalar, Array, Matrix}

  private void addBuiltinNodes(NodeId valuesId) {
    ArityFolders folders =
        addArityFolders(valuesId, "Builtin", "All 24 built-in OPC UA types plus Decimal (i=50)");

    for (OpcUaDataType dataType : OpcUaDataType.values()) {
      if (dataType == OpcUaDataType.DiagnosticInfo) {
        continue;
      }

      Object scalarValue = getDefaultScalarValue(dataType);
      Variant scalarVariant = scalarValue instanceof Variant v ? v : Variant.of(scalarValue);
      addVariable(
          folders.scalar(),
          dataType.name(),
          "Built-in " + dataType.name() + " scalar",
          dataType.getNodeId(),
          ValueRanks.Scalar,
          null,
          scalarVariant);

      Object arrayValue = getDefaultArrayValue(dataType);
      Variant arrayVariant = arrayValue instanceof Variant v ? v : Variant.of(arrayValue);
      addVariable(
          folders.array(),
          dataType.name(),
          "Built-in " + dataType.name() + " 1-D array",
          dataType.getNodeId(),
          ValueRanks.OneDimension,
          ARRAY_DIMS,
          arrayVariant);

      Matrix matrixValue = getDefaultMatrixValue(dataType);
      addVariable(
          folders.matrix(),
          dataType.name(),
          "Built-in " + dataType.name() + " 2-D matrix",
          dataType.getNodeId(),
          2,
          MATRIX_DIMS,
          Variant.ofMatrix(matrixValue));
    }

    // Built-in Decimal (i=50), Part 6 §5.1.10
    UaVariableNode decimalScalar =
        addVariable(
            folders.scalar(),
            "Decimal",
            "Built-in Decimal (i=50) scalar encoded per Part 6 §5.1.10",
            NodeIds.Decimal,
            ValueRanks.Scalar,
            null,
            Variant.ofExtensionObject(DecimalEncoding.encode(new BigDecimal("123.45"))));
    decimalScalar.setAllowNulls(false);

    UaVariableNode decimalArray =
        addVariable(
            folders.array(),
            "Decimal",
            "Built-in Decimal (i=50) 1-D array encoded per Part 6 §5.1.10",
            NodeIds.Decimal,
            ValueRanks.OneDimension,
            ARRAY_DIMS,
            Variant.ofExtensionObjectArray(
                new ExtensionObject[] {
                  DecimalEncoding.encode(new BigDecimal("12.34")),
                  DecimalEncoding.encode(new BigDecimal("-56.78")),
                  DecimalEncoding.encode(new BigDecimal("0.00"))
                }));
    decimalArray.setAllowNulls(false);

    UaVariableNode decimalMatrix =
        addVariable(
            folders.matrix(),
            "Decimal",
            "Built-in Decimal (i=50) 2-D matrix encoded per Part 6 §5.1.10",
            NodeIds.Decimal,
            2,
            MATRIX_DIMS,
            Variant.ofMatrix(
                Matrix.ofExtensionObject(
                    new ExtensionObject[][] {
                      {
                        DecimalEncoding.encode(new BigDecimal("1.10")),
                        DecimalEncoding.encode(new BigDecimal("2.20"))
                      },
                      {
                        DecimalEncoding.encode(new BigDecimal("3.30")),
                        DecimalEncoding.encode(new BigDecimal("4.40"))
                      }
                    })));
    decimalMatrix.setAllowNulls(false);
  }

  // endregion

  // region 2. Variant/{Scalar, Array, Matrix}

  private void addVariantNodes(NodeId valuesId) {
    ArityFolders folders =
        addArityFolders(
            valuesId, "Variant", "BaseDataType variables holding each built-in type plus mixed");

    var mixedArray = new ArrayList<Variant>();
    var mixedMatrix = new ArrayList<Variant[]>();

    for (OpcUaDataType dataType : OpcUaDataType.values()) {
      if (dataType == OpcUaDataType.DiagnosticInfo || dataType == OpcUaDataType.Variant) {
        continue;
      }

      Variant scalar = Variant.of(getDefaultScalarValue(dataType));
      addVariable(
          folders.scalar(),
          dataType.name(),
          "BaseDataType scalar carrying " + dataType.name(),
          NodeIds.BaseDataType,
          ValueRanks.Scalar,
          null,
          scalar);

      Variant[] array = new Variant[5];
      for (int i = 0; i < array.length; i++) {
        array[i] = Variant.of(getDefaultScalarValue(dataType));
      }
      addVariable(
          folders.array(),
          dataType.name(),
          "BaseDataType 1-D array carrying " + dataType.name() + " elements",
          NodeIds.BaseDataType,
          ValueRanks.OneDimension,
          ARRAY_DIMS,
          Variant.ofVariantArray(array));

      Variant[][] matrix = new Variant[2][3];
      for (int r = 0; r < matrix.length; r++) {
        for (int c = 0; c < matrix[r].length; c++) {
          matrix[r][c] = Variant.of(getDefaultScalarValue(dataType));
        }
      }
      addVariable(
          folders.matrix(),
          dataType.name(),
          "BaseDataType 2-D matrix carrying " + dataType.name() + " elements",
          NodeIds.BaseDataType,
          2,
          MATRIX_DIMS,
          Variant.ofMatrix(Matrix.ofVariant(matrix)));

      mixedArray.add(Variant.of(getDefaultScalarValue(dataType)));
      mixedMatrix.add(
          new Variant[] {
            Variant.of(getDefaultScalarValue(dataType)),
            Variant.of(getDefaultScalarValue(dataType)),
            Variant.of(getDefaultScalarValue(dataType))
          });
    }

    addVariable(
        folders.array(),
        "Mixed",
        "BaseDataType 1-D array holding heterogeneous Variant elements across all built-in types",
        NodeIds.BaseDataType,
        ValueRanks.OneDimension,
        ARRAY_DIMS,
        Variant.ofVariantArray(mixedArray.toArray(new Variant[0])));

    addVariable(
        folders.matrix(),
        "Mixed",
        "BaseDataType 2-D matrix holding heterogeneous Variant rows across all built-in types",
        NodeIds.BaseDataType,
        2,
        MATRIX_DIMS,
        Variant.ofMatrix(Matrix.ofVariant(mixedMatrix.toArray(new Variant[0][]))));
  }

  // endregion

  // region 3. Enumeration/{Scalar, Array, Matrix}

  private void addEnumerationNodes(NodeId valuesId, NamespaceTable nsTable) {
    ArityFolders folders =
        addArityFolders(
            valuesId,
            "Enumeration",
            "Contiguous, non-contiguous EnumValues, and restricting subtype Enumerations");

    NodeId testEnumId = DataTypeTestNodeIds.TestEnumType.toNodeId(nsTable).orElseThrow();
    addVariable(
        folders.scalar(),
        "TestEnumType",
        "Contiguous TestEnumType scalar (0=A, 1=B, 2=C)",
        testEnumId,
        ValueRanks.Scalar,
        null,
        Variant.ofEnum(TestEnumType.A));
    addVariable(
        folders.array(),
        "TestEnumType",
        "Contiguous TestEnumType 1-D array",
        testEnumId,
        ValueRanks.OneDimension,
        ARRAY_DIMS,
        Variant.of(new TestEnumType[] {TestEnumType.A, TestEnumType.B, TestEnumType.C}));
    addVariable(
        folders.matrix(),
        "TestEnumType",
        "Contiguous TestEnumType 2-D matrix",
        testEnumId,
        2,
        MATRIX_DIMS,
        Variant.of(
            Matrix.ofEnum(
                new TestEnumType[][] {
                  {TestEnumType.A, TestEnumType.B, TestEnumType.C},
                  {TestEnumType.C, TestEnumType.B, TestEnumType.A}
                })));

    NodeId sparseEnumId =
        new NodeId(namespaceIndex, DemoTypesFragment.SPARSE_STATUS_ENUM_IDENTIFIER);
    UaVariableNode sparseScalar =
        addVariable(
            folders.scalar(),
            "SparseStatusEnum",
            "SparseStatusEnum scalar (defined values: 1, 2, 4, 8, 16)",
            sparseEnumId,
            ValueRanks.Scalar,
            null,
            Variant.ofInt32(2));
    sparseScalar
        .getFilterChain()
        .addLast(DemoValueFilters.enumValuesCheck(DemoTypesFragment.SPARSE_ENUM_VALUES));

    UaVariableNode sparseArray =
        addVariable(
            folders.array(),
            "SparseStatusEnum",
            "SparseStatusEnum 1-D array (defined values: 1, 2, 4, 8, 16)",
            sparseEnumId,
            ValueRanks.OneDimension,
            ARRAY_DIMS,
            Variant.ofInt32Array(new Integer[] {1, 4, 16}));
    sparseArray
        .getFilterChain()
        .addLast(DemoValueFilters.enumValuesCheck(DemoTypesFragment.SPARSE_ENUM_VALUES));

    UaVariableNode sparseMatrix =
        addVariable(
            folders.matrix(),
            "SparseStatusEnum",
            "SparseStatusEnum 2-D matrix (defined values: 1, 2, 4, 8, 16)",
            sparseEnumId,
            2,
            MATRIX_DIMS,
            Variant.ofMatrix(Matrix.ofInt32(new Integer[][] {{1, 2}, {4, 16}})));
    sparseMatrix
        .getFilterChain()
        .addLast(DemoValueFilters.enumValuesCheck(DemoTypesFragment.SPARSE_ENUM_VALUES));

    NodeId operationalEnumId =
        new NodeId(namespaceIndex, DemoTypesFragment.OPERATIONAL_STATUS_ENUM_IDENTIFIER);
    UaVariableNode operationalScalar =
        addVariable(
            folders.scalar(),
            "OperationalStatusEnum",
            "OperationalStatusEnum subtype scalar (defined values: 1, 2, 4; 8 and 16 removed)",
            operationalEnumId,
            ValueRanks.Scalar,
            null,
            Variant.ofInt32(1));
    operationalScalar
        .getFilterChain()
        .addLast(DemoValueFilters.enumValuesCheck(DemoTypesFragment.OPERATIONAL_ENUM_VALUES));

    UaVariableNode operationalArray =
        addVariable(
            folders.array(),
            "OperationalStatusEnum",
            "OperationalStatusEnum subtype 1-D array (defined values: 1, 2, 4)",
            operationalEnumId,
            ValueRanks.OneDimension,
            ARRAY_DIMS,
            Variant.ofInt32Array(new Integer[] {1, 2, 4}));
    operationalArray
        .getFilterChain()
        .addLast(DemoValueFilters.enumValuesCheck(DemoTypesFragment.OPERATIONAL_ENUM_VALUES));

    UaVariableNode operationalMatrix =
        addVariable(
            folders.matrix(),
            "OperationalStatusEnum",
            "OperationalStatusEnum subtype 2-D matrix (defined values: 1, 2, 4)",
            operationalEnumId,
            2,
            MATRIX_DIMS,
            Variant.ofMatrix(Matrix.ofInt32(new Integer[][] {{1, 2}, {4, 1}})));
    operationalMatrix
        .getFilterChain()
        .addLast(DemoValueFilters.enumValuesCheck(DemoTypesFragment.OPERATIONAL_ENUM_VALUES));
  }

  // endregion

  // region 4. OptionSet/{Scalar, Array, Matrix}

  private void addOptionSetNodes(
      NodeId valuesId, NamespaceTable nsTable, EncodingContext encodingContext) {

    ArityFolders folders =
        addArityFolders(
            valuesId,
            "OptionSet",
            "Structure OptionSet subtypes (Part 3 §8.40) and integer bitmask OptionSets");

    NodeId demoOptionSetId = DemoOptionSet.TYPE_ID.toNodeId(nsTable).orElseThrow();
    NodeId demoOptionSetEncodingId =
        DemoOptionSet.BINARY_ENCODING_ID.toNodeId(nsTable).orElseThrow();
    NodeId structWithOptionSetId = StructWithOptionSet.TYPE_ID.toNodeId(nsTable).orElseThrow();
    NodeId structWithOptionSetEncodingId =
        StructWithOptionSet.BINARY_ENCODING_ID.toNodeId(nsTable).orElseThrow();
    NodeId demoOptionSetUInt32Id =
        new NodeId(namespaceIndex, DemoTypesFragment.DEMO_OPTION_SET_UINT32_IDENTIFIER);

    // DemoOptionSet (Structure OptionSet)
    UaVariableNode optionSetScalar =
        addVariable(
            folders.scalar(),
            "DemoOptionSet",
            "Concrete 2-byte OptionSet subtype (bits 0-3, 8-9; gaps 4-7, 10-15)",
            demoOptionSetId,
            ValueRanks.Scalar,
            null,
            Variant.ofExtensionObject(encode(encodingContext, DemoOptionSet.of(0x03, 0x01))));
    optionSetScalar
        .getFilterChain()
        .addLast(DemoValueFilters.optionSetScalar(encodingContext, demoOptionSetEncodingId));

    UaVariableNode optionSetArray =
        addVariable(
            folders.array(),
            "DemoOptionSet",
            "1-D array of DemoOptionSet values",
            demoOptionSetId,
            ValueRanks.OneDimension,
            ARRAY_DIMS,
            Variant.ofExtensionObjectArray(
                new ExtensionObject[] {
                  encode(encodingContext, DemoOptionSet.of(0x01, 0x00)),
                  encode(encodingContext, DemoOptionSet.of(0x0A, 0x03))
                }));
    optionSetArray
        .getFilterChain()
        .addLast(DemoValueFilters.optionSetArray(encodingContext, demoOptionSetEncodingId));

    UaVariableNode optionSetMatrix =
        addVariable(
            folders.matrix(),
            "DemoOptionSet",
            "2-D matrix of DemoOptionSet values",
            demoOptionSetId,
            2,
            MATRIX_DIMS,
            Variant.ofMatrix(
                Matrix.ofExtensionObject(
                    new ExtensionObject[][] {
                      {
                        encode(encodingContext, DemoOptionSet.of(0x01, 0x00)),
                        encode(encodingContext, DemoOptionSet.of(0x02, 0x01))
                      },
                      {
                        encode(encodingContext, DemoOptionSet.of(0x04, 0x02)),
                        encode(encodingContext, DemoOptionSet.of(0x0F, 0x03))
                      }
                    })));
    optionSetMatrix
        .getFilterChain()
        .addLast(DemoValueFilters.optionSetMatrix(encodingContext, demoOptionSetEncodingId));

    // StructWithOptionSet
    UaVariableNode structScalar =
        addVariable(
            folders.scalar(),
            "StructWithOptionSet",
            "Structure carrying a DemoOptionSet field",
            structWithOptionSetId,
            ValueRanks.Scalar,
            null,
            Variant.ofExtensionObject(
                encode(
                    encodingContext,
                    new StructWithOptionSet("DefaultProfile", DemoOptionSet.of(0x05, 0x02)))));
    structScalar
        .getFilterChain()
        .addLast(
            DemoValueFilters.structWithOptionSetScalar(
                encodingContext, structWithOptionSetEncodingId));

    UaVariableNode structArray =
        addVariable(
            folders.array(),
            "StructWithOptionSet",
            "1-D array of StructWithOptionSet values",
            structWithOptionSetId,
            ValueRanks.OneDimension,
            ARRAY_DIMS,
            Variant.ofExtensionObjectArray(
                new ExtensionObject[] {
                  encode(
                      encodingContext,
                      new StructWithOptionSet("ProfileA", DemoOptionSet.of(0x01, 0x00))),
                  encode(
                      encodingContext,
                      new StructWithOptionSet("ProfileB", DemoOptionSet.of(0x0A, 0x03)))
                }));
    structArray
        .getFilterChain()
        .addLast(
            DemoValueFilters.structWithOptionSetArray(
                encodingContext, structWithOptionSetEncodingId));

    UaVariableNode structMatrix =
        addVariable(
            folders.matrix(),
            "StructWithOptionSet",
            "2-D matrix of StructWithOptionSet values",
            structWithOptionSetId,
            2,
            MATRIX_DIMS,
            Variant.ofMatrix(
                Matrix.ofExtensionObject(
                    new ExtensionObject[][] {
                      {
                        encode(
                            encodingContext,
                            new StructWithOptionSet("P00", DemoOptionSet.of(0x01, 0x00))),
                        encode(
                            encodingContext,
                            new StructWithOptionSet("P01", DemoOptionSet.of(0x02, 0x01)))
                      },
                      {
                        encode(
                            encodingContext,
                            new StructWithOptionSet("P10", DemoOptionSet.of(0x04, 0x02))),
                        encode(
                            encodingContext,
                            new StructWithOptionSet("P11", DemoOptionSet.of(0x0F, 0x03)))
                      }
                    })));
    structMatrix
        .getFilterChain()
        .addLast(
            DemoValueFilters.structWithOptionSetMatrix(
                encodingContext, structWithOptionSetEncodingId));

    // DemoOptionSetUInt32 (UInt32 bitmask with gap bit 2)
    addVariable(
        folders.scalar(),
        "DemoOptionSetUInt32",
        "UInt32 OptionSet scalar (bits 0=Read, 1=Write, gap 2, 3=Execute, 4=Admin)",
        demoOptionSetUInt32Id,
        ValueRanks.Scalar,
        null,
        Variant.ofUInt32(uint(0b01011)));
    addVariable(
        folders.array(),
        "DemoOptionSetUInt32",
        "UInt32 OptionSet 1-D array",
        demoOptionSetUInt32Id,
        ValueRanks.OneDimension,
        ARRAY_DIMS,
        Variant.ofUInt32Array(new UInteger[] {uint(0b00001), uint(0b11010)}));
    addVariable(
        folders.matrix(),
        "DemoOptionSetUInt32",
        "UInt32 OptionSet 2-D matrix",
        demoOptionSetUInt32Id,
        2,
        MATRIX_DIMS,
        Variant.ofMatrix(
            Matrix.ofUInt32(
                new UInteger[][] {
                  {uint(0b00001), uint(0b00011)},
                  {uint(0b01001), uint(0b11011)}
                })));

    // Standard PermissionType (i=94, UInt32 bitmask)
    addVariable(
        folders.scalar(),
        "PermissionType",
        "Standard PermissionType (i=94) UInt32 OptionSet scalar",
        NodeIds.PermissionType,
        ValueRanks.Scalar,
        null,
        Variant.ofUInt32(uint(0x0003)));
    addVariable(
        folders.array(),
        "PermissionType",
        "Standard PermissionType (i=94) UInt32 OptionSet 1-D array",
        NodeIds.PermissionType,
        ValueRanks.OneDimension,
        ARRAY_DIMS,
        Variant.ofUInt32Array(new UInteger[] {uint(0x0001), uint(0x0011)}));
    addVariable(
        folders.matrix(),
        "PermissionType",
        "Standard PermissionType (i=94) UInt32 OptionSet 2-D matrix",
        NodeIds.PermissionType,
        2,
        MATRIX_DIMS,
        Variant.ofMatrix(
            Matrix.ofUInt32(
                new UInteger[][] {
                  {uint(0x0001), uint(0x0003)},
                  {uint(0x0011), uint(0x0021)}
                })));

    // Standard AccessLevelType (i=15031, Byte bitmask)
    addVariable(
        folders.scalar(),
        "AccessLevelType",
        "Standard AccessLevelType (i=15031) Byte OptionSet scalar",
        NodeIds.AccessLevelType,
        ValueRanks.Scalar,
        null,
        Variant.ofByte(ubyte(0x03)));
    addVariable(
        folders.array(),
        "AccessLevelType",
        "Standard AccessLevelType (i=15031) Byte OptionSet 1-D array",
        NodeIds.AccessLevelType,
        ValueRanks.OneDimension,
        ARRAY_DIMS,
        Variant.ofByteArray(new UByte[] {ubyte(0x01), ubyte(0x03)}));
    addVariable(
        folders.matrix(),
        "AccessLevelType",
        "Standard AccessLevelType (i=15031) Byte OptionSet 2-D matrix",
        NodeIds.AccessLevelType,
        2,
        MATRIX_DIMS,
        Variant.ofMatrix(
            Matrix.ofByte(
                new UByte[][] {
                  {ubyte(0x01), ubyte(0x03)},
                  {ubyte(0x02), ubyte(0x07)}
                })));
  }

  // endregion

  // region 5. Union/{Scalar, Array, Matrix}

  private void addUnionNodes(
      NodeId valuesId, NamespaceTable nsTable, EncodingContext encodingContext) {

    ArityFolders folders =
        addArityFolders(
            valuesId,
            "Union",
            "OPC UA Union DataTypes (scalar, array, and matrix branches) across all arities");

    NodeId unionScalarId = DataTypeTestNodeIds.UnionOfScalar.toNodeId(nsTable).orElseThrow();
    addVariable(
        folders.scalar(),
        "UnionOfScalar",
        "UnionOfScalar with active Boolean branch",
        unionScalarId,
        ValueRanks.Scalar,
        null,
        Variant.ofExtensionObject(encode(encodingContext, UnionOfScalar.ofBoolean(true))));
    addVariable(
        folders.scalar(),
        "UnionOfScalarNull",
        "UnionOfScalar with SwitchField = 0 (typed null union)",
        unionScalarId,
        ValueRanks.Scalar,
        null,
        Variant.ofExtensionObject(encode(encodingContext, UnionOfScalar.ofNull())));
    addVariable(
        folders.array(),
        "UnionOfScalar",
        "1-D array of UnionOfScalar covering every branch including SwitchField = 0",
        unionScalarId,
        ValueRanks.OneDimension,
        ARRAY_DIMS,
        Variant.ofExtensionObjectArray(
            new ExtensionObject[] {
              encode(encodingContext, UnionOfScalar.ofBoolean(true)),
              encode(encodingContext, UnionOfScalar.ofSByte((byte) 42)),
              encode(encodingContext, UnionOfScalar.ofByte(ubyte(7))),
              encode(encodingContext, UnionOfScalar.ofNull())
            }));
    addVariable(
        folders.matrix(),
        "UnionOfScalar",
        "2-D matrix of UnionOfScalar values",
        unionScalarId,
        2,
        MATRIX_DIMS,
        Variant.ofMatrix(
            Matrix.ofExtensionObject(
                new ExtensionObject[][] {
                  {
                    encode(encodingContext, UnionOfScalar.ofBoolean(true)),
                    encode(encodingContext, UnionOfScalar.ofSByte((byte) -1))
                  },
                  {
                    encode(encodingContext, UnionOfScalar.ofByte(ubyte(9))),
                    encode(encodingContext, UnionOfScalar.ofNull())
                  }
                })));

    NodeId unionArrayId = DataTypeTestNodeIds.UnionOfArray.toNodeId(nsTable).orElseThrow();
    addStructureArities(
        folders,
        "UnionOfArray",
        "Union whose branches are 1-D arrays",
        unionArrayId,
        () -> encode(encodingContext, UnionOfArray.ofBoolean(new Boolean[] {true, false})),
        () -> encode(encodingContext, UnionOfArray.ofSByte(new Byte[] {1, -2, 3})));

    NodeId unionMatrixId = DataTypeTestNodeIds.UnionOfMatrix.toNodeId(nsTable).orElseThrow();
    addStructureArities(
        folders,
        "UnionOfMatrix",
        "Union whose branches are 2-D matrices",
        unionMatrixId,
        () ->
            encode(
                encodingContext,
                UnionOfMatrix.ofBoolean(
                    Matrix.ofBoolean(new Boolean[][] {{true, false}, {false, true}}))),
        () ->
            encode(
                encodingContext,
                UnionOfMatrix.ofSByte(Matrix.ofSByte(new Byte[][] {{1, 2}, {3, 4}}))));
  }

  // endregion

  // region 6. Structure/{Scalar, Array, Matrix}

  private void addStructureNodes(
      NodeId valuesId, NamespaceTable nsTable, EncodingContext encodingContext) {

    ArityFolders folders =
        addArityFolders(
            valuesId,
            "Structure",
            "Concrete, inherited, optional-field, abstract-field, nested, nullable, opaque, and"
                + " unknown-encoding Structures");

    addStructureArities(
        folders,
        "ConcreteTestType",
        "Concrete subtype of AbstractTestType",
        DataTypeTestNodeIds.ConcreteTestType.toNodeId(nsTable).orElseThrow(),
        () -> encode(encodingContext, new ConcreteTestType((short) 1, 1.5, "alpha", true)),
        () -> encode(encodingContext, new ConcreteTestType((short) 2, 2.5, "beta", false)));

    addStructureArities(
        folders,
        "ConcreteTestTypeEx",
        "Concrete subtype of ConcreteTestType adding UInt32Field",
        DataTypeTestNodeIds.ConcreteTestTypeEx.toNodeId(nsTable).orElseThrow(),
        () ->
            encode(
                encodingContext, new ConcreteTestTypeEx((short) 1, 1.5, "alpha", true, uint(100))),
        () ->
            encode(
                encodingContext, new ConcreteTestTypeEx((short) 2, 2.5, "beta", false, uint(200))));

    addStructureArities(
        folders,
        "StructWithBuiltinScalarFields",
        "Structure containing scalar fields for every built-in OPC UA type",
        DataTypeTestNodeIds.StructWithBuiltinScalarFields.toNodeId(nsTable).orElseThrow(),
        () -> encode(encodingContext, sampleBuiltinScalarStruct(true, 1, "one")),
        () -> encode(encodingContext, sampleBuiltinScalarStruct(false, 2, "two")));

    addStructureArities(
        folders,
        "StructWithBuiltinScalarFieldsEx",
        "Extended structure with built-in scalar, enum, struct, union, and OptionSet fields",
        DataTypeTestNodeIds.StructWithBuiltinScalarFieldsEx.toNodeId(nsTable).orElseThrow(),
        () -> encode(encodingContext, sampleBuiltinScalarStructEx(true, 1, "one")),
        () -> encode(encodingContext, sampleBuiltinScalarStructEx(false, 2, "two")));

    addStructureArities(
        folders,
        "StructWithBuiltinArrayFields",
        "Structure containing 1-D array fields for every built-in OPC UA type",
        DataTypeTestNodeIds.StructWithBuiltinArrayFields.toNodeId(nsTable).orElseThrow(),
        () -> encode(encodingContext, sampleBuiltinArrayStruct(1)),
        () -> encode(encodingContext, sampleBuiltinArrayStruct(2)));

    addStructureArities(
        folders,
        "StructWithBuiltinArrayFieldsEx",
        "Extended structure with built-in array, enum, struct, union, and OptionSet fields",
        DataTypeTestNodeIds.StructWithBuiltinArrayFieldsEx.toNodeId(nsTable).orElseThrow(),
        () -> encode(encodingContext, sampleBuiltinArrayStructEx(1)),
        () -> encode(encodingContext, sampleBuiltinArrayStructEx(2)));

    addStructureArities(
        folders,
        "StructWithBuiltinMatrixFields",
        "Structure containing 2-D matrix fields for every built-in OPC UA type",
        DataTypeTestNodeIds.StructWithBuiltinMatrixFields.toNodeId(nsTable).orElseThrow(),
        () -> encode(encodingContext, sampleBuiltinMatrixStruct(1)),
        () -> encode(encodingContext, sampleBuiltinMatrixStruct(2)));

    addStructureArities(
        folders,
        "StructWithBuiltinMatrixFieldsEx",
        "Extended structure with built-in matrix, enum, struct, union, and OptionSet fields",
        DataTypeTestNodeIds.StructWithBuiltinMatrixFieldsEx.toNodeId(nsTable).orElseThrow(),
        () -> encode(encodingContext, sampleBuiltinMatrixStructEx(1)),
        () -> encode(encodingContext, sampleBuiltinMatrixStructEx(2)));

    addStructureArities(
        folders,
        "StructWithOptionalScalarFields",
        "StructureWithOptionalFields with scalar optional fields",
        DataTypeTestNodeIds.StructWithOptionalScalarFields.toNodeId(nsTable).orElseThrow(),
        () ->
            encode(
                encodingContext,
                new StructWithOptionalScalarFields(
                    "required",
                    "optional-present",
                    10,
                    20,
                    1.5,
                    2.5,
                    new ConcreteTestType((short) 1, 1.0, "req", true),
                    new ConcreteTestType((short) 2, 2.0, "opt", false))),
        () ->
            encode(
                encodingContext,
                new StructWithOptionalScalarFields(
                    "required-only",
                    null,
                    10,
                    null,
                    1.5,
                    null,
                    new ConcreteTestType((short) 1, 1.0, "req", true),
                    null)));

    addStructureArities(
        folders,
        "StructWithOptionalArrayFields",
        "StructureWithOptionalFields with 1-D array optional fields",
        DataTypeTestNodeIds.StructWithOptionalArrayFields.toNodeId(nsTable).orElseThrow(),
        () ->
            encode(
                encodingContext,
                new StructWithOptionalArrayFields(
                    new Integer[] {1, 2},
                    new Integer[] {3, 4},
                    new String[] {"a", "b"},
                    new String[] {"c", "d"},
                    new Double[] {1.0, 2.0},
                    new Double[] {3.0, 4.0},
                    new ConcreteTestType[] {new ConcreteTestType((short) 1, 1.0, "r", true)},
                    new ConcreteTestType[] {new ConcreteTestType((short) 2, 2.0, "o", false)})),
        () ->
            encode(
                encodingContext,
                new StructWithOptionalArrayFields(
                    new Integer[] {1, 2},
                    null,
                    new String[] {"a", "b"},
                    null,
                    new Double[] {1.0, 2.0},
                    null,
                    new ConcreteTestType[] {new ConcreteTestType((short) 1, 1.0, "r", true)},
                    null)));

    addStructureArities(
        folders,
        "StructWithOptionalMatrixFields",
        "StructureWithOptionalFields with 2-D matrix optional fields",
        DataTypeTestNodeIds.StructWithOptionalMatrixFields.toNodeId(nsTable).orElseThrow(),
        () ->
            encode(
                encodingContext,
                new StructWithOptionalMatrixFields(
                    Matrix.ofInt32(new Integer[][] {{1, 2}, {3, 4}}),
                    Matrix.ofInt32(new Integer[][] {{5, 6}, {7, 8}}),
                    Matrix.ofString(new String[][] {{"a", "b"}, {"c", "d"}}),
                    Matrix.ofString(new String[][] {{"e", "f"}, {"g", "h"}}),
                    Matrix.ofDouble(new Double[][] {{1.0, 2.0}, {3.0, 4.0}}),
                    Matrix.ofDouble(new Double[][] {{5.0, 6.0}, {7.0, 8.0}}),
                    Matrix.ofStruct(
                        new ConcreteTestType[][] {
                          {
                            new ConcreteTestType((short) 1, 1.0, "a", true),
                            new ConcreteTestType((short) 2, 2.0, "b", false)
                          },
                          {
                            new ConcreteTestType((short) 3, 3.0, "c", true),
                            new ConcreteTestType((short) 4, 4.0, "d", false)
                          }
                        }),
                    Matrix.ofStruct(
                        new ConcreteTestType[][] {
                          {
                            new ConcreteTestType((short) 5, 5.0, "e", true),
                            new ConcreteTestType((short) 6, 6.0, "f", false)
                          },
                          {
                            new ConcreteTestType((short) 7, 7.0, "g", true),
                            new ConcreteTestType((short) 8, 8.0, "h", false)
                          }
                        }))),
        () ->
            encode(
                encodingContext,
                new StructWithOptionalMatrixFields(
                    Matrix.ofInt32(new Integer[][] {{1, 2}, {3, 4}}),
                    null,
                    Matrix.ofString(new String[][] {{"a", "b"}, {"c", "d"}}),
                    null,
                    Matrix.ofDouble(new Double[][] {{1.0, 2.0}, {3.0, 4.0}}),
                    null,
                    Matrix.ofStruct(
                        new ConcreteTestType[][] {
                          {
                            new ConcreteTestType((short) 1, 1.0, "a", true),
                            new ConcreteTestType((short) 2, 2.0, "b", false)
                          },
                          {
                            new ConcreteTestType((short) 3, 3.0, "c", true),
                            new ConcreteTestType((short) 4, 4.0, "d", false)
                          }
                        }),
                    null)));

    addStructureArities(
        folders,
        "StructWithAbstractScalarFields",
        "Structure with abstract scalar Number, AbstractTestType, and ConcreteTestType fields",
        DataTypeTestNodeIds.StructWithAbstractScalarFields.toNodeId(nsTable).orElseThrow(),
        () ->
            encode(
                encodingContext,
                new StructWithAbstractScalarFields(
                    42,
                    new ConcreteTestType((short) 1, 1.0, "base", true),
                    new ConcreteTestTypeEx((short) 2, 2.0, "ext", false, uint(9)))),
        () ->
            encode(
                encodingContext,
                new StructWithAbstractScalarFields(
                    3.14,
                    new ConcreteTestTypeEx((short) 3, 3.0, "sub", true, uint(10)),
                    new ConcreteTestTypeEx((short) 4, 4.0, "ext2", false, uint(11)))));

    addStructureArities(
        folders,
        "StructWithAbstractArrayFields",
        "Structure with abstract 1-D array fields",
        DataTypeTestNodeIds.StructWithAbstractArrayFields.toNodeId(nsTable).orElseThrow(),
        () ->
            encode(
                encodingContext,
                new StructWithAbstractArrayFields(
                    new Integer[] {1, 2},
                    new ConcreteTestType[] {
                      new ConcreteTestType((short) 1, 1.0, "a", true),
                      new ConcreteTestType((short) 2, 2.0, "b", false)
                    },
                    new ConcreteTestTypeEx[] {
                      new ConcreteTestTypeEx((short) 3, 3.0, "c", true, uint(1)),
                      new ConcreteTestTypeEx((short) 4, 4.0, "d", false, uint(2))
                    })),
        () ->
            encode(
                encodingContext,
                new StructWithAbstractArrayFields(
                    new Double[] {1.5, 2.5},
                    new ConcreteTestType[] {
                      new ConcreteTestType((short) 5, 5.0, "e", true),
                      new ConcreteTestType((short) 6, 6.0, "f", false)
                    },
                    new ConcreteTestTypeEx[] {
                      new ConcreteTestTypeEx((short) 7, 7.0, "g", true, uint(3)),
                      new ConcreteTestTypeEx((short) 8, 8.0, "h", false, uint(4))
                    })));

    addStructureArities(
        folders,
        "StructWithAbstractMatrixFields",
        "Structure with abstract 2-D matrix fields",
        DataTypeTestNodeIds.StructWithAbstractMatrixFields.toNodeId(nsTable).orElseThrow(),
        () ->
            encode(
                encodingContext,
                new StructWithAbstractMatrixFields(
                    Matrix.ofInt32(new Integer[][] {{1, 2}, {3, 4}}),
                    Matrix.ofStruct(
                        new ConcreteTestType[][] {
                          {
                            new ConcreteTestType((short) 1, 1.0, "a", true),
                            new ConcreteTestType((short) 2, 2.0, "b", false)
                          },
                          {
                            new ConcreteTestType((short) 3, 3.0, "c", true),
                            new ConcreteTestType((short) 4, 4.0, "d", false)
                          }
                        }),
                    Matrix.ofStruct(
                        new ConcreteTestTypeEx[][] {
                          {
                            new ConcreteTestTypeEx((short) 1, 1.0, "a", true, uint(1)),
                            new ConcreteTestTypeEx((short) 2, 2.0, "b", false, uint(2))
                          },
                          {
                            new ConcreteTestTypeEx((short) 3, 3.0, "c", true, uint(3)),
                            new ConcreteTestTypeEx((short) 4, 4.0, "d", false, uint(4))
                          }
                        }))),
        () ->
            encode(
                encodingContext,
                new StructWithAbstractMatrixFields(
                    Matrix.ofDouble(new Double[][] {{1.5, 2.5}, {3.5, 4.5}}),
                    Matrix.ofStruct(
                        new ConcreteTestType[][] {
                          {
                            new ConcreteTestType((short) 5, 5.0, "e", true),
                            new ConcreteTestType((short) 6, 6.0, "f", false)
                          },
                          {
                            new ConcreteTestType((short) 7, 7.0, "g", true),
                            new ConcreteTestType((short) 8, 8.0, "h", false)
                          }
                        }),
                    Matrix.ofStruct(
                        new ConcreteTestTypeEx[][] {
                          {
                            new ConcreteTestTypeEx((short) 5, 5.0, "e", true, uint(5)),
                            new ConcreteTestTypeEx((short) 6, 6.0, "f", false, uint(6))
                          },
                          {
                            new ConcreteTestTypeEx((short) 7, 7.0, "g", true, uint(7)),
                            new ConcreteTestTypeEx((short) 8, 8.0, "h", false, uint(8))
                          }
                        }))));

    addStructureArities(
        folders,
        "StructWithStructureScalarFields",
        "Structure with nested abstract Structure (ExtensionObject) scalar fields",
        DataTypeTestNodeIds.StructWithStructureScalarFields.toNodeId(nsTable).orElseThrow(),
        () ->
            encode(
                encodingContext,
                new StructWithStructureScalarFields(
                    encode(encodingContext, new XVType(1.0, 2.0f)),
                    encode(encodingContext, new ConcreteTestType((short) 1, 1.0, "a", true)),
                    encode(
                        encodingContext,
                        new ConcreteTestTypeEx((short) 2, 2.0, "b", false, uint(2))))),
        () ->
            encode(
                encodingContext,
                new StructWithStructureScalarFields(
                    encode(encodingContext, new XVType(3.0, 4.0f)),
                    encode(encodingContext, new ConcreteTestType((short) 3, 3.0, "c", false)),
                    encode(
                        encodingContext,
                        new ConcreteTestTypeEx((short) 4, 4.0, "d", true, uint(4))))));

    addStructureArities(
        folders,
        "StructWithStructureArrayFields",
        "Structure with nested abstract Structure 1-D array fields",
        DataTypeTestNodeIds.StructWithStructureArrayFields.toNodeId(nsTable).orElseThrow(),
        () -> {
          ExtensionObject xo1 = encode(encodingContext, new XVType(1.0, 2.0f));
          ExtensionObject xo2 =
              encode(encodingContext, new ConcreteTestType((short) 1, 1.0, "a", true));
          ExtensionObject xo3 =
              encode(encodingContext, new ConcreteTestTypeEx((short) 2, 2.0, "b", false, uint(2)));
          return encode(
              encodingContext,
              new StructWithStructureArrayFields(
                  new ExtensionObject[] {xo1, xo1},
                  new ExtensionObject[] {xo2, xo2},
                  new ExtensionObject[] {xo3, xo3}));
        },
        () -> {
          ExtensionObject xo1 = encode(encodingContext, new XVType(3.0, 4.0f));
          ExtensionObject xo2 =
              encode(encodingContext, new ConcreteTestType((short) 3, 3.0, "c", false));
          ExtensionObject xo3 =
              encode(encodingContext, new ConcreteTestTypeEx((short) 4, 4.0, "d", true, uint(4)));
          return encode(
              encodingContext,
              new StructWithStructureArrayFields(
                  new ExtensionObject[] {xo1, xo1},
                  new ExtensionObject[] {xo2, xo2},
                  new ExtensionObject[] {xo3, xo3}));
        });

    addStructureArities(
        folders,
        "StructWithStructureMatrixFields",
        "Structure with nested abstract Structure 2-D matrix fields",
        DataTypeTestNodeIds.StructWithStructureMatrixFields.toNodeId(nsTable).orElseThrow(),
        () -> {
          ExtensionObject xo1 = encode(encodingContext, new XVType(1.0, 2.0f));
          ExtensionObject xo2 =
              encode(encodingContext, new ConcreteTestType((short) 1, 1.0, "a", true));
          ExtensionObject xo3 =
              encode(encodingContext, new ConcreteTestTypeEx((short) 2, 2.0, "b", false, uint(2)));
          return encode(
              encodingContext,
              new StructWithStructureMatrixFields(
                  Matrix.ofExtensionObject(new ExtensionObject[][] {{xo1, xo1}, {xo1, xo1}}),
                  Matrix.ofExtensionObject(new ExtensionObject[][] {{xo2, xo2}, {xo2, xo2}}),
                  Matrix.ofExtensionObject(new ExtensionObject[][] {{xo3, xo3}, {xo3, xo3}})));
        },
        () -> {
          ExtensionObject xo1 = encode(encodingContext, new XVType(3.0, 4.0f));
          ExtensionObject xo2 =
              encode(encodingContext, new ConcreteTestType((short) 3, 3.0, "c", false));
          ExtensionObject xo3 =
              encode(encodingContext, new ConcreteTestTypeEx((short) 4, 4.0, "d", true, uint(4)));
          return encode(
              encodingContext,
              new StructWithStructureMatrixFields(
                  Matrix.ofExtensionObject(new ExtensionObject[][] {{xo1, xo1}, {xo1, xo1}}),
                  Matrix.ofExtensionObject(new ExtensionObject[][] {{xo2, xo2}, {xo2, xo2}}),
                  Matrix.ofExtensionObject(new ExtensionObject[][] {{xo3, xo3}, {xo3, xo3}})));
        });

    // Custom Demo.Types structures
    addStructureArities(
        folders,
        "DemoRecord",
        "Compact 4-field DemoRecord structure",
        DemoRecord.TYPE_ID.toNodeId(nsTable).orElseThrow(),
        () -> encode(encodingContext, new DemoRecord(1, "Primary", 42.5, true)),
        () -> encode(encodingContext, new DemoRecord(2, "Secondary", 19.25, false)));

    addStructureArities(
        folders,
        "NullableFieldsStruct",
        "Structure with nullable String, ByteString, LocalizedText, NodeId, and Int32[] fields",
        NullableFieldsStruct.TYPE_ID.toNodeId(nsTable).orElseThrow(),
        () ->
            encode(
                encodingContext,
                new NullableFieldsStruct(
                    "present",
                    ByteString.of(new byte[] {1, 2, 3}),
                    LocalizedText.english("hello"),
                    NodeIds.Int32,
                    new Integer[] {1, 2, 3})),
        () ->
            encode(
                encodingContext,
                new NullableFieldsStruct(
                    null,
                    ByteString.NULL_VALUE,
                    LocalizedText.NULL_VALUE,
                    NodeId.NULL_VALUE,
                    null)));

    NodeId opaqueStructId = new NodeId(namespaceIndex, DemoTypesFragment.OPAQUE_STRUCT_IDENTIFIER);
    NodeId opaqueEncodingId =
        new NodeId(namespaceIndex, DemoTypesFragment.OPAQUE_STRUCT_ENCODING_IDENTIFIER);
    addStructureArities(
        folders,
        "OpaqueStruct",
        "Structure whose DataType intentionally omits DataTypeDefinition",
        opaqueStructId,
        () ->
            ExtensionObject.of(
                ByteString.of(new byte[] {0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08}),
                opaqueEncodingId),
        () ->
            ExtensionObject.of(
                ByteString.of(new byte[] {0x11, 0x12, 0x13, 0x14}), opaqueEncodingId));

    NodeId unknownEncodingId =
        new NodeId(namespaceIndex, DemoTypesFragment.UNKNOWN_ENCODING_IDENTIFIER);
    addStructureArities(
        folders,
        "UnknownEncoding",
        "Structure carrying an ExtensionObject whose encoding NodeId does not exist",
        NodeIds.Structure,
        () ->
            ExtensionObject.of(
                ByteString.of(new byte[] {(byte) 0xDE, (byte) 0xAD, (byte) 0xBE, (byte) 0xEF}),
                unknownEncodingId),
        () ->
            ExtensionObject.of(
                ByteString.of(new byte[] {(byte) 0xCA, (byte) 0xFE, (byte) 0xBA, (byte) 0xBE}),
                unknownEncodingId));
  }

  private void addStructureArities(
      ArityFolders folders,
      String name,
      String description,
      NodeId dataTypeId,
      Supplier<ExtensionObject> first,
      Supplier<ExtensionObject> second) {

    addVariable(
        folders.scalar(),
        name,
        description + " (scalar)",
        dataTypeId,
        ValueRanks.Scalar,
        null,
        Variant.ofExtensionObject(first.get()));

    addVariable(
        folders.array(),
        name,
        description + " (1-D array)",
        dataTypeId,
        ValueRanks.OneDimension,
        ARRAY_DIMS,
        Variant.ofExtensionObjectArray(new ExtensionObject[] {first.get(), second.get()}));

    addVariable(
        folders.matrix(),
        name,
        description + " (2-D matrix)",
        dataTypeId,
        2,
        MATRIX_DIMS,
        Variant.ofMatrix(
            Matrix.ofExtensionObject(
                new ExtensionObject[][] {
                  {first.get(), second.get()},
                  {second.get(), first.get()}
                })));
  }

  // endregion

  // region 7. Abstract/{Scalar, Array, Matrix}

  private void addAbstractNodes(
      NodeId valuesId, NamespaceTable nsTable, EncodingContext encodingContext) {

    ArityFolders folders =
        addArityFolders(
            valuesId,
            "Abstract",
            "Abstract Number, Integer, UInteger, Structure, and AbstractTestType variables"
                + " (populated and empty/null)");

    // Number (populated + empty)
    addVariable(
        folders.scalar(),
        "Number",
        "Abstract Number scalar initialized to Int32(42)",
        NodeIds.Number,
        ValueRanks.Scalar,
        null,
        Variant.ofInt32(42));
    addEmptyAbstractVariable(
        folders.scalar(),
        "NumberEmpty",
        "Writable abstract Number scalar initialized to null (AllowNulls=true)",
        NodeIds.Number,
        ValueRanks.Scalar,
        null);

    addVariable(
        folders.array(),
        "Number",
        "Abstract Number 1-D array initialized to Int32[]",
        NodeIds.Number,
        ValueRanks.OneDimension,
        ARRAY_DIMS,
        Variant.ofInt32Array(new Integer[] {1, 2, 3}));
    addEmptyAbstractVariable(
        folders.array(),
        "NumberEmpty",
        "Writable abstract Number 1-D array initialized to null (AllowNulls=true)",
        NodeIds.Number,
        ValueRanks.OneDimension,
        ARRAY_DIMS);

    addVariable(
        folders.matrix(),
        "Number",
        "Abstract Number 2-D matrix initialized to Double[][]",
        NodeIds.Number,
        2,
        MATRIX_DIMS,
        Variant.ofMatrix(Matrix.ofDouble(new Double[][] {{1.0, 2.0}, {3.0, 4.0}})));
    addEmptyAbstractVariable(
        folders.matrix(),
        "NumberEmpty",
        "Writable abstract Number 2-D matrix initialized to null (AllowNulls=true)",
        NodeIds.Number,
        2,
        MATRIX_DIMS);

    // Integer & UInteger
    addVariable(
        folders.scalar(),
        "Integer",
        "Abstract signed Integer scalar",
        NodeIds.Integer,
        ValueRanks.Scalar,
        null,
        Variant.ofInt32(-42));
    addVariable(
        folders.array(),
        "Integer",
        "Abstract signed Integer 1-D array",
        NodeIds.Integer,
        ValueRanks.OneDimension,
        ARRAY_DIMS,
        Variant.ofInt32Array(new Integer[] {-10, 0, 10}));
    addVariable(
        folders.matrix(),
        "Integer",
        "Abstract signed Integer 2-D matrix",
        NodeIds.Integer,
        2,
        MATRIX_DIMS,
        Variant.ofMatrix(Matrix.ofInt32(new Integer[][] {{-1, 0}, {1, 2}})));

    addVariable(
        folders.scalar(),
        "UInteger",
        "Abstract unsigned UInteger scalar",
        NodeIds.UInteger,
        ValueRanks.Scalar,
        null,
        Variant.ofUInt32(uint(42)));
    addVariable(
        folders.array(),
        "UInteger",
        "Abstract unsigned UInteger 1-D array",
        NodeIds.UInteger,
        ValueRanks.OneDimension,
        ARRAY_DIMS,
        Variant.ofUInt32Array(new UInteger[] {uint(1), uint(2), uint(3)}));
    addVariable(
        folders.matrix(),
        "UInteger",
        "Abstract unsigned UInteger 2-D matrix",
        NodeIds.UInteger,
        2,
        MATRIX_DIMS,
        Variant.ofMatrix(
            Matrix.ofUInt32(new UInteger[][] {{uint(1), uint(2)}, {uint(3), uint(4)}})));

    // Structure (populated heterogeneous + empty)
    Supplier<ExtensionObject> baseXo =
        () -> encode(encodingContext, new ConcreteTestType((short) 1, 1.5, "base", true));
    Supplier<ExtensionObject> derivedXo =
        () ->
            encode(
                encodingContext,
                new ConcreteTestTypeEx((short) 2, 2.5, "derived", false, uint(42)));

    addStructureArities(
        folders,
        "Structure",
        "Abstract Structure (i=22) holding heterogeneous ConcreteTestType and ConcreteTestTypeEx",
        NodeIds.Structure,
        baseXo,
        derivedXo);
    addEmptyAbstractVariable(
        folders.scalar(),
        "StructureEmpty",
        "Writable abstract Structure scalar initialized to null (AllowNulls=true)",
        NodeIds.Structure,
        ValueRanks.Scalar,
        null);
    addEmptyAbstractVariable(
        folders.array(),
        "StructureEmpty",
        "Writable abstract Structure 1-D array initialized to null (AllowNulls=true)",
        NodeIds.Structure,
        ValueRanks.OneDimension,
        ARRAY_DIMS);
    addEmptyAbstractVariable(
        folders.matrix(),
        "StructureEmpty",
        "Writable abstract Structure 2-D matrix initialized to null (AllowNulls=true)",
        NodeIds.Structure,
        2,
        MATRIX_DIMS);

    // AbstractTestType (populated heterogeneous + empty)
    NodeId abstractTestTypeId =
        DataTypeTestNodeIds.AbstractTestType.toNodeId(nsTable).orElseThrow();
    addStructureArities(
        folders,
        "AbstractTestType",
        "Abstract custom structure AbstractTestType holding concrete subtypes",
        abstractTestTypeId,
        baseXo,
        derivedXo);
    addEmptyAbstractVariable(
        folders.scalar(),
        "AbstractTestTypeEmpty",
        "Writable AbstractTestType scalar initialized to null (AllowNulls=true)",
        abstractTestTypeId,
        ValueRanks.Scalar,
        null);
    addEmptyAbstractVariable(
        folders.array(),
        "AbstractTestTypeEmpty",
        "Writable AbstractTestType 1-D array initialized to null (AllowNulls=true)",
        abstractTestTypeId,
        ValueRanks.OneDimension,
        ARRAY_DIMS);
    addEmptyAbstractVariable(
        folders.matrix(),
        "AbstractTestTypeEmpty",
        "Writable AbstractTestType 2-D matrix initialized to null (AllowNulls=true)",
        abstractTestTypeId,
        2,
        MATRIX_DIMS);
  }

  private void addEmptyAbstractVariable(
      NodeId folderId,
      String name,
      String description,
      NodeId dataTypeId,
      int valueRank,
      UInteger @Nullable [] arrayDimensions) {

    UaVariableNode node =
        addVariable(
            folderId,
            name,
            description,
            dataTypeId,
            valueRank,
            arrayDimensions,
            Variant.NULL_VALUE);
    node.setAllowNulls(true);
  }

  // endregion

  // region 8. DataAccess/

  private void addDataAccessNodes(NodeId valuesId) {
    UaFolderNode folder =
        addFolder(
            valuesId,
            "DataAccess",
            "Representative OPC UA Part 8 DataAccess VariableTypes (Analog, Discrete, ArrayItem)");
    NodeId folderId = folder.getNodeId();

    try {
      // AnalogItem
      AnalogItemTypeNode analogItem =
          instantiate(
              InstantiationRequest.of(AnalogItemTypeNode.class, NodeIds.AnalogItemType)
                  .nodeId(deriveChildNodeId(folderId, "AnalogItem"))
                  .browseName(new QualifiedName(namespaceIndex, "AnalogItem"))
                  .displayName(LocalizedText.english("AnalogItem"))
                  .description(
                      LocalizedText.english(
                          "AnalogItemType Double with EURange [0, 100] °C and InstrumentRange"
                              + " [-10, 110]"))
                  .parent(folderId, NodeIds.HasComponent));
      configureReadWrite(analogItem, NodeIds.Double, ValueRanks.Scalar, null);
      analogItem.setEuRange(new Range(0.0, 100.0));
      analogItem.setInstrumentRange(new Range(-10.0, 110.0));
      analogItem.setEngineeringUnits(CefactEngineeringUnits.CODE_CEL);
      analogItem.setValue(new DataValue(Variant.ofDouble(25.0)));
      analogItem.getFilterChain().addLast(EuRangeCheckFilter.INSTANCE);

      // AnalogUnit
      AnalogUnitTypeNode analogUnit =
          instantiate(
              InstantiationRequest.of(AnalogUnitTypeNode.class, NodeIds.AnalogUnitType)
                  .nodeId(deriveChildNodeId(folderId, "AnalogUnit"))
                  .browseName(new QualifiedName(namespaceIndex, "AnalogUnit"))
                  .displayName(LocalizedText.english("AnalogUnit"))
                  .description(LocalizedText.english("AnalogUnitType Double in bar"))
                  .parent(folderId, NodeIds.HasComponent));
      configureReadWrite(analogUnit, NodeIds.Double, ValueRanks.Scalar, null);
      analogUnit.setEngineeringUnits(CefactEngineeringUnits.CODE_BAR);
      analogUnit.setValue(new DataValue(Variant.ofDouble(1.013)));

      // TwoStateDiscrete
      TwoStateDiscreteTypeNode twoState =
          instantiate(
              InstantiationRequest.of(TwoStateDiscreteTypeNode.class, NodeIds.TwoStateDiscreteType)
                  .nodeId(deriveChildNodeId(folderId, "TwoStateDiscrete"))
                  .browseName(new QualifiedName(namespaceIndex, "TwoStateDiscrete"))
                  .displayName(LocalizedText.english("TwoStateDiscrete"))
                  .description(LocalizedText.english("TwoStateDiscreteType Boolean (On / Off)"))
                  .parent(folderId, NodeIds.HasComponent));
      configureReadWrite(twoState, NodeIds.Boolean, ValueRanks.Scalar, null);
      twoState.setTrueState(LocalizedText.english("On"));
      twoState.setFalseState(LocalizedText.english("Off"));
      twoState.setValue(new DataValue(Variant.ofBoolean(false)));

      // MultiStateDiscrete
      MultiStateDiscreteTypeNode multiState =
          instantiate(
              InstantiationRequest.of(
                      MultiStateDiscreteTypeNode.class, NodeIds.MultiStateDiscreteType)
                  .nodeId(deriveChildNodeId(folderId, "MultiStateDiscrete"))
                  .browseName(new QualifiedName(namespaceIndex, "MultiStateDiscrete"))
                  .displayName(LocalizedText.english("MultiStateDiscrete"))
                  .description(
                      LocalizedText.english(
                          "MultiStateDiscreteType UInt32 (Off, Auto, Manual, Fault)"))
                  .parent(folderId, NodeIds.HasComponent));
      configureReadWrite(multiState, NodeIds.UInt32, ValueRanks.Scalar, null);
      multiState.setEnumStrings(
          new LocalizedText[] {
            LocalizedText.english("Off"),
            LocalizedText.english("Auto"),
            LocalizedText.english("Manual"),
            LocalizedText.english("Fault")
          });
      multiState.setValue(new DataValue(Variant.ofUInt32(uint(1))));

      // MultiStateValueDiscrete
      MultiStateValueDiscreteTypeNode multiStateValue =
          instantiate(
              InstantiationRequest.of(
                      MultiStateValueDiscreteTypeNode.class, NodeIds.MultiStateValueDiscreteType)
                  .nodeId(deriveChildNodeId(folderId, "MultiStateValueDiscrete"))
                  .browseName(new QualifiedName(namespaceIndex, "MultiStateValueDiscrete"))
                  .displayName(LocalizedText.english("MultiStateValueDiscrete"))
                  .description(
                      LocalizedText.english(
                          "MultiStateValueDiscreteType Int32 with non-contiguous EnumValues (0,"
                              + " 10, 20, 30)"))
                  .parent(folderId, NodeIds.HasComponent));
      configureReadWrite(multiStateValue, NodeIds.Int32, ValueRanks.Scalar, null);
      Map<Integer, LocalizedText> valueTexts = new LinkedHashMap<>();
      valueTexts.put(0, LocalizedText.english("Stopped"));
      valueTexts.put(10, LocalizedText.english("Starting"));
      valueTexts.put(20, LocalizedText.english("Running"));
      valueTexts.put(30, LocalizedText.english("Stopping"));
      multiStateValue.setEnumValues(
          new EnumValueType[] {
            new EnumValueType(
                0L, LocalizedText.english("Stopped"), LocalizedText.english("Equipment stopped")),
            new EnumValueType(
                10L,
                LocalizedText.english("Starting"),
                LocalizedText.english("Equipment starting")),
            new EnumValueType(
                20L, LocalizedText.english("Running"), LocalizedText.english("Equipment running")),
            new EnumValueType(
                30L, LocalizedText.english("Stopping"), LocalizedText.english("Equipment stopping"))
          });
      multiStateValue.setValueAsText(LocalizedText.english("Running"));
      multiStateValue.setValue(new DataValue(Variant.ofInt32(20)));
      multiStateValue
          .getFilterChain()
          .addLast(DemoValueFilters.multiStateValueDiscrete(valueTexts));

      // YArrayItem
      YArrayItemTypeNode yArrayItem =
          instantiate(
              InstantiationRequest.of(YArrayItemTypeNode.class, NodeIds.YArrayItemType)
                  .nodeId(deriveChildNodeId(folderId, "YArrayItem"))
                  .browseName(new QualifiedName(namespaceIndex, "YArrayItem"))
                  .displayName(LocalizedText.english("YArrayItem"))
                  .description(LocalizedText.english("YArrayItemType 1-D Double profile"))
                  .parent(folderId, NodeIds.HasComponent));
      configureReadWrite(yArrayItem, NodeIds.Double, ValueRanks.OneDimension, ARRAY_DIMS);
      yArrayItem.setInstrumentRange(new Range(0.0, 100.0));
      yArrayItem.setEuRange(new Range(0.0, 100.0));
      yArrayItem.setEngineeringUnits(CefactEngineeringUnits.CODE_CEL);
      yArrayItem.setTitle(LocalizedText.english("Temperature Profile"));
      yArrayItem.setAxisScaleType(AxisScaleEnumeration.Linear);
      yArrayItem.setXAxisDefinition(
          new AxisInformation(
              CefactEngineeringUnits.CODE_MMT,
              new Range(0.0, 4.0),
              LocalizedText.english("Position"),
              AxisScaleEnumeration.Linear,
              new Double[] {0.0, 1.0, 2.0, 3.0, 4.0}));
      yArrayItem.setValue(
          new DataValue(Variant.ofDoubleArray(new Double[] {10.0, 20.0, 30.0, 40.0, 50.0})));
      yArrayItem.getFilterChain().addLast(EuRangeCheckFilter.INSTANCE);

      // XYArrayItem
      XYArrayItemTypeNode xyArrayItem =
          instantiate(
              InstantiationRequest.of(XYArrayItemTypeNode.class, NodeIds.XYArrayItemType)
                  .nodeId(deriveChildNodeId(folderId, "XYArrayItem"))
                  .browseName(new QualifiedName(namespaceIndex, "XYArrayItem"))
                  .displayName(LocalizedText.english("XYArrayItem"))
                  .description(LocalizedText.english("XYArrayItemType 1-D Double X/Y curve"))
                  .parent(folderId, NodeIds.HasComponent));
      configureReadWrite(xyArrayItem, NodeIds.Double, ValueRanks.OneDimension, ARRAY_DIMS);
      xyArrayItem.setInstrumentRange(new Range(0.0, 100.0));
      xyArrayItem.setEuRange(new Range(0.0, 100.0));
      xyArrayItem.setEngineeringUnits(CefactEngineeringUnits.CODE_C62);
      xyArrayItem.setTitle(LocalizedText.english("Calibration Curve"));
      xyArrayItem.setAxisScaleType(AxisScaleEnumeration.Linear);
      xyArrayItem.setXAxisDefinition(
          new AxisInformation(
              CefactEngineeringUnits.CODE_C62,
              new Range(0.0, 5.0),
              LocalizedText.english("X Axis"),
              AxisScaleEnumeration.Linear,
              new Double[] {0.0, 1.0, 2.0, 3.0, 4.0, 5.0}));
      xyArrayItem.setValue(
          new DataValue(Variant.ofDoubleArray(new Double[] {1.0, 10.0, 2.0, 20.0, 3.0, 30.0})));
      xyArrayItem.getFilterChain().addLast(EuRangeCheckFilter.INSTANCE);

      // ImageItem
      ImageItemTypeNode imageItem =
          instantiate(
              InstantiationRequest.of(ImageItemTypeNode.class, NodeIds.ImageItemType)
                  .nodeId(deriveChildNodeId(folderId, "ImageItem"))
                  .browseName(new QualifiedName(namespaceIndex, "ImageItem"))
                  .displayName(LocalizedText.english("ImageItem"))
                  .description(LocalizedText.english("ImageItemType 3x3 Double matrix"))
                  .parent(folderId, NodeIds.HasComponent));
      configureReadWrite(imageItem, NodeIds.Double, 2, new UInteger[] {uint(3), uint(3)});
      imageItem.setInstrumentRange(new Range(0.0, 100.0));
      imageItem.setEuRange(new Range(0.0, 100.0));
      imageItem.setEngineeringUnits(CefactEngineeringUnits.CODE_C62);
      imageItem.setTitle(LocalizedText.english("Thermal Map"));
      imageItem.setAxisScaleType(AxisScaleEnumeration.Linear);
      imageItem.setXAxisDefinition(
          new AxisInformation(
              CefactEngineeringUnits.CODE_C62,
              new Range(0.0, 2.0),
              LocalizedText.english("X Axis"),
              AxisScaleEnumeration.Linear,
              new Double[] {0.0, 1.0, 2.0}));
      imageItem.setYAxisDefinition(
          new AxisInformation(
              CefactEngineeringUnits.CODE_C62,
              new Range(0.0, 2.0),
              LocalizedText.english("Y Axis"),
              AxisScaleEnumeration.Linear,
              new Double[] {0.0, 1.0, 2.0}));
      imageItem.setValue(
          new DataValue(
              Variant.ofMatrix(
                  Matrix.ofDouble(
                      new Double[][] {
                        {10.0, 20.0, 30.0},
                        {40.0, 50.0, 60.0},
                        {70.0, 80.0, 90.0}
                      }))));
      imageItem.getFilterChain().addLast(EuRangeCheckFilter.INSTANCE);
    } catch (UaException e) {
      throw new IllegalStateException("failed to instantiate Demo/Values/DataAccess nodes", e);
    }
  }

  // endregion

  // region Sample Struct Factories

  private static StructWithBuiltinScalarFields sampleBuiltinScalarStruct(
      boolean flag, int n, String label) {

    return new StructWithBuiltinScalarFields(
        flag,
        (byte) n,
        ubyte(n),
        (short) n,
        ushort(n),
        n,
        uint(n),
        (long) n,
        ulong(n),
        (float) n,
        (double) n,
        label,
        DateTime.MIN_DATE_TIME,
        SAMPLE_UUID,
        ByteString.of(new byte[] {(byte) n}),
        XmlElement.of("<v>" + label + "</v>"),
        new NodeId(0, n),
        ExpandedNodeId.NULL_VALUE,
        StatusCode.GOOD,
        new QualifiedName(0, label),
        LocalizedText.english(label),
        new DataValue(Variant.ofInt32(n)),
        Variant.ofStruct(new XVType((double) n, (float) n)));
  }

  private static StructWithBuiltinScalarFieldsEx sampleBuiltinScalarStructEx(
      boolean flag, int n, String label) {

    return new StructWithBuiltinScalarFieldsEx(
        flag,
        (byte) n,
        ubyte(n),
        (short) n,
        ushort(n),
        n,
        uint(n),
        (long) n,
        ulong(n),
        (float) n,
        (double) n,
        label,
        DateTime.MIN_DATE_TIME,
        SAMPLE_UUID,
        ByteString.of(new byte[] {(byte) n}),
        XmlElement.of("<v>" + label + "</v>"),
        new NodeId(0, n),
        ExpandedNodeId.NULL_VALUE,
        StatusCode.GOOD,
        new QualifiedName(0, label),
        LocalizedText.english(label),
        new DataValue(Variant.ofInt32(n)),
        Variant.ofVariantArray(
            new Variant[] {
              Variant.ofStruct(new XVType(1.0, 2.0f)), Variant.ofStruct(new XVType(3.0, 4.0f))
            }),
        (double) n,
        flag ? ApplicationType.Server : ApplicationType.Client,
        flag ? TestEnumType.A : TestEnumType.B,
        new XVType((double) n, (float) n),
        new ConcreteTestType((short) n, (double) n, label, flag),
        UnionOfScalar.ofBoolean(flag),
        UnionOfArray.ofBoolean(new Boolean[] {flag, !flag}),
        AccessLevelType.of(AccessLevelType.Field.CurrentRead),
        AccessRestrictionType.of(),
        AccessLevelExType.of(AccessLevelExType.Field.CurrentRead),
        ulong(n));
  }

  private static StructWithBuiltinArrayFields sampleBuiltinArrayStruct(int n) {
    return new StructWithBuiltinArrayFields(
        new Boolean[] {true, false},
        new Byte[] {(byte) n, (byte) (n + 1)},
        new UByte[] {ubyte(n), ubyte(n + 1)},
        new Short[] {(short) n, (short) (n + 1)},
        new UShort[] {ushort(n), ushort(n + 1)},
        new Integer[] {n, n + 1},
        new UInteger[] {uint(n), uint(n + 1)},
        new Long[] {(long) n, (long) (n + 1)},
        new ULong[] {ulong(n), ulong(n + 1)},
        new Float[] {(float) n, (float) (n + 1)},
        new Double[] {(double) n, (double) (n + 1)},
        new String[] {"a" + n, "b" + n},
        new DateTime[] {DateTime.MIN_DATE_TIME, DateTime.MIN_DATE_TIME},
        new UUID[] {SAMPLE_UUID, SAMPLE_UUID},
        new ByteString[] {ByteString.of(new byte[] {(byte) n}), ByteString.of(new byte[] {0})},
        new XmlElement[] {XmlElement.of("<a/>"), XmlElement.of("<b/>")},
        new NodeId[] {new NodeId(0, n), new NodeId(0, n + 1)},
        new ExpandedNodeId[] {ExpandedNodeId.NULL_VALUE, ExpandedNodeId.NULL_VALUE},
        new StatusCode[] {StatusCode.GOOD, StatusCode.GOOD},
        new QualifiedName[] {new QualifiedName(0, "a"), new QualifiedName(0, "b")},
        new LocalizedText[] {LocalizedText.english("a"), LocalizedText.english("b")},
        new DataValue[] {new DataValue(Variant.ofInt32(n)), new DataValue(Variant.ofInt32(n + 1))},
        new Variant[] {Variant.ofInt32(n), Variant.ofString("v" + n)});
  }

  private static StructWithBuiltinArrayFieldsEx sampleBuiltinArrayStructEx(int n) {
    return new StructWithBuiltinArrayFieldsEx(
        new Boolean[] {true, false},
        new Byte[] {(byte) n, (byte) (n + 1)},
        new UByte[] {ubyte(n), ubyte(n + 1)},
        new Short[] {(short) n, (short) (n + 1)},
        new UShort[] {ushort(n), ushort(n + 1)},
        new Integer[] {n, n + 1},
        new UInteger[] {uint(n), uint(n + 1)},
        new Long[] {(long) n, (long) (n + 1)},
        new ULong[] {ulong(n), ulong(n + 1)},
        new Float[] {(float) n, (float) (n + 1)},
        new Double[] {(double) n, (double) (n + 1)},
        new String[] {"a" + n, "b" + n},
        new DateTime[] {DateTime.MIN_DATE_TIME, DateTime.MIN_DATE_TIME},
        new UUID[] {SAMPLE_UUID, SAMPLE_UUID},
        new ByteString[] {ByteString.of(new byte[] {(byte) n}), ByteString.of(new byte[] {0})},
        new XmlElement[] {XmlElement.of("<a/>"), XmlElement.of("<b/>")},
        new NodeId[] {new NodeId(0, n), new NodeId(0, n + 1)},
        new ExpandedNodeId[] {ExpandedNodeId.NULL_VALUE, ExpandedNodeId.NULL_VALUE},
        new StatusCode[] {StatusCode.GOOD, StatusCode.GOOD},
        new QualifiedName[] {new QualifiedName(0, "a"), new QualifiedName(0, "b")},
        new LocalizedText[] {LocalizedText.english("a"), LocalizedText.english("b")},
        new DataValue[] {new DataValue(Variant.ofInt32(n)), new DataValue(Variant.ofInt32(n + 1))},
        new Variant[] {Variant.ofInt32(n), Variant.ofString("v" + n)},
        new Double[] {(double) n, (double) (n + 1)},
        new ApplicationType[] {ApplicationType.Server, ApplicationType.Client},
        new TestEnumType[] {TestEnumType.A, TestEnumType.B},
        new XVType[] {new XVType((double) n, 1.0f), new XVType((double) (n + 1), 2.0f)},
        new ConcreteTestType[] {
          new ConcreteTestType((short) n, (double) n, "a", true),
          new ConcreteTestType((short) (n + 1), (double) (n + 1), "b", false)
        },
        new UnionOfScalar[] {UnionOfScalar.ofBoolean(true), UnionOfScalar.ofByte(ubyte(n))},
        new UnionOfArray[] {
          UnionOfArray.ofBoolean(new Boolean[] {true, false}),
          UnionOfArray.ofSByte(new Byte[] {1, 2})
        },
        new AccessLevelType[] {AccessLevelType.of(), AccessLevelType.of()},
        new AccessRestrictionType[] {AccessRestrictionType.of(), AccessRestrictionType.of()},
        new AccessLevelExType[] {AccessLevelExType.of(), AccessLevelExType.of()},
        new ULong[] {ulong(n), ulong(n + 1)});
  }

  private static StructWithBuiltinMatrixFields sampleBuiltinMatrixStruct(int n) {
    return new StructWithBuiltinMatrixFields(
        Matrix.ofBoolean(new Boolean[][] {{true, false}, {false, true}}),
        Matrix.ofSByte(new Byte[][] {{(byte) n, 0}, {0, (byte) n}}),
        Matrix.ofByte(new UByte[][] {{ubyte(n), ubyte(0)}, {ubyte(0), ubyte(n)}}),
        Matrix.ofInt16(new Short[][] {{(short) n, 0}, {0, (short) n}}),
        Matrix.ofUInt16(new UShort[][] {{ushort(n), ushort(0)}, {ushort(0), ushort(n)}}),
        Matrix.ofInt32(new Integer[][] {{n, 0}, {0, n}}),
        Matrix.ofUInt32(new UInteger[][] {{uint(n), uint(0)}, {uint(0), uint(n)}}),
        Matrix.ofInt64(new Long[][] {{(long) n, 0L}, {0L, (long) n}}),
        Matrix.ofUInt64(new ULong[][] {{ulong(n), ulong(0)}, {ulong(0), ulong(n)}}),
        Matrix.ofFloat(new Float[][] {{(float) n, 0.0f}, {0.0f, (float) n}}),
        Matrix.ofDouble(new Double[][] {{(double) n, 0.0d}, {0.0d, (double) n}}),
        Matrix.ofString(new String[][] {{"a", "b"}, {"c", "d"}}),
        Matrix.ofDateTime(
            new DateTime[][] {
              {DateTime.MIN_DATE_TIME, DateTime.MIN_DATE_TIME},
              {DateTime.MIN_DATE_TIME, DateTime.MIN_DATE_TIME}
            }),
        Matrix.ofGuid(new UUID[][] {{SAMPLE_UUID, SAMPLE_UUID}, {SAMPLE_UUID, SAMPLE_UUID}}),
        Matrix.ofByteString(
            new ByteString[][] {
              {ByteString.of(new byte[] {(byte) n}), ByteString.of(new byte[] {0})},
              {ByteString.of(new byte[] {0}), ByteString.of(new byte[] {(byte) n})}
            }),
        Matrix.ofXmlElement(
            new XmlElement[][] {
              {XmlElement.of("<a/>"), XmlElement.of("<b/>")},
              {XmlElement.of("<c/>"), XmlElement.of("<d/>")}
            }),
        Matrix.ofNodeId(
            new NodeId[][] {
              {new NodeId(0, n), new NodeId(0, 0)},
              {new NodeId(0, 0), new NodeId(0, n)}
            }),
        Matrix.ofExpandedNodeId(
            new ExpandedNodeId[][] {
              {ExpandedNodeId.NULL_VALUE, ExpandedNodeId.NULL_VALUE},
              {ExpandedNodeId.NULL_VALUE, ExpandedNodeId.NULL_VALUE}
            }),
        Matrix.ofStatusCode(
            new StatusCode[][] {
              {StatusCode.GOOD, StatusCode.GOOD},
              {StatusCode.GOOD, StatusCode.GOOD}
            }),
        Matrix.ofQualifiedName(
            new QualifiedName[][] {
              {new QualifiedName(0, "a"), new QualifiedName(0, "b")},
              {new QualifiedName(0, "c"), new QualifiedName(0, "d")}
            }),
        Matrix.ofLocalizedText(
            new LocalizedText[][] {
              {LocalizedText.english("a"), LocalizedText.english("b")},
              {LocalizedText.english("c"), LocalizedText.english("d")}
            }),
        Matrix.ofDataValue(
            new DataValue[][] {
              {new DataValue(Variant.ofInt32(n)), new DataValue(Variant.ofInt32(0))},
              {new DataValue(Variant.ofInt32(0)), new DataValue(Variant.ofInt32(n))}
            }),
        Matrix.ofVariant(
            new Variant[][] {
              {Variant.ofInt32(n), Variant.ofInt32(0)},
              {Variant.ofInt32(0), Variant.ofInt32(n)}
            }));
  }

  private static StructWithBuiltinMatrixFieldsEx sampleBuiltinMatrixStructEx(int n) {
    return new StructWithBuiltinMatrixFieldsEx(
        Matrix.ofBoolean(new Boolean[][] {{true, false}, {false, true}}),
        Matrix.ofSByte(new Byte[][] {{(byte) n, 0}, {0, (byte) n}}),
        Matrix.ofByte(new UByte[][] {{ubyte(n), ubyte(0)}, {ubyte(0), ubyte(n)}}),
        Matrix.ofInt16(new Short[][] {{(short) n, 0}, {0, (short) n}}),
        Matrix.ofUInt16(new UShort[][] {{ushort(n), ushort(0)}, {ushort(0), ushort(n)}}),
        Matrix.ofInt32(new Integer[][] {{n, 0}, {0, n}}),
        Matrix.ofUInt32(new UInteger[][] {{uint(n), uint(0)}, {uint(0), uint(n)}}),
        Matrix.ofInt64(new Long[][] {{(long) n, 0L}, {0L, (long) n}}),
        Matrix.ofUInt64(new ULong[][] {{ulong(n), ulong(0)}, {ulong(0), ulong(n)}}),
        Matrix.ofFloat(new Float[][] {{(float) n, 0.0f}, {0.0f, (float) n}}),
        Matrix.ofDouble(new Double[][] {{(double) n, 0.0d}, {0.0d, (double) n}}),
        Matrix.ofString(new String[][] {{"a", "b"}, {"c", "d"}}),
        Matrix.ofDateTime(
            new DateTime[][] {
              {DateTime.MIN_DATE_TIME, DateTime.MIN_DATE_TIME},
              {DateTime.MIN_DATE_TIME, DateTime.MIN_DATE_TIME}
            }),
        Matrix.ofGuid(new UUID[][] {{SAMPLE_UUID, SAMPLE_UUID}, {SAMPLE_UUID, SAMPLE_UUID}}),
        Matrix.ofByteString(
            new ByteString[][] {
              {ByteString.of(new byte[] {(byte) n}), ByteString.of(new byte[] {0})},
              {ByteString.of(new byte[] {0}), ByteString.of(new byte[] {(byte) n})}
            }),
        Matrix.ofXmlElement(
            new XmlElement[][] {
              {XmlElement.of("<a/>"), XmlElement.of("<b/>")},
              {XmlElement.of("<c/>"), XmlElement.of("<d/>")}
            }),
        Matrix.ofNodeId(
            new NodeId[][] {
              {new NodeId(0, n), new NodeId(0, 0)},
              {new NodeId(0, 0), new NodeId(0, n)}
            }),
        Matrix.ofExpandedNodeId(
            new ExpandedNodeId[][] {
              {ExpandedNodeId.NULL_VALUE, ExpandedNodeId.NULL_VALUE},
              {ExpandedNodeId.NULL_VALUE, ExpandedNodeId.NULL_VALUE}
            }),
        Matrix.ofStatusCode(
            new StatusCode[][] {
              {StatusCode.GOOD, StatusCode.GOOD},
              {StatusCode.GOOD, StatusCode.GOOD}
            }),
        Matrix.ofQualifiedName(
            new QualifiedName[][] {
              {new QualifiedName(0, "a"), new QualifiedName(0, "b")},
              {new QualifiedName(0, "c"), new QualifiedName(0, "d")}
            }),
        Matrix.ofLocalizedText(
            new LocalizedText[][] {
              {LocalizedText.english("a"), LocalizedText.english("b")},
              {LocalizedText.english("c"), LocalizedText.english("d")}
            }),
        Matrix.ofDataValue(
            new DataValue[][] {
              {new DataValue(Variant.ofInt32(n)), new DataValue(Variant.ofInt32(0))},
              {new DataValue(Variant.ofInt32(0)), new DataValue(Variant.ofInt32(n))}
            }),
        Matrix.ofVariant(
            new Variant[][] {
              {Variant.ofInt32(n), Variant.ofInt32(0)},
              {Variant.ofInt32(0), Variant.ofInt32(n)}
            }),
        Matrix.ofDouble(new Double[][] {{(double) n, 0.0}, {0.0, (double) n}}),
        Matrix.ofEnum(
            new ApplicationType[][] {
              {ApplicationType.Server, ApplicationType.Client},
              {ApplicationType.Server, ApplicationType.Client}
            }),
        Matrix.ofEnum(
            new TestEnumType[][] {
              {TestEnumType.A, TestEnumType.B},
              {TestEnumType.A, TestEnumType.B}
            }),
        Matrix.ofStruct(
            new XVType[][] {
              {new XVType((double) n, 1.0f), new XVType(0.0, 0.0f)},
              {new XVType(0.0, 0.0f), new XVType((double) n, 1.0f)}
            }),
        Matrix.ofStruct(
            new ConcreteTestType[][] {
              {
                new ConcreteTestType((short) n, (double) n, "a", true),
                new ConcreteTestType((short) 0, 0.0, "", false)
              },
              {
                new ConcreteTestType((short) 0, 0.0, "", false),
                new ConcreteTestType((short) n, (double) n, "b", true)
              }
            }),
        Matrix.ofStruct(
            new UnionOfScalar[][] {
              {UnionOfScalar.ofBoolean(true), UnionOfScalar.ofByte(ubyte(n))},
              {UnionOfScalar.ofBoolean(false), UnionOfScalar.ofByte(ubyte(0))}
            }),
        Matrix.ofStruct(
            new UnionOfArray[][] {
              {
                UnionOfArray.ofBoolean(new Boolean[] {true, false}),
                UnionOfArray.ofSByte(new Byte[] {1, 2})
              },
              {
                UnionOfArray.ofBoolean(new Boolean[] {false, true}),
                UnionOfArray.ofSByte(new Byte[] {3, 4})
              }
            }),
        Matrix.ofOptionSetUI(
            new AccessLevelType[][] {
              {AccessLevelType.of(), AccessLevelType.of()},
              {AccessLevelType.of(), AccessLevelType.of()}
            }),
        Matrix.ofOptionSetUI(
            new AccessRestrictionType[][] {
              {AccessRestrictionType.of(), AccessRestrictionType.of()},
              {AccessRestrictionType.of(), AccessRestrictionType.of()}
            }),
        Matrix.ofOptionSetUI(
            new AccessLevelExType[][] {
              {AccessLevelExType.of(), AccessLevelExType.of()},
              {AccessLevelExType.of(), AccessLevelExType.of()}
            }),
        Matrix.ofUInt64(new ULong[][] {{ulong(n), ulong(0)}, {ulong(0), ulong(n)}}));
  }

  // endregion

  // region Helpers

  private record ArityFolders(NodeId scalar, NodeId array, NodeId matrix) {}

  private ArityFolders addArityFolders(NodeId parentId, String categoryName, String description) {
    UaFolderNode category = addFolder(parentId, categoryName, description);
    UaFolderNode scalar =
        addFolder(category.getNodeId(), "Scalar", categoryName + " scalar values");
    UaFolderNode array =
        addFolder(category.getNodeId(), "Array", categoryName + " 1-D array values");
    UaFolderNode matrix =
        addFolder(category.getNodeId(), "Matrix", categoryName + " 2-D matrix values");
    return new ArityFolders(scalar.getNodeId(), array.getNodeId(), matrix.getNodeId());
  }

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
    getNodeManager().addNode(node);
    node.addReference(
        new Reference(
            node.getNodeId(), ReferenceTypes.HasComponent, parentId.expanded(), Direction.INVERSE));
    return node;
  }

  private static void configureReadWrite(
      UaVariableNode node,
      NodeId dataTypeId,
      int valueRank,
      UInteger @Nullable [] arrayDimensions) {

    node.setDataType(dataTypeId);
    node.setValueRank(valueRank);
    if (arrayDimensions != null) {
      node.setArrayDimensions(arrayDimensions);
    }
    node.setAccessLevel(AccessLevel.toValue(AccessLevel.READ_WRITE));
    node.setUserAccessLevel(AccessLevel.toValue(AccessLevel.READ_WRITE));
    node.setMinimumSamplingInterval(MIN_SUPPORTED_SAMPLE_RATE);
  }

  private <T extends UaNode> T instantiate(InstantiationRequest.Builder<T> request)
      throws UaException {

    return getServer()
        .getNodeInstantiator()
        .instantiate(request.legacyPathStrings().target(getNodeManager()).build())
        .root();
  }

  private static ExtensionObject encode(EncodingContext context, UaStructuredType struct) {
    return ExtensionObject.encode(context, struct);
  }
}

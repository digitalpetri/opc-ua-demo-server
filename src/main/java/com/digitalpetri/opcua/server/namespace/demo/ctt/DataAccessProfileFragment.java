package com.digitalpetri.opcua.server.namespace.demo.ctt;

import static com.digitalpetri.opcua.server.namespace.demo.Util.deriveChildNodeId;
import static org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.Unsigned.uint;

import com.digitalpetri.opcua.server.namespace.demo.EuRangeCheckFilter;
import com.digitalpetri.opcua.server.namespace.demo.Util;
import java.util.List;
import org.eclipse.milo.opcua.sdk.core.AccessLevel;
import org.eclipse.milo.opcua.sdk.core.Reference;
import org.eclipse.milo.opcua.sdk.core.Reference.Direction;
import org.eclipse.milo.opcua.sdk.core.ValueRanks;
import org.eclipse.milo.opcua.sdk.server.AddressSpaceComposite;
import org.eclipse.milo.opcua.sdk.server.AddressSpaceFilter;
import org.eclipse.milo.opcua.sdk.server.ManagedAddressSpaceFragmentWithLifecycle;
import org.eclipse.milo.opcua.sdk.server.OpcUaServer;
import org.eclipse.milo.opcua.sdk.server.SimpleAddressSpaceFilter;
import org.eclipse.milo.opcua.sdk.server.items.DataItem;
import org.eclipse.milo.opcua.sdk.server.items.MonitoredItem;
import org.eclipse.milo.opcua.sdk.server.model.variables.AnalogItemTypeNode;
import org.eclipse.milo.opcua.sdk.server.model.variables.CubeItemTypeNode;
import org.eclipse.milo.opcua.sdk.server.model.variables.DataItemTypeNode;
import org.eclipse.milo.opcua.sdk.server.model.variables.ImageItemTypeNode;
import org.eclipse.milo.opcua.sdk.server.model.variables.MultiStateDiscreteTypeNode;
import org.eclipse.milo.opcua.sdk.server.model.variables.MultiStateValueDiscreteTypeNode;
import org.eclipse.milo.opcua.sdk.server.model.variables.NDimensionArrayItemTypeNode;
import org.eclipse.milo.opcua.sdk.server.model.variables.TwoStateDiscreteTypeNode;
import org.eclipse.milo.opcua.sdk.server.model.variables.XYArrayItemTypeNode;
import org.eclipse.milo.opcua.sdk.server.model.variables.YArrayItemTypeNode;
import org.eclipse.milo.opcua.sdk.server.nodes.UaFolderNode;
import org.eclipse.milo.opcua.sdk.server.nodes.UaNode;
import org.eclipse.milo.opcua.sdk.server.util.SubscriptionModel;
import org.eclipse.milo.opcua.stack.core.NodeIds;
import org.eclipse.milo.opcua.stack.core.OpcUaDataType;
import org.eclipse.milo.opcua.stack.core.ReferenceTypes;
import org.eclipse.milo.opcua.stack.core.UaException;
import org.eclipse.milo.opcua.stack.core.types.builtin.DataValue;
import org.eclipse.milo.opcua.stack.core.types.builtin.LocalizedText;
import org.eclipse.milo.opcua.stack.core.types.builtin.Matrix;
import org.eclipse.milo.opcua.stack.core.types.builtin.NodeId;
import org.eclipse.milo.opcua.stack.core.types.builtin.QualifiedName;
import org.eclipse.milo.opcua.stack.core.types.builtin.Variant;
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.UInteger;
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.UShort;
import org.eclipse.milo.opcua.stack.core.types.enumerated.AxisScaleEnumeration;
import org.eclipse.milo.opcua.stack.core.types.structured.AxisInformation;
import org.eclipse.milo.opcua.stack.core.types.structured.EUInformation;
import org.eclipse.milo.opcua.stack.core.types.structured.EnumValueType;
import org.eclipse.milo.opcua.stack.core.types.structured.Range;

public class DataAccessProfileFragment extends ManagedAddressSpaceFragmentWithLifecycle {

  private final SimpleAddressSpaceFilter filter;
  private final SubscriptionModel subscriptionModel;

  private final NodeId parentNodeId;
  private final UShort namespaceIndex;

  public DataAccessProfileFragment(
      OpcUaServer server,
      AddressSpaceComposite composite,
      NodeId parentNodeId,
      UShort namespaceIndex) {

    super(server, composite);

    this.parentNodeId = parentNodeId;
    this.namespaceIndex = namespaceIndex;

    filter = SimpleAddressSpaceFilter.create(getNodeManager()::containsNode);

    subscriptionModel = new SubscriptionModel(server, this);
    getLifecycleManager().addLifecycle(subscriptionModel);

    getLifecycleManager().addStartupTask(this::addNodes);
  }

  @Override
  public AddressSpaceFilter getFilter() {
    return filter;
  }

  @Override
  public void onDataItemsCreated(List<DataItem> dataItems) {
    subscriptionModel.onDataItemsCreated(dataItems);
  }

  @Override
  public void onDataItemsModified(List<DataItem> dataItems) {
    subscriptionModel.onDataItemsModified(dataItems);
  }

  @Override
  public void onDataItemsDeleted(List<DataItem> dataItems) {
    subscriptionModel.onDataItemsDeleted(dataItems);
  }

  @Override
  public void onMonitoringModeChanged(List<MonitoredItem> monitoredItems) {
    subscriptionModel.onMonitoringModeChanged(monitoredItems);
  }

  private void addNodes() {
    var dataAccessFolder =
        new UaFolderNode(
            getNodeContext(),
            deriveChildNodeId(parentNodeId, "DataAccessProfile"),
            new QualifiedName(namespaceIndex, "DataAccessProfile"),
            new LocalizedText("DataAccessProfile"));

    getNodeManager().addNode(dataAccessFolder);

    dataAccessFolder.addReference(
        new Reference(
            dataAccessFolder.getNodeId(),
            ReferenceTypes.HasComponent,
            parentNodeId.expanded(),
            Direction.INVERSE));

    try {
      addAnalogItemTypeNodes(dataAccessFolder.getNodeId());
      addAnalogItemTypeArrayNodes(dataAccessFolder.getNodeId());
      addArrayItemTypeNodes(dataAccessFolder.getNodeId());
      addDataItemTypeNodes(dataAccessFolder.getNodeId());
      addDiscreteItemTypeNodes(dataAccessFolder.getNodeId());
      addMultiStateValueDiscreteTypeNodes(dataAccessFolder.getNodeId());
    } catch (UaException e) {
      throw new RuntimeException(e);
    }
  }

  private void addAnalogItemTypeNodes(NodeId parentNodeId) throws UaException {
    var analogItemTypeFolder =
        new UaFolderNode(
            getNodeContext(),
            deriveChildNodeId(parentNodeId, "AnalogItemType"),
            new QualifiedName(namespaceIndex, "AnalogItemType"),
            new LocalizedText("AnalogItemType"));

    getNodeManager().addNode(analogItemTypeFolder);

    analogItemTypeFolder.addReference(
        new Reference(
            analogItemTypeFolder.getNodeId(),
            ReferenceTypes.HasComponent,
            parentNodeId.expanded(),
            Direction.INVERSE));

    var analogTypes =
        List.of(
            OpcUaDataType.Byte,
            OpcUaDataType.Double,
            OpcUaDataType.Float,
            OpcUaDataType.Int16,
            OpcUaDataType.Int32,
            OpcUaDataType.Int64,
            OpcUaDataType.SByte,
            OpcUaDataType.UInt16,
            OpcUaDataType.UInt32,
            OpcUaDataType.UInt64);

    for (OpcUaDataType dataType : analogTypes) {
      UaNode node =
          getNodeFactory()
              .createNode(
                  deriveChildNodeId(analogItemTypeFolder.getNodeId(), dataType.name() + "Analog"),
                  NodeIds.AnalogItemType);

      if (node instanceof AnalogItemTypeNode analogItemNode) {
        analogItemNode.setBrowseName(new QualifiedName(namespaceIndex, dataType.name() + "Analog"));
        analogItemNode.setDisplayName(new LocalizedText(dataType.name() + "Analog"));
        analogItemNode.setDataType(dataType.getNodeId());
        analogItemNode.setAccessLevel(AccessLevel.toValue(AccessLevel.READ_WRITE));
        analogItemNode.setUserAccessLevel(AccessLevel.toValue(AccessLevel.READ_WRITE));
        analogItemNode.setMinimumSamplingInterval(100.0);

        analogItemNode.setEuRange(new Range(0.0, 100.0));
        analogItemNode.setValue(new DataValue(Variant.of(Util.getDefaultScalarValue(dataType))));

        analogItemNode.getFilterChain().addLast(EuRangeCheckFilter.INSTANCE);

        getNodeManager().addNode(analogItemNode);

        analogItemNode.addReference(
            new Reference(
                analogItemNode.getNodeId(),
                ReferenceTypes.HasComponent,
                analogItemTypeFolder.getNodeId().expanded(),
                Direction.INVERSE));
      }
    }
  }

  private void addAnalogItemTypeArrayNodes(NodeId parentNodeId) throws UaException {
    var arrayFolder =
        new UaFolderNode(
            getNodeContext(),
            deriveChildNodeId(parentNodeId, "AnalogItemTypeArrays"),
            new QualifiedName(namespaceIndex, "AnalogItemTypeArrays"),
            new LocalizedText("AnalogItemTypeArrays"));

    getNodeManager().addNode(arrayFolder);

    arrayFolder.addReference(
        new Reference(
            arrayFolder.getNodeId(),
            ReferenceTypes.HasComponent,
            parentNodeId.expanded(),
            Direction.INVERSE));

    var analogTypes =
        List.of(
            OpcUaDataType.Double,
            OpcUaDataType.Float,
            OpcUaDataType.Int16,
            OpcUaDataType.UInt16,
            OpcUaDataType.Int32,
            OpcUaDataType.UInt32);

    for (OpcUaDataType dataType : analogTypes) {
      UaNode node =
          getNodeFactory()
              .createNode(
                  deriveChildNodeId(arrayFolder.getNodeId(), dataType.name() + "ArrayAnalog"),
                  NodeIds.AnalogItemType);

      if (node instanceof AnalogItemTypeNode analogItemNode) {
        analogItemNode.setBrowseName(
            new QualifiedName(namespaceIndex, dataType.name() + "ArrayAnalog"));
        analogItemNode.setDisplayName(new LocalizedText(dataType.name() + "ArrayAnalog"));
        analogItemNode.setDataType(dataType.getNodeId());
        analogItemNode.setValueRank(ValueRanks.OneDimension);
        analogItemNode.setArrayDimensions(new UInteger[] {uint(0)});
        analogItemNode.setAccessLevel(AccessLevel.toValue(AccessLevel.READ_WRITE));
        analogItemNode.setUserAccessLevel(AccessLevel.toValue(AccessLevel.READ_WRITE));
        analogItemNode.setMinimumSamplingInterval(100.0);

        analogItemNode.setEuRange(new Range(0.0, 100.0));

        Object arrayValue = Util.getDefaultArrayValue(dataType);
        if (arrayValue instanceof Variant v) {
          analogItemNode.setValue(new DataValue(v));
        } else {
          analogItemNode.setValue(new DataValue(Variant.of(arrayValue)));
        }

        // analogItemNode.getFilterChain().addLast(EuRangeCheckFilter.INSTANCE);

        getNodeManager().addNode(analogItemNode);

        analogItemNode.addReference(
            new Reference(
                analogItemNode.getNodeId(),
                ReferenceTypes.HasComponent,
                arrayFolder.getNodeId().expanded(),
                Direction.INVERSE));
      }
    }
  }

  private void addArrayItemTypeNodes(NodeId parentNodeId) throws UaException {
    var arrayItemFolder =
        new UaFolderNode(
            getNodeContext(),
            deriveChildNodeId(parentNodeId, "ArrayItemType"),
            new QualifiedName(namespaceIndex, "ArrayItemType"),
            new LocalizedText("ArrayItemType"));

    getNodeManager().addNode(arrayItemFolder);

    arrayItemFolder.addReference(
        new Reference(
            arrayItemFolder.getNodeId(),
            ReferenceTypes.HasComponent,
            parentNodeId.expanded(),
            Direction.INVERSE));

    // Add CubeItemType instance
    {
      NodeId cubeItemTypeId = new NodeId(UShort.valueOf(0), 12057);
      UaNode node =
          getNodeFactory()
              .createNode(
                  deriveChildNodeId(arrayItemFolder.getNodeId(), "CubeItem"), cubeItemTypeId);

      if (node instanceof CubeItemTypeNode cubeItem) {
        cubeItem.setBrowseName(new QualifiedName(namespaceIndex, "CubeItem"));
        cubeItem.setDisplayName(new LocalizedText("CubeItem"));
        cubeItem.setDataType(NodeIds.Double);
        cubeItem.setValueRank(3);
        cubeItem.setArrayDimensions(new UInteger[] {uint(2), uint(2), uint(2)});
        cubeItem.setAccessLevel(AccessLevel.toValue(AccessLevel.READ_WRITE));
        cubeItem.setUserAccessLevel(AccessLevel.toValue(AccessLevel.READ_WRITE));
        // Create 3D array data: 2x2x2 = 8 elements
        Double[][][] cubeData =
            new Double[][][] {
              {{1.0, 2.0}, {3.0, 4.0}},
              {{5.0, 6.0}, {7.0, 8.0}}
            };
        cubeItem.setValue(new DataValue(Variant.ofMatrix(new Matrix(cubeData))));

        // Set ArrayItemType properties
        cubeItem.setInstrumentRange(new Range(0.0, 100.0));
        cubeItem.setEuRange(new Range(0.0, 100.0));
        cubeItem.setEngineeringUnits(
            new EUInformation("", -1, new LocalizedText(""), new LocalizedText("units")));
        cubeItem.setTitle(new LocalizedText("Cube Item"));
        cubeItem.setAxisScaleType(AxisScaleEnumeration.Linear);

        // Set CubeItemType specific properties
        cubeItem.setXAxisDefinition(
            new AxisInformation(
                new EUInformation("", -1, new LocalizedText(""), new LocalizedText("mm")),
                new Range(0.0, 10.0),
                new LocalizedText("X Axis"),
                AxisScaleEnumeration.Linear,
                new Double[] {0.0, 10.0}));
        cubeItem.setYAxisDefinition(
            new AxisInformation(
                new EUInformation("", -1, new LocalizedText(""), new LocalizedText("mm")),
                new Range(0.0, 10.0),
                new LocalizedText("Y Axis"),
                AxisScaleEnumeration.Linear,
                new Double[] {0.0, 10.0}));
        cubeItem.setZAxisDefinition(
            new AxisInformation(
                new EUInformation("", -1, new LocalizedText(""), new LocalizedText("mm")),
                new Range(0.0, 10.0),
                new LocalizedText("Z Axis"),
                AxisScaleEnumeration.Linear,
                new Double[] {0.0, 10.0}));

        cubeItem.getFilterChain().addLast(EuRangeCheckFilter.INSTANCE);

        getNodeManager().addNode(cubeItem);

        cubeItem.addReference(
            new Reference(
                cubeItem.getNodeId(),
                ReferenceTypes.HasComponent,
                arrayItemFolder.getNodeId().expanded(),
                Direction.INVERSE));
      }
    }

    // Add ImageItemType instance
    {
      NodeId imageItemTypeId = new NodeId(UShort.valueOf(0), 12047);
      UaNode node =
          getNodeFactory()
              .createNode(
                  deriveChildNodeId(arrayItemFolder.getNodeId(), "ImageItem"), imageItemTypeId);

      if (node instanceof ImageItemTypeNode imageItem) {
        imageItem.setBrowseName(new QualifiedName(namespaceIndex, "ImageItem"));
        imageItem.setDisplayName(new LocalizedText("ImageItem"));
        imageItem.setDataType(NodeIds.Double);
        imageItem.setValueRank(2);
        imageItem.setArrayDimensions(new UInteger[] {uint(3), uint(3)});
        imageItem.setAccessLevel(AccessLevel.toValue(AccessLevel.READ_WRITE));
        imageItem.setUserAccessLevel(AccessLevel.toValue(AccessLevel.READ_WRITE));
        // Create 2D array data: 3x3 = 9 elements
        Double[][] imageData =
            new Double[][] {
              {1.0, 2.0, 3.0},
              {4.0, 5.0, 6.0},
              {7.0, 8.0, 9.0}
            };
        imageItem.setValue(new DataValue(Variant.ofMatrix(new Matrix(imageData))));

        // Set ArrayItemType properties
        imageItem.setInstrumentRange(new Range(0.0, 100.0));
        imageItem.setEuRange(new Range(0.0, 100.0));
        imageItem.setEngineeringUnits(
            new EUInformation("", -1, new LocalizedText(""), new LocalizedText("units")));
        imageItem.setTitle(new LocalizedText("Image Item"));
        imageItem.setAxisScaleType(AxisScaleEnumeration.Linear);

        // Set ImageItemType specific properties
        imageItem.setXAxisDefinition(
            new AxisInformation(
                new EUInformation("", -1, new LocalizedText(""), new LocalizedText("pixels")),
                new Range(0.0, 2.0),
                new LocalizedText("X Axis"),
                AxisScaleEnumeration.Linear,
                new Double[] {0.0, 1.0, 2.0}));
        imageItem.setYAxisDefinition(
            new AxisInformation(
                new EUInformation("", -1, new LocalizedText(""), new LocalizedText("pixels")),
                new Range(0.0, 2.0),
                new LocalizedText("Y Axis"),
                AxisScaleEnumeration.Linear,
                new Double[] {0.0, 1.0, 2.0}));

        imageItem.getFilterChain().addLast(EuRangeCheckFilter.INSTANCE);

        getNodeManager().addNode(imageItem);

        imageItem.addReference(
            new Reference(
                imageItem.getNodeId(),
                ReferenceTypes.HasComponent,
                arrayItemFolder.getNodeId().expanded(),
                Direction.INVERSE));
      }
    }

    // Add NDimensionArrayItemType instance
    {
      NodeId nDimensionArrayItemTypeId = new NodeId(UShort.valueOf(0), 12068);
      UaNode node =
          getNodeFactory()
              .createNode(
                  deriveChildNodeId(arrayItemFolder.getNodeId(), "NDimensionArrayItem"),
                  nDimensionArrayItemTypeId);

      if (node instanceof NDimensionArrayItemTypeNode nDimensionArrayItem) {
        nDimensionArrayItem.setBrowseName(new QualifiedName(namespaceIndex, "NDimensionArrayItem"));
        nDimensionArrayItem.setDisplayName(new LocalizedText("NDimensionArrayItem"));
        nDimensionArrayItem.setDataType(NodeIds.Double);
        nDimensionArrayItem.setValueRank(2);
        nDimensionArrayItem.setArrayDimensions(new UInteger[] {uint(2), uint(3)});
        nDimensionArrayItem.setAccessLevel(AccessLevel.toValue(AccessLevel.READ_WRITE));
        nDimensionArrayItem.setUserAccessLevel(AccessLevel.toValue(AccessLevel.READ_WRITE));
        // Create 2D array data: 2x3 = 6 elements
        Double[][] nDimensionData =
            new Double[][] {
              {1.0, 2.0, 3.0},
              {4.0, 5.0, 6.0}
            };
        nDimensionArrayItem.setValue(new DataValue(Variant.ofMatrix(new Matrix(nDimensionData))));

        // Set ArrayItemType properties
        nDimensionArrayItem.setInstrumentRange(new Range(0.0, 100.0));
        nDimensionArrayItem.setEuRange(new Range(0.0, 100.0));
        nDimensionArrayItem.setEngineeringUnits(
            new EUInformation("", -1, new LocalizedText(""), new LocalizedText("units")));
        nDimensionArrayItem.setTitle(new LocalizedText("NDimensionArray Item"));
        nDimensionArrayItem.setAxisScaleType(AxisScaleEnumeration.Linear);

        // Set NDimensionArrayItemType specific properties
        AxisInformation[] axisDefinitions = new AxisInformation[2];
        axisDefinitions[0] =
            new AxisInformation(
                new EUInformation("", -1, new LocalizedText(""), new LocalizedText("units")),
                new Range(0.0, 1.0),
                new LocalizedText("Axis 0"),
                AxisScaleEnumeration.Linear,
                new Double[] {0.0, 1.0});
        axisDefinitions[1] =
            new AxisInformation(
                new EUInformation("", -1, new LocalizedText(""), new LocalizedText("units")),
                new Range(0.0, 2.0),
                new LocalizedText("Axis 1"),
                AxisScaleEnumeration.Linear,
                new Double[] {0.0, 1.0, 2.0});
        nDimensionArrayItem.setAxisDefinition(axisDefinitions);

        nDimensionArrayItem.getFilterChain().addLast(EuRangeCheckFilter.INSTANCE);

        getNodeManager().addNode(nDimensionArrayItem);

        nDimensionArrayItem.addReference(
            new Reference(
                nDimensionArrayItem.getNodeId(),
                ReferenceTypes.HasComponent,
                arrayItemFolder.getNodeId().expanded(),
                Direction.INVERSE));
      }
    }

    // Add XYArrayItemType instance
    {
      NodeId xyArrayItemTypeId = new NodeId(UShort.valueOf(0), 12038);
      UaNode node =
          getNodeFactory()
              .createNode(
                  deriveChildNodeId(arrayItemFolder.getNodeId(), "XYArrayItem"), xyArrayItemTypeId);

      if (node instanceof XYArrayItemTypeNode xyArrayItem) {
        xyArrayItem.setBrowseName(new QualifiedName(namespaceIndex, "XYArrayItem"));
        xyArrayItem.setDisplayName(new LocalizedText("XYArrayItem"));
        xyArrayItem.setDataType(NodeIds.Double);
        xyArrayItem.setValueRank(ValueRanks.OneDimension);
        xyArrayItem.setArrayDimensions(new UInteger[] {uint(0)});
        xyArrayItem.setAccessLevel(AccessLevel.toValue(AccessLevel.READ_WRITE));
        xyArrayItem.setUserAccessLevel(AccessLevel.toValue(AccessLevel.READ_WRITE));
        // Create 1D array data: 6 elements (3 X-Y pairs)
        Double[] xyData = new Double[] {1.0, 2.0, 3.0, 4.0, 5.0, 6.0};
        xyArrayItem.setValue(new DataValue(Variant.ofDoubleArray(xyData)));

        // Set ArrayItemType properties
        xyArrayItem.setInstrumentRange(new Range(0.0, 100.0));
        xyArrayItem.setEuRange(new Range(0.0, 100.0));
        xyArrayItem.setEngineeringUnits(
            new EUInformation("", -1, new LocalizedText(""), new LocalizedText("units")));
        xyArrayItem.setTitle(new LocalizedText("XYArray Item"));
        xyArrayItem.setAxisScaleType(AxisScaleEnumeration.Linear);

        // Set XYArrayItemType specific properties
        xyArrayItem.setXAxisDefinition(
            new AxisInformation(
                new EUInformation("", -1, new LocalizedText(""), new LocalizedText("units")),
                new Range(0.0, 5.0),
                new LocalizedText("X Axis"),
                AxisScaleEnumeration.Linear,
                new Double[] {0.0, 1.0, 2.0, 3.0, 4.0, 5.0}));

        xyArrayItem.getFilterChain().addLast(EuRangeCheckFilter.INSTANCE);

        getNodeManager().addNode(xyArrayItem);

        xyArrayItem.addReference(
            new Reference(
                xyArrayItem.getNodeId(),
                ReferenceTypes.HasComponent,
                arrayItemFolder.getNodeId().expanded(),
                Direction.INVERSE));
      }
    }

    // Add YArrayItemType instance
    {
      NodeId yArrayItemTypeId = new NodeId(UShort.valueOf(0), 12029);
      UaNode node =
          getNodeFactory()
              .createNode(
                  deriveChildNodeId(arrayItemFolder.getNodeId(), "YArrayItem"), yArrayItemTypeId);

      if (node instanceof YArrayItemTypeNode yArrayItem) {
        yArrayItem.setBrowseName(new QualifiedName(namespaceIndex, "YArrayItem"));
        yArrayItem.setDisplayName(new LocalizedText("YArrayItem"));
        yArrayItem.setDataType(NodeIds.Double);
        yArrayItem.setValueRank(ValueRanks.OneDimension);
        yArrayItem.setArrayDimensions(new UInteger[] {uint(0)});
        yArrayItem.setAccessLevel(AccessLevel.toValue(AccessLevel.READ_WRITE));
        yArrayItem.setUserAccessLevel(AccessLevel.toValue(AccessLevel.READ_WRITE));
        // Create 1D array data: 6 Y values
        Double[] yData = new Double[] {1.0, 2.0, 3.0, 4.0, 5.0, 6.0};
        yArrayItem.setValue(new DataValue(Variant.ofDoubleArray(yData)));

        // Set ArrayItemType properties
        yArrayItem.setInstrumentRange(new Range(0.0, 100.0));
        yArrayItem.setEuRange(new Range(0.0, 100.0));
        yArrayItem.setEngineeringUnits(
            new EUInformation("", -1, new LocalizedText(""), new LocalizedText("units")));
        yArrayItem.setTitle(new LocalizedText("YArray Item"));
        yArrayItem.setAxisScaleType(AxisScaleEnumeration.Linear);

        // Set YArrayItemType specific properties
        yArrayItem.setXAxisDefinition(
            new AxisInformation(
                new EUInformation("", -1, new LocalizedText(""), new LocalizedText("units")),
                new Range(0.0, 5.0),
                new LocalizedText("X Axis"),
                AxisScaleEnumeration.Linear,
                new Double[] {0.0, 1.0, 2.0, 3.0, 4.0, 5.0}));

        yArrayItem.getFilterChain().addLast(EuRangeCheckFilter.INSTANCE);

        getNodeManager().addNode(yArrayItem);

        yArrayItem.addReference(
            new Reference(
                yArrayItem.getNodeId(),
                ReferenceTypes.HasComponent,
                arrayItemFolder.getNodeId().expanded(),
                Direction.INVERSE));
      }
    }
  }

  private void addDataItemTypeNodes(NodeId parentNodeId) throws UaException {
    var dataTypeFolder =
        new UaFolderNode(
            getNodeContext(),
            deriveChildNodeId(parentNodeId, "DataItemType"),
            new QualifiedName(namespaceIndex, "DataItemType"),
            new LocalizedText("DataItemType"));

    getNodeManager().addNode(dataTypeFolder);

    dataTypeFolder.addReference(
        new Reference(
            dataTypeFolder.getNodeId(),
            ReferenceTypes.HasComponent,
            parentNodeId.expanded(),
            Direction.INVERSE));

    var dataTypes =
        List.of(
            OpcUaDataType.Byte,
            OpcUaDataType.DateTime,
            OpcUaDataType.Double,
            OpcUaDataType.Float,
            OpcUaDataType.Int16,
            OpcUaDataType.Int32,
            OpcUaDataType.Int64,
            OpcUaDataType.SByte,
            OpcUaDataType.String,
            OpcUaDataType.UInt16,
            OpcUaDataType.UInt32,
            OpcUaDataType.UInt64);

    for (OpcUaDataType dataType : dataTypes) {
      UaNode node =
          getNodeFactory()
              .createNode(
                  deriveChildNodeId(dataTypeFolder.getNodeId(), dataType.name()),
                  NodeIds.DataItemType);

      if (node instanceof DataItemTypeNode dataItemNode) {
        dataItemNode.setBrowseName(new QualifiedName(namespaceIndex, dataType.name()));
        dataItemNode.setDisplayName(new LocalizedText(dataType.name()));
        dataItemNode.setDataType(dataType.getNodeId());
        dataItemNode.setAccessLevel(AccessLevel.toValue(AccessLevel.READ_WRITE));
        dataItemNode.setUserAccessLevel(AccessLevel.toValue(AccessLevel.READ_WRITE));
        dataItemNode.setMinimumSamplingInterval(100.0);

        Object value = Util.getDefaultScalarValue(dataType);
        if (value instanceof Variant v) {
          dataItemNode.setValue(new DataValue(v));
        } else {
          dataItemNode.setValue(new DataValue(Variant.of(value)));
        }

        getNodeManager().addNode(dataItemNode);

        dataItemNode.addReference(
            new Reference(
                dataItemNode.getNodeId(),
                ReferenceTypes.HasComponent,
                dataTypeFolder.getNodeId().expanded(),
                Direction.INVERSE));
      }
    }
  }

  private void addDiscreteItemTypeNodes(NodeId parentNodeId) throws UaException {
    var discreteItemTypeFolder =
        new UaFolderNode(
            getNodeContext(),
            deriveChildNodeId(parentNodeId, "DiscreteItemType"),
            new QualifiedName(namespaceIndex, "DiscreteItemType"),
            new LocalizedText("DiscreteItemType"));

    getNodeManager().addNode(discreteItemTypeFolder);

    discreteItemTypeFolder.addReference(
        new Reference(
            discreteItemTypeFolder.getNodeId(),
            ReferenceTypes.HasComponent,
            parentNodeId.expanded(),
            Direction.INVERSE));

    // Add MultiStateDiscrete nodes (001-005)
    for (int i = 1; i <= 5; i++) {
      String nodeName = "MultiStateDiscrete%03d".formatted(i);
      UaNode node =
          getNodeFactory()
              .createNode(
                  deriveChildNodeId(discreteItemTypeFolder.getNodeId(), nodeName),
                  NodeIds.MultiStateDiscreteType);

      if (node instanceof MultiStateDiscreteTypeNode multiStateNode) {
        multiStateNode.setBrowseName(new QualifiedName(namespaceIndex, nodeName));
        multiStateNode.setDisplayName(new LocalizedText(nodeName));
        multiStateNode.setDataType(NodeIds.UInt32);
        multiStateNode.setAccessLevel(AccessLevel.toValue(AccessLevel.READ_WRITE));
        multiStateNode.setUserAccessLevel(AccessLevel.toValue(AccessLevel.READ_WRITE));
        multiStateNode.setMinimumSamplingInterval(100.0);

        // Set mandatory EnumStrings property
        multiStateNode.setEnumStrings(
            new LocalizedText[] {
              new LocalizedText("State0"),
              new LocalizedText("State1"),
              new LocalizedText("State2"),
              new LocalizedText("State3")
            });

        multiStateNode.setValue(new DataValue(Variant.ofUInt32(uint(0))));

        getNodeManager().addNode(multiStateNode);

        multiStateNode.addReference(
            new Reference(
                multiStateNode.getNodeId(),
                ReferenceTypes.HasComponent,
                discreteItemTypeFolder.getNodeId().expanded(),
                Direction.INVERSE));
      }
    }

    // Add TwoStateDiscrete nodes (001-005)
    for (int i = 1; i <= 5; i++) {
      String nodeName = "TwoStateDiscrete%03d".formatted(i);
      UaNode node =
          getNodeFactory()
              .createNode(
                  deriveChildNodeId(discreteItemTypeFolder.getNodeId(), nodeName),
                  NodeIds.TwoStateDiscreteType);

      if (node instanceof TwoStateDiscreteTypeNode twoStateNode) {
        twoStateNode.setBrowseName(new QualifiedName(namespaceIndex, nodeName));
        twoStateNode.setDisplayName(new LocalizedText(nodeName));
        twoStateNode.setDataType(NodeIds.Boolean);
        twoStateNode.setAccessLevel(AccessLevel.toValue(AccessLevel.READ_WRITE));
        twoStateNode.setUserAccessLevel(AccessLevel.toValue(AccessLevel.READ_WRITE));
        twoStateNode.setMinimumSamplingInterval(100.0);

        // Set mandatory TrueState and FalseState properties
        twoStateNode.setTrueState(new LocalizedText("True"));
        twoStateNode.setFalseState(new LocalizedText("False"));

        twoStateNode.setValue(new DataValue(Variant.ofBoolean(false)));

        getNodeManager().addNode(twoStateNode);

        twoStateNode.addReference(
            new Reference(
                twoStateNode.getNodeId(),
                ReferenceTypes.HasComponent,
                discreteItemTypeFolder.getNodeId().expanded(),
                Direction.INVERSE));
      }
    }
  }

  private void addMultiStateValueDiscreteTypeNodes(NodeId parentNodeId) throws UaException {
    var multiStateValueDiscreteTypeFolder =
        new UaFolderNode(
            getNodeContext(),
            deriveChildNodeId(parentNodeId, "MultiStateValueDiscreteType"),
            new QualifiedName(namespaceIndex, "MultiStateValueDiscreteType"),
            new LocalizedText("MultiStateValueDiscreteType"));

    getNodeManager().addNode(multiStateValueDiscreteTypeFolder);

    multiStateValueDiscreteTypeFolder.addReference(
        new Reference(
            multiStateValueDiscreteTypeFolder.getNodeId(),
            ReferenceTypes.HasComponent,
            parentNodeId.expanded(),
            Direction.INVERSE));

    var dataTypes =
        List.of(
            OpcUaDataType.Byte,
            OpcUaDataType.Int16,
            OpcUaDataType.Int32,
            OpcUaDataType.Int64,
            OpcUaDataType.SByte,
            OpcUaDataType.UInt16,
            OpcUaDataType.UInt32,
            OpcUaDataType.UInt64);

    for (OpcUaDataType dataType : dataTypes) {
      UaNode node =
          getNodeFactory()
              .createNode(
                  deriveChildNodeId(multiStateValueDiscreteTypeFolder.getNodeId(), dataType.name()),
                  NodeIds.MultiStateValueDiscreteType);

      if (node instanceof MultiStateValueDiscreteTypeNode multiStateValueNode) {
        multiStateValueNode.setBrowseName(new QualifiedName(namespaceIndex, dataType.name()));
        multiStateValueNode.setDisplayName(new LocalizedText(dataType.name()));
        multiStateValueNode.setDataType(dataType.getNodeId());
        multiStateValueNode.setAccessLevel(AccessLevel.toValue(AccessLevel.READ_WRITE));
        multiStateValueNode.setUserAccessLevel(AccessLevel.toValue(AccessLevel.READ_WRITE));
        multiStateValueNode.setMinimumSamplingInterval(100.0);

        // Set mandatory EnumValues property
        multiStateValueNode.setEnumValues(
            new EnumValueType[] {
              new EnumValueType(0L, new LocalizedText("Value0"), new LocalizedText("Value0")),
              new EnumValueType(1L, new LocalizedText("Value1"), new LocalizedText("Value1")),
              new EnumValueType(2L, new LocalizedText("Value2"), new LocalizedText("Value2")),
              new EnumValueType(3L, new LocalizedText("Value3"), new LocalizedText("Value3"))
            });

        // Set mandatory ValueAsText property
        multiStateValueNode.setValueAsText(new LocalizedText("Value0"));

        Object value = Util.getDefaultScalarValue(dataType);
        if (value instanceof Variant v) {
          multiStateValueNode.setValue(new DataValue(v));
        } else {
          multiStateValueNode.setValue(new DataValue(Variant.of(value)));
        }

        getNodeManager().addNode(multiStateValueNode);

        multiStateValueNode.addReference(
            new Reference(
                multiStateValueNode.getNodeId(),
                ReferenceTypes.HasComponent,
                multiStateValueDiscreteTypeFolder.getNodeId().expanded(),
                Direction.INVERSE));
      }
    }
  }
}

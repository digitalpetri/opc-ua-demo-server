package com.digitalpetri.opcua.server.namespaces.demo

import com.digitalpetri.opcua.milo.extensions.inverseReferenceTo
import com.digitalpetri.opcua.server.types.CustomEnumType
import com.digitalpetri.opcua.server.types.CustomStructType
import com.digitalpetri.opcua.server.types.CustomUnionType
import org.eclipse.milo.opcua.sdk.core.AccessLevel
import org.eclipse.milo.opcua.sdk.core.Reference
import org.eclipse.milo.opcua.sdk.core.ValueRanks
import org.eclipse.milo.opcua.sdk.core.dtd.BinaryDataTypeCodec
import org.eclipse.milo.opcua.sdk.server.OpcUaServer
import org.eclipse.milo.opcua.sdk.server.api.AddressSpaceComposite
import org.eclipse.milo.opcua.sdk.server.dtd.BinaryDataTypeDictionaryManager
import org.eclipse.milo.opcua.sdk.server.nodes.UaDataTypeNode
import org.eclipse.milo.opcua.sdk.server.nodes.UaFolderNode
import org.eclipse.milo.opcua.sdk.server.nodes.UaVariableNode
import org.eclipse.milo.opcua.stack.core.BuiltinDataType
import org.eclipse.milo.opcua.stack.core.NodeIds
import org.eclipse.milo.opcua.stack.core.types.builtin.*
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.UShort
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.Unsigned
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.Unsigned.uint
import org.eclipse.milo.opcua.stack.core.types.enumerated.StructureType
import org.eclipse.milo.opcua.stack.core.types.structured.*

class ComplexTypesFragment(
    server: OpcUaServer,
    composite: AddressSpaceComposite,
    private val namespaceIndex: UShort
) : SampledAddressSpaceFragment(server, composite) {

    private val dictionaryManager = BinaryDataTypeDictionaryManager(
        nodeContext,
        DemoNamespace.NAMESPACE_URI
    )

    init {
        lifecycleManager.addLifecycle(dictionaryManager)

        lifecycleManager.addStartupTask { addComplexTypeNodes() }
    }

    private fun addComplexTypeNodes() {
        val complexTypesFolder = UaFolderNode(
            nodeContext,
            NodeId(namespaceIndex, "ComplexTypes"),
            QualifiedName(namespaceIndex, "ComplexTypes"),
            LocalizedText("ComplexTypes")
        )

        nodeManager.addNode(complexTypesFolder)

        complexTypesFolder.inverseReferenceTo(
            NodeIds.ObjectsFolder,
            NodeIds.HasComponent
        )

        registerCustomEnumType()
        registerCustomStructType()
        registerCustomUnionType()

        addCustomEnumTypeVariable(complexTypesFolder)
        addCustomStructTypeVariable(complexTypesFolder)
        addCustomUnionTypeVariable(complexTypesFolder)
    }

    private fun addCustomEnumTypeVariable(rootFolder: UaFolderNode) {
        val dataTypeId: NodeId = CustomEnumType.TYPE_ID
            .toNodeIdOrThrow(server.namespaceTable)

        val customEnumTypeVariable = UaVariableNode.build(nodeContext) {
            it.setNodeId(NodeId(namespaceIndex, "ComplexTypes/CustomEnumTypeVariable"))
            it.setAccessLevel(AccessLevel.toValue(AccessLevel.READ_WRITE))
            it.setUserAccessLevel(AccessLevel.toValue(AccessLevel.READ_WRITE))
            it.setBrowseName(QualifiedName(namespaceIndex, "CustomEnumTypeVariable"))
            it.setDisplayName(LocalizedText.english("CustomEnumTypeVariable"))
            it.setDataType(dataTypeId)
            it.setTypeDefinition(NodeIds.BaseDataVariableType)
            it.build()
        }

        customEnumTypeVariable.value = DataValue(Variant(CustomEnumType.Field1))

        nodeManager.addNode(customEnumTypeVariable)

        customEnumTypeVariable.addReference(
            Reference(
                customEnumTypeVariable.nodeId,
                NodeIds.Organizes,
                rootFolder.nodeId.expanded(),
                false
            )
        )
    }

    private fun addCustomStructTypeVariable(rootFolder: UaFolderNode) {
        val dataTypeId: NodeId = CustomStructType.TYPE_ID
            .toNodeIdOrThrow(server.namespaceTable)
        val binaryEncodingId: NodeId = CustomStructType.BINARY_ENCODING_ID
            .toNodeIdOrThrow(server.namespaceTable)

        val customStructTypeVariable = UaVariableNode.build(nodeContext) {
            it.setNodeId(NodeId(namespaceIndex, "ComplexTypes/CustomStructTypeVariable"))
            it.setAccessLevel(AccessLevel.toValue(AccessLevel.READ_WRITE))
            it.setUserAccessLevel(AccessLevel.toValue(AccessLevel.READ_WRITE))
            it.setBrowseName(QualifiedName(namespaceIndex, "CustomStructTypeVariable"))
            it.setDisplayName(LocalizedText.english("CustomStructTypeVariable"))
            it.setDataType(dataTypeId)
            it.setTypeDefinition(NodeIds.BaseDataVariableType)
            it.build()
        }

        val value = CustomStructType(
            "foo",
            uint(42),
            true
        )
        val xo = ExtensionObject.encodeDefaultBinary(
            server.encodingContext,
            value,
            binaryEncodingId
        )
        customStructTypeVariable.value = DataValue(Variant(xo))

        nodeManager.addNode(customStructTypeVariable)

        customStructTypeVariable.addReference(
            Reference(
                customStructTypeVariable.nodeId,
                NodeIds.Organizes,
                rootFolder.nodeId.expanded(),
                false
            )
        )
    }

    private fun addCustomUnionTypeVariable(rootFolder: UaFolderNode) {
        val dataTypeId: NodeId = CustomUnionType.TYPE_ID
            .toNodeIdOrThrow(server.namespaceTable)
        val binaryEncodingId: NodeId = CustomUnionType.BINARY_ENCODING_ID
            .toNodeIdOrThrow(server.namespaceTable)

        val customUnionTypeVariable = UaVariableNode.build(nodeContext) {
            it.setNodeId(NodeId(namespaceIndex, "ComplexTypes/CustomUnionTypeVariable"))
            it.setAccessLevel(AccessLevel.toValue(AccessLevel.READ_WRITE))
            it.setUserAccessLevel(AccessLevel.toValue(AccessLevel.READ_WRITE))
            it.setBrowseName(QualifiedName(namespaceIndex, "CustomUnionTypeVariable"))
            it.setDisplayName(LocalizedText.english("CustomUnionTypeVariable"))
            it.setDataType(dataTypeId)
            it.setTypeDefinition(NodeIds.BaseDataVariableType)
            it.build()
        }

        val value: CustomUnionType = CustomUnionType.ofBar("hello")
        val xo = ExtensionObject.encodeDefaultBinary(
            server.encodingContext,
            value,
            binaryEncodingId
        )
        customUnionTypeVariable.value = DataValue(Variant(xo))

        nodeManager.addNode(customUnionTypeVariable)

        customUnionTypeVariable.addReference(
            Reference(
                customUnionTypeVariable.nodeId,
                NodeIds.Organizes,
                rootFolder.nodeId.expanded(),
                false
            )
        )
    }

    private fun registerCustomEnumType() {
        val dataTypeId = CustomEnumType.TYPE_ID
            .toNodeIdOrThrow(server.namespaceTable)

        // Add a custom DataTypeNode with a SubtypeOf reference to Enumeration
        val dataTypeNode = UaDataTypeNode(
            nodeContext,
            dataTypeId,
            QualifiedName(namespaceIndex, "CustomEnumType"),
            LocalizedText.english("CustomEnumType"),
            LocalizedText.NULL_VALUE,
            uint(0),
            uint(0),
            false
        )

        dataTypeNode.addReference(
            Reference(
                dataTypeId,
                NodeIds.HasSubtype,
                NodeIds.Enumeration.expanded(),
                Reference.Direction.INVERSE
            )
        )

        nodeManager.addNode(dataTypeNode)

        // Define the enumeration
        val fields = arrayOf(
            EnumField(
                0L,
                LocalizedText.english("Field0"),
                LocalizedText.NULL_VALUE,
                "Field0"
            ),
            EnumField(
                1L,
                LocalizedText.english("Field1"),
                LocalizedText.NULL_VALUE,
                "Field1"
            ),
            EnumField(
                2L,
                LocalizedText.english("Field2"),
                LocalizedText.NULL_VALUE,
                "Field2"
            )
        )

        val definition = EnumDefinition(fields)

        dataTypeNode.dataTypeDefinition = definition

        // This Enum is zero-based and naturally incrementing, so we set the EnumStrings property.
        // If it were more complex the EnumValues property would be used instead.
        dataTypeNode.setEnumStrings(
            arrayOf(
                LocalizedText.english("Field0"),
                LocalizedText.english("Field1"),
                LocalizedText.english("Field2")
            )
        )

        // Legacy DataTypeDictionary support
        val description = EnumDescription(
            dataTypeId,
            QualifiedName(namespaceIndex, "CustomEnumType"),
            definition,
            Unsigned.ubyte(BuiltinDataType.Int32.typeId)
        )

        dictionaryManager.registerEnum(description)
    }

    private fun registerCustomStructType() {
        // Get the NodeId for the DataType and encoding Nodes.
        val dataTypeId: NodeId = CustomStructType.TYPE_ID
            .toNodeIdOrThrow(server.namespaceTable)
        val binaryEncodingId: NodeId = CustomStructType.BINARY_ENCODING_ID
            .toNodeIdOrThrow(server.namespaceTable)

        // Add a custom DataTypeNode with a SubtypeOf reference to Structure
        val dataTypeNode = UaDataTypeNode(
            nodeContext,
            dataTypeId,
            QualifiedName(namespaceIndex, "CustomStructType"),
            LocalizedText.english("CustomStructType"),
            LocalizedText.NULL_VALUE,
            uint(0),
            uint(0),
            false
        )

        dataTypeNode.addReference(
            Reference(
                dataTypeId,
                NodeIds.HasSubtype,
                NodeIds.Structure.expanded(),
                Reference.Direction.INVERSE
            )
        )

        nodeManager.addNode(dataTypeNode)

        // Define the struct
        val fields = arrayOf(
            StructureField(
                "foo",
                LocalizedText.NULL_VALUE,
                NodeIds.String,
                ValueRanks.Scalar,
                null,
                server.config.limits.maxStringLength,
                false
            ),
            StructureField(
                "bar",
                LocalizedText.NULL_VALUE,
                NodeIds.UInt32,
                ValueRanks.Scalar,
                null,
                uint(0),
                false
            ),
            StructureField(
                "baz",
                LocalizedText.NULL_VALUE,
                NodeIds.Boolean,
                ValueRanks.Scalar,
                null,
                uint(0),
                false
            )
        )

        val definition = StructureDefinition(
            binaryEncodingId,
            NodeIds.Structure,
            StructureType.Structure,
            fields
        )

        dataTypeNode.dataTypeDefinition = definition

        // Register Codecs for each supported encoding with DataTypeManager
        nodeContext.server.dataTypeManager.registerType(
            dataTypeId,
            CustomStructType.Codec(),
            binaryEncodingId,
            null,
            null
        )

        // Legacy DataTypeDictionary support
        val description = StructureDescription(
            dataTypeId,
            QualifiedName(namespaceIndex, "CustomStructType"),
            definition
        )

        dictionaryManager.registerStructure(
            "CustomStructType",
            dataTypeId,
            binaryEncodingId,
            BinaryDataTypeCodec.from(CustomStructType.Codec()),
            description
        )
    }

    private fun registerCustomUnionType() {
        val dataTypeId: NodeId = CustomUnionType.TYPE_ID
            .toNodeIdOrThrow(server.namespaceTable)
        val binaryEncodingId: NodeId = CustomUnionType.BINARY_ENCODING_ID
            .toNodeIdOrThrow(server.namespaceTable)

        // Add a custom DataTypeNode with a SubtypeOf reference to Union
        val dataTypeNode = UaDataTypeNode(
            nodeContext,
            dataTypeId,
            QualifiedName(namespaceIndex, "CustomUnionType"),
            LocalizedText.english("CustomUnionType"),
            LocalizedText.NULL_VALUE,
            uint(0),
            uint(0),
            false
        )

        dataTypeNode.addReference(
            Reference(
                dataTypeId,
                NodeIds.HasSubtype,
                NodeIds.Union.expanded(),
                Reference.Direction.INVERSE
            )
        )

        nodeManager.addNode(dataTypeNode)

        // Define the Union
        val fields = arrayOf(
            StructureField(
                "foo",
                LocalizedText.NULL_VALUE,
                NodeIds.UInt32,
                ValueRanks.Scalar,
                null,
                server.config.limits.maxStringLength,
                false
            ),
            StructureField(
                "bar",
                LocalizedText.NULL_VALUE,
                NodeIds.String,
                ValueRanks.Scalar,
                null,
                uint(0),
                false
            )
        )

        val definition = StructureDefinition(
            binaryEncodingId,
            NodeIds.Structure,
            StructureType.Union,
            fields
        )

        dataTypeNode.dataTypeDefinition = definition

        // Register Codecs for each supported encoding with DataTypeManager
        nodeContext.server.dataTypeManager.registerType(
            dataTypeId,
            CustomUnionType.Codec(),
            binaryEncodingId,
            null,
            null
        )

        // Legacy DataTypeDictionary support
        val description = StructureDescription(
            dataTypeId,
            QualifiedName(namespaceIndex, "CustomUnionType"),
            definition
        )

        dictionaryManager.registerStructure(
            "CustomUnionType",
            dataTypeId,
            binaryEncodingId,
            BinaryDataTypeCodec.from(CustomUnionType.Codec()),
            description
        )
    }

}


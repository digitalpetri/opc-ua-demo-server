package com.digitalpetri.opcua.server.namespaces.demo

import com.digitalpetri.opcua.milo.extensions.inverseReferenceTo
import com.digitalpetri.opcua.server.types.CustomEnumType
import com.digitalpetri.opcua.server.types.CustomStructType
import com.digitalpetri.opcua.server.types.CustomUnionType
import org.eclipse.milo.opcua.sdk.core.AccessLevel
import org.eclipse.milo.opcua.sdk.core.Reference
import org.eclipse.milo.opcua.sdk.core.ValueRanks
import org.eclipse.milo.opcua.sdk.server.OpcUaServer
import org.eclipse.milo.opcua.sdk.server.api.AddressSpaceComposite
import org.eclipse.milo.opcua.sdk.server.dtd.DataTypeDictionaryManager
import org.eclipse.milo.opcua.sdk.server.nodes.UaFolderNode
import org.eclipse.milo.opcua.sdk.server.nodes.UaVariableNode
import org.eclipse.milo.opcua.stack.core.BuiltinDataType
import org.eclipse.milo.opcua.stack.core.Identifiers
import org.eclipse.milo.opcua.stack.core.types.builtin.*
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.UShort
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.Unsigned
import org.eclipse.milo.opcua.stack.core.types.enumerated.StructureType
import org.eclipse.milo.opcua.stack.core.types.structured.*

class ComplexTypesFragment(
    server: OpcUaServer,
    composite: AddressSpaceComposite,
    private val namespaceIndex: UShort
) : SampledAddressSpaceFragment(server, composite) {

    private val dictionaryManager = DataTypeDictionaryManager(
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
            Identifiers.ObjectsFolder,
            Identifiers.HasComponent
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

        val customEnumTypeVariable =
            UaVariableNode.builder(nodeContext)
                .setNodeId(NodeId(namespaceIndex, "ComplexTypes/CustomEnumTypeVariable"))
                .setAccessLevel(AccessLevel.toValue(AccessLevel.READ_WRITE))
                .setUserAccessLevel(AccessLevel.toValue(AccessLevel.READ_WRITE))
                .setBrowseName(QualifiedName(namespaceIndex, "CustomEnumTypeVariable"))
                .setDisplayName(LocalizedText.english("CustomEnumTypeVariable"))
                .setDataType(dataTypeId)
                .setTypeDefinition(Identifiers.BaseDataVariableType)
                .build()

        customEnumTypeVariable.value = DataValue(Variant(CustomEnumType.Field1))

        nodeManager.addNode(customEnumTypeVariable)

        customEnumTypeVariable.addReference(
            Reference(
                customEnumTypeVariable.nodeId,
                Identifiers.Organizes,
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

        val customStructTypeVariable =
            UaVariableNode.builder(nodeContext)
                .setNodeId(NodeId(namespaceIndex, "ComplexTypes/CustomStructTypeVariable"))
                .setAccessLevel(AccessLevel.toValue(AccessLevel.READ_WRITE))
                .setUserAccessLevel(AccessLevel.toValue(AccessLevel.READ_WRITE))
                .setBrowseName(QualifiedName(namespaceIndex, "CustomStructTypeVariable"))
                .setDisplayName(LocalizedText.english("CustomStructTypeVariable"))
                .setDataType(dataTypeId)
                .setTypeDefinition(Identifiers.BaseDataVariableType)
                .build()

        val value = CustomStructType(
            "foo",
            Unsigned.uint(42),
            true
        )
        val xo = ExtensionObject.encodeDefaultBinary(
            server.serializationContext,
            value,
            binaryEncodingId
        )
        customStructTypeVariable.value = DataValue(Variant(xo))

        nodeManager.addNode(customStructTypeVariable)

        customStructTypeVariable.addReference(
            Reference(
                customStructTypeVariable.nodeId,
                Identifiers.Organizes,
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

        val customUnionTypeVariable =
            UaVariableNode.builder(nodeContext)
                .setNodeId(NodeId(namespaceIndex, "ComplexTypes/CustomUnionTypeVariable"))
                .setAccessLevel(AccessLevel.toValue(AccessLevel.READ_WRITE))
                .setUserAccessLevel(AccessLevel.toValue(AccessLevel.READ_WRITE))
                .setBrowseName(QualifiedName(namespaceIndex, "CustomUnionTypeVariable"))
                .setDisplayName(LocalizedText.english("CustomUnionTypeVariable"))
                .setDataType(dataTypeId)
                .setTypeDefinition(Identifiers.BaseDataVariableType)
                .build()

        val value: CustomUnionType = CustomUnionType.ofBar("hello")
        val xo = ExtensionObject.encodeDefaultBinary(
            server.serializationContext,
            value,
            binaryEncodingId
        )
        customUnionTypeVariable.value = DataValue(Variant(xo))

        nodeManager.addNode(customUnionTypeVariable)

        customUnionTypeVariable.addReference(
            Reference(
                customUnionTypeVariable.nodeId,
                Identifiers.Organizes,
                rootFolder.nodeId.expanded(),
                false
            )
        )
    }

    private fun registerCustomEnumType() {
        val dataTypeId = CustomEnumType.TYPE_ID
            .toNodeIdOrThrow(server.namespaceTable)

        dictionaryManager.registerEnumCodec(
            CustomEnumType.Codec().asBinaryCodec(),
            "CustomEnumType",
            dataTypeId
        )
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

        val description = EnumDescription(
            dataTypeId,
            QualifiedName(namespaceIndex, "CustomEnumType"),
            definition,
            Unsigned.ubyte(BuiltinDataType.Int32.typeId)
        )

        dictionaryManager.registerEnumDescription(description)
    }

    private fun registerCustomStructType() {
        // Get the NodeId for the DataType and encoding Nodes.
        val dataTypeId = CustomStructType.TYPE_ID
            .toNodeIdOrThrow(server.namespaceTable)
        val binaryEncodingId = CustomStructType.BINARY_ENCODING_ID
            .toNodeIdOrThrow(server.namespaceTable)

        // At a minimum, custom types must have their codec registered.
        // If clients don't need to dynamically discover types and will
        // register the codecs on their own then this is all that is
        // necessary.
        // The dictionary manager will add a corresponding DataType Node to
        // the AddressSpace.
        dictionaryManager.registerStructureCodec(
            CustomStructType.Codec().asBinaryCodec(),
            "CustomStructType",
            dataTypeId,
            binaryEncodingId
        )

        // If the custom type also needs to be discoverable by clients then it
        // needs an entry in a DataTypeDictionary that can be read by those
        // clients. We describe the type using StructureDefinition or
        // EnumDefinition and register it with the dictionary manager.
        // The dictionary manager will add all the necessary nodes to the
        // AddressSpace and generate the required dictionary bsd.xml file.
        val fields = arrayOf(
            StructureField(
                "foo",
                LocalizedText.NULL_VALUE,
                Identifiers.String,
                ValueRanks.Scalar,
                null,
                server.config.limits.maxStringLength,
                false
            ),
            StructureField(
                "bar",
                LocalizedText.NULL_VALUE,
                Identifiers.UInt32,
                ValueRanks.Scalar,
                null,
                Unsigned.uint(0),
                false
            ),
            StructureField(
                "baz",
                LocalizedText.NULL_VALUE,
                Identifiers.Boolean,
                ValueRanks.Scalar,
                null,
                Unsigned.uint(0),
                false
            )
        )

        val definition = StructureDefinition(
            binaryEncodingId,
            Identifiers.Structure,
            StructureType.Structure,
            fields
        )
        val description = StructureDescription(
            dataTypeId,
            QualifiedName(namespaceIndex, "CustomStructType"),
            definition
        )

        dictionaryManager.registerStructureDescription(description, binaryEncodingId)
    }

    private fun registerCustomUnionType() {
        val dataTypeId = CustomUnionType.TYPE_ID
            .toNodeIdOrThrow(server.namespaceTable)
        val binaryEncodingId = CustomUnionType.BINARY_ENCODING_ID
            .toNodeIdOrThrow(server.namespaceTable)

        dictionaryManager.registerUnionCodec(
            CustomUnionType.Codec().asBinaryCodec(),
            "CustomUnionType",
            dataTypeId,
            binaryEncodingId
        )

        val fields = arrayOf(
            StructureField(
                "foo",
                LocalizedText.NULL_VALUE,
                Identifiers.UInt32,
                ValueRanks.Scalar,
                null,
                server.config.limits.maxStringLength,
                false
            ),
            StructureField(
                "bar",
                LocalizedText.NULL_VALUE,
                Identifiers.String,
                ValueRanks.Scalar,
                null,
                Unsigned.uint(0),
                false
            )
        )

        val definition = StructureDefinition(
            binaryEncodingId,
            Identifiers.Structure,
            StructureType.Union,
            fields
        )
        val description = StructureDescription(
            dataTypeId,
            QualifiedName(namespaceIndex, "CustomUnionType"),
            definition
        )

        dictionaryManager.registerStructureDescription(description, binaryEncodingId)
    }

}


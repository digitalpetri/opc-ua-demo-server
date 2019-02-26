package com.isw.opcua.server.namespaces

import org.eclipse.milo.opcua.sdk.core.AccessLevel
import org.eclipse.milo.opcua.sdk.core.Reference
import org.eclipse.milo.opcua.sdk.core.ValueRank
import org.eclipse.milo.opcua.sdk.server.api.nodes.VariableNode
import org.eclipse.milo.opcua.sdk.server.model.nodes.variables.AnalogItemNode
import org.eclipse.milo.opcua.sdk.server.model.types.variables.AnalogItemType
import org.eclipse.milo.opcua.sdk.server.nodes.AttributeContext
import org.eclipse.milo.opcua.sdk.server.nodes.UaFolderNode
import org.eclipse.milo.opcua.sdk.server.nodes.UaNode
import org.eclipse.milo.opcua.sdk.server.nodes.UaVariableNode.UaVariableNodeBuilder
import org.eclipse.milo.opcua.sdk.server.nodes.delegates.AttributeDelegate
import org.eclipse.milo.opcua.stack.core.BuiltinDataType
import org.eclipse.milo.opcua.stack.core.Identifiers
import org.eclipse.milo.opcua.stack.core.StatusCodes
import org.eclipse.milo.opcua.stack.core.UaException
import org.eclipse.milo.opcua.stack.core.types.builtin.*
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.Unsigned.*
import org.eclipse.milo.opcua.stack.core.types.structured.Range
import java.util.*


fun DemoNamespace.addCttNodes() {
    val cttFolder = UaFolderNode(
        server,
        NodeId(namespaceIndex, "CTT"),
        QualifiedName(namespaceIndex, "CTT"),
        LocalizedText("CTT")
    )

    nodeManager.addNode(cttFolder)

    cttFolder.inverseReferenceTo(
        Identifiers.ObjectsFolder,
        Identifiers.HasComponent
    )

    addStaticNodes(cttFolder.nodeId)
}

private fun DemoNamespace.addStaticNodes(parentNodeId: NodeId) {
    val staticFolder = addFolderNode(parentNodeId, "Static")

    addAllProfilesNode(staticFolder.nodeId)
    addDaProfileNode(staticFolder.nodeId)
}

private fun DemoNamespace.addAllProfilesNode(parentNodeId: NodeId) {
    val allProfilesFolder = addFolderNode(parentNodeId, "All Profiles")

    addScalarNodes(allProfilesFolder.nodeId)
    addArrayNodes(allProfilesFolder.nodeId)
}

private fun DemoNamespace.addScalarNodes(parentNodeId: NodeId) {
    val scalarFolder = addFolderNode(parentNodeId, "Scalar")

    val scalarTypes = listOf(
        BuiltinDataType.Byte,
        BuiltinDataType.Double,
        BuiltinDataType.Float,
        BuiltinDataType.Int16,
        BuiltinDataType.Int32,
        BuiltinDataType.Int64,
        BuiltinDataType.SByte,
        BuiltinDataType.UInt16,
        BuiltinDataType.UInt32,
        BuiltinDataType.UInt64
    )

    scalarTypes.forEach { dataType ->
        val name = dataType.name

        val node = UaVariableNodeBuilder(server).run {
            setNodeId(parentNodeId.resolve(name))
            setAccessLevel(ubyte(AccessLevel.getMask(AccessLevel.READ_WRITE)))
            setUserAccessLevel(ubyte(AccessLevel.getMask(AccessLevel.READ_WRITE)))
            setBrowseName(QualifiedName(namespaceIndex, name))
            setDisplayName(LocalizedText.english(name))
            setDataType(dataType.nodeId)
            setTypeDefinition(Identifiers.BaseDataVariableType)
            setMinimumSamplingInterval(100.0)

            build()
        }

        node.value = DataValue(Variant(dataType.defaultValue()))

        nodeManager.addNode(node)
        node.inverseReferenceTo(scalarFolder.nodeId, Identifiers.Organizes)
    }
}

private fun DemoNamespace.addArrayNodes(parentNodeId: NodeId) {
    val arrayFolder = addFolderNode(parentNodeId, "Array")

    val arrayTypes = listOf(
        BuiltinDataType.Byte,
        BuiltinDataType.Double,
        BuiltinDataType.Float,
        BuiltinDataType.Int16,
        BuiltinDataType.Int32,
        BuiltinDataType.Int64,
        BuiltinDataType.SByte,
        BuiltinDataType.UInt16,
        BuiltinDataType.UInt32,
        BuiltinDataType.UInt64
    )

    arrayTypes.forEach { dataType ->
        val name = "${dataType.name}Array"

        val node = UaVariableNodeBuilder(server).run {
            setNodeId(parentNodeId.resolve(name))
            setAccessLevel(ubyte(AccessLevel.getMask(AccessLevel.READ_WRITE)))
            setUserAccessLevel(ubyte(AccessLevel.getMask(AccessLevel.READ_WRITE)))
            setBrowseName(QualifiedName(namespaceIndex, name))
            setDisplayName(LocalizedText.english(name))
            setDataType(dataType.nodeId)
            setTypeDefinition(Identifiers.BaseDataVariableType)
            setValueRank(ValueRank.OneDimension.value)
            setArrayDimensions(arrayOf(uint(0)))
            setMinimumSamplingInterval(100.0)

            build()
        }

        node.value = DataValue(Variant(dataType.defaultValueArray()))

        nodeManager.addNode(node)
        node.inverseReferenceTo(arrayFolder.nodeId, Identifiers.Organizes)
    }

}

private fun DemoNamespace.addDaProfileNode(parentNodeId: NodeId) {
    val daProfileFolder = addFolderNode(parentNodeId, "DA Profile")

    addAnalogTypeNodes(daProfileFolder.nodeId)
}

private fun DemoNamespace.addAnalogTypeNodes(parentNodeId: NodeId) {
    val analogTypeFolder = addFolderNode(parentNodeId, "Analog Type")

    val analogTypes = listOf(
        BuiltinDataType.Byte,
        BuiltinDataType.Double,
        BuiltinDataType.Float,
        BuiltinDataType.Int16,
        BuiltinDataType.Int32,
        BuiltinDataType.Int64,
        BuiltinDataType.SByte,
        BuiltinDataType.UInt16,
        BuiltinDataType.UInt32,
        BuiltinDataType.UInt64
    )

    analogTypes.forEach { dataType ->
        val name = dataType.name

        val node = server.nodeFactory.createNode(
            parentNodeId.resolve(name),
            Identifiers.AnalogItemType,
            false
        ) as AnalogItemNode

        node.browseName = QualifiedName(namespaceIndex, name)
        node.displayName = LocalizedText(name)
        node.dataType = dataType.nodeId
        node.accessLevel = ubyte(AccessLevel.getMask(AccessLevel.READ_WRITE))
        node.userAccessLevel = ubyte(AccessLevel.getMask(AccessLevel.READ_WRITE))
        node.minimumSamplingInterval = 100.0

        node.euRange = Range(0.0, 100.0)
        node.value = DataValue(Variant(dataType.defaultValue()))

        node.setAttributeDelegate(EuRangeCheckDelegate)

        nodeManager.addNode(node)
        node.inverseReferenceTo(analogTypeFolder.nodeId, Identifiers.Organizes)
    }

}

fun DemoNamespace.addFolderNode(parentNodeId: NodeId, name: String): UaFolderNode {
    val folderNode = UaFolderNode(
        server,
        parentNodeId.resolve(name),
        QualifiedName(namespaceIndex, name),
        LocalizedText(name)
    )

    nodeManager.addNode(folderNode)

    folderNode.inverseReferenceTo(
        parentNodeId,
        Identifiers.HasComponent
    )

    return folderNode
}


fun NodeId.resolve(child: String): NodeId {
    val id = this.identifier.toString()
    return NodeId(this.namespaceIndex, "$id/$child")
}

fun UaNode.referenceTo(targetNodeId: NodeId, typeId: NodeId) {
    this.addReference(
        Reference(
            this.nodeId,
            typeId,
            targetNodeId.expanded(),
            true
        )
    )
}

fun UaNode.inverseReferenceTo(targetNodeId: NodeId, typeId: NodeId) {
    this.addReference(
        Reference(
            this.nodeId,
            typeId,
            targetNodeId.expanded(),
            false
        )
    )
}

private fun BuiltinDataType.defaultValue(): Any {
    return when (this) {
        BuiltinDataType.Boolean -> false
        BuiltinDataType.SByte -> 0.toByte()
        BuiltinDataType.Int16 -> 0.toShort()
        BuiltinDataType.Int32 -> 0
        BuiltinDataType.Int64 -> 0L
        BuiltinDataType.Byte -> ubyte(0)
        BuiltinDataType.UInt16 -> ushort(0)
        BuiltinDataType.UInt32 -> uint(0)
        BuiltinDataType.UInt64 -> ulong(0)
        BuiltinDataType.Float -> 0f
        BuiltinDataType.Double -> 0.0
        BuiltinDataType.String -> ""
        BuiltinDataType.DateTime -> DateTime.NULL_VALUE
        BuiltinDataType.Guid -> UUID.randomUUID()
        BuiltinDataType.ByteString -> ByteString.NULL_VALUE
        BuiltinDataType.XmlElement -> XmlElement(null)
        BuiltinDataType.NodeId -> NodeId.NULL_VALUE
        BuiltinDataType.ExpandedNodeId -> ExpandedNodeId.NULL_VALUE
        BuiltinDataType.StatusCode -> StatusCode.GOOD
        BuiltinDataType.QualifiedName -> QualifiedName.NULL_VALUE
        BuiltinDataType.LocalizedText -> LocalizedText.NULL_VALUE
        BuiltinDataType.ExtensionObject -> ExtensionObject(ByteString.NULL_VALUE, NodeId.NULL_VALUE)
        BuiltinDataType.DataValue -> DataValue(Variant.NULL_VALUE)
        BuiltinDataType.Variant -> Variant.NULL_VALUE
        BuiltinDataType.DiagnosticInfo -> DiagnosticInfo.NULL_VALUE
    }
}

private fun BuiltinDataType.defaultValueArray(length: Int = 5): Any {
    val value = defaultValue()
    val array = java.lang.reflect.Array.newInstance(this.backingClass, length)
    for (i in 0 until length) {
        java.lang.reflect.Array.set(array, i, value)
    }
    return array
}

/**
 * [AttributeDelegate] that checks to see if a VariableNode is an [AnalogItemType] and, if it is, throws an exception
 * with [StatusCodes.Bad_OutOfRange] when a value is written outside the configured EU [Range].
 */
private object EuRangeCheckDelegate : AttributeDelegate {

    override fun setValue(context: AttributeContext, node: VariableNode, value: DataValue) {
        if (node is AnalogItemType) {
            val low = node.euRange.low
            val high = node.euRange.high

            val v = value.value?.value

            if (v is Number) {
                val d = v.toDouble()

                if (d in low..high) {
                    super.setValue(context, node, value)
                } else {
                    throw UaException(StatusCodes.Bad_OutOfRange)
                }
            } else {
                throw UaException(StatusCodes.Bad_TypeMismatch)
            }
        }
    }

}

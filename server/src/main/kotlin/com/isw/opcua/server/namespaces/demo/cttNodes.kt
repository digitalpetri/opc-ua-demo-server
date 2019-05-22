package com.isw.opcua.server.namespaces.demo

import com.isw.opcua.milo.extensions.defaultValue
import com.isw.opcua.milo.extensions.defaultValueArray
import com.isw.opcua.milo.extensions.inverseReferenceTo
import com.isw.opcua.milo.extensions.resolve
import org.eclipse.milo.opcua.sdk.core.AccessLevel
import org.eclipse.milo.opcua.sdk.core.ValueRank
import org.eclipse.milo.opcua.sdk.server.api.nodes.VariableNode
import org.eclipse.milo.opcua.sdk.server.model.nodes.variables.AnalogItemTypeNode
import org.eclipse.milo.opcua.sdk.server.model.types.variables.AnalogItemType
import org.eclipse.milo.opcua.sdk.server.nodes.AttributeContext
import org.eclipse.milo.opcua.sdk.server.nodes.UaFolderNode
import org.eclipse.milo.opcua.sdk.server.nodes.delegates.AttributeDelegate
import org.eclipse.milo.opcua.stack.core.BuiltinDataType
import org.eclipse.milo.opcua.stack.core.Identifiers
import org.eclipse.milo.opcua.stack.core.StatusCodes
import org.eclipse.milo.opcua.stack.core.UaException
import org.eclipse.milo.opcua.stack.core.types.builtin.*
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.Unsigned.ubyte
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.Unsigned.uint
import org.eclipse.milo.opcua.stack.core.types.structured.Range


fun DemoNamespace.addCttNodes() {
    val cttFolder = UaFolderNode(
        nodeContext,
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
    addReferencesNodes(cttFolder.nodeId)
    addSecurityAccessNodes(cttFolder.nodeId)
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

    val scalarDataTypes = BuiltinDataType.values().filterNot {
        it == BuiltinDataType.Variant ||
            it == BuiltinDataType.DataValue ||
            it == BuiltinDataType.DiagnosticInfo
    }

    scalarDataTypes.forEach { dataType ->
        val name = dataType.name

        val node = addVariableNode(scalarFolder.nodeId, name, dataType = dataType)
        node.value = DataValue(Variant(dataType.defaultValue()))
    }
}

private fun DemoNamespace.addArrayNodes(parentNodeId: NodeId) {
    val arrayFolder = addFolderNode(parentNodeId, "Array")

    val arrayDataTypes = BuiltinDataType.values().filterNot {
        it == BuiltinDataType.Variant ||
            it == BuiltinDataType.DataValue ||
            it == BuiltinDataType.DiagnosticInfo
    }

    arrayDataTypes.forEach { dataType ->
        val name = "${dataType.name}Array"

        val node = addVariableNode(arrayFolder.nodeId, name, dataType = dataType)
        node.valueRank = ValueRank.OneDimension.value
        node.arrayDimensions = arrayOf(uint(0))
        node.value = DataValue(Variant(dataType.defaultValueArray()))
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

        val node = nodeFactory.createNode(
            parentNodeId.resolve(name),
            Identifiers.AnalogItemType,
            false
        ) as AnalogItemTypeNode

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

private fun DemoNamespace.addReferencesNodes(parentNodeId: NodeId) {
    val referencesFolder = addFolderNode(parentNodeId, "References")

    for (i in 1..5) {
        val folderNode = addFolderNode(referencesFolder.nodeId, "Has3ForwardRefs_$i")

        for (j in 1..3) {
            val name = "%03d".format(j)

            addVariableNode(folderNode.nodeId, name)
        }
    }
}

private fun DemoNamespace.addSecurityAccessNodes(parentNodeId: NodeId) {
    val securityAccessFolder = addFolderNode(parentNodeId, "SecurityAccess")

    val nodeWithCurrentRead = addVariableNode(
        securityAccessFolder.nodeId,
        "AccessLevel_CurrentRead"
    )
    nodeWithCurrentRead.accessLevel = ubyte(AccessLevel.CurrentRead.value)
    nodeWithCurrentRead.userAccessLevel = ubyte(AccessLevel.CurrentRead.value)

    val nodeWithCurrentWrite = addVariableNode(
        securityAccessFolder.nodeId,
        "AccessLevel_CurrentWrite"
    )
    nodeWithCurrentWrite.accessLevel = ubyte(AccessLevel.CurrentWrite.value)
    nodeWithCurrentWrite.accessLevel = ubyte(AccessLevel.CurrentWrite.value)
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

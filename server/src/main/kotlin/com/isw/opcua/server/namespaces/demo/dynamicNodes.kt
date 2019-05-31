package com.isw.opcua.server.namespaces.demo

import com.isw.opcua.milo.extensions.inverseReferenceTo
import org.eclipse.milo.opcua.sdk.core.AccessLevel
import org.eclipse.milo.opcua.sdk.server.nodes.UaFolderNode
import org.eclipse.milo.opcua.sdk.server.nodes.filters.AttributeFilters
import org.eclipse.milo.opcua.stack.core.BuiltinDataType
import org.eclipse.milo.opcua.stack.core.Identifiers
import org.eclipse.milo.opcua.stack.core.types.builtin.*
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.Unsigned
import kotlin.random.Random


fun DemoNamespace.addDynamicNodes() {
    val dynamicFolder = UaFolderNode(
        nodeContext,
        NodeId(namespaceIndex, "Dynamic"),
        QualifiedName(namespaceIndex, "Dynamic"),
        LocalizedText("Dynamic")
    )

    nodeManager.addNode(dynamicFolder)

    dynamicFolder.inverseReferenceTo(
        Identifiers.ObjectsFolder,
        Identifiers.HasComponent
    )

    addRandomNodes(dynamicFolder.nodeId)
}

private fun DemoNamespace.addRandomNodes(parentNodeId: NodeId) {
    addVariableNode(parentNodeId, "RandomInt32", dataType = BuiltinDataType.Int32).apply {
        accessLevel = Unsigned.ubyte(AccessLevel.getMask(AccessLevel.READ_ONLY))
        userAccessLevel = Unsigned.ubyte(AccessLevel.getMask(AccessLevel.READ_ONLY))

        filterChain.addLast(AttributeFilters.getValue {
            DataValue(Variant(Random.nextInt()))
        })
    }

    addVariableNode(parentNodeId, "RandomInt64", dataType = BuiltinDataType.Int64).apply {
        accessLevel = Unsigned.ubyte(AccessLevel.getMask(AccessLevel.READ_ONLY))
        userAccessLevel = Unsigned.ubyte(AccessLevel.getMask(AccessLevel.READ_ONLY))

        filterChain.addLast(AttributeFilters.getValue {
            DataValue(Variant(Random.nextLong()))
        })
    }

    addVariableNode(parentNodeId, "RandomFloat", dataType = BuiltinDataType.Float).apply {
        accessLevel = Unsigned.ubyte(AccessLevel.getMask(AccessLevel.READ_ONLY))
        userAccessLevel = Unsigned.ubyte(AccessLevel.getMask(AccessLevel.READ_ONLY))

        filterChain.addLast(AttributeFilters.getValue {
            DataValue(Variant(Random.nextFloat()))
        })
    }

    addVariableNode(parentNodeId, "RandomDouble", dataType = BuiltinDataType.Double).apply {
        accessLevel = Unsigned.ubyte(AccessLevel.getMask(AccessLevel.READ_ONLY))
        userAccessLevel = Unsigned.ubyte(AccessLevel.getMask(AccessLevel.READ_ONLY))

        filterChain.addLast(AttributeFilters.getValue {
            DataValue(Variant(Random.nextDouble()))
        })
    }
}

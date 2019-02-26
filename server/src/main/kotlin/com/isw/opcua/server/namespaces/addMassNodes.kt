package com.isw.opcua.server.namespaces

import org.eclipse.milo.opcua.sdk.core.AccessLevel
import org.eclipse.milo.opcua.sdk.server.nodes.UaFolderNode
import org.eclipse.milo.opcua.sdk.server.nodes.UaVariableNode
import org.eclipse.milo.opcua.stack.core.Identifiers
import org.eclipse.milo.opcua.stack.core.types.builtin.*
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.Unsigned


fun CttNamespace.addMassNodes() {
    val massFolder = UaFolderNode(
        server,
        NodeId(namespaceIndex, "Mass"),
        QualifiedName(namespaceIndex, "Mass"),
        LocalizedText("Mass")
    )

    nodeManager.addNode(massFolder)

    massFolder.inverseReferenceTo(
        Identifiers.ObjectsFolder,
        Identifiers.HasComponent
    )

    addMassInt32Nodes(massFolder.nodeId)
}

private fun CttNamespace.addMassInt32Nodes(parentNodeId: NodeId) {
    for (i in 0 until 100) {
        val folderNode = addFolderNode(parentNodeId, "%03d".format(i))

        for (j in 0 until 100) {
            val name = "%03d".format(j)

            val node = UaVariableNode.UaVariableNodeBuilder(server).run {
                setNodeId(folderNode.nodeId.resolve(name))
                setAccessLevel(Unsigned.ubyte(AccessLevel.getMask(AccessLevel.READ_WRITE)))
                setUserAccessLevel(Unsigned.ubyte(AccessLevel.getMask(AccessLevel.READ_WRITE)))
                setBrowseName(QualifiedName(namespaceIndex, name))
                setDisplayName(LocalizedText.english(name))
                setDataType(Identifiers.Int32)
                setTypeDefinition(Identifiers.BaseDataVariableType)
                setMinimumSamplingInterval(100.0)

                build()
            }

            node.value = DataValue(Variant(0))

            nodeManager.addNode(node)
            node.inverseReferenceTo(folderNode.nodeId, Identifiers.Organizes)
        }
    }
}
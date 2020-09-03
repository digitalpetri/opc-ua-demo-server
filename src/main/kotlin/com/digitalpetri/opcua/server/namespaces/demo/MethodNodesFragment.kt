package com.digitalpetri.opcua.server.namespaces.demo

import com.digitalpetri.opcua.milo.extensions.inverseReferenceTo
import com.digitalpetri.opcua.server.methods.SqrtMethod
import org.eclipse.milo.opcua.sdk.server.OpcUaServer
import org.eclipse.milo.opcua.sdk.server.api.AddressSpaceComposite
import org.eclipse.milo.opcua.sdk.server.nodes.UaFolderNode
import org.eclipse.milo.opcua.sdk.server.nodes.UaMethodNode
import org.eclipse.milo.opcua.stack.core.Identifiers
import org.eclipse.milo.opcua.stack.core.types.builtin.LocalizedText
import org.eclipse.milo.opcua.stack.core.types.builtin.NodeId
import org.eclipse.milo.opcua.stack.core.types.builtin.QualifiedName
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.UShort

class MethodNodesFragment(
    server: OpcUaServer,
    composite: AddressSpaceComposite,
    private val namespaceIndex: UShort
) : SampledAddressSpaceFragment(server, composite) {

    init {
        lifecycleManager.addStartupTask { addDemoMethodNodes() }
    }

    private fun addDemoMethodNodes() {
        val methodFolder = UaFolderNode(
            nodeContext,
            NodeId(namespaceIndex, "Methods"),
            QualifiedName(namespaceIndex, "Methods"),
            LocalizedText("Methods")
        )

        nodeManager.addNode(methodFolder)
        methodFolder.inverseReferenceTo(
            Identifiers.ObjectsFolder,
            Identifiers.HasComponent
        )

        addSqrtMethod(methodFolder.nodeId)
    }

    private fun addSqrtMethod(parentNodeId: NodeId) {
        val methodNode = UaMethodNode.builder(nodeContext)
            .setNodeId(NodeId(namespaceIndex, "Methods/sqrt(x)"))
            .setBrowseName(QualifiedName(namespaceIndex, "sqrt(x)"))
            .setDisplayName(LocalizedText(null, "sqrt(x)"))
            .setDescription(
                LocalizedText.english("Returns the correctly rounded positive square root of a double value.")
            )
            .build()

        val sqrtMethod = SqrtMethod(methodNode)
        methodNode.inputArguments = sqrtMethod.inputArguments
        methodNode.outputArguments = sqrtMethod.outputArguments
        methodNode.invocationHandler = sqrtMethod

        nodeManager.addNode(methodNode)
        methodNode.inverseReferenceTo(parentNodeId, Identifiers.HasComponent)
    }

}

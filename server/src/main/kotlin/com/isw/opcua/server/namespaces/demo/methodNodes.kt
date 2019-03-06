package com.isw.opcua.server.namespaces.demo

import com.isw.opcua.milo.extensions.inverseReferenceTo
import com.isw.opcua.server.methods.SqrtMethod
import org.eclipse.milo.opcua.sdk.server.nodes.UaFolderNode
import org.eclipse.milo.opcua.sdk.server.nodes.UaMethodNode
import org.eclipse.milo.opcua.stack.core.Identifiers
import org.eclipse.milo.opcua.stack.core.types.builtin.LocalizedText
import org.eclipse.milo.opcua.stack.core.types.builtin.NodeId
import org.eclipse.milo.opcua.stack.core.types.builtin.QualifiedName


fun DemoNamespace.addMethodNodes() {
    val methodFolder = UaFolderNode(
        server,
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

private fun DemoNamespace.addSqrtMethod(parentNodeId: NodeId) {
    val methodNode = UaMethodNode.builder(server)
        .setNodeId(NodeId(namespaceIndex, "Methods/sqrt(x)"))
        .setBrowseName(QualifiedName(namespaceIndex, "sqrt(x)"))
        .setDisplayName(LocalizedText(null, "sqrt(x)"))
        .setDescription(
            LocalizedText.english("Returns the correctly rounded positive square root of a double value.")
        )
        .build()

    val sqrtMethod = SqrtMethod(methodNode)
    methodNode.setProperty(UaMethodNode.InputArguments, sqrtMethod.inputArguments)
    methodNode.setProperty(UaMethodNode.OutputArguments, sqrtMethod.outputArguments)
    methodNode.invocationHandler = sqrtMethod

    nodeManager.addNode(methodNode)
    methodNode.inverseReferenceTo(parentNodeId, Identifiers.HasComponent)
}

package com.isw.opcua.server.namespaces.demo

import com.isw.opcua.milo.extensions.inverseReferenceTo
import com.isw.opcua.server.DemoServer
import com.isw.opcua.server.objects.FileObject
import org.eclipse.milo.opcua.sdk.server.model.nodes.objects.FileTypeNode
import org.eclipse.milo.opcua.sdk.server.nodes.UaFolderNode
import org.eclipse.milo.opcua.stack.core.Identifiers
import org.eclipse.milo.opcua.stack.core.types.builtin.LocalizedText
import org.eclipse.milo.opcua.stack.core.types.builtin.NodeId
import org.eclipse.milo.opcua.stack.core.types.builtin.QualifiedName
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption


fun DemoNamespace.addFileNodes() {
    val fileFolder = UaFolderNode(
        nodeContext,
        NodeId(namespaceIndex, "Files"),
        QualifiedName(namespaceIndex, "Files"),
        LocalizedText("Files")
    )

    nodeManager.addNode(fileFolder)

    fileFolder.inverseReferenceTo(
        Identifiers.ObjectsFolder,
        Identifiers.HasComponent
    )

    addManifestoFile(fileFolder)
}

private fun DemoNamespace.addManifestoFile(fileFolder: UaFolderNode) {
    val fileNode = nodeFactory.createNode(
        NodeId(namespaceIndex, "manifesto.txt"),
        Identifiers.FileType,
        true
    )
    fileNode.browseName = QualifiedName(namespaceIndex, "manifesto.txt")
    fileNode.displayName = LocalizedText("manifesto.txt")

    val tempFile = File.createTempFile("manifesto", ".txt").apply { deleteOnExit() }

    Files.copy(
        DemoServer::class.java
            .classLoader
            .getResourceAsStream("manifesto.txt"),
        tempFile.toPath(),
        StandardCopyOption.REPLACE_EXISTING
    )

    val fileObject = (fileNode as? FileTypeNode)?.let {
        FileObject(it) { tempFile }
    }

    fileObject?.startup()

    nodeManager.addNode(fileNode)
    fileNode.inverseReferenceTo(fileFolder.nodeId, Identifiers.HasComponent)
}

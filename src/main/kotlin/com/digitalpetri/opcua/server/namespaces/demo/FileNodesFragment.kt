package com.digitalpetri.opcua.server.namespaces.demo

import com.digitalpetri.opcua.milo.extensions.inverseReferenceTo
import com.digitalpetri.opcua.server.DemoServer
import com.digitalpetri.opcua.server.objects.FileObject
import org.eclipse.milo.opcua.sdk.server.AddressSpaceComposite
import org.eclipse.milo.opcua.sdk.server.OpcUaServer
import org.eclipse.milo.opcua.sdk.server.model.objects.FileTypeNode
import org.eclipse.milo.opcua.sdk.server.nodes.UaFolderNode
import org.eclipse.milo.opcua.sdk.server.nodes.factories.NodeFactory
import org.eclipse.milo.opcua.stack.core.NodeIds
import org.eclipse.milo.opcua.stack.core.types.builtin.LocalizedText
import org.eclipse.milo.opcua.stack.core.types.builtin.NodeId
import org.eclipse.milo.opcua.stack.core.types.builtin.QualifiedName
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.UShort
import org.slf4j.LoggerFactory
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

class FileNodesFragment(
    server: OpcUaServer,
    composite: AddressSpaceComposite,
    private val namespaceIndex: UShort
) : SampledAddressSpaceFragment(server, composite) {

    init {
        lifecycleManager.addStartupTask { addFileNodes() }
    }

    private fun addFileNodes() {
        val fileFolder = UaFolderNode(
            nodeContext,
            NodeId(namespaceIndex, "Files"),
            QualifiedName(namespaceIndex, "Files"),
            LocalizedText("Files")
        )

        nodeManager.addNode(fileFolder)

        fileFolder.inverseReferenceTo(
            NodeIds.ObjectsFolder,
            NodeIds.HasComponent
        )

        addManifestoFile(fileFolder)
    }

    private fun addManifestoFile(fileFolder: UaFolderNode) {
        val fileNode = nodeFactory.createNode(
            NodeId(namespaceIndex, "manifesto.txt"),
            NodeIds.FileType,
            object : NodeFactory.InstantiationCallback {
                override fun includeOptionalNode(typeDefinitionId: NodeId, browseName: QualifiedName): Boolean {
                    return true
                }
            }
        )
        fileNode.browseName = QualifiedName(namespaceIndex, "manifesto.txt")
        fileNode.displayName = LocalizedText("manifesto.txt")

        val tempFile = File.createTempFile("manifesto", ".txt").apply { deleteOnExit() }

        Files.copy(
            DemoServer::class.java
                .classLoader
                .getResourceAsStream("manifesto.txt")!!,
            tempFile.toPath(),
            StandardCopyOption.REPLACE_EXISTING
        )

        LoggerFactory.getLogger(javaClass)
            .info("Using ${tempFile.path} for manifesto.txt")

        val fileObject = (fileNode as? FileTypeNode)?.let {
            FileObject(it) { tempFile }
        }

        fileObject?.startup()

        nodeManager.addNode(fileNode)
        fileNode.inverseReferenceTo(fileFolder.nodeId, NodeIds.HasComponent)
    }

}

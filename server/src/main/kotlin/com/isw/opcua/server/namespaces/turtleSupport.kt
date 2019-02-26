package com.isw.opcua.server.namespaces

import com.isw.opcua.server.DemoServer
import org.eclipse.milo.opcua.sdk.core.Reference
import org.eclipse.milo.opcua.sdk.server.nodes.UaFolderNode
import org.eclipse.milo.opcua.sdk.server.nodes.UaObjectNode
import org.eclipse.milo.opcua.sdk.server.nodes.UaObjectTypeNode
import org.eclipse.milo.opcua.stack.core.Identifiers
import org.eclipse.milo.opcua.stack.core.types.builtin.ByteString
import org.eclipse.milo.opcua.stack.core.types.builtin.LocalizedText
import org.eclipse.milo.opcua.stack.core.types.builtin.NodeId
import org.eclipse.milo.opcua.stack.core.types.builtin.QualifiedName
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.Unsigned.uint


internal fun DemoNamespace.addTurtlesFolder() {
    val turtlesFolder = UaFolderNode(
        server,
        NodeId(namespaceIndex, "[turtles]"),
        QualifiedName(namespaceIndex, "Turtles"),
        LocalizedText("Turtles")
    )

    turtlesFolder.description =
        LocalizedText("Turtles all the way down!")

    nodeManager.addNode(turtlesFolder)


    val turtleIconStream = DemoServer::class.java
        .classLoader
        .getResourceAsStream("turtle-icon.png")

    val turtleIconBytes = turtleIconStream.readAllBytes()

    turtlesFolder.icon = ByteString.of(turtleIconBytes)
    turtlesFolder.getPropertyNode(UaObjectNode.Icon).ifPresent {
        it.dataType = Identifiers.ImagePNG
    }

    turtlesFolder.referenceTo(
        NodeId(namespaceIndex, "[turtles]0"),
        Identifiers.Organizes
    )

    turtlesFolder.inverseReferenceTo(
        Identifiers.ObjectsFolder,
        Identifiers.HasComponent
    )

    addTurtleTypeNode()
}

private fun DemoNamespace.addTurtleTypeNode() {
    val turtleType = UaObjectTypeNode(
        server,
        NodeId(namespaceIndex, "TurtleType"),
        QualifiedName(namespaceIndex, "TurtleType"),
        LocalizedText("TurtleType"),
        LocalizedText("It's a Turtle."),
        uint(0),
        uint(0),
        false
    )

    nodeManager.addNode(turtleType)

    turtleType.inverseReferenceTo(
        Identifiers.BaseObjectType,
        Identifiers.HasSubtype
    )
}

internal fun DemoNamespace.maybeTurtleNode(nodeId: NodeId): UaObjectNode? {
    val turtleNumber: Long? = nodeId.turtleNumber()

    return turtleNumber?.let {
        UaObjectNode(
            server,
            NodeId(namespaceIndex, "[turtles]$it"),
            QualifiedName(namespaceIndex, "Turtle"),
            LocalizedText("Turtle")
        )
    }
}

internal fun DemoNamespace.maybeTurtleReferences(nodeId: NodeId): List<Reference>? {
    val turtleNumber = nodeId.turtleNumber()

    return turtleNumber?.let {
        val prevTurtle = turtleNumber - 1
        val nextTurtle = turtleNumber + 1

        val inverseReference = if (prevTurtle >= 0) {
            Reference(
                nodeId,
                Identifiers.Organizes,
                NodeId(namespaceIndex, "[turtles]$prevTurtle").expanded(),
                Reference.Direction.INVERSE
            )
        } else {
            null
        }

        listOfNotNull(
            inverseReference,
            Reference(
                nodeId,
                Identifiers.Organizes,
                NodeId(namespaceIndex, "[turtles]$nextTurtle").expanded(),
                Reference.Direction.FORWARD
            ),
            Reference(
                nodeId,
                Identifiers.HasTypeDefinition,
                NodeId(namespaceIndex, "TurtleType").expanded(),
                Reference.Direction.FORWARD
            )
        )
    }
}

internal fun NodeId.turtleNumber(): Long? {
    val id = this.identifier.toString()

    return if (id.startsWith("[turtles]")) {
        id.removePrefix("[turtles]").toLongOrNull()
    } else {
        null
    }
}

package com.digitalpetri.opcua.server.namespaces.demo

import com.digitalpetri.opcua.milo.extensions.inverseReferenceTo
import com.digitalpetri.opcua.milo.extensions.referenceTo
import com.digitalpetri.opcua.server.DemoServer
import org.eclipse.milo.opcua.sdk.core.Reference
import org.eclipse.milo.opcua.sdk.core.nodes.ObjectNodeProperties
import org.eclipse.milo.opcua.sdk.server.OpcUaServer
import org.eclipse.milo.opcua.sdk.server.api.AddressSpaceComposite
import org.eclipse.milo.opcua.sdk.server.api.AddressSpaceFilter
import org.eclipse.milo.opcua.sdk.server.api.SimpleAddressSpaceFilter
import org.eclipse.milo.opcua.sdk.server.api.services.AttributeServices
import org.eclipse.milo.opcua.sdk.server.api.services.ViewServices
import org.eclipse.milo.opcua.sdk.server.nodes.*
import org.eclipse.milo.opcua.stack.core.Identifiers
import org.eclipse.milo.opcua.stack.core.StatusCodes
import org.eclipse.milo.opcua.stack.core.types.builtin.*
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.UShort
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.Unsigned
import org.eclipse.milo.opcua.stack.core.types.enumerated.TimestampsToReturn
import org.eclipse.milo.opcua.stack.core.types.structured.ReadValueId
import org.eclipse.milo.opcua.stack.core.types.structured.ViewDescription

class TurtleNodesFragment(
    server: OpcUaServer,
    composite: AddressSpaceComposite,
    private val namespaceIndex: UShort
) : SampledAddressSpaceFragment(server, composite) {

    companion object {

        private const val MAX_TURTLES: Long = 1000L

    }

    private val filter = SimpleAddressSpaceFilter.create {
        nodeManager.containsNode(it) ||
            it.turtleNumber()?.let { n -> n in 0 until MAX_TURTLES } == true
    }

    init {
        lifecycleManager.addStartupTask { addTurtleNodes() }
    }

    override fun getFilter(): AddressSpaceFilter {
        return filter
    }

    override fun browse(context: ViewServices.BrowseContext, viewDescription: ViewDescription, nodeId: NodeId) {
        val node: UaNode? = nodeManager[nodeId]

        val references: List<Reference>? = node?.references ?: maybeTurtleReferences(nodeId)

        if (references != null) {
            context.success(references)
        } else {
            context.failure(StatusCodes.Bad_NodeIdUnknown)
        }
    }

    override fun getReferences(
        context: ViewServices.BrowseContext,
        viewDescription: ViewDescription,
        sourceNodeId: NodeId
    ) {

        val references: List<Reference> =
            nodeManager.getReferences(sourceNodeId) +
                (maybeTurtleReferences(sourceNodeId) ?: emptyList())

        context.success(references)
    }

    override fun read(
        context: AttributeServices.ReadContext,
        maxAge: Double,
        timestamps: TimestampsToReturn,
        readValueIds: List<ReadValueId>
    ) {

        val values = readValueIds.map { readValueId ->
            val node: UaNode? = nodeManager[readValueId.nodeId] ?: maybeTurtleNode(readValueId.nodeId)

            val value: DataValue? = node?.readAttribute(
                AttributeContext(context),
                readValueId.attributeId,
                timestamps,
                readValueId.indexRange,
                readValueId.dataEncoding
            )

            value ?: DataValue(StatusCodes.Bad_NodeIdUnknown)
        }

        context.success(values)
    }

    private fun addTurtleNodes() {
        val turtlesFolder = UaFolderNode(
            nodeContext,
            NodeId(namespaceIndex, "[turtles]"),
            QualifiedName(namespaceIndex, "Turtles"),
            LocalizedText("Turtles")
        )

        turtlesFolder.description =
            LocalizedText("Turtles all the way down!")

        nodeManager.addNode(turtlesFolder)


        val turtleIconStream = DemoServer::class.java
            .classLoader
            .getResourceAsStream("turtle-icon.png")!!

        val turtleIconBytes = turtleIconStream.readAllBytes()

        turtlesFolder.icon = ByteString.of(turtleIconBytes)
        turtlesFolder.getPropertyNode(ObjectNodeProperties.Icon).ifPresent {
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

    private fun addTurtleTypeNode() {
        val turtleType = UaObjectTypeNode(
            nodeContext,
            NodeId(namespaceIndex, "TurtleType"),
            QualifiedName(namespaceIndex, "TurtleType"),
            LocalizedText("TurtleType"),
            LocalizedText("It's a Turtle."),
            Unsigned.uint(0),
            Unsigned.uint(0),
            false
        )

        nodeManager.addNode(turtleType)

        turtleType.inverseReferenceTo(
            Identifiers.BaseObjectType,
            Identifiers.HasSubtype
        )
    }

    private fun maybeTurtleNode(nodeId: NodeId): UaObjectNode? {
        val turtleNumber: Long? = nodeId.turtleNumber()

        return turtleNumber?.let {
            UaObjectNode(
                nodeContext,
                NodeId(namespaceIndex, "[turtles]$it"),
                QualifiedName(namespaceIndex, "Turtle$it"),
                LocalizedText("Turtle")
            )
        }
    }

    private fun maybeTurtleReferences(nodeId: NodeId): List<Reference>? {
        val turtleNumber = nodeId.turtleNumber()

        return turtleNumber?.let {
            val prevTurtle = turtleNumber - 1
            val nextTurtle = turtleNumber + 1

            val references = mutableListOf<Reference>()

            if (prevTurtle >= 0) {
                references += Reference(
                    nodeId,
                    Identifiers.Organizes,
                    NodeId(namespaceIndex, "[turtles]$prevTurtle").expanded(),
                    Reference.Direction.INVERSE
                )
            }

            if (nextTurtle < MAX_TURTLES) {
                references += listOf(
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

            references
        }
    }

    private fun NodeId.turtleNumber(): Long? {
        val id = this.identifier.toString()

        return if (id.startsWith("[turtles]")) {
            id.removePrefix("[turtles]").toLongOrNull()
        } else {
            null
        }
    }

}

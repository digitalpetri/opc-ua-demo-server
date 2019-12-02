package com.isw.opcua.server.namespaces.demo

import com.google.common.collect.Lists
import com.google.common.collect.Maps
import com.isw.opcua.milo.extensions.defaultValue
import com.isw.opcua.milo.extensions.inverseReferenceTo
import com.isw.opcua.milo.extensions.resolve
import com.isw.opcua.server.sampling.SampledDataItem
import com.isw.opcua.server.sampling.TickManager
import kotlinx.coroutines.CoroutineScope
import org.eclipse.milo.opcua.sdk.core.AccessLevel
import org.eclipse.milo.opcua.sdk.core.Reference
import org.eclipse.milo.opcua.sdk.server.AbstractLifecycle
import org.eclipse.milo.opcua.sdk.server.OpcUaServer
import org.eclipse.milo.opcua.sdk.server.UaNodeManager
import org.eclipse.milo.opcua.sdk.server.api.*
import org.eclipse.milo.opcua.sdk.server.api.methods.MethodInvocationHandler
import org.eclipse.milo.opcua.sdk.server.api.services.AttributeServices.ReadContext
import org.eclipse.milo.opcua.sdk.server.api.services.AttributeServices.WriteContext
import org.eclipse.milo.opcua.sdk.server.api.services.MethodServices
import org.eclipse.milo.opcua.sdk.server.api.services.MethodServices.CallContext
import org.eclipse.milo.opcua.sdk.server.api.services.ViewServices.BrowseContext
import org.eclipse.milo.opcua.sdk.server.model.nodes.objects.ServerTypeNode
import org.eclipse.milo.opcua.sdk.server.nodes.*
import org.eclipse.milo.opcua.sdk.server.nodes.factories.NodeFactory
import org.eclipse.milo.opcua.stack.core.*
import org.eclipse.milo.opcua.stack.core.types.builtin.*
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.UInteger
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.UShort
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.Unsigned.ubyte
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.Unsigned.ushort
import org.eclipse.milo.opcua.stack.core.types.enumerated.TimestampsToReturn
import org.eclipse.milo.opcua.stack.core.types.structured.*
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.*
import java.util.concurrent.ConcurrentMap
import java.util.concurrent.TimeUnit
import java.util.function.BiConsumer

class DemoNamespace(
    internal val server: OpcUaServer,
    private val coroutineScope: CoroutineScope
) : AbstractLifecycle(), Namespace {

    companion object {
        const val NAMESPACE_URI: String = "urn:industrialsoftworks:opcua:server:demo"
    }

    private val logger: Logger = LoggerFactory.getLogger(DemoNamespace::class.java)

    private val tickManager = TickManager(coroutineScope)

    internal val nodeManager = UaNodeManager()

    internal val nodeContext = object : UaNodeContext {
        override fun getServer() =
            this@DemoNamespace.server

        override fun getNodeManager() =
            this@DemoNamespace.nodeManager
    }

    internal val nodeFactory = NodeFactory(nodeContext)

    private val sampledNodes: ConcurrentMap<DataItem, SampledNode> = Maps.newConcurrentMap()
    private val subscribedNodes: ConcurrentMap<DataItem, SubscribedNode> = Maps.newConcurrentMap()

    private val namespaceIndex: UShort = server.namespaceTable.addUri(NAMESPACE_URI)

    private val filter = SimpleAddressSpaceFilter.create {
        it.namespaceIndex == namespaceIndex
    }

    override fun onStartup() {
        server.addressSpaceManager.register(this)
        server.addressSpaceManager.register(nodeManager)

        addCttNodes()
        addMassNodes()
        addTurtleNodes()
        addFileNodes()
        addMethodNodes()
        addDynamicNodes()
        addNullValueNodes()

        // Set the EventNotifier bit on Server Node for Events.
        val serverNode = server.addressSpaceManager.getManagedNode(Identifiers.Server).orElse(null)

        if (serverNode is ServerTypeNode) {
            serverNode.eventNotifier = ubyte(1)

            // Post a bogus Event every couple seconds
            server.scheduledExecutorService.scheduleAtFixedRate({
                try {
                    val eventNode = server.eventFactory.createEvent(
                        NodeId(1, UUID.randomUUID()),
                        Identifiers.BaseEventType
                    )

                    eventNode.browseName = QualifiedName(1, "foo")
                    eventNode.displayName = LocalizedText.english("foo")

                    eventNode.eventId = ByteString.of(byteArrayOf(0, 1, 2, 3))
                    eventNode.eventType = Identifiers.BaseEventType
                    eventNode.sourceNode = serverNode.getNodeId()
                    eventNode.sourceName = serverNode.getDisplayName().text
                    eventNode.time = DateTime.now()
                    eventNode.receiveTime = DateTime.NULL_VALUE
                    eventNode.message = LocalizedText.english("event message!")
                    eventNode.severity = ushort(2)

                    server.eventBus.post(eventNode)

                    eventNode.delete()
                } catch (e: Throwable) {
                    logger.error("Error creating EventNode: {}", e.message, e)
                }
            }, 0, 2, TimeUnit.SECONDS)
        }
    }

    override fun onShutdown() {
        sampledNodes.values.forEach { it.shutdown() }

        server.addressSpaceManager.unregister(this)
        server.addressSpaceManager.unregister(nodeManager)
    }

    override fun getFilter(): AddressSpaceFilter {
        return filter
    }

    override fun getNamespaceUri(): String = NAMESPACE_URI

    override fun getNamespaceIndex(): UShort = this@DemoNamespace.namespaceIndex

    override fun browse(context: BrowseContext, viewDescription: ViewDescription, nodeId: NodeId) {
        val node: UaNode? = nodeManager[nodeId]

        val references: List<Reference>? = node?.references ?: maybeTurtleReferences(nodeId)

        if (references != null) {
            context.success(references)
        } else {
            context.failure(StatusCodes.Bad_NodeIdUnknown)
        }
    }

    override fun getReferences(
        context: BrowseContext,
        viewDescription: ViewDescription,
        sourceNodeId: NodeId
    ) {

        val references: List<Reference> =
            nodeManager.getReferences(sourceNodeId) +
                (maybeTurtleReferences(sourceNodeId) ?: emptyList())

        context.success(references)
    }

    override fun read(
        context: ReadContext,
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

    override fun write(context: WriteContext, writeValues: List<WriteValue>) {
        val results: List<StatusCode> = writeValues.map { writeValue ->
            val node: UaNode? = nodeManager[writeValue.nodeId]

            val status: StatusCode? = node?.run {
                try {
                    writeAttribute(
                        AttributeContext(context),
                        writeValue.attributeId,
                        writeValue.value,
                        writeValue.indexRange
                    )
                    StatusCode.GOOD
                } catch (e: UaException) {
                    e.statusCode
                }
            }

            status ?: StatusCode(StatusCodes.Bad_NodeIdUnknown)
        }

        context.success(results)
    }

    override fun onCreateDataItem(
        itemToMonitor: ReadValueId,
        requestedSamplingInterval: Double,
        requestedQueueSize: UInteger,
        revisionCallback: BiConsumer<Double, UInteger>
    ) {

        if (itemToMonitor.nodeId.isMassNode()) {
            revisionCallback.accept(0.0, requestedQueueSize)
        } else {
            super.onCreateDataItem(itemToMonitor, requestedSamplingInterval, requestedQueueSize, revisionCallback)
        }
    }

    override fun onModifyDataItem(
        itemToModify: ReadValueId,
        requestedSamplingInterval: Double,
        requestedQueueSize: UInteger,
        revisionCallback: BiConsumer<Double, UInteger>
    ) {

        if (itemToModify.nodeId.isMassNode()) {
            revisionCallback.accept(0.0, requestedQueueSize)
        } else {
            super.onModifyDataItem(itemToModify, requestedSamplingInterval, requestedQueueSize, revisionCallback)
        }
    }

    override fun onDataItemsCreated(items: List<DataItem>) {
        items.forEach { item ->
            val nodeId: NodeId = item.readValueId.nodeId
            val node: UaNode? = nodeManager.get(nodeId)

            if (node != null) {
                if (nodeId.isMassNode()) {
                    val subscribedNode = SubscribedNode(item, node)
                    subscribedNode.samplingEnabled = item.isSamplingEnabled
                    subscribedNode.startup()

                    subscribedNodes[item] = subscribedNode
                } else {
                    val sampledNode = SampledNode(item, coroutineScope, node)
                    sampledNode.samplingEnabled = item.isSamplingEnabled
                    sampledNode.startup()

                    sampledNodes[item] = sampledNode
                }
            }
        }
    }

    private fun NodeId.isMassNode(): Boolean {
        val id = identifier as? UInteger
        return id is UInteger && id.toInt() < 26000
    }

    override fun onDataItemsModified(items: List<DataItem>) {
        items.forEach { item ->
            sampledNodes[item]?.modifyRate(item.samplingInterval)
        }
    }

    override fun onDataItemsDeleted(items: List<DataItem>) {
        items.forEach { item ->
            sampledNodes.remove(item)?.shutdown()
            subscribedNodes.remove(item)?.shutdown()
        }
    }

    override fun onMonitoringModeChanged(items: List<MonitoredItem>) {
        items.forEach {
            sampledNodes[it]?.samplingEnabled = it.isSamplingEnabled
            subscribedNodes[it]?.samplingEnabled = it.isSamplingEnabled
        }
    }

    /**
     * Invoke one or more methods belonging to this [MethodServices].
     *
     * @param context  the [CallContext].
     * @param requests The [CallMethodRequest]s for the methods to invoke.
     */
    override fun call(context: CallContext, requests: List<CallMethodRequest>) {
        val results = Lists.newArrayListWithCapacity<CallMethodResult>(requests.size)

        for (request in requests) {
            val handler = getInvocationHandler(
                request.objectId,
                request.methodId
            ).orElse(MethodInvocationHandler.NODE_ID_UNKNOWN)

            try {
                results.add(handler.invoke(context, request))
            } catch (t: Throwable) {
                LoggerFactory.getLogger(javaClass)
                    .error("Uncaught Throwable invoking method handler for methodId={}.", request.methodId, t)

                results.add(
                    CallMethodResult(
                        StatusCode(StatusCodes.Bad_InternalError),
                        arrayOfNulls(0), arrayOfNulls(0), arrayOfNulls(0)
                    )
                )
            }

        }

        context.success(results)
    }

    /**
     * Get the [MethodInvocationHandler] for the method identified by `methodId`, if it exists.
     *
     * @param objectId the [NodeId] identifying the object the method will be invoked on.
     * @param methodId the [NodeId] identifying the method.
     * @return the [MethodInvocationHandler] for `methodId`, if it exists.
     */
    private fun getInvocationHandler(objectId: NodeId, methodId: NodeId): Optional<MethodInvocationHandler> {
        return nodeManager.getNode(objectId).flatMap { node ->
            var methodNode: UaMethodNode? = null

            if (node is UaObjectNode) {
                methodNode = node.findMethodNode(methodId)
            } else if (node is UaObjectTypeNode) {
                methodNode = node.findMethodNode(methodId)
            }

            if (methodNode != null) {
                Optional.of(methodNode.invocationHandler)
            } else {
                Optional.empty()
            }
        }
    }

    inner class SampledNode(
        item: DataItem,
        scope: CoroutineScope,
        private val node: UaNode
    ) : SampledDataItem(item, scope, tickManager) {

        override suspend fun sampleCurrentValue(currentTime: Long): DataValue {
            return node.readAttribute(
                AttributeContext(server),
                item.readValueId.attributeId,
                TimestampsToReturn.Both,
                item.readValueId.indexRange,
                item.readValueId.dataEncoding
            )
        }

    }

    inner class SubscribedNode(
        private val item: DataItem,
        private val node: UaNode
    ) : AbstractLifecycle() {

        @Volatile
        var samplingEnabled: Boolean = true

        private val targetAttributeId = AttributeId.from(item.readValueId.attributeId).orElseThrow()

        private val attributeObserver = AttributeObserver { _, attributeId, value ->
            if (samplingEnabled && attributeId == targetAttributeId) {
                item.setValue(value as DataValue)
            }
        }

        override fun onStartup() {
            item.setValue(node.getAttribute(AttributeContext(server), targetAttributeId))

            node.addAttributeObserver(attributeObserver)
        }

        override fun onShutdown() {
            node.removeAttributeObserver(attributeObserver)
        }

    }

}

fun Optional<NodeManager<UaNode>>.addNode(node: UaNode) {
    this.ifPresent { it.addNode(node) }
}

fun DemoNamespace.addFolderNode(parentNodeId: NodeId, name: String): UaFolderNode {
    val folderNode = UaFolderNode(
        nodeContext,
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

fun DemoNamespace.addVariableNode(
    parentNodeId: NodeId,
    name: String,
    nodeId: NodeId = parentNodeId.resolve(name),
    dataType: BuiltinDataType = BuiltinDataType.Int32
): UaVariableNode {

    return addVariableNode(
        parentNodeId,
        name,
        nodeId,
        dataType.nodeId,
        dataType.defaultValue()
    )
}

fun DemoNamespace.addVariableNode(
    parentNodeId: NodeId,
    name: String,
    nodeId: NodeId = parentNodeId.resolve(name),
    dataTypeId: NodeId,
    value: Any
): UaVariableNode {

    val variableNode = UaVariableNode.UaVariableNodeBuilder(nodeContext).run {
        setNodeId(nodeId)
        setAccessLevel(ubyte(AccessLevel.getMask(AccessLevel.READ_WRITE)))
        setUserAccessLevel(ubyte(AccessLevel.getMask(AccessLevel.READ_WRITE)))
        setBrowseName(QualifiedName(namespaceIndex, name))
        setDisplayName(LocalizedText.english(name))
        setDataType(dataTypeId)
        setTypeDefinition(Identifiers.BaseDataVariableType)
        setMinimumSamplingInterval(100.0)
        setValue(DataValue(Variant(value)))

        build()
    }

    nodeManager.addNode(variableNode)

    variableNode.inverseReferenceTo(
        parentNodeId,
        Identifiers.HasComponent
    )

    return variableNode
}

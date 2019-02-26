package com.isw.opcua.server.namespaces

import com.google.common.collect.Maps
import com.isw.opcua.server.sampling.SampledDataItem
import com.isw.opcua.server.sampling.TickManager
import com.isw.opcua.server.util.AbstractLifecycle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.apache.logging.log4j.core.AbstractLifeCycle
import org.eclipse.milo.opcua.sdk.core.Reference
import org.eclipse.milo.opcua.sdk.server.NamespaceNodeManager
import org.eclipse.milo.opcua.sdk.server.OpcUaServer
import org.eclipse.milo.opcua.sdk.server.api.*
import org.eclipse.milo.opcua.sdk.server.api.AttributeServices.ReadContext
import org.eclipse.milo.opcua.sdk.server.api.AttributeServices.WriteContext
import org.eclipse.milo.opcua.sdk.server.nodes.AttributeContext
import org.eclipse.milo.opcua.sdk.server.nodes.UaNode
import org.eclipse.milo.opcua.stack.core.StatusCodes
import org.eclipse.milo.opcua.stack.core.UaException
import org.eclipse.milo.opcua.stack.core.types.builtin.DataValue
import org.eclipse.milo.opcua.stack.core.types.builtin.NodeId
import org.eclipse.milo.opcua.stack.core.types.builtin.StatusCode
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.UShort
import org.eclipse.milo.opcua.stack.core.types.enumerated.TimestampsToReturn
import org.eclipse.milo.opcua.stack.core.types.structured.ReadValueId
import org.eclipse.milo.opcua.stack.core.types.structured.WriteValue
import org.eclipse.milo.opcua.stack.core.util.FutureUtils.failedUaFuture
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletableFuture.completedFuture
import java.util.concurrent.ConcurrentMap

class CttNamespace(
    private val namespaceIndex: UShort,
    private val coroutineScope: CoroutineScope,
    internal val server: OpcUaServer
) : AbstractLifecycle(), Namespace {

    companion object {
        const val NAMESPACE_URI = "urn:industrialsoftworks:opcua:server:ctt"
    }

    private val tickManager = TickManager(coroutineScope)

    private val nodeManager = NamespaceNodeManager(server)

    private val sampledNodes: ConcurrentMap<DataItem, SampledDataItem> = Maps.newConcurrentMap()

    override fun onStartup() {
        addCttNodes()
        addMassNodes()
    }

    override fun onShutdown() {
        sampledNodes.values.forEach { it.shutdown() }
    }

    override fun getNamespaceUri(): String = NAMESPACE_URI

    override fun getNamespaceIndex(): UShort = this@CttNamespace.namespaceIndex

    override fun getNodeManager(): NodeManager<UaNode> = nodeManager

    override fun browse(context: AccessContext, nodeId: NodeId): CompletableFuture<List<Reference>> {
        val node: UaNode? = nodeManager[nodeId]

        return if (node != null) {
            completedFuture<List<Reference>>(node.references)
        } else {
            failedUaFuture(StatusCodes.Bad_NodeIdUnknown)
        }
    }

    override fun read(
        context: ReadContext,
        maxAge: Double,
        timestamps: TimestampsToReturn,
        readValueIds: List<ReadValueId>
    ) {

        val values = readValueIds.map { readValueId ->
            val node: UaNode? = nodeManager[readValueId.nodeId]

            val value: DataValue? = node?.readAttribute(
                AttributeContext(context),
                readValueId.attributeId,
                timestamps,
                readValueId.indexRange,
                readValueId.dataEncoding
            )

            value ?: DataValue(StatusCodes.Bad_NodeIdUnknown)
        }

        context.complete(values)
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

        context.complete(results)
    }

    override fun onDataItemsCreated(items: List<DataItem>) {
        items.forEach { item ->
            val nodeId: NodeId = item.readValueId.nodeId
            val node: UaNode? = nodeManager.get(nodeId)

            if (node != null) {
                val sampledNode = SampledNode(item, coroutineScope, node)
                sampledNodes[item] = sampledNode
                sampledNode.startup()
            }
        }
    }

    override fun onDataItemsModified(items: List<DataItem>) {
        items.forEach { item ->
            sampledNodes[item]?.modifyRate(item.samplingInterval)
        }
    }

    override fun onDataItemsDeleted(items: List<DataItem>) {
        items.forEach { item ->
            sampledNodes.remove(item)?.shutdown()
        }
    }

    override fun onMonitoringModeChanged(items: List<MonitoredItem>) {
        items.forEach { sampledNodes[it]?.samplingEnabled = it.isSamplingEnabled }
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

}

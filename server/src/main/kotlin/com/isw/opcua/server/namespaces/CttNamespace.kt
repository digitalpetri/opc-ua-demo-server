package com.isw.opcua.server.namespaces

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

class CttNamespace(
    private val namespaceIndex: UShort,
    internal val server: OpcUaServer
) : Namespace {

    companion object {
        const val NAMESPACE_URI = "urn:industrialsoftworks:opcua:server:ctt"
    }

    private val nodeManager = NamespaceNodeManager(server)

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

    override fun onDataItemsCreated(dataItems: List<DataItem>) {
        TODO("not implemented")
    }

    override fun onDataItemsModified(dataItems: List<DataItem>) {
        TODO("not implemented")
    }

    override fun onDataItemsDeleted(dataItems: List<DataItem>) {
        TODO("not implemented")
    }

    override fun onMonitoringModeChanged(monitoredItems: List<MonitoredItem>) {
        TODO("not implemented")
    }

}

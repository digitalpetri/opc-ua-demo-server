package com.digitalpetri.opcua.server.namespaces.demo

import com.google.common.collect.Maps
import kotlinx.coroutines.*
import org.eclipse.milo.opcua.sdk.server.OpcUaServer
import org.eclipse.milo.opcua.sdk.server.api.*
import org.eclipse.milo.opcua.sdk.server.nodes.UaNode
import org.eclipse.milo.opcua.stack.core.types.builtin.NodeId
import java.util.concurrent.ConcurrentMap


/**
 * A [ManagedAddressSpace] that implements Monitored Item services for managed nodes with a [SampledNode].
 *
 * This [ManagedAddressSpace] is a "fragment" of a [composite].
 */
abstract class SampledAddressSpaceFragment(
    server: OpcUaServer,
    composite: AddressSpaceComposite
) : ManagedAddressSpaceFragmentWithLifecycle(server, composite) {

    private val supervisor = SupervisorJob()
    private val coroutineScope = CoroutineScope(supervisor + Dispatchers.Default)

    private val filter = SimpleAddressSpaceFilter.create {
        nodeManager.containsNode(it)
    }

    private val sampledNodes: ConcurrentMap<DataItem, SampledNode> = Maps.newConcurrentMap()

    init {
        lifecycleManager.addShutdownTask {
            runBlocking { supervisor.cancelAndJoin() }
        }
    }

    override fun getFilter(): AddressSpaceFilter {
        return filter
    }

    override fun onDataItemsCreated(items: List<DataItem>) {
        items.forEach { item ->
            val nodeId: NodeId = item.readValueId.nodeId
            val node: UaNode? = nodeManager.get(nodeId)

            if (node != null) {
                val sampledNode = SampledNode(
                    item,
                    coroutineScope,
                    node
                )
                sampledNode.samplingEnabled = item.isSamplingEnabled
                sampledNode.startup()

                sampledNodes[item] = sampledNode
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
        items.forEach {
            sampledNodes[it]?.samplingEnabled = it.isSamplingEnabled
        }
    }

}

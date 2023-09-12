package com.digitalpetri.opcua.server.namespaces.demo

import org.eclipse.milo.opcua.sdk.server.AbstractLifecycle
import org.eclipse.milo.opcua.sdk.server.items.DataItem
import org.eclipse.milo.opcua.sdk.server.nodes.AttributeObserver
import org.eclipse.milo.opcua.sdk.server.nodes.UaNode
import org.eclipse.milo.opcua.stack.core.AttributeId
import org.eclipse.milo.opcua.stack.core.types.builtin.DataValue
import java.util.*

class SubscribedNode(
    private val item: DataItem,
    private val node: UaNode
) : AbstractLifecycle() {

    @Volatile
    var samplingEnabled: Boolean = true

    private val targetAttributeId = AttributeId.from(item.readValueId.attributeId)
        .orElseThrow()

    private val attributeObserver = AttributeObserver { _, attributeId, value ->
        if (samplingEnabled && attributeId == targetAttributeId) {
            item.setValue(value as DataValue)
        }
    }

    override fun onStartup() {
        item.setValue(node.getAttribute({ Optional.ofNullable(item.session) }, targetAttributeId))

        node.addAttributeObserver(attributeObserver)
    }

    override fun onShutdown() {
        node.removeAttributeObserver(attributeObserver)
    }

}

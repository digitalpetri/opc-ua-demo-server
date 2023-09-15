package com.digitalpetri.opcua.server.namespaces.demo

import org.eclipse.milo.opcua.sdk.server.AbstractLifecycle
import org.eclipse.milo.opcua.sdk.server.AccessContext
import org.eclipse.milo.opcua.sdk.server.items.DataItem
import org.eclipse.milo.opcua.sdk.server.nodes.AttributeObserver
import org.eclipse.milo.opcua.sdk.server.nodes.UaNode
import org.eclipse.milo.opcua.stack.core.AttributeId
import org.eclipse.milo.opcua.stack.core.types.builtin.DataValue
import org.eclipse.milo.opcua.stack.core.types.builtin.Variant

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
            if (value is DataValue) {
                item.setValue(value)
            } else {
                item.setValue(DataValue(Variant((value))))
            }
        }
    }

    override fun onStartup() {
        val value: Any? = node.getAttribute(AccessContext.INTERNAL, targetAttributeId)

        if (value is DataValue) {
            item.setValue(value)
        } else {
            item.setValue(DataValue(Variant((value))))
        }

        node.addAttributeObserver(attributeObserver)
    }

    override fun onShutdown() {
        node.removeAttributeObserver(attributeObserver)
    }

}

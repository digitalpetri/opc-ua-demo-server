package com.digitalpetri.opcua.server.namespaces.demo

import com.digitalpetri.opcua.server.sampling.SampledDataItem
import kotlinx.coroutines.CoroutineScope
import org.eclipse.milo.opcua.sdk.server.api.DataItem
import org.eclipse.milo.opcua.sdk.server.nodes.AttributeContext
import org.eclipse.milo.opcua.sdk.server.nodes.UaNode
import org.eclipse.milo.opcua.stack.core.types.builtin.DataValue
import org.eclipse.milo.opcua.stack.core.types.enumerated.TimestampsToReturn

class SampledNode(
    item: DataItem,
    scope: CoroutineScope,
    private val node: UaNode
) : SampledDataItem(item, scope) {

    override suspend fun sampleCurrentValue(currentTime: Long): DataValue {
        return node.readAttribute(
            AttributeContext(item.session.server),
            item.readValueId.attributeId,
            TimestampsToReturn.Both,
            item.readValueId.indexRange,
            item.readValueId.dataEncoding
        )
    }

}

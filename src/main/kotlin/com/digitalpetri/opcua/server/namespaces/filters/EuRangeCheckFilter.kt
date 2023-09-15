package com.digitalpetri.opcua.server.namespaces.filters

import org.eclipse.milo.opcua.sdk.server.model.variables.AnalogItemType
import org.eclipse.milo.opcua.sdk.server.nodes.UaNode
import org.eclipse.milo.opcua.sdk.server.nodes.filters.AttributeFilter
import org.eclipse.milo.opcua.sdk.server.nodes.filters.AttributeFilterContext
import org.eclipse.milo.opcua.stack.core.AttributeId
import org.eclipse.milo.opcua.stack.core.StatusCodes
import org.eclipse.milo.opcua.stack.core.UaException
import org.eclipse.milo.opcua.stack.core.types.builtin.DataValue

/**
 * [AttributeFilter] that checks to see if a Node is an [AnalogItemType] and, if it is, throws an
 * exception with [StatusCodes.Bad_OutOfRange] when a value is written outside the configured EU
 * [org.eclipse.milo.opcua.stack.core.types.structured.Range], or [StatusCodes.Bad_TypeMismatch]
 * if the value is not a number.
 */
object EuRangeCheckFilter : AttributeFilter {

    override fun writeAttribute(
        ctx: AttributeFilterContext,
        attributeId: AttributeId,
        value: Any?
    ) {

        val node: UaNode = ctx.node

        if (attributeId == AttributeId.Value && node is AnalogItemType) {
            val v: Any? = (value as? DataValue)?.value?.value

            if (v is Number) {
                val low: Double = node.euRange.low
                val high: Double = node.euRange.high

                if (v.toDouble() !in low..high) {
                    throw UaException(StatusCodes.Bad_OutOfRange)
                }
            } else {
                throw UaException(StatusCodes.Bad_TypeMismatch)
            }
        }

        ctx.writeAttribute(attributeId, value)
    }

}

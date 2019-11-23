package com.isw.opcua.server.namespaces.filters

import org.eclipse.milo.opcua.sdk.server.model.types.variables.AnalogItemType
import org.eclipse.milo.opcua.sdk.server.nodes.filters.AttributeFilter
import org.eclipse.milo.opcua.sdk.server.nodes.filters.AttributeFilterContext
import org.eclipse.milo.opcua.stack.core.AttributeId
import org.eclipse.milo.opcua.stack.core.StatusCodes
import org.eclipse.milo.opcua.stack.core.UaException
import org.eclipse.milo.opcua.stack.core.types.builtin.DataValue

/**
 * [AttributeFilter] that checks to see if a Node is an [AnalogItemType] and, if it is, throws an exception with
 * [StatusCodes.Bad_OutOfRange] when a value is written outside the configured EU [Range], or
 * [StatusCodes.Bad_TypeMismatch] if the value is not a number.
 */
object EuRangeCheckFilter : AttributeFilter {

    override fun setAttribute(ctx: AttributeFilterContext.SetAttributeContext, attributeId: AttributeId, value: Any) {
        val node = ctx.node

        if (attributeId == AttributeId.Value && node is AnalogItemType) {
            val v = (value as? DataValue)?.value?.value

            if (v is Number) {
                val low = node.euRange.low
                val high = node.euRange.high

                if (v.toDouble() !in low..high) {
                    throw UaException(StatusCodes.Bad_OutOfRange)
                }
            } else {
                throw UaException(StatusCodes.Bad_TypeMismatch)
            }
        }

        ctx.setAttribute(attributeId, value)
    }

}
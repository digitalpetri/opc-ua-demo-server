package com.digitalpetri.opcua.server.namespaces.filters

import org.eclipse.milo.opcua.sdk.server.nodes.filters.AttributeFilter
import org.eclipse.milo.opcua.sdk.server.nodes.filters.AttributeFilterContext
import org.eclipse.milo.opcua.stack.core.AttributeId
import org.slf4j.LoggerFactory

class AttributeLoggingFilter @JvmOverloads constructor(
    private val predicate: (AttributeId) -> Boolean = { true }
) : AttributeFilter {

    private val logger = LoggerFactory.getLogger(javaClass)

    override fun getAttribute(
        ctx: AttributeFilterContext.GetAttributeContext,
        attributeId: AttributeId
    ): Any? {

        val value = ctx.getAttribute(attributeId)

        // only log external reads
        if (predicate(attributeId) && ctx.session.isPresent) {
            logger.debug(
                "get nodeId={} attributeId={} value={}",
                ctx.node.nodeId, attributeId, value
            )
        }

        return value
    }

    override fun setAttribute(
        ctx: AttributeFilterContext.SetAttributeContext,
        attributeId: AttributeId,
        value: Any?
    ) {

        // only log external writes
        if (predicate(attributeId) && ctx.session.isPresent) {
            logger.debug(
                "set nodeId={} attributeId={} value={}",
                ctx.node.nodeId, attributeId, value
            )
        }

        ctx.setAttribute(attributeId, value)
    }

}

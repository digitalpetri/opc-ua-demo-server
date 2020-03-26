package com.digitalpetri.opcua.server.namespaces.filters

import org.eclipse.milo.opcua.sdk.server.nodes.filters.AttributeFilter
import org.eclipse.milo.opcua.sdk.server.nodes.filters.AttributeFilterContext.GetAttributeContext
import org.eclipse.milo.opcua.stack.core.AttributeId

object ExecutableByAdmin : AttributeFilter {

    override fun getAttribute(ctx: GetAttributeContext, attributeId: AttributeId): Any? {
        return when (attributeId) {
            AttributeId.Executable -> true
            AttributeId.UserExecutable -> {
                val user = ctx.session.orElse(null)?.identityObject

                return user == "admin"
            }
            else -> ctx.getAttribute(attributeId)
        }
    }

}

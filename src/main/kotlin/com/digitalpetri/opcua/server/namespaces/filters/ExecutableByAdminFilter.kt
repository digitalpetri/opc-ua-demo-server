package com.digitalpetri.opcua.server.namespaces.filters

import org.eclipse.milo.opcua.sdk.server.identity.Identity.UsernameIdentity
import org.eclipse.milo.opcua.sdk.server.nodes.filters.AttributeFilter
import org.eclipse.milo.opcua.sdk.server.nodes.filters.AttributeFilterContext.GetAttributeContext
import org.eclipse.milo.opcua.stack.core.AttributeId
import kotlin.jvm.optionals.getOrNull

object ExecutableByAdminFilter : AttributeFilter {

    override fun getAttribute(ctx: GetAttributeContext, attributeId: AttributeId): Any? {
        return when (attributeId) {
            AttributeId.Executable -> true
            AttributeId.UserExecutable -> {
                val identity = ctx.session
                    .getOrNull()
                    ?.identity as? UsernameIdentity

                return identity?.username == "admin"
            }

            else -> ctx.getAttribute(attributeId)
        }
    }

}

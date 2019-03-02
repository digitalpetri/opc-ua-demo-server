package com.isw.opcua.server.util

import org.eclipse.milo.opcua.sdk.server.api.nodes.MethodNode
import org.eclipse.milo.opcua.sdk.server.nodes.AttributeContext
import org.eclipse.milo.opcua.sdk.server.nodes.delegates.AttributeDelegate

object ExecutableByAdmin : AttributeDelegate {

    override fun isExecutable(context: AttributeContext, node: MethodNode): Boolean {
        return true
    }

    override fun isUserExecutable(context: AttributeContext, node: MethodNode): Boolean {
        val user = context.session.orElse(null)?.identityObject

        return user == "admin"
    }

}

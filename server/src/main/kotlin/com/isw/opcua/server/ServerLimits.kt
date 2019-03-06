package com.isw.opcua.server

import org.eclipse.milo.opcua.sdk.server.api.config.OpcUaServerConfigLimits
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.UInteger
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.Unsigned.uint


object ServerLimits : OpcUaServerConfigLimits {

    override fun getMaxNodesPerBrowse(): UInteger {
        return uint(128)
    }

    override fun getMaxNodesPerTranslateBrowsePathsToNodeIds(): UInteger {
        return uint(128)
    }
    
}

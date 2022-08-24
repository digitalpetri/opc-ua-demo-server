package com.digitalpetri.opcua.server

import org.eclipse.milo.opcua.sdk.server.api.config.OpcUaServerConfigLimits
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.UInteger
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.Unsigned.uint


object ServerLimits : OpcUaServerConfigLimits {

    override fun getMaxSessionCount(): UInteger {
        return uint(100)
    }

    override fun getMaxSessionTimeout(): Double {
        return 30_000.0;
    }

    override fun getMinPublishingInterval(): Double {
        return 100.0
    }

    override fun getMaxPublishingInterval(): Double {
        return 15_0000.0;
    }

    override fun getMaxSubscriptionLifetime(): Double {
        return 30_000.0;
    }

    override fun getDefaultPublishingInterval(): Double {
        return 100.0
    }

    override fun getMaxMonitoredItems(): UInteger {
        return uint(50_000)
    }

    override fun getMaxMonitoredItemsPerSession(): UInteger {
        return uint(5000)
    }

    override fun getMaxSubscriptions(): UInteger {
        return uint(5_000)
    }

    override fun getMaxSubscriptionsPerSession(): UInteger {
        return uint(50)
    }
    
}

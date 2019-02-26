package com.isw.opcua.server

import com.uchuhimo.konf.ConfigSpec


object ServerConfig : ConfigSpec() {
    val bindAddressList by optional(listOf("0.0.0.0"))
    val bindPort by optional(62541)
    val endpointAddressList by optional(listOf("<hostname>", "<localhost>"))
    val securityPolicyList by optional(listOf("Basic256Sha256"))
}

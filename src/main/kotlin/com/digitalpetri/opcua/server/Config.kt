package com.digitalpetri.opcua.server

data class Config(
    val bindAddressList: List<String>,
    val bindPort: Int,
    val endpointAddressList: List<String>,
    val securityPolicyList: List<String>,
    val certificateHostnameList: List<String>,
    val gdsPushEnabled: Boolean,
    val registration: Registration
)

data class Registration(
    val enabled: Boolean,
    val frequency: Long,
    val endpointUrl: String
)

package com.digitalpetri.opcua.server

import com.digitalpetri.opcua.server.namespaces.demo.*
import com.digitalpetri.opcua.server.objects.ServerConfigurationObject
import com.digitalpetri.opcua.server.util.cartesianProduct
import com.sksamuel.hoplite.ConfigLoaderBuilder
import com.sksamuel.hoplite.ExperimentalHoplite
import com.sksamuel.hoplite.addResourceOrFileSource
import com.sksamuel.hoplite.addResourceSource
import kotlinx.coroutines.*
import kotlinx.coroutines.future.await
import org.eclipse.milo.opcua.sdk.client.DiscoveryClient
import org.eclipse.milo.opcua.sdk.server.AbstractLifecycle
import org.eclipse.milo.opcua.sdk.server.EndpointConfig
import org.eclipse.milo.opcua.sdk.server.OpcUaServer
import org.eclipse.milo.opcua.sdk.server.OpcUaServerConfig
import org.eclipse.milo.opcua.sdk.server.identity.UsernameIdentityValidator
import org.eclipse.milo.opcua.sdk.server.model.objects.ServerConfigurationTypeNode
import org.eclipse.milo.opcua.sdk.server.util.HostnameUtil
import org.eclipse.milo.opcua.stack.core.NodeIds
import org.eclipse.milo.opcua.stack.core.security.DefaultCertificateManager
import org.eclipse.milo.opcua.stack.core.security.KeyStoreCertificateStore
import org.eclipse.milo.opcua.stack.core.security.RsaSha256CertificateFactory
import org.eclipse.milo.opcua.stack.core.security.SecurityPolicy
import org.eclipse.milo.opcua.stack.core.transport.TransportProfile
import org.eclipse.milo.opcua.stack.core.types.builtin.DateTime
import org.eclipse.milo.opcua.stack.core.types.builtin.LocalizedText
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.Unsigned.ubyte
import org.eclipse.milo.opcua.stack.core.types.enumerated.ApplicationType
import org.eclipse.milo.opcua.stack.core.types.enumerated.MessageSecurityMode
import org.eclipse.milo.opcua.stack.core.types.structured.BuildInfo
import org.eclipse.milo.opcua.stack.core.types.structured.EndpointDescription
import org.eclipse.milo.opcua.stack.core.types.structured.RegisteredServer
import org.eclipse.milo.opcua.stack.core.types.structured.UserTokenPolicy
import org.eclipse.milo.opcua.stack.core.util.ManifestUtil
import org.eclipse.milo.opcua.stack.core.util.SelfSignedCertificateBuilder
import org.eclipse.milo.opcua.stack.transport.client.tcp.OpcTcpClientTransport
import org.eclipse.milo.opcua.stack.transport.client.tcp.OpcTcpClientTransportConfigBuilder
import org.eclipse.milo.opcua.stack.transport.server.OpcServerTransport
import org.eclipse.milo.opcua.stack.transport.server.OpcServerTransportFactory
import org.eclipse.milo.opcua.stack.transport.server.tcp.OpcTcpServerTransport
import org.eclipse.milo.opcua.stack.transport.server.tcp.OpcTcpServerTransportConfigBuilder
import org.slf4j.LoggerFactory
import java.io.File
import java.nio.file.Files
import java.security.KeyPair
import java.security.cert.X509Certificate
import java.text.SimpleDateFormat
import java.util.*
import java.util.regex.Pattern

class DemoServer(configDir: File) : AbstractLifecycle() {

    companion object {
        const val APPLICATION_URI = "urn:eclipse:milo:opcua:server"

        private const val PRODUCT_URI = "https://github.com/digitalpetri/opc-ua-demo-server"

        private const val PROPERTY_BUILD_DATE = "X-Server-Build-Date"
        private const val PROPERTY_BUILD_NUMBER = "X-Server-Build-Number"
        private const val PROPERTY_SOFTWARE_VERSION = "X-Server-Software-Version"
    }

    private val logger = LoggerFactory.getLogger(javaClass)

    private val supervisor = SupervisorJob()
    private val coroutineScope = CoroutineScope(supervisor + Dispatchers.Default)

    private val identityValidator = UsernameIdentityValidator(true) { authChallenge ->
        val username = authChallenge.username
        val password = authChallenge.password

        val adminValid = "admin" == username && "password" == password
        val user1Valid = "user1" == username && "password" == password
        val user2Valid = "user2" == username && "password" == password

        adminValid || user1Valid || user2Valid
    }

    private val config: Config
    private val server: OpcUaServer
    private val demoNamespace: DemoNamespace
    private val serverConfigurationObject: ServerConfigurationObject

    init {
        config = readConfig(configDir)

        logger.info("config: $config")

        val applicationUuid = configDir.resolve(".uuid").let { file ->
            if (file.exists()) {
                try {
                    UUID.fromString(file.readText())
                } catch (e: IllegalArgumentException) {
                    UUID.randomUUID()
                        .also { file.writeText(it.toString()) }
                }
            } else {
                UUID.randomUUID()
                    .also { file.writeText(it.toString()) }
            }
        }

        val securityDir = configDir.resolve("security")

        val pkiDir = securityDir.toPath()
            .resolve("pki")
            .toFile().also { it.mkdirs() }

        val certificateStore = KeyStoreCertificateStore.createAndInitialize(
            KeyStoreCertificateStore.Settings(
                securityDir.toPath().resolve("certificates.pfx"),
                { "password".toCharArray() },
                { _ -> "password".toCharArray() }
            )
        )
        val certificateManager = DefaultCertificateManager.createWithDefaultApplicationGroup(
            pkiDir.toPath(),
            certificateStore,
            object : RsaSha256CertificateFactory() {
                private val IP_ADDR_PATTERN = Pattern.compile(
                    "^(([01]?\\d\\d?|2[0-4]\\d|25[0-5])\\.){3}([01]?\\d\\d?|2[0-4]\\d|25[0-5])$"
                )

                override fun createRsaSha256CertificateChain(keyPair: KeyPair): Array<X509Certificate> {
                    val applicationUri = "urn:eclipse:milo:opcua:server:$applicationUuid"

                    val builder = SelfSignedCertificateBuilder(keyPair)
                        .setCommonName("Eclipse Milo OPC UA Demo Server")
                        .setOrganization("digitalpetri")
                        .setOrganizationalUnit("dev")
                        .setLocalityName("Folsom")
                        .setStateName("CA")
                        .setCountryCode("US")
                        .setApplicationUri(applicationUri)

                    for (hostname in certificateHostnames()) {
                        if (IP_ADDR_PATTERN.matcher(hostname).matches()) {
                            builder.addIpAddress(hostname)
                        } else {
                            builder.addDnsName(hostname)
                        }
                    }

                    return arrayOf(builder.build())
                }
            }
        )

        val endpoints = createEndpointConfigurations(
            config,
            certificateManager
        )

        val serverConfig = OpcUaServerConfig.builder()
            .setProductUri(PRODUCT_URI)
            .setApplicationUri("$APPLICATION_URI:$applicationUuid")
            .setApplicationName(LocalizedText.english("Eclipse Milo OPC UA Demo Server"))
            .setBuildInfo(buildInfo())
            .setCertificateManager(certificateManager)
            .setIdentityValidator(identityValidator)
            .setEndpoints(endpoints)
            .setLimits(ServerLimits)
            .build()


        val transportFactory = object : OpcServerTransportFactory {
            override fun create(profile: TransportProfile): OpcServerTransport {
                if (profile == TransportProfile.TCP_UASC_UABINARY) {
                    val transportConfig = OpcTcpServerTransportConfigBuilder().build()

                    return OpcTcpServerTransport(transportConfig)
                } else {
                    throw RuntimeException("unsupported transport: $profile")
                }
            }
        }

        server = OpcUaServer(serverConfig, transportFactory)

        demoNamespace = DemoNamespace(server)
        demoNamespace.startup()

        val complexTypesFragment = ComplexTypesFragment(
            server,
            demoNamespace,
            demoNamespace.namespaceIndex
        )
        complexTypesFragment.startup()

        val cttNodes = CttNodesFragment(
            server,
            demoNamespace,
            demoNamespace.namespaceIndex
        )
        cttNodes.startup()

        val dynamicNodes = DynamicNodesFragment(
            server,
            demoNamespace,
            demoNamespace.namespaceIndex
        )
        dynamicNodes.startup()

        val fileNodes = FileNodesFragment(
            server,
            demoNamespace,
            demoNamespace.namespaceIndex
        )
        fileNodes.startup()

        val massNodes = MassNodesFragment(
            server,
            demoNamespace,
            demoNamespace.namespaceIndex
        )
        massNodes.startup()

        val methodNodes = MethodNodesFragment(
            server,
            demoNamespace,
            demoNamespace.namespaceIndex
        )
        methodNodes.startup()

        val nullValueNodes = NullValueNodesFragment(
            server,
            demoNamespace,
            demoNamespace.namespaceIndex
        )
        nullValueNodes.startup()

        val turtleNodes = TurtleNodesFragment(
            server,
            demoNamespace,
            demoNamespace.namespaceIndex
        )
        turtleNodes.startup()

        // GDS Push Support via ServerConfiguration
        val serverConfigurationNode = server.addressSpaceManager
            .getManagedNode(NodeIds.ServerConfiguration)
            .map { it as ServerConfigurationTypeNode }
            .orElseThrow()

        serverConfigurationObject = ServerConfigurationObject(
            server,
            serverConfigurationNode
        )
    }

    override fun onStartup() {
        if (config.gdsPushEnabled) {
            serverConfigurationObject.startup()
        }

        server.startup().get()

        if (config.registration.enabled) {
            coroutineScope.launch {
                while (true) {
                    registerWithLds()

                    delay(config.registration.frequency)
                }
            }
        }
    }

    override fun onShutdown() {
        if (config.gdsPushEnabled) {
            serverConfigurationObject.shutdown()
        }

        demoNamespace.shutdown()
        runBlocking { supervisor.cancelAndJoin() }
        server.shutdown().get()
    }

    @OptIn(ExperimentalHoplite::class)
    private fun readConfig(configDir: File): Config {
        val configFile = configDir.resolve("config.json")

        configFile.apply {
            if (!exists()) {
                Files.copy(
                    DemoServer::class.java
                        .classLoader
                        .getResourceAsStream("default-config.json")!!,
                    this.toPath()
                )
            }
            assert(exists())
        }

        return ConfigLoaderBuilder.default()
            .addResourceOrFileSource(configFile.absolutePath)
            .addResourceSource("/default-config.json")
            .withExplicitSealedTypes()
            .build()
            .loadConfigOrThrow<Config>()
    }

    private suspend fun registerWithLds() {
        try {
            val endpointUrl: String = config.registration.endpointUrl

            val endpointDescription = EndpointDescription(
                endpointUrl,
                null,
                null,
                MessageSecurityMode.None,
                SecurityPolicy.None.uri,
                null,
                TransportProfile.TCP_UASC_UABINARY.uri,
                ubyte(0)
            )

            val transport = OpcTcpClientTransport(OpcTcpClientTransportConfigBuilder().build())

            val discoveryClient = DiscoveryClient(endpointDescription, transport)
            discoveryClient.connectAsync().await()

            val discoveryUrls: List<String> = server.applicationContext
                .endpointDescriptions
                .flatMap { it.server.discoveryUrls.toList() }

            val registeredServer = RegisteredServer(
                server.config.applicationUri,
                server.config.productUri,
                arrayOf(server.config.applicationName),
                ApplicationType.Server,
                null,
                discoveryUrls.toTypedArray(),
                null,
                true
            )

            discoveryClient.registerServer(registeredServer).await()
        } catch (e: Exception) {
            logger.error("Error registering with LDS: ${e.message}")
        }
    }

    private fun buildInfo(): BuildInfo {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'").apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }

        val manufacturerName = "digitalpetri"
        val productName = "Eclipse Milo OPC UA Demo Server"

        val softwareVersion = ManifestUtil.read(PROPERTY_SOFTWARE_VERSION).orElse("dev")
        val buildNumber = ManifestUtil.read(PROPERTY_BUILD_NUMBER).orElse("dev")
        val buildDate = ManifestUtil.read(PROPERTY_BUILD_DATE).map { date ->
            try {
                DateTime(dateFormat.parse(date))
            } catch (t: Throwable) {
                DateTime.NULL_VALUE
            }
        }.orElse(DateTime.NULL_VALUE)

        return BuildInfo(
            PRODUCT_URI,
            manufacturerName,
            productName,
            softwareVersion,
            buildNumber,
            buildDate
        )
    }

    private fun certificateHostnames(): List<String> {
        return config.certificateHostnameList.flatMap {
            it.parseHostnames(includeLoopback = false)
        }
    }

    private fun createEndpointConfigurations(
        config: Config,
        certificateManager: DefaultCertificateManager
    ): Set<EndpointConfig> {

        val endpointConfigurations = LinkedHashSet<EndpointConfig>()

        val userTokenPolicies = mutableListOf<UserTokenPolicy>().apply {
            add(OpcUaServerConfig.USER_TOKEN_POLICY_ANONYMOUS)
            add(OpcUaServerConfig.USER_TOKEN_POLICY_USERNAME)
        }

        val bindAddresses: List<String> = config.bindAddressList

        val endpointAddresses: List<String> = config.endpointAddressList.flatMap {
            it.parseHostnames(includeLoopback = true)
        }

        val certificates: List<() -> X509Certificate> = listOf {
            certificateManager.defaultApplicationGroup.get()
                .getCertificateChain(NodeIds.RsaSha256ApplicationCertificateType).get()[0]
        }

        cartesianProduct(bindAddresses, endpointAddresses, certificates).forEach {
            val bindAddress: String = it.first
            val endpointAddress: String = it.second
            val getCertificate: () -> X509Certificate = it.third

            val builder = EndpointConfig.newBuilder()
                .setTransportProfile(TransportProfile.TCP_UASC_UABINARY)
                .setBindAddress(bindAddress)
                .setHostname(endpointAddress)
                .setBindPort(config.bindPort)
                .setPath("milo")
                .setCertificate { getCertificate() }
                .addTokenPolicies(*userTokenPolicies.toTypedArray())

            val securityPolicies = config.securityPolicyList.mapNotNull {
                runCatching { SecurityPolicy.valueOf(it) }.getOrNull()
            }

            if (securityPolicies.isEmpty()) {
                throw RuntimeException("no security policies configured")
            }

            for (securityPolicy in securityPolicies) {
                if (securityPolicy == SecurityPolicy.None) {
                    endpointConfigurations.add(
                        builder.copy()
                            .setSecurityPolicy(SecurityPolicy.None)
                            .setSecurityMode(MessageSecurityMode.None)
                            .build()
                    )
                } else {
                    endpointConfigurations.add(
                        builder.copy()
                            .setSecurityPolicy(securityPolicy)
                            .setSecurityMode(MessageSecurityMode.Sign)
                            .build()
                    )
                    endpointConfigurations.add(
                        builder.copy()
                            .setSecurityPolicy(securityPolicy)
                            .setSecurityMode(MessageSecurityMode.SignAndEncrypt)
                            .build()
                    )
                }
            }

            val discoveryBuilder = builder.copy()
                .setPath("/milo/discovery")
                .setSecurityPolicy(SecurityPolicy.None)
                .setSecurityMode(MessageSecurityMode.None)

            endpointConfigurations.add(discoveryBuilder.build())
        }

        return endpointConfigurations
    }

    private fun String.parseHostnames(includeLoopback: Boolean): Set<String> {
        return if ("<.+>".toRegex().matches(this)) {
            val hostname = this.removeSurrounding("<", ">")

            if (hostname == "hostname") {
                HostnameUtil.getHostnames(HostnameUtil.getHostname())
            } else {
                HostnameUtil.getHostnames(hostname, includeLoopback)
            }
        } else {
            setOf(this)
        }
    }

}


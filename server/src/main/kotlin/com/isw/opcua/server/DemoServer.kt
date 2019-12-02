package com.isw.opcua.server

import com.isw.opcua.server.namespaces.demo.DemoNamespace
import com.isw.opcua.server.objects.ServerConfigurationObject
import com.isw.opcua.server.util.KeyStoreManager
import com.uchuhimo.konf.Config
import com.uchuhimo.konf.source.json.toJson
import kotlinx.coroutines.*
import kotlinx.coroutines.future.await
import org.eclipse.milo.opcua.sdk.server.AbstractLifecycle
import org.eclipse.milo.opcua.sdk.server.OpcUaServer
import org.eclipse.milo.opcua.sdk.server.api.config.OpcUaServerConfig
import org.eclipse.milo.opcua.sdk.server.identity.UsernameIdentityValidator
import org.eclipse.milo.opcua.sdk.server.model.nodes.objects.ServerConfigurationTypeNode
import org.eclipse.milo.opcua.sdk.server.util.HostnameUtil
import org.eclipse.milo.opcua.stack.client.UaStackClient
import org.eclipse.milo.opcua.stack.client.UaStackClientConfig
import org.eclipse.milo.opcua.stack.core.Identifiers
import org.eclipse.milo.opcua.stack.core.security.DefaultCertificateManager
import org.eclipse.milo.opcua.stack.core.security.DefaultTrustListManager
import org.eclipse.milo.opcua.stack.core.security.SecurityPolicy
import org.eclipse.milo.opcua.stack.core.transport.TransportProfile
import org.eclipse.milo.opcua.stack.core.types.builtin.DateTime
import org.eclipse.milo.opcua.stack.core.types.builtin.LocalizedText
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.Unsigned.ubyte
import org.eclipse.milo.opcua.stack.core.types.enumerated.ApplicationType
import org.eclipse.milo.opcua.stack.core.types.enumerated.MessageSecurityMode
import org.eclipse.milo.opcua.stack.core.types.structured.*
import org.eclipse.milo.opcua.stack.core.util.CertificateValidationUtil.ValidationCheck
import org.eclipse.milo.opcua.stack.core.util.ManifestUtil
import org.eclipse.milo.opcua.stack.server.EndpointConfiguration
import org.eclipse.milo.opcua.stack.server.security.DefaultServerCertificateValidator
import org.slf4j.LoggerFactory
import java.io.File
import java.nio.file.Files
import java.util.*

class DemoServer(dataDir: File) : AbstractLifecycle() {

    companion object {
        const val APPLICATION_URI = "urn:eclipse:milo:opcua:server"

        private const val PRODUCT_URI = "urn:eclipse:milo:opcua:server"

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

    private val server: OpcUaServer
    private val serverConfigurationObject: ServerConfigurationObject

    private val config: Config
    private val demoNamespace: DemoNamespace

    init {
        config = readConfig(dataDir)

        logger.info("config: \n${config.toJson.toText()}")

        val applicationUuid = dataDir.resolve(".uuid").let { file ->
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

        val securityDir = dataDir
            .resolve("security")
            .also { logger.info("security dir: $it") }

        val pkiDir = securityDir.toPath()
            .resolve("pki")
            .toFile().also { it.mkdirs() }

        val keyStore = ServerKeyStore(
            KeyStoreManager.Settings(
                keyStoreFile = securityDir.toPath()
                    .resolve("certificates.pfx").toFile(),
                keyStorePassword = "password"
            ),
            applicationUuid
        ) { certificateHostnames() }

        val trustListManager = DefaultTrustListManager(pkiDir)

        val certificateManager = DefaultCertificateManager(
            keyStore.getDefaultKeyPair(),
            keyStore.getDefaultCertificateChain()?.toTypedArray()
        )

        val certificateValidator = DefaultServerCertificateValidator(
            trustListManager,
            ValidationCheck.ALL_OPTIONAL_CHECKS
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
            .setTrustListManager(trustListManager)
            .setCertificateManager(certificateManager)
            .setCertificateValidator(certificateValidator)
            .setIdentityValidator(identityValidator)
            .setEndpoints(endpoints)
            .setLimits(ServerLimits)
            .build()

        server = OpcUaServer(serverConfig)

        demoNamespace = DemoNamespace(server, coroutineScope)
        demoNamespace.startup()

        // GDS Push Support via ServerConfiguration
        val serverConfigurationNode = server.addressSpaceManager
            .getManagedNode(Identifiers.ServerConfiguration)
            .orElse(null) as ServerConfigurationTypeNode

        serverConfigurationObject = ServerConfigurationObject(
            server,
            serverConfigurationNode,
            keyStore,
            trustListManager
        )
    }

    override fun onStartup() {
        if (config[ServerConfig.gdsPushEnabled]) {
            serverConfigurationObject.startup()
        }

        server.startup().get()

        if (config[ServerConfig.Registration.enabled]) {
            val frequency = config[ServerConfig.Registration.frequency]

            coroutineScope.launch {
                while (true) {
                    registerWithLds()

                    delay(frequency)
                }
            }
        }
    }

    override fun onShutdown() {
        if (config[ServerConfig.gdsPushEnabled]) {
            serverConfigurationObject.shutdown()
        }

        demoNamespace.shutdown()
        runBlocking { supervisor.cancelAndJoin() }
        server.shutdown().get()
    }

    private fun readConfig(dataDir: File): Config {
        return with(Config()) {
            addSpec(ServerConfig)

            val configFile = dataDir
                .resolve("config")
                .resolve("server.json")

            configFile.apply {
                if (!exists()) {
                    Files.copy(
                        DemoServer::class.java
                            .classLoader
                            .getResourceAsStream("default-server.json")!!,
                        this.toPath()
                    )
                }
                assert(exists())
            }

            from.json.file(configFile)
        }
    }

    private suspend fun registerWithLds() {
        try {
            val endpointUrl = config[ServerConfig.Registration.endpointUrl]

            val stackClient = UaStackClient.create(
                UaStackClientConfig.builder()
                    .setEndpoint(
                        EndpointDescription(
                            endpointUrl,
                            null,
                            null,
                            MessageSecurityMode.None,
                            SecurityPolicy.None.uri,
                            null,
                            TransportProfile.TCP_UASC_UABINARY.uri,
                            ubyte(0)
                        )
                    )
                    .build()
            )

            stackClient.connect().await()

            val discoveryUrls = server.stackServer
                .endpointDescriptions
                .map { endpoint -> endpoint.endpointUrl }
                .filter { url -> url.endsWith("/discovery") }

            stackClient.sendRequest(
                RegisterServerRequest(
                    stackClient.newRequestHeader(),
                    RegisteredServer(
                        server.config.applicationUri,
                        server.config.productUri,
                        arrayOf(server.config.applicationName),
                        ApplicationType.Server,
                        null,
                        discoveryUrls.toTypedArray(),
                        null,
                        true
                    )
                )
            ).await()
        } catch (e: Exception) {
            logger.error("Error registering with LDS: ${e.message}")
        }
    }

    private fun buildInfo(): BuildInfo {
        val manufacturerName = "Industrial Softworks"
        val productName = "Eclipse Milo OPC UA Demo Server"

        val softwareVersion = ManifestUtil.read(PROPERTY_SOFTWARE_VERSION).orElse("dev")
        val buildNumber = ManifestUtil.read(PROPERTY_BUILD_NUMBER).orElse("dev")
        val buildDate = ManifestUtil.read(PROPERTY_BUILD_DATE).map { ts ->
            DateTime(Calendar.getInstance().also { it.timeInMillis = ts.toLong() }.time)
        }.orElse(DateTime())

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
        return config[ServerConfig.certificateHostnameList].flatMap {
            it.parseHostnames(includeLoopback = false)
        }
    }

    private fun createEndpointConfigurations(
        config: Config,
        certificateManager: DefaultCertificateManager
    ): Set<EndpointConfiguration> {

        val endpointConfigurations = LinkedHashSet<EndpointConfiguration>()

        val userTokenPolicies = mutableListOf<UserTokenPolicy>().apply {
            add(OpcUaServerConfig.USER_TOKEN_POLICY_ANONYMOUS)
            add(OpcUaServerConfig.USER_TOKEN_POLICY_USERNAME)
        }

        val bindAddresses: List<String> = config[ServerConfig.bindAddressList]

        val endpointAddresses: List<String> = config[ServerConfig.endpointAddressList].flatMap {
            it.parseHostnames(includeLoopback = true)
        }

        for (bindAddress in bindAddresses) {
            for (hostname in endpointAddresses) {
                val builder = EndpointConfiguration.newBuilder()
                    .setTransportProfile(TransportProfile.TCP_UASC_UABINARY)
                    .setBindAddress(bindAddress)
                    .setBindPort(config[ServerConfig.bindPort])
                    .setHostname(hostname)
                    .setPath("milo")
                    .setCertificate {
                        certificateManager.certificates.first()
                    }
                    .addTokenPolicies(*userTokenPolicies.toTypedArray())

                val securityPolicies = config[ServerConfig.securityPolicyList].mapNotNull {
                    try {
                        SecurityPolicy.valueOf(it)
                    } catch (t: Throwable) {
                        null
                    }
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

                /*
                 * It's good practice to provide a discovery-specific endpoint with no security.
                 * It's required practice if all regular endpoints have security configured.
                 *
                 * Usage of the  "/discovery" suffix is defined by OPC UA Part 6:
                 *
                 * Each OPC UA Server Application implements the Discovery Service Set.
                 *
                 * If the OPC UA Server requires a different address for this Endpoint it shall
                 * create the address by appending the path "/discovery" to its base address.
                 */

                val discoveryBuilder = builder.copy()
                    .setPath("/milo/discovery")
                    .setSecurityPolicy(SecurityPolicy.None)
                    .setSecurityMode(MessageSecurityMode.None)

                endpointConfigurations.add(discoveryBuilder.build())
            }
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


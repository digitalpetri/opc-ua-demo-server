package com.isw.opcua.server

import com.isw.opcua.server.namespaces.CttNamespace
import com.isw.opcua.server.util.KeyStoreManager
import com.uchuhimo.konf.Config
import com.uchuhimo.konf.source.json.toJson
import kotlinx.coroutines.*
import org.eclipse.milo.opcua.sdk.server.OpcUaServer
import org.eclipse.milo.opcua.sdk.server.api.config.OpcUaServerConfig
import org.eclipse.milo.opcua.sdk.server.util.HostnameUtil
import org.eclipse.milo.opcua.stack.core.application.CertificateManager
import org.eclipse.milo.opcua.stack.core.application.DefaultCertificateManager
import org.eclipse.milo.opcua.stack.core.application.DirectoryCertificateValidator
import org.eclipse.milo.opcua.stack.core.security.SecurityPolicy
import org.eclipse.milo.opcua.stack.core.transport.TransportProfile
import org.eclipse.milo.opcua.stack.core.types.builtin.DateTime
import org.eclipse.milo.opcua.stack.core.types.builtin.LocalizedText
import org.eclipse.milo.opcua.stack.core.types.enumerated.MessageSecurityMode
import org.eclipse.milo.opcua.stack.core.types.structured.BuildInfo
import org.eclipse.milo.opcua.stack.core.types.structured.UserTokenPolicy
import org.eclipse.milo.opcua.stack.core.util.ManifestUtil
import org.eclipse.milo.opcua.stack.server.EndpointConfiguration
import org.slf4j.LoggerFactory
import java.io.File
import java.nio.file.Files
import java.security.cert.X509Certificate
import java.util.*

class DemoServer(dataDir: File) {

    companion object {
        private const val PRODUCT_URI = "urn:eclipse:milo:opcua:server"
        private const val APPLICATION_URI = "urn:eclipse:milo:opcua:server"

        private const val PROPERTY_BUILD_DATE = "X-Server-Build-Date"
        private const val PROPERTY_BUILD_NUMBER = "X-Server-Build-Number"
        private const val PROPERTY_SOFTWARE_VERSION = "X-Server-Software-Version"
    }

    private val logger = LoggerFactory.getLogger(javaClass)

    private val supervisor = SupervisorJob()
    private val coroutineScope = CoroutineScope(supervisor + Dispatchers.Default)

    private val config: Config
    private val server: OpcUaServer
    private val cttNamespace: CttNamespace

    init {
        config = with(Config()) {
            addSpec(ServerConfig)

            val configFile = dataDir
                .resolve("config")
                .resolve("server.json")

            configFile.apply {
                if (!exists()) {
                    Files.copy(
                        DemoServer::class.java
                            .classLoader
                            .getResourceAsStream("default-server.json"),
                        this.toPath()
                    )
                }
                assert(exists())
            }

            from.json.file(configFile)
        }

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
        )

        val certificateManager: CertificateManager = DefaultCertificateManager(
            keyStore.getDefaultKeyPair(),
            keyStore.getDefaultCertificateChain()?.toTypedArray()
        )

        val certificateValidator = DirectoryCertificateValidator(pkiDir)

        val serverConfig = OpcUaServerConfig.builder()
            .setProductUri(PRODUCT_URI)
            .setApplicationUri("$APPLICATION_URI:$applicationUuid")
            .setApplicationName(LocalizedText.english("Eclipse Milo OPC UA Demo Server"))
            .setBuildInfo(buildInfo())
            .setCertificateManager(certificateManager)
            .setCertificateValidator(certificateValidator)
            .setEndpoints(createEndpointConfigurations(config, certificateManager.certificates.first()))
            .build()

        server = OpcUaServer(serverConfig)

        cttNamespace = server.namespaceManager.registerAndAdd(CttNamespace.NAMESPACE_URI) { idx ->
            CttNamespace(idx, coroutineScope, server)
        }
    }

    fun startup() {
        cttNamespace.startup()
        server.startup().get()
    }

    fun shutdown() {
        cttNamespace.shutdown()
        runBlocking { supervisor.cancelAndJoin() }
        server.shutdown().get()
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

    private fun createEndpointConfigurations(
        config: Config,
        certificate: X509Certificate
    ): Set<EndpointConfiguration> {

        val endpointConfigurations = LinkedHashSet<EndpointConfiguration>()

        val userTokenPolicies = mutableListOf<UserTokenPolicy>().apply {
            add(OpcUaServerConfig.USER_TOKEN_POLICY_ANONYMOUS)
            add(OpcUaServerConfig.USER_TOKEN_POLICY_USERNAME)
        }

        val bindAddresses: List<String> = config[ServerConfig.bindAddressList]

        val endpointAddresses: List<String> = config[ServerConfig.endpointAddressList].flatMap {
            if ("<.+>".toRegex().matches(it)) {
                val hostname = it.removeSurrounding("<", ">")

                if (hostname == "hostname") {
                    HostnameUtil.getHostnames(HostnameUtil.getHostname())
                } else {
                    HostnameUtil.getHostnames(hostname)
                }
            } else {
                setOf(it)
            }
        }

        for (bindAddress in bindAddresses) {
            for (hostname in endpointAddresses) {
                val builder = EndpointConfiguration.newBuilder()
                    .setTransportProfile(TransportProfile.TCP_UASC_UABINARY)
                    .setBindAddress(bindAddress)
                    .setBindPort(config[ServerConfig.bindPort])
                    .setHostname(hostname)
                    .setPath("milo")
                    .setCertificate(certificate)
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

}


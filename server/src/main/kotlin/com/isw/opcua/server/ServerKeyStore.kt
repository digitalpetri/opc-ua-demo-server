package com.isw.opcua.server

import com.isw.opcua.server.util.KeyStoreManager
import org.eclipse.milo.opcua.sdk.server.util.HostnameUtil
import org.eclipse.milo.opcua.stack.core.util.SelfSignedCertificateBuilder
import org.eclipse.milo.opcua.stack.core.util.SelfSignedCertificateGenerator
import java.security.KeyPair
import java.security.KeyStore
import java.security.cert.X509Certificate
import java.util.*
import java.util.regex.Pattern

class ServerKeyStore private constructor(
    private val settings: Settings,
    private val applicationUuid: UUID
) : KeyStoreManager(settings) {

    companion object {

        const val DEFAULT_SERVER_ALIAS = "server"
        const val DEFAULT_SERVER_PASSWORD = "password"

        private val IP_ADDR_PATTERN = Pattern.compile(
            "^(([01]?\\d\\d?|2[0-4]\\d|25[0-5])\\.){3}([01]?\\d\\d?|2[0-4]\\d|25[0-5])$"
        )

        operator fun invoke(
            settings: Settings,
            applicationUuid: UUID = UUID.randomUUID()
        ): ServerKeyStore {

            return ServerKeyStore(settings, applicationUuid).also { it.initialize() }
        }

    }

    override fun initializeKeystore(keyStore: KeyStore) {
        val (serverKeyPair, serverCertificate) = generateSelfSignedCertificate()

        keyStore.setKeyEntry(
            DEFAULT_SERVER_ALIAS,
            serverKeyPair.private,
            DEFAULT_SERVER_PASSWORD.toCharArray(),
            arrayOf(serverCertificate)
        )
    }

    override fun getDefaultAlias(): String {
        return DEFAULT_SERVER_ALIAS
    }

    override fun getDefaultKeyPair(): KeyPair? {
        return getKeyPair(getDefaultAlias(), DEFAULT_SERVER_PASSWORD)
    }

    override fun generateSelfSignedCertificate(): Pair<KeyPair, X509Certificate> {
        val keyPair =
            SelfSignedCertificateGenerator.generateRsaKeyPair(settings.defaultKeyLength)

        val applicationUri = "urn:industrialsoftworks:milo:opcua:server:$applicationUuid"

        val builder = SelfSignedCertificateBuilder(keyPair)
            .setCommonName("Eclipse Milo OPC UA Demo Server")
            .setOrganization("Industrial Softworks")
            .setLocalityName("Folsom")
            .setStateName("CA")
            .setCountryCode("US")
            .setApplicationUri(applicationUri)

        // Get as many hostnames and IP addresses as we can listed in the certificate.
        for (hostname in HostnameUtil.getHostnames("0.0.0.0", false)) {
            if (IP_ADDR_PATTERN.matcher(hostname).matches()) {
                builder.addIpAddress(hostname)
            } else {
                builder.addDnsName(hostname)
            }
        }

        val certificate = builder.build()

        return Pair(keyPair, certificate)
    }

}

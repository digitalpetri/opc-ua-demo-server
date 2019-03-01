package com.isw.opcua.server.util

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.KeyPair
import java.security.KeyStore
import java.security.PrivateKey
import java.security.PublicKey
import java.security.cert.Certificate
import java.security.cert.X509Certificate

abstract class KeyStoreManager(private val settings: Settings) {

    data class Settings(
        val keyStoreFile: File,
        val keyStorePassword: String,
        val defaultKeyLength: Int = 2048
    )

    private val logger: Logger = LoggerFactory.getLogger(KeyStoreManager::class.java)

    private val keyStore = KeyStore.getInstance("pkcs12")

    protected fun initialize() {
        logger.info("Loading KeyStore at ${settings.keyStoreFile.absolutePath}")

        if (settings.keyStoreFile.exists()) {
            keyStore.load(
                FileInputStream(settings.keyStoreFile),
                settings.keyStorePassword.toCharArray()
            )
        } else {
            keyStore.load(
                null,
                settings.keyStorePassword.toCharArray()
            )

            initializeKeystore(keyStore)

            keyStore.store(
                FileOutputStream(settings.keyStoreFile),
                settings.keyStorePassword.toCharArray()
            )
        }
    }

    fun getKeyPair(alias: String, password: String): KeyPair? {
        val certificate: X509Certificate? = keyStore.getCertificate(alias) as? X509Certificate
        val publicKey: PublicKey? = certificate?.publicKey
        val privateKey: PrivateKey? = keyStore.getKey(alias, password.toCharArray()) as? PrivateKey

        return if (publicKey != null && privateKey != null) {
            KeyPair(publicKey, privateKey)
        } else {
            null
        }
    }

    fun getCertificateChain(alias: String): List<X509Certificate>? {
        val certificateChain: Array<Certificate>? = keyStore.getCertificateChain(alias)

        return certificateChain?.mapNotNull { it as? X509Certificate }
    }

    fun getDefaultCertificateChain(): List<X509Certificate>? {
        return getCertificateChain(getDefaultAlias())
    }

    fun setCertificateChain(alias: String, key: PrivateKey, password: String, chain: List<X509Certificate>) {
        keyStore.setKeyEntry(alias, key, password.toCharArray(), chain.toTypedArray())
    }

    protected abstract fun initializeKeystore(keyStore: KeyStore)

    abstract fun getDefaultAlias(): String

    abstract fun getDefaultKeyPair(): KeyPair?

    abstract fun generateSelfSignedCertificate(): Pair<KeyPair, X509Certificate>

}



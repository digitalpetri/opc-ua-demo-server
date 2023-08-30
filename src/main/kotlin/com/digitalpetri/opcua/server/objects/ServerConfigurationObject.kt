package com.digitalpetri.opcua.server.objects

import com.digitalpetri.opcua.server.ServerKeyStore
import com.digitalpetri.opcua.server.namespaces.filters.ExecutableByAdminFilter
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.cert.jcajce.JcaX509CertificateHolder
import org.bouncycastle.pkcs.PKCS10CertificationRequest
import org.bouncycastle.util.io.pem.PemReader
import org.eclipse.milo.opcua.sdk.server.AbstractLifecycle
import org.eclipse.milo.opcua.sdk.server.OpcUaServer
import org.eclipse.milo.opcua.sdk.server.api.methods.MethodInvocationHandler
import org.eclipse.milo.opcua.sdk.server.api.methods.Out
import org.eclipse.milo.opcua.sdk.server.model.methods.CreateSigningRequestMethod
import org.eclipse.milo.opcua.sdk.server.model.methods.GetRejectedListMethod
import org.eclipse.milo.opcua.sdk.server.model.methods.UpdateCertificateMethod
import org.eclipse.milo.opcua.sdk.server.model.nodes.objects.ServerConfigurationTypeNode
import org.eclipse.milo.opcua.sdk.server.model.nodes.objects.TrustListTypeNode
import org.eclipse.milo.opcua.sdk.server.nodes.UaMethodNode
import org.eclipse.milo.opcua.stack.core.Identifiers
import org.eclipse.milo.opcua.stack.core.StatusCodes
import org.eclipse.milo.opcua.stack.core.UaException
import org.eclipse.milo.opcua.stack.core.security.DefaultCertificateManager
import org.eclipse.milo.opcua.stack.core.security.TrustListManager
import org.eclipse.milo.opcua.stack.core.types.builtin.ByteString
import org.eclipse.milo.opcua.stack.core.types.builtin.NodeId
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.Unsigned
import org.eclipse.milo.opcua.stack.core.types.enumerated.MessageSecurityMode
import org.eclipse.milo.opcua.stack.core.util.CertificateUtil
import org.eclipse.milo.opcua.stack.core.util.DigestUtil
import org.eclipse.milo.opcua.stack.core.util.SelfSignedCertificateGenerator
import java.io.ByteArrayInputStream
import java.io.InputStreamReader
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyStore
import java.security.PrivateKey
import java.security.spec.PKCS8EncodedKeySpec
import java.util.concurrent.TimeUnit


class ServerConfigurationObject(
    private val server: OpcUaServer,
    private val serverConfigurationNode: ServerConfigurationTypeNode,
    private val keyStore: ServerKeyStore,
    private val trustListManager: TrustListManager
) : AbstractLifecycle() {

    private lateinit var trustListObject: TrustListObject

    override fun onStartup() {
        serverConfigurationNode.createSigningRequestMethodNode.apply {
            invocationHandler = CreateSigningRequestImpl(this)
            filterChain.addLast(ExecutableByAdminFilter)
        }

        serverConfigurationNode.updateCertificateMethodNode.apply {
            invocationHandler = UpdateCertificateImpl(this, keyStore)
            filterChain.addLast(ExecutableByAdminFilter)
        }

        serverConfigurationNode.getRejectedListMethodNode.apply {
            invocationHandler = GetRejectedListMethodImpl(this)
            filterChain.addLast(ExecutableByAdminFilter)
        }

        serverConfigurationNode.serverCapabilities = arrayOf("")
        serverConfigurationNode.supportedPrivateKeyFormats = arrayOf("PEM", "PFX")
        serverConfigurationNode.maxTrustListSize = Unsigned.uint(0)
        serverConfigurationNode.multicastDnsEnabled = false

        serverConfigurationNode.certificateGroupsNode.defaultApplicationGroupNode.certificateTypes = arrayOf(
            Identifiers.RsaSha256ApplicationCertificateType
        )

        val trustListNode = serverConfigurationNode
            .certificateGroupsNode
            .defaultApplicationGroupNode
            .trustListNode as TrustListTypeNode

        trustListObject = TrustListObject(server, trustListNode, trustListManager)
        trustListObject.startup()
    }

    override fun onShutdown() {
        serverConfigurationNode.componentNodes.filterIsInstance<UaMethodNode>().forEach {
            it.invocationHandler = MethodInvocationHandler.NOT_IMPLEMENTED
        }

        trustListObject.shutdown()
    }

    /**
     * The CreateSigningRequest Method asks the Server to create a PKCS #10 DER encoded Certificate Request that is signed
     * with the Server’s private key. This request can be then used to request a Certificate from a CA that expects requests
     * in this format.
     *
     * This Method requires an encrypted channel and that the Client provide credentials with administrative rights on the
     * Server.
     */
    class CreateSigningRequestImpl(node: UaMethodNode) : CreateSigningRequestMethod(node) {

        private val server: OpcUaServer = node.nodeContext.server

        override fun invoke(
            context: InvocationContext,
            certificateGroupId: NodeId?,
            certificateTypeId: NodeId?,
            subjectName: String?,
            regeneratePrivateKey: Boolean,
            nonce: ByteString?,
            certificateRequest: Out<ByteString>
        ) {

            val session = context.session.orElseThrow()

            if (session.endpoint.securityMode == MessageSecurityMode.None) {
                throw UaException(StatusCodes.Bad_SecurityModeInsufficient)
            }

            if (certificateGroupId != null &&
                certificateGroupId.isNotNull &&
                certificateGroupId != Identifiers.ServerConfiguration_CertificateGroups_DefaultApplicationGroup
            ) {

                throw UaException(StatusCodes.Bad_InvalidArgument)
            }

            val signatureAlgorithm = when (certificateTypeId) {
                Identifiers.RsaMinApplicationCertificateType -> "SHA1withRSA"
                Identifiers.RsaSha256ApplicationCertificateType -> "SHA256withRSA"
                else -> throw UaException(StatusCodes.Bad_InvalidArgument)
            }

            val certificateBytes = session.endpoint.serverCertificate.bytesOrEmpty()
            val thumbprint = ByteString.of(DigestUtil.sha1(certificateBytes))

            val keyPair = if (regeneratePrivateKey) {
                SelfSignedCertificateGenerator.generateRsaKeyPair(2048)
            } else {
                server.config.certificateManager.getKeyPair(thumbprint).orElseThrow()
            }

            val certificate = server.config.certificateManager.getCertificate(thumbprint).orElseThrow()

            if (subjectName.isNullOrEmpty()) {
                // use SubjectName from current certificate
                val subject: X500Name = JcaX509CertificateHolder(certificate).subject

                val csr: PKCS10CertificationRequest = CertificateUtil.generateCsr(
                    keyPair,
                    subject,
                    CertificateUtil.getSanUri(certificate)
                        .orElse(session.endpoint.server.applicationUri),
                    CertificateUtil.getSanDnsNames(certificate),
                    CertificateUtil.getSanIpAddresses(certificate),
                    signatureAlgorithm
                )

                certificateRequest.set(ByteString.of(csr.encoded))
            } else {
                // new SubjectName defined by caller
                val csr: PKCS10CertificationRequest = CertificateUtil.generateCsr(
                    keyPair,
                    subjectName,
                    CertificateUtil.getSanUri(certificate)
                        .orElse(session.endpoint.server.applicationUri),
                    CertificateUtil.getSanDnsNames(certificate),
                    CertificateUtil.getSanIpAddresses(certificate),
                    signatureAlgorithm
                )

                certificateRequest.set(ByteString.of(csr.encoded))
            }
        }

    }

    /**
     * UpdateCertificate is used to update a Certificate for a Server.
     *
     * There are the following three use cases for this Method:
     *
     * 1. The new Certificate was created based on a signing request created with the Method CreateSigningRequest defined
     * in 7.7.6. In this case there is no privateKey provided.
     *
     * 2. A new privateKey and Certificate was created outside the Server and both are updated with this Method.
     *
     * 3. A new Certificate was created and signed with the information from the old Certificate. In this case there is no
     * privateKey provided.
     *
     * This Method requires an encrypted channel and that the Client provide credentials with administrative rights on the
     * Server.
     */
    class UpdateCertificateImpl(
        node: UaMethodNode,
        private val keyStore: ServerKeyStore
    ) : UpdateCertificateMethod(node) {

        private val server: OpcUaServer = node.nodeContext.server

        override fun invoke(
            context: InvocationContext,
            certificateGroupId: NodeId?,
            certificateTypeId: NodeId?,
            certificate: ByteString,
            issuerCertificates: Array<out ByteString>?,
            privateKeyFormat: String?,
            privateKey: ByteString?,
            applyChangesRequired: Out<Boolean>
        ) {

            val session = context.session.orElseThrow()

            if (session.endpoint.securityMode == MessageSecurityMode.None) {
                throw UaException(StatusCodes.Bad_SecurityModeInsufficient)
            }

            if (certificateGroupId != null &&
                certificateGroupId.isNotNull &&
                certificateGroupId != Identifiers.ServerConfiguration_CertificateGroups_DefaultApplicationGroup
            ) {

                throw UaException(StatusCodes.Bad_InvalidArgument)
            }

            val certificateManager = server.config.certificateManager as DefaultCertificateManager
            val certificateBytes = session.endpoint.serverCertificate.bytesOrEmpty()
            val thumbprint = ByteString.of(DigestUtil.sha1(certificateBytes))

            val certificateChain = (listOf(certificate) + (issuerCertificates ?: emptyArray())).map {
                CertificateUtil.decodeCertificate(it.bytesOrEmpty())
            }

            val keyPair: KeyPair = if (privateKey == null || privateKey.isNullOrEmpty) {
                // Use current PrivateKey + new certificates
                val oldKeyPair = certificateManager.getKeyPair(thumbprint).orElseThrow()

                KeyPair(certificateChain[0].publicKey, oldKeyPair.private)
            } else {
                // Use new PrivateKey + new certificates
                val newPrivateKey: PrivateKey = when (privateKeyFormat) {
                    "PEM" -> readPemEncodedPrivateKey(privateKey)
                    "PFX" -> readPfxEncodedPrivateKey(privateKey)
                    else -> throw UaException(StatusCodes.Bad_InvalidArgument)
                }

                KeyPair(certificateChain[0].publicKey, newPrivateKey)
            }

            synchronized(certificateManager) {
                certificateManager.remove(thumbprint)
                certificateManager.add(keyPair, certificateChain.toTypedArray())
            }

            keyStore.setDefaultCertificateChain(
                keyPair.private,
                ServerKeyStore.DEFAULT_SERVER_PASSWORD,
                certificateChain
            )

            server.scheduledExecutorService.schedule(
                {
                    server.executorService.execute {
                        server.stackServer.connectedChannels.forEach { it.disconnect() }
                    }
                },
                3,
                TimeUnit.SECONDS
            )

            applyChangesRequired.set(false)
        }

        private fun readPemEncodedPrivateKey(encoded: ByteString): PrivateKey {
            val reader = PemReader(InputStreamReader(ByteArrayInputStream(encoded.bytesOrEmpty())))
            val encodedKey = reader.readPemObject().content

            val spec = PKCS8EncodedKeySpec(encodedKey)
            val kf = KeyFactory.getInstance("RSA")

            return kf.generatePrivate(spec)
        }

        private fun readPfxEncodedPrivateKey(encoded: ByteString): PrivateKey {
            val keyStore = KeyStore.getInstance("PKCS12")
            keyStore.load(ByteArrayInputStream(encoded.bytesOrEmpty()), null)

            for (alias in keyStore.aliases()) {
                if (keyStore.isKeyEntry(alias)) {
                    val key = keyStore.getKey(alias, null)
                    if (key is PrivateKey) return key
                }
            }

            throw UaException(StatusCodes.Bad_InvalidArgument)
        }

    }

    class GetRejectedListMethodImpl(node: UaMethodNode) : GetRejectedListMethod(node) {

        private val server: OpcUaServer = node.nodeContext.server

        override fun invoke(context: InvocationContext, certificates: Out<Array<ByteString>>) {
            val trustListManager = server.config.trustListManager

            val bs = trustListManager.rejectedCertificates.mapNotNull {
                try {
                    ByteString.of(it.encoded)
                } catch (e: UaException) {
                    null
                }
            }

            certificates.set(bs.toTypedArray())
        }

    }
}

package com.isw.opcua.server.discovery

import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.cert.jcajce.JcaX509CertificateHolder
import org.bouncycastle.pkcs.PKCS10CertificationRequest
import org.eclipse.milo.opcua.sdk.server.OpcUaServer
import org.eclipse.milo.opcua.sdk.server.model.methods.CreateSigningRequestMethod
import org.eclipse.milo.opcua.sdk.server.model.methods.UpdateCertificateMethod
import org.eclipse.milo.opcua.sdk.server.nodes.UaMethodNode
import org.eclipse.milo.opcua.stack.core.Identifiers
import org.eclipse.milo.opcua.stack.core.StatusCodes
import org.eclipse.milo.opcua.stack.core.UaException
import org.eclipse.milo.opcua.stack.core.application.DefaultCertificateManager
import org.eclipse.milo.opcua.stack.core.types.builtin.ByteString
import org.eclipse.milo.opcua.stack.core.types.builtin.NodeId
import org.eclipse.milo.opcua.stack.core.types.enumerated.MessageSecurityMode
import org.eclipse.milo.opcua.stack.core.util.CertificateUtil
import org.eclipse.milo.opcua.stack.core.util.DigestUtil.sha1
import org.eclipse.milo.opcua.stack.core.util.SelfSignedCertificateGenerator
import java.io.ByteArrayInputStream
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyStore
import java.security.PrivateKey
import java.security.spec.PKCS8EncodedKeySpec
import java.util.concurrent.atomic.AtomicReference


fun configureGdsPush(server: OpcUaServer) {
    val createSigningRequestNode = server.nodeManager
        .get(Identifiers.ServerConfiguration_CreateSigningRequest)

    if (createSigningRequestNode is UaMethodNode) {
        val invocationHandler = CreateSigningRequest(createSigningRequestNode)
        createSigningRequestNode.invocationHandler = invocationHandler
    }

    val updateCertificateNode = server.nodeManager
        .get(Identifiers.ServerConfiguration_UpdateCertificate)

    if (updateCertificateNode is UaMethodNode) {
        val invocationHandler = UpdateCertificate(updateCertificateNode)
        updateCertificateNode.invocationHandler = invocationHandler
    }
}

class CreateSigningRequest(node: UaMethodNode) : CreateSigningRequestMethod(node) {

    private val server: OpcUaServer = node.nodeContext.server

    override fun invoke(
        context: InvocationContext,
        certificateGroupId: NodeId,
        certificateTypeId: NodeId,
        subjectName: String?,
        regeneratePrivateKey: Boolean,
        nonce: ByteString,
        certificateRequest: AtomicReference<ByteString>
    ) {

        val session = context.session.orElseThrow()

        if (session.endpoint.securityMode == MessageSecurityMode.None) {
            throw UaException(StatusCodes.Bad_SecurityModeInsufficient)
        }

        if (certificateGroupId.isNotNull &&
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
        val thumbprint = ByteString.of(sha1(certificateBytes))

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
 */
class UpdateCertificate(node: UaMethodNode) : UpdateCertificateMethod(node) {

    private val server: OpcUaServer = node.nodeContext.server

    override fun invoke(
        context: InvocationContext,
        certificateGroupId: NodeId,
        certificateTypeId: NodeId,
        certificate: ByteString,
        issuerCertificates: Array<out ByteString>,
        privateKeyFormat: String,
        privateKey: ByteString,
        applyChangesRequired: AtomicReference<Boolean>
    ) {

        val session = context.session.orElseThrow()

        if (session.endpoint.securityMode == MessageSecurityMode.None) {
            throw UaException(StatusCodes.Bad_SecurityModeInsufficient)
        }

        if (certificateGroupId.isNotNull &&
            certificateGroupId != Identifiers.ServerConfiguration_CertificateGroups_DefaultApplicationGroup
        ) {

            throw UaException(StatusCodes.Bad_InvalidArgument)
        }

        val certificateManager = server.config.certificateManager as DefaultCertificateManager
        val certificateBytes = session.endpoint.serverCertificate.bytesOrEmpty()
        val thumbprint = ByteString.of(sha1(certificateBytes))

        val certificateChain = (listOf(certificate) + issuerCertificates).map {
            CertificateUtil.decodeCertificate(it.bytesOrEmpty())
        }

        if (privateKey.isNull) {
            // Use current PrivateKey + new certificates

            val oldKeyPair = certificateManager.getKeyPair(thumbprint).orElseThrow()
            val newKeyPair = KeyPair(certificateChain[0].publicKey, oldKeyPair.private)

            synchronized(certificateManager) {
                certificateManager.remove(thumbprint)
                certificateManager.add(newKeyPair, certificateChain.toTypedArray())
            }
        } else {
            // Use new PrivateKey + new certificates

            val newPrivateKey: PrivateKey = when (privateKeyFormat) {
                "PEM" -> readPemEncodedPrivateKey(privateKey)
                "PFX" -> readPfxEncodedPrivateKey(privateKey)
                else -> throw UaException(StatusCodes.Bad_InvalidArgument)
            }

            val newKeyPair = KeyPair(certificateChain[0].publicKey, newPrivateKey)

            synchronized(certificateManager) {
                certificateManager.remove(thumbprint)
                certificateManager.add(newKeyPair, certificateChain.toTypedArray())
            }
        }

        server.stackServer.connectedChannels.forEach { it.disconnect() }

        applyChangesRequired.set(false)
    }

    private fun readPemEncodedPrivateKey(encoded: ByteString): PrivateKey {
        val spec = PKCS8EncodedKeySpec(encoded.bytesOrEmpty())
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


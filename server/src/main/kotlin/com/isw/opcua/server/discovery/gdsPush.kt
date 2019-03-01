package com.isw.opcua.server.discovery

import com.isw.opcua.server.DemoServer
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
import org.eclipse.milo.opcua.stack.core.types.builtin.ByteString
import org.eclipse.milo.opcua.stack.core.types.builtin.NodeId
import org.eclipse.milo.opcua.stack.core.types.enumerated.MessageSecurityMode
import org.eclipse.milo.opcua.stack.core.util.CertificateUtil
import org.eclipse.milo.opcua.stack.core.util.DigestUtil.sha1
import org.eclipse.milo.opcua.stack.core.util.SelfSignedCertificateGenerator
import java.util.concurrent.atomic.AtomicReference


fun configureGdsPush(server: DemoServer) {}

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
            Identifiers.RsaMinApplicationCertificateType -> ""
            Identifiers.RsaSha256ApplicationCertificateType -> ""
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


    }

}


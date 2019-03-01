package com.isw.opcua.server.discovery

import com.isw.opcua.server.DemoServer
import org.eclipse.milo.opcua.sdk.server.OpcUaServer
import org.eclipse.milo.opcua.sdk.server.model.methods.CreateSigningRequestMethod
import org.eclipse.milo.opcua.sdk.server.model.methods.UpdateCertificateMethod
import org.eclipse.milo.opcua.sdk.server.nodes.UaMethodNode
import org.eclipse.milo.opcua.stack.core.Identifiers
import org.eclipse.milo.opcua.stack.core.StatusCodes
import org.eclipse.milo.opcua.stack.core.UaException
import org.eclipse.milo.opcua.stack.core.types.builtin.ByteString
import org.eclipse.milo.opcua.stack.core.types.builtin.NodeId
import java.util.concurrent.atomic.AtomicReference


fun configureGdsPush(server: DemoServer) {}

class CreateSigningRequest(node: UaMethodNode) : CreateSigningRequestMethod(node) {

    private val server: OpcUaServer = node.nodeContext.server

    override fun invoke(
        context: InvocationContext,
        certificateGroupId: NodeId,
        certificateTypeId: NodeId,
        subjectName: String,
        regeneratePrivateKey: Boolean,
        nonce: ByteString,
        certificateRequest: AtomicReference<ByteString>
    ) {

        if (certificateGroupId.isNotNull &&
            certificateGroupId != Identifiers.ServerConfiguration_CertificateGroups_DefaultApplicationGroup) {

            throw UaException(StatusCodes.Bad_InvalidArgument)
        }

        if (certificateTypeId != Identifiers.RsaMinApplicationCertificateType &&
            certificateTypeId != Identifiers.RsaSha256ApplicationCertificateType) {

            throw UaException(StatusCodes.Bad_InvalidArgument)
        }



        certificateRequest.set(ByteString.NULL_VALUE)
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


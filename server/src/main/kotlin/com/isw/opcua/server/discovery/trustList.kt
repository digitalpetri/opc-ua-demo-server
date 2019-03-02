package com.isw.opcua.server.discovery

import org.eclipse.milo.opcua.sdk.server.OpcUaServer
import org.eclipse.milo.opcua.sdk.server.model.methods.*
import org.eclipse.milo.opcua.sdk.server.model.nodes.objects.TrustListNode
import org.eclipse.milo.opcua.sdk.server.nodes.UaMethodNode
import org.eclipse.milo.opcua.stack.core.StatusCodes
import org.eclipse.milo.opcua.stack.core.UaException
import org.eclipse.milo.opcua.stack.core.types.builtin.ByteString
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.UByte
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.UInteger
import org.eclipse.milo.opcua.stack.core.types.enumerated.TrustListMasks
import java.util.concurrent.atomic.AtomicReference


fun configureTrustList(server: OpcUaServer, trustListNode: TrustListNode) {
    trustListNode.openWithMasksMethodNode.apply {
        invocationHandler = OpenWithMasks(this)
        setAttributeDelegate(ExecutableByAdmin)
    }

    trustListNode.readMethodNode.apply {
        invocationHandler = Read(this)
        setAttributeDelegate(ExecutableByAdmin)
    }

    trustListNode.closeAndUpdateMethodNode?.apply {
        invocationHandler = CloseAndUpdate(this)
        setAttributeDelegate(ExecutableByAdmin)
    }

    trustListNode.addCertificateMethodNode?.apply {
        invocationHandler = AddCertificate(this)
        setAttributeDelegate(ExecutableByAdmin)
    }

    trustListNode.removeCertificateMethodNode?.apply {
        invocationHandler = RemoveCertificate(this)
        setAttributeDelegate(ExecutableByAdmin)
    }
}

/**
 * The OpenWithMasks Method allows a Client to read only the portion of the Trust List.
 *
 * This Method can only be used to read the Trust List.
 */
class OpenWithMasks(node: UaMethodNode) : OpenWithMasksMethod(node) {

    override fun invoke(
        context: InvocationContext,
        masks: UInteger,
        fileHandle: AtomicReference<UInteger>
    ) {

        if (masks.isBitSet(TrustListMasks.TrustedCertificates)) {

        }

        if (masks.isBitSet(TrustListMasks.TrustedCrls)) {

        }

        if (masks.isBitSet(TrustListMasks.IssuerCertificates)) {

        }

        if (masks.isBitSet(TrustListMasks.IssuerCrls)) {

        }

        throw UaException(StatusCodes.Bad_NotImplemented)
    }

}

class Open(node: UaMethodNode): OpenMethod(node) {

    override fun invoke(
        context: InvocationContext,
        mode: UByte,
        fileHandle: AtomicReference<UInteger>) {

        throw UaException(StatusCodes.Bad_NotImplemented)
    }

}

class Read(node: UaMethodNode) : ReadMethod(node) {

    override fun invoke(
        context: InvocationContext,
        fileHandle: UInteger,
        length: Int,
        data: AtomicReference<ByteString>
    ) {

        throw UaException(StatusCodes.Bad_NotImplemented)
    }

}

class CloseAndUpdate(node: UaMethodNode) : CloseAndUpdateMethod(node) {

    override fun invoke(
        context: InvocationContext,
        fileHandle: UInteger,
        applyChangesRequired: AtomicReference<Boolean>
    ) {

        throw UaException(StatusCodes.Bad_NotImplemented)
    }

}

class AddCertificate(node: UaMethodNode) : AddCertificateMethod(node) {

    override fun invoke(
        context: InvocationContext,
        certificate: ByteString,
        isTrustedCertificate: Boolean
    ) {

        throw UaException(StatusCodes.Bad_NotImplemented)
    }

}

class RemoveCertificate(node: UaMethodNode) : RemoveCertificateMethod(node) {

    override fun invoke(
        context: InvocationContext,
        thumbprint: String,
        isTrustedCertificate: Boolean
    ) {

        throw UaException(StatusCodes.Bad_NotImplemented)
    }

}

private fun UInteger.isBitSet(mask: TrustListMasks): Boolean {
    return (this.toInt() and mask.value) == mask.value
}

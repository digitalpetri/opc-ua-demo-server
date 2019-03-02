package com.isw.opcua.server.discovery

import com.isw.opcua.server.objects.TrustListObject
import com.isw.opcua.server.objects.getTrustListDataType
import com.isw.opcua.server.util.ExecutableByAdmin
import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import org.eclipse.milo.opcua.sdk.server.OpcUaServer
import org.eclipse.milo.opcua.sdk.server.model.methods.*
import org.eclipse.milo.opcua.sdk.server.model.nodes.objects.TrustListNode
import org.eclipse.milo.opcua.sdk.server.nodes.UaMethodNode
import org.eclipse.milo.opcua.stack.core.StatusCodes
import org.eclipse.milo.opcua.stack.core.UaException
import org.eclipse.milo.opcua.stack.core.application.DirectoryCertificateValidator
import org.eclipse.milo.opcua.stack.core.serialization.OpcUaBinaryStreamEncoder
import org.eclipse.milo.opcua.stack.core.types.builtin.ByteString
import org.eclipse.milo.opcua.stack.core.types.builtin.ExtensionObject
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.UByte
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.UInteger
import org.eclipse.milo.opcua.stack.core.types.enumerated.TrustListMasks
import java.io.File
import java.util.concurrent.atomic.AtomicReference


fun configureTrustList(server: OpcUaServer, trustListNode: TrustListNode) {
    val validator = server.config.certificateValidator as DirectoryCertificateValidator

    val trustListObject = TrustListObject(
        trustListNode,
        {
            val trustList = validator.getTrustListDataType()

            File.createTempFile("TrustListDataType", null).apply {
                val buffer = Unpooled.buffer()
                val encoder = OpcUaBinaryStreamEncoder(buffer)
                encoder.writeExtensionObject(ExtensionObject.encode(trustList))

                println(path)

                writeBytes(buffer.array())
            }
        },
        validator
    )

    trustListObject.startup()
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

class Open(node: UaMethodNode) : OpenMethod(node) {

    override fun invoke(
        context: InvocationContext,
        mode: UByte,
        fileHandle: AtomicReference<UInteger>
    ) {

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

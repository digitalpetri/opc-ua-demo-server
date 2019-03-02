package com.isw.opcua.server.objects

import com.isw.opcua.server.util.ExecutableByAdmin
import org.eclipse.milo.opcua.sdk.server.api.MethodInvocationHandler
import org.eclipse.milo.opcua.sdk.server.model.methods.AddCertificateMethod
import org.eclipse.milo.opcua.sdk.server.model.methods.CloseAndUpdateMethod
import org.eclipse.milo.opcua.sdk.server.model.methods.OpenWithMasksMethod
import org.eclipse.milo.opcua.sdk.server.model.methods.RemoveCertificateMethod
import org.eclipse.milo.opcua.sdk.server.model.nodes.objects.TrustListNode
import org.eclipse.milo.opcua.sdk.server.nodes.UaMethodNode
import org.eclipse.milo.opcua.stack.core.StatusCodes
import org.eclipse.milo.opcua.stack.core.UaException
import org.eclipse.milo.opcua.stack.core.application.DirectoryCertificateValidator
import org.eclipse.milo.opcua.stack.core.types.builtin.ByteString
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.UInteger
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.Unsigned.ubyte
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.Unsigned.uint
import org.eclipse.milo.opcua.stack.core.types.enumerated.TrustListMasks
import org.eclipse.milo.opcua.stack.core.types.structured.TrustListDataType
import java.io.File
import java.io.RandomAccessFile
import java.security.cert.X509CRL
import java.util.*
import java.util.concurrent.atomic.AtomicReference


class TrustListObject(
    private val trustListNode: TrustListNode,
    private val openTrustListFile: (EnumSet<TrustListMasks>) -> File,
    private val certificateValidator: DirectoryCertificateValidator
) : FileObject(trustListNode, { openTrustListFile(EnumSet.allOf(TrustListMasks::class.java)) }) {

    override fun onStartup() {
        super.onStartup()

        trustListNode.openWithMasksMethodNode.apply {
            invocationHandler = OpenWithMasksImpl(this)
            setAttributeDelegate(ExecutableByAdmin)
        }
    }

    override fun onShutdown() {
        trustListNode.openWithMasksMethodNode.apply {
            invocationHandler = MethodInvocationHandler.NOT_IMPLEMENTED
        }

        super.onShutdown()
    }

    inner class OpenWithMasksImpl(node: UaMethodNode) : OpenWithMasksMethod(node) {
        override fun invoke(
            context: InvocationContext,
            masks: UInteger,
            fileHandle: AtomicReference<UInteger>
        ) {

            val session = context.session.orElseThrow()

            val maskSet = EnumSet.noneOf(TrustListMasks::class.java)

            TrustListMasks.values().forEach { mask ->
                if (masks.isSet(mask)) {
                    maskSet.add(mask)
                }
            }

            val file = openTrustListFile(maskSet)
            val raf = RandomAccessFile(file, "r")

            val handle = uint(fileHandleSequence.incrementAndGet())

            handles.put(session.sessionId, handle, Pair(raf, ubyte(FileModeBit.Read.value)))

            fileHandle.set(handle)
        }
    }

    inner class CloseAndUpdateImpl(node: UaMethodNode) : CloseAndUpdateMethod(node) {
        override fun invoke(
            context: InvocationContext?,
            fileHandle: UInteger?,
            applyChangesRequired: AtomicReference<Boolean>?
        ) {

            throw UaException(StatusCodes.Bad_NotImplemented)
        }
    }

    inner class AddCertificateImpl(node: UaMethodNode) : AddCertificateMethod(node) {
        override fun invoke(context: InvocationContext?, certificate: ByteString?, isTrustedCertificate: Boolean?) {
            throw UaException(StatusCodes.Bad_NotImplemented)
        }
    }

    inner class RemoveCertificateImpl(node: UaMethodNode) : RemoveCertificateMethod(node) {
        override fun invoke(context: InvocationContext?, thumbprint: String?, isTrustedCertificate: Boolean?) {
            throw UaException(StatusCodes.Bad_NotImplemented)
        }
    }

}

private fun UInteger.isSet(bit: TrustListMasks): Boolean {
    return true // TODO
}

fun DirectoryCertificateValidator.getTrustListDataType(): TrustListDataType {
    val trustedCertificates = this.trustedCertificates.map { ByteString.of(it.encoded) }
    val issuerCertificates = this.issuerCertificates.map { ByteString.of(it.encoded) }
    val issuerCrls = this.issuerCrls.map {
        val crl = it as X509CRL
        ByteString.of(crl.encoded)
    }

    return TrustListDataType(
        uint(0b1111),
        trustedCertificates.toTypedArray(),
        emptyArray(),
        issuerCertificates.toTypedArray(),
        issuerCrls.toTypedArray()
    )
}

package com.isw.opcua.server.util

import com.google.common.collect.Maps
import org.eclipse.milo.opcua.sdk.server.model.methods.*
import org.eclipse.milo.opcua.sdk.server.model.nodes.objects.FileNode
import org.eclipse.milo.opcua.sdk.server.nodes.UaMethodNode
import org.eclipse.milo.opcua.stack.core.StatusCodes
import org.eclipse.milo.opcua.stack.core.UaException
import org.eclipse.milo.opcua.stack.core.types.builtin.ByteString
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.UByte
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.UInteger
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.ULong
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.Unsigned.uint
import java.io.*
import java.util.concurrent.ConcurrentMap
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference


class FileObject(
    private val fileNode: FileNode,
    private val open: () -> FileStreams
) : AbstractLifecycle() {

    private val fileHandleSequence = AtomicLong(0)

    private val openHandles: ConcurrentMap<UInteger, Pair<UByte, FileStreams>> = Maps.newConcurrentMap()

    override fun onStartup() {
        fileNode.openMethodNode.apply {
            invocationHandler = getOpenMethod(this)
        }

        fileNode.closeMethodNode.apply {
            invocationHandler = getCloseMethod(this)
        }

        fileNode.readMethodNode.apply {
            invocationHandler = getReadMethod(this)
        }
    }

    override fun onShutdown() {
        // TODO close streams and set invocation handlers back to not implemented
    }

    open fun getOpenMethod(methodNode: UaMethodNode): OpenMethod = OpenImpl(methodNode)
    open fun getCloseMethod(methodNode: UaMethodNode): CloseMethod = CloseImpl(methodNode)
    open fun getReadMethod(methodNode: UaMethodNode): ReadMethod = ReadImpl(methodNode)

    inner class OpenImpl(node: UaMethodNode) : OpenMethod(node) {

        override fun invoke(
            context: InvocationContext,
            mode: UByte,
            fileHandle: AtomicReference<UInteger>
        ) {

            // bits: Read, Write, EraseExisting, Append
            val handle = uint(fileHandleSequence.incrementAndGet())
            val fileStreams: FileStreams = open()

            openHandles[handle] = Pair(mode, fileStreams)

            fileHandle.set(handle)
        }

    }

    inner class CloseImpl(node: UaMethodNode) : CloseMethod(node) {

        override fun invoke(context: InvocationContext, fileHandle: UInteger) {
            openHandles.remove(fileHandle)?.let {
                val (_, fileStreams) = it

                // TODO
            }
        }

    }

    inner class ReadImpl(node: UaMethodNode) : ReadMethod(node) {
        override fun invoke(
            context: InvocationContext,
            fileHandle: UInteger,
            length: Int,
            data: AtomicReference<ByteString>
        ) {

            openHandles[fileHandle]?.let {
                val (mode, fileStreams) = it

                if ((mode.toInt() and 1) == 1) {
                    val inputStream = fileStreams.openInputStream()
                    val bs = inputStream.readNBytes(length)
                    data.set(ByteString.of(bs))
                } else {
                    throw UaException(StatusCodes.Bad_NotReadable)
                }
            }
        }
    }

    inner class WriteImpl(node: UaMethodNode) : WriteMethod(node) {
        override fun invoke(context: InvocationContext?, fileHandle: UInteger?, data: ByteString?) {
            throw UaException(StatusCodes.Bad_NotImplemented)
        }
    }

    inner class GetPositionImpl(node: UaMethodNode) : GetPositionMethod(node) {
        override fun invoke(context: InvocationContext?, fileHandle: UInteger?, position: AtomicReference<ULong>?) {
            throw UaException(StatusCodes.Bad_NotImplemented)
        }
    }

    inner class SetPositionImpl(node: UaMethodNode) : SetPositionMethod(node) {
        override fun invoke(context: InvocationContext?, fileHandle: UInteger?, position: ULong?) {
            throw UaException(StatusCodes.Bad_NotImplemented)
        }
    }

}

data class FileStreams(
    val openInputStream: () -> InputStream,
    val openOutputStream: () -> OutputStream
) {

    companion object {
        operator fun invoke(file: File): FileStreams {
            return FileStreams(
                { FileInputStream(file) },
                { FileOutputStream(file) }
            )
        }
    }

}

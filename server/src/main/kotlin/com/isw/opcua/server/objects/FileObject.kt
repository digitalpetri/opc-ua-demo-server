package com.isw.opcua.server.objects

import com.google.common.collect.HashBasedTable
import com.google.common.collect.Table
import com.isw.opcua.server.util.AbstractLifecycle
import org.eclipse.milo.opcua.sdk.server.api.MethodInvocationHandler
import org.eclipse.milo.opcua.sdk.server.api.nodes.VariableNode
import org.eclipse.milo.opcua.sdk.server.model.methods.*
import org.eclipse.milo.opcua.sdk.server.model.nodes.objects.FileNode
import org.eclipse.milo.opcua.sdk.server.nodes.AttributeContext
import org.eclipse.milo.opcua.sdk.server.nodes.UaMethodNode
import org.eclipse.milo.opcua.sdk.server.nodes.delegates.AttributeDelegate
import org.eclipse.milo.opcua.stack.core.StatusCodes
import org.eclipse.milo.opcua.stack.core.UaException
import org.eclipse.milo.opcua.stack.core.types.builtin.ByteString
import org.eclipse.milo.opcua.stack.core.types.builtin.DataValue
import org.eclipse.milo.opcua.stack.core.types.builtin.NodeId
import org.eclipse.milo.opcua.stack.core.types.builtin.Variant
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.*
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.Unsigned.uint
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.Unsigned.ulong
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference


open class FileObject(
    private val fileNode: FileNode,
    private val openFile: () -> File
) : AbstractLifecycle() {

    protected val fileHandleSequence = AtomicLong(0)

    protected val handles: Table<NodeId, UInteger, Pair<RandomAccessFile, UByte>> = HashBasedTable.create()

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

        fileNode.writeMethodNode.apply {
            invocationHandler = getWriteMethod(this)
        }

        fileNode.getPositionMethodNode.apply {
            invocationHandler = getGetPositionMethod(this)
        }

        fileNode.setPositionMethodNode.apply {
            invocationHandler = getSetPositionMethod(this)
        }

        fileNode.openCountNode.setAttributeDelegate(object : AttributeDelegate {
            override fun getValue(context: AttributeContext, node: VariableNode): DataValue {
                val count: UShort = Unsigned.ushort(handles.size())

                return DataValue(Variant(count))
            }
        })

        fileNode.sizeNode.setAttributeDelegate(object : AttributeDelegate {
            override fun getValue(context: AttributeContext, node: VariableNode): DataValue {
                val file = openFile()
                val size: ULong = ulong(file.length())

                return DataValue(Variant(size))
            }
        })

        fileNode.writable = false
        fileNode.userWritable = false
    }

    override fun onShutdown() {
        fileNode.openMethodNode.apply {
            invocationHandler = MethodInvocationHandler.NOT_IMPLEMENTED
        }

        fileNode.closeMethodNode.apply {
            invocationHandler = MethodInvocationHandler.NOT_IMPLEMENTED
        }

        fileNode.readMethodNode.apply {
            invocationHandler = MethodInvocationHandler.NOT_IMPLEMENTED
        }

        fileNode.writeMethodNode.apply {
            invocationHandler = MethodInvocationHandler.NOT_IMPLEMENTED
        }

        fileNode.getPositionMethodNode.apply {
            invocationHandler = MethodInvocationHandler.NOT_IMPLEMENTED
        }

        fileNode.setPositionMethodNode.apply {
            invocationHandler = MethodInvocationHandler.NOT_IMPLEMENTED
        }

        handles.values().forEach { (f, _) -> f.close() }
        handles.clear()
    }

    open fun getOpenMethod(methodNode: UaMethodNode): OpenMethod = OpenImpl(methodNode)
    open fun getCloseMethod(methodNode: UaMethodNode): CloseMethod = CloseImpl(methodNode)
    open fun getReadMethod(methodNode: UaMethodNode): ReadMethod = ReadImpl(methodNode)
    open fun getWriteMethod(methodNode: UaMethodNode): WriteMethod = WriteImpl(methodNode)
    open fun getGetPositionMethod(methodNode: UaMethodNode): GetPositionMethod = GetPositionImpl(methodNode)
    open fun getSetPositionMethod(methodNode: UaMethodNode): SetPositionMethod = SetPositionImpl(methodNode)

    enum class FileModeBit(val value: Int) {
        Read(1),
        Write(2),
        EraseExisting(4),
        Append(8)
    }

    fun isOpen(): Boolean {
        return handles.size() > 0
    }

    fun isOpenForWriting(): Boolean {
        return handles.values().any { (_, mode) ->
            (mode.toInt() and 0b0010) == 1
        }
    }

    fun UByte.isSet(mask: FileModeBit): Boolean {
        return (this.toInt() and mask.value) == mask.value
    }

    /**
     * Open is used to open a file represented by an Object of FileType.
     *
     * When a client opens a file it gets a file handle that is valid while the session is open. Clients shall use the
     * Close Method to release the handle when they do not need access to the file anymore. Clients can open the same
     * file several times for read.
     *
     * A request to open for writing shall return Bad_NotWritable when the file is already opened.
     *
     * A request to open for reading shall return Bad_NotReadable when the file is already opened for writing.
     */
    inner class OpenImpl(node: UaMethodNode) : OpenMethod(node) {

        override fun invoke(
            context: InvocationContext,
            mode: UByte,
            fileHandle: AtomicReference<UInteger>
        ) {

            val session = context.session.orElseThrow()

            // bits: Read, Write, EraseExisting, Append
            var modeString = ""
            var erase = false
            var append = false

            if (mode.isSet(FileModeBit.Read)) {
                if (isOpenForWriting()) {
                    throw UaException(StatusCodes.Bad_NotReadable)
                }
                modeString += "r"
            }

            if (mode.isSet(FileModeBit.Write)) {
                if (isOpen()) {
                    throw UaException(StatusCodes.Bad_NotWritable)
                }
                modeString += "ws"
            }

            if (mode.isSet(FileModeBit.EraseExisting)) {
                erase = true
            }

            if (mode.isSet(FileModeBit.Append)) {
                append = true
            }

            val file = openFile()
            if (erase) {
                FileOutputStream(file).close()
            }

            val raf = RandomAccessFile(file, modeString)
            if (append) {
                raf.channel.position(raf.length())
            }

            val handle = uint(fileHandleSequence.incrementAndGet())

            handles.put(session.sessionId, handle, Pair(raf, mode))

            fileHandle.set(handle)
        }

    }

    open inner class CloseImpl(node: UaMethodNode) : CloseMethod(node) {

        override fun invoke(context: InvocationContext, fileHandle: UInteger) {
            val session = context.session.orElseThrow()

            val filePair = handles.remove(session.sessionId, fileHandle)

            if (filePair != null) {
                val (file, _) = filePair

                file.close()
            } else {
                throw UaException(StatusCodes.Bad_NotFound)
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

            val session = context.session.orElseThrow()

            val filePair = handles.get(session.sessionId, fileHandle)

            if (filePair != null) {
                val (file, mode) = filePair

                if (mode.isSet(FileModeBit.Read)) {
                    val bs = ByteArray(length)
                    val bytesRead = file.read(bs)

                    when {
                        bytesRead == -1 -> data.set(ByteString.of(ByteArray(0)))
                        bytesRead < length -> data.set(ByteString.of(bs.copyOfRange(0, bytesRead)))
                        else -> data.set(ByteString.of(bs))
                    }
                } else {
                    throw UaException(StatusCodes.Bad_NotReadable)
                }
            } else {
                throw UaException(StatusCodes.Bad_NotFound)
            }
        }
    }

    inner class WriteImpl(node: UaMethodNode) : WriteMethod(node) {

        override fun invoke(
            context: InvocationContext,
            fileHandle: UInteger,
            data: ByteString
        ) {

            val session = context.session.orElseThrow()

            val filePair = handles.get(session.sessionId, fileHandle)

            if (filePair != null) {
                val (file, mode) = filePair

                if (mode.isSet(FileModeBit.Write)) {
                    file.write(data.bytesOrEmpty())
                } else {
                    throw UaException(StatusCodes.Bad_NotWritable)
                }
            } else {
                throw UaException(StatusCodes.Bad_NotFound)
            }
        }

    }

    inner class GetPositionImpl(node: UaMethodNode) : GetPositionMethod(node) {

        override fun invoke(
            context: InvocationContext,
            fileHandle: UInteger,
            position: AtomicReference<ULong>
        ) {

            val session = context.session.orElseThrow()

            val filePair = handles.get(session.sessionId, fileHandle)

            if (filePair != null) {
                val (file, _) = filePair

                position.set(ulong(file.channel.position()))
            } else {
                throw UaException(StatusCodes.Bad_NotFound)
            }
        }

    }

    inner class SetPositionImpl(node: UaMethodNode) : SetPositionMethod(node) {

        override fun invoke(
            context: InvocationContext,
            fileHandle: UInteger,
            position: ULong
        ) {

            val session = context.session.orElseThrow()

            val filePair = handles.get(session.sessionId, fileHandle)

            if (filePair != null) {
                val (file, _) = filePair

                file.channel.position(position.toLong())
            } else {
                throw UaException(StatusCodes.Bad_NotFound)
            }
        }

    }

}

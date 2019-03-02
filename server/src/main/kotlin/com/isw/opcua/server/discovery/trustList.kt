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

package com.isw.opcua.server.discovery

import com.isw.opcua.server.objects.TrustListObject
import com.isw.opcua.server.objects.getTrustListDataType
import org.eclipse.milo.opcua.sdk.server.OpcUaServer
import org.eclipse.milo.opcua.sdk.server.model.nodes.objects.TrustListNode
import org.eclipse.milo.opcua.stack.core.application.DirectoryCertificateValidator
import org.eclipse.milo.opcua.stack.core.serialization.EncodingLimits
import org.eclipse.milo.opcua.stack.core.types.OpcUaDataTypeManager
import org.eclipse.milo.opcua.stack.core.types.OpcUaDefaultBinaryEncoding
import org.eclipse.milo.opcua.stack.core.types.builtin.ByteString
import org.eclipse.milo.opcua.stack.core.types.structured.TrustListDataType
import java.io.File


fun configureTrustList(server: OpcUaServer, trustListNode: TrustListNode) {
    val validator = server.config.certificateValidator as DirectoryCertificateValidator

    val trustListObject = TrustListObject(
        trustListNode,
        {
            val trustList = validator.getTrustListDataType()

            File.createTempFile("TrustListDataType", null).apply {
                val encoded = OpcUaDefaultBinaryEncoding.getInstance().encode(
                    trustList,
                    TrustListDataType.BinaryEncodingId,
                    EncodingLimits.DEFAULT,
                    OpcUaDataTypeManager.getInstance()
                ) as ByteString

                writeBytes(encoded.bytesOrEmpty())
            }
        },
        validator
    )

    trustListObject.startup()
}

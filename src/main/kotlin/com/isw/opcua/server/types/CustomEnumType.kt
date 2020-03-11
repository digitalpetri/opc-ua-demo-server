package com.isw.opcua.server.types

import com.isw.opcua.server.namespaces.demo.DemoNamespace
import org.eclipse.milo.opcua.stack.core.serialization.SerializationContext
import org.eclipse.milo.opcua.stack.core.serialization.UaDecoder
import org.eclipse.milo.opcua.stack.core.serialization.UaEncoder
import org.eclipse.milo.opcua.stack.core.serialization.UaEnumeration
import org.eclipse.milo.opcua.stack.core.serialization.codecs.GenericDataTypeCodec
import org.eclipse.milo.opcua.stack.core.types.builtin.ExpandedNodeId

enum class CustomEnumType(private val value: Int) : UaEnumeration {
    Field0(0),
    Field1(1),
    Field2(2);

    override fun getValue(): Int {
        return value
    }

    class Codec : GenericDataTypeCodec<CustomEnumType>() {

        override fun getType(): Class<CustomEnumType> {
            return CustomEnumType::class.java
        }

        override fun decode(context: SerializationContext, decoder: UaDecoder): CustomEnumType? {
            return from(decoder.readInt32(null))
        }

        override fun encode(
            context: SerializationContext,
            encoder: UaEncoder,
            value: CustomEnumType
        ) {

            encoder.writeInt32(null, value.getValue())
        }

    }

    companion object {

        val TYPE_ID: ExpandedNodeId = ExpandedNodeId.parse(
            String.format(
                "nsu=%s;s=%s",
                DemoNamespace.NAMESPACE_URI,
                "DataType.CustomEnumType"
            )
        )

        // @Nullable
        fun from(value: Int): CustomEnumType? {
            return when (value) {
                0 -> Field0
                1 -> Field1
                2 -> Field2
                else -> null
            }
        }

    }

}

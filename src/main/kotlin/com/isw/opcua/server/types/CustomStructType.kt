package com.isw.opcua.server.types

import com.google.common.base.MoreObjects
import com.google.common.base.Objects
import com.isw.opcua.server.namespaces.demo.DemoNamespace
import org.eclipse.milo.opcua.stack.core.serialization.SerializationContext
import org.eclipse.milo.opcua.stack.core.serialization.UaDecoder
import org.eclipse.milo.opcua.stack.core.serialization.UaEncoder
import org.eclipse.milo.opcua.stack.core.serialization.UaStructure
import org.eclipse.milo.opcua.stack.core.serialization.codecs.GenericDataTypeCodec
import org.eclipse.milo.opcua.stack.core.types.builtin.ExpandedNodeId
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.UInteger
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.Unsigned.uint

class CustomStructType @JvmOverloads constructor(
    val foo: String? = null,
    val bar: UInteger = uint(0),
    val isBaz: Boolean = false
) : UaStructure {

    override fun getTypeId(): ExpandedNodeId {
        return TYPE_ID
    }

    override fun getBinaryEncodingId(): ExpandedNodeId {
        return BINARY_ENCODING_ID
    }

    override fun getXmlEncodingId(): ExpandedNodeId {
        // XML encoding not supported
        return ExpandedNodeId.NULL_VALUE
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || javaClass != other.javaClass) return false
        val that = other as CustomStructType
        return isBaz == that.isBaz &&
            Objects.equal(foo, that.foo) &&
            Objects.equal(bar, that.bar)
    }

    override fun hashCode(): Int {
        return Objects.hashCode(foo, bar, isBaz)
    }

    override fun toString(): String {
        return MoreObjects.toStringHelper(this)
            .add("foo", foo)
            .add("bar", bar)
            .add("baz", isBaz)
            .toString()
    }

    class Codec : GenericDataTypeCodec<CustomStructType>() {

        override fun getType(): Class<CustomStructType> {
            return CustomStructType::class.java
        }

        override fun decode(
            context: SerializationContext,
            decoder: UaDecoder
        ): CustomStructType {

            val foo = decoder.readString("Foo")
            val bar = decoder.readUInt32("Bar")
            val baz = decoder.readBoolean("Baz")
            return CustomStructType(foo, bar, baz)
        }

        override fun encode(
            context: SerializationContext,
            encoder: UaEncoder, value: CustomStructType
        ) {

            encoder.writeString("Foo", value.foo)
            encoder.writeUInt32("Bar", value.bar)
            encoder.writeBoolean("Baz", value.isBaz)
        }

    }

    companion object {

        val TYPE_ID: ExpandedNodeId = ExpandedNodeId.parse(
            String.format(
                "nsu=%s;s=%s",
                DemoNamespace.NAMESPACE_URI,
                "DataType.CustomStructType"
            )
        )

        val BINARY_ENCODING_ID: ExpandedNodeId = ExpandedNodeId.parse(
            String.format(
                "nsu=%s;s=%s",
                DemoNamespace.NAMESPACE_URI,
                "DataType.CustomStructType.BinaryEncoding"
            )
        )

    }

}

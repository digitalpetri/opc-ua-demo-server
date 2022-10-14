package com.digitalpetri.opcua.server.types

import com.digitalpetri.opcua.server.namespaces.demo.DemoNamespace
import com.google.common.base.MoreObjects
import org.eclipse.milo.opcua.stack.core.StatusCodes
import org.eclipse.milo.opcua.stack.core.UaSerializationException
import org.eclipse.milo.opcua.stack.core.encoding.EncodingContext
import org.eclipse.milo.opcua.stack.core.encoding.GenericDataTypeCodec
import org.eclipse.milo.opcua.stack.core.encoding.UaDecoder
import org.eclipse.milo.opcua.stack.core.encoding.UaEncoder
import org.eclipse.milo.opcua.stack.core.types.UaStructuredType
import org.eclipse.milo.opcua.stack.core.types.builtin.ExpandedNodeId
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.UInteger
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.Unsigned
import org.eclipse.milo.opcua.stack.core.types.structured.Union

class CustomUnionType private constructor(
    private val type: Type,
    private val value: Any?
) : Union(), UaStructuredType {

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

    fun asFoo(): UInteger? {
        return value as UInteger?
    }

    fun asBar(): String? {
        return value as String?
    }

    val isNull: Boolean
        get() = type == Type.Null

    val isFoo: Boolean
        get() = type == Type.Foo

    val isBar: Boolean
        get() = type == Type.Bar

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        if (!super.equals(other)) return false

        other as CustomUnionType

        if (type != other.type) return false
        if (value != other.value) return false

        return true
    }

    override fun hashCode(): Int {
        var result = super.hashCode()
        result = 31 * result + type.hashCode()
        result = 31 * result + (value?.hashCode() ?: 0)
        return result
    }

    override fun toString(): String {
        return MoreObjects.toStringHelper(this)
            .add("type", type)
            .add("value", value)
            .toString()
    }

    internal enum class Type {
        Null, Foo, Bar
    }

    class Codec : GenericDataTypeCodec<CustomUnionType>() {

        override fun getType(): Class<CustomUnionType> {
            return CustomUnionType::class.java
        }

        override fun decodeType(context: EncodingContext, decoder: UaDecoder): CustomUnionType {
            val switchValue = decoder.decodeUInt32("SwitchValue")

            return when (switchValue.toInt()) {
                0 -> ofNull()
                1 -> {
                    val foo = decoder.decodeUInt32("foo")
                    ofFoo(foo)
                }

                2 -> {
                    val bar = decoder.decodeString("bar")
                    ofBar(bar)
                }

                else -> throw UaSerializationException(
                    StatusCodes.Bad_DecodingError,
                    "unknown field in Union CustomUnionType: $switchValue"
                )
            }
        }

        override fun encodeType(
            context: EncodingContext,
            encoder: UaEncoder,
            value: CustomUnionType
        ) {

            encoder.encodeUInt32(
                "SwitchValue",
                Unsigned.uint(value.type.ordinal)
            )

            when (value.type) {
                Type.Null -> {
                    // noop
                }

                Type.Foo -> {
                    encoder.encodeUInt32("foo", value.asFoo())
                }

                Type.Bar -> {
                    encoder.encodeString("bar", value.asBar())
                }
            }
        }

    }

    companion object {
        val TYPE_ID: ExpandedNodeId = ExpandedNodeId.parse(
            String.format(
                "nsu=%s;s=%s",
                DemoNamespace.NAMESPACE_URI,
                "DataType.CustomUnionType"
            )
        )

        val BINARY_ENCODING_ID: ExpandedNodeId = ExpandedNodeId.parse(
            String.format(
                "nsu=%s;s=%s",
                DemoNamespace.NAMESPACE_URI,
                "DataType.CustomUnionType.BinaryEncoding"
            )
        )

        fun ofNull(): CustomUnionType {
            return CustomUnionType(Type.Null, null)
        }

        fun ofFoo(value: UInteger?): CustomUnionType {
            return CustomUnionType(Type.Foo, value)
        }

        fun ofBar(value: String?): CustomUnionType {
            return CustomUnionType(Type.Bar, value)
        }
    }

}

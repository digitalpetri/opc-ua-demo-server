package com.isw.opcua.milo.extensions

import org.eclipse.milo.opcua.sdk.core.Reference
import org.eclipse.milo.opcua.sdk.server.nodes.UaNode
import org.eclipse.milo.opcua.stack.core.BuiltinDataType
import org.eclipse.milo.opcua.stack.core.types.builtin.*
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.Unsigned
import java.util.*


fun NodeId.resolve(child: String): NodeId {
    val id = this.identifier.toString()
    return NodeId(this.namespaceIndex, "$id/$child")
}

fun UaNode.referenceTo(targetNodeId: NodeId, typeId: NodeId) {
    this.addReference(
        Reference(
            this.nodeId,
            typeId,
            targetNodeId.expanded(),
            true
        )
    )
}

fun UaNode.inverseReferenceTo(targetNodeId: NodeId, typeId: NodeId) {
    this.addReference(
        Reference(
            this.nodeId,
            typeId,
            targetNodeId.expanded(),
            false
        )
    )
}

fun BuiltinDataType.defaultValue(): Any {
    return when (this) {
        BuiltinDataType.Boolean -> false
        BuiltinDataType.SByte -> 0.toByte()
        BuiltinDataType.Int16 -> 0.toShort()
        BuiltinDataType.Int32 -> 0
        BuiltinDataType.Int64 -> 0L
        BuiltinDataType.Byte -> Unsigned.ubyte(0)
        BuiltinDataType.UInt16 -> Unsigned.ushort(0)
        BuiltinDataType.UInt32 -> Unsigned.uint(0)
        BuiltinDataType.UInt64 -> Unsigned.ulong(0)
        BuiltinDataType.Float -> 0f
        BuiltinDataType.Double -> 0.0
        BuiltinDataType.String -> ""
        BuiltinDataType.DateTime -> DateTime.NULL_VALUE
        BuiltinDataType.Guid -> UUID.randomUUID()
        BuiltinDataType.ByteString -> ByteString.NULL_VALUE
        BuiltinDataType.XmlElement -> XmlElement(null)
        BuiltinDataType.NodeId -> NodeId.NULL_VALUE
        BuiltinDataType.ExpandedNodeId -> ExpandedNodeId.NULL_VALUE
        BuiltinDataType.StatusCode -> StatusCode.GOOD
        BuiltinDataType.QualifiedName -> QualifiedName.NULL_VALUE
        BuiltinDataType.LocalizedText -> LocalizedText.NULL_VALUE
        BuiltinDataType.ExtensionObject -> ExtensionObject(ByteString.NULL_VALUE, NodeId.NULL_VALUE)
        BuiltinDataType.DataValue -> DataValue(Variant.NULL_VALUE)
        BuiltinDataType.Variant -> Variant.NULL_VALUE
        BuiltinDataType.DiagnosticInfo -> DiagnosticInfo.NULL_VALUE
    }
}

fun BuiltinDataType.defaultValueArray(length: Int = 5): Any {
    val value = defaultValue()
    val array = java.lang.reflect.Array.newInstance(this.backingClass, length)
    for (i in 0 until length) {
        java.lang.reflect.Array.set(array, i, value)
    }
    return array
}

package com.digitalpetri.opcua.server.types

import com.digitalpetri.opcua.server.namespaces.demo.DemoNamespace
import org.eclipse.milo.opcua.stack.core.types.UaEnumeratedType
import org.eclipse.milo.opcua.stack.core.types.builtin.ExpandedNodeId

enum class CustomEnumType(private val value: Int) : UaEnumeratedType {
    Field0(0),
    Field1(1),
    Field2(2);

    override fun getTypeId(): ExpandedNodeId {
        return TYPE_ID
    }

    override fun getValue(): Int {
        return value
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

package com.isw.opcua.server.namespaces.demo

import com.isw.opcua.milo.extensions.inverseReferenceTo
import org.eclipse.milo.opcua.sdk.core.AccessLevel
import org.eclipse.milo.opcua.sdk.server.nodes.UaFolderNode
import org.eclipse.milo.opcua.stack.core.BuiltinDataType
import org.eclipse.milo.opcua.stack.core.Identifiers
import org.eclipse.milo.opcua.stack.core.types.builtin.*
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.Unsigned


fun DemoNamespace.addNullValueNodes() {
    val dynamicFolder = UaFolderNode(
        server,
        NodeId(namespaceIndex, "NullValues"),
        QualifiedName(namespaceIndex, "NullValues"),
        LocalizedText("NullValues")
    )

    nodeManager.addNode(dynamicFolder)

    dynamicFolder.inverseReferenceTo(
        Identifiers.ObjectsFolder,
        Identifiers.HasComponent
    )

    val dataTypes = BuiltinDataType.values().filter {
        it != BuiltinDataType.DataValue && it != BuiltinDataType.Variant && it != BuiltinDataType.DiagnosticInfo
    }

    dataTypes.forEach { dataType ->
        val name = dataType.name

        addVariableNode(dynamicFolder.nodeId, name, dataType = dataType).apply {
            accessLevel = Unsigned.ubyte(AccessLevel.getMask(AccessLevel.READ_ONLY))
            userAccessLevel = Unsigned.ubyte(AccessLevel.getMask(AccessLevel.READ_ONLY))

            value = DataValue(Variant.NULL_VALUE)
        }
    }
}

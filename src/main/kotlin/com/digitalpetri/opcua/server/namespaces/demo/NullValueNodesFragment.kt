package com.digitalpetri.opcua.server.namespaces.demo

import com.digitalpetri.opcua.milo.extensions.inverseReferenceTo
import org.eclipse.milo.opcua.sdk.core.AccessLevel
import org.eclipse.milo.opcua.sdk.server.OpcUaServer
import org.eclipse.milo.opcua.sdk.server.api.AddressSpaceComposite
import org.eclipse.milo.opcua.sdk.server.nodes.UaFolderNode
import org.eclipse.milo.opcua.stack.core.BuiltinDataType
import org.eclipse.milo.opcua.stack.core.Identifiers
import org.eclipse.milo.opcua.stack.core.types.builtin.*
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.UShort

class NullValueNodesFragment(
    server: OpcUaServer,
    composite: AddressSpaceComposite,
    private val namespaceIndex: UShort
) : SampledAddressSpaceFragment(server, composite) {

    init {
        lifecycleManager.addStartupTask { addNullValueNodes() }
    }

    private fun addNullValueNodes() {
        val dynamicFolder = UaFolderNode(
            nodeContext,
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

            nodeContext.addVariableNode(dynamicFolder.nodeId, name, dataType = dataType).apply {
                accessLevel = AccessLevel.toValue(AccessLevel.READ_ONLY)
                userAccessLevel = AccessLevel.toValue(AccessLevel.READ_ONLY)

                value = DataValue(Variant.NULL_VALUE)
            }
        }
    }

}

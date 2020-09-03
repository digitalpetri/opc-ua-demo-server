package com.digitalpetri.opcua.server.namespaces.demo

import com.digitalpetri.opcua.milo.extensions.inverseReferenceTo
import org.eclipse.milo.opcua.sdk.core.AccessLevel
import org.eclipse.milo.opcua.sdk.server.OpcUaServer
import org.eclipse.milo.opcua.sdk.server.api.AddressSpaceComposite
import org.eclipse.milo.opcua.sdk.server.nodes.UaFolderNode
import org.eclipse.milo.opcua.sdk.server.nodes.filters.AttributeFilters
import org.eclipse.milo.opcua.stack.core.BuiltinDataType
import org.eclipse.milo.opcua.stack.core.Identifiers
import org.eclipse.milo.opcua.stack.core.types.builtin.*
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.UShort
import kotlin.random.Random

class DynamicNodesFragment(
    server: OpcUaServer,
    composite: AddressSpaceComposite,
    private val namespaceIndex: UShort
) : SampledAddressSpaceFragment(server, composite) {

    init {
        lifecycleManager.addStartupTask { addDynamicNodes() }
    }

    private fun addDynamicNodes() {
        val dynamicFolder = UaFolderNode(
            nodeContext,
            NodeId(namespaceIndex, "Dynamic"),
            QualifiedName(namespaceIndex, "Dynamic"),
            LocalizedText("Dynamic")
        )

        nodeManager.addNode(dynamicFolder)

        dynamicFolder.inverseReferenceTo(
            Identifiers.ObjectsFolder,
            Identifiers.HasComponent
        )

        addRandomNodes(dynamicFolder.nodeId)
    }

    private fun addRandomNodes(parentNodeId: NodeId) {
        nodeContext.addVariableNode(parentNodeId, "RandomInt32", dataType = BuiltinDataType.Int32).apply {
            accessLevel = AccessLevel.toValue(AccessLevel.READ_ONLY)
            userAccessLevel = AccessLevel.toValue(AccessLevel.READ_ONLY)

            filterChain.addLast(AttributeFilters.getValue {
                DataValue(
                    Variant(Random.nextInt()),
                    StatusCode.GOOD,
                    DateTime.nowNanos()
                )
            })
        }

        nodeContext.addVariableNode(parentNodeId, "RandomInt64", dataType = BuiltinDataType.Int64).apply {
            accessLevel = AccessLevel.toValue(AccessLevel.READ_ONLY)
            userAccessLevel = AccessLevel.toValue(AccessLevel.READ_ONLY)

            filterChain.addLast(AttributeFilters.getValue {
                DataValue(
                    Variant(Random.nextLong()),
                    StatusCode.GOOD,
                    DateTime.nowNanos()
                )
            })
        }

        nodeContext.addVariableNode(parentNodeId, "RandomFloat", dataType = BuiltinDataType.Float).apply {
            accessLevel = AccessLevel.toValue(AccessLevel.READ_ONLY)
            userAccessLevel = AccessLevel.toValue(AccessLevel.READ_ONLY)

            filterChain.addLast(AttributeFilters.getValue {
                DataValue(
                    Variant(Random.nextFloat()),
                    StatusCode.GOOD,
                    DateTime.nowNanos()
                )
            })
        }

        nodeContext.addVariableNode(parentNodeId, "RandomDouble", dataType = BuiltinDataType.Double).apply {
            accessLevel = AccessLevel.toValue(AccessLevel.READ_ONLY)
            userAccessLevel = AccessLevel.toValue(AccessLevel.READ_ONLY)

            filterChain.addLast(AttributeFilters.getValue {
                DataValue(
                    Variant(Random.nextDouble()),
                    StatusCode.GOOD,
                    DateTime.nowNanos()
                )
            })
        }
    }

}

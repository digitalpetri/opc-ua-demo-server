package com.digitalpetri.opcua.server.namespaces.demo

import com.digitalpetri.opcua.milo.extensions.defaultValue
import com.digitalpetri.opcua.milo.extensions.inverseReferenceTo
import com.digitalpetri.opcua.milo.extensions.resolve
import com.digitalpetri.opcua.server.namespaces.filters.AttributeLoggingFilter
import org.eclipse.milo.opcua.sdk.core.AccessLevel
import org.eclipse.milo.opcua.sdk.server.Lifecycle
import org.eclipse.milo.opcua.sdk.server.LifecycleManager
import org.eclipse.milo.opcua.sdk.server.OpcUaServer
import org.eclipse.milo.opcua.sdk.server.api.AddressSpaceComposite
import org.eclipse.milo.opcua.sdk.server.api.Namespace
import org.eclipse.milo.opcua.sdk.server.nodes.UaFolderNode
import org.eclipse.milo.opcua.sdk.server.nodes.UaNodeContext
import org.eclipse.milo.opcua.sdk.server.nodes.UaVariableNode
import org.eclipse.milo.opcua.stack.core.BuiltinDataType
import org.eclipse.milo.opcua.stack.core.Identifiers
import org.eclipse.milo.opcua.stack.core.types.builtin.*
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.UShort

class DemoNamespace(server: OpcUaServer) : AddressSpaceComposite(server), Namespace, Lifecycle {

    companion object {
        const val NAMESPACE_URI: String = "urn:eclipse:milo:opcua:server:demo"
    }

    private val lifecycleManager = LifecycleManager()

    private val namespaceIndex: UShort = server.namespaceTable.addUri(NAMESPACE_URI)

    init {
        lifecycleManager.addLifecycle(object : Lifecycle {
            override fun startup() {
                server.addressSpaceManager.register(this@DemoNamespace)
            }

            override fun shutdown() {
                server.addressSpaceManager.unregister(this@DemoNamespace)
            }
        })
    }

    override fun startup() {
        lifecycleManager.startup()
    }

    override fun shutdown() {
        lifecycleManager.shutdown()
    }

    override fun getNamespaceUri(): String {
        return NAMESPACE_URI
    }

    override fun getNamespaceIndex(): UShort {
        return namespaceIndex
    }

}


fun UaNodeContext.addVariableNode(
    parentNodeId: NodeId,
    name: String,
    nodeId: NodeId = parentNodeId.resolve(name),
    dataType: BuiltinDataType = BuiltinDataType.Int32,
    referenceTypeId: NodeId = Identifiers.HasComponent
): UaVariableNode {

    return addVariableNode(
        parentNodeId,
        name,
        nodeId,
        dataType.nodeId,
        dataType.defaultValue(),
        referenceTypeId
    )
}

fun UaNodeContext.addVariableNode(
    parentNodeId: NodeId,
    name: String,
    nodeId: NodeId = parentNodeId.resolve(name),
    dataTypeId: NodeId,
    value: Any,
    referenceTypeId: NodeId = Identifiers.HasComponent
): UaVariableNode {

    val variableNode = UaVariableNode.UaVariableNodeBuilder(this).run {
        setNodeId(nodeId)
        setAccessLevel(AccessLevel.toValue(AccessLevel.READ_WRITE))
        setUserAccessLevel(AccessLevel.toValue(AccessLevel.READ_WRITE))
        setBrowseName(QualifiedName(parentNodeId.namespaceIndex, name))
        setDisplayName(LocalizedText.english(name))
        setDataType(dataTypeId)
        setTypeDefinition(Identifiers.BaseDataVariableType)
        setMinimumSamplingInterval(100.0)
        setValue(DataValue(Variant(value)))

        build()
    }

    variableNode.filterChain.addFirst(AttributeLoggingFilter())

    nodeManager.addNode(variableNode)

    variableNode.inverseReferenceTo(
        parentNodeId,
        referenceTypeId
    )

    return variableNode
}

fun UaNodeContext.addFolderNode(parentNodeId: NodeId, name: String): UaFolderNode {
    val folderNode = UaFolderNode(
        this,
        parentNodeId.resolve(name),
        QualifiedName(parentNodeId.namespaceIndex, name),
        LocalizedText(name)
    )

    nodeManager.addNode(folderNode)

    folderNode.inverseReferenceTo(
        parentNodeId,
        Identifiers.HasComponent
    )

    return folderNode
}


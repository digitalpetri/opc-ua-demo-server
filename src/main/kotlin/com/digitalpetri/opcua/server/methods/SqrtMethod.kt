package com.digitalpetri.opcua.server.methods

import org.eclipse.milo.opcua.sdk.core.ValueRanks
import org.eclipse.milo.opcua.sdk.server.methods.AbstractMethodInvocationHandler
import org.eclipse.milo.opcua.sdk.server.nodes.UaMethodNode
import org.eclipse.milo.opcua.stack.core.NodeIds
import org.eclipse.milo.opcua.stack.core.types.builtin.LocalizedText
import org.eclipse.milo.opcua.stack.core.types.builtin.Variant
import org.eclipse.milo.opcua.stack.core.types.structured.Argument
import org.slf4j.LoggerFactory
import kotlin.math.sqrt

class SqrtMethod(node: UaMethodNode) : AbstractMethodInvocationHandler(node) {

    private val logger = LoggerFactory.getLogger(javaClass)

    override fun getInputArguments(): Array<Argument> {
        return arrayOf(X)
    }

    override fun getOutputArguments(): Array<Argument> {
        return arrayOf(X_SQRT)
    }

    override fun invoke(
        invocationContext: InvocationContext,
        inputValues: Array<Variant>
    ): Array<Variant> {

        logger.debug("Invoking sqrt() method of objectId={}", invocationContext.objectId)

        val x = inputValues[0].value as Double
        val xSqrt = sqrt(x)

        return arrayOf(Variant(xSqrt))
    }

    companion object {

        val X = Argument(
            "x",
            NodeIds.Double,
            ValueRanks.Scalar,
            null,
            LocalizedText("A value.")
        )

        val X_SQRT = Argument(
            "x_sqrt",
            NodeIds.Double,
            ValueRanks.Scalar, null,
            LocalizedText("A value.")
        )

    }

}

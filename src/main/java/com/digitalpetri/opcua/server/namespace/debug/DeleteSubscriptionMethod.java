package com.digitalpetri.opcua.server.namespace.debug;

import org.eclipse.milo.opcua.sdk.core.ValueRanks;
import org.eclipse.milo.opcua.sdk.server.Session;
import org.eclipse.milo.opcua.sdk.server.methods.AbstractMethodInvocationHandler;
import org.eclipse.milo.opcua.sdk.server.nodes.UaMethodNode;
import org.eclipse.milo.opcua.sdk.server.subscriptions.Subscription;
import org.eclipse.milo.opcua.stack.core.NodeIds;
import org.eclipse.milo.opcua.stack.core.StatusCodes;
import org.eclipse.milo.opcua.stack.core.UaException;
import org.eclipse.milo.opcua.stack.core.types.builtin.Variant;
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.UInteger;
import org.eclipse.milo.opcua.stack.core.types.structured.Argument;

public class DeleteSubscriptionMethod extends AbstractMethodInvocationHandler {

  public static final Argument SUBSCRIPTION_ID =
      new Argument("SubscriptionId", NodeIds.UInt32, ValueRanks.Scalar, null, null);

  public DeleteSubscriptionMethod(UaMethodNode node) {
    super(node);
  }

  @Override
  public Argument[] getInputArguments() {
    return new Argument[] {SUBSCRIPTION_ID};
  }

  @Override
  public Argument[] getOutputArguments() {
    return new Argument[0];
  }

  @Override
  protected Variant[] invoke(InvocationContext invocationContext, Variant[] inputValues)
      throws UaException {

    Session session = invocationContext.getSession().orElseThrow();

    Object iv0 = inputValues[0].getValue();

    if (iv0 instanceof UInteger subscriptionId) {
      Subscription subscription =
          session.getSubscriptionManager().removeSubscription(subscriptionId);

      if (subscription != null) {
        subscription.deleteSubscription();
        return new Variant[0];
      } else {
        throw new UaException(StatusCodes.Bad_SubscriptionIdInvalid);
      }
    } else {
      throw new UaException(StatusCodes.Bad_InvalidArgument);
    }
  }
}

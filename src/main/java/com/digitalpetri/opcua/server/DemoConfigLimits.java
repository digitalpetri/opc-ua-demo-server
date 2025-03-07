package com.digitalpetri.opcua.server;

import static org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.Unsigned.uint;

import org.eclipse.milo.opcua.sdk.server.OpcUaServerConfigLimits;
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.UInteger;

public class DemoConfigLimits implements OpcUaServerConfigLimits {

  @Override
  public Double getMinPublishingInterval() {
    return 100.0;
  }

  @Override
  public Double getDefaultPublishingInterval() {
    return 100.0;
  }

  @Override
  public UInteger getMaxSessions() {
    return uint(200);
  }

  @Override
  public Double getMaxSessionTimeout() {
    return 30_000.0;
  }

  @Override
  public UInteger getMaxMonitoredItems() {
    return uint(500_000);
  }

  @Override
  public UInteger getMaxMonitoredItemsPerSession() {
    return uint(100_000);
  }
}

package com.digitalpetri.opcua.server;

import static org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.Unsigned.uint;

import org.eclipse.milo.opcua.sdk.server.OpcUaServerConfigLimits;
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.UInteger;

public class DemoConfigLimits implements OpcUaServerConfigLimits {

  /**
   * The fastest sampling interval the server supports, in milliseconds.
   *
   * <p>Milo's sampling framework revises every requested interval up to a multiple of its default
   * 25 ms bucket, so nothing samples faster than this. Variables that can be sampled as fast as the
   * server allows report it as their MinimumSamplingInterval.
   */
  public static final double MIN_SUPPORTED_SAMPLE_RATE = 25.0;

  @Override
  public Double getMinSupportedSampleRate() {
    return MIN_SUPPORTED_SAMPLE_RATE;
  }

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
    return uint(500);
  }

  @Override
  public Double getMaxSessionTimeout() {
    return 300_000.0;
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

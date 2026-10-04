package com.digitalpetri.opcua.server;

import java.util.List;

/** Capabilities shared by push management and the GDS directory record. */
public final class DemoServerCapabilities {
  public static final List<String> VALUES = List.of("DA", "AC");

  private DemoServerCapabilities() {}
}

package com.digitalpetri.opcua.server.namespace.demo;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class MassNodesFragmentTest {

  // A quantity of 1 used to compute log10(0) and produce "%0-2147483647d", which crashed server
  // startup with IllegalFormatFlagsException (#47). Quantities 0 and 1 must yield a usable format.
  @Test
  void nameFormatHandlesQuantitiesBelowTwo() {
    assertEquals("%01d", MassNodesFragment.nameFormat(0));
    assertEquals("%01d", MassNodesFragment.nameFormat(1));
    assertEquals("0", MassNodesFragment.nameFormat(1).formatted(0));
  }

  // Width follows the largest index (quantity - 1), so 10 nodes (0..9) need one digit and 11 nodes
  // (0..10) need two.
  @Test
  void nameFormatPadsToWidthOfLargestIndex() {
    assertEquals("%01d", MassNodesFragment.nameFormat(2));
    assertEquals("%01d", MassNodesFragment.nameFormat(10));
    assertEquals("%02d", MassNodesFragment.nameFormat(11));
    assertEquals("00", MassNodesFragment.nameFormat(11).formatted(0));
    assertEquals("10", MassNodesFragment.nameFormat(11).formatted(10));
  }
}

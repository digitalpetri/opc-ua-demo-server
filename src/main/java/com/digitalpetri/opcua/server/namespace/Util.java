package com.digitalpetri.opcua.server.namespace;

import org.eclipse.milo.opcua.stack.core.types.builtin.NodeId;

public class Util {

  private Util() {}

  /**
   * Derives a child NodeId from a parent NodeId and a name.
   *
   * <p>The derived NodeId will have the same namespace index as the parent NodeId, and its
   * identifier will be a concatenation of the parent's identifier and the provided name, separated
   * by a dot.
   *
   * @param parentNodeId the parent NodeId.
   * @param name the name to derive the child NodeId from.
   * @return the derived child NodeId.
   */
  public static NodeId deriveChildNodeId(NodeId parentNodeId, String name) {
    return new NodeId(
        parentNodeId.getNamespaceIndex(), "%s.%s".formatted(parentNodeId.getIdentifier(), name));
  }
}

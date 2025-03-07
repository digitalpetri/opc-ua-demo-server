package com.digitalpetri.opcua.server.objects;

import java.util.Collections;
import org.eclipse.milo.opcua.sdk.server.Session;
import org.eclipse.milo.opcua.sdk.server.nodes.filters.AttributeFilter;
import org.eclipse.milo.opcua.sdk.server.nodes.filters.AttributeFilterContext;
import org.eclipse.milo.opcua.stack.core.AttributeId;
import org.eclipse.milo.opcua.stack.core.NodeIds;

public class SecurityAdminFilter implements AttributeFilter {

  @Override
  public Object getAttribute(AttributeFilterContext ctx, AttributeId attributeId) {
    return switch (attributeId) {
      case Executable -> true;

      case UserExecutable ->
          ctx.getSession()
              .flatMap(Session::getRoleIds)
              .orElse(Collections.emptyList())
              .contains(NodeIds.WellKnownRole_SecurityAdmin);

      default -> ctx.getAttribute(attributeId);
    };
  }
}

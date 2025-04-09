package com.digitalpetri.opcua.server.namespace.demo;

import com.typesafe.config.Config;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import org.eclipse.milo.opcua.sdk.core.AccessLevel;
import org.eclipse.milo.opcua.sdk.server.Session;
import org.eclipse.milo.opcua.sdk.server.nodes.filters.AttributeFilter;
import org.eclipse.milo.opcua.sdk.server.nodes.filters.AttributeFilterContext;
import org.eclipse.milo.opcua.stack.core.AttributeId;
import org.eclipse.milo.opcua.stack.core.types.builtin.NodeId;
import org.eclipse.milo.opcua.stack.core.types.structured.PermissionType;
import org.eclipse.milo.opcua.stack.core.types.structured.PermissionType.Field;
import org.eclipse.milo.opcua.stack.core.types.structured.RolePermissionType;
import org.jspecify.annotations.Nullable;

public class AccessControlFilter implements AttributeFilter {

  private final List<RolePermissionType> rolePermissions;

  public AccessControlFilter(Config config, String key) {
    this.rolePermissions =
        config.getConfigList(key).stream()
            .map(
                roleConfig -> {
                  String roleIdString = roleConfig.getString("role-id");
                  List<String> permissionsString = roleConfig.getStringList("permissions");

                  NodeId roleId = NodeId.parse(roleIdString);
                  Field[] permissions =
                      permissionsString.stream().map(Field::valueOf).toArray(Field[]::new);

                  return new RolePermissionType(roleId, PermissionType.of(permissions));
                })
            .toList();
  }

  @Override
  public @Nullable Object getAttribute(AttributeFilterContext ctx, AttributeId attributeId) {
    return switch (attributeId) {
      case UserAccessLevel -> {
        Session session = ctx.getSession().orElseThrow();

        List<RolePermissionType> rolePermissions = getSessionRolePermissions(session);

        var accessLevels = new HashSet<AccessLevel>();
        if (rolePermissions.stream().anyMatch(rpt -> rpt.getPermissions().getRead())) {
          accessLevels.add(AccessLevel.CurrentRead);
        }
        if (rolePermissions.stream().anyMatch(rpt -> rpt.getPermissions().getWrite())) {
          accessLevels.add(AccessLevel.CurrentWrite);
        }

        yield AccessLevel.toValue(accessLevels);
      }
      case UserExecutable -> {
        Session session = ctx.getSession().orElseThrow();

        List<RolePermissionType> rolePermissions = getSessionRolePermissions(session);

        yield rolePermissions.stream().anyMatch(rpt -> rpt.getPermissions().getCall());
      }
      case RolePermissions -> rolePermissions.toArray(new RolePermissionType[0]);
      case UserRolePermissions -> {
        Session session = ctx.getSession().orElseThrow();
        List<RolePermissionType> rolePermissions = getSessionRolePermissions(session);

        yield rolePermissions.toArray(new RolePermissionType[0]);
      }
      default -> ctx.getAttribute(attributeId);
    };
  }

  private List<RolePermissionType> getSessionRolePermissions(Session session) {
    List<NodeId> roleIds = session.getRoleIds().orElse(Collections.emptyList());

    return rolePermissions.stream().filter(rpt -> roleIds.contains(rpt.getRoleId())).toList();
  }
}

package com.digitalpetri.opcua.server.namespace.demo;

import static org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.Unsigned.uint;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import org.eclipse.milo.opcua.sdk.core.AccessLevel;
import org.eclipse.milo.opcua.sdk.server.Session;
import org.eclipse.milo.opcua.sdk.server.nodes.filters.AttributeFilter;
import org.eclipse.milo.opcua.sdk.server.nodes.filters.AttributeFilterContext;
import org.eclipse.milo.opcua.stack.core.AttributeId;
import org.eclipse.milo.opcua.stack.core.types.builtin.NodeId;
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.UByte;
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.UInteger;
import org.eclipse.milo.opcua.stack.core.types.structured.RolePermissionType;
import org.jspecify.annotations.Nullable;

/**
 * An attribute filter that implements OPC UA role-based access control (RBAC).
 *
 * <p>This filter computes user-specific attribute values based on the session's assigned roles and
 * the node's role permissions. It handles the following attributes:
 *
 * <ul>
 *   <li>RolePermissions
 *   <li>UserRolePermissions
 *   <li>UserAccessLevel
 *   <li>UserExecutable
 *   <li>UserWriteMask
 * </ul>
 */
public class AccessControlFilter implements AttributeFilter {

  private final Supplier<RolePermissionType[]> getRolePermissions;

  /**
   * Creates an access control filter with static role permissions.
   *
   * @param rolePermissions the role permissions for the node
   */
  public AccessControlFilter(RolePermissionType[] rolePermissions) {
    this(() -> rolePermissions);
  }

  /**
   * Creates an access control filter with dynamic role permissions.
   *
   * @param getRolePermissions a supplier that provides the node's role permissions
   */
  public AccessControlFilter(Supplier<RolePermissionType[]> getRolePermissions) {
    this.getRolePermissions = getRolePermissions;
  }

  @Override
  public @Nullable Object getAttribute(AttributeFilterContext ctx, AttributeId attributeId) {
    return switch (attributeId) {
      case RolePermissions -> getRolePermissions.get();

      case UserRolePermissions -> {
        Session session = ctx.getSession().orElseThrow();

        RolePermissionType[] rolePermissions =
            (RolePermissionType[])
                ctx.getNode().getAttribute(() -> Optional.of(session), AttributeId.RolePermissions);

        yield calculateUserRolePermissions(rolePermissions, session);
      }

      case UserAccessLevel -> {
        Session session = ctx.getSession().orElseThrow();

        UByte accessLevel =
            (UByte) ctx.getNode().getAttribute(() -> Optional.of(session), AttributeId.AccessLevel);

        RolePermissionType[] userRolePermissions =
            (RolePermissionType[])
                ctx.getNode()
                    .getAttribute(() -> Optional.of(session), AttributeId.UserRolePermissions);

        yield calculateUserAccessLevel(accessLevel, userRolePermissions);
      }

      case UserExecutable -> {
        Session session = ctx.getSession().orElseThrow();

        Boolean executable =
            (Boolean)
                ctx.getNode().getAttribute(() -> Optional.of(session), AttributeId.Executable);

        RolePermissionType[] userRolePermissions =
            (RolePermissionType[])
                ctx.getNode()
                    .getAttribute(() -> Optional.of(session), AttributeId.UserRolePermissions);

        yield calculateUserExecutable(executable, userRolePermissions);
      }

      case UserWriteMask -> {
        Session session = ctx.getSession().orElseThrow();

        UInteger writeMask =
            (UInteger)
                ctx.getNode().getAttribute(() -> Optional.of(session), AttributeId.WriteMask);

        RolePermissionType[] userRolePermissions =
            (RolePermissionType[])
                ctx.getNode()
                    .getAttribute(() -> Optional.of(session), AttributeId.UserRolePermissions);

        yield calculateUserWriteMask(writeMask, userRolePermissions);
      }

      default -> ctx.getAttribute(attributeId);
    };
  }

  private static RolePermissionType[] calculateUserRolePermissions(
      RolePermissionType[] rolePermissions, Session session) {

    return calculateUserRolePermissions(rolePermissions, session.getRoleIds().orElse(List.of()));
  }

  /**
   * Calculates the UserRolePermissions by filtering to only those matching the user's assigned role
   * IDs.
   *
   * @param rolePermissions all role permissions defined for the node.
   * @param roleIds the role IDs assigned to the user's session.
   * @return the subset of role permissions applicable to the user.
   */
  static RolePermissionType[] calculateUserRolePermissions(
      RolePermissionType[] rolePermissions, List<NodeId> roleIds) {

    return Arrays.stream(rolePermissions)
        .filter(rpt -> roleIds.contains(rpt.getRoleId()))
        .toArray(RolePermissionType[]::new);
  }

  /**
   * Calculates the UserAccessLevel based on role permissions.
   *
   * <p>Maps OPC UA permissions to access level flags:
   *
   * <ul>
   *   <li>Read permission → CurrentRead
   *   <li>Write permission → CurrentWrite
   *   <li>ReadHistory permission → HistoryRead
   *   <li>InsertHistory/ModifyHistory/DeleteHistory → HistoryWrite
   * </ul>
   *
   * @param accessLevel the node's base access level.
   * @param userRolePermissions the user's applicable role permissions.
   * @return the computed UserAccessLevel.
   */
  static UByte calculateUserAccessLevel(
      UByte accessLevel, RolePermissionType[] userRolePermissions) {

    if (accessLevel == null || accessLevel.intValue() == 0) {
      return UByte.MIN;
    } else {
      Set<AccessLevel> accessLevels = AccessLevel.fromValue(accessLevel);
      Set<AccessLevel> userAccessLevels = new HashSet<>();

      if (accessLevels.contains(AccessLevel.CurrentRead)
          && Arrays.stream(userRolePermissions).anyMatch(rpt -> rpt.getPermissions().getRead())) {

        userAccessLevels.add(AccessLevel.CurrentRead);
      }

      if (accessLevels.contains(AccessLevel.CurrentWrite)
          && Arrays.stream(userRolePermissions).anyMatch(rpt -> rpt.getPermissions().getWrite())) {

        userAccessLevels.add(AccessLevel.CurrentWrite);
      }

      if (accessLevels.contains(AccessLevel.HistoryRead)
          && Arrays.stream(userRolePermissions)
              .anyMatch(rpt -> rpt.getPermissions().getReadHistory())) {

        userAccessLevels.add(AccessLevel.HistoryRead);
      }

      if (accessLevels.contains(AccessLevel.HistoryWrite)
          && (Arrays.stream(userRolePermissions)
                  .anyMatch(rpt -> rpt.getPermissions().getInsertHistory())
              || Arrays.stream(userRolePermissions)
                  .anyMatch(rpt -> rpt.getPermissions().getModifyHistory())
              || Arrays.stream(userRolePermissions)
                  .anyMatch(rpt -> rpt.getPermissions().getDeleteHistory()))) {

        userAccessLevels.add(AccessLevel.HistoryWrite);
      }

      return AccessLevel.toValue(userAccessLevels);
    }
  }

  /**
   * Calculates the UserExecutable attribute based on role permissions.
   *
   * @param executable the node's base executable flag.
   * @param userRolePermissions the user's applicable role permissions.
   * @return true if the user has Call permission and the method is executable.
   */
  static boolean calculateUserExecutable(
      Boolean executable, RolePermissionType[] userRolePermissions) {

    if (executable == null || !executable) {
      return false;
    } else {
      return Arrays.stream(userRolePermissions).anyMatch(rpt -> rpt.getPermissions().getCall());
    }
  }

  /**
   * Calculates the UserWriteMask based on role permissions.
   *
   * <p>Controls which attributes can be written:
   *
   * <ul>
   *   <li>WriteAttribute permission → all attribute bits (except Historizing and RolePermissions)
   *   <li>WriteHistorizing permission → Historizing bit (bit 9)
   *   <li>WriteRolePermissions permission → RolePermissions bit (bit 23)
   * </ul>
   *
   * @param writeMask the node's base write mask.
   * @param userRolePermissions the user's applicable role permissions.
   * @return the computed UserWriteMask.
   */
  static UInteger calculateUserWriteMask(
      UInteger writeMask, RolePermissionType[] userRolePermissions) {

    if (writeMask == null || writeMask.intValue() == 0) {
      return UInteger.MIN;
    } else {
      int writeMaskInt = writeMask.intValue();

      if (Arrays.stream(userRolePermissions)
          .noneMatch(rpt -> rpt.getPermissions().getWriteAttribute())) {

        // disable all bits except Historizing and RolePermissions, which are controlled by separate
        // permissions.
        writeMaskInt &= ((1 << 9) | (1 << 23));
      }

      if (Arrays.stream(userRolePermissions)
          .noneMatch(rpt -> rpt.getPermissions().getWriteHistorizing())) {

        // disable Historizing bit.
        writeMaskInt &= ~(1 << 9);
      }

      if (Arrays.stream(userRolePermissions)
          .noneMatch(rpt -> rpt.getPermissions().getWriteRolePermissions())) {

        // disable RolePermissions bit.
        writeMaskInt &= ~(1 << 23);
      }

      return uint(writeMaskInt);
    }
  }
}

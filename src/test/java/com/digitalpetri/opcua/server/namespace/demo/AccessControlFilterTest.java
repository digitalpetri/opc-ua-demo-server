package com.digitalpetri.opcua.server.namespace.demo;

import static com.digitalpetri.opcua.server.namespace.demo.AccessControlFilter.calculateUserAccessLevel;
import static com.digitalpetri.opcua.server.namespace.demo.AccessControlFilter.calculateUserExecutable;
import static com.digitalpetri.opcua.server.namespace.demo.AccessControlFilter.calculateUserRolePermissions;
import static com.digitalpetri.opcua.server.namespace.demo.AccessControlFilter.calculateUserWriteMask;
import static org.eclipse.milo.opcua.sdk.core.AccessLevel.CurrentRead;
import static org.eclipse.milo.opcua.sdk.core.AccessLevel.CurrentWrite;
import static org.eclipse.milo.opcua.sdk.core.AccessLevel.HistoryRead;
import static org.eclipse.milo.opcua.sdk.core.AccessLevel.HistoryWrite;
import static org.eclipse.milo.opcua.sdk.core.AccessLevel.fromValue;
import static org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.Unsigned.uint;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;
import org.eclipse.milo.opcua.sdk.core.AccessLevel;
import org.eclipse.milo.opcua.stack.core.types.builtin.NodeId;
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.UByte;
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.UInteger;
import org.eclipse.milo.opcua.stack.core.types.structured.PermissionType;
import org.eclipse.milo.opcua.stack.core.types.structured.PermissionType.Field;
import org.eclipse.milo.opcua.stack.core.types.structured.RolePermissionType;
import org.junit.jupiter.api.Test;

class AccessControlFilterTest {

  // region UserRolePermissions

  @Test
  void calculateUserRolePermissions_onlyIncludesMatchingRoles() {
    RolePermissionType[] base =
        new RolePermissionType[] {
          rpt("RoleA", Field.Read), rpt("RoleB", Field.Write), rpt("RoleC", Field.Call)
        };

    List<NodeId> userRoles = List.of(NodeId.parse("ns=2;s=RoleA"), NodeId.parse("ns=2;s=RoleC"));

    RolePermissionType[] actual = calculateUserRolePermissions(base, userRoles);

    RolePermissionType[] expected =
        new RolePermissionType[] {rpt("RoleA", Field.Read), rpt("RoleC", Field.Call)};

    // Equal set and order preserved for matching entries
    assertArrayEquals(expected, actual);
  }

  @Test
  void calculateUserRolePermissions_neverAddsNewOrElevatesPermissions() {
    // User has a role id that is NOT present in base permissions
    RolePermissionType[] base = new RolePermissionType[] {rpt("RoleA", Field.Read)};

    List<NodeId> userRoles = List.of(NodeId.parse("ns=2;s=RoleA"), NodeId.parse("ns=2;s=RoleZ"));

    RolePermissionType[] actual = calculateUserRolePermissions(base, userRoles);

    // Ensure only RoleA from base is present; RoleZ must not be added.
    assertEquals(1, actual.length);
    assertEquals(NodeId.parse("ns=2;s=RoleA"), actual[0].getRoleId());

    // Ensure permissions are exactly the same as base (no elevation)
    PermissionType basePermissions = base[0].getPermissions();
    PermissionType actualPermissions = actual[0].getPermissions();
    assertEquals(basePermissions, actualPermissions);
  }

  // endregion

  // region UserAccessLevel

  @Test
  void calculateUserAccessLevel_nullOrZeroBaseResultsInZero() {
    RolePermissionType[] userPermissions =
        new RolePermissionType[] {rpt("RoleA", Field.Read, Field.Write)};

    UByte resultNull = calculateUserAccessLevel(null, userPermissions);
    UByte resultZero = calculateUserAccessLevel(UByte.MIN, userPermissions);

    assertEquals(0, resultNull.intValue());
    assertEquals(0, resultZero.intValue());
  }

  @Test
  void calculateUserAccessLevel_intersectionWithUserPermissions() {
    // Base: Read + Write + HistoryRead + HistoryWrite
    UByte base = AccessLevel.toValue(Set.of(CurrentRead, CurrentWrite, HistoryRead, HistoryWrite));

    // User: has only Read and Write (no history access)
    RolePermissionType[] userPermissions =
        new RolePermissionType[] {rpt("RoleA", Field.Read, Field.Write)};

    UByte userLevel = calculateUserAccessLevel(base, userPermissions);
    Set<AccessLevel> levels = fromValue(userLevel);

    // Must include Read and Write
    assertEquals(Set.of(CurrentRead, CurrentWrite), levels);
  }

  @Test
  void calculateUserAccessLevel_neverElevatesBeyondBase() {
    // Base: only Read access
    UByte base = AccessLevel.toValue(Set.of(CurrentRead));

    // User has Write access, but base AccessLevel does not allow it
    RolePermissionType[] userPerms = new RolePermissionType[] {rpt("RoleA", Field.Write)};

    UByte userLevel = calculateUserAccessLevel(base, userPerms);
    Set<AccessLevel> levels = fromValue(userLevel);

    // Only Read can be present at most; since user lacks Read, should be empty
    assertEquals(Set.of(), levels);
  }

  @Test
  void calculateUserAccessLevel_historyWriteRequiresAnyOfInsertModifyDelete() {
    // Base includes HistoryWrite
    UByte base = AccessLevel.toValue(Set.of(HistoryWrite));

    // 1) User has InsertHistory -> allowed
    RolePermissionType[] u1 = new RolePermissionType[] {rpt("RoleA", Field.InsertHistory)};
    // 2) User has ModifyHistory -> allowed
    RolePermissionType[] u2 = new RolePermissionType[] {rpt("RoleA", Field.ModifyHistory)};
    // 3) User has DeleteHistory -> allowed
    RolePermissionType[] u3 = new RolePermissionType[] {rpt("RoleA", Field.DeleteHistory)};
    // 4) User has none -> not allowed
    RolePermissionType[] u4 = new RolePermissionType[] {rpt("RoleA", Field.Read)};

    Set<AccessLevel> l1 = fromValue(calculateUserAccessLevel(base, u1));
    Set<AccessLevel> l2 = fromValue(calculateUserAccessLevel(base, u2));
    Set<AccessLevel> l3 = fromValue(calculateUserAccessLevel(base, u3));
    Set<AccessLevel> l4 = fromValue(calculateUserAccessLevel(base, u4));

    assertEquals(Set.of(HistoryWrite), l1);
    assertEquals(Set.of(HistoryWrite), l2);
    assertEquals(Set.of(HistoryWrite), l3);
    assertEquals(Set.of(), l4);
  }

  @Test
  void calculateUserAccessLevel_handlesMixedCombinations() {
    // Base: Read + Write + HistoryRead
    UByte base = AccessLevel.toValue(Set.of(CurrentRead, CurrentWrite, HistoryRead));

    // User: ReadHistory only -> should get only HistoryRead
    RolePermissionType[] u1 = new RolePermissionType[] {rpt("RoleA", Field.ReadHistory)};
    Set<AccessLevel> l1 = fromValue(calculateUserAccessLevel(base, u1));
    assertEquals(Set.of(HistoryRead), l1);

    // User: Read + Write + DeleteHistory, but base lacks HistoryWrite -> only Read and Write
    // allowed
    RolePermissionType[] u2 =
        new RolePermissionType[] {rpt("RoleA", Field.Read, Field.Write, Field.DeleteHistory)};
    Set<AccessLevel> l2 = fromValue(calculateUserAccessLevel(base, u2));
    assertEquals(Set.of(CurrentRead, CurrentWrite), l2);
  }

  // endregion

  // region UserExecutable

  @Test
  void calculateUserExecutable_nullOrFalseBaseIsFalse() {
    RolePermissionType[] userPerms = new RolePermissionType[] {rpt("RoleA", Field.Call)};

    //noinspection ConstantValue
    boolean r1 = calculateUserExecutable(null, userPerms);
    boolean r2 = calculateUserExecutable(Boolean.FALSE, userPerms);

    //noinspection ConstantValue
    assertFalse(r1);
    assertFalse(r2);
  }

  @Test
  void calculateUserExecutable_trueBaseButNoCallPermission_false() {
    // Base Executable is true, but User lacks Call permission -> must be false
    RolePermissionType[] userPerms = new RolePermissionType[] {rpt("RoleA", Field.Read)};

    boolean result = calculateUserExecutable(Boolean.TRUE, userPerms);
    assertFalse(result);
  }

  @Test
  void calculateUserExecutable_trueBaseWithCallPermission_true() {
    // Base Executable is true and user has Call permission -> true
    RolePermissionType[] userPerms = new RolePermissionType[] {rpt("RoleA", Field.Call)};

    boolean result = calculateUserExecutable(Boolean.TRUE, userPerms);
    assertTrue(result);
  }

  // endregion

  // region UserWriteMask

  @Test
  void calculateUserWriteMask_nullOrZeroBaseResultsInZero() {
    RolePermissionType[] userPermissions =
        new RolePermissionType[] {rpt("RoleA", Field.WriteAttribute)};

    assertEquals(0, calculateUserWriteMask(null, userPermissions).intValue());
    assertEquals(0, calculateUserWriteMask(uint(0), userPermissions).intValue());
  }

  @Test
  void calculateUserWriteMask_cannotExceedBaseMask_writeAttributeOff() {
    // Base mask has multiple bits set (including some besides Historizing and RolePermissions)
    int base = (1) | (1 << 1) | (1 << 9) | (1 << 23); // some bits ON

    // User lacks WriteAttribute permission -> only Historizing and RolePermissions may remain,
    // and only if the base had them set. This ensures the mask is reduced, never elevated.
    RolePermissionType[] noPermissions = new RolePermissionType[] {rpt("RoleA")};
    RolePermissionType[] writeHistorizingAndRolePermissions =
        new RolePermissionType[] {rpt("RoleA", Field.WriteHistorizing, Field.WriteRolePermissions)};

    assertEquals(uint(0), calculateUserWriteMask(uint(base), noPermissions));
    assertEquals(
        uint((1 << 9) | (1 << 23)),
        calculateUserWriteMask(uint(base), writeHistorizingAndRolePermissions));
  }

  @Test
  void calculateUserWriteMask_historizingAndRolePermissionsControlledSeparately() {
    int base = (1 << 9) | (1 << 23);

    // Case 1: User has neither -> expect 0
    RolePermissionType[] none = new RolePermissionType[] {rpt("RoleA")};
    assertEquals(0, calculateUserWriteMask(uint(base), none).intValue());

    // Case 2: Only WriteHistorizing -> expect bit 9
    RolePermissionType[] writeHistorizing =
        new RolePermissionType[] {rpt("RoleA", Field.WriteHistorizing)};
    assertEquals((1 << 9), calculateUserWriteMask(uint(base), writeHistorizing).intValue());

    // Case 3: Only WriteRolePermissions -> expect bit 23
    RolePermissionType[] writeRolePermissions =
        new RolePermissionType[] {rpt("RoleA", Field.WriteRolePermissions)};
    assertEquals((1 << 23), calculateUserWriteMask(uint(base), writeRolePermissions).intValue());

    // Case 4: Both -> expect both bits
    RolePermissionType[] both =
        new RolePermissionType[] {rpt("RoleA", Field.WriteHistorizing, Field.WriteRolePermissions)};
    assertEquals((1 << 9) | (1 << 23), calculateUserWriteMask(uint(base), both).intValue());
  }

  @Test
  void calculateUserWriteMask_withWriteAttributeUserCanKeepBaseBitsOnly() {
    // Base turns on a few arbitrary bits, including ones other than 9 and 23
    int base = (1) | (1 << 2) | (1 << 5);

    // User has WriteAttribute -> they can retain the base bits but never add new ones
    RolePermissionType[] userRolePermissions =
        new RolePermissionType[] {rpt("RoleA", Field.WriteAttribute)};

    UInteger result = calculateUserWriteMask(uint(base), userRolePermissions);

    assertEquals(base, result.intValue());
  }

  // endregion

  private static RolePermissionType rpt(String id, Field... fields) {
    NodeId roleId = NodeId.parse("ns=2;s=" + id);
    return new RolePermissionType(roleId, PermissionType.of(fields));
  }
}

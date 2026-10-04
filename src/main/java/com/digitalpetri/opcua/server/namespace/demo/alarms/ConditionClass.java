package com.digitalpetri.opcua.server.namespace.demo.alarms;

import org.eclipse.milo.opcua.stack.core.NodeIds;
import org.eclipse.milo.opcua.stack.core.types.builtin.LocalizedText;
import org.eclipse.milo.opcua.stack.core.types.builtin.NodeId;

/**
 * The kind of plant concern an alarm reports, which lets a client narrow a mixed alarm view to e.g.
 * process upsets or maintenance work.
 *
 * <p>Pairing the ConditionClassId with its ConditionClassName keeps the two from disagreeing, which
 * a client filtering on one and displaying the other would surface as a mislabelled alarm.
 */
enum ConditionClass {
  PROCESS(NodeIds.ProcessConditionClassType, "ProcessConditionClass"),

  MAINTENANCE(NodeIds.MaintenanceConditionClassType, "MaintenanceConditionClass"),

  SYSTEM(NodeIds.SystemConditionClassType, "SystemConditionClass");

  private final NodeId typeId;
  private final LocalizedText displayName;

  ConditionClass(NodeId typeId, String displayName) {
    this.typeId = typeId;
    this.displayName = LocalizedText.english(displayName);
  }

  /**
   * Get the ConditionClassId, the {@link NodeId} of the ConditionClassType subtype.
   *
   * @return the ConditionClassId.
   */
  NodeId typeId() {
    return typeId;
  }

  /**
   * Get the ConditionClassName.
   *
   * @return the ConditionClassName.
   */
  LocalizedText displayName() {
    return displayName;
  }
}

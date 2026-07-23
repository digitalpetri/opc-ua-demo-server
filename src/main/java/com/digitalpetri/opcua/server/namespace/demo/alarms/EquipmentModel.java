package com.digitalpetri.opcua.server.namespace.demo.alarms;

import java.util.List;
import org.eclipse.milo.opcua.sdk.server.conditions.Condition;

/**
 * One simulated piece of equipment in the {@code Demo/Alarms} plant: an event-notifying Object Node
 * acting as the condition source for a set of alarms, plus a deterministic state machine driven by
 * a shared tick counter.
 *
 * <p>Implementations build their Node structure in their constructor and are ticked by {@link
 * AlarmNodesFragment} afterwards. One tick represents one simulated second regardless of the
 * configured wall-clock tick interval, so a model's rates and cycle times read the same whether the
 * fragment ticks once a second in a demo or every 50 ms in a test.
 */
interface EquipmentModel {

  /**
   * Get the Conditions this model owns, for registration with the server's ConditionManager.
   *
   * @return the {@link Condition}s owned by this model.
   */
  List<Condition> conditions();

  /**
   * Advance the simulation by one tick.
   *
   * @param tick the monotonic tick counter, starting at zero.
   */
  void tick(long tick);

  /**
   * Whether a scripted fault episode is running at {@code tick}.
   *
   * <p>Episodes recur every {@code periodTicks} and occupy the last {@code durationTicks} of each
   * period, so a model always starts healthy and reaches its first episode after a period of normal
   * operation a viewer can take in first.
   *
   * @param tick the current tick.
   * @param periodTicks the simulated seconds between the start of one episode and the next.
   * @param durationTicks the simulated seconds each episode lasts.
   * @return {@code true} while the episode is running.
   */
  static boolean episodeActive(long tick, long periodTicks, long durationTicks) {
    return tick % periodTicks >= periodTicks - durationTicks;
  }
}

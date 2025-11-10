package edu.ftcphoenix.fw.flow.simple;

import edu.ftcphoenix.fw.core.Updatable;
import edu.ftcphoenix.fw.util.LoopClock;

import java.util.Set;

/**
 * A tiny, lane-aware “workcell” that participates in a pull-first item transfer.
 *
 * <h3>Design goals</h3>
 * <ul>
 *   <li>Keep robot code simple: a stage exposes only the minimum needed for safe handoffs.</li>
 *   <li>Timing lives inside the stage (no jitter from the flow).</li>
 *   <li>Works for bilateral work (both sides act) and passive endpoints (field/goal).</li>
 * </ul>
 *
 * <h3>Lifecycle (per lane)</h3>
 * The flow calls {@link #process(String)} every loop to let the stage move toward readiness
 * on the chosen lane. When both sides report ready for that lane, the flow triggers an
 * atomic handoff by calling {@link #provide(String)} on the upstream stage and
 * {@link #accept(String)} on the downstream stage in the same loop.
 *
 * <h3>Non-negotiable invariants (enforced by your implementation):</h3>
 * <ul>
 *   <li><b>Non-blocking:</b> {@code process}, {@code provide}, {@code accept} must return quickly.</li>
 *   <li><b>Idempotent process:</b> {@code process(lane)} may be called every loop; set targets, don’t pulse hardware here.</li>
 *   <li><b>Busy gating:</b> When a timed action is running, expose {@code canProvide=false} and/or
 *       {@code canAccept=false} until it completes. Start your timers in {@code provide/accept};
 *       advance them in {@link #update(LoopClock)}.</li>
 *   <li><b>Debounce:</b> Sensor-readiness used in {@code canProvide/canAccept} must be debounced/hysteretic.</li>
 *   <li><b>No hidden sleeps:</b> The stage must never block the loop; all timing is via internal state + {@code update}.</li>
 * </ul>
 *
 * <h3>Typical mappings</h3>
 * <ul>
 *   <li>Arm+Claw placer: lanes = {"left","right"}, {@code process} drives pose, {@code provide} opens claw.</li>
 *   <li>Chamber: lanes = {"accept","reject"}, {@code process} indexes, {@code provide} pulses window or reverses transport.</li>
 *   <li>Shooter: lanes = {"accept"}, {@code process} maintains flywheel speed, {@code accept} marks ingest, upstream does the pulse.</li>
 *   <li>Passive endpoints: field/goal have no actuators—{@code provide/accept} can be no-ops;
 *       they gate with {@code canProvide}/{@code canAccept}.</li>
 * </ul>
 */
public interface SimpleLanedStage extends Updatable {

    /**
     * Set of lane names this stage supports (e.g., "accept","reject","left","right").
     */
    Set<String> lanes();

    /**
     * Move internal controllers toward readiness on the specified lane.
     * <p>Best practice: set control targets here (pose, speed, index state), not pulses.</p>
     */
    void process(String lane);

    /**
     * True if an item is positioned and available to provide on this lane (after debouncing & busy gating).
     */
    boolean canProvide(String lane);

    /**
     * Start the upstream action for this lane (e.g., open window, reverse transport, open claw).
     * <p>Must set a {@code busy} flag immediately and clear it later in {@link #update(LoopClock)}.</p>
     */
    void provide(String lane);

    /**
     * True if this stage is able to accept on this lane right now (after debouncing & busy gating).
     */
    boolean canAccept(String lane);

    /**
     * Start the downstream action for this lane (e.g., close claw to grab, spin intake outward for reject).
     * <p>Must set a {@code busy} flag immediately and clear it later in {@link #update(LoopClock)}.</p>
     */
    void accept(String lane);
}

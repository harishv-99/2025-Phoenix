package edu.ftcphoenix.fw.actuation;

import edu.ftcphoenix.fw.debug.DebugSink;

/**
 * A generic setpoint-driven mechanism.
 *
 * <p>A {@code Plant} is the low-level "sink" that accepts a scalar target
 * and drives one or more hardware outputs (motors, servos, etc.) toward
 * that target using whatever control logic it chooses (open-loop, PID,
 * vendor velocity control, feedforward, etc.).</p>
 *
 * <h2>Semantic categories</h2>
 *
 * <p>Each concrete plant should document which of these categories it uses:</p>
 *
 * <ul>
 *   <li><b>Power plants</b> – target is a normalized power command:
 *     <ul>
 *       <li>Typical range: [-1.0, +1.0].</li>
 *       <li>Examples: intake motor power, buffer/feeder power.</li>
 *     </ul>
 *   </li>
 *
 *   <li><b>Velocity plants</b> – target is angular velocity:
 *     <ul>
 *       <li>Units: rad/s at the motor shaft (unless otherwise documented).</li>
 *       <li>Examples: shooter flywheel velocity, conveyor belt speed.</li>
 *     </ul>
 *   </li>
 *
 *   <li><b>Position plants</b> – target is angle or position:
 *     <ul>
 *       <li>Units: radians at the motor shaft, or [0, 1] for normalized servo
 *           positions, depending on the implementation.</li>
 *       <li>Examples: arm angle, slide extension, servo pusher position.</li>
 *     </ul>
 *   </li>
 * </ul>
 *
 * <p>Higher-level code should treat {@code target} as "some scalar command in
 * this plant's native units" and avoid mixing plants with incompatible
 * semantics.</p>
 */
public interface Plant {

    /**
     * Update the desired target setpoint.
     *
     * <p>This should be a cheap operation – callers are free to set the
     * same target repeatedly (e.g., each loop) without penalty.</p>
     *
     * @param target mechanism-defined target (power, rad/s, radians, etc.)
     */
    void setTarget(double target);

    /**
     * Optional introspection: return the current target setpoint in the
     * plant's native units (power, rad/s, radians, etc.).
     *
     * <p>Implementations are encouraged (but not required) to store the
     * last target passed to {@link #setTarget(double)} and return it here
     * so that callsites (telemetry, {@link #debugDump(DebugSink, String)},
     * decorators) can inspect it.</p>
     *
     * <p>Plants that do not track a target may simply return {@code 0.0}
     * or any convenient value; callers should treat this as best-effort
     * telemetry rather than a strict contract.</p>
     *
     * @return last commanded target, or {@code 0.0} if not tracked
     */
    default double getTarget() {
        return 0.0;
    }

    /**
     * Advance the plant by {@code dtSec} seconds.
     *
     * <p>Implementations may:</p>
     * <ul>
     *   <li>Compute new actuator commands based on internal state.</li>
     *   <li>Call into underlying vendor APIs (e.g., velocity setters).</li>
     *   <li>Do nothing for stateless plants.</li>
     * </ul>
     *
     * <p><b>Callers are responsible</b> for invoking this once per control
     * loop. Helper classes (tasks, controllers, mechanisms) should <b>not</b>
     * assume exclusive ownership of update timing – it is common for
     * multiple decorators/controllers to share a plant as long as only
     * one of them is responsible for calling {@code update(dtSec)}.</p>
     *
     * @param dtSec time since last update in seconds
     */
    void update(double dtSec);

    /**
     * Optional lifecycle hook to clear any internal state associated with
     * this plant.
     *
     * <p>Examples:</p>
     * <ul>
     *   <li>Resetting integrators in a PID controller.</li>
     *   <li>Zeroing internal timers or filters.</li>
     *   <li>Reinitializing vendor control modes if needed.</li>
     * </ul>
     *
     * <p>Default implementation does nothing.</p>
     */
    default void reset() {
        // Default: no internal state to clear.
    }

    /**
     * @return {@code true} if this plant considers itself "at" its current
     * target setpoint. Implementations that do not track this can
     * simply return {@code false} or {@code true} unconditionally.
     */
    default boolean atSetpoint() {
        return false;
    }

    /**
     * Optional debug hook: emit a compact summary of this plant's state.
     *
     * <p>The default implementation writes only the target and atSetpoint
     * flag. Implementations are encouraged to override this to include
     * additional mechanism-specific details (sensor feedback, errors,
     * internal controller state, etc.).</p>
     *
     * @param dbg    debug sink (may be {@code null}; if null, no output is produced)
     * @param prefix base key prefix, e.g. "intake", "shooter", or "arm"
     */
    default void debugDump(DebugSink dbg, String prefix) {
        if (dbg == null) {
            return;
        }
        String p = (prefix == null || prefix.isEmpty()) ? "plant" : prefix;
        dbg.addData(p + ".target", getTarget())
                .addData(p + ".atSetpoint", atSetpoint());
    }
}

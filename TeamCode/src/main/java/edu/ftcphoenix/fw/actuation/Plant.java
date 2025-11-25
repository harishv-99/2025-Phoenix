package edu.ftcphoenix.fw.actuation;

import edu.ftcphoenix.fw.debug.DebugSink;

/**
 * A generic setpoint-driven mechanism.
 *
 * <p>Implementations typically wrap one or more HAL outputs
 * (e.g., {@code MotorOutput}, {@code ServoOutput}) and any internal
 * control logic (PID, feedforward, vendor velocity control, etc.).</p>
 *
 * <p>Examples:</p>
 * <ul>
 *   <li>Open-loop power plant for an intake motor.</li>
 *   <li>Velocity plant for a shooter flywheel (rad/s).</li>
 *   <li>Angle plant for an arm (radians).</li>
 *   <li>Paired plants driving left/right motors as one mechanism.</li>
 * </ul>
 *
 * <p>Units and semantics of the {@code target} are mechanism-defined.
 * For example, a power plant might interpret targets in [-1, +1],
 * while a velocity plant uses rad/s.</p>
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
     * so that callsites (telemetry, debugDump, decorators) can inspect it.</p>
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
     * @param dtSec time since last update in seconds
     */
    void update(double dtSec);

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

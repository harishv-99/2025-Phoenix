package edu.ftcphoenix.fw.actuation;

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
     * @param target mechanism-defined target (power, rad/s, radians, etc.)
     */
    void setTarget(double target);

    /**
     * Advance the plant by {@code dtSec} seconds.
     *
     * <p>Implementations may:
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
}

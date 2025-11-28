package edu.ftcphoenix.fw.hal;

/**
 * Generic "power" control channel for an actuator.
 *
 * <p>This is the lowest-level interface that plants see for
 * anything driven by a scalar power command:
 *
 * <ul>
 *   <li>DC motors driven with {@code setPower(-1..+1)}</li>
 *   <li>Continuous rotation servos treated as "motors"</li>
 *   <li>Other platforms' actuators that conceptually accept a
 *       dimensionless power signal</li>
 * </ul>
 *
 * <h2>Design goals</h2>
 *
 * <ul>
 *   <li>Hide FTC-specific types (DcMotor, CRServo, etc.) from
 *       core logic.</li>
 *   <li>Let {@code Plant} and {@code Task} work with a single,
 *       simple abstraction.</li>
 *   <li>Keep semantics minimal: "set power now", "what was the
 *       last power command?"</li>
 * </ul>
 *
 * <p>Implementations are responsible for mapping the logical
 * {@code power} value to whatever the underlying platform uses
 * (PWM duty cycle, voltage, etc.).</p>
 */
public interface PowerOutput {

    /**
     * Sets the instantaneous power command for this actuator.
     *
     * <p>Typical range is {@code [-1.0, +1.0]}, but the exact valid
     * range and meaning are defined by the implementation. Plants
     * will usually clamp to a reasonable range before calling this.</p>
     *
     * @param power desired power command
     */
    void setPower(double power);

    /**
     * Returns the last power command that was passed to
     * {@link #setPower(double)}.
     *
     * <p>This is a cached value, not a sensor reading. It reflects
     * "what we asked the actuator to do", not necessarily what the
     * hardware actually did.</p>
     *
     * @return last commanded power value
     */
    double getLastPower();

    /**
     * Convenience method to set power to zero.
     *
     * <p>Implementations may override this if zero has a special
     * meaning (e.g., brake vs. coast), but the default behavior is
     * equivalent to {@code setPower(0.0)}.</p>
     */
    default void stop() {
        setPower(0.0);
    }
}

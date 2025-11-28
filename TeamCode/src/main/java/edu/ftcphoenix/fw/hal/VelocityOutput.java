package edu.ftcphoenix.fw.hal;

/**
 * Generic "velocity" control channel for an actuator.
 *
 * <p>This interface represents a single degree of freedom with a
 * scalar velocity in the actuator's <b>native units</b>. The exact
 * meaning of the velocity value depends on the implementation:</p>
 *
 * <ul>
 *   <li>Motor with encoder:
 *       <ul>
 *         <li>Domain: encoder ticks per second (or the SDK's native
 *             velocity units).</li>
 *         <li>Typical implementation delegates to
 *             {@code DcMotorEx.setVelocity(double)} and reads back from
 *             {@code DcMotorEx.getVelocity()}.</li>
 *       </ul>
 *   </li>
 *   <li>Other platforms:
 *       <ul>
 *         <li>Any consistent "native" velocity unit defined by the
 *             adapter.</li>
 *       </ul>
 *   </li>
 * </ul>
 *
 * <p>The framework does not impose physical units (radians per second,
 * meters per second, etc.) on this interface. Higher-level code is free
 * to convert to/from those units if needed, but {@code VelocityOutput}
 * itself is intentionally simple and device-oriented.</p>
 */
public interface VelocityOutput {

    /**
     * Sets the desired target velocity for this actuator in its native units.
     *
     * <p>Examples:</p>
     * <ul>
     *   <li>Motor with encoder: ticks per second</li>
     * </ul>
     *
     * <p>Implementations may clamp or scale the input to a valid range
     * before applying it to the underlying hardware.</p>
     *
     * @param velocity desired target velocity (native units)
     */
    void setVelocity(double velocity);

    /**
     * Returns the last velocity value that was <b>commanded</b> via
     * {@link #setVelocity(double)}.
     *
     * <p>This is a cached command value, not a sensor measurement. It
     * reflects "what we requested" in native units, which may differ
     * from the actual measured velocity at any given moment.</p>
     *
     * @return last commanded target velocity (native units)
     */
    double getCommandedVelocity();

    /**
     * Returns the latest measured velocity of the actuator in native units.
     *
     * <p>When supported, this should be backed by a sensor reading
     * (for example, {@code DcMotorEx.getVelocity()} in ticks per
     * second). Implementations that cannot provide a real measurement
     * may return a best-effort estimate (such as {@link #getCommandedVelocity()})
     * or a sentinel value, but should document their behavior.</p>
     *
     * @return measured velocity (native units), or an implementation-defined
     * fallback if no sensor data is available
     */
    double getMeasuredVelocity();

    /**
     * Convenience method to command zero velocity.
     *
     * <p>By default, this is equivalent to calling
     * {@link #setVelocity(double)} with {@code 0.0}. Implementations
     * may override this if zero has special semantics.</p>
     */
    default void stop() {
        setVelocity(0.0);
    }
}

package edu.ftcphoenix.fw.hal;

/**
 * Generic "position" control channel for an actuator.
 *
 * <p>This interface represents a single degree of freedom with a
 * scalar position in the actuator's <b>native units</b>. The exact
 * meaning of the position value depends on the implementation:</p>
 *
 * <ul>
 *   <li>Standard FTC servo:
 *       <ul>
 *         <li>Domain: {@code 0.0 .. 1.0}</li>
 *         <li>Matches {@link com.qualcomm.robotcore.hardware.Servo#setPosition(double)}.</li>
 *       </ul>
 *   </li>
 *   <li>Motor with encoder used for positioning:
 *       <ul>
 *         <li>Domain: encoder ticks (usually an integer, but passed as {@code double}).</li>
 *         <li>Typical implementation uses {@code RUN_TO_POSITION} mode.</li>
 *       </ul>
 *   </li>
 *   <li>Other platforms:
 *       <ul>
 *         <li>Any consistent "native" position unit defined by the adapter.</li>
 *       </ul>
 *   </li>
 * </ul>
 *
 * <p>The framework does not impose physical units (radians, degrees, etc.)
 * on this interface. Higher-level code is free to convert to/from those
 * units if needed, but {@code PositionOutput} itself is intentionally
 * simple and device-oriented.</p>
 */
public interface PositionOutput {

    /**
     * Sets the desired target position for this actuator in its native units.
     *
     * <p>Examples:</p>
     * <ul>
     *   <li>Servo: {@code 0.0 .. 1.0}</li>
     *   <li>Motor with encoder: encoder ticks</li>
     * </ul>
     *
     * <p>Implementations may clamp the input to a valid range before
     * applying it to the underlying hardware.</p>
     *
     * @param position desired target position (native units)
     */
    void setPosition(double position);

    /**
     * Returns the last target position that was passed to
     * {@link #setPosition(double)}.
     *
     * <p>This is a cached command value, not necessarily a sensor reading.
     * It reflects "what we requested" in native units.</p>
     *
     * @return last commanded target position (native units)
     */
    double getLastPosition();

    /**
     * Convenience method to reset the target position to a default
     * "home" or "zero" value in native units.
     *
     * <p>By default, this calls {@link #setPosition(double)} with
     * {@code 0.0}. Implementations may override this if a different
     * reset value is more appropriate for the device.</p>
     */
    default void resetPosition() {
        setPosition(0.0);
    }
}

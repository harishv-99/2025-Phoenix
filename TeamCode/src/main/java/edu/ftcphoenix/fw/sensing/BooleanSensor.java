package edu.ftcphoenix.fw.sensing;

import edu.ftcphoenix.fw.util.LoopClock;

/**
 * Boolean specialization of {@link Sensor} with a primitive getter and optional edge helpers.
 *
 * <h3>Why this shape?</h3>
 * <ul>
 *   <li>Robot/control code can use the fast primitive {@link #asBoolean()} with no boxing.</li>
 *   <li>Generic tooling (telemetry taps, collections) can still use {@link #value()} via a
 *       default implementation that boxes {@code asBoolean()}.</li>
 *   <li>No return-type conflicts with {@code Sensor<Boolean>}.</li>
 * </ul>
 */
public interface BooleanSensor extends Sensor<Boolean> {

    /**
     * Fast primitive read; implement this in concrete sensors.
     */
    boolean asBoolean();

    /**
     * Default boxed read to satisfy {@link Sensor}.
     */
    @Override
    default Boolean value() {
        return asBoolean() ? Boolean.TRUE : Boolean.FALSE;
    }

    /**
     * Optional: rising edge since last {@link #update(LoopClock)}.
     */
    default boolean rose() {
        return false;
    }

    /**
     * Optional: falling edge since last {@link #update(LoopClock)}.
     */
    default boolean fell() {
        return false;
    }
}

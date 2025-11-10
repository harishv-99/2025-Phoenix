package edu.ftcphoenix.fw.sensing;

import edu.ftcphoenix.fw.core.Updatable;
import edu.ftcphoenix.fw.util.LoopClock;

/**
 * Generic pull-based sensor.
 *
 * <h3>Contract</h3>
 * <ul>
 *   <li>Call {@link #update(LoopClock)} once per loop; never block or sleep inside it.</li>
 *   <li>Read the current value via {@link #value()} (reference type).</li>
 * </ul>
 *
 * <p>Specialized sensors (e.g., booleans) can extend this interface and add
 * primitive getters for performance.</p>
 */
public interface Sensor<T> extends Updatable {
    /**
     * Current sensor value (reference type).
     */
    T value();

    @Override
    void update(LoopClock clock);
}

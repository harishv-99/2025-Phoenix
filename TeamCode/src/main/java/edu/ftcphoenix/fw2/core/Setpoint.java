package edu.ftcphoenix.fw2.core;

/**
 * A mutable target value that is also a pull-based {@link Source}.
 *
 * <p>Use this to unify "commanded target" storage and consumption APIs. {@link #peek()} provides
 * the raw last-set value, while {@link #get(FrameClock)} enables participation in frame-driven
 * pipelines.</p>
 */
public interface Setpoint<T> extends Source<T> {
    /** Update the target value. */
    void set(T value);

    /** Read the last set value without any filtering/shaping. */
    T peek();
}

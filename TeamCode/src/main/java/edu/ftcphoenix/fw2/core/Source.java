package edu.ftcphoenix.fw2.core;

import edu.ftcphoenix.fw2.filters.Filter;

import java.util.function.BiFunction;

/**
 * A pull-based producer of values, sampled once per loop.
 *
 * <p>Sources encapsulate "where values come from": gamepads, sensors, setpoints, or
 * upstream computations. They are asked to produce a value given a {@link FrameClock}
 * that provides both the frame timestamp and {@code dt}.</p>
 *
 * <h2>When to use</h2>
 * <ul>
 *   <li>Expose inputs: joystick readings, measurements, setpoints.</li>
 *   <li>Compose processing graphs by mapping or filtering an upstream source.</li>
 * </ul>
 *
 * <h2>Ergonomics</h2>
 * <ul>
 *   <li>Use {@link #filtered(Filter)} to apply a {@link Filter} to this source inline.</li>
 *   <li>Use {@link #mapped(BiFunction)} for small one-off transforms that may depend on {@code dt}.</li>
 * </ul>
 *
 * @param <T> value type produced by the source
 */
public interface Source<T> {

    /**
     * Produce a value for the current frame.
     *
     * @param clock frame clock providing timestamp and {@code dt}
     * @return value for this frame
     */
    T get(FrameClock clock);

    /**
     * Apply a {@link Filter} to this source, returning a derived source that yields filtered values.
     *
     * <p>This is sugar over the “wrap source with a filter” pattern and replaces the need
     * for a separate {@code FilteredSource} helper.</p>
     *
     * @param f filter to apply (nullable = passthrough)
     * @return derived source that filters this source's output each frame
     */
    default Source<T> filtered(Filter<T> f) {
        if (f == null) return this;
        return clock -> f.apply(this.get(clock), clock.dtSec());
    }

    /**
     * Map this source into another source using a function that sees the current value and {@code dt}.
     *
     * <p>Use this for small, custom transforms that would be overkill as a standalone {@link Filter}.
     * If the transform will be reused or needs internal state, prefer a proper {@code Filter} and
     * use {@link #filtered(Filter)} instead.</p>
     *
     * @param fn  mapping function {@code (value, dt) -> result}
     * @param <R> result value type
     * @return derived source producing mapped results each frame
     */
    default <R> Source<R> mapped(BiFunction<T, Double, R> fn) {
        return clock -> fn.apply(this.get(clock), clock.dtSec());
    }
}

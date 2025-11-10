package edu.ftcphoenix.fw2.filters;

/**
 * A tiny, composable, dt-aware filter.
 *
 * <p>Filters transform values (optionally with internal state) using the loop's time step {@code dtSeconds}.
 * Implementations may be stateless or stateful. Keep implementations fast and side-effect free.</p>
 *
 * <h2>When to use</h2>
 * <ul>
 *   <li>To shape a signal (deadband, expo, slew, clamps, smoothing) before sending it to a sink.</li>
 *   <li>To post-process sensor readings (median/average/low-pass) before decisions or control.</li>
 *   <li>As a reusable building block inside larger pipelines (e.g., {@code Pipeline<T>} or axis chains).</li>
 * </ul>
 *
 * <h2>Composition</h2>
 * <p>Use {@link #then(Filter)} to chain filters in order: {@code this → next}.</p>
 *
 * @param <T> value type processed by the filter
 */
@FunctionalInterface
public interface Filter<T> {
    /**
     * Apply this filter to an input value.
     *
     * @param in        input value
     * @param dtSeconds time step in seconds since previous call (≥ 0)
     * @return filtered output value
     */
    T apply(T in, double dtSeconds);

    /**
     * Identity/no-op filter.
     *
     * @param <T> value type
     * @return a filter that returns its input unchanged
     */
    static <T> Filter<T> identity() {
        return (x, dt) -> x;
    }

    /**
     * Sequential composition of filters: {@code this → next}.
     *
     * <p>The returned filter first applies {@code this}, then passes the result into {@code next}.</p>
     *
     * @param next the filter to apply after this one (nullable = no-op)
     * @return composed filter
     */
    default Filter<T> then(Filter<T> next) {
        if (next == null) return this;
        return (x, dt) -> next.apply(this.apply(x, dt), dt);
    }
}

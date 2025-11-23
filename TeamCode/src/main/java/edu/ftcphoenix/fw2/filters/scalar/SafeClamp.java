package edu.ftcphoenix.fw2.filters.scalar;

import edu.ftcphoenix.fw2.filters.Filter;
import edu.ftcphoenix.fw2.util.MathUtil;

/**
 * SafeClamp — final-stage clamp with NaN/Inf guard (sink protection).
 *
 * <p><b>What it does:</b></p>
 * <ul>
 *   <li>Coerces non-finite inputs (NaN/Inf) to a configurable fallback (default 0.0).</li>
 *   <li>Clamps the (coerced) value to {@code [min, max]}.</li>
 *   <li>Intended as the <i>last</i> stage before a sink (motors, servos, IO).</li>
 * </ul>
 *
 * <p><b>Why separate from {@link Clamp}?</b></p>
 * <ul>
 *   <li>Separation of concerns: mid-chain shaping vs. sink guarding.</li>
 *   <li>Predictability: avoid hidden NaN/Inf coercion in mid-chain filters.</li>
 *   <li>Simplicity: one clear place to guarantee safe values for hardware.</li>
 * </ul>
 */
public final class SafeClamp implements Filter<Double> {
    private final double min, max, fallback;

    /**
     * Create a SafeClamp with bounds {@code [min, max]} and fallback {@code 0.0}.
     *
     * @param min lower bound (inclusive)
     * @param max upper bound (inclusive)
     */
    public SafeClamp(double min, double max) {
        this(min, max, 0.0);
    }

    /**
     * Create a SafeClamp with bounds {@code [min, max]} and a custom fallback.
     *
     * @param min      lower bound (inclusive)
     * @param max      upper bound (inclusive)
     * @param fallback value to use when input is non-finite (NaN/Inf)
     */
    public SafeClamp(double min, double max, double fallback) {
        double lo = Math.min(min, max), hi = Math.max(min, max);
        this.min = lo;
        this.max = hi;
        this.fallback = fallback;
    }

    /**
     * Symmetric convenience: {@code [-limit, +limit]} with fallback {@code 0.0}.
     *
     * @param limit non-negative magnitude for both sides
     * @return a {@code SafeClamp} with symmetric bounds
     */
    public static SafeClamp symmetric(double limit) {
        double l = Math.abs(limit);
        return new SafeClamp(-l, l);
    }

    /**
     * Symmetric convenience with custom fallback.
     *
     * @param limit    non-negative magnitude for both sides
     * @param fallback value to use when input is non-finite
     * @return a {@code SafeClamp} with symmetric bounds and custom fallback
     */
    public static SafeClamp symmetric(double limit, double fallback) {
        double l = Math.abs(limit);
        return new SafeClamp(-l, l, fallback);
    }

    @Override
    public Double apply(Double x, double dtSeconds) {
        return MathUtil.clampFinite(x, min, max, fallback);
    }
}

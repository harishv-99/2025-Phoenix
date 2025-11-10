package edu.ftcphoenix.fw2.filters.scalar;

import edu.ftcphoenix.fw2.filters.Filter;
import edu.ftcphoenix.fw2.util.MathUtil;

import java.util.function.DoubleSupplier;

/**
 * Clamp — saturation clamp with constant or live bounds.
 *
 * <p><b>What it does:</b></p>
 * <ul>
 *   <li>Enforces hard bounds {@code [min, max]} on a scalar signal.</li>
 *   <li>Supports constant bounds (constructor with doubles) or live bounds
 *       (constructor with {@link DoubleSupplier}s).</li>
 *   <li>Auto-corrects swapped bounds at runtime (if {@code min &gt; max}, they are swapped).</li>
 * </ul>
 *
 * <p><b>What it does <i>not</i> do:</b></p>
 * <ul>
 *   <li><b>No</b> NaN/Inf coercion. If input is non-finite, the output will also be non-finite.</li>
 *   <li><b>No</b> special sink protection. Use {@link SafeClamp} for that purpose.</li>
 * </ul>
 *
 * <p><b>When to use:</b></p>
 * <ul>
 *   <li>As a <i>mid-chain</i> clamp inside shaping pipelines where the input is already known
 *       to be finite and you want to bound intermediate values.</li>
 *   <li>When you need live, mode-dependent limits (e.g., precision/turbo), using suppliers.</li>
 * </ul>
 *
 * <p><b>When to prefer {@link SafeClamp}:</b></p>
 * <ul>
 *   <li>As the <i>final</i> stage before a sink (motors/servos/IO) where you want both a clamp
 *       <i>and</i> NaN/Inf coercion to a safe fallback.</li>
 * </ul>
 */
public final class Clamp implements Filter<Double> {
    private final DoubleSupplier minSup, maxSup;

    /**
     * Create a clamp with constant bounds {@code [min, max]}.
     *
     * @param min lower bound (inclusive)
     * @param max upper bound (inclusive)
     */
    public Clamp(double min, double max) {
        this(() -> min, () -> max);
    }

    /**
     * Create a clamp with live bounds. The suppliers are evaluated on each call.
     *
     * @param minSup supplier for lower bound (inclusive)
     * @param maxSup supplier for upper bound (inclusive)
     */
    public Clamp(DoubleSupplier minSup, DoubleSupplier maxSup) {
        this.minSup = minSup;
        this.maxSup = maxSup;
    }

    /**
     * Symmetric convenience clamp: {@code [-limit, +limit]} with a constant limit.
     *
     * @param limit non-negative magnitude for both sides
     * @return a {@code Clamp} with symmetric bounds
     */
    public static Clamp symmetric(double limit) {
        double l = Math.abs(limit);
        return new Clamp(-l, l);
    }

    /**
     * Symmetric convenience clamp with a live limit: {@code [-limit(), +limit()]}.
     *
     * @param limitSup supplier of a non-negative magnitude for both sides
     * @return a {@code Clamp} with symmetric, live bounds
     */
    public static Clamp symmetric(DoubleSupplier limitSup) {
        return new Clamp(
                () -> -Math.abs(limitSup.getAsDouble()),
                () -> Math.abs(limitSup.getAsDouble())
        );
    }

    @Override
    public Double apply(Double x, double dtSeconds) {
        double min = minSup.getAsDouble();
        double max = maxSup.getAsDouble();
        // Tolerate accidental inversion at runtime
        if (min > max) {
            double t = min;
            min = max;
            max = t;
        }
        return MathUtil.clamp(x, min, max);
    }
}

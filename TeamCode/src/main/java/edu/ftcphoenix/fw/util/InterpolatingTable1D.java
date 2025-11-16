package edu.ftcphoenix.fw.util;

import java.util.Objects;
import java.util.function.DoubleUnaryOperator;
import java.util.ArrayList;
import java.util.List;
import java.util.Arrays;

/**
 * Simple immutable 1D lookup table with linear interpolation.
 *
 * <p>Typical usage:</p>
 * <ul>
 *   <li>Distance (inches) → shooter velocity (rad/s).</li>
 *   <li>Distance (inches) → arm angle (rad).</li>
 * </ul>
 *
 * <p>Semantics:</p>
 * <ul>
 *   <li>x-values must be strictly increasing.</li>
 *   <li>Values below the first x clamp to the first y.</li>
 *   <li>Values above the last x clamp to the last y.</li>
 *   <li>Values in-between are linearly interpolated.</li>
 * </ul>
 */
public final class InterpolatingTable1D implements DoubleUnaryOperator {

    private final double[] xs;
    private final double[] ys;

    private InterpolatingTable1D(double[] xs, double[] ys) {
        this.xs = xs;
        this.ys = ys;
    }

    /**
     * Create a table from sorted x / y arrays.
     *
     * @param xs strictly increasing x-values (e.g., distance in inches)
     * @param ys corresponding y-values (e.g., velocity in rad/s)
     */
    public static InterpolatingTable1D ofSorted(double[] xs, double[] ys) {
        Objects.requireNonNull(xs, "xs is required");
        Objects.requireNonNull(ys, "ys is required");
        if (xs.length != ys.length) {
            throw new IllegalArgumentException("xs and ys must have same length");
        }
        if (xs.length == 0) {
            throw new IllegalArgumentException("xs/ys must contain at least one point");
        }

        double[] xsCopy = xs.clone();
        double[] ysCopy = ys.clone();

        for (int i = 1; i < xsCopy.length; i++) {
            if (!(xsCopy[i] > xsCopy[i - 1])) {
                throw new IllegalArgumentException(
                        "xs must be strictly increasing; xs[" + (i - 1) + "]=" + xsCopy[i - 1]
                                + ", xs[" + i + "]=" + xsCopy[i]);
            }
        }

        return new InterpolatingTable1D(xsCopy, ysCopy);
    }

    /**
     * Convenience factory: build from sorted (x,y) pairs.
     *
     * <p>Example:</p>
     * <pre>
     * InterpolatingTable1D table = InterpolatingTable1D.ofSortedPairs(
     *      24.0, 180.0,
     *      30.0, 190.0,
     *      36.0, 205.0,
     *      42.0, 220.0
     * );
     * </pre>
     *
     * @param xsAndYs flattened pairs: x0, y0, x1, y1, ...
     */
    public static InterpolatingTable1D ofSortedPairs(double... xsAndYs) {
        Objects.requireNonNull(xsAndYs, "xsAndYs is required");
        if (xsAndYs.length == 0 || xsAndYs.length % 2 != 0) {
            throw new IllegalArgumentException("xsAndYs must contain an even number of values (x0, y0, x1, y1, ...)");
        }
        int n = xsAndYs.length / 2;
        double[] xs = new double[n];
        double[] ys = new double[n];
        int idx = 0;
        for (int i = 0; i < n; i++) {
            xs[i] = xsAndYs[idx++];
            ys[i] = xsAndYs[idx++];
        }
        return ofSorted(xs, ys);
    }

    /**
     * Builder for readable table declarations in robot code.
     *
     * <p>Example:</p>
     * <pre>
     * private static final InterpolatingTable1D SHOOTER_TABLE =
     *     InterpolatingTable1D.builder()
     *         .add(24.0, 180.0)
     *         .add(30.0, 190.0)
     *         .add(36.0, 205.0)
     *         .add(42.0, 220.0)
     *         .build();
     * </pre>
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Evaluate the table at the given x using linear interpolation
     * with clamping to the end values.
     *
     * @param x query x-value
     * @return interpolated y-value
     */
    public double interpolate(double x) {
        int n = xs.length;
        if (n == 1) {
            return ys[0];
        }

        // Clamp below/above range
        if (x <= xs[0]) {
            return ys[0];
        }
        int last = n - 1;
        if (x >= xs[last]) {
            return ys[last];
        }

        // Binary search for segment
        int idx = Arrays.binarySearch(xs, x);
        if (idx >= 0) {
            // Exact match
            return ys[idx];
        }

        // Insertion point of first element greater than x
        int insertionPoint = -idx - 1;
        int i0 = insertionPoint - 1;
        int i1 = insertionPoint;

        double x0 = xs[i0];
        double x1 = xs[i1];
        double y0 = ys[i0];
        double y1 = ys[i1];

        if (x1 == x0) {
            return y0;
        }

        double t = (x - x0) / (x1 - x0);
        return y0 + t * (y1 - y0);
    }

    /**
     * Functional interface integration: treat this table as a DoubleUnaryOperator.
     */
    @Override
    public double applyAsDouble(double operand) {
        return interpolate(operand);
    }

    /**
     * @return number of calibration points.
     */
    public int size() {
        return xs.length;
    }

    /**
     * @return defensive copy of x-samples.
     */
    public double[] xs() {
        return xs.clone();
    }

    /**
     * @return defensive copy of y-samples.
     */
    public double[] ys() {
        return ys.clone();
    }

    /**
     * @return minimum x in the table.
     */
    public double minX() {
        return xs[0];
    }

    /**
     * @return maximum x in the table.
     */
    public double maxX() {
        return xs[xs.length - 1];
    }

    /**
     * @return true if x is within [minX, maxX].
     */
    public boolean isInRange(double x) {
        return x >= xs[0] && x <= xs[xs.length - 1];
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("InterpolatingTable1D{");
        for (int i = 0; i < xs.length; i++) {
            if (i > 0) sb.append(", ");
            sb.append('(').append(xs[i]).append(", ").append(ys[i]).append(')');
        }
        sb.append('}');
        return sb.toString();
    }

    /**
     * Builder for InterpolatingTable1D.
     *
     * <p>Note: x-values must be added in strictly increasing order.
     * This is enforced when build() is called.</p>
     */
    public static final class Builder {
        private final List<Double> xs = new ArrayList<>();
        private final List<Double> ys = new ArrayList<>();

        /**
         * Add a calibration sample (x, y).
         *
         * @param x x-value (e.g., distance)
         * @param y y-value (e.g., shooter velocity)
         * @return this builder for chaining
         */
        public Builder add(double x, double y) {
            xs.add(x);
            ys.add(y);
            return this;
        }

        /**
         * Build an immutable table. Validates that x-values are strictly increasing.
         */
        public InterpolatingTable1D build() {
            int n = xs.size();
            if (n == 0) {
                throw new IllegalStateException("No points added to table");
            }

            double[] xsArr = new double[n];
            double[] ysArr = new double[n];
            for (int i = 0; i < n; i++) {
                xsArr[i] = xs.get(i);
                ysArr[i] = ys.get(i);
            }
            return InterpolatingTable1D.ofSorted(xsArr, ysArr);
        }
    }
}

package edu.ftcphoenix.fw2.filters.scalar;

import edu.ftcphoenix.fw2.filters.Filter;

/**
 * Quantize to discrete steps; useful for precise jogs or coarse servo increments.
 */
public final class Quantize implements Filter<Double> {
    private final double step;

    public Quantize(double step) {
        this.step = Math.max(1e-9, step);
    }

    @Override
    public Double apply(Double x, double dt) {
        return Math.rint(x / step) * step;
    }
}

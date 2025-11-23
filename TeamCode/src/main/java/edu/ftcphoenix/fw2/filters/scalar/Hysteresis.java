package edu.ftcphoenix.fw2.filters.scalar;

import edu.ftcphoenix.fw2.filters.Filter;

/**
 * Hysteresis step: output 0 below low, 1 above high, else hold last.
 * Use for clean on/off decisions from noisy signals (e.g., thresholds).
 */
public final class Hysteresis implements Filter<Double> {
    private final double lo, hi;
    private double y = 0.0;

    public Hysteresis(double low, double high) {
        this.lo = Math.min(low, high);
        this.hi = Math.max(low, high);
    }

    @Override
    public Double apply(Double x, double dt) {
        if (x >= hi) y = 1.0;
        else if (x <= lo) y = 0.0;
        return y;
    }
}

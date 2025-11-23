package edu.ftcphoenix.fw2.filters.scalar;

import edu.ftcphoenix.fw2.filters.Filter;

/**
 * Fixed-size moving average (boxcar).
 * Use for short-window smoothing of measurements or setpoints.
 */
public final class MovingAverage implements Filter<Double> {
    private final int n;
    private final double[] buf;
    private int idx = 0, filled = 0;
    private double sum = 0;

    public MovingAverage(int window) {
        this.n = Math.max(1, window);
        this.buf = new double[n];
    }

    @Override
    public Double apply(Double x, double dt) {
        sum += x - buf[idx];
        buf[idx] = x;
        idx = (idx + 1) % n;
        if (filled < n) filled++;
        return sum / Math.max(1, filled);
    }
}

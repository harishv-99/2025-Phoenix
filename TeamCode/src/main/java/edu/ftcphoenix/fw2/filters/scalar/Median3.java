package edu.ftcphoenix.fw2.filters.scalar;

import edu.ftcphoenix.fw2.filters.Filter;

/**
 * Median of last 3 samples; good spike rejection for noisy sensors.
 */
public final class Median3 implements Filter<Double> {
    private double a = 0, b = 0, c = 0;
    private int k = 0;

    @Override
    public Double apply(Double x, double dt) {
        if (k == 0) a = x;
        else if (k == 1) b = x;
        else c = x;
        k = (k + 1) % 3;
        double x1 = a, x2 = b, x3 = c;
        if (x1 > x2) {
            double t = x1;
            x1 = x2;
            x2 = t;
        }
        if (x2 > x3) {
            double t = x2;
            x2 = x3;
            x3 = t;
        }
        if (x1 > x2) {
            double t = x1;
            x1 = x2;
            x2 = t;
        }
        return x2;
    }
}

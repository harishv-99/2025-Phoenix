package edu.ftcphoenix.fw2.filters.scalar;

import edu.ftcphoenix.fw2.filters.Filter;
import edu.ftcphoenix.fw2.util.MathUtil;

import java.util.function.DoubleSupplier;

/**
 * Sign-preserving exponent (1=linear, 2=square, etc.).
 * <p>
 * Use when:
 * - You want fine control near center while preserving full-scale endpoints.
 * - Typical choice: 1.5–3.0 for drive sticks, or blend using (1-k)*v + k*v^3 elsewhere.
 * <p>
 * Best practice:
 * - If you tune expo on the fly (dashboard/driver), pass a supplier; otherwise use constant ctor.
 */
public final class Expo implements Filter<Double> {
    private final DoubleSupplier exponentSup;

    /**
     * Constant exponent.
     */
    public Expo(double exponent) {
        this(() -> exponent);
    }

    /**
     * Live exponent.
     */
    public Expo(DoubleSupplier exponentSup) {
        this.exponentSup = exponentSup;
    }

    @Override
    public Double apply(Double x, double dtSeconds) {
        double n = Math.max(1.0, exponentSup.getAsDouble());
        return MathUtil.expoSigned(x, n);
    }
}

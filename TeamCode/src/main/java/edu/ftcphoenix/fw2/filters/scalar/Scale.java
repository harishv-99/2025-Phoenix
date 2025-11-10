package edu.ftcphoenix.fw2.filters.scalar;

import edu.ftcphoenix.fw2.filters.Filter;

import java.util.function.DoubleSupplier;

/**
 * Scalar multiplier: y = k() * x.
 * <p>
 * Use when:
 * - You want uniform "precision mode" scaling, or a live factor tied to a button/trigger/voltage.
 * <p>
 * Best practice:
 * - Accept a DoubleSupplier for live changes; use constant ctor for fixed factors.
 */
public final class Scale implements Filter<Double> {
    private final DoubleSupplier kSup;

    public Scale(double k) {
        this(() -> k);
    }

    public Scale(DoubleSupplier kSup) {
        this.kSup = kSup;
    }

    @Override
    public Double apply(Double x, double dt) {
        return x * kSup.getAsDouble();
    }
}

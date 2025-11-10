package edu.ftcphoenix.fw2.filters.scalar;

import edu.ftcphoenix.fw2.filters.Filter;
import edu.ftcphoenix.fw2.util.MathUtil;

import java.util.function.BooleanSupplier;
import java.util.function.DoubleSupplier;

/**
 * Deadband with optional linear re-scaling to keep slope continuous.
 * <p>
 * Use when:
 * - You want to ignore small stick noise (deadzone).
 * - Optional "rescale" preserves full-range feel outside the band (continuous slope).
 * <p>
 * Best practice:
 * - Make band live (supplier) if you have a precision/turbo toggle; otherwise use the constant ctor.
 */
public final class Deadband implements Filter<Double> {
    private final DoubleSupplier bandSup;
    private final BooleanSupplier rescaleSup;

    /**
     * Constant band and rescale flag.
     */
    public Deadband(double band, boolean rescale) {
        this(() -> band, () -> rescale);
    }

    /**
     * Live band and/or live rescale toggle.
     */
    public Deadband(DoubleSupplier bandSup, BooleanSupplier rescaleSup) {
        this.bandSup = bandSup;
        this.rescaleSup = rescaleSup;
    }

    /**
     * Convenience: non-rescaling deadband with constant band.
     */
    public static Deadband of(double band) {
        return new Deadband(band, false);
    }

    /**
     * Convenience: rescaling deadband with constant band.
     */
    public static Deadband rescaling(double band) {
        return new Deadband(band, true);
    }

    @Override
    public Double apply(Double x, double dtSeconds) {
        double band = Math.max(0.0, bandSup.getAsDouble());
        boolean rescale = rescaleSup.getAsBoolean();
        return rescale ? MathUtil.deadbandRescale(x, band)
                : MathUtil.deadband(x, band);
    }
}

package edu.ftcphoenix.fw2.filters.scalar;

import edu.ftcphoenix.fw2.filters.Filter;
import edu.ftcphoenix.fw2.util.MathUtil;

import java.util.function.DoubleSupplier;

/**
 * Rate limiter in units/sec with separate rise/fall rates.
 * <p>
 * Use when:
 * - You want to prevent jerky step changes (driver snaps stick or code jumps).
 * - Use a higher omega rate than translation to keep rotations responsive.
 * <p>
 * Best practice:
 * - Make rates live if your “precision/turbo” mode changes responsiveness.
 * - Consider initializing on first sample to avoid a ramp from zero.
 */
public final class SlewLimiter implements Filter<Double> {
    private final DoubleSupplier rateUpSup;   // units/sec
    private final DoubleSupplier rateDownSup; // units/sec
    private double prev = 0.0;
    private boolean first = true;
    private final boolean snapOnFirst; // start from input immediately on first apply()

    /**
     * Symmetric constant rate; snaps to input on first sample by default.
     */
    public SlewLimiter(double symmetricRate) {
        this(symmetricRate, symmetricRate, true);
    }

    /**
     * Asymmetric constant rate; snaps to input on first sample by default.
     */
    public SlewLimiter(double rateUp, double rateDown) {
        this(rateUp, rateDown, true);
    }
    /**
     * Constant rates; choose snap behavior on first sample.
     */
    public SlewLimiter(double rateUp, double rateDown, boolean snapOnFirst) {
        this(() -> rateUp, () -> rateDown, snapOnFirst);
    }

    /**
     * Live rates; choose snap behavior on first sample.
     */
    public SlewLimiter(DoubleSupplier rateUpSup, DoubleSupplier rateDownSup, boolean snapOnFirst) {
        this.rateUpSup = rateUpSup;
        this.rateDownSup = rateDownSup;
        this.snapOnFirst = snapOnFirst;
    }

    @Override
    public Double apply(Double target, double dtSeconds) {
        if (first) {
            first = false;
            if (snapOnFirst) prev = target;
            // else keep prev as-is (default 0) and ramp up to target
        }
        double up = Math.abs(rateUpSup.getAsDouble());
        double down = Math.abs(rateDownSup.getAsDouble());
        prev = MathUtil.slewStep(prev, target, up, down, dtSeconds);
        return prev;
    }

    /**
     * Reset internal state (e.g., when disabling/enabling control).
     */
    public void reset(double value) {
        this.prev = value;
        this.first = false;
    }
}

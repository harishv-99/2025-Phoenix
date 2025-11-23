package edu.ftcphoenix.fw2.filters.scalar;

import edu.ftcphoenix.fw2.filters.Filter;
import edu.ftcphoenix.fw2.util.MathUtil;

/**
 * Jerk limiter: constrains the change of the rate (acceleration) per second^2.
 * Chain before a SlewLimiter for ultra-gentle motion on arms/elevators.
 */
public final class JerkLimiter implements Filter<Double> {
    private final double maxJerk; // units/sec^2
    private double vPrev = 0.0;   // last output
    private double aPrev = 0.0;   // last rate (units/sec)

    public JerkLimiter(double maxJerk) {
        this.maxJerk = Math.max(0, maxJerk);
    }

    @Override
    public Double apply(Double target, double dt) {
        double dtc = Math.max(0, dt);
        double aDesired = (dtc > 0) ? (target - vPrev) / dtc : 0.0;
        double aLimited = MathUtil.clamp(aDesired, aPrev - maxJerk * dtc, aPrev + maxJerk * dtc);
        double v = vPrev + aLimited * dtc;
        vPrev = v;
        aPrev = aLimited;
        return v;
    }
}

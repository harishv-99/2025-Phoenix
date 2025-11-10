package edu.ftcphoenix.fw.util;

/**
 * First-order rate limiter for scalar commands.
 *
 * <h3>Why</h3>
 * Command steps (e.g., motor power, setpoint jumps) can cause brownouts or overshoot.
 * This limiter guarantees the output changes no faster than the configured rise/fall
 * rates, based on the loop's {@code dtSec}.
 *
 * <h3>Best practices</h3>
 * <ul>
 *   <li>Call {@link #reset(double)} when enabling a mechanism to avoid a long slew from 0.</li>
 *   <li>Use asymmetric rise/fall when deceleration must be faster than acceleration.</li>
 *   <li>Do not call blocking sleeps; just pass {@code dtSec} each loop.</li>
 * </ul>
 */
public final class RateLimiter {
    private final double risePerSec;
    private final double fallPerSec;
    private double y;

    /**
     * @param risePerSec max positive slope (units per second) when increasing output
     * @param fallPerSec max magnitude (units per second) when decreasing output; must be positive
     *                   (actual negative slope limit is -fallPerSec)
     * @param initial    initial output value
     */
    public RateLimiter(double risePerSec, double fallPerSec, double initial) {
        this.risePerSec = Math.max(0.0, risePerSec);
        this.fallPerSec = Math.max(0.0, fallPerSec);
        this.y = initial;
    }

    /**
     * Symmetric limiter with the same rise/fall limits.
     */
    public static RateLimiter symmetric(double ratePerSec, double initial) {
        double r = Math.max(0.0, ratePerSec);
        return new RateLimiter(r, r, initial);
    }

    /**
     * Reset the internal state (use when (re)enabling a mechanism).
     */
    public void reset(double value) {
        this.y = value;
    }

    /**
     * Apply rate limiting toward {@code target} given loop delta time.
     *
     * @param target desired value
     * @param dtSec  non-negative loop delta time (seconds)
     * @return new limited output
     */
    public double update(double target, double dtSec) {
        if (dtSec < 0) dtSec = 0;
        final double up = risePerSec * dtSec;
        final double dn = fallPerSec * dtSec;

        double err = target - y;
        if (err > up) y += up;
        else if (err < -dn) y -= dn;
        else y = target;

        return y;
    }

    /**
     * Last output.
     */
    public double value() {
        return y;
    }
}

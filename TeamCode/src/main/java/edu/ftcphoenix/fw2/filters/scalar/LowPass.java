package edu.ftcphoenix.fw2.filters.scalar;

import edu.ftcphoenix.fw2.filters.Filter;
import edu.ftcphoenix.fw2.util.MathUtil;

import java.util.function.DoubleSupplier;

/**
 * First-order low-pass filter y' = (1 - α) y + α x with α = dt / (τ + dt).
 * <p>
 * Use when:
 * - You want to smooth noisy signals or soften sharp stick changes.
 * - τ (seconds) controls smoothness: larger τ -> more smoothing (slower).
 * <p>
 * Best practice:
 * - Make τ live if you want dynamic smoothing (e.g., stronger smoothing in precision mode).
 * - For deterministic startup, set an initial value or call reset().
 */
public final class LowPass implements Filter<Double> {
    private final DoubleSupplier tauSup; // seconds
    private double y;                    // state

    /**
     * Constant τ with initial state 0.
     */
    public LowPass(double tauSeconds) {
        this(() -> tauSeconds, 0.0);
    }

    /**
     * Constant τ with explicit initial state.
     */
    public LowPass(double tauSeconds, double initial) {
        this(() -> tauSeconds, initial);
    }

    /**
     * Live τ with explicit initial state.
     */
    public LowPass(DoubleSupplier tauSecondsSup, double initial) {
        this.tauSup = () -> Math.max(0.0, tauSecondsSup.getAsDouble());
        this.y = initial;
    }

    @Override
    public Double apply(Double x, double dt) {
        double tau = tauSup.getAsDouble();
        if (tau <= 0.0) { // bypass smoothing
            y = x;
            return y;
        }
        double alpha = MathUtil.lowPassAlphaFromTau(dt, tau);
        y = MathUtil.lowPassStep(y, x, alpha);
        return y;
    }

    /**
     * Reset internal state (e.g., on enable).
     */
    public void reset(double value) {
        this.y = value;
    }
}

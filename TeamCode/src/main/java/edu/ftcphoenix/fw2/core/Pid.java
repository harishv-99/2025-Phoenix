package edu.ftcphoenix.fw2.core;

import edu.ftcphoenix.fw2.util.MathUtil;

/**
 * General-purpose PID with integral clamp, output clamp, optional derivative low-pass,
 * D-on-measurement, and conditional-integration anti-windup.
 *
 * <h2>When to use</h2>
 * <ul>
 *   <li>Scalar control problems (velocity, position, heading) that benefit from standard PID behavior.</li>
 *   <li>Situations where actuator saturation can occur (use {@link #withConditionalIntegration(boolean)}).</li>
 * </ul>
 *
 * <h2>Key features</h2>
 * <ul>
 *   <li><b>Integral guard:</b> {@link #withIntegralLimit(double)} caps the integral term.</li>
 *   <li><b>Output limits:</b> {@link #withOutputLimits(double, double)} clamps the final output.</li>
 *   <li><b>Derivative filtering:</b> {@link #withDerivativeLpf(double)} or {@link #withDerivativeTauSeconds(double)}.</li>
 *   <li><b>D on measurement:</b> {@link #withDOnMeasurement(boolean)} reduces derivative kick.</li>
 *   <li><b>Anti-windup:</b> {@link #withConditionalIntegration(boolean)} integrates only when not pushing further into saturation.</li>
 * </ul>
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * Pid pid = new Pid(kP, kI, kD)
 *     .withOutputLimits(-1, 1)
 *     .withIntegralLimit(0.5)
 *     .withDerivativeLpf(0.2)
 *     .withConditionalIntegration(true);
 * double u = pid.update(error, clock.dtSec());
 * }</pre>
 *
 * <h2>Implementation notes</h2>
 * <ul>
 *   <li>{@link #withDerivativeTauSeconds(double)} currently uses a nominal dt (0.02 s) to compute alpha.
 *       If your loop dt varies significantly, consider a variant that derives alpha from the live dt,
 *       or pass your nominal dt into this class so alpha matches your system.</li>
 * </ul>
 */
public final class Pid implements PidController {
    private double kP, kI, kD;

    // Integral & derivative state
    private double iState = 0.0;
    private double prevError = 0.0;
    private boolean first = true;

    // Configurable guards
    private double iLimit = 1e9;                 // abs cap on integral term
    private double outMin = -1e9, outMax = 1e9;  // output clamp
    private boolean dOnMeasurement = false;      // true = D on measurement
    private double dLpfAlpha = 1.0;              // 1.0 disables filtering
    private double dFiltered = 0.0;              // LPF state

    // Optional anti-windup mode
    private boolean conditionalIntegration = false;

    public Pid(double p, double i, double d) {
        this.kP = p;
        this.kI = i;
        this.kD = d;
    }

    // ---- Fluent config ----
    public Pid withIntegralLimit(double limitAbs) {
        this.iLimit = Math.abs(limitAbs);
        return this;
    }

    public Pid withOutputLimits(double min, double max) {
        this.outMin = Math.min(min, max);
        this.outMax = Math.max(min, max);
        return this;
    }

    /**
     * alpha in (0,1]; 1 disables filter.
     */
    public Pid withDerivativeLpf(double alpha) {
        this.dLpfAlpha = Math.max(1e-6, Math.min(1.0, alpha));
        return this;
    }

    /**
     * Set D LPF via time constant (seconds).
     */
    public Pid withDerivativeTauSeconds(double tauSeconds) {
        // Convert tau→alpha using the same rule as our LowPass
        this.dLpfAlpha = MathUtil.lowPassAlphaFromTau( /*dt*/ 0.02, // nominal dt; real filtering still stable
                Math.max(0.0, tauSeconds));
        return this;
    }

    public Pid withDOnMeasurement(boolean on) {
        this.dOnMeasurement = on;
        return this;
    }

    public Pid withGains(double p, double i, double d) {
        this.kP = p;
        this.kI = i;
        this.kD = d;
        return this;
    }

    /**
     * Only integrate when not driving deeper into saturation.
     */
    public Pid withConditionalIntegration(boolean on) {
        this.conditionalIntegration = on;
        return this;
    }

    @Override
    public void reset() {
        iState = 0.0;
        prevError = 0.0;
        dFiltered = 0.0;
        first = true;
    }

    @Override
    public double update(double error, double dtSec) {
        // Guard dt
        if (!Double.isFinite(dtSec) || dtSec <= 0) dtSec = 0;

        // Derivative term
        double rawD;
        if (first || dtSec == 0) {
            rawD = 0.0;
            first = false;
        } else {
            double dErr = error - prevError;
            double slope = dErr / Math.max(dtSec, 1e-6);
            rawD = dOnMeasurement ? (-slope) : slope;
        }
        prevError = error;

        // LPF for D
        dFiltered = dLpfAlpha * rawD + (1.0 - dLpfAlpha) * dFiltered;

        // Provisional integral
        double newI = iState + error * dtSec;
        if (Math.abs(newI) > iLimit) newI = Math.copySign(iLimit, newI);

        // Provisional output (before clamp)
        double uCandidate = kP * error + kI * newI + kD * dFiltered;

        // Conditional integration: if saturated AND uCandidate pushes further out, don't integrate
        if (conditionalIntegration) {
            boolean satLow = uCandidate < outMin;
            boolean satHigh = uCandidate > outMax;
            if ((satLow && error < 0) ||   // negative error would push more negative
                    (satHigh && error > 0)) {   // positive error would push more positive
                newI = iState;              // hold integral
                uCandidate = kP * error + kI * newI + kD * dFiltered;
            }
        }

        iState = newI;

        // Output clamp
        double u = uCandidate;
        if (u < outMin) u = outMin;
        else if (u > outMax) u = outMax;

        return u;
    }

    /**
     * Convenience to compute from measurement and setpoint.
     * Equivalent to {@code update(setpoint - measurement, dtSec)}.
     */
    public double update(double measurement, double setpoint, double dtSec) {
        return update(setpoint - measurement, dtSec);
    }
}

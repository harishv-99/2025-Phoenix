package edu.ftcphoenix.fw2.drive.source;

import edu.ftcphoenix.fw2.core.FrameClock;
import edu.ftcphoenix.fw2.core.PidController;
import edu.ftcphoenix.fw2.drive.DriveSignal;
import edu.ftcphoenix.fw2.drive.DriveSource;
import edu.ftcphoenix.fw2.sensing.FeedbackSample;
import edu.ftcphoenix.fw2.sensing.FeedbackSource;

import static java.lang.Math.*;

/**
 * TagAimTurnSource
 * <p>
 * Consumes a bearing-to-target (degrees; + = target is to the left/CCW) from a time-aware FeedbackSource
 * and produces an omega-only DriveSignal using a PID on the bearing error.
 * <p>
 * Conventions:
 * - Input units: degrees (tune kP accordingly).
 * - Output: DriveSignal with only omega set (x,y = 0). Omega is clipped to [-maxOmegaAbs, +maxOmegaAbs].
 * - Inside deadband: output 0 and reset integrator to avoid windup.
 * <p>
 * Vision loss behavior is controlled by LossPolicy (see enum docs below).
 */
public final class TagAimTurnSource implements DriveSource {

    /**
     * LossPolicy describes what to do when the bearing source reports invalid (e.g., tag not visible).
     * <p>
     * STOP_AND_RESET:
     * - Output 0, call pid.reset(). Best when you want immediate stop and a clean restart on reacquire.
     * <p>
     * STOP_HOLD_STATE:
     * - Output 0, keep PID state. Resume smoothly on reacquire, but integrator/derivative history is preserved.
     * <p>
     * HOLD_OUTPUT:
     * - Keep the last commanded omega, optionally decaying exponentially with holdDecayPerSec.
     * - Does NOT step the PID during loss (prevents windup). Good when you want a short coast-through.
     * <p>
     * P_ON_LAST_ERROR:
     * - Compute a P-only output from the last valid error (dt=0 → no I/D). Keeps “some correction”
     * without accumulating integral while the target is lost.
     */
    public enum LossPolicy {STOP_AND_RESET, STOP_HOLD_STATE, HOLD_OUTPUT, P_ON_LAST_ERROR}

    // ==== Inputs & control ====
    private final FeedbackSource<Double> bearingSource; // degrees, + = CCW/left
    private final PidController pid;                    // error-centric: update(errorDeg, dtSec)

    // ==== Tuning knobs ====
    private double deadbandDeg = 1.0;        // inside ±deadband → zero output + reset I
    private double maxOmegaAbs = 0.8;        // symmetric clamp on omega command
    private LossPolicy lossPolicy = LossPolicy.STOP_AND_RESET;
    private double holdDecayPerSec = 0.0;    // used by HOLD_OUTPUT (0 = no decay)

    // ==== Internal state ====
    private double lastErrorDeg = 0.0;       // last valid bearing (error)
    private double lastCmd = 0.0;            // last commanded omega

    public TagAimTurnSource(FeedbackSource<Double> bearingSource, PidController pid) {
        this.bearingSource = bearingSource;
        this.pid = pid;
    }

    // ---- Fluent configuration ----

    /**
     * Set deadband in degrees (>= 0).
     */
    public TagAimTurnSource deadbandDeg(double deg) {
        this.deadbandDeg = max(0.0, deg);
        return this;
    }

    /**
     * Set symmetric omega clamp (absolute value).
     */
    public TagAimTurnSource maxOmega(double maxAbs) {
        this.maxOmegaAbs = max(0.0, abs(maxAbs));
        return this;
    }

    public TagAimTurnSource lossPolicy(LossPolicy p) {
        this.lossPolicy = (p == null) ? LossPolicy.STOP_AND_RESET : p;
        return this;
    }

    /**
     * Exponential decay rate for HOLD_OUTPUT policy (per second).
     * 0 disables decay; e.g., 1.0 ≈ 63% decay per second.
     */
    public TagAimTurnSource holdDecay(double perSec) {
        this.holdDecayPerSec = max(0.0, perSec);
        return this;
    }

    // ---- DriveSource ----
    @Override
    public DriveSignal get(FrameClock clock) {
        final long now = clock.nanoTime();
        final double dt = clock.dtSec();

        final FeedbackSample<Double> s = bearingSource.sample(now);
        if (!s.valid) {
            return onLoss(dt);
        }

        // Valid sample
        final double errorDeg = s.value;   // bearing IS the error: +CCW needs +omega
        lastErrorDeg = errorDeg;

        // Inside deadband: stop, clear integrator for crisp re-engage
        if (abs(errorDeg) <= deadbandDeg) {
            pid.reset();
            lastCmd = 0.0;
            return DriveSignal.ZERO;
        }

        // PID step (error-centric)
        final double u = pid.update(errorDeg, dt);
        lastCmd = clampOmega(u);
        return new DriveSignal(0, 0, lastCmd);
    }

    // ---- Helpers ----
    private DriveSignal onLoss(double dt) {
        switch (lossPolicy) {
            case STOP_HOLD_STATE:
                // Pause motion but keep PID history for smooth resume
                lastCmd = 0.0;
                return DriveSignal.ZERO;

            case HOLD_OUTPUT:
                // Keep last output; optionally decay toward 0
                if (holdDecayPerSec > 0 && dt > 0) {
                    final double a = exp(-holdDecayPerSec * dt);
                    lastCmd *= a;
                }
                return new DriveSignal(0, 0, clampOmega(lastCmd));

            case P_ON_LAST_ERROR:
                // Step the controller with dt=0 to suppress I and D (P-only)
                lastCmd = clampOmega(pid.update(lastErrorDeg, 0.0));
                return new DriveSignal(0, 0, lastCmd);

            case STOP_AND_RESET:
            default:
                // Immediate stop and clean state
                pid.reset();
                lastCmd = 0.0;
                return DriveSignal.ZERO;
        }
    }

    private double clampOmega(double u) {
        if (u > maxOmegaAbs) return maxOmegaAbs;
        if (u < -maxOmegaAbs) return -maxOmegaAbs;
        return u;
    }
}

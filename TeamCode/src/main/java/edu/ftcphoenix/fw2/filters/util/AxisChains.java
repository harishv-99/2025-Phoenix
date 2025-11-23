package edu.ftcphoenix.fw2.filters.util;

import java.util.function.DoubleSupplier;

import edu.ftcphoenix.fw2.filters.Filter;
import edu.ftcphoenix.fw2.filters.Pipeline;
import edu.ftcphoenix.fw2.filters.scalar.Deadband;
import edu.ftcphoenix.fw2.filters.scalar.Expo;
import edu.ftcphoenix.fw2.filters.scalar.JerkLimiter;
import edu.ftcphoenix.fw2.filters.scalar.LowPass;
import edu.ftcphoenix.fw2.filters.scalar.Median3;
import edu.ftcphoenix.fw2.filters.scalar.MovingAverage;
import edu.ftcphoenix.fw2.filters.scalar.Quantize;
import edu.ftcphoenix.fw2.filters.scalar.SafeClamp;
import edu.ftcphoenix.fw2.filters.scalar.Scale;
import edu.ftcphoenix.fw2.filters.scalar.SlewLimiter;

/**
 * AxisChains — tiny factory for common scalar (double → double) filter pipelines.
 *
 * <p><b>Two flavors:</b></p>
 * <ul>
 *   <li><b>Core</b> chains (no final clamp): use these <i>inside</i> larger pipelines
 *       (e.g., with DriveAxesFilter → DrivePipeline) so the true sink-side clamp can live at the end.</li>
 *   <li><b>Final</b> chains (with {@link SafeClamp}): use these when you are going
 *       <i>directly</i> to a sink (motors/servos) and want sink protection here.</li>
 * </ul>
 *
 * <p><b>Best practice:</b> there should be exactly one <i>final</i> safe clamp close to the sink.
 * Redundant SafeClamps won’t break things, but they can hide where a NaN first appeared.</p>
 */
public final class AxisChains {
    private AxisChains() {
    }

    // ===== TeleOp feel =====

    /**
     * TeleOp feel (core): deadband → expo → slew → scale.
     * <p>No final clamp. Use when this axis chain will feed into another pipeline that
     * will add its own sink-side clamp (or when mixer/normalizer does the final limiting).</p>
     */
    public static Filter<Double> teleopAxisCore(double deadband,
                                                double expo,
                                                double slewRate,
                                                DoubleSupplier liveScale) {
        return new Pipeline<Double>()
                .add(new Deadband(deadband, false))
                .add(new Expo(expo))
                .add(new SlewLimiter(slewRate))
                .add(new Scale(liveScale));
    }

    /**
     * TeleOp feel (final): teleopAxisCore + SafeClamp(|limit|).
     * <p>Use when writing straight to a sink.</p>
     */
    public static Filter<Double> teleopAxis(double deadband,
                                            double expo,
                                            double slewRate,
                                            DoubleSupplier liveScale,
                                            double absLimit) {
        return new Pipeline<Double>()
                .add(teleopAxisCore(deadband, expo, slewRate, liveScale))
                .add(SafeClamp.symmetric(absLimit));
    }

    // ===== Gentle motion =====

    /**
     * Gentle motion (core): jerk-limit → slew-limit. No final clamp.
     */
    public static Filter<Double> gentleMotionCore(double maxJerk,
                                                  double maxRate) {
        return new Pipeline<Double>()
                .add(new JerkLimiter(maxJerk))
                .add(new SlewLimiter(maxRate));
    }

    /**
     * Gentle motion (final): gentleMotionCore + SafeClamp(|limit|).
     */
    public static Filter<Double> gentleMotion(double maxJerk,
                                              double maxRate,
                                              double absLimit) {
        return new Pipeline<Double>()
                .add(gentleMotionCore(maxJerk, maxRate))
                .add(SafeClamp.symmetric(absLimit));
    }

    // ===== Motor power (open-loop compensation) =====

    /**
     * Motor power (final): voltage-comp → static-friction → SafeClamp(|limit|).
     * <p>This one is typically used directly at the motor sink, so we <i>only</i> provide the final form.</p>
     */
    public static Filter<Double> motorPower(DoubleSupplier voltageNow,
                                            double nominalV,
                                            double kS,
                                            double absLimit) {
        return new Pipeline<Double>()
                .add(new edu.ftcphoenix.fw2.filters.scalar.VoltageCompensate(voltageNow, nominalV))
                .add(new edu.ftcphoenix.fw2.filters.scalar.StaticFrictionComp(kS))
                .add(SafeClamp.symmetric(absLimit));
    }

    // ===== Precision jog =====

    /**
     * Precision jog (core): deadband → scale → slew → quantize. No final clamp.
     */
    public static Filter<Double> precisionJogCore(double deadband,
                                                  DoubleSupplier scale,
                                                  double slewRate,
                                                  double step) {
        return new Pipeline<Double>()
                .add(new Deadband(deadband, false))
                .add(new Scale(scale))
                .add(new SlewLimiter(slewRate))
                .add(new Quantize(step));
    }

    /**
     * Precision jog (final): precisionJogCore + SafeClamp(lo, hi).
     */
    public static Filter<Double> precisionJog(double deadband,
                                              DoubleSupplier scale,
                                              double slewRate,
                                              double step,
                                              double lo,
                                              double hi) {
        return new Pipeline<Double>()
                .add(precisionJogCore(deadband, scale, slewRate, step))
                .add(new SafeClamp(lo, hi));
    }

    // ===== Smoothing =====

    /**
     * Measurement smoother: median3 → moving average (N) → optional low-pass(τ).
     * <p>No clamp; smoothing is typically internal, not a sink.</p>
     */
    public static Filter<Double> smoothMeasurement(int movingAvgWindow,
                                                   double lowPassTauSeconds) {
        Pipeline<Double> p = new Pipeline<Double>()
                .add(new Median3())
                .add(new MovingAverage(Math.max(1, movingAvgWindow)));
        if (lowPassTauSeconds > 0) {
            p.add(new LowPass(lowPassTauSeconds));
        }
        return p;
    }
}

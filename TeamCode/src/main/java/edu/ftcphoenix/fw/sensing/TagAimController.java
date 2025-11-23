package edu.ftcphoenix.fw.sensing;

import edu.ftcphoenix.fw.core.PidController;
import edu.ftcphoenix.fw.sensing.BearingSource.BearingSample;
import edu.ftcphoenix.fw.util.DebugSink;
import edu.ftcphoenix.fw.util.LoopClock;
import edu.ftcphoenix.fw.util.MathUtil;

/**
 * Controller that turns a target bearing measurement into a turn command (omega).
 *
 * <h2>Role</h2>
 * <p>{@code TagAimController} is responsible for the <em>control law</em> part of aiming:
 * given a {@link BearingSample} (from a {@link BearingSource}) and loop timing
 * information ({@link LoopClock}), it computes an angular velocity command
 * {@code omega} that tries to drive the bearing to zero.
 *
 * <p>It does <strong>not</strong> know or care how the bearing was measured
 * (AprilTags, other vision targets, synthetic sources, etc.). That wiring is
 * handled elsewhere (for example, by {@code TagAim} helpers).
 *
 * <h2>Typical usage</h2>
 * <pre>{@code
 * BearingSource bearing = ...;  // e.g., built from AprilTagSensor
 * PidController pid = ...;     // your PID implementation/config
 *
 * TagAimController aim = new TagAimController(
 *         pid,
 *         Math.toRadians(1.0),                 // deadband: 1 degree
 *         0.8,                                 // max omega
 *         TagAimController.LossPolicy.ZERO_OUTPUT_RESET_I);
 *
 * // In your TeleOp or Auto loop:
 * BearingSample sample = bearing.sample(clock);
 * double omega = aim.update(clock, sample);
 * }</pre>
 */
public final class TagAimController {

    /**
     * Policy for what to do when the target is not visible (lost).
     */
    public enum LossPolicy {
        /**
         * Set output omega to zero and reset the underlying PID state.
         *
         * <p>This is a safe default for TeleOp: as soon as the target is lost,
         * the robot stops turning and the controller forgets any accumulated
         * integral error.
         */
        ZERO_OUTPUT_RESET_I,

        /**
         * Hold the last computed omega and prevent further integral accumulation.
         *
         * <p>Useful when you want aiming to coast briefly through short vision
         * dropouts, but do not want integral windup.
         */
        HOLD_LAST_NO_I,

        /**
         * Set output omega to zero, but do not reset the PID.
         *
         * <p>Can be useful if you expect the same target to reappear quickly
         * and you want to keep accumulated integral state.
         */
        ZERO_NO_RESET
    }

    private final PidController pid;
    private final double deadbandRad;
    private final double maxOmega;
    private final LossPolicy lossPolicy;

    private double lastOmega = 0.0;

    // For debugging:
    private double lastErrorRad = 0.0;
    private boolean lastHasTarget = false;

    /**
     * Construct a tag aim controller.
     *
     * @param pid         PID implementation used to turn bearing error into omega
     * @param deadbandRad deadband around zero bearing (in radians) where no output is produced
     * @param maxOmega    absolute maximum omega command (output is clamped to [-maxOmega, +maxOmega])
     * @param lossPolicy  policy to apply when no target is visible
     */
    public TagAimController(PidController pid,
                            double deadbandRad,
                            double maxOmega,
                            LossPolicy lossPolicy) {
        this.pid = pid;
        this.deadbandRad = deadbandRad;
        this.maxOmega = maxOmega;
        this.lossPolicy = lossPolicy;
    }

    /**
     * Update the controller for a new bearing sample.
     *
     * <p>This method applies the following logic:
     * <ol>
     *   <li>If the target is lost:
     *     <ul>
     *       <li>Apply the configured {@link LossPolicy}.</li>
     *       <li>Return the resulting {@code omega}.</li>
     *     </ul>
     *   </li>
     *   <li>Otherwise:
     *     <ul>
     *       <li>Compute error = {@code bearingRad} (we want bearing → 0).</li>
     *       <li>If {@code |error| < deadbandRad}, output zero.</li>
     *       <li>Else, run the PID and clamp omega to [-maxOmega, +maxOmega].</li>
     *     </ul>
     *   </li>
     * </ol>
     *
     * @param clock  loop clock for the current iteration (used for dtSec)
     * @param sample current bearing sample from a {@link BearingSource}
     * @return commanded turn rate omega, in [-maxOmega, +maxOmega]
     */
    public double update(LoopClock clock, BearingSample sample) {
        double dtSec = clock.dtSec();
        lastHasTarget = sample.hasTarget;

        if (!sample.hasTarget) {
            lastErrorRad = 0.0;
            handleLoss();
            return lastOmega;
        }

        double error = sample.bearingRad;
        lastErrorRad = error;

        if (Math.abs(error) < deadbandRad) {
            lastOmega = 0.0;
            return lastOmega;
        }

        double raw = pid.update(error, dtSec);
        double clamped = MathUtil.clampAbs(raw, maxOmega);
        lastOmega = clamped;
        return lastOmega;
    }

    /**
     * Last commanded omega value.
     *
     * <p>This can be useful for logging or for advanced loss policies that
     * need to inspect the previous output.
     */
    public double getLastOmega() {
        return lastOmega;
    }

    /**
     * Apply the configured loss policy when the target is not visible.
     */
    private void handleLoss() {
        switch (lossPolicy) {
            case ZERO_OUTPUT_RESET_I:
                pid.reset();
                lastOmega = 0.0;
                break;

            case HOLD_LAST_NO_I:
                // Keep lastOmega; do not touch PID state here.
                // Callers may choose to reset separately if desired.
                break;

            case ZERO_NO_RESET:
                lastOmega = 0.0;
                // PID state is preserved; integral may continue when target reappears.
                break;
        }
    }

    /**
     * Emit current aiming configuration and last update state.
     *
     * @param dbg    debug sink (never null)
     * @param prefix base key prefix, e.g. "tagAim"
     */
    public void debugDump(DebugSink dbg, String prefix) {
        String p = (prefix == null || prefix.isEmpty()) ? "tagAim" : prefix;
        dbg.addLine(p)
                .addData(p + ".deadbandRad", deadbandRad)
                .addData(p + ".deadbandDeg", Math.toDegrees(deadbandRad))
                .addData(p + ".maxOmega", maxOmega)
                .addData(p + ".lossPolicy", lossPolicy.name())
                .addData(p + ".lastHasTarget", lastHasTarget)
                .addData(p + ".lastErrorRad", lastErrorRad)
                .addData(p + ".lastErrorDeg", Math.toDegrees(lastErrorRad))
                .addData(p + ".lastOmega", lastOmega);
    }
}

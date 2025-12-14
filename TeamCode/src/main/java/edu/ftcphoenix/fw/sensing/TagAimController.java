package edu.ftcphoenix.fw.sensing;

import edu.ftcphoenix.fw.core.PidController;
import edu.ftcphoenix.fw.debug.DebugSink;
import edu.ftcphoenix.fw.sensing.BearingSource.BearingSample;
import edu.ftcphoenix.fw.util.LoopClock;
import edu.ftcphoenix.fw.util.MathUtil;

/**
 * Controller that turns a target bearing measurement into a turn command (omega).
 *
 * <h2>Role</h2>
 * <p>{@code TagAimController} is responsible for the <em>control law</em> part of aiming:
 * given a {@link BearingSample} (from a {@link BearingSource}) and loop timing
 * information ({@link LoopClock}), it computes an angular command {@code omega}
 * that tries to drive the bearing to zero (target centered).</p>
 *
 * <h2>Sign conventions</h2>
 * <p>
 * Typical {@link BearingSource} implementations (including those derived from
 * {@link edu.ftcphoenix.fw.sensing.AprilTagObservation}) use a math-style
 * convention where:
 * </p>
 * <ul>
 *   <li>{@code bearingRad = 0} means the target is directly in front.</li>
 *   <li>{@code bearingRad > 0} means the target appears to the <b>left</b>
 *       (counter-clockwise from forward).</li>
 *   <li>{@code bearingRad < 0} means the target appears to the <b>right</b>
 *       (clockwise from forward).</li>
 * </ul>
 *
 * <p>
 * {@code TagAimController} produces an {@code omega} intended to be used as
 * {@link edu.ftcphoenix.fw.drive.DriveSignal#omega}, which in Phoenix follows:
 * </p>
 * <ul>
 *   <li>{@code omega > 0} &rarr; rotate <b>counter-clockwise</b> (turn left).</li>
 *   <li>{@code omega < 0} &rarr; rotate <b>clockwise</b> (turn right).</li>
 * </ul>
 *
 * <p>
 * With these conventions, the correct steering behavior is:
 * </p>
 * <ul>
 *   <li>Target left (positive bearing) &rarr; command positive omega (turn left).</li>
 *   <li>Target right (negative bearing) &rarr; command negative omega (turn right).</li>
 * </ul>
 *
 * <p>It does <strong>not</strong> know or care how the bearing was measured
 * (AprilTags, other vision targets, synthetic sources, etc.). That wiring is
 * handled elsewhere (for example, by {@code TagAim} helpers or
 * {@link edu.ftcphoenix.fw.drive.source.TagAimDriveSource}).</p>
 *
 * <h2>Typical usage</h2>
 *
 * <pre>{@code
 * BearingSource bearing = ...;  // e.g., built from AprilTagSensor
 * PidController pid = ...;     // your PID implementation/config
 *
 * TagAimController aim = new TagAimController(
 *         pid,
 *         Math.toRadians(1.0),                 // deadband: 1 degree
 *         0.8,                                 // max |omega| (command units)
 *         TagAimController.LossPolicy.ZERO_OUTPUT_RESET_I
 * );
 *
 * // In your TeleOp or Auto loop:
 * BearingSource.BearingSample sample = bearing.sample(clock);
 * double omega = aim.update(clock, sample);
 * }</pre>
 */
public final class TagAimController {

    /**
     * Policy for what to do when the target is not visible (lost).
     */
    public enum LossPolicy {
        /**
         * Set output omega to zero and reset the PID integral state.
         *
         * <p>
         * This is the safest default for most robots: when vision loses the
         * target, aiming immediately stops and any accumulated integral term
         * is cleared so that it does not cause a large jump when the target
         * reappears.
         * </p>
         */
        ZERO_OUTPUT_RESET_I,

        /**
         * Hold the last computed omega and prevent further integral accumulation.
         *
         * <p>
         * Useful when you want aiming to coast briefly through short vision
         * dropouts, but do not want integral windup during the loss. While the
         * target is lost, {@link #update(LoopClock, BearingSample)} does not
         * call {@link PidController#update(double, double)}, so no further
         * integral or derivative updates occur.
         * </p>
         */
        HOLD_LAST_NO_I,

        /**
         * Set output omega to zero, but do not reset the PID.
         *
         * <p>
         * Can be useful if you expect the same target to reappear quickly and
         * you want to keep accumulated integral state. During the loss window,
         * output is zero, but the PID state is preserved for when the target
         * comes back.
         * </p>
         */
        ZERO_NO_RESET
    }

    private final PidController pid;
    private final double deadbandRad;
    private final double maxOmega;
    private final LossPolicy lossPolicy;

    private double lastOmega = 0.0;

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
        if (pid == null) {
            throw new IllegalArgumentException("PidController is required");
        }
        if (lossPolicy == null) {
            lossPolicy = LossPolicy.ZERO_OUTPUT_RESET_I;
        }

        // Interpret deadband and maxOmega as magnitudes.
        if (deadbandRad < 0.0) {
            deadbandRad = -deadbandRad;
        }
        if (maxOmega < 0.0) {
            maxOmega = -maxOmega;
        }

        this.pid = pid;
        this.deadbandRad = deadbandRad;
        this.maxOmega = maxOmega;
        this.lossPolicy = lossPolicy;
    }

    /**
     * Update the controller given the latest bearing sample and loop timing.
     *
     * <p>High-level behavior:</p>
     * <ul>
     *   <li>If {@code sample == null} or {@code !sample.hasTarget}, apply the configured
     *       {@link LossPolicy} and return the resulting {@code lastOmega}.</li>
     *   <li>If {@code |bearing| < deadbandRad}, treat as on-target:
     *     <ul>
     *       <li>Set {@code lastOmega = 0}.</li>
     *       <li>Do <em>not</em> update the PID (no further integral/d-state).</li>
     *     </ul>
     *   </li>
     *   <li>Otherwise:
     *     <ul>
     *       <li>Use {@code error = bearingRad} (desired bearing is 0).</li>
     *       <li>Call {@link PidController#update(double, double)} with
     *           {@code (error, clock.dtSec())}.</li>
     *       <li>Clamp the result to {@code [-maxOmega, +maxOmega]}.</li>
     *       <li>Store and return the clamped value as {@code lastOmega}.</li>
     *     </ul>
     *   </li>
     * </ul>
     *
     * @param clock  loop timing source (for dt); must not be null
     * @param sample latest bearing sample (may be null)
     * @return commanded omega in [-maxOmega, +maxOmega] (Phoenix: + is CCW / turn left)
     */
    public double update(LoopClock clock, BearingSample sample) {
        if (sample == null) {
            // Treat "no sample" as equivalent to "no target".
            handleLoss();
            return lastOmega;
        }

        double dtSec = clock.dtSec();

        if (!sample.hasTarget) {
            handleLoss();
            return lastOmega;
        }

        // Desired bearing is 0. With Phoenix conventions, bearing>0 (target left) -> omega>0 (turn left).
        double errorRad = sample.bearingRad;

        // Inside deadband: treat as on-target; no turn command and no PID update.
        if (Math.abs(errorRad) < deadbandRad) {
            lastOmega = 0.0;
            return lastOmega;
        }

        double raw = pid.update(errorRad, dtSec);
        lastOmega = MathUtil.clamp(raw, -maxOmega, maxOmega);
        return lastOmega;
    }

    private void handleLoss() {
        switch (lossPolicy) {
            case ZERO_OUTPUT_RESET_I:
                lastOmega = 0.0;
                pid.reset();
                break;

            case HOLD_LAST_NO_I:
                // Keep lastOmega; do not touch PID state here.
                // Callers may choose to reset separately if desired.
                break;

            case ZERO_NO_RESET:
                // Zero output but keep PID state.
                lastOmega = 0.0;
                break;

            default:
                // Defensive default: behave like ZERO_OUTPUT_RESET_I.
                lastOmega = 0.0;
                pid.reset();
                break;
        }
    }

    /**
     * Last commanded omega value.
     *
     * <p>This can be useful for logging or for advanced logic that wants to
     * inspect the most recent controller output.</p>
     */
    public double getLastOmega() {
        return lastOmega;
    }

    /**
     * @return configured deadband (radians).
     */
    public double getDeadbandRad() {
        return deadbandRad;
    }

    /**
     * @return configured maximum omega magnitude.
     */
    public double getMaxOmega() {
        return maxOmega;
    }

    /**
     * @return configured loss policy.
     */
    public LossPolicy getLossPolicy() {
        return lossPolicy;
    }

    /**
     * Dump controller configuration and last omega to the provided {@link DebugSink}.
     *
     * @param dbg    debug sink (may be {@code null}; if null, no output is produced)
     * @param prefix base key prefix, e.g. "tagAim"
     */
    public void debugDump(DebugSink dbg, String prefix) {
        if (dbg == null) {
            return;
        }
        String p = (prefix == null || prefix.isEmpty()) ? "tagAimCtrl" : prefix;

        dbg.addLine(p + ": TagAimController");

        // Static configuration.
        dbg.addData(p + ".deadbandRad", deadbandRad);
        dbg.addData(p + ".maxOmega", maxOmega);
        dbg.addData(p + ".lossPolicy", lossPolicy.name());
        dbg.addData(p + ".pid.class", pid.getClass().getSimpleName());

        // Dynamic state.
        dbg.addData(p + ".lastOmega", lastOmega);
    }
}

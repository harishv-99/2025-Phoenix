package edu.ftcphoenix.fw.drive.assist;

import edu.ftcphoenix.fw.core.control.PidController;
import edu.ftcphoenix.fw.core.debug.DebugSink;
import edu.ftcphoenix.fw.drive.assist.BearingSource.BearingSample;
import edu.ftcphoenix.fw.core.time.LoopClock;
import edu.ftcphoenix.fw.sensing.vision.CameraMountConfig;
import edu.ftcphoenix.fw.sensing.vision.CameraMountLogic;
import edu.ftcphoenix.fw.sensing.vision.apriltag.TagTarget;

/**
 * Controller that converts a target bearing measurement into a turn-rate command (omega).
 *
 * <h2>Purpose</h2>
 * <p>
 * TagAim uses a {@link BearingSource} to provide a bearing measurement (radians) to a target.
 * This controller turns that bearing into an omega command to rotate the robot until bearing → 0.
 * </p>
 *
 * <h2>Sign conventions (contract)</h2>
 * <ul>
 *   <li>Bearing: {@code bearingRad > 0} means the target is to the <b>left</b> of the forward axis.</li>
 *   <li>DriveSignal: {@code omega > 0} means rotate <b>CCW</b> (turn left).</li>
 * </ul>
 *
 * <p>
 * Therefore, when {@code bearingRad > 0}, the correct behavior is generally to output
 * {@code omega > 0} so the robot turns left to reduce the error.
 * </p>
 *
 * <h2>Camera-centric vs robot-centric bearing</h2>
 * <p>
 * This class does not care how bearing is produced. Your {@link BearingSource} may be:
 * </p>
 * <ul>
 *   <li><b>Camera-centric</b> (camera forward axis faces target), e.g. {@link TagTarget#toBearingSample()}.</li>
 *   <li><b>Robot-centric</b> (robot center faces target) using {@link CameraMountConfig} via
 *       {@link CameraMountLogic} or {@link TagTarget#toRobotBearingSample(CameraMountConfig)}.</li>
 * </ul>
 *
 * <h2>Deadband and loss behavior</h2>
 * <ul>
 *   <li>If {@code |bearing| <= deadbandRad}, the output is 0 and the PID is not updated.</li>
 *   <li>If the target is lost, behavior is controlled by {@link LossPolicy}.</li>
 * </ul>
 */
public final class TagAimController {

    /**
     * Policy for what to do when {@link BearingSample#hasTarget} is false.
     */
    public enum LossPolicy {
        /**
         * Output 0 omega and reset PID state (recommended default).
         */
        ZERO_OUTPUT_RESET_I,

        /**
         * Output 0 omega but keep PID state unchanged.
         */
        ZERO_OUTPUT_HOLD_STATE,

        /**
         * Keep returning the last omega output while target is lost.
         *
         * <p>Use with care; this can cause the robot to keep spinning if the target drops out.</p>
         */
        HOLD_LAST_OUTPUT
    }

    private final PidController pid;
    private final double deadbandRad;
    private final double maxOmegaAbs;
    private final LossPolicy lossPolicy;

    // Debug / introspection
    private boolean lastHasTarget = false;
    private double lastBearingRad = 0.0;
    private double lastOmega = 0.0;
    private double lastDtSec = 0.0;

    /**
     * Construct a TagAimController.
     *
     * @param pid         PID controller that maps bearing error → omega (must not be null)
     * @param deadbandRad deadband in radians (must be >= 0)
     * @param maxOmegaAbs absolute maximum omega output (must be >= 0)
     * @param lossPolicy  behavior when target is lost (if null, defaults to {@link LossPolicy#ZERO_OUTPUT_RESET_I})
     */
    public TagAimController(PidController pid,
                            double deadbandRad,
                            double maxOmegaAbs,
                            LossPolicy lossPolicy) {
        if (pid == null) {
            throw new IllegalArgumentException("pid is required");
        }
        if (deadbandRad < 0.0) {
            throw new IllegalArgumentException("deadbandRad must be >= 0");
        }
        if (maxOmegaAbs < 0.0) {
            throw new IllegalArgumentException("maxOmegaAbs must be >= 0");
        }

        this.pid = pid;
        this.deadbandRad = deadbandRad;
        this.maxOmegaAbs = maxOmegaAbs;
        this.lossPolicy = (lossPolicy != null) ? lossPolicy : LossPolicy.ZERO_OUTPUT_RESET_I;
    }

    /**
     * Update the controller using the latest bearing measurement.
     *
     * <p>
     * Error definition: desired bearing is 0, so {@code error = sample.bearingRad}.
     * With the framework conventions, positive error should typically produce positive omega
     * (turn left) to reduce the error.
     * </p>
     *
     * @param clock  loop clock (used for dtSec)
     * @param sample bearing measurement (may be null; treated as no-target)
     * @return omega command (CCW-positive), clamped to {@code [-maxOmegaAbs, +maxOmegaAbs]}
     */
    public double update(LoopClock clock, BearingSample sample) {
        if (clock == null) {
            throw new IllegalArgumentException("clock is required");
        }

        double dtSec = clock.dtSec();
        lastDtSec = dtSec;

        if (sample == null || !sample.hasTarget) {
            lastHasTarget = false;
            lastBearingRad = 0.0;

            switch (lossPolicy) {
                case HOLD_LAST_OUTPUT:
                    return lastOmega;

                case ZERO_OUTPUT_HOLD_STATE:
                    lastOmega = 0.0;
                    return 0.0;

                case ZERO_OUTPUT_RESET_I:
                default:
                    pid.reset();
                    lastOmega = 0.0;
                    return 0.0;
            }
        }

        lastHasTarget = true;
        lastBearingRad = sample.bearingRad;

        // Deadband: do not update PID state, output zero.
        if (Math.abs(sample.bearingRad) <= deadbandRad) {
            lastOmega = 0.0;
            return 0.0;
        }

        // Bearing error is the bearing itself (desired = 0).
        double omega = pid.update(sample.bearingRad, dtSec);

        // Clamp to max magnitude.
        if (omega > maxOmegaAbs) {
            omega = maxOmegaAbs;
        } else if (omega < -maxOmegaAbs) {
            omega = -maxOmegaAbs;
        }

        lastOmega = omega;
        return omega;
    }

    /**
     * Reset controller state (delegates to {@link PidController#reset()} and clears debug state).
     */
    public void reset() {
        pid.reset();
        lastHasTarget = false;
        lastBearingRad = 0.0;
        lastOmega = 0.0;
        lastDtSec = 0.0;
    }

    /**
     * Dump controller state to a debug sink.
     *
     * @param dbg    debug sink; if null, does nothing
     * @param prefix key prefix; if null/empty, {@code "tagAimCtrl"} is used
     */
    public void debugDump(DebugSink dbg, String prefix) {
        if (dbg == null) {
            return;
        }
        String p = (prefix == null || prefix.isEmpty()) ? "tagAimCtrl" : prefix;

        dbg.addLine(p + ": TagAimController");
        dbg.addData(p + ".hasTarget", lastHasTarget);
        dbg.addData(p + ".bearingRad", lastBearingRad);
        dbg.addData(p + ".omega", lastOmega);
        dbg.addData(p + ".dtSec", lastDtSec);

        dbg.addData(p + ".deadbandRad", deadbandRad);
        dbg.addData(p + ".maxOmegaAbs", maxOmegaAbs);
        dbg.addData(p + ".lossPolicy", lossPolicy.toString());
    }

    public double deadbandRad() {
        return deadbandRad;
    }

    public double maxOmegaAbs() {
        return maxOmegaAbs;
    }

    public LossPolicy lossPolicy() {
        return lossPolicy;
    }

    public double lastOmega() {
        return lastOmega;
    }
}

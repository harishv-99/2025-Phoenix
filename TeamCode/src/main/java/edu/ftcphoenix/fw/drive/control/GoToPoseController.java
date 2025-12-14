package edu.ftcphoenix.fw.drive.control;

import java.util.Objects;

import edu.ftcphoenix.fw.drive.DriveSignal;
import edu.ftcphoenix.fw.geom.Pose2d;

/**
 * Simple holonomic controller that drives the robot toward a target pose in a
 * field-centric coordinate frame.
 *
 * <p>This controller computes a robot-centric {@link DriveSignal} given the
 * current robot pose and a desired target robot pose. It applies:</p>
 *
 * <ul>
 *   <li>A proportional position controller in the field frame to generate
 *       linear velocities.</li>
 *   <li>A {@link HeadingStrategy} to decide what heading we want to face.</li>
 *   <li>A {@link HeadingController} to turn heading error (plus optional
 *       feedforward) into the rotational command {@code omega}.</li>
 * </ul>
 *
 * <p>It does <strong>not</strong> decide when the target is reached; callers are
 * expected to check position and heading error separately and stop or switch
 * behaviors when tolerances are met.</p>
 *
 * <h2>Coordinate conventions</h2>
 *
 * <ul>
 *   <li>{@link Pose2d} uses +X forward and +Y left in the field frame.</li>
 *   <li>The returned {@link DriveSignal} is robot-centric and uses Phoenix pose conventions:
 *     <ul>
 *       <li><b>axial</b>   – forward/backward in robot frame (+ forward).</li>
 *       <li><b>lateral</b> – left/right in robot frame (+ left).</li>
 *       <li><b>omega</b>   – rotation about +Z (+ CCW / turn left viewed from above).</li>
 *     </ul>
 *   </li>
 * </ul>
 *
 * <h2>Pose naming conventions</h2>
 *
 * <ul>
 *   <li>{@code fieldRobotPose}: current robot pose expressed in the field frame.</li>
 *   <li>{@code fieldRobotTargetPose}: desired robot pose expressed in the field frame.</li>
 * </ul>
 *
 * <h2>Typical usage</h2>
 *
 * <pre>{@code
 * GoToPoseController.Config cfg = new GoToPoseController.Config();
 * cfg.kPPosition = 0.05;
 * cfg.maxSpeedInchesPerSec = 40.0;
 *
 * HeadingStrategy headingStrategy = HeadingStrategies.faceFinalHeading();
 *
 * HeadingController.Config hCfg = new HeadingController.Config();
 * hCfg.kP = 3.0;
 * hCfg.maxOmegaRadPerSec = 6.0;
 *
 * HeadingController headingCtrl = new HeadingController(hCfg);
 *
 * GoToPoseController controller =
 *     new GoToPoseController(cfg, headingStrategy, headingCtrl);
 *
 * // In your loop:
 * Pose2d fieldRobotPose = estimate.toPose2d();   // PoseEstimate.pose is Pose3d; project to Pose2d for driving
 * Pose2d fieldRobotTargetPose = someTargetPose;
 *
 * double omegaFF = 0.0; // optional feedforward (rad/sec)
 *
 * DriveSignal cmd = controller.update(fieldRobotPose, fieldRobotTargetPose, omegaFF);
 * // Send cmd to your drivebase.
 * }</pre>
 */
public final class GoToPoseController {

    /**
     * Configuration parameters for {@link GoToPoseController}.
     */
    public static final class Config {

        /**
         * Proportional gain applied to position error (inches).
         *
         * <p>The raw field-frame velocity command is:</p>
         * <pre>{@code
         * vField = kPPosition * (targetPosition - robotPosition)
         * }</pre>
         */
        public double kPPosition = 0.05;

        /**
         * Maximum linear speed command in inches per second.
         *
         * <p>The resulting field-frame velocity vector is clamped so that its
         * magnitude does not exceed this value. This helps prevent excessive
         * speeds when the target is far away.</p>
         */
        public double maxSpeedInchesPerSec = 40.0;

        /**
         * Creates a new {@code Config} with default values.
         */
        public Config() {
        }
    }

    private final double kPPosition;
    private final double maxSpeedInchesPerSec;
    private final HeadingStrategy headingStrategy;
    private final HeadingController headingController;

    /**
     * Creates a new {@code GoToPoseController}.
     *
     * @param cfg               configuration parameters (must not be {@code null})
     * @param headingStrategy   strategy to decide desired heading (must not be {@code null})
     * @param headingController controller that turns heading error into omega
     *                          (must not be {@code null})
     */
    public GoToPoseController(
            Config cfg,
            HeadingStrategy headingStrategy,
            HeadingController headingController
    ) {
        Objects.requireNonNull(cfg, "cfg must not be null");
        Objects.requireNonNull(headingStrategy, "headingStrategy must not be null");
        Objects.requireNonNull(headingController, "headingController must not be null");

        this.kPPosition = cfg.kPPosition;
        this.maxSpeedInchesPerSec = Math.abs(cfg.maxSpeedInchesPerSec);
        this.headingStrategy = headingStrategy;
        this.headingController = headingController;
    }

    /**
     * Computes a robot-centric {@link DriveSignal} that drives the robot toward
     * the given field-frame target robot pose.
     *
     * <p>This method <strong>always</strong> returns a command; it does not
     * stop or zero the outputs when the error is small. Callers are expected
     * to decide when to stop using the controller (for example, when position
     * and heading errors are within tolerances) and then take appropriate
     * action (such as commanding {@link DriveSignal#ZERO}).</p>
     *
     * @param fieldRobotPose        current robot pose in the field frame
     * @param fieldRobotTargetPose  desired target robot pose in the same field frame
     * @param omegaFeedforwardRadPerSec feedforward angular velocity in radians/second
     *                              (may be 0.0 if not used)
     * @return robot-centric {@link DriveSignal} command (axial/lateral/omega)
     */
    public DriveSignal update(
            Pose2d fieldRobotPose,
            Pose2d fieldRobotTargetPose,
            double omegaFeedforwardRadPerSec
    ) {
        Objects.requireNonNull(fieldRobotPose, "fieldRobotPose");
        Objects.requireNonNull(fieldRobotTargetPose, "fieldRobotTargetPose");

        // 1) Position error in the field frame (inches).
        double dxFieldInches = fieldRobotTargetPose.xInches - fieldRobotPose.xInches;
        double dyFieldInches = fieldRobotTargetPose.yInches - fieldRobotPose.yInches;

        // 2) Desired field-frame velocity (inches/sec) using proportional control.
        double vxFieldInchesPerSec = kPPosition * dxFieldInches;
        double vyFieldInchesPerSec = kPPosition * dyFieldInches;

        // 3) Clamp the field-frame velocity vector magnitude.
        double speedInchesPerSec = Math.hypot(vxFieldInchesPerSec, vyFieldInchesPerSec);
        if (maxSpeedInchesPerSec > 0.0 && speedInchesPerSec > maxSpeedInchesPerSec && speedInchesPerSec > 1e-9) {
            double scale = maxSpeedInchesPerSec / speedInchesPerSec;
            vxFieldInchesPerSec *= scale;
            vyFieldInchesPerSec *= scale;
        }

        // 4) Convert field-frame velocity to robot-centric axial/lateral (Phoenix convention: +lateral is left).
        double headingRad = fieldRobotPose.headingRad;
        double cos = Math.cos(headingRad);
        double sin = Math.sin(headingRad);

        // Field → robot rotation is R(-heading).
        double axial = cos * vxFieldInchesPerSec + sin * vyFieldInchesPerSec;
        double lateral = -sin * vxFieldInchesPerSec + cos * vyFieldInchesPerSec;

        // 5) Heading strategy + controller → omega (Phoenix convention: +omega is CCW).
        double desiredHeadingRad = headingStrategy.desiredHeading(fieldRobotPose, fieldRobotTargetPose);
        double omega = headingController.update(desiredHeadingRad, fieldRobotPose.headingRad, omegaFeedforwardRadPerSec);

        return new DriveSignal(axial, lateral, omega);
    }

    /**
     * Convenience overload that assumes zero feedforward.
     *
     * @param fieldRobotPose       current robot pose in the field frame
     * @param fieldRobotTargetPose desired target robot pose in the same field frame
     * @return robot-centric {@link DriveSignal} command
     */
    public DriveSignal update(Pose2d fieldRobotPose, Pose2d fieldRobotTargetPose) {
        return update(fieldRobotPose, fieldRobotTargetPose, 0.0);
    }
}

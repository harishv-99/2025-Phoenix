package edu.ftcphoenix.fw.drive.control;

import java.util.Objects;

import edu.ftcphoenix.fw.drive.ChassisSpeeds;
import edu.ftcphoenix.fw.core.geometry.Pose2d;
import edu.ftcphoenix.fw.core.math.MathUtil;

/**
 * Simple holonomic controller that drives the robot toward a target pose in a
 * field-centric coordinate frame.
 *
 * <p>This controller computes a robot-centric {@link ChassisSpeeds} given the
 * current robot pose and a desired target robot pose. It applies:</p>
 *
 * <ul>
 *   <li>A proportional position controller to generate robot-centric linear speeds.</li>
 *   <li>A {@link HeadingStrategy} to decide what heading we want to face.</li>
 *   <li>A {@link HeadingController} to turn heading error (plus optional
 *       feedforward) into {@code omegaRobotRadPerSec}.</li>
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
 *   <li>The returned {@link ChassisSpeeds} is robot-centric and uses Phoenix pose conventions:
 *     <ul>
 *       <li><b>vxRobotIps</b> – forward/backward in robot frame (+ forward).</li>
 *       <li><b>vyRobotIps</b> – left/right in robot frame (+ left).</li>
 *       <li><b>omegaRobotRadPerSec</b> – rotation about +Z (+ CCW / turn left viewed from above).</li>
 *     </ul>
 *   </li>
 * </ul>
 */
public final class GoToPoseController {

    /**
     * Configuration parameters for {@link GoToPoseController}.
     */
    public static final class Config {

        /**
         * Proportional gain applied to position error (inches).
         *
         * <p>Raw robot-centric speed commands are:</p>
         * <pre>{@code
         * vxRobotIps = kPPosition * forwardErrorInches
         * vyRobotIps = kPPosition * leftErrorInches
         * }</pre>
         */
        public double kPPosition = 0.05;

        /**
         * Maximum magnitude of {@link ChassisSpeeds#vxRobotIps} (inches/sec).
         */
        public double maxAxialInchesPerSec = 40.0;

        /**
         * Maximum magnitude of {@link ChassisSpeeds#vyRobotIps} (inches/sec).
         */
        public double maxLateralInchesPerSec = 40.0;

        /** Creates a new {@code Config} with default values. */
        public Config() {
        }
    }

    private final double kPPosition;
    private final double maxAxialInchesPerSec;
    private final double maxLateralInchesPerSec;
    private final HeadingStrategy headingStrategy;
    private final HeadingController headingController;

    /**
     * Creates a new {@code GoToPoseController}.
     *
     * @param cfg               configuration parameters (must not be {@code null})
     * @param headingStrategy   strategy to decide desired heading (must not be {@code null})
     * @param headingController controller that turns heading error into omega (must not be {@code null})
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
        this.maxAxialInchesPerSec = Math.abs(cfg.maxAxialInchesPerSec);
        this.maxLateralInchesPerSec = Math.abs(cfg.maxLateralInchesPerSec);
        this.headingStrategy = headingStrategy;
        this.headingController = headingController;
    }

    /**
     * Computes a robot-centric {@link ChassisSpeeds} that drives the robot toward
     * the given field-frame target robot pose.
     *
     * <p>This method <strong>always</strong> returns a command; it does not
     * stop or zero the outputs when the error is small. Callers are expected
     * to decide when to stop using the controller (for example, when position
     * and heading errors are within tolerances) and then take appropriate
     * action.</p>
     *
     * @param fieldRobotPose            current robot pose in the field frame
     * @param fieldRobotTargetPose      desired target robot pose in the same field frame
     * @param omegaFeedforwardRadPerSec feedforward angular velocity in radians/second (may be 0.0)
     * @return robot-centric {@link ChassisSpeeds} command
     */
    public ChassisSpeeds update(
            Pose2d fieldRobotPose,
            Pose2d fieldRobotTargetPose,
            double omegaFeedforwardRadPerSec
    ) {
        Objects.requireNonNull(fieldRobotPose, "fieldRobotPose");
        Objects.requireNonNull(fieldRobotTargetPose, "fieldRobotTargetPose");

        // Position error in field frame (inches).
        double dxFieldInches = fieldRobotTargetPose.xInches - fieldRobotPose.xInches;
        double dyFieldInches = fieldRobotTargetPose.yInches - fieldRobotPose.yInches;

        // Convert field-frame error to robot-centric error: e_r = R(-heading) * e_f
        double headingRad = fieldRobotPose.headingRad;
        double cos = Math.cos(headingRad);
        double sin = Math.sin(headingRad);

        double forwardErrorInches = dxFieldInches * cos + dyFieldInches * sin;
        double leftErrorInches = -dxFieldInches * sin + dyFieldInches * cos;

        // Proportional position controller -> robot-centric speed command (ips).
        double vxRobotIps = kPPosition * forwardErrorInches;
        double vyRobotIps = kPPosition * leftErrorInches;

        // Clamp to configured maxima.
        if (maxAxialInchesPerSec > 0.0) {
            vxRobotIps = MathUtil.clamp(vxRobotIps, -maxAxialInchesPerSec, +maxAxialInchesPerSec);
        }
        if (maxLateralInchesPerSec > 0.0) {
            vyRobotIps = MathUtil.clamp(vyRobotIps, -maxLateralInchesPerSec, +maxLateralInchesPerSec);
        }

        // Heading strategy + controller -> omega (rad/sec).
        double desiredHeadingRad = headingStrategy.desiredHeading(fieldRobotPose, fieldRobotTargetPose);
        double omegaRobotRadPerSec = headingController.update(
                desiredHeadingRad,
                fieldRobotPose.headingRad,
                omegaFeedforwardRadPerSec
        );

        return new ChassisSpeeds(vxRobotIps, vyRobotIps, omegaRobotRadPerSec);
    }

}

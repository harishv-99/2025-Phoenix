package edu.ftcphoenix.fw.drive.control;

import edu.ftcphoenix.fw.drive.MecanumDrivebase;
import edu.ftcphoenix.fw.field.TagLayout;
import edu.ftcphoenix.fw.core.geometry.Pose2d;
import edu.ftcphoenix.fw.core.geometry.Pose3d;
import edu.ftcphoenix.fw.localization.PoseEstimate;
import edu.ftcphoenix.fw.localization.PoseEstimator;
import edu.ftcphoenix.fw.task.Task;

/**
 * Factory methods for the framework's single "go-to-pose" stack.
 *
 * <p>This module wraps {@link GoToPoseController} + {@link GoToPoseTask}.
 * It exists to keep the common use-case ("drive to pose") easy to consume
 * without requiring every call-site to manually instantiate controllers and
 * config objects.</p>
 */
public final class GoToPoseTasks {

    private GoToPoseTasks() {
        // utility
    }

    /**
     * User-facing configuration for {@link #goToPoseFieldRelative(MecanumDrivebase, PoseEstimator, Pose2d, Config)}.
     */
    public static final class Config {

        // ----------------
        // Position control
        // ----------------

        /** Proportional gain for position (inches -> inches/sec). */
        public double kPos = 0.05;

        /** Maximum commanded forward speed (ips). */
        public double maxAxialInchesPerSec = 40.0;

        /** Maximum commanded leftward strafe speed (ips). */
        public double maxLateralInchesPerSec = 40.0;

        // --------------
        // Heading control
        // --------------

        /** Proportional gain for heading (rad -> rad/sec). */
        public double kHeading = 3.0;

        /** Maximum commanded angular speed (rad/sec). */
        public double maxOmegaRadPerSec = Math.toRadians(180.0);

        /** Strategy for choosing desired heading while driving. */
        public HeadingStrategy headingStrategy = HeadingStrategies.faceFinalHeading();

        // -----------------
        // Completion / safety
        // -----------------

        public double positionToleranceInches = 1.0;
        public double headingToleranceRad = Math.toRadians(5.0);

        /** Hard timeout for the overall task. */
        public double timeoutSec = 3.0;

        /** How long we will wait for pose before timing out. */
        public double maxNoPoseSec = 0.25;

        /** @return a new config with Phoenix defaults. */
        public static Config defaults() {
            return new Config();
        }
    }

    /**
     * Create a {@link Task} that drives the robot toward a target robot pose in the
     * field frame using feedback from a {@link PoseEstimator}.
     *
     * <p>This is a simple "go to pose" behavior intended for short moves.
     * It does <strong>not</strong> perform path planning or obstacle avoidance.</p>
     *
     * <p><b>Important:</b> {@link PoseEstimate#pose} is 6DOF ({@link Pose3d}). This controller
     * is planar and uses {@link PoseEstimate#toPose2d()}.</p>
     */
    public static Task goToPoseFieldRelative(
            final MecanumDrivebase drivebase,
            final PoseEstimator poseEstimator,
            final Pose2d fieldRobotTargetPose,
            final Config cfg) {

        final Config c = (cfg != null) ? cfg : Config.defaults();

        // Position controller config.
        GoToPoseController.Config posCfg = new GoToPoseController.Config();
        posCfg.kPPosition = c.kPos;
        posCfg.maxAxialInchesPerSec = c.maxAxialInchesPerSec;
        posCfg.maxLateralInchesPerSec = c.maxLateralInchesPerSec;

        // Heading controller config.
        HeadingController.Config headingCfg = new HeadingController.Config();
        headingCfg.kP = c.kHeading;
        headingCfg.maxOmegaRadPerSec = c.maxOmegaRadPerSec;

        HeadingStrategy headingStrategy = (c.headingStrategy != null)
                ? c.headingStrategy
                : HeadingStrategies.faceFinalHeading();

        HeadingController headingCtrl = new HeadingController(headingCfg);
        GoToPoseController controller = new GoToPoseController(posCfg, headingStrategy, headingCtrl);

        // Task completion / safety config.
        GoToPoseTask.Config taskCfg = new GoToPoseTask.Config();
        taskCfg.positionTolInches = c.positionToleranceInches;
        taskCfg.headingTolRad = c.headingToleranceRad;
        taskCfg.timeoutSec = c.timeoutSec;
        taskCfg.maxNoPoseSec = c.maxNoPoseSec;

        return new GoToPoseTask(poseEstimator, controller, drivebase, fieldRobotTargetPose, taskCfg);
    }

    /** Convenience overload that uses {@link Config#defaults()}. */
    public static Task goToPoseFieldRelative(
            final MecanumDrivebase drivebase,
            final PoseEstimator poseEstimator,
            final Pose2d fieldRobotTargetPose) {
        return goToPoseFieldRelative(drivebase, poseEstimator, fieldRobotTargetPose, Config.defaults());
    }

    /**
     * Create a {@link Task} that drives the robot to a pose defined <strong>tag-relative</strong>.
     *
     * <p>This is a thin wrapper around {@link #goToPoseFieldRelative(MecanumDrivebase, PoseEstimator, Pose2d, Config)}.
     * It:</p>
     * <ol>
     *   <li>Looks up the tag's pose in the field frame from {@link TagLayout}.</li>
     *   <li>Computes a target robot pose in the field frame using offsets in the tag's local frame.</li>
     *   <li>Invokes {@code goToPoseFieldRelative(...)} with that target robot pose.</li>
     * </ol>
     */
    public static Task goToPoseTagRelative(
            final MecanumDrivebase drivebase,
            final PoseEstimator poseEstimator,
            final TagLayout tagLayout,
            final int tagId,
            final double forwardOffsetInches,
            final double leftOffsetInches,
            final double headingOffsetRad,
            final Config cfg) {

        TagLayout.TagPose tag = tagLayout.require(tagId);
        Pose3d fieldToTagPose = tag.fieldToTagPose();

        // Project tag pose to the floor plane: (x, y, yaw).
        Pose2d fieldTagPose = new Pose2d(fieldToTagPose.xInches, fieldToTagPose.yInches, fieldToTagPose.yawRad);
        double tagHeadingRad = fieldTagPose.headingRad;

        // Tag-facing unit vectors in field frame.
        double nx = Math.cos(tagHeadingRad);  // forward (tag faces this way)
        double ny = Math.sin(tagHeadingRad);
        double lx = -ny;                      // left = rotate forward 90° CCW
        double ly = nx;

        // Target position in field frame: tag center plus local offsets.
        double targetXInches = fieldTagPose.xInches
                + forwardOffsetInches * nx
                + leftOffsetInches * lx;

        double targetYInches = fieldTagPose.yInches
                + forwardOffsetInches * ny
                + leftOffsetInches * ly;

        // Base heading: face the tag (opposite of tag facing), then apply offset.
        double targetHeadingRad = Pose2d.wrapToPi(tagHeadingRad + Math.PI + headingOffsetRad);

        Pose2d fieldRobotTargetPose = new Pose2d(targetXInches, targetYInches, targetHeadingRad);

        return goToPoseFieldRelative(drivebase, poseEstimator, fieldRobotTargetPose, cfg);
    }

    /** Convenience overload that uses {@link Config#defaults()}. */
    public static Task goToPoseTagRelative(
            final MecanumDrivebase drivebase,
            final PoseEstimator poseEstimator,
            final TagLayout tagLayout,
            final int tagId,
            final double forwardOffsetInches,
            final double leftOffsetInches,
            final double headingOffsetRad) {
        return goToPoseTagRelative(
                drivebase,
                poseEstimator,
                tagLayout,
                tagId,
                forwardOffsetInches,
                leftOffsetInches,
                headingOffsetRad,
                Config.defaults()
        );
    }
}

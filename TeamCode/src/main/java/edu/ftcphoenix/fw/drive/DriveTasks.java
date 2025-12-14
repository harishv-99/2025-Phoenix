package edu.ftcphoenix.fw.drive;

import edu.ftcphoenix.fw.field.TagLayout;
import edu.ftcphoenix.fw.field.TagLayout.TagPose;
import edu.ftcphoenix.fw.geom.Pose2d;
import edu.ftcphoenix.fw.geom.Pose3d;
import edu.ftcphoenix.fw.localization.PoseEstimate;
import edu.ftcphoenix.fw.localization.PoseEstimator;
import edu.ftcphoenix.fw.task.InstantTask;
import edu.ftcphoenix.fw.task.RunForSecondsTask;
import edu.ftcphoenix.fw.task.Task;
import edu.ftcphoenix.fw.task.TaskOutcome;
import edu.ftcphoenix.fw.util.LoopClock;
import edu.ftcphoenix.fw.util.MathUtil;

/**
 * Small helper class for creating common drive-related {@link Task} patterns.
 *
 * <p>Pose naming convention used here (to be captured in framework docs):</p>
 * <ul>
 *   <li>{@code fieldRobotPose}: robot pose expressed in the field frame (Pose2d).</li>
 *   <li>{@code fieldRobotTargetPose}: target robot pose expressed in the field frame (Pose2d).</li>
 *   <li>{@code fieldToTagPose}: a 6DOF pose/transform expressed in the field frame (Pose3d).</li>
 * </ul>
 *
 * <p>Drive command convention:</p>
 * <ul>
 *   <li>{@link DriveSignal#lateral} is +left.</li>
 *   <li>{@link DriveSignal#omega} is +CCW.</li>
 * </ul>
 */
public final class DriveTasks {

    private DriveTasks() {
        // utility class; do not instantiate
    }

    // ------------------------------------------------------------------------
    // Timed drive
    // ------------------------------------------------------------------------

    /**
     * Create a {@link Task} that holds a given {@link DriveSignal} for a fixed
     * amount of time, then stops the drivebase.
     *
     * @param drivebase   the drivebase to command
     * @param signal      the drive signal to hold (robot-centric)
     * @param durationSec how long to hold the signal, in seconds (must be {@code >= 0})
     * @return a {@link Task} that runs the drive signal for a fixed time
     */
    public static Task driveForSeconds(final MecanumDrivebase drivebase,
                                       final DriveSignal signal,
                                       final double durationSec) {
        return new RunForSecondsTask(
                durationSec,
                // onStart: set the drive signal
                () -> drivebase.drive(signal),
                // onUpdate: no-op; drivebase.update(dt) is handled elsewhere
                null,
                // onFinish: stop the drive
                drivebase::stop
        );
    }

    // ------------------------------------------------------------------------
    // Instant drive helpers
    // ------------------------------------------------------------------------

    /**
     * Create a {@link Task} that sets a drive signal once and then finishes
     * immediately. This is a thin wrapper around {@link InstantTask}.
     *
     * @param drivebase the drivebase to command
     * @param signal    the drive signal to apply
     * @return a {@link Task} that sets the drive signal once and then completes
     */
    public static Task driveInstant(final MecanumDrivebase drivebase,
                                    final DriveSignal signal) {
        return new InstantTask(() -> drivebase.drive(signal));
    }

    /**
     * Create a {@link Task} that stops the drivebase once and then finishes.
     *
     * @param drivebase the drivebase to stop
     * @return a {@link Task} that stops the drivebase once and then finishes
     */
    public static Task stop(final MecanumDrivebase drivebase) {
        return new InstantTask(drivebase::stop);
    }

    // ------------------------------------------------------------------------
    // Go-to-pose helper (field-relative)
    // ------------------------------------------------------------------------

    /**
     * Configuration parameters for {@link #goToPoseFieldRelative(MecanumDrivebase, PoseEstimator, Pose2d, GoToPoseConfig)}.
     *
     * <p>This is a simple proportional controller that tries to drive the robot
     * to a target pose in the <strong>field frame</strong> using a
     * {@link PoseEstimator} for feedback.</p>
     *
     * <p><b>Important:</b> {@link PoseEstimate#pose} is 6DOF ({@link Pose3d}). This controller
     * is planar and uses {@link PoseEstimate#toPose2d()}.</p>
     */
    public static final class GoToPoseConfig {

        public double kPos = 0.05;
        public double maxAxial = 0.7;
        public double maxLateral = 0.7;

        public double kHeading = 3.0;
        public double maxOmegaRadPerSec = Math.toRadians(180.0);

        public double positionToleranceInches = 1.0;
        public double headingToleranceRad = Math.toRadians(5.0);

        public double timeoutSec = 3.0;
    }

    /**
     * Create a {@link Task} that drives the robot toward a target robot pose in the
     * field frame using feedback from a {@link PoseEstimator}.
     *
     * <p>This is a simple "go to pose" behavior intended for short moves
     * (e.g., parking in a box, small navigation steps). It does <strong>not</strong>
     * perform path planning or obstacle avoidance.</p>
     *
     * @param drivebase            the drivebase to command
     * @param poseEstimator        pose estimator providing field-frame robot pose
     * @param fieldRobotTargetPose target robot pose in the field frame
     * @param cfg                  gains, limits, tolerances and timeout configuration
     * @return task that drives toward {@code fieldRobotTargetPose}
     */
    public static Task goToPoseFieldRelative(
            final MecanumDrivebase drivebase,
            final PoseEstimator poseEstimator,
            final Pose2d fieldRobotTargetPose,
            final GoToPoseConfig cfg) {

        return new Task() {

            private boolean started = false;
            private boolean complete = false;
            private double startTimeSec = 0.0;
            private TaskOutcome outcome = TaskOutcome.UNKNOWN;

            // For optional telemetry/debugging:
            @SuppressWarnings("unused")
            private double lastPosErrorInches = Double.NaN;
            @SuppressWarnings("unused")
            private double lastHeadingErrorRad = Double.NaN;

            @Override
            public void start(LoopClock clock) {
                started = true;
                complete = false;
                outcome = TaskOutcome.NOT_DONE;
                startTimeSec = clock.nowSec();
                lastPosErrorInches = Double.NaN;
                lastHeadingErrorRad = Double.NaN;
            }

            @Override
            public void update(LoopClock clock) {
                if (!started || complete) {
                    return;
                }

                final double nowSec = clock.nowSec();
                final double elapsedSec = nowSec - startTimeSec;

                // Timeout check first.
                if (elapsedSec >= cfg.timeoutSec) {
                    drivebase.stop();
                    complete = true;
                    outcome = TaskOutcome.TIMEOUT;
                    return;
                }

                PoseEstimate estimate = poseEstimator.getEstimate();
                if (!estimate.hasPose) {
                    // No pose available: safest behavior is to stop and wait.
                    drivebase.stop();
                    lastPosErrorInches = Double.NaN;
                    lastHeadingErrorRad = Double.NaN;
                    return;
                }

                // PoseEstimate.pose is Pose3d; drive control is planar, so project to Pose2d.
                Pose2d fieldRobotPose = estimate.toPose2d();

                // Position and heading error in field frame.
                lastPosErrorInches = fieldRobotPose.distanceTo(fieldRobotTargetPose);
                lastHeadingErrorRad = fieldRobotPose.headingErrorTo(fieldRobotTargetPose);

                // Check goal conditions.
                if (lastPosErrorInches <= cfg.positionToleranceInches
                        && Math.abs(lastHeadingErrorRad) <= cfg.headingToleranceRad) {
                    drivebase.stop();
                    complete = true;
                    outcome = TaskOutcome.SUCCESS;
                    return;
                }

                // Position error vector in field frame.
                double dxFieldInches = fieldRobotTargetPose.xInches - fieldRobotPose.xInches;
                double dyFieldInches = fieldRobotTargetPose.yInches - fieldRobotPose.yInches;

                // Transform into robot frame: v_r = R(-θ) * v_f
                double cosH = Math.cos(fieldRobotPose.headingRad);
                double sinH = Math.sin(fieldRobotPose.headingRad);

                double forwardErrorInches = dxFieldInches * cosH + dyFieldInches * sinH;
                double leftErrorInches = -dxFieldInches * sinH + dyFieldInches * cosH;

                // Map errors to drive commands (robot-centric, Phoenix convention).
                double axialCmd = cfg.kPos * forwardErrorInches;
                double lateralCmd = cfg.kPos * leftErrorInches;

                // Heading error is "target - current", already wrapped to [-π, +π] by headingErrorTo().
                double headingErrorRad = lastHeadingErrorRad;

                // DriveSignal.omega > 0 is CCW.
                double omegaCmd = cfg.kHeading * headingErrorRad;

                // Clamp to limits.
                axialCmd = MathUtil.clamp(axialCmd, -cfg.maxAxial, cfg.maxAxial);
                lateralCmd = MathUtil.clamp(lateralCmd, -cfg.maxLateral, cfg.maxLateral);
                omegaCmd = MathUtil.clamp(omegaCmd, -cfg.maxOmegaRadPerSec, cfg.maxOmegaRadPerSec);

                DriveSignal signal = new DriveSignal(axialCmd, lateralCmd, omegaCmd);
                drivebase.drive(signal);
            }

            @Override
            public boolean isComplete() {
                return complete;
            }

            @Override
            public TaskOutcome getOutcome() {
                if (!complete) {
                    return TaskOutcome.NOT_DONE;
                }
                return outcome;
            }
        };
    }

    // ------------------------------------------------------------------------
    // Tag-relative helper
    // ------------------------------------------------------------------------

    /**
     * Create a {@link Task} that drives the robot to a pose defined
     * <strong>tag-relative</strong>.
     *
     * <p>This is a thin wrapper around
     * {@link #goToPoseFieldRelative(MecanumDrivebase, PoseEstimator, Pose2d, GoToPoseConfig)}.
     * It:</p>
     *
     * <ol>
     *   <li>Looks up the tag's pose in the field frame from {@link TagLayout}.</li>
     *   <li>Computes a target robot pose in the field frame using offsets in the
     *       tag's local frame.</li>
     *   <li>Invokes {@code goToPoseFieldRelative(...)} with that target robot pose.</li>
     * </ol>
     *
     * @param drivebase           the drivebase to command
     * @param poseEstimator       pose estimator providing field-frame robot pose
     * @param tagLayout           layout describing tag poses in the field
     * @param tagId               ID of the tag to stand relative to
     * @param forwardOffsetInches desired forward offset relative to tag facing, in inches
     * @param leftOffsetInches    desired left offset relative to tag facing, in inches
     * @param headingOffsetRad    desired heading offset relative to "facing the tag", in radians
     * @param cfg                 gains, limits, tolerances and timeout configuration
     * @return a {@link Task} that drives to the specified tag-relative robot pose
     * @throws IllegalArgumentException if {@code tagId} is not present in {@code tagLayout}
     */
    public static Task goToPoseTagRelative(
            final MecanumDrivebase drivebase,
            final PoseEstimator poseEstimator,
            final TagLayout tagLayout,
            final int tagId,
            final double forwardOffsetInches,
            final double leftOffsetInches,
            final double headingOffsetRad,
            final GoToPoseConfig cfg) {

        TagPose tag = tagLayout.require(tagId);
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

    /**
     * Convenience overload of
     * {@link #goToPoseTagRelative(MecanumDrivebase, PoseEstimator, TagLayout, int, double, double, double, GoToPoseConfig)}
     * that uses a default {@link GoToPoseConfig}.
     *
     * @see #goToPoseTagRelative(MecanumDrivebase, PoseEstimator, TagLayout, int, double, double, double, GoToPoseConfig)
     */
    public static Task goToPoseTagRelative(
            final MecanumDrivebase drivebase,
            final PoseEstimator poseEstimator,
            final TagLayout tagLayout,
            final int tagId,
            final double forwardOffsetInches,
            final double leftOffsetInches,
            final double headingOffsetRad) {

        GoToPoseConfig cfg = new GoToPoseConfig();
        return goToPoseTagRelative(
                drivebase,
                poseEstimator,
                tagLayout,
                tagId,
                forwardOffsetInches,
                leftOffsetInches,
                headingOffsetRad,
                cfg
        );
    }
}

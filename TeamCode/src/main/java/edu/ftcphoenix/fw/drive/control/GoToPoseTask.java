package edu.ftcphoenix.fw.drive.control;

import java.util.Objects;

import edu.ftcphoenix.fw.drive.ChassisSpeeds;
import edu.ftcphoenix.fw.drive.MecanumDrivebase;
import edu.ftcphoenix.fw.geom.Pose2d;
import edu.ftcphoenix.fw.localization.PoseEstimate;
import edu.ftcphoenix.fw.localization.PoseEstimator;
import edu.ftcphoenix.fw.task.Task;
import edu.ftcphoenix.fw.task.TaskOutcome;
import edu.ftcphoenix.fw.util.LoopClock;

/**
 * {@link Task} that drives a mecanum robot toward a target pose using a
 * {@link PoseEstimator} and a {@link GoToPoseController}.
 *
 * <p>This task is intended for short, local motions such as:
 * "move to a pose near an AprilTag while keeping it in view", or
 * "drive to a known field landmark". It is deliberately conservative:
 * <ul>
 *   <li>If the pose estimator does not have a pose, the task stops the drive
 *       and waits up to a configurable {@code maxNoPoseSec} before timing out.</li>
 *   <li>If the overall timeout elapses, the task stops the drive and reports
 *       {@link TaskOutcome#TIMEOUT}.</li>
 *   <li>Once position and heading errors are within the configured tolerances,
 *       the task stops the drive and reports {@link TaskOutcome#SUCCESS}.</li>
 * </ul>
 *
 * <p>It does not attempt any odometry integration or long-range planning; it
 * simply uses whatever pose the {@link PoseEstimator} provides each loop.</p>
 */
public final class GoToPoseTask implements Task {

    /**
     * Configuration parameters for {@link GoToPoseTask}.
     */
    public static final class Config {

        /**
         * Position tolerance in inches. When the distance between the current
         * pose and the target pose is less than or equal to this value, the
         * task considers the position "good enough" for completion.
         */
        public double positionTolInches = 1.0;

        /**
         * Heading tolerance in radians. When the absolute heading error between
         * the current pose and the target pose is less than or equal to this
         * value, the task considers the heading "good enough" for completion.
         */
        public double headingTolRad = Math.toRadians(5.0);

        /**
         * Hard timeout in seconds. If the task has not reached the target
         * within this time, it stops the drive and reports
         * {@link TaskOutcome#TIMEOUT}.
         */
        public double timeoutSec = 3.0;

        /**
         * Maximum time in seconds the task will wait for a valid pose from the
         * {@link PoseEstimator} before timing out.
         *
         * <ul>
         *   <li>If {@code maxNoPoseSec <= 0}, the task times out immediately
         *       when no pose is available.</li>
         *   <li>If positive, the task will wait up to this many seconds (while
         *       commanding a stop) for {@link PoseEstimate#hasPose} to become
         *       true again.</li>
         * </ul>
         */
        public double maxNoPoseSec = 0.25;

        /** Creates a new {@code Config} with default values. */
        public Config() {
        }
    }

    private final PoseEstimator poseEstimator;
    private final GoToPoseController controller;
    private final MecanumDrivebase drivebase;
    private final Pose2d targetPose;
    private final Config cfg;

    private boolean started = false;
    private boolean finished = false;
    private TaskOutcome outcome = TaskOutcome.NOT_DONE;

    private double elapsedSec = 0.0;
    private double noPoseElapsedSec = 0.0;

    // For optional debugging/telemetry.
    private double lastPosErrorInches = 0.0;
    private double lastHeadingErrorRad = 0.0;

    /**
     * Creates a new {@code GoToPoseTask}.
     *
     * @param poseEstimator pose estimator providing the robot's pose
     * @param controller    controller that converts pose error into a {@link ChassisSpeeds}
     * @param drivebase     mecanum drivebase to command
     * @param targetPose    target pose in the same field frame as the estimator
     * @param cfg           configuration parameters for this task
     */
    public GoToPoseTask(PoseEstimator poseEstimator,
                        GoToPoseController controller,
                        MecanumDrivebase drivebase,
                        Pose2d targetPose,
                        Config cfg) {

        this.poseEstimator = Objects.requireNonNull(poseEstimator, "poseEstimator");
        this.controller = Objects.requireNonNull(controller, "controller");
        this.drivebase = Objects.requireNonNull(drivebase, "drivebase");
        this.targetPose = Objects.requireNonNull(targetPose, "targetPose");
        this.cfg = Objects.requireNonNull(cfg, "cfg");
    }

    @Override
    public void start(LoopClock clock) {
        started = true;
        finished = false;
        outcome = TaskOutcome.NOT_DONE;
        elapsedSec = 0.0;
        noPoseElapsedSec = 0.0;
        lastPosErrorInches = 0.0;
        lastHeadingErrorRad = 0.0;

        // Ensure the drive is in a known state at the start.
        drivebase.stop();
    }

    @Override
    public void update(LoopClock clock) {
        if (finished) {
            return;
        }
        if (!started) {
            // For robustness; TaskRunner should always call start() first.
            start(clock);
        }

        // Feed dt to the drivebase (for any enabled rate limiting).
        drivebase.update(clock);

        double dtSec = clock.dtSec();
        elapsedSec += dtSec;

        // Hard timeout.
        if (elapsedSec > cfg.timeoutSec) {
            drivebase.stop();
            finished = true;
            outcome = TaskOutcome.TIMEOUT;
            return;
        }

        // Get the latest pose estimate.
        PoseEstimate estimate = poseEstimator.getEstimate();

        if (!estimate.hasPose) {
            // No usable pose; stop the drive and wait up to maxNoPoseSec.
            drivebase.stop();

            if (cfg.maxNoPoseSec <= 0.0) {
                finished = true;
                outcome = TaskOutcome.TIMEOUT;
                return;
            }

            noPoseElapsedSec += dtSec;
            if (noPoseElapsedSec > cfg.maxNoPoseSec) {
                finished = true;
                outcome = TaskOutcome.TIMEOUT;
            }
            return;
        }

        // We have a pose; reset no-pose timer.
        noPoseElapsedSec = 0.0;

        // PoseEstimate.pose is Pose3d; drivetrain control is planar, so project to Pose2d.
        Pose2d robotPose = estimate.toPose2d();

        // Compute position and heading errors.
        lastPosErrorInches = robotPose.distanceTo(targetPose);
        lastHeadingErrorRad = Math.abs(robotPose.headingErrorTo(targetPose));

        // Check completion conditions.
        if (lastPosErrorInches <= cfg.positionTolInches
                && lastHeadingErrorRad <= cfg.headingTolRad) {
            drivebase.stop();
            finished = true;
            outcome = TaskOutcome.SUCCESS;
            return;
        }

        // Compute feedforward for heading if desired.
        // For now, default to zero; this can be extended later.
        double omegaFF = 0.0;

        // Use the controller to generate a chassis-speed command.
        ChassisSpeeds cmd = controller.update(robotPose, targetPose, omegaFF);

        // Apply to drivebase (best-effort mapping to motor power).
        drivebase.drive(cmd);
    }

    @Override
    public boolean isComplete() {
        return finished;
    }

    @Override
    public TaskOutcome getOutcome() {
        return finished ? outcome : TaskOutcome.NOT_DONE;
    }

    /** @return the target pose this task is driving toward. */
    public Pose2d getTargetPose() {
        return targetPose;
    }

    /** @return the most recent position error magnitude in inches. */
    public double getLastPosErrorInches() {
        return lastPosErrorInches;
    }

    /** @return the most recent absolute heading error in radians. */
    public double getLastHeadingErrorRad() {
        return lastHeadingErrorRad;
    }

    /** @return the total elapsed time in seconds since this task started. */
    public double getElapsedSec() {
        return elapsedSec;
    }

    /**
     * @return how long (in seconds) we have been in a "no pose" condition since the last valid pose was seen.
     */
    public double getNoPoseElapsedSec() {
        return noPoseElapsedSec;
    }
}

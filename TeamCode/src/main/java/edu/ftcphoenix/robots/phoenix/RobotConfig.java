package edu.ftcphoenix.robots.phoenix;

import edu.ftcphoenix.fw.drive.Drives;
import edu.ftcphoenix.fw.ftc.localization.PinpointPoseEstimator;

import edu.ftcphoenix.fw.core.hal.Direction;
import edu.ftcphoenix.fw.sensing.vision.CameraMountConfig;

/**
 * Centralized robot configuration for the Phoenix example robot.
 *
 * <p>This file is intentionally "boring": it stores hardware map names,
 * directions, and calibration constants in one place so the rest of the robot
 * code can stay clean and robot-centric.</p>
 *
 * <p>Rule of thumb: if you find yourself repeating a device name string or a
 * mount/offset constant in multiple files, it probably belongs here.</p>
 */
public class RobotConfig {

    /**
     * Drivetrain hardware mapping (motor names + directions).
     */
    public static class DriveTrain {
        public static final String nameMotorFrontLeft = "frontLeftMotor";
        public static final Direction directionMotorFrontLeft = Direction.REVERSE;

        public static final String nameMotorFrontRight = "frontRightMotor";
        public static final Direction directionMotorFrontRight = Direction.REVERSE;

        public static final String nameMotorBackLeft = "backLeftMotor";
        public static final Direction directionMotorBackLeft = Direction.FORWARD;

        public static final String nameMotorBackRight = "backRightMotor";
        public static final Direction directionMotorBackRight = Direction.REVERSE;


        /**
         * Convenience wiring bundle for framework helpers/testers that want to instantiate a mecanum drive.
         */
        public static Drives.MecanumWiringConfig mecanumWiring() {
            Drives.MecanumWiringConfig w = Drives.MecanumWiringConfig.defaults();
            w.frontLeftName = nameMotorFrontLeft;
            w.frontLeftDirection = directionMotorFrontLeft;
            w.frontRightName = nameMotorFrontRight;
            w.frontRightDirection = directionMotorFrontRight;
            w.backLeftName = nameMotorBackLeft;
            w.backLeftDirection = directionMotorBackLeft;
            w.backRightName = nameMotorBackRight;
            w.backRightDirection = directionMotorBackRight;
            return w;

        }
    }

    /**
     * Shooter hardware mapping + basic tuning constants.
     *
     * <p>Units for shooter velocity are the motor's native velocity units
     * (e.g., ticks/sec), because Phoenix plants operate in native units by design.</p>
     */
    public static class Shooter {
        public static final String nameServoPusher = "pusher";
        public static final Direction directionServoPusher = Direction.FORWARD;

        public static final String nameCrServoTransferLeft = "transferLeft";
        public static final Direction directionServoTransferLeft = Direction.REVERSE;

        public static final String nameCrServoTransferRight = "transferRight";
        public static final Direction directionServoTransferRight = Direction.FORWARD;

        public static final String nameMotorShooterLeft = "shooterLeft";
        public static final Direction directionMotorShooterLeft = Direction.FORWARD;

        public static final String nameMotorShooterRight = "shooterRight";
        public static final Direction directionMotorShooterRight = Direction.FORWARD;

        public static final double velocityMin = 1500;
        public static final double velocityMax = 1900;
        public static final double velocityIncrement = 50;

        public static final double targetPusherBack = 0.55;
        public static final double targetPusherFront = 1.00;
    }


    /**
     * Vision-related configuration for the Phoenix robot.
     *
     * <p>These values are consumed by robot code (for example {@code PhoenixRobot}) and by
     * robot-specific testers. Keeping them here avoids hard-coding camera names and mount
     * numbers in many places.</p>
     *
     * <p><b>Camera mount calibration:</b> use the framework tester <i>Calib: Camera Mount</i>
     * to solve {@link #cameraMount}. Paste the printed {@link CameraMountConfig#of(double, double, double, double, double, double)}
     * values here.</p>
     */
    public static class Vision {

        /**
         * FTC Robot Configuration name of the webcam used for AprilTag vision.
         */
        public static final String nameWebcam = "Webcam 1";

        /**
         * Robot→camera extrinsics (Phoenix axes: +X forward, +Y left, +Z up).
         *
         * <p>Defaults to identity (all zeros) so code compiles out-of-the-box. For accurate
         * localization/aiming, calibrate and update this value.</p>
         */
        public static final CameraMountConfig cameraMount = CameraMountConfig.identity();
    }

    /**
     * Localization-related configuration.
     *
     * <p>Phase 1: Pinpoint + AprilTag fusion requires the Pinpoint device name and offsets.
     * Offsets use the Pinpoint convention (matching the goBILDA docs):</p>
     * <ul>
     *   <li><b>forwardPodOffsetLeftInches</b>: how far LEFT of your tracking point the forward (X) pod is (+left, -right)</li>
     *   <li><b>strafePodOffsetForwardInches</b>: how far FORWARD of your tracking point the strafe (Y) pod is (+forward, -back)</li>
     * </ul>
     */
    public static class Localization {

        /**
         * goBILDA Pinpoint configuration.
         *
         * <p>Distances are in inches to match Phoenix's pose types.</p>
         */
        public static final PinpointPoseEstimator.Config pinpoint =
                PinpointPoseEstimator.Config.defaults()
                        .withHardwareMapName("odo")
                        .withOffsets(0.0, 0.0);

        // Advanced options (uncomment if needed):
        // pinpoint.withEncoderPods(GoBildaPinpointDriver.GoBildaOdometryPods.goBILDA_4_BAR_POD);
        // pinpoint.withCustomEncoderResolutionTicksPerInch(null);
        // pinpoint.withForwardPodDirection(GoBildaPinpointDriver.EncoderDirection.FORWARD);
        // pinpoint.withStrafePodDirection(GoBildaPinpointDriver.EncoderDirection.FORWARD);
        // pinpoint.withYawScalar(null);
        // pinpoint.withResetOnInit(true);
        // pinpoint.withResetWaitMs(300);
        // pinpoint.withQuality(0.75);
    }

}

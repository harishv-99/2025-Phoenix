package edu.ftcphoenix.robots.phoenix;

import edu.ftcphoenix.fw.core.hal.Direction;
import edu.ftcphoenix.fw.sensing.vision.CameraMountConfig;

public class RobotConfig {

    public static class DriveTrain {
        public static final String nameMotorFrontLeft = "frontLeftMotor";
        public static final Direction directionMotorFrontLeft = Direction.REVERSE;

        public static final String nameMotorFrontRight = "frontRightMotor";
        public static final Direction directionMotorFrontRight = Direction.REVERSE;

        public static final String nameMotorBackLeft = "backLeftMotor";
        public static final Direction directionMotorBackLeft = Direction.FORWARD;

        public static final String nameMotorBackRight = "backRightMotor";
        public static final Direction directionMotorBackRight = Direction.REVERSE;
    }

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

        public static final double targetPusherBack = 0.5;
        public static final double targetPusherFront = 0.9;
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

        /** FTC Robot Configuration name of the webcam used for AprilTag vision. */
        public static final String nameWebcam = "Webcam 1";

        /**
         * Robot→camera extrinsics (Phoenix axes: +X forward, +Y left, +Z up).
         *
         * <p>Defaults to identity (all zeros) so code compiles out-of-the-box. For accurate
         * localization/aiming, calibrate and update this value.</p>
         */
        public static final CameraMountConfig cameraMount = CameraMountConfig.identity();
    }

}

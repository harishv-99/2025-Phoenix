package edu.ftcphoenix.robots.phoenix;

import edu.ftcphoenix.fw.core.hal.Direction;

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
}

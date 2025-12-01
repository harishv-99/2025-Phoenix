package edu.ftcphoenix.robots.phoenix;

public class RobotConfig {

    public static class DriveTrain {
        public static final String nameMotorFrontLeft = "frontLeftMotor";
        public static final boolean invertMotorFrontLeft = true;
        public static final String nameMotorFrontRight = "frontRightMotor";
        public static final boolean invertMotorFrontRight = true;
        public static final String nameMotorBackLeft = "backLeftMotor";
        public static final boolean invertMotorBackLeft = false;
        public static final String nameMotorBackRight = "backRightMotor";
        public static final boolean invertMotorBackRight = true;
    }

    public static class Shooter {
        public static final String nameServoPusher = "pusher";
        public static final boolean invertServoPusher = false;
        public static final String nameCrServoTransferLeft = "transferLeft";
        public static final boolean invertServoTransferLeft = true;
        public static final String nameCrServoTransferRight = "transferRight";
        public static final boolean invertServoTransferRight = false;
        public static final String nameMotorShooterLeft = "shooterLeft";
        public static final boolean invertMotorShooterLeft = false;
        public static final String nameMotorShooterRight = "shooterRight";
        public static final boolean invertMotorShooterRight = false;
        public static final double velocityMin = 1000;
        public static final double velocityMax = 2800;
        public static final double velocityIncrement = 100;

        public static final double targetPusherBack = 0.5;
        public static final double targetPusherFront = 0.9;
    }
}

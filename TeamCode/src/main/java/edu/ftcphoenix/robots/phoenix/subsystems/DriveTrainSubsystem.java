package edu.ftcphoenix.robots.phoenix.subsystems;

import com.acmerobotics.roadrunner.HolonomicController;
import com.acmerobotics.roadrunner.Pose2d;
import com.acmerobotics.roadrunner.PoseVelocity2d;
import com.qualcomm.hardware.rev.RevHubOrientationOnRobot;
import com.qualcomm.robotcore.hardware.DcMotorEx;
import com.qualcomm.robotcore.hardware.DcMotorSimple;
import com.qualcomm.robotcore.hardware.HardwareMap;
import com.qualcomm.robotcore.hardware.IMU;
import com.qualcomm.robotcore.hardware.VoltageSensor;

import org.firstinspires.ftc.robotcore.external.Telemetry;
import org.firstinspires.ftc.robotcore.external.navigation.AngleUnit;
import org.firstinspires.ftc.robotcore.external.navigation.CurrentUnit;
import org.firstinspires.ftc.teamcode.MecanumDrive;

import edu.ftcphoenix.robots.phoenix.Constants;

public class DriveTrainSubsystem {
    private final DcMotorEx motorBackLeft;
    private final DcMotorEx motorFrontLeft;
    private final DcMotorEx motorBackRight;
    private final DcMotorEx motorFrontRight;
    private final IMU imu;

    private final VoltageSensor voltageSensor;
    private final Telemetry telemetry;

    private final MecanumDrive mecanumDrive;
    private final HolonomicController holonomicController;

    private PoseVelocity2d movePrior;

    public DriveTrainSubsystem(HardwareMap hardwareMap, Telemetry telemetry) {
        // Configure the motors
        motorBackLeft = hardwareMap.get(DcMotorEx.class, Constants.MOTOR_NAME_BACK_LEFT);
        motorFrontLeft = hardwareMap.get(DcMotorEx.class, Constants.MOTOR_NAME_FRONT_LEFT);
        motorBackRight = hardwareMap.get(DcMotorEx.class, Constants.MOTOR_NAME_BACK_RIGHT);
        motorFrontRight = hardwareMap.get(DcMotorEx.class, Constants.MOTOR_NAME_FRONT_RIGHT);

        motorFrontLeft.setDirection(DcMotorSimple.Direction.REVERSE);
        motorFrontRight.setDirection(DcMotorSimple.Direction.REVERSE);
        motorBackLeft.setDirection(DcMotorSimple.Direction.REVERSE);

        // Configure the IMU
        imu = hardwareMap.get(IMU.class, Constants.IMU_NAME);
        IMU.Parameters parameters = new IMU.Parameters(new RevHubOrientationOnRobot(
                RevHubOrientationOnRobot.LogoFacingDirection.UP,
                RevHubOrientationOnRobot.UsbFacingDirection.BACKWARD));
        imu.initialize(parameters);

        // Configure the voltage sensor
        voltageSensor = hardwareMap.get(VoltageSensor.class, "Control Hub");
        this.telemetry = telemetry;

        Pose2d beginPose = new Pose2d(0, 0, 0);
        mecanumDrive = new MecanumDrive(hardwareMap, beginPose);

        holonomicController = new HolonomicController(
                MecanumDrive.PARAMS.axialGain,
                MecanumDrive.PARAMS.lateralGain,
                MecanumDrive.PARAMS.headingGain,
                MecanumDrive.PARAMS.axialVelGain,
                MecanumDrive.PARAMS.lateralVelGain,
                MecanumDrive.PARAMS.headingVelGain
        );
    }

    public double getHeading() {
        return imu.getRobotYawPitchRollAngles().getYaw(AngleUnit.RADIANS);
    }


    public double getCurrent() {
        return motorFrontRight.getCurrent(CurrentUnit.MILLIAMPS);
    }

    public double getVoltage() {
        return voltageSensor.getVoltage();
    }

    public boolean hasRunIntoObstacle() {
        return (voltageSensor.getVoltage() < Constants.VOLTAGE_SENSOR_OBSTACLE_THRESHOLD);
    }

    public void updatePoseEstimate() {
        movePrior = mecanumDrive.updatePoseEstimate();
    }

    public Pose2d getPose() {
//        return mecanumDrive.pose;
        // TODO: Needs to return an object.  FIXME
        return null;
    }

    public PoseVelocity2d getMovePrior() {
        return movePrior;
    }

    public HolonomicController getHolonomicController() {
        return holonomicController;
    }

    public void drive(PoseVelocity2d powers) {
        mecanumDrive.setDrivePowers(powers);

        // TODO FIXME: The pose object needs to be accessed.
//        telemetry.addData("x", mecanumDrive.pose.position.x);
//        telemetry.addData("y", mecanumDrive.pose.position.y);
//        telemetry.addData("head", mecanumDrive.pose.heading);
    }

    public void drive2(PoseVelocity2d powers) {
        double x = -powers.linearVel.y;
        double y = powers.linearVel.x;
        double rx = powers.angVel;

        // Denominator is the largest motor power (absolute value) or 1
        // This ensures all the powers maintain the same ratio,
        // but only if at least one is out of the range [-1, 1]
        double denominator = Math.max(Math.abs(y) + Math.abs(x) + Math.abs(rx), 1);
        double frontLeftPower = (y + x + rx) / denominator;
        double backLeftPower = (y - x + rx) / denominator;
        double frontRightPower = (y - x - rx) / denominator;
        double backRightPower = (y + x - rx) / denominator;

        motorFrontLeft.setPower(frontLeftPower);
        motorBackLeft.setPower(backLeftPower);
        motorFrontRight.setPower(frontRightPower);
        motorBackRight.setPower(backRightPower);
    }
}

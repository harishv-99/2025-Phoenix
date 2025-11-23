package edu.ftcphoenix.robots.phoenix2;

import com.qualcomm.robotcore.eventloop.opmode.LinearOpMode;

import edu.ftcphoenix.fw2.robotbase.RobotBase;
import edu.ftcphoenix.robots.phoenix2.controllers.TeleOpController;
import edu.ftcphoenix.robots.phoenix2.subsystems.DriveTrainSubsystem;
import edu.ftcphoenix.robots.phoenix2.subsystems.ShooterSubsystem;

public class Robot extends RobotBase<Robot.Components> {
    public DriveTrainSubsystem driveTrainSubsystem;
    public ShooterSubsystem shooterSubsystem;
    TeleOpController teleOpController;

    public static Robot g_Robot;

    public Robot(LinearOpMode ftcRobot, OpModeType opModeType,
                 AllianceColor allianceColor,
                 StartPosition startPosition) {
        super(ftcRobot, opModeType, allianceColor, startPosition);
        g_Robot = this;
    }

    @Override
    protected void initRobot() {
        driveTrainSubsystem = new DriveTrainSubsystem(getHardwareMap(), getTelemetry());
        shooterSubsystem    = new ShooterSubsystem(getHardwareMap(), getTelemetry());
        registerSubsystems(driveTrainSubsystem, shooterSubsystem);
    }

    @Override
    protected void onPeriodicRobot() {

    }

    @Override
    protected void exitRobot() {

    }

    @Override
    protected void initAutonomous() {

    }

    @Override
    protected void onPeriodicAutonomous() {

    }

    @Override
    protected void exitAutonomous() {

    }

    @Override
    protected void initTeleOp() {
        // Create a new teleOpController and initialize it.
        teleOpController = new TeleOpController(this);
    }

    @Override
    protected void onPeriodicTeleOp() {
        teleOpController.onPeriodicTeleOp();
    }

    @Override
    protected void exitTeleOp() {

    }

    public enum Components {
        CHASSIS,
        ARM
    }
}
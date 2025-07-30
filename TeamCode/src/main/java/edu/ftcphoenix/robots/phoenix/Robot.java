package edu.ftcphoenix.robots.phoenix;

import com.qualcomm.robotcore.eventloop.opmode.LinearOpMode;

import edu.ftcphoenix.fw.robotbase.RobotBase;
import edu.ftcphoenix.robots.phoenix.controllers.TeleOpController;
import edu.ftcphoenix.robots.phoenix.controllers.misc.RobotStateWriter;
import edu.ftcphoenix.robots.phoenix.subsystems.DriveTrainSubsystem;

public class Robot extends RobotBase<Robot.Components> {
    public DriveTrainSubsystem driveTrainSubsystem;
    public RobotStateWriter robotStateWriter;
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
//        driveTrainSubsystem = new DriveTrainSubsystem(getHardwareMap(),
//                getTelemetry());
//        robotStateWriter = new RobotStateWriter(this);
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
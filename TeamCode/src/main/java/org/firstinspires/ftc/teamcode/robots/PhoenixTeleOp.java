package org.firstinspires.ftc.teamcode.robots;

import com.qualcomm.robotcore.eventloop.opmode.TeleOp;

import edu.ftcphoenix.fw.robot.PhoenixTeleOpBase;
import edu.ftcphoenix.robots.phoenix.PhoenixRobot;

/**
 * Thin TeleOp shell. All interesting logic lives in PhoenixRobot + subsystems.
 */
@TeleOp(name = "Phoenix: Drive + Shooter (Subsystems)", group = "Phoenix")
public final class PhoenixTeleOp extends PhoenixTeleOpBase {

    private PhoenixRobot robot;

    @Override
    protected void onInitRobot() {
        robot = new PhoenixRobot(hardwareMap, driverKit(), telemetry);
    }

    @Override
    protected void onStartRobot() {
        robot.onTeleopInit();
    }

    @Override
    protected void onLoopRobot(double dtSec) {
        robot.onTeleopLoop(clock());
    }

    @Override
    protected void onStopRobot() {
        robot.onStop();
    }
}

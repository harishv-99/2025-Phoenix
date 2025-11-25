package org.firstinspires.ftc.teamcode.robots;

import com.qualcomm.robotcore.eventloop.opmode.Disabled;
import com.qualcomm.robotcore.eventloop.opmode.TeleOp;

import edu.ftcphoenix.fw.robot.PhoenixTeleOpBase;
import edu.ftcphoenix.robots.phoenix_subsystem.PhoenixRobot;

/**
 * Thin TeleOp shell. All interesting logic lives in PhoenixRobot + subsystems.
 */
@Disabled
@TeleOp(name = "Phoenix: TeleOp Test", group = "Phoenix")
public final class PhoenixTeleOpSubSystem extends PhoenixTeleOpBase {

    private PhoenixRobot robot;

    @Override
    protected void onInitRobot() {
        robot = new PhoenixRobot(hardwareMap, gamepads(), telemetry);
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

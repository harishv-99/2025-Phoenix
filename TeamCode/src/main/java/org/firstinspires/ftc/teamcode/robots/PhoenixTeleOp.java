package org.firstinspires.ftc.teamcode.robots;

import com.qualcomm.robotcore.eventloop.opmode.OpMode;
import com.qualcomm.robotcore.eventloop.opmode.TeleOp;

import edu.ftcphoenix.robots.phoenix.PhoenixRobot;

@TeleOp(name = "Phoenix TeleOp", group = "Phoenix")
public final class PhoenixTeleOp extends OpMode {
    private final PhoenixRobot robot = new PhoenixRobot();


    @Override
    public void init() {
        robot.initHardware(hardwareMap, telemetry, gamepad1, gamepad2);
    }


    @Override
    public void start() {
        robot.startTeleOp(getRuntime());
    }


    @Override
    public void loop() {
        robot.loopTeleOp(getRuntime());
    }
}
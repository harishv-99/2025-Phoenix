package org.firstinspires.ftc.teamcode.robots;

import com.qualcomm.robotcore.eventloop.opmode.OpMode;
import com.qualcomm.robotcore.eventloop.opmode.TeleOp;

import edu.ftcphoenix.robots.phoenix.tester.MotorDirectionTester;

@TeleOp(name = "PhoenixTeleop - Tester - MotorDirection", group = "Phoenix")
public class PhoenixTeleop_Tester_MotorDirection extends OpMode {
    private MotorDirectionTester robot;

    @Override
    public void init() {

        // Create the season robot and share its bindings.
        robot = new MotorDirectionTester(hardwareMap, telemetry, gamepad1, gamepad2);
        robot.initTeleOp();
    }

    @Override
    public void start() {
        robot.startAny(getRuntime());
        robot.startTeleOp();
    }

    @Override
    public void loop() {
        robot.updateAny(getRuntime());
        robot.updateTeleOp();
    }

    @Override
    public void stop() {
        robot.stopAny();
        robot.stopTeleOp();
    }
}
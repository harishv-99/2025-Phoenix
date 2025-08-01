package edu.ftcphoenix.robots.phoenix.controllers;

import static edu.ftcphoenix.fw.gamepad.GamepadKeys.Stick.LEFT_STICK_X;

import com.acmerobotics.roadrunner.Vector2d;

import edu.ftcphoenix.fw.assisteddriving.GuidanceAdjustor;
import edu.ftcphoenix.fw.assisteddriving.shape.Segment;
import edu.ftcphoenix.fw.assisteddriving.shape.Shape;
import edu.ftcphoenix.fw.assisteddriving.shapeadjustor.ShapeAdjustorBuilder;
import edu.ftcphoenix.fw.assisteddriving.shapeadjustor.closestpointadjustor.AttractorAdjustor;
import edu.ftcphoenix.fw.assisteddriving.shapeadjustor.closestpointadjustor.ClosestPointAdjustor;
import edu.ftcphoenix.fw.fsm.AbstractFsmContainer;
import edu.ftcphoenix.fw.gamepad.GamepadController;
import edu.ftcphoenix.fw.gamepad.GamepadKeys;
import edu.ftcphoenix.robots.phoenix.Constants;
import edu.ftcphoenix.robots.phoenix.Robot;
import edu.ftcphoenix.robots.phoenix.controllers.teleop.TeleOpButtonDriveState;
import edu.ftcphoenix.robots.phoenix.controllers.teleop.TeleOpStickDriveState;


public class TeleOpController {

    Robot robot;
    GamepadController gp1;

    public TeleOpController(Robot robot) {
        this.robot = robot;
        gp1 = robot.getGamepad1();

        createGamepadControls();
    }

    public void onPeriodicTeleOp() {
        //double lat = robot.getGamepad1().getValue(LEFT_STICK_X);
        double ang = robot.getGamepadInputs().getInterval(Constants.INTERVAL_NAME_ANGULAR).getValue();
        robot.getTelemetry().addData("Ang", ang);
    }

    public void createGamepadControls() {
        // Setup input from gamepad 1
        gp1.createIntervalDoubleUnit(Constants.INTERVAL_NAME_LATERAL,
                LEFT_STICK_X);
        gp1.createIntervalDoubleUnit(Constants.INTERVAL_NAME_AXIAL,
                GamepadKeys.Stick.LEFT_STICK_Y);
        gp1.createIntervalDoubleUnit(Constants.INTERVAL_NAME_ANGULAR,
                GamepadKeys.Stick.RIGHT_STICK_X);

        gp1.createButton(Constants.BUTTON_NAME_MOVE, GamepadKeys.Button.SQUARE);
        gp1.createButton(Constants.BUTTON_NAME_STOP, GamepadKeys.Button.CIRCLE);
    }
}

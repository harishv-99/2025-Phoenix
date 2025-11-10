package edu.ftcphoenix.robots.phoenix2.controllers;

import com.acmerobotics.roadrunner.Vector2d;

import edu.ftcphoenix.fw2.deprecated.assisteddriving.GuidanceAdjustor;
import edu.ftcphoenix.fw2.deprecated.assisteddriving.shape.Segment;
import edu.ftcphoenix.fw2.deprecated.assisteddriving.shape.Shape;
import edu.ftcphoenix.fw2.deprecated.assisteddriving.shapeadjustor.ShapeAdjustorBuilder;
import edu.ftcphoenix.fw2.deprecated.assisteddriving.shapeadjustor.closestpointadjustor.AttractorAdjustor;
import edu.ftcphoenix.fw2.deprecated.assisteddriving.shapeadjustor.closestpointadjustor.ClosestPointAdjustor;
import edu.ftcphoenix.fw2.deprecated.fsm.AbstractFsmContainer;
import edu.ftcphoenix.fw2.gamepad.rev.GamepadController;
import edu.ftcphoenix.fw2.gamepad.GamepadKeys;
import edu.ftcphoenix.robots.phoenix2.Constants;
import edu.ftcphoenix.robots.phoenix2.Robot;


public class TeleOpControllerOld2 extends AbstractFsmContainer {

    Robot robot;
    GamepadController gp1;

    GuidanceAdjustor guidanceAdjustor;

    public TeleOpControllerOld2(Robot robot) {
        /*
        this.robot = robot;
        gp1 = robot.getGamepad1();

        createGamepadControls();

        createGuidanceAdjustor();

        // Add all the states
        TeleOpStickDriveState stickDriveState = new TeleOpStickDriveState(robot, guidanceAdjustor);
        addState(stickDriveState);

        TeleOpButtonDriveState buttonDriveState = new TeleOpButtonDriveState(robot);
        addState(buttonDriveState);

        // Initialize the container after adding all the states.
        initContainer();
         */
    }

    public void createGuidanceAdjustor() {
        /*
        Shape l = new Segment(new Vector2d(24 + 24 - 9, 0),
                new Vector2d(24 + 24 - 9, 24));
        guidanceAdjustor = new ShapeAdjustorBuilder(
                robot.driveTrainSubsystem.getHolonomicController(), l)
                .addClosestPointAdjustor(
                        new AttractorAdjustor(Shape.RelativeLocation.LEFT_OF_SHAPE,
                                ClosestPointAdjustor.RelativeMovement.ANYWHERE,
                                ClosestPointAdjustor.AxisToAdjust.POSITION)
                )
                .build();
         */
    }

    public void createGamepadControls() {
        // Setup input from gamepad 1
        gp1.createIntervalDoubleUnit(Constants.INTERVAL_NAME_LATERAL,
                GamepadKeys.Stick.LEFT_STICK_X);
        gp1.createIntervalDoubleUnit(Constants.INTERVAL_NAME_AXIAL,
                GamepadKeys.Stick.LEFT_STICK_Y);
        gp1.createIntervalDoubleUnit(Constants.INTERVAL_NAME_ANGULAR,
                GamepadKeys.Stick.RIGHT_STICK_X);

        gp1.createButton(Constants.BUTTON_NAME_MOVE, GamepadKeys.Button.SQUARE);
        gp1.createButton(Constants.BUTTON_NAME_STOP, GamepadKeys.Button.CIRCLE);
    }

    public GuidanceAdjustor getGuidanceAdjustor() {
        return guidanceAdjustor;
    }
}

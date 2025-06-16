package edu.ftcphoenix.robots.phoenix.controllers.teleop;

import edu.ftcphoenix.fw.fsm.AbstractFsmState;
import edu.ftcphoenix.fw.gamepad.GamepadInputs;
import edu.ftcphoenix.fw.util.RoadRunner;
import edu.ftcphoenix.robots.phoenix.Constants;
import edu.ftcphoenix.robots.phoenix.Robot;

public class TeleOpButtonDriveState extends AbstractFsmState {
    Robot robot;
    GamepadInputs gpi;

    public TeleOpButtonDriveState(Robot robot) {
        this.robot = robot;
        gpi = robot.getGamepadInputs();
    }

    @Override
    public void initState() {
    }

    @Override
    public void executeState() {
        if (gpi.getButton(Constants.BUTTON_NAME_MOVE).isDown()) {
            robot.driveTrainSubsystem.drive(RoadRunner.toPoseVelocity2d(0, 0.2, 0));
            robot.getTelemetry().addLine(Constants.BUTTON_NAME_MOVE);
        }

        if (gpi.getButton(Constants.BUTTON_NAME_STOP).isDown())
            robot.driveTrainSubsystem.drive(RoadRunner.toPoseVelocity2d(0,0,0));

        robot.getTelemetry().addData("Voltage", robot.driveTrainSubsystem.getVoltage());
        robot.getTelemetry().addData("Current", robot.driveTrainSubsystem.getCurrent());

        if (robot.driveTrainSubsystem.hasRunIntoObstacle())
            robot.driveTrainSubsystem.drive(RoadRunner.toPoseVelocity2d(0,-0.2,0));

        // Check for transitions out of the state
        if (hasStickMoved())
            getContainer().transitionToState(Constants.TELEOP_STICK_DRIVE_STATE);
    }

    @Override
    public void exitState() {

    }

    private boolean hasStickMoved() {
        return (gpi.getInterval(Constants.INTERVAL_NAME_LATERAL).getValue() != 0 ||
                gpi.getInterval(Constants.INTERVAL_NAME_AXIAL).getValue() != 0 ||
                gpi.getInterval(Constants.INTERVAL_NAME_ANGULAR).getValue() != 0);
    }
}

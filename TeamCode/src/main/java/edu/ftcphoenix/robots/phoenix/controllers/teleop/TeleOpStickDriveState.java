package edu.ftcphoenix.robots.phoenix.controllers.teleop;

import com.acmerobotics.roadrunner.PoseVelocity2d;

import edu.ftcphoenix.fw.assisteddriving.GuidanceAdjustor;
import edu.ftcphoenix.fw.fsm.AbstractFsmState;
import edu.ftcphoenix.fw.gamepad.GamepadInputs;
import edu.ftcphoenix.fw.util.RoadRunner;
import edu.ftcphoenix.robots.phoenix.Constants;
import edu.ftcphoenix.robots.phoenix.Robot;

public class TeleOpStickDriveState extends AbstractFsmState {
    final Robot robot;
    final GamepadInputs gpi;

    final GuidanceAdjustor guidanceAdjustor;

    public TeleOpStickDriveState(Robot robot, GuidanceAdjustor guidanceAdjustor) {
        this.robot = robot;
        gpi = robot.getGamepadInputs();
        this.guidanceAdjustor = guidanceAdjustor;
    }

    @Override
    public void initState() {

    }

    private double slowDown(double power) {
        return com.acmerobotics.roadrunner.Math.clamp(power, -0.3, 0.3);
    }

    @Override
    public void executeState() {
        // Get the input from the operator
        double axial = gpi.getInterval(Constants.INTERVAL_NAME_AXIAL).getValue();
        double lateral = -gpi.getInterval(Constants.INTERVAL_NAME_LATERAL).getValue();
        double angular = -gpi.getInterval(Constants.INTERVAL_NAME_ANGULAR).getValue();

        // Give more control to the user by reducing the impact of small changes
        axial = slowDown(Math.copySign(Math.pow(axial, 4), axial));
        lateral = slowDown(Math.copySign(Math.pow(lateral, 4), lateral));
        angular = slowDown(Math.copySign(Math.pow(angular, 4), angular));


        PoseVelocity2d poseVelOrig = RoadRunner.toPoseVelocity2d(axial, lateral, angular);
        PoseVelocity2d poseVelNew;
        robot.getTelemetry().addData("Pose Vel", poseVelOrig);
//        Pose2d pose = robot.getRobotStateHistory().getLatestRobotStateEntry().
//                getComponentStateEntry(Robot.Components.CHASSIS, PoseEntry.class).getPose();
        poseVelNew = guidanceAdjustor.adjustPoseVelocity(robot.driveTrainSubsystem.getPose(),
                robot.driveTrainSubsystem.getMovePrior(), poseVelOrig);

//        if(pose.position.x >= 20) {
//            // Only allow to go back
//            if (poseVelOrig.linearVel.x > 0)
//                xvel = 0;
//            else
//                xvel = poseVelOrig.linearVel.x;
//            poseVelNew = new PoseVelocity2d(new Vector2d(xvel,
//                    poseVelOrig.linearVel.y), poseVelOrig.angVel);
//        }
//        else
//            poseVelNew = poseVelOrig;

        robot.driveTrainSubsystem.drive(poseVelNew);

        robot.getTelemetry().addData("Voltage", robot.driveTrainSubsystem.getVoltage());
        robot.getTelemetry().addData("Current", robot.driveTrainSubsystem.getCurrent());

        // Transition to button state if button is pushed.
        if (hasButtonBeenPressed())
            getContainer().transitionToState(Constants.TELEOP_BUTTON_DRIVE_STATE);
    }

    @Override
    public void exitState() {

    }

    private boolean hasButtonBeenPressed() {
        return (gpi.getButton(Constants.BUTTON_NAME_MOVE).hasChangedState() ||
                gpi.getButton(Constants.BUTTON_NAME_STOP).hasChangedState());
    }
}

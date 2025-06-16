package edu.ftcphoenix.robots.phoenix.controllers.misc;

import edu.ftcphoenix.fw.robotbase.statehistory.AbstractRobotStateWriter;
import edu.ftcphoenix.fw.robotbase.statehistory.RobotStateEntry;
import edu.ftcphoenix.fw.robotbase.statehistory.RobotStateHistory;
import edu.ftcphoenix.fw.robotbase.statehistory.componentstate.PoseEntry;
import edu.ftcphoenix.robots.phoenix.Robot;

public class RobotStateWriter extends AbstractRobotStateWriter {

    final Robot robot;
    final RobotStateHistory<Robot.Components> robotStateHistory;

    public RobotStateWriter(Robot robot) {
        super(robot.getPeriodicRunner());

        this.robot = robot;
        this.robotStateHistory = robot.getRobotStateHistory();
    }

    @Override
    public void onPeriodic() {
        // Create a new state entry
        RobotStateEntry<Robot.Components> stateEntry = new RobotStateEntry<>();

        // Add chassis state
        robot.driveTrainSubsystem.updatePoseEstimate();
        PoseEntry chassisPose = new PoseEntry(
                robot.driveTrainSubsystem.getPose());
        stateEntry.addComponentStateEntry(Robot.Components.CHASSIS, chassisPose);
        robotStateHistory.addRobotStateEntry(stateEntry, robot.getTimePeriodicStartNanoseconds());
    }
}

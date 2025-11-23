package edu.ftcphoenix.fw2.robotbase.statehistory.componentstate;

import com.acmerobotics.roadrunner.Pose2d;

/**
 * An entry containing {@link Pose2d} information about the component.
 */
public class PoseEntry implements ComponentStateEntry {
    private final Pose2d pose;

    /**
     * Construct a pose entry for the robot with the specific chassis pose.
     *
     * @param pose The pose of the chassis.
     */
    public PoseEntry(Pose2d pose) {
        this.pose = pose;
    }

    /**
     * Get the pose of the chassis.
     *
     * @return The pose of the chassis.
     */
    public Pose2d getPose() {
        return pose;
    }
}

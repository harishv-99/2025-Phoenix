package edu.ftcphoenix.fw.assisteddriving.precondition;

import com.acmerobotics.roadrunner.Pose2d;
import com.acmerobotics.roadrunner.PoseVelocity2d;

import edu.ftcphoenix.fw.assisteddriving.AbstractGuidanceAdjustor;

/**
 * This checks the preconditions that have to be met before the {@link AbstractGuidanceAdjustor} object
 * can make guided adjustments to the robot's velocities.  An instance of this interface can be used to
 * specify whether these preconditions are met.
 */
public interface GuidanceAdjustorPrecondition {
    /**
     * Indicate whether the precondition for the adjustor has been met.
     *
     * @param poseRobot          The pose of the robot.
     * @param moveProposed_Robot The proposed move that needs to be modified to fit the guidance.
     * @return Whether the precondition has been met.
     */
    boolean hasMetPrecondition(Pose2d poseRobot, PoseVelocity2d moveProposed_Robot);
}

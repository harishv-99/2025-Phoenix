package edu.ftcphoenix.fw2.deprecated.assisteddriving.precondition;

import com.acmerobotics.roadrunner.Pose2d;
import com.acmerobotics.roadrunner.PoseVelocity2d;

/**
 * Precondition object for an adjustor applies when the robot speed is "slow".  This can be used to have snap points
 * be enabled when the operator is showing an intention to slow down
 */
public class SlowSpeedPrecondition implements GuidanceAdjustorPrecondition {

    final double speedMax;

    /**
     * Precondition is met if the robot is moving at a "slow" default speed.
     */
    public SlowSpeedPrecondition() {
        this.speedMax = 0.2;
    }

    /**
     * Precondition is met if the robot is going to move at a slower speed than that specified.
     *
     * @param speedMax Maximum speed of the proposed movement for the precondition to be met.
     */
    public SlowSpeedPrecondition(double speedMax) {
        this.speedMax = speedMax;
    }

    @Override
    public boolean hasMetPrecondition(Pose2d poseRobot, PoseVelocity2d moveProposed_Robot) {
        // Check whether the L1 norm, or Manhattan distance, meets the speed threshold.  L2 norm, or Euclidean norm,
        //    is not used to just avoid having to do a sqrt.  The extra value is probably not required.
        return (moveProposed_Robot.linearVel.x + moveProposed_Robot.linearVel.y) < speedMax;
    }
}

package edu.ftcphoenix.fw.assisteddriving;

import com.acmerobotics.roadrunner.Pose2d;
import com.acmerobotics.roadrunner.PoseVelocity2d;

/**
 * During TeleOp mode, the proposed motion of the robot can be adjusted to assist the operator.
 */
public interface GuidanceAdjustor {
    /**
     * Given the robot's position and the proposed move, make adjustments to the movement.  The proposed move input is
     * usually in the "robot" frame, and so is the output.  However, it is possible for an adjuster that directly
     * processes operator input to get a frame that is different (e.g. field/world frame) and the adjustor converts
     * it to robot frame.
     *
     * @param poseRobot             The pose of the robot.
     * @param moveActualPrior_Robot The actual prior move
     * @param moveProposed_Robot    The proposed move that needs to be modified to fit the guidance.
     * @return The modified movement in the robot frame.
     */
    PoseVelocity2d adjustPoseVelocity(Pose2d poseRobot,
                                      PoseVelocity2d moveActualPrior_Robot,
                                      PoseVelocity2d moveProposed_Robot);
}

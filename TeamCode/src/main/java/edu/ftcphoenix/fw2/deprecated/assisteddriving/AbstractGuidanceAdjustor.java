package edu.ftcphoenix.fw2.deprecated.assisteddriving;

import com.acmerobotics.roadrunner.HolonomicController;
import com.acmerobotics.roadrunner.Pose2d;
import com.acmerobotics.roadrunner.PoseVelocity2d;

import java.util.List;

import edu.ftcphoenix.fw2.deprecated.assisteddriving.precondition.GuidanceAdjustorPrecondition;

public abstract class AbstractGuidanceAdjustor implements GuidanceAdjustor {

    final private HolonomicController controller;
    final private List<GuidanceAdjustorPrecondition> lstPreconditions;

    /**
     * Create the guidance adjustor object that will ensure that all preconditions are met before the
     * guidance adjustments are made.
     *
     * @param controller       Holonomic proportional controller used to move to target.
     * @param lstPreconditions List of preconditions that have to hold true.
     */
    protected AbstractGuidanceAdjustor(HolonomicController controller,
                                       List<GuidanceAdjustorPrecondition> lstPreconditions) {
        this.controller = controller;
        this.lstPreconditions = lstPreconditions;
    }

    @Override
    public final PoseVelocity2d adjustPoseVelocity(Pose2d poseRobot, PoseVelocity2d moveActualPrior_Robot,
                                                   PoseVelocity2d moveProposed_Robot) {

        // Ensure that all preconditions are met.  Stop when the first precondition is not met.
        if (lstPreconditions != null) {
            for (GuidanceAdjustorPrecondition precondition : lstPreconditions) {
                if (!precondition.hasMetPrecondition(poseRobot, moveProposed_Robot))
                    // We have failed a precondition test.  Do not modify the proposed move and return it as-is.
                    return moveProposed_Robot;
            }
        }

        // Transform the pose velocity
        return forceAdjustPoseVelocity(poseRobot, moveActualPrior_Robot, moveProposed_Robot);
    }

    /**
     * Transform the pose velocity.  This is an internal function that does not check for preconditions
     * of this guidance object.
     *
     * @param poseRobot             The pose of the robot.
     * @param moveActualPrior_Robot The actual movement of the robot previously in the "robot" frame.
     * @param moveProposed_Robot    The proposed move that needs to be modified to fit the guidance and in the
     *                              "robot" frame.
     * @return The modified movement in the robot frame.
     */
    protected abstract PoseVelocity2d forceAdjustPoseVelocity(Pose2d poseRobot,
                                                              PoseVelocity2d moveActualPrior_Robot,
                                                              PoseVelocity2d moveProposed_Robot);

    protected HolonomicController getController() {
        return controller;
    }
}

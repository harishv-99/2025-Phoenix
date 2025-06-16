package edu.ftcphoenix.fw.assisteddriving;

import com.acmerobotics.roadrunner.Pose2d;
import com.acmerobotics.roadrunner.PoseVelocity2d;

import java.util.ArrayList;
import java.util.List;

import edu.ftcphoenix.fw.assisteddriving.precondition.GuidanceAdjustorPrecondition;

public final class GuidanceCollection extends AbstractGuidanceAdjustor {

    private final List<GuidanceAdjustor> lstAdjustors;

    public GuidanceCollection() {
        this(null);
    }
    public GuidanceCollection(List<GuidanceAdjustorPrecondition> lstPreconditions) {
        super(null, lstPreconditions);

        lstAdjustors = new ArrayList<>();
    }

    /**
     * Add a new guidance adjustor to the collection.
     *
     * @param adjustor The adjustor to add.
     * @return The collection in case more adjustors need to be added in a chain.
     */
    public GuidanceCollection add(GuidanceAdjustor adjustor) {
        lstAdjustors.add(adjustor);

        return this;
    }

    @Override
    public PoseVelocity2d forceAdjustPoseVelocity(Pose2d poseRobot,
                                                  PoseVelocity2d moveActualPrior_Robot,
                                                  PoseVelocity2d moveProposed_Robot) {
        // Run all the adjustors in the collection and keep updating the same move.
        PoseVelocity2d moveProposedMod_Robot = moveProposed_Robot;
        for (GuidanceAdjustor adjustor : lstAdjustors) {
            moveProposedMod_Robot = adjustor.adjustPoseVelocity(poseRobot, moveActualPrior_Robot, moveProposedMod_Robot);
        }

        // Return the move after all modifications.
        return moveProposedMod_Robot;
    }
}

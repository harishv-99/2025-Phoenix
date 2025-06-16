package edu.ftcphoenix.fw.assisteddriving.shapeadjustor;

import com.acmerobotics.roadrunner.Arclength;
import com.acmerobotics.roadrunner.HolonomicController;
import com.acmerobotics.roadrunner.Pose2d;
import com.acmerobotics.roadrunner.PoseVelocity2d;
import com.acmerobotics.roadrunner.Rotation2d;
import com.acmerobotics.roadrunner.Vector2d;
import com.acmerobotics.roadrunner.Vector2dDual;

import java.util.ArrayList;
import java.util.List;

import edu.ftcphoenix.fw.assisteddriving.AbstractGuidanceAdjustor;
import edu.ftcphoenix.fw.assisteddriving.GuidanceAdjustor;
import edu.ftcphoenix.fw.assisteddriving.precondition.GuidanceAdjustorPrecondition;
import edu.ftcphoenix.fw.assisteddriving.shape.Shape;
import edu.ftcphoenix.fw.assisteddriving.shapeadjustor.closestpointadjustor.ClosestPointAdjustor;
import edu.ftcphoenix.fw.assisteddriving.shapeadjustor.closestpointadjustor.ClosestPointAdjustorData;
import edu.ftcphoenix.fw.util.MathEx;
import edu.ftcphoenix.robots.phoenix.Robot;


/**
 * Construct a shape adjustor which can be used to add a guided vector field for tele-op mode using a shape.
 */
public class ShapeAdjustorBuilder {

    HolonomicController controller;
    Shape shape;
    List<GuidanceAdjustorPrecondition> lstPreconditions = new ArrayList<>();
    List<ClosestPointAdjustor> lstAdjustors = new ArrayList<>();

    /**
     * Construct a shape-based guidance adjustor.
     *
     * @param controller Holonomic controller used for movement.
     * @param shape      The shape to use as the basis for the shape adjustor.
     */
    public ShapeAdjustorBuilder(HolonomicController controller, Shape shape) {
        this.controller = controller;
        this.shape = shape;
    }

    /**
     * Add preconditions that have to be valid before the adjustment is made.  These preconditions are
     * checked in the order in which they are added.  If any precondition fails, no further checks are made.
     *
     * @param precondition The object that will evaluate whether the precondition has been met.
     * @return This builder so build methods can be chained.
     */
    public ShapeAdjustorBuilder addAdjustPrecondition(GuidanceAdjustorPrecondition precondition) {
        lstPreconditions.add(precondition);
        return this;
    }

    public ShapeAdjustorBuilder addClosestPointAdjustor(ClosestPointAdjustor adjustor) {
        lstAdjustors.add(adjustor);
        return this;
    }

    /**
     * Build the shape adjustor object with the parameters provided so far.
     *
     * @return Shape adjustor object.
     */
    public GuidanceAdjustor build() {
        return new ShapeAdjustor(controller, shape, lstPreconditions, lstAdjustors);
    }
}

/**
 * Adjust the guidance for tele-op mode using a shape as the basis for the guided vector field.
 */
class ShapeAdjustor extends AbstractGuidanceAdjustor {

    final private Shape shape;
    final private List<ClosestPointAdjustor> lstAdjustors;

    /**
     * Adjust the pose velocity and guide tele-op using a shape as the basis for the adjustment.
     *
     * @param controller       Holonomic proportional controller for movement.
     * @param shape            The base shape to use to guide the tele-op.
     * @param lstPreconditions The list of preconditions that have to be met before these adjustments can be made.
     * @param lstAdjustors     The list of adjustors that should be applied one at a time.
     */
    public ShapeAdjustor(HolonomicController controller, Shape shape, List<GuidanceAdjustorPrecondition> lstPreconditions,
                         List<ClosestPointAdjustor> lstAdjustors) {
        super(controller, lstPreconditions);

        this.shape = shape;
        this.lstAdjustors = lstAdjustors;
    }

    @Override
    public PoseVelocity2d forceAdjustPoseVelocity(Pose2d poseRobot,
                                                  PoseVelocity2d moveActualPrior_Robot,
                                                  PoseVelocity2d moveProposed_Robot) {

        // Reference: https://math.libretexts.org/Bookshelves/Applied_Mathematics/Mathematics_for_Game_Developers_(Burzynski)/02%3A_Vectors_In_Two_Dimensions/2.06%3A_The_Vector_Projection_of_One_Vector_onto_Another

        // Find the closest point and its tangent.  Get the base tangent and normal vectors pointing towards the
        // robot.
        Vector2dDual<Arclength> vecdClosestPoint = shape.getClosestPoint(poseRobot.position);
        Vector2d vecunClosestTangent = MathEx.normalize(MathEx.getTangentTowards(vecdClosestPoint, poseRobot.position));
        Vector2d vecunClosestNormal = MathEx.normalize(MathEx.getNormalTowards(vecdClosestPoint,
                poseRobot.position));

        Robot.g_Robot.getTelemetry().addData("norm tow", MathEx.getNormalTowards(vecdClosestPoint,
                poseRobot.position));
        Robot.g_Robot.getTelemetry().addData("closest pt", vecdClosestPoint.value());
        Robot.g_Robot.getTelemetry().addData("pose robot", poseRobot.position);


        // Project move onto tangent and its normal.
        Rotation2d rotRobotToWorld = poseRobot.heading;
        Vector2d vecMoveRobot = rotRobotToWorld.times(moveProposed_Robot.linearVel);

        // Get the initial values of the proposed move that will be modified by the first adjustor.
        double magfClosestNormal = MathEx.getMagnitudeFactorOfProjection(vecMoveRobot, vecunClosestNormal);
        double magfClosestTangent = MathEx.getMagnitudeFactorOfProjection(vecMoveRobot, vecunClosestTangent);
        double velAngle = moveProposed_Robot.angVel;

        // Compute auxiliary information about robot's pose and velocity with respect to shape
        // ...Get the relative location of robot to shape
        Shape.RelativeLocation locationRobot = shape.getRelativeLocationOfPoint(poseRobot.position);

        // ...Compute the direction of movement on the tangent vector of the closest point.
        ClosestPointAdjustor.RelativeMovement movementRobotTangent = (magfClosestTangent < 0) ?
                ClosestPointAdjustor.RelativeMovement.TOWARDS_CLOSEST_POINT :
                ClosestPointAdjustor.RelativeMovement.AWAY_FROM_CLOSEST_POINT;

        // ...Compute the direction of movement on the normal vector of the closest point.
        ClosestPointAdjustor.RelativeMovement movementRobotNormal = (magfClosestNormal < 0) ?
                ClosestPointAdjustor.RelativeMovement.TOWARDS_CLOSEST_POINT :
                ClosestPointAdjustor.RelativeMovement.AWAY_FROM_CLOSEST_POINT;


        // Call all the closest point adjustors.  Since each is only modifying the magnitude factor, the
        //    output of one adjustor can be passed along to the next.
        for (ClosestPointAdjustor adjustor : lstAdjustors) {

            // Get the latest values to pass into the adjustor.  In each iteration, the unit vector of the tangent
            //    and the normal does not change.
            ClosestPointAdjustorData data = new ClosestPointAdjustorData(getController(), shape, poseRobot,
                    moveActualPrior_Robot, vecdClosestPoint.value(), vecunClosestTangent, magfClosestTangent,
                    vecunClosestNormal, magfClosestNormal, velAngle, locationRobot, movementRobotTangent,
                    movementRobotNormal);

            // Run the adjustor
            adjustor.adjustPoseVelocityComponents(data);

            // Get the updated values
            magfClosestNormal = data.getModifiedMagnitudeFactorOfVelClosestNormal();
            Robot.g_Robot.getTelemetry().addData("Normal", magfClosestNormal);
            magfClosestTangent = data.getModifiedMagnitudeFactorOfVelClosestTangent();
            Robot.g_Robot.getTelemetry().addData("Tangent", magfClosestTangent);
            velAngle = data.getModifiedVelocityAngle();

            // Ensure that the values are within bounds.
            double magfMax = Math.max(Math.abs(magfClosestNormal), Math.abs(magfClosestTangent));
            if (magfMax > 1) {
                magfClosestNormal /= magfMax;
                magfClosestTangent /= magfMax;
            }
        }

//
//        // If the movement is towards the shape (or its magnitude is negative), then apply friction
//        double frictionMultiplier = 1;
//        if (magfClosestNormal < 0) {
//            // Apply the friction
//            // TODO: Get a friction function to apply.  It could be a uniform friction or could be a function
//            //    of distance.
//            frictionMultiplier = 0.5;
//        }

        // Compute the move vector by combining the tangent and normal vectors.
        Vector2d moveMod = vecunClosestNormal.times(magfClosestNormal)
                .plus((vecunClosestTangent.times(magfClosestTangent)));

        // Transform the move vector back to the robot frame.
        Vector2d moveModified_Robot = rotRobotToWorld.inverse().times(moveMod);

        // Return the modified move in the robot frame
        return new PoseVelocity2d(moveModified_Robot, velAngle);
    }
}

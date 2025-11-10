package edu.ftcphoenix.fw2.deprecated.assisteddriving.shapeadjustor.closestpointadjustor;

import com.acmerobotics.roadrunner.DualNum;
import com.acmerobotics.roadrunner.Pose2dDual;
import com.acmerobotics.roadrunner.PoseVelocity2d;
import com.acmerobotics.roadrunner.PoseVelocity2dDual;
import com.acmerobotics.roadrunner.Rotation2dDual;
import com.acmerobotics.roadrunner.Time;
import com.acmerobotics.roadrunner.Vector2d;
import com.acmerobotics.roadrunner.Vector2dDual;

import edu.ftcphoenix.fw2.deprecated.assisteddriving.shape.Shape;
import edu.ftcphoenix.fw2.util.GeomUtil;

/**
 * Abstract adjustor for closest point that saves the location, movement, and axis to adjust.  If an adjustor
 * will match the location and movement of the actual robot against the criteria, this should be used.
 */
public abstract class AbstractClosestPointAdjustor implements ClosestPointAdjustor {
    private final Shape.RelativeLocation locationCriteria;
    private final ClosestPointAdjustor.RelativeMovement movementCriteriaNormal;
    private final ClosestPointAdjustor.RelativeMovement movementCriteriaTangent;
    private final ClosestPointAdjustor.AxisToAdjust axisToAdjust;

    protected AbstractClosestPointAdjustor(Shape.RelativeLocation locationCriteria,
                                           RelativeMovement movementCriteriaNormal,
                                           RelativeMovement movementCriteriaTangent,
                                           AxisToAdjust axisToAdjust) {
        this.locationCriteria = locationCriteria;
        this.movementCriteriaNormal = movementCriteriaNormal;
        this.movementCriteriaTangent = movementCriteriaTangent;
        this.axisToAdjust = axisToAdjust;
    }

    @Override
    public void adjustPoseVelocityComponents(ClosestPointAdjustorData data) {
        if (matchesLocationAndMovementCriteria(data))
            forceAdjustPoseVelocityComponents(data);
    }

    /**
     * Force the pose velocity component changes.  The location and movement criteria would have already been
     * compared.
     *
     * @param data The data used by closest point adjustors.
     * @see ClosestPointAdjustorData#setModifiedVelocityAngle(double)
     * @see ClosestPointAdjustorData#setModifiedMagnitudeFactorOfVelClosestNormal(double)
     * @see ClosestPointAdjustorData#setModifiedMagnitudeFactorOfVelClosestTangent(double)
     */
    protected abstract void forceAdjustPoseVelocityComponents(ClosestPointAdjustorData data);

    protected Shape.RelativeLocation getLocationCriteria() {
        return locationCriteria;
    }

    protected RelativeMovement getMovementCriteriaNormal() {
        return movementCriteriaNormal;
    }

    public RelativeMovement getMovementCriteriaTangent() {
        return movementCriteriaTangent;
    }

    protected AxisToAdjust getAxisToAdjust() {
        return axisToAdjust;
    }

    /**
     * Does the actual location and movement of the robot match the criteria specified.
     *
     * @param data Data provided to the adjustors
     * @return Whether the location and movement matches the criteria.
     */
    private boolean matchesLocationAndMovementCriteria(ClosestPointAdjustorData data) {
        RelativeMovement movementActualNormal = data.getMovementActualRobotNormal();
        RelativeMovement movementActualTangent = data.getMovementActualRobotTangent();
        Shape.RelativeLocation locationActual = data.getLocationActualRobot();

        return getMovementCriteriaNormal().isSupsersetOf(movementActualNormal) &&
                getMovementCriteriaTangent().isSupsersetOf(movementActualTangent) &&
                getLocationCriteria().isSupersetOf(locationActual);
    }

    protected MovementToClosestPoint getMovementToClosestPoint(ClosestPointAdjustorData data) {
        double magfNormal;
        double magfTangent;
        double velAngle;


        // Apply friction to the motion in the direction of the shape.
        Pose2dDual<Time> txWorldTarget;

        Vector2d vecRobotToClosestPoint = data.getVectorClosestPoint().minus(data.getPoseActualRobot().position);

        // Set the target to move towards the closest point and point towards it.  Whether the movement
        //    actually takes place will later depend on the axis to adjust.  This will be done later.
        //
        //    Also, the target velocity (or 2nd derivative) is 0 because we need the robot at rest.
        txWorldTarget = new Pose2dDual<>(
                new Vector2dDual<>(
                        new DualNum<>(new double[]{data.getVectorClosestPoint().x, 0}),
                        new DualNum<>(new double[]{data.getVectorClosestPoint().y, 0})
                ),

                new Rotation2dDual<>(
                        new DualNum<>(new double[]{
                                vecRobotToClosestPoint.x,
                                0
                        }),
                        new DualNum<>(new double[]{
                                vecRobotToClosestPoint.y,
                                0})
                )
        );

        // Use the proportional controller to get the initial move.
        PoseVelocity2dDual<Time> command_Robot = data.getController()
                .compute(txWorldTarget, data.getPoseActualRobot(),
                        data.getMoveActualPrior_Robot());
        PoseVelocity2d command = data.getPoseActualRobot().heading.times(command_Robot.value());

        // Apply the multiplier to the chosen axis.  It is possible for multiple axes to be matched.
        // ...Do we move normal to closest point?
        Vector2d vecNormal = data.getVectorUnitClosestNormal();
        magfNormal = GeomUtil.getMagnitudeFactorOfProjection(command.linearVel, vecNormal);

        // ...Compute movement on tangent vector.
        Vector2d vecTangent = data.getVectorUnitClosestTangent();
        magfTangent = GeomUtil.getMagnitudeFactorOfProjection(command.linearVel, vecTangent);

        // ...Compute heading adjustment to point to closest point?
        velAngle = command_Robot.angVel.value();

        // Return the movement required to closest point
        return new MovementToClosestPoint(magfTangent, magfNormal, velAngle);
    }
}

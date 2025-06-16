package edu.ftcphoenix.fw.assisteddriving.shapeadjustor.closestpointadjustor;


import edu.ftcphoenix.fw.assisteddriving.shape.Shape;

public class AttractorAdjustor extends AbstractClosestPointAdjustor {

    public AttractorAdjustor(Shape.RelativeLocation locationCriteria,
                             ClosestPointAdjustor.RelativeMovement movementCriteriaNormal,
                             ClosestPointAdjustor.AxisToAdjust axisToAdjust) {
        super(locationCriteria, movementCriteriaNormal, RelativeMovement.ANYWHERE, axisToAdjust);
    }

    @Override
    public void forceAdjustPoseVelocityComponents(ClosestPointAdjustorData data) {
        MovementToClosestPoint movementToClosestPoint = getMovementToClosestPoint(data);

        // Apply the multiplier to the chosen axis.  It is possible for multiple axes to be matched.
        // ...Do we move normal to closest point?
        if (getAxisToAdjust().isSupersetOf(AxisToAdjust.NORMAL_TO_CLOSEST_POINT)) {
            data.setModifiedMagnitudeFactorOfVelClosestNormal(
                    movementToClosestPoint.getMagnitudeFactorOfVelClosestNormal()
            );
        }

        // ...Do we move tangent to closest point?
        if (getAxisToAdjust().isSupersetOf(AxisToAdjust.TANGENT_TO_CLOSEST_POINT)) {
            data.setModifiedMagnitudeFactorOfVelClosestTangent(
                    movementToClosestPoint.getMagnitudeFactorOfVelClosestTangent()
            );
        }

        // ...Do we adjust heading to point to closest point?
        if (getAxisToAdjust().isSupersetOf(AxisToAdjust.HEADING)) {
            data.setModifiedVelocityAngle(
                    movementToClosestPoint.getModifiedVelocityAngle()
            );
        }
    }
}

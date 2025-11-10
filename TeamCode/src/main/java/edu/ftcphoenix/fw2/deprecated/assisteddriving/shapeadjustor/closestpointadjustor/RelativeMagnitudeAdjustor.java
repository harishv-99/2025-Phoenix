package edu.ftcphoenix.fw2.deprecated.assisteddriving.shapeadjustor.closestpointadjustor;

import edu.ftcphoenix.fw2.deprecated.assisteddriving.shape.Shape;

/**
 * Apply relative guidance by multiplying the magnitude factor with some constant.
 */
public class RelativeMagnitudeAdjustor extends AbstractClosestPointAdjustor {
    private final double movementMultiplier;

    public RelativeMagnitudeAdjustor(Shape.RelativeLocation location,
                                     RelativeMovement movementNormal,
                                     AxisToAdjust axisToAdjust,
                                     double movementMultiplier) {
        super(location, movementNormal, RelativeMovement.ANYWHERE, axisToAdjust);

        this.movementMultiplier = movementMultiplier;
    }

    @Override
    public void forceAdjustPoseVelocityComponents(ClosestPointAdjustorData data) {
        // Apply friction to the motion in the direction of the shape.
        double magfProposedClosestNormal = data.getMagnitudeFactorProposedOfClosestNormal();
        double magfProposedClosestTangent = data.getMagnitudeFactorProposedOfClosestTangent();
        MovementToClosestPoint movementToClosestPoint = getMovementToClosestPoint(data);

        // Apply the multiplier to the chosen axis and use that.  However if this will overshoot the closest
        //    point, use the smaller force required to get to the closest point.

        if (getAxisToAdjust() == AxisToAdjust.NORMAL_TO_CLOSEST_POINT) {
            // Apply the multiplier proposed.
            double magfMultiplier = magfProposedClosestNormal * movementMultiplier;

            // Find the smaller force ensuring we do not overshoot the closest point.
            double magf = Math.copySign(
                    Math.min(
                            Math.abs(magfMultiplier),
                            Math.abs(movementToClosestPoint.getMagnitudeFactorOfVelClosestNormal())
                    ),
                    magfMultiplier);
            data.setModifiedMagnitudeFactorOfVelClosestNormal(magf);
        } else if (getAxisToAdjust() == AxisToAdjust.TANGENT_TO_CLOSEST_POINT) {
            // Apply the multiplier proposed.
            double magfMultiplier = magfProposedClosestTangent * movementMultiplier;

            // Find the smaller force ensuring we do not overshoot the closest point.
            double magf = Math.copySign(
                    Math.min(
                            Math.abs(magfMultiplier),
                            Math.abs(movementToClosestPoint.getMagnitudeFactorOfVelClosestNormal())
                    ),
                    magfMultiplier);
            data.setModifiedMagnitudeFactorOfVelClosestTangent(magf);
        }
    }
}

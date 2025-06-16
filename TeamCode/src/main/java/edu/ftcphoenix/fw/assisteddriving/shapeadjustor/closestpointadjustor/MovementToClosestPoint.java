package edu.ftcphoenix.fw.assisteddriving.shapeadjustor.closestpointadjustor;

/**
 * This contains the component vector and heading movements required to move towards and point to the closest point.
 */
public class MovementToClosestPoint {
    private final double magfVelClosestTangent;
    private final double magfVelClosestNormal;
    private final double velocityAngle;

    public MovementToClosestPoint(double magfVelClosestTangent,
                                  double magfVelClosestNormal,
                                  double velocityAngle) {
        this.magfVelClosestNormal = magfVelClosestNormal;
        this.magfVelClosestTangent = magfVelClosestTangent;
        this.velocityAngle = velocityAngle;
    }

    public double getMagnitudeFactorOfVelClosestTangent() {
        return magfVelClosestTangent;
    }

    public double getMagnitudeFactorOfVelClosestNormal() {
        return magfVelClosestNormal;
    }

    public double getModifiedVelocityAngle() {
        return velocityAngle;
    }
}

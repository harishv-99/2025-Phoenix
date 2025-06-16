package edu.ftcphoenix.fw.assisteddriving.shapeadjustor.closestpointadjustor;

import com.acmerobotics.roadrunner.HolonomicController;
import com.acmerobotics.roadrunner.Pose2d;
import com.acmerobotics.roadrunner.PoseVelocity2d;
import com.acmerobotics.roadrunner.Vector2d;

import edu.ftcphoenix.fw.assisteddriving.shape.Shape;

public class ClosestPointAdjustorData {

    private final HolonomicController controller;
    private final Shape shape;
    private final Pose2d poseActualRobot;
    private final PoseVelocity2d moveActualPrior_Robot;
    private final Vector2d vecClosestPoint;
    private final Vector2d vecunVelClosestTangent;
    private final Vector2d vecunVelClosestNormal;
    private final double magfProposedVelClosestTangent;
    private final double magfProposedVelClosestNormal;
    private final double velocityProposedAngle;
    private final Shape.RelativeLocation locationActualRobot;
    private final ClosestPointAdjustor.RelativeMovement movementActualRobotTangent;
    private final ClosestPointAdjustor.RelativeMovement movementActualRobotNormal;

    private double magfModVelClosestTangent;
    private double magfModVelClosestNormal;
    private double modifiedVelocityAngle;

    /**
     * Pass the data required for the closest point adjustors to make informed changes.
     * <p>
     * If the adjustor makes any changes, those changes are made in this object.  The functions used for this
     * are:
     * <li>{@link #setModifiedVelocityAngle(double)}</li>
     * <li>{@link #setModifiedMagnitudeFactorOfVelClosestTangent(double)}</li>
     * <li>{@link #setModifiedMagnitudeFactorOfVelClosestNormal(double)}</li>
     *
     * @param controller                    Holonomic proportional controller for movement.
     * @param shape                         The shape that is used to as the guide for the guided vector field.
     * @param poseActualRobot               The pose of the robot.
     * @param moveActualPrior_Robot         The actual move made by the robot in the prior iteration and in "robot" frame.
     * @param vecClosestPoint               The closest point on the shape.
     * @param vecunVelClosestTangent        Unit vector that represents the tangent at the closest point.
     * @param magfProposedVelClosestTangent The magnitude factor of the proposed move projected onto the closest
     *                                      tangent.  Multiply these two to get the vector projection.
     * @param vecunVelClosestNormal         Unit vector that is normal to the tangent where the
     *                                      positive direction points towards the location of the robot.
     * @param magfProposedVelClosestNormal  The magnitude factor of the proposed move projected on to the
     *                                      normal vector.  Multiply these two to get the vector
     *                                      projection on the normal.
     * @param velocityProposedAngle         The angle change to the heading of the robot.
     * @param locationActualRobot           Location of the robot with respect to the shape.
     * @param movementActualRobotTangent    Movement of the robot with respect to the tangent at the closest point.
     * @param movementActualRobotNormal     Movement of the robot with respect to the normal at the closest point.
     */
    public ClosestPointAdjustorData(HolonomicController controller,
                                    Shape shape,
                                    Pose2d poseActualRobot,
                                    PoseVelocity2d moveActualPrior_Robot,
                                    Vector2d vecClosestPoint,
                                    Vector2d vecunVelClosestTangent,
                                    double magfProposedVelClosestTangent,
                                    Vector2d vecunVelClosestNormal,
                                    double magfProposedVelClosestNormal,
                                    double velocityProposedAngle,
                                    Shape.RelativeLocation locationActualRobot,
                                    ClosestPointAdjustor.RelativeMovement movementActualRobotTangent,
                                    ClosestPointAdjustor.RelativeMovement movementActualRobotNormal) {
        this.controller = controller;
        this.shape = shape;
        this.poseActualRobot = poseActualRobot;
        this.moveActualPrior_Robot = moveActualPrior_Robot;
        this.vecClosestPoint = vecClosestPoint;
        this.vecunVelClosestTangent = vecunVelClosestTangent;
        this.magfProposedVelClosestTangent = magfProposedVelClosestTangent;
        this.vecunVelClosestNormal = vecunVelClosestNormal;
        this.magfProposedVelClosestNormal = magfProposedVelClosestNormal;
        this.velocityProposedAngle = velocityProposedAngle;
        this.locationActualRobot = locationActualRobot;
        this.movementActualRobotTangent = movementActualRobotTangent;
        this.movementActualRobotNormal = movementActualRobotNormal;

        // Set the default modified values to be the same as the initial proposed values
        magfModVelClosestTangent = magfProposedVelClosestTangent;
        magfModVelClosestNormal = magfProposedVelClosestNormal;
        modifiedVelocityAngle = velocityProposedAngle;
    }

    public HolonomicController getController() {
        return controller;
    }

    public Shape getShape() {
        return shape;
    }

    public Pose2d getPoseActualRobot() {
        return poseActualRobot;
    }

    public PoseVelocity2d getMoveActualPrior_Robot() {
        return moveActualPrior_Robot;
    }

    public Vector2d getVectorClosestPoint() {
        return vecClosestPoint;
    }

    public Vector2d getVectorUnitClosestTangent() {
        return vecunVelClosestTangent;
    }

    public Vector2d getVectorUnitClosestNormal() {
        return vecunVelClosestNormal;
    }

    public double getMagnitudeFactorProposedOfClosestTangent() {
        return magfProposedVelClosestTangent;
    }

    public double getMagnitudeFactorProposedOfClosestNormal() {
        return magfProposedVelClosestNormal;
    }

    public double getVelocityProposedAngle() {
        return velocityProposedAngle;
    }

    public Shape.RelativeLocation getLocationActualRobot() {
        return locationActualRobot;
    }

    public ClosestPointAdjustor.RelativeMovement getMovementActualRobotTangent() {
        return movementActualRobotTangent;
    }

    public ClosestPointAdjustor.RelativeMovement getMovementActualRobotNormal() {
        return movementActualRobotNormal;
    }

    public void setModifiedMagnitudeFactorOfVelClosestTangent(double magfVelClosestTangent) {
        this.magfModVelClosestTangent = magfVelClosestTangent;
    }

    public double getModifiedMagnitudeFactorOfVelClosestTangent() {
        return magfModVelClosestTangent;
    }

    public void setModifiedMagnitudeFactorOfVelClosestNormal(double magfVelClosestNormal) {
        this.magfModVelClosestNormal = magfVelClosestNormal;
    }

    public double getModifiedMagnitudeFactorOfVelClosestNormal() {
        return magfModVelClosestNormal;
    }

    public void setModifiedVelocityAngle(double velocityAngle) {
        modifiedVelocityAngle = velocityAngle;
    }

    public double getModifiedVelocityAngle() {
        return modifiedVelocityAngle;
    }
}

package edu.ftcphoenix.fw.assisteddriving.precondition;

import com.acmerobotics.roadrunner.Pose2d;
import com.acmerobotics.roadrunner.PoseVelocity2d;

import edu.ftcphoenix.fw.assisteddriving.shape.Shape;

/**
 * Precondition object for an adjustor which makes sure that the robot is in the correct location -- i.e.
 * either inside or outside -- with respect to the specified enclosed shape.
 */
public class LocationWrtShapePrecondition implements GuidanceAdjustorPrecondition {

    private final Shape shape;
    private final Shape.RelativeLocation location;

    /**
     * Precondition is met if the robot is in the appropriate location with respect to the shape.
     * @param shape The shape to use for checking the precondition.
     */
    public LocationWrtShapePrecondition(Shape shape, Shape.RelativeLocation location) {
        this.shape = shape;
        this.location = location;
    }

    /**
     * Precondition is met if the robot is inside the shape.
     * @param shape The shape to use for checking the precondition.
     */
    public LocationWrtShapePrecondition(Shape shape) {
        this.shape = shape;
        this.location = Shape.RelativeLocation.INSIDE_OF_SHAPE;
    }

    @Override
    public boolean hasMetPrecondition(Pose2d poseRobot, PoseVelocity2d moveProposed_Robot) {
        return location.isSupersetOf(shape.getRelativeLocationOfPoint(poseRobot.position));
    }
}

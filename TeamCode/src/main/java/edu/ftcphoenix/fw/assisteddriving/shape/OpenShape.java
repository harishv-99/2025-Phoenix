package edu.ftcphoenix.fw.assisteddriving.shape;

import com.acmerobotics.roadrunner.Vector2d;

public interface OpenShape extends Shape {
    /**
     * Which side of the shape is the specified point?
     * <p>
     * Side is determined by starting at the start point of the shape and moving to form the shape.
     * At that moment, is the specified point to the right or left?
     * <p>
     * If the specified point is directly ahead of or behind the shape's start point, then it is
     * considered to be on the right side.
     *
     * @param point The point to test whether it is to the left or right
     * @return Whether the point is on the left or right side.
     */
    Shape.RelativeLocation getRelativeLocationOfPoint(Vector2d point);

    enum RelativeLocation {
        LEFT_OF_SHAPE,
        RIGHT_OF_SHAPE
    }
}

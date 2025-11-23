package edu.ftcphoenix.fw2.deprecated.assisteddriving.shape;

import com.acmerobotics.roadrunner.Vector2d;

public interface EnclosedShape extends Shape {
    /**
     * Is the point inside the shape or outside it?
     * <p>
     * This can be used to find out whether the
     * robot is inside the influence zone, and this shape can define that zone.
     *
     * @param point The point to test.
     * @return Relative position of point to shape -- inside or outside.
     */
    Shape.RelativeLocation getRelativeLocationOfPoint(Vector2d point);

    enum RelativeLocation {
        INSIDE_OF_SHAPE,
        OUTSIDE_OF_SHAPE
    }
}

package edu.ftcphoenix.fw2.deprecated.assisteddriving.shape;

import com.acmerobotics.roadrunner.Arclength;
import com.acmerobotics.roadrunner.Pose2d;
import com.acmerobotics.roadrunner.Rotation2d;
import com.acmerobotics.roadrunner.Vector2d;
import com.acmerobotics.roadrunner.Vector2dDual;

public interface Shape {
    /**
     * Find the point on the shape closest to the specified point (e.g. robot's position).
     *
     * @param point The point to test.
     * @return The distance from the given point to the closest point on the shape.
     */
    Vector2dDual<Arclength> getClosestPoint(Vector2d point);


    /**
     * Get the point where the shape is intersected by a pose.
     *
     * @param pose The pose to figure out whether it intersects with shape.
     * @return The point on the shape where the pose intersects the shape.  If there is no
     * intersection, null is returned.
     */
    Vector2dDual<Arclength> getPointIntersectedBy(Pose2d pose);


    /**
     * Get the minimal amount to turn the heading to first contact the shape.
     *
     * @param pose The position and the current angle to measure from.
     * @return The minimal amount required to turn.
     */
    Rotation2d getClosestHeading(Pose2d pose);

    /**
     * Where is the specified point with respect to the shape?
     * <p>
     * Open shapes would define "left" and "right".  Side is determined by starting at the start point of the shape
     * and moving to form the shape.  At that moment, is the specified point to the right or left?  If the specified
     * point is directly ahead of or behind the shape's start point, then it is
     * considered to be on the right side.
     * <p>
     * Enclosed shapes would define "inside" and "outside".
     *
     * @param point The point to test its relative location.
     * @return The relative location of the point.
     */
    RelativeLocation getRelativeLocationOfPoint(Vector2d point);

    /**
     * Define positions relative to the shape.  Enclosed shapes would define inside and outside whereas open shapes
     * would use left and right of shape.  ANYWHERE will "match" any location when using
     * {@link #isSupersetOf(RelativeLocation)}.
     */
    enum RelativeLocation {
        INSIDE_OF_SHAPE,
        OUTSIDE_OF_SHAPE,
        LEFT_OF_SHAPE,
        RIGHT_OF_SHAPE,
        ANYWHERE;

        /**
         * Is this relative location a superset of the given relative location?  ANYWHERE is a superset of any
         * location.
         *
         * @param compareTo The relative location to compare this to.
         * @return Whether the locations match.
         */
        public boolean isSupersetOf(RelativeLocation compareTo) {
            // ANYWHERE is a superset of any other item.
            if (this == ANYWHERE)
                return true;

            // Otherwise, we should have an exact match for this to be a superset.
            return this == compareTo;
        }
    }
}

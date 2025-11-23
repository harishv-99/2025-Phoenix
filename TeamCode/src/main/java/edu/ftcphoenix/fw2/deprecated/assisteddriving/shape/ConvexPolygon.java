package edu.ftcphoenix.fw2.deprecated.assisteddriving.shape;

import com.acmerobotics.roadrunner.Arclength;
import com.acmerobotics.roadrunner.Pose2d;
import com.acmerobotics.roadrunner.Rotation2d;
import com.acmerobotics.roadrunner.Vector2d;
import com.acmerobotics.roadrunner.Vector2dDual;

public class ConvexPolygon implements Shape {


    private final Vector2d corner1;
    private final Vector2d corner2;
    public ConvexPolygon(double x1, double y1, double x2, double y2) {
        corner1 = new Vector2d(x1, y1);
        corner2 = new Vector2d(x2, y2);
    }

    @Override
    public RelativeLocation getRelativeLocationOfPoint(Vector2d point) {
        boolean isXInsideRect = point.x >= Math.min(corner1.x, corner2.x) &&
                point.x <= Math.max(corner1.x, corner2.x);
        boolean isYInsideRect = point.y >= Math.min(corner1.y, corner2.y) &&
                point.y <= Math.max(corner1.y, corner2.y);

        if (isXInsideRect && isYInsideRect)
            return RelativeLocation.INSIDE_OF_SHAPE;

        return RelativeLocation.OUTSIDE_OF_SHAPE;
    }

    @Override
    public Vector2dDual<Arclength> getClosestPoint(Vector2d point) {
        return null;
    }

    @Override
    public Vector2dDual<Arclength> getPointIntersectedBy(Pose2d pose) {
        return null;
    }

    @Override
    public Rotation2d getClosestHeading(Pose2d pose) {
        return null;
    }
}

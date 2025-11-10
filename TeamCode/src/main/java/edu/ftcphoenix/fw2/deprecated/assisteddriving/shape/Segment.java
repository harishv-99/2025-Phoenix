package edu.ftcphoenix.fw2.deprecated.assisteddriving.shape;

import com.acmerobotics.roadrunner.Arclength;
import com.acmerobotics.roadrunner.Math;
import com.acmerobotics.roadrunner.Pose2d;
import com.acmerobotics.roadrunner.Rotation2d;
import com.acmerobotics.roadrunner.Vector2d;
import com.acmerobotics.roadrunner.Vector2dDual;

import edu.ftcphoenix.fw2.util.GeomUtil;

public class Segment implements Shape {

    private final Vector2d start;
    private final Vector2d end;

    private final Vector2d lineNormalized;
    private final double length;

    public Segment(Vector2d start, Vector2d end) {
        this.start = start;
        this.end = end;

        // Pre-compute some common items
        Vector2d lineTemp = end.minus(start);
        length = lineTemp.norm();
        lineNormalized = GeomUtil.normalize(lineTemp);
    }

    @Override
    public RelativeLocation getRelativeLocationOfPoint(Vector2d point) {
        // Use determinants to decide position of point with respect to line.
        //    References:
        //       https://stackoverflow.com/questions/1560492/how-to-tell-whether-a-point-is-to-the-right-or-left-side-of-a-line
        //       https://guzintamath.com/textsavvy/2018/05/15/the-determinant-briefly/
        //
        //    As a simplification, I do not worry about the point being collinear to the vector; I
        //       just pick a side.
        if (GeomUtil.det(end.minus(start), point.minus(start)) > 1)
            return RelativeLocation.LEFT_OF_SHAPE;
        return RelativeLocation.RIGHT_OF_SHAPE;
    }

    @Override
    public Vector2dDual<Arclength> getClosestPoint(Vector2d point) {
        // Use dot product to find the projection of a vector onto the line.
        //    Reference: https://forum.unity.com/threads/how-do-i-find-the-closest-point-on-a-line.340058/
        //       See message with code from "lordofduct".
        Vector2d vectorToPoint = point.minus(start);
        double distToClosestPoint = vectorToPoint.dot(lineNormalized);
        distToClosestPoint = Math.clamp(distToClosestPoint, 0.0, length);

        Vector2d closestPoint = start.plus(lineNormalized.times(distToClosestPoint));

        return GeomUtil.createPointTangent(closestPoint, lineNormalized);
    }

    @Override
    public Vector2dDual<Arclength> getPointIntersectedBy(Pose2d pose) {
        // Setup here is as follows:
        //    - Line 1 goes through point P with direction R
        //    - Line 2 goes through point Q with direction S
        //
        //    The intersection of the two lines is X.
        //
        //    Reference: https://stackoverflow.com/questions/58541430/find-intersection-point-of-two-vectors-independent-from-direction

        Vector2d R = lineNormalized;
        Vector2d S = GeomUtil.normalize(pose.heading.vec());
        Vector2d Q = pose.position;
        Vector2d P = start;

        Vector2d QP = Q.minus(P);
        Vector2d SNV = new Vector2d(S.y, -S.x);

        double t = QP.dot(SNV) / R.dot(SNV);
        if (t > length)
            return null;

        Vector2d X = P.plus(R.times(t));
        return GeomUtil.createPointTangent(X, lineNormalized);
    }

    @Override
    public Rotation2d getClosestHeading(Pose2d pose) {
        Vector2d poseToStart = start.minus(pose.position);
        Vector2d poseToEnd = end.minus(pose.position);

        double angleToStart = GeomUtil.angleToTurn(pose.heading.vec(), poseToStart);
        double angleToEnd = GeomUtil.angleToTurn(pose.heading.vec(), poseToEnd);
        if (angleToStart < angleToEnd)
            return Rotation2d.exp(angleToStart);
        return Rotation2d.exp(angleToEnd);
    }
}

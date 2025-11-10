package edu.ftcphoenix.fw2.util;

import com.acmerobotics.roadrunner.Arclength;
import com.acmerobotics.roadrunner.DualNum;
import com.acmerobotics.roadrunner.Vector2d;
import com.acmerobotics.roadrunner.Vector2dDual;

import edu.ftcphoenix.robots.phoenix2.Robot;

public final class GeomUtil {
    private GeomUtil() {}


    /**
     * Find the determinant of two vectors.
     *
     * @param v1 First vector
     * @param v2 Second vector
     * @return The determinant of the vectors.
     */
    public static double det(Vector2d v1, Vector2d v2) {
        return v1.x * v2.y - v2.x * v1.y;
    }

    /**
     * Create a normalized or unit vector.
     *
     * @param vector The vector to transform.
     * @return The unit vector.
     */
    public static Vector2d normalize(Vector2d vector) {
        return vector.div(vector.norm());
    }

    /**
     * Create a dual vector with the specified point and the tangent.
     *
     * @param point   The point to include.
     * @param tangent The tangent at the point.
     * @return Dual vector encompassing the point and the tangent.
     */
    public static Vector2dDual<Arclength> createPointTangent(Vector2d point, Vector2d tangent) {
        return new Vector2dDual<>(
                new DualNum<>(new double[]
                        {
                                point.x,
                                tangent.x,
                        }),
                new DualNum<>(new double[]
                        {
                                point.y,
                                tangent.y
                        })
        );
    }

    /**
     * Return the angle between two vectors in radians.  The angle will always be between 0 and PI.
     *
     * @param v1 The first vector
     * @param v2 The second vector
     * @return Angle between vectors in radians.
     */
    public static double angleBetween(Vector2d v1, Vector2d v2) {
        // Reference: https://www.geeksforgeeks.org/angle-between-two-vectors-formula/#
        return Math.acos(v1.dot(v2) / (v1.norm() * v2.norm()));
    }

    /**
     * Get the angle to move from the source to the destination angle.  The angle will be between -PI to PI.
     *
     * @param angleSource The starting angle
     * @param angleDest   The ending angle.
     * @return Angle to move in radians.
     */
    public static double angleToTurn(Vector2d angleSource, Vector2d angleDest) {
        // Reference: https://stackoverflow.com/questions/14066933/direct-way-of-computing-the-clockwise-angle-between-two-vectors

        // Note: I contemplated creating a Rotation2d object by passing the numerator and denominator to save the
        //    computation of atan2 needlessly.  However, I am not guaranteed that this forms a unit vector, which
        //    the Rotation2d object requires.  So I am just computing the angle in Radians.  It can be converted to
        //    Rotate2d later if needed.
        return Math.atan2(det(angleSource, angleDest), angleSource.dot(angleDest));
    }

    /**
     * Point a vector towards a specific point.  Only the direction of the vector is changed.
     *
     * @param vecStartPoint   The starting point of the vector.
     * @param vecToRedirect   The vector that begins at the starting point showing the direction and magnitude.
     * @param vecPointTowards The point towards which the vector has to point.
     * @return The modified version of vector {@code vecToRedirect}.
     */
    public static Vector2d pointTowards(Vector2d vecStartPoint, Vector2d vecToRedirect, Vector2d vecPointTowards) {

        // Project the vector to the destination point onto the vector to redirect.  Then we can figure out whether the
        //    vector is going towards the target or not.
        Vector2d vecStartToPointTowards = vecPointTowards.minus(vecStartPoint);

        // ...NOTE: The full magnitude is not computed by dividing by the square of the magnitude.  We only need the
        //       sign of the magnitude; so computing the numerator (or the dot project) is sufficient.
        double signMagnitude = vecStartToPointTowards.dot(vecToRedirect);
        if (signMagnitude < 0)
            return vecToRedirect.times(-1);

        return vecToRedirect;
    }

    /**
     * Get the tangent from a dual vector.  The direction of the tangent is arbitrary.
     *
     * @param vecd The dual vector to get the tangent from.  The first item is the position and the second item is the
     *             tangent.
     * @return The tangent.
     */
    public static Vector2d getTangent(Vector2dDual<Arclength> vecd) {
        return (new Vector2d(vecd.x.get(1), vecd.y.get(1)));
    }

    /**
     * Get a vector tangent to the specified dual vector.  The resulting tangent vector will point
     * towards a target point.
     *
     * @param vecdBase   The baseline point & tangent for which we want a tangent vector.
     * @param vecTowards The tangent vector will be pointing towards this point.
     * @return The normal vector.
     */
    public static Vector2d getTangentTowards(Vector2dDual<Arclength> vecdBase, Vector2d vecTowards) {
        // Reference: https://math.libretexts.org/Bookshelves/Applied_Mathematics/Mathematics_for_Game_Developers_(Burzynski)/02%3A_Vectors_In_Two_Dimensions/2.05%3A_Parallel_and_Perpendicular_Vectors_The_Unit_Vector

        // Get the tangent at the point, but it is possible that it is pointing in the wrong direction.
        Vector2d vecBaseTangent = getTangent(vecdBase);

        // Make the base tangent vector point towards the target point.
        return pointTowards(vecdBase.value(), vecBaseTangent, vecTowards);
    }

    /**
     * Get a vector normal to a given vector.  Be aware that the magnitude is arbitrary, and the direction
     * of the normal vector is also arbitrary.
     *
     * @param vec The vector for which to find a normal vector.
     * @return The normal vector.
     */
    public static Vector2d getNormal(Vector2d vec) {
        // Reference: https://math.libretexts.org/Bookshelves/Applied_Mathematics/Mathematics_for_Game_Developers_(Burzynski)/02%3A_Vectors_In_Two_Dimensions/2.05%3A_Parallel_and_Perpendicular_Vectors_The_Unit_Vector
        return new Vector2d(-vec.y, vec.x);
    }

    /**
     * Get a vector normal to the specified dual vector.  The resulting normal vector will point
     * towards a target point.
     *
     * @param vecdBase   The baseline point & tangent for which we want a normal vector.
     * @param vecTowards The normal vector will be pointing towards this point.
     * @return The normal vector.
     */
    public static Vector2d getNormalTowards(Vector2dDual<Arclength> vecdBase, Vector2d vecTowards) {
        // Reference: https://math.libretexts.org/Bookshelves/Applied_Mathematics/Mathematics_for_Game_Developers_(Burzynski)/02%3A_Vectors_In_Two_Dimensions/2.05%3A_Parallel_and_Perpendicular_Vectors_The_Unit_Vector

        Vector2d vecBaseTangent = getTangent(vecdBase);
        Robot.g_Robot.getTelemetry().addData("GNT tan", vecBaseTangent);

        // Get a normal vector of the base.  It is possible that this vector is not facing the right way.
        Vector2d vecNormal = getNormal(vecBaseTangent);
        Robot.g_Robot.getTelemetry().addData("GNT norm", vecNormal);

        // Make sure the normal vector is pointing towards the target point.
        return pointTowards(vecdBase.value(), vecNormal, vecTowards);
    }

    /**
     * Project a vector onto another vector and get the magnitude factor of the projection.
     * <p>
     * If the vector of the projection is needed, just multiply the magnitude factor with vecProjectOnto.
     *
     * @param vecToProject   The vector to project
     * @param vecProjectOnto The vector onto which the projection has to happen.
     * @return The magnitude factor of the projected vector.
     */
    public static double getMagnitudeFactorOfProjection(Vector2d vecToProject, Vector2d vecProjectOnto) {
        return vecToProject.dot(vecProjectOnto) / vecProjectOnto.sqrNorm();
    }
}

package edu.ftcphoenix.fw.core.geometry;

import edu.ftcphoenix.fw.core.math.MathUtil;

/**
 * Immutable 2D pose in a right-handed, robot/field-friendly coordinate system.
 *
 * <p>A {@code Pose2d} represents a position on the floor plane plus a heading
 * (orientation) about the vertical axis. It is used throughout the framework
 * to describe the pose of the robot, AprilTags in the field layout, and other
 * objects constrained to the floor plane.</p>
 *
 * <h2>Coordinate conventions</h2>
 *
 * <ul>
 *   <li><strong>Units:</strong> distances are in inches, angles are in radians.</li>
 *   <li><strong>Axes:</strong>
 *     <ul>
 *       <li>{@code xInches}: +X forward (toward some reference side of the field).</li>
 *       <li>{@code yInches}: +Y to the left when facing +X.</li>
 *     </ul>
 *   </li>
 *   <li><strong>Heading:</strong>
 *     <ul>
 *       <li>{@code headingRad} is measured CCW from +X.</li>
 *       <li>{@code headingRad = 0} -> "facing +X".</li>
 *       <li>{@code headingRad = +pi/2} -> "facing +Y" (left).</li>
 *       <li>{@code headingRad = -pi/2} -> "facing -Y" (right).</li>
 *     </ul>
 *   </li>
 * </ul>
 *
 * <p>This matches the conventions used in {@link edu.ftcphoenix.fw.drive.DriveSignal}
 * and {@code TagLayout}: forward is +X, left is +Y, and heading increases
 * counter-clockwise.</p>
 */
public final class Pose2d {

    /**
     * X position on the floor plane, in inches (forward).
     */
    public final double xInches;

    /**
     * Y position on the floor plane, in inches (left).
     */
    public final double yInches;

    /**
     * Heading about the vertical axis, in radians, measured CCW from +X.
     *
     * <p>Callers may provide any real value; no normalization is performed in
     * the constructor. Use {@link #wrapToPi(double)} if you need a canonical
     * angle in the range [-pi, +pi].</p>
     */
    public final double headingRad;

    /**
     * Constructs a new immutable 2D pose.
     *
     * @param xInches    X position on the floor plane, in inches (forward)
     * @param yInches    Y position on the floor plane, in inches (left)
     * @param headingRad heading about the vertical axis, in radians
     */
    public Pose2d(double xInches, double yInches, double headingRad) {
        this.xInches = xInches;
        this.yInches = yInches;
        this.headingRad = headingRad;
    }

    /**
     * Returns a new {@code Pose2d} with the same x/y but heading wrapped
     * into the range [-pi, +pi].
     *
     * @return a new {@code Pose2d} whose heading is normalized to [-pi, +pi]
     */
    public Pose2d normalizedHeading() {
        return new Pose2d(xInches, yInches, wrapToPi(headingRad));
    }

    /**
     * Compute the Euclidean distance in the floor plane to another pose.
     *
     * <p>This ignores heading and only considers (x, y):</p>
     *
     * <pre>
     * sqrt((other.xInches - xInches)^2 + (other.yInches - yInches)^2)
     * </pre>
     *
     * @param other another pose in the same field frame
     * @return straight-line distance between this pose and {@code other}, in inches
     */
    public double distanceTo(Pose2d other) {
        double dx = other.xInches - this.xInches;
        double dy = other.yInches - this.yInches;
        return Math.hypot(dx, dy);
    }

    /**
     * Compute the smallest signed heading error from this pose's heading
     * to another pose's heading.
     *
     * <p>The result is normalized to the range [-pi, +pi] and represents the
     * rotation needed to go from {@code this.headingRad} to
     * {@code other.headingRad}:</p>
     *
     * <pre>
     * headingErrorTo(other) = wrapToPi(other.headingRad - this.headingRad)
     * </pre>
     *
     * <p>Positive values indicate that {@code other} is rotated CCW (to the
     * left) relative to this pose; negative values indicate CW.</p>
     *
     * @param other another pose in the same frame
     * @return heading error (other - this), wrapped to [-pi, +pi]
     */
    public double headingErrorTo(Pose2d other) {
        return wrapToPi(other.headingRad - this.headingRad);
    }

    /**
     * Normalize an angle in radians into the range [-pi, +pi].
     *
     * <p>This method exists for convenience in pose/math code, but the canonical
     * implementation lives in {@link MathUtil#wrapToPi(double)} so all framework
     * code shares identical behavior.</p>
     *
     * @param angleRad angle in radians (any real value)
     * @return equivalent angle in radians, wrapped to [-pi, +pi]
     */
    public static double wrapToPi(double angleRad) {
        return MathUtil.wrapToPi(angleRad);
    }

    /** {@inheritDoc} */
    @Override
    public String toString() {
        return "Pose2d{" +
                "xInches=" + xInches +
                ", yInches=" + yInches +
                ", headingRad=" + headingRad +
                '}';
    }
}

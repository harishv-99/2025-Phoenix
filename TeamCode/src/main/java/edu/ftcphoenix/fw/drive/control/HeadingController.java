package edu.ftcphoenix.fw.drive.control;

import edu.ftcphoenix.fw.geom.Pose2d;
import edu.ftcphoenix.fw.util.MathUtil;

/**
 * Simple heading (yaw) controller used by go-to-pose and tag-aim behaviors.
 *
 * <p>This controller converts a desired heading and current heading into an
 * angular velocity command, optionally adding a feedforward term.</p>
 *
 * <h2>Coordinate conventions</h2>
 *
 * <ul>
 *   <li>Headings are in radians, measured CCW-positive, consistent with {@link Pose2d#headingRad}.</li>
 *   <li>The output {@code omega} is also CCW-positive, consistent with {@link edu.ftcphoenix.fw.drive.DriveSignal#omega}.</li>
 *   <li>Heading error is computed as {@code wrapToPi(desired - current)}.</li>
 * </ul>
 *
 * <p>
 * If you are using a drivebase that expects a different sign convention for omega,
 * convert at the boundary (in the drivebase or adapter), not inside this controller.
 * </p>
 */
public final class HeadingController {

    /**
     * Configuration parameters for {@link HeadingController}.
     */
    public static final class Config {

        /**
         * Proportional gain applied to heading error (radians).
         *
         * <p>Command is:</p>
         * <pre>{@code omegaCmd = kP * headingError + omegaFF}</pre>
         */
        public double kP = 3.0;

        /**
         * Maximum magnitude of omega output (radians/sec).
         *
         * <p>Omega output is clamped to [-maxOmegaRadPerSec, +maxOmegaRadPerSec].</p>
         */
        public double maxOmegaRadPerSec = Math.toRadians(180.0);

        /**
         * Creates a new config with default values.
         */
        public Config() {
        }
    }

    private final double kP;
    private final double maxOmegaRadPerSec;

    /**
     * Construct a new {@link HeadingController}.
     *
     * @param cfg configuration (non-null)
     */
    public HeadingController(Config cfg) {
        if (cfg == null) {
            throw new IllegalArgumentException("cfg is required");
        }
        this.kP = cfg.kP;
        this.maxOmegaRadPerSec = Math.abs(cfg.maxOmegaRadPerSec);
    }

    /**
     * Compute an omega command to move from the current heading toward the desired heading.
     *
     * @param desiredHeadingRad         desired heading in radians (CCW-positive)
     * @param currentHeadingRad         current heading in radians (CCW-positive)
     * @param omegaFeedforwardRadPerSec feedforward omega in radians/sec (CCW-positive).
     *                                  Use 0.0 if not needed.
     * @return omega command in radians/sec (CCW-positive), clamped to config max
     */
    public double update(double desiredHeadingRad,
                         double currentHeadingRad,
                         double omegaFeedforwardRadPerSec) {

        // Wrap error to [-pi, +pi] so we always take the shortest direction.
        double errorRad = Pose2d.wrapToPi(desiredHeadingRad - currentHeadingRad);

        double omegaCmd = kP * errorRad + omegaFeedforwardRadPerSec;

        if (maxOmegaRadPerSec > 0.0) {
            omegaCmd = MathUtil.clamp(omegaCmd, -maxOmegaRadPerSec, +maxOmegaRadPerSec);
        }

        return omegaCmd;
    }

    /**
     * Convenience overload with zero feedforward.
     */
    public double update(double desiredHeadingRad, double currentHeadingRad) {
        return update(desiredHeadingRad, currentHeadingRad, 0.0);
    }
}

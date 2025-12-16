package edu.ftcphoenix.fw.drive;

import edu.ftcphoenix.fw.util.MathUtil;

/**
 * Robot-centric chassis velocity command.
 *
 * <p>This is the "real units" companion to {@link DriveSignal}:
 * {@code DriveSignal} is a normalized, unitless command typically in [-1, +1],
 * while {@code ChassisSpeeds} expresses intent in physical units.</p>
 *
 * <h2>Frame conventions</h2>
 * <ul>
 *   <li>All components are <b>robot-centric</b>.</li>
 *   <li>{@code vxRobotIps > 0} drives forward (+X).</li>
 *   <li>{@code vyRobotIps > 0} strafes left (+Y).</li>
 *   <li>{@code omegaRobotRadPerSec > 0} rotates CCW (turn left, viewed from above).</li>
 * </ul>
 */
public final class ChassisSpeeds {

    /** Forward velocity in the robot frame, in inches/sec (+ forward). */
    public final double vxRobotIps;

    /** Leftward velocity in the robot frame, in inches/sec (+ left). */
    public final double vyRobotIps;

    /** Angular velocity about +Z, in rad/sec (+ CCW). */
    public final double omegaRobotRadPerSec;

    private static final ChassisSpeeds ZERO = new ChassisSpeeds(0.0, 0.0, 0.0);

    /** @return a zero-velocity command. */
    public static ChassisSpeeds zero() {
        return ZERO;
    }

    public ChassisSpeeds(double vxRobotIps, double vyRobotIps, double omegaRobotRadPerSec) {
        this.vxRobotIps = vxRobotIps;
        this.vyRobotIps = vyRobotIps;
        this.omegaRobotRadPerSec = omegaRobotRadPerSec;
    }

    /**
     * @return a new command with translation components ({@code vxRobotIps}, {@code vyRobotIps})
     * scaled by {@code translationScale}, and {@code omegaRobotRadPerSec} scaled by {@code omegaScale}.
     */
    public ChassisSpeeds scaled(double translationScale, double omegaScale) {
        return new ChassisSpeeds(
                vxRobotIps * translationScale,
                vyRobotIps * translationScale,
                omegaRobotRadPerSec * omegaScale
        );
    }

    /**
     * Clamp each component independently.
     */
    public ChassisSpeeds clamped(double maxVxAbsIps, double maxVyAbsIps, double maxOmegaAbsRadPerSec) {
        return new ChassisSpeeds(
                MathUtil.clamp(vxRobotIps, -Math.abs(maxVxAbsIps), Math.abs(maxVxAbsIps)),
                MathUtil.clamp(vyRobotIps, -Math.abs(maxVyAbsIps), Math.abs(maxVyAbsIps)),
                MathUtil.clamp(omegaRobotRadPerSec, -Math.abs(maxOmegaAbsRadPerSec), Math.abs(maxOmegaAbsRadPerSec))
        );
    }
}

package edu.ftcphoenix.fw.drive;

/**
 * Robot-centric drive command for holonomic drivetrains.
 *
 * <p>Axes:</p>
 * <ul>
 *   <li>axial    = forward (+) / backward (-)</li>
 *   <li>lateral  = strafe right (+) / left (-)</li>
 *   <li>omega    = yaw CCW (+) / CW (-), normalized (not rad/s)</li>
 * </ul>
 *
 * <p>Values are typically in [-1, +1]. Shaping and deadbanding should happen in the source.</p>
 */
public final class DriveSignal {
    public final double axial;
    public final double lateral;
    public final double omega;

    public DriveSignal(double axial, double lateral, double omega) {
        this.axial = axial;
        this.lateral = lateral;
        this.omega = omega;
    }

    /** Clamp each component into [-1,1]. */
    public DriveSignal clamped() {
        return new DriveSignal(clamp(axial), clamp(lateral), clamp(omega));
    }

    private static double clamp(double v) { return v < -1 ? -1 : (v > 1 ? 1 : v); }

    @Override public String toString() {
        return String.format("DriveSignal{ax=%.3f, lat=%.3f, om=%.3f}", axial, lateral, omega);
    }
}

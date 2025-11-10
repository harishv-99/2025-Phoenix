package edu.ftcphoenix.fw2.drive.math;

import edu.ftcphoenix.fw2.drive.DriveSignal;
import edu.ftcphoenix.fw2.util.MathUtil;

/**
 * MecanumKinematics — pure math helpers for mecanum mixing.
 *
 * <p><b>Conventions (robot-centric):</b>
 * <ul>
 *   <li>axial   &gt; 0  → forward (+Y)</li>
 *   <li>lateral &gt; 0  → left (+X)</li>
 *   <li>omega   &gt; 0  → counter-clockwise (+Z rotation)</li>
 * </ul>
 *
 * <p><b>Design goals:</b>
 * <ul>
 *   <li>Pure, side-effect free functions (no hardware access).</li>
 *   <li>"Sum &amp; normalize": preserves the <i>ratios</i> of wheel powers if any exceeds |1| (proportional rescale, not clamp).</li>
 *   <li>NaN/Inf guarding via {@link MathUtil#coerceFinite(double, double)} so garbage never reaches motor commands.</li>
 * </ul>
 *
 * <p><b>Typical usage:</b></p>
 * <pre>
 *   double[] w = MecanumKinematics.chassisToWheels(cmd.axial(), cmd.lateral(), cmd.omega());
 *   io.setWheelPowers(w[0], w[1], w[2], w[3]);
 * </pre>
 *
 * <p>If you prefer an imperative one-liner that writes to a {@code DriveIO}, use the optional
 * facade {@code MecanumDrive} instead.</p>
 */
public final class MecanumKinematics {
    private MecanumKinematics() {
    }

    // ---------------------------------------------------------------------
    // Forward kinematics: chassis (axial, lateral, omega) → wheel powers
    // ---------------------------------------------------------------------

    /**
     * Convert chassis-space command to mecanum wheel powers using ratio-preserving normalization.
     *
     * @param axial   forward command (+forward).
     * @param lateral strafe command (+left).
     * @param omega   rotational command (+CCW).
     * @return wheel powers {@code [fl, fr, bl, br]} normalized into [-1, 1] while preserving ratios.
     */
    public static double[] chassisToWheels(double axial, double lateral, double omega) {
        // Sanitize inputs
        double y = MathUtil.coerceFinite(axial, 0.0);
        double x = MathUtil.coerceFinite(lateral, 0.0);
        double w = MathUtil.coerceFinite(omega, 0.0);

        // Standard mecanum mix with our sign convention
        double fl = y + x + w;
        double fr = y - x - w;
        double bl = y - x + w;
        double br = y + x - w;

        return normalize(fl, fr, bl, br);
    }

    /**
     * Overload that accepts a {@link DriveSignal}.
     *
     * @param s drive signal (lateral, axial, omega).
     * @return wheel powers {@code [fl, fr, bl, br]} normalized into [-1, 1].
     */
    public static double[] chassisToWheels(DriveSignal s) {
        return chassisToWheels(s.axial(), s.lateral(), s.omega());
    }

    // ---------------------------------------------------------------------
    // Inverse kinematics: wheel powers → chassis (axial, lateral, omega)
    // Handy for telemetry/tests; assumes symmetric geometry.
    // ---------------------------------------------------------------------

    /**
     * Convert wheel powers back to a chassis-space command.
     *
     * <p>Solves the linear system for the standard mix:</p>
     * <pre>
     *   y = (fl + fr + bl + br)/4
     *   x = (fl - fr - bl + br)/4
     *   w = (fl - fr + bl - br)/4
     * </pre>
     *
     * @param fl front-left wheel power.
     * @param fr front-right wheel power.
     * @param bl back-left wheel power.
     * @param br back-right wheel power.
     * @return a {@link DriveSignal} with components (lateral, axial, omega).
     */
    public static DriveSignal wheelsToChassis(double fl, double fr, double bl, double br) {
        // Sanitize inputs
        fl = MathUtil.coerceFinite(fl, 0.0);
        fr = MathUtil.coerceFinite(fr, 0.0);
        bl = MathUtil.coerceFinite(bl, 0.0);
        br = MathUtil.coerceFinite(br, 0.0);

        double y = (fl + fr + bl + br) * 0.25;
        double x = (fl - fr - bl + br) * 0.25;
        double w = (fl - fr + bl - br) * 0.25;

        return new DriveSignal(x, y, w);
    }

    // ---------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------

    /**
     * Ratio-preserving normalization into [-1, 1] if any wheel exceeds magnitude 1.
     *
     * @param fl front-left raw power.
     * @param fr front-right raw power.
     * @param bl back-left raw power.
     * @param br back-right raw power.
     * @return normalized powers preserving the original ratios.
     */
    private static double[] normalize(double fl, double fr, double bl, double br) {
        // Coerce to finite before measuring magnitudes
        fl = MathUtil.coerceFinite(fl, 0.0);
        fr = MathUtil.coerceFinite(fr, 0.0);
        bl = MathUtil.coerceFinite(bl, 0.0);
        br = MathUtil.coerceFinite(br, 0.0);

        double maxAbs = Math.max(
                Math.max(Math.abs(fl), Math.abs(fr)),
                Math.max(Math.abs(bl), Math.abs(br))
        );
        double s = (maxAbs > 1.0) ? (1.0 / maxAbs) : 1.0;

        return new double[]{fl * s, fr * s, bl * s, br * s};
    }
}

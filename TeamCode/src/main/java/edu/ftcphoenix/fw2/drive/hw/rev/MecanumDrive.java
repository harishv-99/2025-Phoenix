package edu.ftcphoenix.fw2.drive.hw.rev;

import edu.ftcphoenix.fw2.drive.DriveSignal;
import edu.ftcphoenix.fw2.drive.hw.DriveIO;
import edu.ftcphoenix.fw2.drive.math.MecanumKinematics;

/**
 * MecanumDrive — tiny facade over {@link DriveIO}.
 *
 * <p><b>Responsibilities:</b>
 * <ul>
 *   <li>Convert a robot-centric {@link DriveSignal} to wheel powers via {@link MecanumKinematics}.</li>
 *   <li>Write those powers to the provided {@link DriveIO}.</li>
 * </ul>
 *
 * <p><b>Non-responsibilities:</b>
 * <ul>
 *   <li>Hardware creation/config (use {@link MecanumIO} via its Builder).</li>
 *   <li>Input shaping or field-centric (keep in DriveGraph + AxisChains).</li>
 * </ul>
 *
 * <p><b>Why keep this class (optional):</b>
 * <ul>
 *   <li>Provides a seam for taps (logging), safety guards (brownout/current), or wheel-level rate limits later.</li>
 *   <li>If you do not need such a seam, call
 *       {@code MecanumKinematics.chassisToWheels(...)} + {@code io.setWheelPowers(...)} directly.</li>
 * </ul>
 */
public final class MecanumDrive {

    /**
     * OutputTap — observe or adjust outgoing wheel powers before they hit hardware.
     *
     * <p>Return the (possibly adjusted) powers to be written.</p>
     */
    public interface OutputTap {
        /**
         * Called before writing to {@link DriveIO}.
         *
         * @param fl front-left power.
         * @param fr front-right power.
         * @param bl back-left power.
         * @param br back-right power.
         * @return possibly-adjusted powers {@code [fl, fr, bl, br]}.
         */
        double[] onWheels(double fl, double fr, double bl, double br);
    }

    private final DriveIO io;
    private OutputTap tap; // nullable

    /**
     * Create a facade bound to a drive I/O implementation.
     *
     * @param io the {@link DriveIO} to receive wheel powers.
     */
    public MecanumDrive(DriveIO io) {
        this.io = io;
    }

    /**
     * Install an optional tap (telemetry, safety limits, etc.).
     *
     * @param t tap to run on outgoing powers; may be {@code null}.
     * @return this for chaining.
     */
    public MecanumDrive withTap(OutputTap t) {
        this.tap = t;
        return this;
    }

    /**
     * Compute wheel powers from a {@link DriveSignal} and write to {@link DriveIO}.
     *
     * <p>Uses ratio-preserving normalization inside {@link MecanumKinematics}.</p>
     *
     * @param s drive signal (lateral, axial, omega).
     */
    public void drive(DriveSignal s) {
        double[] w = MecanumKinematics.chassisToWheels(s);
        if (tap != null) {
            w = tap.onWheels(w[0], w[1], w[2], w[3]);
        }
        io.setWheelPowers(w[0], w[1], w[2], w[3]);
    }

    /**
     * Convenience stop (sets all wheel powers to zero).
     */
    public void stop() {
        io.stop();
    }
}

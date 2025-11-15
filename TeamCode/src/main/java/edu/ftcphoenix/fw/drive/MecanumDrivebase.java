package edu.ftcphoenix.fw.drive;

import edu.ftcphoenix.fw.hal.MotorOutput;
import edu.ftcphoenix.fw.util.LoopClock;
import edu.ftcphoenix.fw.util.MathUtil;

/**
 * Simple open-loop mecanum mixer.
 *
 * <p>Maps a high-level {@link DriveSignal} to four wheel power commands:
 * <pre>
 * fl = axial + lateral + omega
 * fr = axial - lateral - omega
 * bl = axial - lateral + omega
 * br = axial + lateral - omega
 * </pre>
 *
 * <p>Each wheel power is then clamped to [-1, +1] before being applied to
 * the underlying motors.</p>
 *
 * <p>The {@link MecanumConfig} allows optional scaling of the three drive
 * components (axial, lateral, omega) before mixing.</p>
 */
public final class MecanumDrivebase {

    private final MotorOutput fl, fr, bl, br;
    private final MecanumConfig cfg;

    private double lastFlPower = 0.0;
    private double lastFrPower = 0.0;
    private double lastBlPower = 0.0;
    private double lastBrPower = 0.0;

    /**
     * Create a mecanum drivebase from four motors and a config.
     *
     * @param fl  front-left motor
     * @param fr  front-right motor
     * @param bl  back-left motor
     * @param br  back-right motor
     * @param cfg configuration (may be null to use {@link MecanumConfig#defaults()})
     */
    public MecanumDrivebase(MotorOutput fl,
                            MotorOutput fr,
                            MotorOutput bl,
                            MotorOutput br,
                            MecanumConfig cfg) {
        if (fl == null || fr == null || bl == null || br == null) {
            throw new IllegalArgumentException("All four motors (fl, fr, bl, br) are required");
        }
        this.fl = fl;
        this.fr = fr;
        this.bl = bl;
        this.br = br;
        this.cfg = (cfg != null) ? cfg : MecanumConfig.defaults();
    }

    /**
     * Apply a drive signal to the motors.
     *
     * <p>Expected input range is [-1, +1] for each component, but values
     * outside that range will be clamped per wheel after mixing.</p>
     *
     * @param s drive signal to apply; {@code null} is treated as "stop"
     */
    public void drive(DriveSignal s) {
        if (s == null) {
            // Treat null as a stop command for robustness.
            stop();
            return;
        }

        // Apply per-axis scaling from the config.
        double axial = s.axial * cfg.maxAxial;
        double lateral = s.lateral * cfg.maxLateral;
        double omega = s.omega * cfg.maxOmega;

        // Basic mecanum mixing with the scaled components.
        double flP = axial + lateral + omega;
        double frP = axial - lateral - omega;
        double blP = axial - lateral + omega;
        double brP = axial + lateral - omega;

        // Clamp and apply, while tracking last commanded values.
        lastFlPower = MathUtil.clamp(flP, -1.0, 1.0);
        lastFrPower = MathUtil.clamp(frP, -1.0, 1.0);
        lastBlPower = MathUtil.clamp(blP, -1.0, 1.0);
        lastBrPower = MathUtil.clamp(brP, -1.0, 1.0);

        fl.setPower(lastFlPower);
        fr.setPower(lastFrPower);
        bl.setPower(lastBlPower);
        br.setPower(lastBrPower);
    }

    /**
     * Optional update hook; currently no-op but kept for symmetry with other
     * subsystems that may need time-based updates.
     */
    public void update(LoopClock clock) {
        // No-op for now; placeholder if we add odometry or other logic later.
    }

    /**
     * Immediately stop all four drive motors and reset last commanded powers.
     */
    public void stop() {
        lastFlPower = 0.0;
        lastFrPower = 0.0;
        lastBlPower = 0.0;
        lastBrPower = 0.0;

        fl.setPower(0.0);
        fr.setPower(0.0);
        bl.setPower(0.0);
        br.setPower(0.0);
    }

    // ------------------------------------------------------------------------
    // Telemetry helpers
    // ------------------------------------------------------------------------

    /**
     * @return last commanded (clamped) power for front-left motor.
     */
    public double getLastFlPower() {
        return lastFlPower;
    }

    /**
     * @return last commanded (clamped) power for front-right motor.
     */
    public double getLastFrPower() {
        return lastFrPower;
    }

    /**
     * @return last commanded (clamped) power for back-left motor.
     */
    public double getLastBlPower() {
        return lastBlPower;
    }

    /**
     * @return last commanded (clamped) power for back-right motor.
     */
    public double getLastBrPower() {
        return lastBrPower;
    }
}

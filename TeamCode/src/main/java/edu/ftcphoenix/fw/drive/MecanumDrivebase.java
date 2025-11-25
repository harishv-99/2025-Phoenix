package edu.ftcphoenix.fw.drive;

import edu.ftcphoenix.fw.debug.DebugSink;
import edu.ftcphoenix.fw.hal.MotorOutput;
import edu.ftcphoenix.fw.util.LoopClock;
import edu.ftcphoenix.fw.util.MathUtil;

/**
 * Simple open-loop mecanum mixer.
 *
 * <p>Maps a high-level {@link DriveSignal} to four wheel power commands:</p>
 *
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
 * <p>
 * The {@link MecanumConfig} allows optional scaling of the three drive
 * components (axial, lateral, omega) <em>and</em> optional per-axis rate
 * limiting of how quickly those commands may change over time.
 * </p>
 *
 * <h2>Usage</h2>
 *
 * <p>A typical wiring in a TeleOp might look like:</p>
 *
 * <pre>{@code
 * // 1) Hardware: create motor outputs (platform-specific wrapper).
 * MotorOutput fl = FtcHardware.motor(hw, "fl");
 * MotorOutput fr = FtcHardware.motor(hw, "fr");
 * MotorOutput bl = FtcHardware.motor(hw, "bl");
 * MotorOutput br = FtcHardware.motor(hw, "br");
 *
 * // 2) Optional: tune drive behavior via config.
 * MecanumConfig cfg = MecanumConfig.defaults();
 * cfg.maxLateralRatePerSec = 4.0; // smooth strafing (optional)
 *
 * // 3) Create the drivebase.
 * MecanumDrivebase drivebase = new MecanumDrivebase(fl, fr, bl, br, cfg);
 *
 * // 4) In your OpMode loop:
 * clock.update(getRuntime());
 * DriveSignal cmd = driveSource.get(clock);
 * drivebase.drive(cmd);
 * drivebase.update(clock); // feeds dtSec for smoothing (optional but recommended)
 * }</pre>
 *
 * <p>
 * The ordering between {@link #drive(DriveSignal)} and {@link #update(LoopClock)}
 * is not critical. This class stores the most recent {@code dtSec} provided by
 * {@link #update(LoopClock)} and uses it on subsequent calls to
 * {@link #drive(DriveSignal)} when rate limits are enabled in the config.
 * </p>
 */
public final class MecanumDrivebase {

    private final MotorOutput fl, fr, bl, br;
    private final MecanumConfig cfg;

    // Last commanded wheel powers (after clamping).
    private double lastFlPower = 0.0;
    private double lastFrPower = 0.0;
    private double lastBlPower = 0.0;
    private double lastBrPower = 0.0;

    // Last commanded high-level components (after scaling & rate limiting).
    private double lastAxialCmd = 0.0;
    private double lastLateralCmd = 0.0;
    private double lastOmegaCmd = 0.0;

    // Most recent dtSec provided via update(clock).
    private double lastDtSec = 0.0;

    /**
     * Construct a mecanum drivebase from four motor outputs and an optional config.
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
        // Defensive copy so later cfg mutations don't affect this drivebase.
        this.cfg = ((cfg != null) ? cfg : MecanumConfig.defaults()).copy();
    }

    /**
     * Apply a drive signal to the motors.
     *
     * <p>
     * Expected input range is [-1, +1] for each component, but values outside
     * that range will be clamped per wheel after mixing.
     * </p>
     *
     * <p>
     * This method applies the following processing steps:
     * </p>
     *
     * <ol>
     *   <li>Apply per-axis scaling from {@link MecanumConfig}.</li>
     *   <li>Optionally apply per-axis rate limiting (also from {@link MecanumConfig}).</li>
     *   <li>Mix the (possibly rate-limited) components into wheel powers.</li>
     *   <li>Clamp each wheel power to [-1, +1] and send to the motors.</li>
     * </ol>
     *
     * @param s drive signal to apply; {@code null} is treated as "stop"
     */
    public void drive(DriveSignal s) {
        if (s == null) {
            // Treat null as a stop command for robustness.
            stop();
            return;
        }

        // 1) Apply per-axis scaling from the config.
        double desiredAxial = s.axial * cfg.maxAxial;
        double desiredLateral = s.lateral * cfg.maxLateral;
        double desiredOmega = s.omega * cfg.maxOmega;

        // 2) Optionally apply per-axis rate limiting based on lastDtSec.
        double dt = lastDtSec;
        double axialCmd = limitRate(desiredAxial, lastAxialCmd, cfg.maxAxialRatePerSec, dt);
        double lateralCmd = limitRate(desiredLateral, lastLateralCmd, cfg.maxLateralRatePerSec, dt);
        double omegaCmd = limitRate(desiredOmega, lastOmegaCmd, cfg.maxOmegaRatePerSec, dt);

        lastAxialCmd = axialCmd;
        lastLateralCmd = lateralCmd;
        lastOmegaCmd = omegaCmd;

        // 3) Basic mecanum mixing with the (possibly rate-limited) components.
        double flP = axialCmd + lateralCmd + omegaCmd;
        double frP = axialCmd - lateralCmd - omegaCmd;
        double blP = axialCmd - lateralCmd + omegaCmd;
        double brP = axialCmd + lateralCmd - omegaCmd;

        // 4) Clamp and apply, while tracking last commanded values.
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
     * Internal helper to limit the rate of change of a command.
     *
     * <p>
     * If {@code maxRatePerSec <= 0} or {@code dtSec <= 0}, the desired value
     * is returned unchanged. Otherwise, the delta between {@code desired} and
     * {@code last} is clamped to {@code +/- maxRatePerSec * dtSec}.
     * </p>
     *
     * @param desired       desired new command value
     * @param last          last commanded value
     * @param maxRatePerSec maximum allowed change per second (<= 0 means "no limit")
     * @param dtSec         time since last update (seconds)
     * @return rate-limited command value
     */
    private static double limitRate(double desired,
                                    double last,
                                    double maxRatePerSec,
                                    double dtSec) {
        if (maxRatePerSec <= 0.0 || dtSec <= 0.0) {
            return desired;
        }
        double maxDelta = maxRatePerSec * dtSec;
        double delta = desired - last;
        if (delta > maxDelta) {
            delta = maxDelta;
        } else if (delta < -maxDelta) {
            delta = -maxDelta;
        }
        return last + delta;
    }

    /**
     * Optional update hook; feeds the latest loop timing into this drivebase.
     *
     * <p>
     * This method stores the current {@code dtSec} from the provided
     * {@link LoopClock}. When rate limits are enabled in {@link MecanumConfig},
     * the most recent {@code dtSec} is used by subsequent calls to
     * {@link #drive(DriveSignal)}.
     * </p>
     *
     * <p>
     * If {@code clock} is {@code null}, this method does nothing and any
     * existing {@code dtSec} value is left unchanged.
     * </p>
     *
     * @param clock loop timing helper (may be {@code null})
     */
    public void update(LoopClock clock) {
        if (clock == null) {
            return;
        }
        lastDtSec = clock.dtSec();
    }

    /**
     * Immediately stop all four drive motors and reset last commanded powers.
     */
    public void stop() {
        lastFlPower = 0.0;
        lastFrPower = 0.0;
        lastBlPower = 0.0;
        lastBrPower = 0.0;

        lastAxialCmd = 0.0;
        lastLateralCmd = 0.0;
        lastOmegaCmd = 0.0;

        fl.setPower(0.0);
        fr.setPower(0.0);
        bl.setPower(0.0);
        br.setPower(0.0);
    }

    // ------------------------------------------------------------------------
    // Telemetry / debug helpers
    // ------------------------------------------------------------------------

    /**
     * Dump internal state to a {@link DebugSink}.
     *
     * <p>
     * This is intended for one-off debugging and tuning. Callers can choose
     * any prefix they like; nested callers often use dotted paths such as
     * {@code "drive.mecanum"}.
     * </p>
     *
     * <p>
     * This method is defensive: if {@code dbg} is {@code null}, it does
     * nothing. Framework classes consistently follow this pattern so callers
     * may freely pass a {@code NullDebugSink} or {@code null}.
     * </p>
     *
     * @param dbg    debug sink to write to (may be {@code null})
     * @param prefix key prefix for all entries (may be {@code null} or empty)
     */
    public void debugDump(DebugSink dbg, String prefix) {
        if (dbg == null) {
            return;
        }
        String p = (prefix == null || prefix.isEmpty()) ? "drive.mecanum" : prefix;

        dbg.addLine(p + ": MecanumDrivebase");

        // Last commanded powers.
        dbg.addData(p + ".power.fl", lastFlPower);
        dbg.addData(p + ".power.fr", lastFrPower);
        dbg.addData(p + ".power.bl", lastBlPower);
        dbg.addData(p + ".power.br", lastBrPower);

        // Last commanded high-level components.
        dbg.addData(p + ".cmd.axial", lastAxialCmd);
        dbg.addData(p + ".cmd.lateral", lastLateralCmd);
        dbg.addData(p + ".cmd.omega", lastOmegaCmd);

        // Config snapshot.
        dbg.addData(p + ".cfg.maxAxial", cfg.maxAxial);
        dbg.addData(p + ".cfg.maxLateral", cfg.maxLateral);
        dbg.addData(p + ".cfg.maxOmega", cfg.maxOmega);
        dbg.addData(p + ".cfg.maxAxialRatePerSec", cfg.maxAxialRatePerSec);
        dbg.addData(p + ".cfg.maxLateralRatePerSec", cfg.maxLateralRatePerSec);
        dbg.addData(p + ".cfg.maxOmegaRatePerSec", cfg.maxOmegaRatePerSec);

        // Timing info.
        dbg.addData(p + ".lastDtSec", lastDtSec);
    }

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

package edu.ftcphoenix.fw.drive;

import edu.ftcphoenix.fw.debug.DebugSink;
import edu.ftcphoenix.fw.hal.PowerOutput;
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
 * the underlying actuators via {@link PowerOutput}.</p>
 *
 * <p>
 * The {@link MecanumConfig} allows optional scaling of the three drive
 * components (axial, lateral, omega) <em>and</em> optional per-axis rate
 * limiting of how quickly those commands may change over time.
 * </p>
 *
 * <h2>Inversion policy</h2>
 *
 * <p>
 * All inversion is handled at the hardware level (e.g., using FTC SDK
 * {@code setDirection(REVERSE)} when constructing {@link PowerOutput}s).
 * This class assumes that positive power means "forward" for each wheel
 * in whatever coordinate system the hardware already uses.
 * </p>
 */
public final class MecanumDrivebase {

    private final PowerOutput fl, fr, bl, br;
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
     * Construct a mecanum drivebase from four outputs and an optional config.
     *
     * @param fl  front-left wheel power output
     * @param fr  front-right wheel power output
     * @param bl  back-left wheel power output
     * @param br  back-right wheel power output
     * @param cfg configuration (may be null to use {@link MecanumConfig#defaults()})
     */
    public MecanumDrivebase(PowerOutput fl,
                            PowerOutput fr,
                            PowerOutput bl,
                            PowerOutput br,
                            MecanumConfig cfg) {
        if (fl == null || fr == null || bl == null || br == null) {
            throw new IllegalArgumentException("All four PowerOutput channels are required");
        }
        this.fl = fl;
        this.fr = fr;
        this.bl = bl;
        this.br = br;
        this.cfg = (cfg != null) ? cfg : MecanumConfig.defaults();
    }

    /**
     * Construct a mecanum drivebase with the default {@link MecanumConfig}.
     */
    public MecanumDrivebase(PowerOutput fl,
                            PowerOutput fr,
                            PowerOutput bl,
                            PowerOutput br) {
        this(fl, fr, bl, br, null);
    }

    /**
     * Apply a drive signal to the wheels.
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
     *   <li>Clamp each wheel power to [-1, +1] and send to the actuators.</li>
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
        double desiredLateral = -s.lateral * cfg.maxLateral;
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
     * If {@code maxRatePerSec} is &lt;= 0 or {@code dtSec} is &lt;= 0, this
     * method returns {@code desired} unchanged.
     * </p>
     */
    private static double limitRate(double desired,
                                    double previous,
                                    double maxRatePerSec,
                                    double dtSec) {
        if (maxRatePerSec <= 0.0 || dtSec <= 0.0) {
            return desired;
        }
        double maxDelta = maxRatePerSec * dtSec;
        double delta = desired - previous;
        if (delta > maxDelta) {
            return previous + maxDelta;
        } else if (delta < -maxDelta) {
            return previous - maxDelta;
        }
        return desired;
    }

    /**
     * Update loop timing information used for rate limiting.
     *
     * <p>
     * Call this once per loop before calling {@link #drive(DriveSignal)} if
     * you want rate limiting to be based on the actual loop period.
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
     * Immediately stop all four drive outputs and reset last command
     * bookkeeping.
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
    // Debug / inspection helpers
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
     * may freely pass {@code null} when they do not care about debug output.
     * </p>
     */
    public void debugDump(DebugSink dbg, String prefix) {
        if (dbg == null) {
            return;
        }
        String p = (prefix != null && !prefix.isEmpty()) ? prefix + "." : "";

        dbg.addData(p + "lastFlPower", lastFlPower);
        dbg.addData(p + "lastFrPower", lastFrPower);
        dbg.addData(p + "lastBlPower", lastBlPower);
        dbg.addData(p + "lastBrPower", lastBrPower);

        dbg.addData(p + "lastAxialCmd", lastAxialCmd);
        dbg.addData(p + "lastLateralCmd", lastLateralCmd);
        dbg.addData(p + "lastOmegaCmd", lastOmegaCmd);

        dbg.addData(p + "lastDtSec", lastDtSec);
    }

    // ------------------------------------------------------------------------
    // Accessors
    // ------------------------------------------------------------------------

    /**
     * @return last commanded (clamped) power for front-left wheel.
     */
    public double getLastFlPower() {
        return lastFlPower;
    }

    /**
     * @return last commanded (clamped) power for front-right wheel.
     */
    public double getLastFrPower() {
        return lastFrPower;
    }

    /**
     * @return last commanded (clamped) power for back-left wheel.
     */
    public double getLastBlPower() {
        return lastBlPower;
    }

    /**
     * @return last commanded (clamped) power for back-right wheel.
     */
    public double getLastBrPower() {
        return lastBrPower;
    }
}

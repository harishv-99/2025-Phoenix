package edu.ftcphoenix.fw.drive.source;

import org.firstinspires.ftc.robotcore.external.Telemetry;

import edu.ftcphoenix.fw.drive.DriveSignal;
import edu.ftcphoenix.fw.drive.DriveSource;
import edu.ftcphoenix.fw.input.Axis;
import edu.ftcphoenix.fw.input.Button;
import edu.ftcphoenix.fw.input.DriverKit;
import edu.ftcphoenix.fw.util.DebugSink;
import edu.ftcphoenix.fw.util.LoopClock;
import edu.ftcphoenix.fw.util.MathUtil;

/**
 * Stick-based drive source for common FTC teleop use cases (mecanum / holonomic).
 *
 * <p>This class is the <em>single</em> recommended place to put:
 * <ul>
 *   <li>Stick mapping (which axis controls axial/lateral/turn).</li>
 *   <li>Deadband and expo (including “squared sticks”).</li>
 *   <li>Overall drive scaling, including optional “slow mode”.</li>
 * </ul>
 *
 * <h2>Default mapping (robot-centric mecanum)</h2>
 * <ul>
 *   <li>Axial (forward/back): player 1 left stick Y (up is +).</li>
 *   <li>Lateral (strafe):     player 1 left stick X.</li>
 *   <li>Omega (turn):         player 1 right stick X.</li>
 * </ul>
 *
 * <h2>Typical usage</h2>
 *
 * <pre>{@code
 * Gamepads pads = Gamepads.create(gamepad1, gamepad2);
 * DriverKit dk = DriverKit.of(pads);
 *
 * // No slow mode:
 * StickDriveSource drive = StickDriveSource.teleOpMecanum(dk);
 *
 * // Or, with slow mode on p1 right bumper (30% speed):
 * StickDriveSource drive = StickDriveSource.teleOpMecanumWithSlowMode(
 *         dk,
 *         dk.p1().rightBumper(),
 *         0.30
 * );
 *
 * // In loop:
 * DriveSignal sig = drive.get(clock);
 * drivebase.drive(sig);
 * }</pre>
 */
public final class StickDriveSource implements DriveSource {

    /**
     * Tuning parameters for stick shaping.
     *
     * <p>Values are public for simplicity; treat instances as config structs.</p>
     */
    public static final class Params {
        /**
         * Deadband applied to all axes before shaping (0..1).
         */
        public double deadband = 0.05;

        /**
         * Exponent for axial/lateral shaping (1 = linear, 2 ≈ “squared”).
         */
        public double translateExpo = 1.5;

        /**
         * Exponent for omega (turn) shaping.
         */
        public double rotateExpo = 1.0;

        /**
         * Max magnitude scale for axial/lateral after shaping.
         */
        public double translateScale = 1.0;

        /**
         * Max magnitude scale for omega after shaping.
         */
        public double rotateScale = 1.0;

        /**
         * Create a new params instance with default values.
         */
        public static Params defaults() {
            return new Params();
        }
    }

    // ------------------------------------------------------------------------

    private final DriverKit.Player player;
    private final Params params;

    private final Axis axisLateral;
    private final Axis axisAxial;
    private final Axis axisOmega;

    // Optional slow-mode support
    private final Button slowButton;   // may be null if no slow mode
    private final double slowScale;    // only used if slowButton != null

    // Track last output for debugging.
    private DriveSignal lastSignal = DriveSignal.ZERO;

    /**
     * Create a stick drive source using the given player and parameters,
     * with no slow mode.
     *
     * <p>For most robots, prefer {@link #teleOpMecanum(DriverKit)} or
     * {@link #teleOpMecanumWithSlowMode(DriverKit, Button, double)}.</p>
     */
    public StickDriveSource(DriverKit.Player player, Params params) {
        this(player, params, null, 1.0);
    }

    /**
     * Internal constructor that optionally configures a slow-mode button.
     */
    private StickDriveSource(DriverKit.Player player,
                             Params params,
                             Button slowButton,
                             double slowScale) {
        if (player == null) {
            throw new IllegalArgumentException("DriverKit.Player is required");
        }
        if (params == null) {
            throw new IllegalArgumentException("Params is required");
        }
        if (slowButton != null) {
            if (slowScale <= 0.0 || slowScale > 1.0) {
                throw new IllegalArgumentException("slowScale must be in (0,1]");
            }
        }

        this.player = player;
        this.params = params;
        this.slowButton = slowButton;
        this.slowScale = slowScale;

        // Robot-centric defaults:
        // leftX  → lateral
        // leftY  → axial (up is +)
        // rightX → omega
        this.axisLateral = player.leftX();
        this.axisAxial = player.leftY();
        this.axisOmega = player.rightX();
    }

    /**
     * TeleOp preset: player 1, robot-centric mecanum, no slow mode.
     *
     * <p>This is the recommended entry point for basic mecanum teleop.</p>
     */
    public static StickDriveSource teleOpMecanum(DriverKit kit) {
        if (kit == null) {
            throw new IllegalArgumentException("DriverKit is required");
        }
        return new StickDriveSource(kit.p1(), Params.defaults());
    }

    /**
     * TeleOp preset: player 1, robot-centric mecanum, with slow mode.
     *
     * <p>This configures mecanum drive with stick shaping as described in the
     * class Javadoc and uses {@code slowButton} to scale all commands by
     * {@code slowScale} when held.</p>
     *
     * <pre>{@code
     * // Example: p1 right bumper as slow mode (30% speed)
     * StickDriveSource drive = StickDriveSource.teleOpMecanumWithSlowMode(
     *         dk,
     *         dk.p1().rightBumper(),
     *         0.30
     * );
     * }</pre>
     */
    public static StickDriveSource teleOpMecanumWithSlowMode(DriverKit kit,
                                                             Button slowButton,
                                                             double slowScale) {
        if (kit == null) {
            throw new IllegalArgumentException("DriverKit is required");
        }
        return new StickDriveSource(kit.p1(), Params.defaults(), slowButton, slowScale);
    }

    /**
     * Compute a drive signal from the configured sticks.
     *
     * <p>{@link LoopClock} is accepted to match the {@link DriveSource}
     * interface, but this implementation does not currently use dt; all shaping
     * is purely positional. If you later add rate limiting, dt is available.</p>
     */
    @Override
    public DriveSignal get(LoopClock clock) {
        double rawLat = axisLateral.get();
        double rawAx = axisAxial.get();
        double rawOm = axisOmega.get();

        double lat = shape(rawLat,
                params.deadband,
                params.translateExpo,
                params.translateScale);

        double ax = shape(rawAx,
                params.deadband,
                params.translateExpo,
                params.translateScale);

        double om = shape(rawOm,
                params.deadband,
                params.rotateExpo,
                params.rotateScale);

        // Optional slow-mode scaling
        double modeScale = 1.0;
        if (slowButton != null && slowButton.isPressed()) {
            modeScale = slowScale;
        }

        DriveSignal out = new DriveSignal(
                ax * modeScale,
                lat * modeScale,
                om * modeScale
        );
        lastSignal = out;
        return out;
    }

    // ------------------------------------------------------------------------
    // Internal shaping helper
    // ------------------------------------------------------------------------

    /**
     * Apply deadband, shaping exponent, and scaling to a raw stick value.
     *
     * <p>Steps:</p>
     * <ol>
     *   <li>Apply symmetric deadband around zero (using {@link MathUtil#deadband(double, double)}).</li>
     *   <li>Normalize the remaining magnitude to [0,1].</li>
     *   <li>Apply exponent (1 = linear, &gt;1 = more gentle near center).</li>
     *   <li>Restore sign and apply scale.</li>
     * </ol>
     *
     * @param x        raw stick value in [-1, +1]
     * @param deadband deadband radius (0..1)
     * @param expo     shaping exponent (&gt;= 1)
     * @param scale    output scale (typically &lt;= 1)
     * @return shaped output in [-scale, +scale]
     */
    private static double shape(double x,
                                double deadband,
                                double expo,
                                double scale) {
        // Apply deadband
        double ax = Math.abs(x);
        if (ax <= deadband) {
            return 0.0;
        }

        // Map [deadband, 1] → [0, 1]
        double norm = (ax - deadband) / (1.0 - deadband);
        // Use shared clamp to avoid re-implementing bounds logic.
        norm = MathUtil.clamp01(norm);

        // Apply exponent; expo = 1 → linear, >1 → more gentle near center.
        if (expo < 1.0) {
            expo = 1.0; // avoid amplification near center
        }
        double shaped = Math.pow(norm, expo);

        // Restore sign and apply scale.
        double sign = (x >= 0.0) ? 1.0 : -1.0;
        return sign * shaped * scale;
    }

    /**
     * Emit current stick shaping parameters and last output command.
     *
     * @param dbg    debug sink (never null)
     * @param prefix base key prefix, e.g. "drive.sticks"
     */
    public void debugDump(DebugSink dbg, String prefix) {
        String p = (prefix == null || prefix.isEmpty()) ? "drive.sticks" : prefix;

        dbg.addLine(p)
                .addData(p + ".deadband",        params.deadband)
                .addData(p + ".translateExpo",   params.translateExpo)
                .addData(p + ".rotateExpo",      params.rotateExpo)
                .addData(p + ".translateScale",  params.translateScale)
                .addData(p + ".rotateScale",     params.rotateScale)
                .addData(p + ".slowConfigured",  slowButton != null)
                .addData(p + ".slowScale",       slowButton != null ? slowScale : 1.0)
                .addData(p + ".lastAxial",       lastSignal.axial)
                .addData(p + ".lastLateral",     lastSignal.lateral)
                .addData(p + ".lastOmega",       lastSignal.omega);
    }
}

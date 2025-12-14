package edu.ftcphoenix.fw.drive.source;

import edu.ftcphoenix.fw.debug.DebugSink;
import edu.ftcphoenix.fw.drive.DriveSignal;
import edu.ftcphoenix.fw.drive.DriveSource;
import edu.ftcphoenix.fw.input.Axis;
import edu.ftcphoenix.fw.input.Button;
import edu.ftcphoenix.fw.input.GamepadDevice;
import edu.ftcphoenix.fw.input.Gamepads;
import edu.ftcphoenix.fw.util.LoopClock;
import edu.ftcphoenix.fw.util.MathUtil;

/**
 * {@link DriveSource} implementation that maps gamepad sticks to a {@link DriveSignal}
 * for common FTC TeleOp use cases (mecanum / holonomic).
 *
 * <p>
 * This class is the <em>single</em> recommended place to put:
 * </p>
 *
 * <ul>
 *   <li>Stick mapping (which axis controls axial / lateral / turn).</li>
 *   <li>Deadband, expo, and scaling for the sticks (via {@link Config}).</li>
 *   <li>Slow-mode behavior controlled by a button (optional).</li>
 * </ul>
 *
 * <h2>Stick mapping and sign conventions</h2>
 *
 * <p>The standard mecanum TeleOp helpers in this class use:</p>
 *
 * <ul>
 *   <li>P1 left stick Y ({@link GamepadDevice#leftY()}): translation forward/back</li>
 *   <li>P1 left stick X ({@link GamepadDevice#leftX()}): strafe left/right</li>
 *   <li>P1 right stick X ({@link GamepadDevice#rightX()}): rotate left/right</li>
 * </ul>
 *
 * <p>
 * Important: {@link DriveSignal} is defined using Phoenix pose conventions:
 * +X forward, +Y left, and yaw CCW-positive. Therefore:
 * </p>
 *
 * <ul>
 *   <li>{@code axial > 0}   → forward</li>
 *   <li>{@code lateral > 0} → left</li>
 *   <li>{@code omega > 0}   → counter-clockwise (turn left)</li>
 * </ul>
 *
 * <p>
 * However, driver intuition is typically:
 * </p>
 *
 * <ul>
 *   <li>Push stick right → robot strafes right</li>
 *   <li>Push stick right → robot turns right</li>
 * </ul>
 *
 * <p>
 * This class preserves that driver intuition by converting at the boundary:
 * </p>
 *
 * <ul>
 *   <li>Left stick right (raw +) becomes {@code lateral < 0} (right strafe)</li>
 *   <li>Right stick right (raw +) becomes {@code omega < 0} (clockwise / turn right)</li>
 * </ul>
 *
 * <h2>Typical usage</h2>
 *
 * <pre>{@code
 * Gamepads pads = Gamepads.create(gamepad1, gamepad2);
 *
 * // Standard stick mapping + shaping + slow mode on P1 right bumper.
 * DriveSource drive = GamepadDriveSource.teleOpMecanumStandard(pads);
 *
 * MecanumDrivebase drivebase = Drives.mecanum(hardwareMap);
 *
 * // In loop():
 * clock.update(getRuntime());
 *
 * DriveSignal cmd = drive.get(clock);   // or drive.get(clock).clamped()
 * drivebase.update(clock);              // update dt used for drivebase rate limiting (if enabled)
 * drivebase.drive(cmd);
 * }</pre>
 *
 * <h2>Configuration</h2>
 *
 * <p>
 * Stick shaping parameters live in {@link Config}. The recommended pattern:
 * </p>
 *
 * <pre>{@code
 * GamepadDriveSource.Config cfg = GamepadDriveSource.Config.defaults();
 * cfg.deadband = 0.08;
 * cfg.rotateExpo = 1.2;
 *
 * DriveSource drive = GamepadDriveSource.teleOpMecanum(
 *         pads,
 *         cfg,
 *         pads.p1().rightBumper(),  // slow-mode button (optional)
 *         0.30                      // slow-mode scale (optional)
 * );
 * }</pre>
 *
 * <p>
 * Implementations that accept a {@link Config} (such as
 * {@link #teleOpMecanum(Gamepads, Config, Button, double)}) make a
 * <em>defensive copy</em> of the config at construction time. Changing the
 * fields of a {@code Config} instance <strong>after</strong> you pass it into
 * a drive source will <strong>not</strong> affect that already-created source.
 * </p>
 *
 * <h2>Generators vs wrappers</h2>
 *
 * <p>
 * {@code GamepadDriveSource} is a <strong>generator</strong>: it reads stick
 * inputs and directly produces a complete {@link DriveSignal} in robot-centric
 * coordinates ({@code axial / lateral / omega}).
 * </p>
 */
public final class GamepadDriveSource implements DriveSource {

    /**
     * Configuration for stick shaping in {@link GamepadDriveSource}.
     *
     * <p>
     * This is a simple <strong>mutable data object</strong> that controls how raw stick
     * values (in [-1, +1]) are turned into axial / lateral / omega drive commands.
     * </p>
     *
     * <h2>Shaping model</h2>
     *
     * <p>For each axis, {@link GamepadDriveSource} applies the following steps:</p>
     *
     * <ol>
     *   <li>Apply a symmetric deadband around zero ({@link #deadband}).</li>
     *   <li>Normalize the remaining magnitude to [0, 1].</li>
     *   <li>Apply an exponent ({@link #translateExpo} or {@link #rotateExpo}).</li>
     *   <li>Restore the sign and apply a scale ({@link #translateScale} or {@link #rotateScale}).</li>
     * </ol>
     */
    public static final class Config {

        /**
         * Symmetric deadband radius in [0, 1].
         *
         * <p>
         * Any raw stick magnitude whose absolute value is {@code <= deadband} will
         * be treated as zero. Values above the deadband are rescaled into [0, 1]
         * before the exponent is applied.
         * </p>
         *
         * <p>Default: {@code 0.05}.</p>
         */
        public double deadband = 0.05;

        /**
         * Exponent used for axial and lateral (translation) shaping.
         *
         * <p>
         * A value of {@code 1.0} leaves the response linear. Values &gt; 1.0 make
         * the response gentler near center and steeper near the edges.
         * </p>
         *
         * <p>Default: {@code 1.5}.</p>
         */
        public double translateExpo = 1.5;

        /**
         * Exponent used for rotational (omega) shaping.
         *
         * <p>
         * A value of {@code 1.0} leaves the response linear. Values &gt; 1.0 make
         * rotation more gentle near center while still allowing full-speed turns.
         * </p>
         *
         * <p>Default: {@code 1.5}.</p>
         */
        public double rotateExpo = 1.5;

        /**
         * Maximum scale for axial and lateral (translation) outputs.
         *
         * <p>
         * Applied after shaping. A value of {@code 1.0} allows full translation output;
         * values &lt; 1.0 reduce the maximum output in both axial and lateral directions.
         * </p>
         *
         * <p>Default: {@code 1.0}.</p>
         */
        public double translateScale = 1.0;

        /**
         * Maximum scale for rotational (omega) output.
         *
         * <p>
         * Applied after shaping. A value of {@code 1.0} allows full rotation output;
         * values &lt; 1.0 reduce the maximum rotational speed.
         * </p>
         *
         * <p>Default: {@code 1.0}.</p>
         */
        public double rotateScale = 1.0;

        private Config() {
            // Defaults assigned in field initializers.
        }

        /**
         * Create a new {@link Config} with Phoenix default values.
         */
        public static Config defaults() {
            return new Config();
        }

        /**
         * Create a deep copy of this config.
         */
        public Config copy() {
            Config c = new Config();
            c.deadband = this.deadband;
            c.translateExpo = this.translateExpo;
            c.rotateExpo = this.rotateExpo;
            c.translateScale = this.translateScale;
            c.rotateScale = this.rotateScale;
            return c;
        }
    }

    private final Axis axisLateralRaw; // raw stick axis: +right (typical)
    private final Axis axisAxial;      // raw stick axis: +forward (per GamepadDevice wrapper)
    private final Axis axisOmegaRaw;   // raw stick axis: +right turn (clockwise, typical)

    private final Config cfg;

    // Optional slow-mode configuration.
    private final Button slowButton; // may be null
    private final double slowScale;  // only used when slowButton != null

    // Last output for debug/telemetry.
    private DriveSignal lastSignal = DriveSignal.ZERO;

    // ------------------------------------------------------------------------
    // Static helpers (recommended entry points)
    // ------------------------------------------------------------------------

    /**
     * Simple mecanum TeleOp mapping using Phoenix defaults and no slow mode.
     *
     * @param pads gamepad wrapper created from FTC {@code gamepad1}, {@code gamepad2}
     * @return a {@link DriveSource} that reads P1 sticks and produces drive commands
     */
    public static DriveSource teleOpMecanum(Gamepads pads) {
        if (pads == null) {
            throw new IllegalArgumentException("Gamepads is required");
        }
        return teleOpMecanum(pads, Config.defaults(), null, 1.0);
    }

    /**
     * Mecanum TeleOp mapping with custom stick config and no slow mode.
     *
     * @param pads gamepad wrapper created from FTC {@code gamepad1}, {@code gamepad2}
     * @param cfg  stick shaping configuration (deadband, expo, scales)
     * @return a {@link DriveSource} that reads P1 sticks and produces drive commands
     */
    public static DriveSource teleOpMecanum(Gamepads pads, Config cfg) {
        if (pads == null) {
            throw new IllegalArgumentException("Gamepads is required");
        }
        if (cfg == null) {
            throw new IllegalArgumentException("GamepadDriveSource.Config is required");
        }
        return teleOpMecanum(pads, cfg, null, 1.0);
    }

    /**
     * Full-control mecanum TeleOp mapping with custom config and optional slow mode.
     *
     * <p>
     * Uses P1 left stick X/Y for translation and P1 right stick X for rotation.
     * The supplied {@link Config} controls deadband/expo/scales, and the
     * optional {@code slowButton} + {@code slowScale} control slow-mode behavior.
     * </p>
     *
     * @param pads       gamepad wrapper created from FTC {@code gamepad1}, {@code gamepad2}
     * @param cfg        stick shaping configuration (will be defensively copied)
     * @param slowButton button that enables slow mode while pressed (may be {@code null})
     * @param slowScale  scale applied to all components when slow mode is active
     *                   (must be in (0,1] if {@code slowButton} is non-null)
     * @return a {@link DriveSource} that reads P1 sticks and produces drive commands
     */
    public static DriveSource teleOpMecanum(Gamepads pads,
                                            Config cfg,
                                            Button slowButton,
                                            double slowScale) {
        if (pads == null) {
            throw new IllegalArgumentException("Gamepads is required");
        }
        if (cfg == null) {
            throw new IllegalArgumentException("GamepadDriveSource.Config is required");
        }
        GamepadDevice p1 = pads.p1();
        return new GamepadDriveSource(
                p1.leftX(),   // raw lateral: + = right
                p1.leftY(),   // axial:       + = forward (stick up, per GamepadDevice)
                p1.rightX(),  // raw omega:   + = turn right (clockwise)
                cfg,
                slowButton,
                slowScale
        );
    }

    /**
     * Phoenix standard mecanum TeleOp mapping with default shaping and slow mode.
     *
     * @param pads gamepad wrapper created from FTC {@code gamepad1}, {@code gamepad2}
     * @return a {@link DriveSource} ready to plug into a drivebase
     */
    public static DriveSource teleOpMecanumStandard(Gamepads pads) {
        if (pads == null) {
            throw new IllegalArgumentException("Gamepads is required");
        }
        Config cfg = Config.defaults();
        Button slow = pads.p1().rightBumper();
        double slowScale = 0.30;
        return teleOpMecanum(pads, cfg, slow, slowScale);
    }

    // ------------------------------------------------------------------------
    // Constructors
    // ------------------------------------------------------------------------

    /**
     * Core constructor: map three axes + optional slow button into a drive signal.
     *
     * <p>
     * Most callers should use one of the {@code teleOpMecanum(...)} helpers
     * instead of calling this directly.
     * </p>
     *
     * @param axisLateralRaw raw lateral axis (typically +right)
     * @param axisAxial      axial axis (+forward per {@link GamepadDevice} wrapper)
     * @param axisOmegaRaw   raw omega axis (typically +turn-right / clockwise)
     * @param cfg            stick shaping configuration (will be defensively copied)
     * @param slowButton     button that enables slow mode while pressed
     *                       (may be {@code null} for no slow mode)
     * @param slowScale      scale applied to all components when slow mode is active
     *                       (must be in (0,1] if {@code slowButton} is non-null)
     */
    public GamepadDriveSource(Axis axisLateralRaw,
                              Axis axisAxial,
                              Axis axisOmegaRaw,
                              Config cfg,
                              Button slowButton,
                              double slowScale) {
        if (axisLateralRaw == null) {
            throw new IllegalArgumentException("axisLateralRaw is required");
        }
        if (axisAxial == null) {
            throw new IllegalArgumentException("axisAxial is required");
        }
        if (axisOmegaRaw == null) {
            throw new IllegalArgumentException("axisOmegaRaw is required");
        }
        if (cfg == null) {
            throw new IllegalArgumentException("GamepadDriveSource.Config is required");
        }
        if (slowButton != null) {
            if (!(slowScale > 0.0 && slowScale <= 1.0)) {
                throw new IllegalArgumentException("slowScale must be in (0,1] when slowButton is non-null");
            }
        }

        this.axisLateralRaw = axisLateralRaw;
        this.axisAxial = axisAxial;
        this.axisOmegaRaw = axisOmegaRaw;
        this.cfg = cfg.copy(); // defensive copy

        this.slowButton = slowButton;
        this.slowScale = (slowButton != null) ? slowScale : 1.0;
    }

    // ------------------------------------------------------------------------
    // DriveSource implementation
    // ------------------------------------------------------------------------

    /**
     * Compute a {@link DriveSignal} from the configured sticks.
     *
     * <p>
     * This preserves standard driver intuition while still outputting Phoenix-consistent
     * {@link DriveSignal} signs:
     * </p>
     *
     * <ul>
     *   <li>Stick right strafe → {@code lateral < 0} (right strafe)</li>
     *   <li>Stick right turn   → {@code omega < 0} (clockwise / turn right)</li>
     * </ul>
     */
    @Override
    public DriveSignal get(LoopClock clock) {
        double rawLat = axisLateralRaw.get(); // +right
        double rawAx = axisAxial.get();       // +forward (per GamepadDevice)
        double rawOm = axisOmegaRaw.get();    // +turn-right (clockwise)

        double latRightPositive = shape(rawLat, cfg.deadband, cfg.translateExpo, cfg.translateScale);
        double ax = shape(rawAx, cfg.deadband, cfg.translateExpo, cfg.translateScale);
        double omClockwisePositive = shape(rawOm, cfg.deadband, cfg.rotateExpo, cfg.rotateScale);

        // Convert raw driver conventions to Phoenix DriveSignal conventions:
        // - DriveSignal.lateral > 0 means LEFT, so invert raw +right
        // - DriveSignal.omega   > 0 means CCW,  so invert raw +clockwise
        double lateralLeftPositive = -latRightPositive;
        double omegaCcwPositive = -omClockwisePositive;

        double modeScale = 1.0;
        if (slowButton != null && slowButton.isHeld()) {
            modeScale = slowScale;
        }

        DriveSignal out = new DriveSignal(
                ax * modeScale,
                lateralLeftPositive * modeScale,
                omegaCcwPositive * modeScale
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
     *
     * <ol>
     *   <li>Apply symmetric deadband around zero (using absolute value).</li>
     *   <li>Normalize the remaining magnitude to [0,1].</li>
     *   <li>Apply exponent (1 = linear, &gt;1 = more gentle near center).</li>
     *   <li>Restore sign and apply scale.</li>
     * </ol>
     *
     * @param x        raw stick value in [-1, +1]
     * @param deadband deadband radius (0..1)
     * @param expo     shaping exponent (&gt;= 1 recommended)
     * @param scale    output scale (typically &lt;= 1)
     * @return shaped output in [-scale, +scale]
     */
    private static double shape(double x,
                                double deadband,
                                double expo,
                                double scale) {
        double ax = Math.abs(x);
        if (ax <= deadband) {
            return 0.0;
        }

        // Map [deadband, 1] → [0, 1]
        double norm = (ax - deadband) / (1.0 - deadband);
        norm = MathUtil.clamp01(norm);

        // Apply exponent; expo = 1 → linear, >1 → more gentle near center.
        if (expo < 1.0) {
            expo = 1.0; // avoid amplification near center
        }
        double shaped = Math.pow(norm, expo);

        // Restore sign and apply scale.
        double sign = (x >= 0.0) ? 1.0 : -1.0;
        return sign * scale * shaped;
    }

    // ------------------------------------------------------------------------
    // Debug support
    // ------------------------------------------------------------------------

    /**
     * Dump internal state to a {@link DebugSink}.
     *
     * @param dbg    debug sink to write to (may be {@code null})
     * @param prefix key prefix for all entries (may be {@code null} or empty)
     */
    public void debugDump(DebugSink dbg, String prefix) {
        if (dbg == null) {
            return;
        }
        String p = (prefix == null || prefix.isEmpty()) ? "sticks" : prefix;

        dbg.addLine(p + ": GamepadDriveSource");

        // Current raw axis readings (sampling now).
        dbg.addData(p + ".axis.lateral.raw", axisLateralRaw.get());
        dbg.addData(p + ".axis.axial.raw", axisAxial.get());
        dbg.addData(p + ".axis.omega.raw", axisOmegaRaw.get());

        // Last shaped output (Phoenix DriveSignal conventions).
        dbg.addData(p + ".last.axial", lastSignal.axial);
        dbg.addData(p + ".last.lateral", lastSignal.lateral);
        dbg.addData(p + ".last.omega", lastSignal.omega);

        // Shaping params.
        dbg.addData(p + ".cfg.deadband", cfg.deadband);
        dbg.addData(p + ".cfg.translateExpo", cfg.translateExpo);
        dbg.addData(p + ".cfg.rotateExpo", cfg.rotateExpo);
        dbg.addData(p + ".cfg.translateScale", cfg.translateScale);
        dbg.addData(p + ".cfg.rotateScale", cfg.rotateScale);

        // Slow mode configuration and state.
        dbg.addData(p + ".slow.configured", slowButton != null);
        if (slowButton != null) {
            dbg.addData(p + ".slow.scale", slowScale);
            dbg.addData(p + ".slow.pressed", slowButton.isHeld());
        }
    }

    /**
     * Last computed command from this source.
     *
     * @return last {@link DriveSignal} produced by this source
     */
    public DriveSignal getLastSignal() {
        return lastSignal;
    }
}

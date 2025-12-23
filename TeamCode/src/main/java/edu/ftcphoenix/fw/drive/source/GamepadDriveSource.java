package edu.ftcphoenix.fw.drive.source;

import edu.ftcphoenix.fw.core.debug.DebugSink;
import edu.ftcphoenix.fw.drive.DriveSignal;
import edu.ftcphoenix.fw.drive.DriveSource;
import edu.ftcphoenix.fw.input.Axis;
import edu.ftcphoenix.fw.input.Button;
import edu.ftcphoenix.fw.input.GamepadDevice;
import edu.ftcphoenix.fw.input.Gamepads;
import edu.ftcphoenix.fw.core.time.LoopClock;

/**
 * {@link DriveSource} that maps gamepad inputs to a robot-centric {@link DriveSignal}
 * for TeleOp driving (e.g., mecanum).
 *
 * <h2>What this class is responsible for</h2>
 * <ul>
 *   <li>Mapping gamepad axes to drive intent (axial / lateral / omega).</li>
 *   <li>Stick shaping (deadband + exponent) and scaling (max translate / max omega).</li>
 *   <li>Optional slow mode (separate translation vs omega scaling).</li>
 * </ul>
 *
 * <h2>Phoenix sign conventions</h2>
 * <p>{@link DriveSignal} uses Phoenix conventions:</p>
 * <ul>
 *   <li>{@code axial > 0}   → forward</li>
 *   <li>{@code lateral > 0} → left</li>
 *   <li>{@code omega > 0}   → CCW (turn left)</li>
 * </ul>
 *
 * <p>
 * Standard FTC stick intuition is typically “stick right means right / clockwise”.
 * This class preserves that driver intuition by converting signs at the boundary:
 * </p>
 * <ul>
 *   <li>Left stick X: raw +right becomes {@code lateral < 0} (right strafe) → inverted</li>
 *   <li>Right stick X: raw +clockwise becomes {@code omega < 0} (clockwise) → inverted</li>
 * </ul>
 *
 * <h2>Recommended usage</h2>
 *
 * <pre>{@code
 * Gamepads pads = Gamepads.create(gamepad1, gamepad2);
 *
 * // Default mapping + shaping, no slow-mode (avoids button conflicts by default).
 * DriveSource drive = GamepadDriveSource.teleOpMecanum(pads);
 *
 * // Default mapping + shaping + slow mode on RB (with practical default slow scales).
 * DriveSource driveSlow = GamepadDriveSource.teleOpMecanumSlowRb(pads);
 *
 * // Or: explicit config (for custom slow button/scales or shaping).
 * GamepadDriveSource.Config cfg = GamepadDriveSource.Config.defaults()
 *         .withSlow(pads.p1().rightBumper(), 0.35, 0.20);
 * DriveSource driveCustom = GamepadDriveSource.teleOpMecanum(pads, cfg);
 * }</pre>
 *
 * <h2>Note on shaping</h2>
 * <p>
 * Stick shaping uses {@link Axis#shaped(double, double, double, double)} with min/max
 * of {@code [-1, +1]} because this class is mapping gamepad sticks.
 * </p>
 */
public final class GamepadDriveSource implements DriveSource {

    /**
     * Configuration for TeleOp stick shaping and optional slow mode.
     *
     * <p>
     * This is a mutable data object. {@link GamepadDriveSource} makes a defensive copy
     * when constructed.
     * </p>
     */
    public static final class Config {

        /**
         * Symmetric deadband radius in [0, 1]. Default: 0.05.
         *
         * <p>
         * Values with {@code |v| <= deadband} are treated as 0. Values outside the deadband
         * are normalized before the exponent is applied.
         * </p>
         */
        public double deadband = 0.05;

        /**
         * Exponent for translation (axial + lateral). Default: 1.5.
         *
         * <p>Values &gt; 1 soften near center and keep full-scale at the edges.</p>
         */
        public double translateExpo = 1.5;

        /**
         * Exponent for rotation (omega). Default: 1.5.
         */
        public double rotateExpo = 1.5;

        /**
         * Max translation scale (applied after shaping). Default: 1.0.
         */
        public double translateScale = 1.0;

        /**
         * Max rotation scale (applied after shaping). Default: 1.0.
         */
        public double rotateScale = 1.0;

        /**
         * Optional slow-mode enable button. Default: null (slow mode disabled).
         *
         * <p>
         * If null, slow mode is disabled and the slow scales are ignored.
         * </p>
         */
        public Button slowButton = null;

        /**
         * Translation scale while slow mode is active. Default: 0.35.
         *
         * <p>Only used when {@link #slowButton} is non-null.</p>
         */
        public double slowTranslateScale = 0.35;

        /**
         * Omega scale while slow mode is active. Default: 0.20.
         *
         * <p>Only used when {@link #slowButton} is non-null.</p>
         */
        public double slowOmegaScale = 0.20;

        private Config() {
            // Defaults set via field initializers.
        }

        /**
         * Default shaping with slow mode disabled (no button conflicts by default).
         */
        public static Config defaults() {
            return new Config();
        }

        /**
         * Convenience factory: {@link #defaults()} plus slow mode enabled using the current
         * default slow scales ({@link #slowTranslateScale} and {@link #slowOmegaScale}).
         *
         * <p>
         * This method intentionally does not hard-code the slow scales so that changing the
         * default field initializers automatically updates this factory.
         * </p>
         */
        public static Config defaultsWithSlow(Button slowButton) {
            Config c = defaults();
            return c.withSlow(slowButton, c.slowTranslateScale, c.slowOmegaScale);
        }

        /**
         * Enable slow mode with the given button and scales.
         *
         * @param button slow-mode enable button (non-null)
         * @param translateScale translation scale while slow mode active (0, 1]
         * @param omegaScale omega scale while slow mode active (0, 1]
         */
        public Config withSlow(Button button, double translateScale, double omegaScale) {
            if (button == null) {
                throw new IllegalArgumentException("slow button must be non-null");
            }
            if (!(translateScale > 0.0 && translateScale <= 1.0)) {
                throw new IllegalArgumentException("slow translateScale must be in (0,1]");
            }
            if (!(omegaScale > 0.0 && omegaScale <= 1.0)) {
                throw new IllegalArgumentException("slow omegaScale must be in (0,1]");
            }

            this.slowButton = button;
            this.slowTranslateScale = translateScale;
            this.slowOmegaScale = omegaScale;
            return this;
        }

        /**
         * Disable slow mode (clears {@link #slowButton}).
         */
        public Config withoutSlow() {
            this.slowButton = null;
            return this;
        }

        /**
         * Deep copy of this config (note: {@link #slowButton} reference is copied as-is).
         */
        public Config copy() {
            Config c = new Config();
            c.deadband = this.deadband;
            c.translateExpo = this.translateExpo;
            c.rotateExpo = this.rotateExpo;
            c.translateScale = this.translateScale;
            c.rotateScale = this.rotateScale;

            c.slowButton = this.slowButton;
            c.slowTranslateScale = this.slowTranslateScale;
            c.slowOmegaScale = this.slowOmegaScale;
            return c;
        }
    }

    // Raw axes (sampled for debug).
    private final Axis axisLateralRaw; // raw +right (typical)
    private final Axis axisAxialRaw;   // +forward (per GamepadDevice)
    private final Axis axisOmegaRaw;   // raw +clockwise (typical)

    // Shaped axes in Phoenix DriveSignal conventions.
    private final Axis axisAxialCmd;
    private final Axis axisLateralCmd; // +left
    private final Axis axisOmegaCmd;   // +CCW

    private final Config cfg;

    private DriveSignal lastSignal = DriveSignal.zero();

    // ------------------------------------------------------------------------
    // Recommended entry points
    // ------------------------------------------------------------------------

    /**
     * Mecanum TeleOp mapping using Phoenix defaults (no slow mode).
     *
     * <p>
     * Mapping:
     * <ul>
     *   <li>P1 left stick Y → axial</li>
     *   <li>P1 left stick X → lateral</li>
     *   <li>P1 right stick X → omega</li>
     * </ul>
     * </p>
     */
    public static DriveSource teleOpMecanum(Gamepads pads) {
        if (pads == null) {
            throw new IllegalArgumentException("Gamepads is required");
        }
        return teleOpMecanum(pads, Config.defaults());
    }

    /**
     * Mecanum TeleOp mapping with custom config (including optional slow mode).
     *
     * <p>
     * Most teams should start with {@link Config#defaults()} and optionally call
     * {@link Config#withSlow(Button, double, double)}.
     * </p>
     */
    public static DriveSource teleOpMecanum(Gamepads pads, Config cfg) {
        if (pads == null) {
            throw new IllegalArgumentException("Gamepads is required");
        }
        if (cfg == null) {
            throw new IllegalArgumentException("GamepadDriveSource.Config is required");
        }

        GamepadDevice p1 = pads.p1();
        return new GamepadDriveSource(
                p1.leftX(),
                p1.leftY(),
                p1.rightX(),
                cfg
        );
    }

    /**
     * Convenience factory: {@link #teleOpMecanum(Gamepads)} plus slow mode on P1 RB
     * using the current default slow scales in {@link Config}.
     */
    public static DriveSource teleOpMecanumSlowRb(Gamepads pads) {
        if (pads == null) {
            throw new IllegalArgumentException("Gamepads is required");
        }
        return teleOpMecanum(pads, Config.defaultsWithSlow(pads.p1().rightBumper()));
    }

    // ------------------------------------------------------------------------
    // Construction
    // ------------------------------------------------------------------------

    /**
     * Core constructor: map three raw axes into a drive signal using {@link Config}.
     *
     * <p>
     * Most callers should use one of the {@code teleOpMecanum(...)} helpers.
     * </p>
     *
     * @param axisLateralRaw raw lateral axis (typically +right)
     * @param axisAxialRaw axial axis (+forward per {@link GamepadDevice})
     * @param axisOmegaRaw raw omega axis (typically +clockwise / turn-right)
     * @param cfg shaping + slow-mode configuration (defensively copied)
     */
    public GamepadDriveSource(Axis axisLateralRaw,
                              Axis axisAxialRaw,
                              Axis axisOmegaRaw,
                              Config cfg) {
        if (axisLateralRaw == null) {
            throw new IllegalArgumentException("axisLateralRaw is required");
        }
        if (axisAxialRaw == null) {
            throw new IllegalArgumentException("axisAxialRaw is required");
        }
        if (axisOmegaRaw == null) {
            throw new IllegalArgumentException("axisOmegaRaw is required");
        }
        if (cfg == null) {
            throw new IllegalArgumentException("GamepadDriveSource.Config is required");
        }

        // Validate slow-mode parameters if configured.
        if (cfg.slowButton != null) {
            if (!(cfg.slowTranslateScale > 0.0 && cfg.slowTranslateScale <= 1.0)) {
                throw new IllegalArgumentException("slowTranslateScale must be in (0,1]");
            }
            if (!(cfg.slowOmegaScale > 0.0 && cfg.slowOmegaScale <= 1.0)) {
                throw new IllegalArgumentException("slowOmegaScale must be in (0,1]");
            }
        }

        this.axisLateralRaw = axisLateralRaw;
        this.axisAxialRaw = axisAxialRaw;
        this.axisOmegaRaw = axisOmegaRaw;
        this.cfg = cfg.copy();

        // Build shaped command axes (pre-built wrappers, no per-loop allocation).
        //
        // Notes on sign:
        // - DriveSignal.lateral is +left, but stick X raw is +right → invert.
        // - DriveSignal.omega is +CCW, but stick X raw is +clockwise → invert.
        Axis axial = this.axisAxialRaw
                .shaped(this.cfg.deadband, this.cfg.translateExpo, -1.0, 1.0)
                .scaled(this.cfg.translateScale);

        Axis lateralLeft = this.axisLateralRaw
                .shaped(this.cfg.deadband, this.cfg.translateExpo, -1.0, 1.0)
                .scaled(this.cfg.translateScale)
                .inverted();

        Axis omegaCcw = this.axisOmegaRaw
                .shaped(this.cfg.deadband, this.cfg.rotateExpo, -1.0, 1.0)
                .scaled(this.cfg.rotateScale)
                .inverted();

        this.axisAxialCmd = axial;
        this.axisLateralCmd = lateralLeft;
        this.axisOmegaCmd = omegaCcw;
    }

    // ------------------------------------------------------------------------
    // DriveSource implementation
    // ------------------------------------------------------------------------

    /** {@inheritDoc} */
    @Override
    public DriveSignal get(LoopClock clock) {
        double ax = axisAxialCmd.get();
        double lat = axisLateralCmd.get();
        double om = axisOmegaCmd.get();

        if (cfg.slowButton != null && cfg.slowButton.isHeld()) {
            ax *= cfg.slowTranslateScale;
            lat *= cfg.slowTranslateScale;
            om *= cfg.slowOmegaScale;
        }

        DriveSignal out = new DriveSignal(ax, lat, om);
        lastSignal = out;
        return out;
    }

    // ------------------------------------------------------------------------
    // Debug support
    // ------------------------------------------------------------------------

    /**
     * Dump internal state to a {@link DebugSink}.
     *
     * @param dbg debug sink to write to (may be {@code null})
     * @param prefix key prefix for all entries (may be {@code null} or empty)
     */
    public void debugDump(DebugSink dbg, String prefix) {
        if (dbg == null) {
            return;
        }
        String p = (prefix == null || prefix.isEmpty()) ? "sticks" : prefix;

        dbg.addLine(p + ": GamepadDriveSource");

        dbg.addData(p + ".axis.lateral.raw", axisLateralRaw.get());
        dbg.addData(p + ".axis.axial.raw", axisAxialRaw.get());
        dbg.addData(p + ".axis.omega.raw", axisOmegaRaw.get());

        dbg.addData(p + ".last.axial", lastSignal.axial);
        dbg.addData(p + ".last.lateral", lastSignal.lateral);
        dbg.addData(p + ".last.omega", lastSignal.omega);

        dbg.addData(p + ".cfg.deadband", cfg.deadband);
        dbg.addData(p + ".cfg.translateExpo", cfg.translateExpo);
        dbg.addData(p + ".cfg.rotateExpo", cfg.rotateExpo);
        dbg.addData(p + ".cfg.translateScale", cfg.translateScale);
        dbg.addData(p + ".cfg.rotateScale", cfg.rotateScale);

        dbg.addData(p + ".slow.configured", cfg.slowButton != null);
        if (cfg.slowButton != null) {
            dbg.addData(p + ".slow.translateScale", cfg.slowTranslateScale);
            dbg.addData(p + ".slow.omegaScale", cfg.slowOmegaScale);
            dbg.addData(p + ".slow.pressed", cfg.slowButton.isHeld());
        }
    }

    /**
     * Last computed command from this source.
     */
    public DriveSignal getLastSignal() {
        return lastSignal;
    }
}

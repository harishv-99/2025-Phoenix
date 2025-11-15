package edu.ftcphoenix.fw.input;

import com.qualcomm.robotcore.hardware.Gamepad;

import org.firstinspires.ftc.robotcore.external.Telemetry;

import edu.ftcphoenix.fw.util.MathUtil;

/**
 * Thin wrapper around FTC {@link Gamepad} that:
 * <ul>
 *   <li>Captures stick center bias and corrects it (auto-calibrates on construction).</li>
 *   <li>Normalizes stick Y so that “up is +”, matching common robot math conventions.</li>
 *   <li>Exposes a consistent, explicit API for axes and buttons:
 *       leftX/leftY/rightX/rightY/leftTrigger/rightTrigger,
 *       leftBumper/rightBumper, leftStickButton/rightStickButton,
 *       dpadUp/Down/Left/Right, buttonA/B/X/Y.</li>
 * </ul>
 *
 * <h2>Calibration model</h2>
 * On construction (and whenever {@link #recalibrate()} is called) we sample the current stick
 * positions as the center offsets (lx0, ly0, rx0, ry0). Reads are then corrected using:
 * <pre>
 *   if (raw >= offset)  v = (raw - offset) / (1 - offset);
 *   else                v = (raw - offset) / (1 + offset);
 * </pre>
 * This recenters to 0 and preserves full travel to ±1 even with a biased center.
 * After normalization, Y axes are negated so “stick up” becomes +1 (FTC raw Y is typically -1).
 *
 * <h2>Responsibilities</h2>
 * Provide bias-corrected, consistently named axes & buttons for one gamepad. Offer on-demand re-zero.
 *
 * <h2>Non-responsibilities</h2>
 * No edge detection/debounce (use input.binding.Bindings). No response shaping (use drive sources).
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * Gamepads pads = Gamepads.create(gamepad1, gamepad2);
 * Axis strafe  = pads.p1().leftX();
 * Axis forward = pads.p1().leftY();      // up is + after normalization
 * Axis turn    = pads.p1().rightX();
 * Button fire  = pads.p1().buttonX();
 * // Optional re-zero mid-match: pads.p1().recalibrate();
 * }</pre>
 */
public final class GamepadDevice {

    private final Gamepad gp;

    // Stick center offsets
    private double lx0, ly0, rx0, ry0;

    public GamepadDevice(Gamepad gp) {
        this.gp = gp;
        recalibrate();
    }

    /**
     * Sample the current stick positions as the new centers.
     *
     * <p>Call this when sticks are physically centered to correct for bias.</p>
     */
    public void recalibrate() {
        lx0 = gp.left_stick_x;
        ly0 = gp.left_stick_y;
        rx0 = gp.right_stick_x;
        ry0 = gp.right_stick_y;
    }

    // ---------------- Axes ----------------

    public Axis leftX() {
        return Axis.of(() -> normalizeAxis(gp.left_stick_x, lx0));
    }

    public Axis leftY() {
        // FTC Y+ is down, so we invert.
        return Axis.of(() -> -normalizeAxis(gp.left_stick_y, ly0));
    }

    public Axis rightX() {
        return Axis.of(() -> normalizeAxis(gp.right_stick_x, rx0));
    }

    public Axis rightY() {
        // FTC Y+ is down, so we invert.
        return Axis.of(() -> -normalizeAxis(gp.right_stick_y, ry0));
    }

    public Axis leftTrigger() {
        return Axis.of(() -> clamp01(gp.left_trigger));
    }

    public Axis rightTrigger() {
        return Axis.of(() -> clamp01(gp.right_trigger));
    }

    // Short aliases
    public Axis lx() { return leftX(); }
    public Axis ly() { return leftY(); }
    public Axis rx() { return rightX(); }
    public Axis ry() { return rightY(); }
    public Axis lt() { return leftTrigger(); }
    public Axis rt() { return rightTrigger(); }

    // ---------------- Buttons ----------------

    public Button leftBumper() {
        return Button.of(() -> gp.left_bumper);
    }

    public Button rightBumper() {
        return Button.of(() -> gp.right_bumper);
    }

    public Button leftStickButton() {
        return Button.of(() -> gp.left_stick_button);
    }

    public Button rightStickButton() {
        return Button.of(() -> gp.right_stick_button);
    }

    public Button dpadUp() {
        return Button.of(() -> gp.dpad_up);
    }

    public Button dpadDown() {
        return Button.of(() -> gp.dpad_down);
    }

    public Button dpadLeft() {
        return Button.of(() -> gp.dpad_left);
    }

    public Button dpadRight() {
        return Button.of(() -> gp.dpad_right);
    }

    public Button buttonA() {
        return Button.of(() -> gp.a);
    }

    public Button buttonB() {
        return Button.of(() -> gp.b);
    }

    public Button buttonX() {
        return Button.of(() -> gp.x);
    }

    public Button buttonY() {
        return Button.of(() -> gp.y);
    }

    public Button start() {
        return Button.of(() -> gp.start);
    }

    public Button back() {
        return Button.of(() -> gp.back);
    }

    // Short aliases for buttons
    public Button a() { return buttonA(); }
    public Button b() { return buttonB(); }
    public Button x() { return buttonX(); }
    public Button y() { return buttonY(); }

    // ---------------- Debug / Telemetry ----------------

    /**
     * Emit calibration info to telemetry.
     */
    public void addCalibrationTelemetry(Telemetry telemetry, String prefix) {
        telemetry.addData(prefix + " lx0", fmt(lx0));
        telemetry.addData(prefix + " ly0", fmt(ly0));
        telemetry.addData(prefix + " rx0", fmt(rx0));
        telemetry.addData(prefix + " ry0", fmt(ry0));
    }

    // ---------------- Helpers ----------------

    private static String fmt(double v) {
        return String.format("%.3f", v);
    }

    /**
     * Clamp a value to [0, 1] for triggers or other normalized quantities.
     */
    private static double clamp01(double v) {
        return MathUtil.clamp01(v);
    }

    /**
     * Apply piecewise normalization around offset and clamp to [-1..+1].
     */
    private static double normalizeAxis(double raw, double offset) {
        double v = (raw >= offset)
                ? (raw - offset) / (1.0 - offset)
                : (raw - offset) / (1.0 + offset);
        return MathUtil.clamp(v, -1.0, 1.0);
    }
}

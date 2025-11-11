package edu.ftcphoenix.fw.input;

import com.qualcomm.robotcore.hardware.Gamepad;

import org.firstinspires.ftc.robotcore.external.Telemetry;

import java.util.Objects;

/**
 * Thin wrapper around FTC {@link Gamepad} that:
 * <ul>
 *   <li>Captures stick center bias and corrects it (auto-calibrates on construction).</li>
 *   <li>Exposes PS aliases (cross/circle/square/triangle) in addition to A/B/X/Y.</li>
 *   <li>Normalizes stick Y so that "up is +", matching common robot math conventions.</li>
 * </ul>
 *
 * <h2>Calibration model</h2>
 * On construction (and whenever {@link #recalibrate()} is called) we sample the current stick
 * positions as the center offsets (lx0, ly0, rx0, ry0). Reads are then corrected using the
 * linear normalization:
 * <pre>
 *   if (raw >= offset)  v = (raw - offset) / (1 - offset);
 *   else                v = (raw - offset) / (1 + offset);
 * </pre>
 * This both recenters to 0 and preserves full travel to ±1 even with a biased center.
 *
 * <h2>Usage</h2>
 * Typically constructed by {@link Gamepads#create(Gamepad, Gamepad)}; you shouldn't need to
 * call {@link #recalibrate()} at startup. Bind a chord (e.g., BACK+START) to {@link #recalibrate()}
 * if you want an on-demand re-zero mid-match.
 */
public final class GamepadDevice {

    private final Gamepad gp;

    // captured zero offsets at (auto)calibration time
    private double lx0, ly0, rx0, ry0;

    // last-calibration raw snapshot (for telemetry)
    private double calLX, calLY, calRX, calRY;

    public GamepadDevice(Gamepad gp) {
        this.gp = Objects.requireNonNull(gp, "gamepad");
        recalibrate(); // <-- auto-calibrate at construction
    }

    /**
     * Re-sample current stick positions as the new centers. Safe to call anytime (e.g., BACK+START chord).
     */
    public void recalibrate() {
        // Snapshot current raw values
        calLX = gp.left_stick_x;
        calLY = gp.left_stick_y;
        calRX = gp.right_stick_x;
        calRY = gp.right_stick_y;

        // Store as offsets (note: we flip Y sign later in accessors, not here)
        lx0 = calLX;
        ly0 = calLY;
        rx0 = calRX;
        ry0 = calRY;
    }

    // ---------------------------
    // Sticks (normalized with bias correction)
    // Y returns "up is +" (negate FTC's default)
    // ---------------------------

    /**
     * Left stick X in [-1..1], corrected for center bias.
     */
    public double leftX() {
        return correct(gp.left_stick_x, lx0);
    }

    /**
     * Left stick Y in [-1..1], corrected for center bias; up is positive.
     */
    public double leftY() {
        return correct(-gp.left_stick_y, -ly0);
    }

    /**
     * Right stick X in [-1..1], corrected for center bias.
     */
    public double rightX() {
        return correct(gp.right_stick_x, rx0);
    }

    /**
     * Right stick Y in [-1..1], corrected for center bias; up is positive.
     */
    public double rightY() {
        return correct(-gp.right_stick_y, -ry0);
    }

    // ---------------------------
    // Triggers (0..1) — typically no calibration needed
    // ---------------------------

    public double leftTrigger() {
        return clamp01(gp.left_trigger);
    }

    public double rightTrigger() {
        return clamp01(gp.right_trigger);
    }

    // ---------------------------
    // Bumpers, stick buttons, meta
    // ---------------------------

    public boolean leftBumper() {
        return gp.left_bumper;
    }

    public boolean rightBumper() {
        return gp.right_bumper;
    }

    public boolean leftStickButton() {
        return gp.left_stick_button;
    }

    public boolean rightStickButton() {
        return gp.right_stick_button;
    }

    public boolean start() {
        return gp.start;
    }

    public boolean back() {
        return gp.back;
    }

    // ---------------------------
    // D-Pad
    // ---------------------------

    public boolean dpadUp() {
        return gp.dpad_up;
    }

    public boolean dpadDown() {
        return gp.dpad_down;
    }

    public boolean dpadLeft() {
        return gp.dpad_left;
    }

    public boolean dpadRight() {
        return gp.dpad_right;
    }

    // ---------------------------
    // Face buttons (Xbox + PS aliases)
    // ---------------------------

    // Xbox / Logitech labels
    public boolean a() {
        return gp.a;
    }

    public boolean b() {
        return gp.b;
    }

    public boolean x() {
        return gp.x;
    }

    public boolean y() {
        return gp.y;
    }

    // PlayStation aliases mapped to the above

    /**
     * CROSS ↔ A
     */
    public boolean cross() {
        return a();
    }

    /**
     * CIRCLE ↔ B
     */
    public boolean circle() {
        return b();
    }

    /**
     * SQUARE ↔ X
     */
    public boolean square() {
        return x();
    }

    /**
     * TRIANGLE ↔ Y
     */
    public boolean triangle() {
        return y();
    }

    // ---------------------------
    // Telemetry helper
    // ---------------------------

    /**
     * Optional: quick line to show calibration and current corrected values.
     */
    public void addTelemetry(Telemetry t, String label) {
        if (t == null) return;
        t.addLine(label);
        t.addData("lx", leftX()).addData("ly", leftY())
                .addData("rx", rightX()).addData("ry", rightY());
        t.addData("cal.lx0", lx0).addData("cal.ly0", ly0)
                .addData("cal.rx0", rx0).addData("cal.ry0", ry0);
    }

    // ---------------------------
    // Correction math
    // ---------------------------

    /**
     * Bias-correct and preserve full travel to ±1 around an offset center.
     */
    private static double correct(double raw, double offset) {
        // Avoid division by zero when stick is perfectly centered
        if (Math.abs(offset) < 1e-9) return clamp(raw);

        final double v = (raw >= offset)
                ? (raw - offset) / (1.0 - offset)
                : (raw - offset) / (1.0 + offset);
        return clamp(v);
    }

    private static double clamp(double v) {
        if (v > 1.0) return 1.0;
        if (v < -1.0) return -1.0;
        return v;
    }

    private static double clamp01(double v) {
        if (v > 1.0) return 1.0;
        if (v < 0.0) return 0.0;
        return v;
    }
}

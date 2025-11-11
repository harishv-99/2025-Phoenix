package edu.ftcphoenix.fw.input;

import com.qualcomm.robotcore.hardware.Gamepad;

import org.firstinspires.ftc.robotcore.external.Telemetry;

import java.util.Objects;

/**
 * Orchestrates two controllers and a shared {@link InputRegistry}.
 *
 * <h2>Update model</h2>
 * All RAW and DERIVED inputs (axes, buttons, virtuals) register with the same registry.
 * Call {@link #update(double)} exactly once per loop. There is NO per-device sample step.
 *
 * <h2>Calibration</h2>
 * Each {@link GamepadDevice} auto-calibrates stick centers on construction.
 * Bind a chord to {@link #p1()}{@link GamepadDevice#recalibrate() .recalibrate()} or p2() if you
 * want to re-zero mid-match.
 *
 * <h2>Typical usage</h2>
 * <pre>{@code
 * Gamepads pads = Gamepads.create(gamepad1, gamepad2);
 * DriverKit kit = DriverKit.of(pads);
 * // in loop:
 * pads.update(dt);   // RAW → DERIVED
 * bindings.update(dt);
 * }</pre>
 */
public final class Gamepads {
    private final InputRegistry registry = new InputRegistry();
    private final GamepadDevice p1;
    private final GamepadDevice p2;

    private Gamepads(Gamepad g1, Gamepad g2) {
        this.p1 = new GamepadDevice(Objects.requireNonNull(g1, "gamepad1"));
        this.p2 = new GamepadDevice(Objects.requireNonNull(g2, "gamepad2"));
        // Note: Inputs/Axis/Button register themselves with `registry` as they are created.
    }

    /**
     * Factory: build a pair of devices with a shared registry.
     */
    public static Gamepads create(Gamepad g1, Gamepad g2) {
        return new Gamepads(g1, g2);
    }

    /**
     * Primary driver (gamepad1).
     */
    public GamepadDevice p1() {
        return p1;
    }

    /**
     * Secondary operator (gamepad2).
     */
    public GamepadDevice p2() {
        return p2;
    }

    /**
     * Shared registry that updates all inputs (RAW first, then DERIVED).
     */
    public InputRegistry registry() {
        return registry;
    }

    /**
     * Advance one input tick. This calls {@link InputRegistry#updateAll(double)}.
     * <b>Do not</b> call any device-level sample method; there isn't one anymore.
     */
    public void update(double dtSec) {
        registry.updateAll(dtSec);
    }

    // ---------------------------
    // Optional debugging helpers
    // ---------------------------

    /**
     * Enable/disable double-update guardrail (logs a warning if update() seems called twice per loop).
     */
    public void setDebugGuardEnabled(boolean enabled) {
        registry.setDebugGuardEnabled(enabled);
    }

    /**
     * Tune thresholds for the debug guard (see {@link InputRegistry#configureDebugGuard(double, double)}).
     */
    public void configureDebugGuard(double epsilonDtSec, double minGapSec) {
        registry.configureDebugGuard(epsilonDtSec, minGapSec);
    }

    /**
     * Convenience telemetry for quick sanity checks.
     */
    public void addTelemetry(Telemetry t) {
        if (t == null) return;
        t.addLine("Gamepads");
        registry.addTelemetry(t);
        p1.addTelemetry(t, "p1");
        p2.addTelemetry(t, "p2");
    }
}

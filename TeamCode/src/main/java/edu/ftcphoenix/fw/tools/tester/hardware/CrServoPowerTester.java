package edu.ftcphoenix.fw.tools.tester.hardware;

import com.qualcomm.robotcore.hardware.CRServo;

import org.firstinspires.ftc.robotcore.external.Telemetry;

import java.util.Locale;

import edu.ftcphoenix.fw.tools.tester.BaseTeleOpTester;
import edu.ftcphoenix.fw.tools.tester.ui.HardwareNamePicker;
import edu.ftcphoenix.fw.tools.tester.ui.ScalarTuner;

/**
 * Generic tester for a configured {@link CRServo} that lets you vary servo power.
 *
 * <h2>Selection</h2>
 * If constructed without a name, shows an INIT picker listing configured CRServos.
 *
 * <h2>Controls (gamepad1)</h2>
 * <ul>
 *   <li><b>INIT (no device selected)</b>: Dpad Up/Down select, A choose, B refresh</li>
 *   <li><b>RUN (device selected)</b>:
 *     <ul>
 *       <li>A: enable/disable output</li>
 *       <li>X: invert</li>
 *       <li>START: fine/coarse step</li>
 *       <li>Dpad Up/Down: step power</li>
 *       <li>Left stick Y: live override (sets target while moved)</li>
 *       <li>B: zero</li>
 *     </ul>
 *   </li>
 * </ul>
 *
 * <p><b>Safety:</b> power is set to 0 on stop.</p>
 */
public final class CrServoPowerTester extends BaseTeleOpTester {

    private final String preferredName;

    private HardwareNamePicker picker;

    private String servoName = null;
    private CRServo servo = null;

    private boolean ready = false;
    private String resolveError = null;

    private final ScalarTuner power =
            new ScalarTuner("Power", -1.0, +1.0, 0.05, 0.20, 0.0);

    public CrServoPowerTester() {
        this(null);
    }

    public CrServoPowerTester(String servoName) {
        this.preferredName = servoName;
    }

    @Override
    public String name() {
        return "CRServo Power Tester";
    }

    @Override
    protected void onInit() {
        picker = new HardwareNamePicker(
                ctx.hw,
                CRServo.class,
                "Select CRServo",
                "Dpad: select | A: choose | B: refresh"
        );
        picker.refresh();

        power.attachAxis(gamepads.p1().leftY(), 0.08, v -> v);

        if (preferredName != null && !preferredName.trim().isEmpty()) {
            servoName = preferredName.trim();
            tryResolve(servoName);
        }

        picker.bind(
                bindings,
                gamepads.p1().dpadUp(),
                gamepads.p1().dpadDown(),
                gamepads.p1().a(),
                gamepads.p1().b(),
                () -> !ready,
                chosen -> {
                    servoName = chosen;
                    tryResolve(servoName);
                }
        );

        power.bind(
                bindings,
                gamepads.p1().a(),        // enable
                gamepads.p1().x(),        // invert
                gamepads.p1().start(),    // fine/coarse
                gamepads.p1().dpadUp(),   // inc
                gamepads.p1().dpadDown(), // dec
                gamepads.p1().b(),        // zero
                () -> ready
        );
    }

    @Override
    protected void onInitLoop(double dtSec) {
        if (!ready) {
            renderPicker();
            return;
        }
        updateAndRender();
    }

    @Override
    protected void onLoop(double dtSec) {
        if (!ready) {
            renderPicker();
            return;
        }
        updateAndRender();
    }

    @Override
    protected void onStop() {
        applyPower(0.0);
    }

    // ---------------------------------------------------------------------------------------------
    // Internals
    // ---------------------------------------------------------------------------------------------

    private void tryResolve(String name) {
        resolveError = null;
        try {
            servo = ctx.hw.get(CRServo.class, name);
            ready = true;
        } catch (Exception ex) {
            servo = null;
            ready = false;
            resolveError = ex.getClass().getSimpleName() + ": " + ex.getMessage();
        }
    }

    private void updateAndRender() {
        power.updateFromAxis(() -> ready);

        double applied = power.applied();
        applyPower(applied);

        renderTelemetry(applied);
    }

    private void applyPower(double pwr) {
        if (servo == null) return;
        servo.setPower(pwr);
    }

    private void renderPicker() {
        Telemetry t = ctx.telemetry;
        t.clearAll();

        picker.render(t);

        if (servoName != null && !servoName.isEmpty()) {
            t.addLine("");
            t.addLine("Chosen: " + servoName);
        }

        if (resolveError != null) {
            t.addLine("");
            t.addLine("Resolve error:");
            t.addLine(resolveError);
        }

        t.update();
    }

    private void renderTelemetry(double appliedPower) {
        Telemetry t = ctx.telemetry;
        t.clearAll();

        t.addLine("=== CRServo Power Tester ===");
        t.addLine("CRServo: " + servoName);

        power.render(t);

        t.addLine("");
        t.addLine("Controls: A enable | X invert | START step | dpad +/- | stickY override | B zero");

        if (servo != null) {
            try {
                t.addLine(String.format(Locale.US, "Applied=%.2f", appliedPower));
            } catch (Exception ignored) {
            }
        }

        t.update();
    }
}

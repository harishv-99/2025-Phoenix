package edu.ftcphoenix.fw.input.examples;

import com.qualcomm.robotcore.eventloop.opmode.OpMode;
import com.qualcomm.robotcore.eventloop.opmode.TeleOp;

import edu.ftcphoenix.fw.drive.source.GamepadDriveSource;
import edu.ftcphoenix.fw.input.Axis;
import edu.ftcphoenix.fw.input.Button;
import edu.ftcphoenix.fw.input.DriverKit;
import edu.ftcphoenix.fw.input.Gamepads;
import edu.ftcphoenix.fw.input.InputDefaults;
import edu.ftcphoenix.fw.input.InputRegistry;
import edu.ftcphoenix.fw.input.Inputs;
import edu.ftcphoenix.fw.input.binding.Bindings;
import edu.ftcphoenix.fw.input.extras.Combos;
import edu.ftcphoenix.fw.input.extras.GesturePresets;
import edu.ftcphoenix.fw.input.extras.InputDebug;

/**
 * Phoenix: Comprehensive TeleOp Example
 *
 * <h2>What it demonstrates</h2>
 * <ul>
 *   <li>DriverKit (pre-shaped axes, PS aliases, built-in gestures)</li>
 *   <li>Bindings (press/release/toggle/whileHeld, double-tap, long-hold)</li>
 *   <li>Combos & chords (AND, XOR, time-based chord) across players</li>
 *   <li>Virtual inputs (buttons→axis, triggers→axis, axis→DPAD via hysteresis)</li>
 *   <li>Drive mapping using GamepadDriveSource (x, y, omega) with slow-mode scaling</li>
 *   <li>Optional debug guard (detect double update), and compact input telemetry</li>
 * </ul>
 *
 * <h2>Loop order</h2>
 * dt → pads.update(dt) → bind.update(dt) → drive/sample → telemetry.update()
 * <p>
 * Replace the stubs at the bottom with your real subsystems.
 */
@TeleOp(name = "Phoenix: Comprehensive TeleOp", group = "Phoenix")
public final class ExampleTeleOpBindings extends OpMode {

    // --- Framework objects ---
    private Gamepads pads;
    private DriverKit kit;
    private Bindings bind;
    private InputRegistry reg;

    // --- Drive mapping ---
    private GamepadDriveSource driveMap;

    // --- Timing for dt ---
    private long lastNs;

    // --- Subsystem stubs (replace with your real implementations) ---
    private final Drive drive = new Drive();
    private final Shooter shooter = new Shooter();
    private final Intake intake = new Intake();
    private final Lift lift = new Lift();

    @Override
    public void init() {
        // Optional: tune global defaults for THIS robot (coexistence-safe DT/LH, etc.)
        InputDefaults tuned = InputDefaults.create()
                .deadband(0.06)                // a touch more deadband
                .expo(2.0)                     // classic "square"
                .slew(9.0)                     // slightly snappier lateral/omega
                .hysteresis(0.14, 0.24)        // dpad split thresholds
                // gesture guidance (DT < LH, with suppression policy)
                .doubleTapWindowSec(0.30)      // single-gesture DT (used where only DT applies)
                .longHoldSec(0.30)             // single-gesture LH
                .coexistDoubleTapSec(0.22)     // when both gestures are on one button
                .coexistLongHoldSec(0.40);

        // Construct inputs with tuned defaults (or use DriverKit.of(pads) for standard())
        pads = Gamepads.create(gamepad1, gamepad2);
        kit = DriverKit.of(pads, tuned);
        bind = new Bindings();
        reg = pads.registry();

        // (Optional) Guardrail: warn if updateAll() is accidentally called more than once per loop
        // reg.setDebugGuardEnabled(true);

        // ---------------------------
        // Drive axes & mapping
        // ---------------------------
        Axis lx = kit.p1().lx(); // deadband+expo+slew already applied
        Axis ly = kit.p1().ly(); // deadband+expo
        Axis rx = kit.p1().rx(); // deadband+expo+slew

        // Slow rotation while LB long-held (50% scale)
        java.util.function.Supplier<Double> omegaScale = kit.p1().slowModeScale(0.5);

        driveMap = new GamepadDriveSource(lx, ly, rx)
                .maxOmega(2.5)
                .omegaScale(omegaScale);

        // ---------------------------
        // Buttons & gestures
        // ---------------------------
        // PS aliases available alongside A/B/X/Y
        Button cross = kit.p1().cross();    // == A
        Button circle = kit.p1().circle();   // == B
        Button square = kit.p1().square();   // == X
        Button triangle = kit.p1().triangle(); // == Y

        Button lb = kit.p1().lb(); // preconfigured long-hold via DriverKit defaults
        Button rb = kit.p1().rb(); // preconfigured double-tap via DriverKit defaults

        // Example of applying gesture presets yourself (coexistence-safe) to another button:
        Button rStickBtn = kit.p1().rightStickButton();
        GesturePresets.doubleTapAndHold(tuned, rStickBtn);

        // Recalibration chord: BACK + START within 0.30 s
        Button back = kit.p1().back();
        Button start = kit.p1().start();
        Button recalibChord = edu.ftcphoenix.fw.input.extras.Combos.chordSec(reg, 0.30, back, start);

        // A pure AND combo (hold both): p2 LB + p2 RB
        Button p2lb = kit.p2().lb();
        Button p2rb = kit.p2().rb();
        Button p2BothHeld = Combos.all(reg, p2lb, p2rb);

        // XOR combo: exactly one of (B or Y) to toggle crawl
        Button crawlXor = Combos.xor(reg, circle, triangle);

        // ---------------------------
        // Virtual inputs (buttons <-> axes)
        // ---------------------------
        // Operator lift power from two buttons: p2 DPAD Down (-1) / p2 DPAD Up (+1)
        Button p2Up = kit.p2().dpadUp();
        Button p2Down = kit.p2().dpadDown();
        Axis liftAxis = Inputs.axisFromButtons(reg, p2Down, p2Up).rateLimit(2.5);

        // Turn a button into a trigger axis [0..1] for intake throttle (square as throttle)
        Axis intakeThrottle = Inputs.triggerFromButton(reg, square);

        // Derive a DPAD from p1 left stick (default hysteresis) for discrete nudges
        Inputs.Dpad dpad = kit.p1().dpad();

        // Split right stick Y into up/down buttons with explicit hysteresis for another use-case
        Inputs.AxisSplit rySplit = Inputs.splitAxisToButtons(reg, kit.p1().ry(), tuned.hystLow, tuned.hystHigh);

        // A custom virtual axis: triggers → differential (-1..1): LT negative, RT positive
        Axis tAxis = Inputs.axisFromTriggers(reg, kit.p1().lt(), kit.p1().rt());

        // ---------------------------
        // Bindings (actions)
        // ---------------------------

        // Shooter
        bind.onPress(kit.p1().chordLB_A(), shooter::fireOnce); // safety chord (LB + A within default window)
        bind.onDoubleTap(rb, shooter::burst);                  // RB double-tap
        bind.toggle(triangle, shooter::setEnabled);            // TRIANGLE toggles shooter enable
        bind.stream(kit.p1().rt(), shooter::setTargetRpmFromTrigger); // RT [0..1] → RPM

        // Intake
        bind.whileHeld(cross, () -> intake.setPower(1.0), () -> intake.setPower(0.0));  // A/CROSS
        bind.whileHeld(lb, () -> intake.setPower(-0.8), () -> intake.setPower(0.0)); // reverse while LB pressed
        // Use intakeThrottle axis to scale intake when RB is held; otherwise stop
        bind.streamWhile(intakeThrottle, rb::isDown, intake::setPower, () -> intake.setPower(0.0));

        // Lift
        bind.stream(liftAxis, lift::setPower); // p2 DPAD Up/Down into axis
        // Discrete nudges using derived DPAD from p1 left stick
        bind.onPress(dpad.up, lift::nudgeUp);
        bind.onPress(dpad.down, lift::nudgeDown);

        // Drive crawl mode via XOR (exactly one of B/Y)
        bind.toggle(crawlXor, drive::setCrawlEnabled);

        // Map differential trigger axis to a precision offset for axial (example)
        bind.streamScaled(tAxis, () -> 0.3, v -> drive.addAxialOffset(v)); // +/- 0.3 * tAxis

        // Recalibrate sticks via chord
        bind.onPress(recalibChord, pads.p1()::recalibrate);

        // Example: right stick button (DT+LH coexistence) - DT toggles align mode, LH triggers auto-align
        bind.onDoubleTap(rStickBtn, drive::toggleAlignMode);
        bind.onLongHoldStart(rStickBtn, drive::startAutoAlign);

        lastNs = System.nanoTime();
        telemetry.addLine("Comprehensive TeleOp: init complete");
    }

    @Override
    public void loop() {
        // dt
        long now = System.nanoTime();
        double dtSec = (now - lastNs) / 1e9;
        lastNs = now;

        // Update inputs → run bindings
        pads.update(dtSec);
        bind.update(dtSec);

        // Drive command
        GamepadDriveSource.ChassisCommand cmd = driveMap.sample();
        drive.setLateral(cmd.x);
        drive.setAxial(cmd.y);
        drive.setOmega(cmd.omega);

        // Telemetry (compact; uncomment extra debug as needed)
        kit.addTelemetry(telemetry);
        // InputDebug.axis(telemetry, "p1.lx", kit.p1().lx());
        // InputDebug.axis(telemetry, "p1.ly", kit.p1().ly());
        // InputDebug.button(telemetry, "LB+A chord", kit.p1().chordLB_A());

        telemetry.addData("drive.lat", drive.lat);
        telemetry.addData("drive.ax", drive.ax);
        telemetry.addData("drive.om", drive.om);
        telemetry.addData("drive.crawl", drive.crawl);
        telemetry.addData("shooter.enabled", shooter.enabled);
        telemetry.addData("intake.power", intake.power);
        telemetry.addData("lift.power", lift.power);
        telemetry.update();
    }

    // =====================================================================
    // Subsystem stubs for demo (replace with your real code)
    // =====================================================================

    private static final class Drive {
        double lat, ax, om;
        boolean crawl = false;
        boolean alignMode = false;

        void setLateral(double v) {
            lat = clamp(v);
        }

        void setAxial(double v) {
            ax = clamp(v);
        }

        void setOmega(double v) {
            om = clamp(v);
        }

        void addAxialOffset(double v) {
            ax = clamp(ax + v);
        }

        void setCrawlEnabled(boolean on) {
            crawl = on;
        }

        void toggleAlignMode() {
            alignMode = !alignMode;
        }

        void startAutoAlign() { /* kick off vision/heading align routine */ }

        private static double clamp(double v) {
            return Math.max(-1.0, Math.min(1.0, v));
        }
    }

    private static final class Shooter {
        boolean enabled;
        double targetRpm;

        void fireOnce() {/* single shot */}

        void burst() {/* short burst */}

        void setEnabled(boolean on) {
            enabled = on;
        }

        void setTargetRpmFromTrigger(double t) {
            targetRpm = t * 5000.0;
        }
    }

    private static final class Intake {
        double power;

        void setPower(double p) {
            power = Math.max(-1.0, Math.min(1.0, p));
        }
    }

    private static final class Lift {
        double power;

        void setPower(double p) {
            power = Math.max(-1.0, Math.min(1.0, p));
        }

        void nudgeUp() {/* small positive step */}

        void nudgeDown() {/* small negative step */}
    }
}

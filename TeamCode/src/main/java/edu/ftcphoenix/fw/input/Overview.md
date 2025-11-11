# Phoenix Gamepad: Happy Path Recipe

This gives you a copy‑pasteable quickstart plus a full TeleOp showing the **simple path**:
- `Gamepads` + `DriverKit` for shaped axes and PS aliases
- `Bindings` for one‑liners (press, toggle, whileHeld, double‑tap, long‑hold)
- `GamepadDriveSource` to map shaped axes → (x, y, omega)

Packages used:
- `edu.ftcphoenix.fw.input` — `Gamepads`, `InputRegistry`, `DriverKit`, `Axis`, `Button`, `InputDefaults`
- `edu.ftcphoenix.fw.input.binding` — `Bindings`
- `edu.ftcphoenix.fw.input.extras` — `Combos`, `InputDebug`, `GesturePresets`
- `edu.ftcphoenix.fw.drive.source` — `GamepadDriveSource`

---

## TL;DR: Quickstart (≈10 lines)
```java
Gamepads pads = Gamepads.create(gamepad1, gamepad2);
DriverKit kit = DriverKit.of(pads);                    // deadband/expo/slew/gestures preset
Bindings bind = new Bindings();

// Drive: scale omega while LB long-held
var mapper = new GamepadDriveSource(kit.p1().lx(), kit.p1().ly(), kit.p1().rx())
        .maxOmega(2.5)
        .omegaScale(kit.p1().slowModeScale(0.5));

// Shooter/intake/lift examples
bind.onPress(kit.p1().chordLB_A(), shooter::fireOnce);        // safety chord
bind.onDoubleTap(kit.p1().rb(), shooter::burst);               // RB double-tap
bind.toggle(kit.p1().triangle(), shooter::setEnabled);         // PS alias
bind.whileHeld(kit.p1().cross(), () -> intake.setPower(1),     // A/CROSS held
                                 () -> intake.setPower(0));
bind.stream(kit.p2().dpadYAxis(), lift::setPower);             // DPAD from stick

// loop:
// pads.update(dt); bind.update(dt); var cmd = mapper.sample(); drive.accept(cmd);
```

---

## Recipe (step‑by‑step)
1) **Construct the inputs** once in `init()`:
    - `Gamepads.create(gamepad1, gamepad2)`
    - `DriverKit.of(pads)` → shaped axes, PS aliases, preconfigured gestures (LB long‑hold, RB double‑tap)
2) **Bindings**: declare what should happen on edges or while held:
    - `onPress`, `onRelease`, `toggle`, `whileHeld`, `onDoubleTap`, `onLongHoldStart`, `whileLongHeld`, `stream`, `streamScaled`
3) **Drive mapping** (optional helper):
    - `GamepadDriveSource(lx, ly, rx).maxOmega(2.5).omegaScale(kit.p1().slowModeScale(0.5))`
    - In `loop()`: `cmd = mapper.sample()` → send `cmd.x/cmd.y/cmd.omega` to your drive
4) **Loop order (important):**
    - Compute `dt`
    - `pads.update(dt)` (updates RAW then DERIVED inputs)
    - `bind.update(dt)` (runs your actions)
    - Drive control + telemetry
5) **Gesture coexistence best‑practice** is already encoded in defaults:
    - If a button uses both DT+LH, defaults aim for **DT < LH** and policy **LONG_HOLD_SUPPRESSES_DOUBLE_TAP**
    - For one‑liners, you can also use `GesturePresets.doubleTapAndHold(btn)` if needed

---

## New TeleOp Example — Minimal Happy Path
Paste this entire file.

```java
package edu.ftcphoenix.fw.input.examples;

import com.qualcomm.robotcore.eventloop.opmode.OpMode;
import com.qualcomm.robotcore.eventloop.opmode.TeleOp;

import edu.ftcphoenix.fw.drive.source.GamepadDriveSource;
import edu.ftcphoenix.fw.input.DriverKit;
import edu.ftcphoenix.fw.input.Gamepads;
import edu.ftcphoenix.fw.input.InputRegistry;
import edu.ftcphoenix.fw.input.binding.Bindings;

/**
 * Phoenix: Happy Path TeleOp
 *
 * Shows the simplest, opinionated setup using DriverKit + Bindings + GamepadDriveSource.
 * Includes PS aliases and safety chord (LB + A within default window), RB double-tap, and
 * DPAD derived from stick with default hysteresis.
 */
@TeleOp(name = "Phoenix: Happy Path TeleOp", group = "Phoenix")
public final class HappyPathTeleOp extends OpMode {

    // Framework objects
    private Gamepads pads;
    private DriverKit kit;
    private Bindings bind;
    private InputRegistry reg;

    // Drive mapper
    private GamepadDriveSource mapper;

    // Subsystem stubs (replace with your real subsystems)
    private final Drive drive = new Drive();
    private final Shooter shooter = new Shooter();
    private final Intake intake = new Intake();
    private final Lift lift = new Lift();

    // dt tracking
    private long lastNs;

    @Override
    public void init() {
        pads = Gamepads.create(gamepad1, gamepad2);
        kit  = DriverKit.of(pads);        // uses InputDefaults.standard()
        bind = new Bindings();
        reg  = pads.registry();

        // Drive: shaped axes from kit, slow omega while LB long-held
        mapper = new GamepadDriveSource(kit.p1().lx(), kit.p1().ly(), kit.p1().rx())
                .maxOmega(2.5)
                .omegaScale(kit.p1().slowModeScale(0.5));

        // Shooter
        bind.onPress(kit.p1().chordLB_A(), shooter::fireOnce);   // safety chord
        bind.onDoubleTap(kit.p1().rb(), shooter::burst);          // RB double-tap (defaults)
        bind.toggle(kit.p1().triangle(), shooter::setEnabled);    // PS alias
        bind.stream(kit.p1().rt(), shooter::setTargetRpmFromTrigger); // RT [0..1] → RPM

        // Intake (A/CROSS while-held)
        bind.whileHeld(kit.p1().cross(), () -> intake.setPower(1.0), () -> intake.setPower(0.0));

        // Lift (operator): DPAD-like Y derived from stick
        bind.stream(kit.p2().dpadYAxis(), lift::setPower);

        lastNs = System.nanoTime();
        telemetry.addLine("Happy Path: init complete");
    }

    @Override
    public void loop() {
        long now = System.nanoTime();
        double dt = (now - lastNs) / 1e9;
        lastNs = now;

        // Update inputs then run bindings
        pads.update(dt);
        bind.update(dt);

        // Drive command
        GamepadDriveSource.ChassisCommand cmd = mapper.sample();
        drive.setLateral(cmd.x);
        drive.setAxial(cmd.y);
        drive.setOmega(cmd.omega);

        // Telemetry (quick sanity)
        kit.addTelemetry(telemetry);
        telemetry.addData("drive.lat", drive.lat);
        telemetry.addData("drive.ax", drive.ax);
        telemetry.addData("drive.om", drive.om);
        telemetry.addData("shooter.enabled", shooter.enabled);
        telemetry.addData("intake.power", intake.power);
        telemetry.update();
    }

    // ---------------------------
    // Minimal subsystem stubs
    // ---------------------------
    private static final class Drive {
        double lat, ax, om;
        void setLateral(double v) { lat = clamp(v); }
        void setAxial(double v)   { ax = clamp(v); }
        void setOmega(double v)   { om = clamp(v); }
        private static double clamp(double v) { return Math.max(-1.0, Math.min(1.0, v)); }
    }

    private static final class Shooter {
        boolean enabled;
        double targetRpm;
        void fireOnce() { /* trigger one shot */ }
        void burst() { /* short burst */ }
        void setEnabled(boolean on) { enabled = on; }
        void setTargetRpmFromTrigger(double t) { targetRpm = t * 5000.0; }
    }

    private static final class Intake {
        double power;
        void setPower(double p) { power = Math.max(-1.0, Math.min(1.0, p)); }
    }

    private static final class Lift {
        double power;
        void setPower(double p) { power = Math.max(-1.0, Math.min(1.0, p)); }
    }
}
```

---

## Notes / Options
- If you want update guardrails, enable once during bring‑up:
  ```java
  pads.registry().setDebugGuardEnabled(true);
  ```
- To tune defaults robot‑wide:
  ```java
  InputDefaults tuned = InputDefaults.create()
      .deadband(0.07).expo(2.2).slew(10.0)
      .hysteresis(0.12, 0.22)
      .doubleTapWindowSec(0.22).longHoldSec(0.40)
      .coexistDoubleTapSec(0.22).coexistLongHoldSec(0.40)
      .coexistPolicy(Button.GesturePolicy.LONG_HOLD_SUPPRESSES_DOUBLE_TAP);
  DriverKit kit = DriverKit.of(pads, tuned);
  ```
- For extra debugging, you can add:
  ```java
  // InputDebug.axis(telemetry, "p1.lx", kit.p1().lx());
  // InputDebug.button(telemetry, "chordLB_A", kit.p1().chordLB_A());
  ```


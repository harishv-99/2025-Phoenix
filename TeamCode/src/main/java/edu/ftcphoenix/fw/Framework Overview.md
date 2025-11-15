# Phoenix FTC Framework Overview

Welcome! This document is a guided tour of the Phoenix FTC framework and how you use it to build clear, maintainable robot code.

The overarching goal:

> **Make robot-specific code as simple and readable as possible, while the framework handles the wiring, control patterns, and boilerplate.**

“Students write the *story* of what the robot does; the framework handles the plumbing.”

---

## 1. Design Principles (high-level)

(See **`Framework Principles.md`** for the full list. This is the short version you should keep in your head.)

1. **Simplicity of robot-specific code is paramount**

    * TeleOp / Auton code should be readable by a new student.
    * OpModes should describe *behavior*, not motor math.
    * Repeated patterns (drive, intake, shooter, vision, etc.) are hidden behind helpers.

2. **Clear layering and separation of concerns**

    * FTC SDK (`HardwareMap`, `Gamepad`, `DcMotorEx`, `Servo`) is wrapped by a small HAL.
    * Adapters convert SDK → framework interfaces.
    * “Logic” code talks to simple interfaces (`DriveSource`, `Task`, `SetpointStage.Plant`, `AprilTagSensor`), not raw SDK classes.

3. **Beginner-friendly path, with an advanced “escape hatch”**

   For almost every feature there are two tiers:

    * **Beginner path:** 1–2 obvious helper calls, minimal types.
    * **Advanced path:** more control, explicit wiring, access to the underlying interfaces.

   If something feels too complex for beginners, we add a helper instead of pushing complexity into OpModes.

4. **Consistency over cleverness**

    * Naming and patterns are consistent across packages.
    * Helpers follow similar shapes (`of(...)`, `defaultXxx(...)`, `forTeleOp(...)`, `mecanum(...).build()`, etc.).
    * The AprilTag pieces follow **the same patterns** as drive and input.

---

## 2. Package Map (what lives where)

This section tells you:

* What each package is for.
* Which parts are **beginner** vs **advanced**.

### 2.1 `fw.hal` – Hardware Abstraction Layer

**Main types**

* `MotorOutput` – “thing that accepts a normalized power in [-1, 1]”.
* `ServoOutput` – “thing that accepts a normalized position in [0, 1]”.

**Who uses this?**

* **Beginners:** almost never directly.
* **Advanced:** when constructing custom mechanisms that aren’t covered by `Plants` / `Drives`.

---

### 2.2 `fw.adapters.ftc` – FTC SDK adapters

**Main types**

* `FtcHardware`

    * `motor(hardwareMap, name, inverted)` → `MotorOutput`
    * `servo(hardwareMap, name, inverted)` → `ServoOutput`
* (Vision) `FtcVision` (adapter that creates the underlying VisionPortal / AprilTagProcessor and exposes an `AprilTagSensor`).

**Usage level**

* **Beginner:** do not use directly. Use `Drives` (for motors) and `Tags` (for AprilTags).
* **Advanced:** use when you need custom wiring from SDK hardware to HAL interfaces.

---

### 2.3 `fw.adapters.plants` – Mechanism “plants”

**Main entry point**

* `Plants` – static helpers that create `SetpointStage.Plant` implementations from hardware.

Examples:

* `Plants.power(hw, "intake", false)` – open-loop power plant.
* `Plants.velocity(hw, "shooter", ticksPerRev, inverted)` – velocity plant (rad/s → ticks/s).
* `Plants.servoPosition(hw, "pusher", false)` – positional servo plant [0, 1].
* `Plants.powerPair(...)`, `Plants.velocityPair(...)`, `Plants.servoPositionPair(...)`, `Plants.motorPositionPair(...)` – dual-output variants.

**Usage level**

* **Beginner:**

    * Can use these helpers from examples (“wire my shooter/intake”).
    * Don’t need to know about the underlying `SetpointStage.Plant` interface.
* **Advanced:**

    * Use them with `SetpointStage` to build staged subsystems (e.g., staged shooter, buffer, arm).

---

### 2.4 `fw.drive` – Drivebases and drive signals

**Main types**

* `DriveSignal` – immutable (axial, lateral, omega) command.
* `DriveSource` – interface that produces `DriveSignal` given a `LoopClock`.
* `MecanumDrivebase` – simple open-loop 4-wheel mecanum mixer.
* `MecanumConfig` – configuration for scaling drive commands.
* `Drives` – builder helpers for creating drivebases from `HardwareMap`:

  ```java
  drivebase = Drives
      .mecanum(hardwareMap)
      .frontLeft("fl")
      .frontRight("fr")
      .backLeft("bl")
      .backRight("br")
      .invertRightSide()   // typical FRC/FTC wiring
      .build();
  ```

**Usage level**

* **Beginner:**

    * Use `Drives.mecanum(...)` to build your drivebase.
    * Treat `MecanumDrivebase` as a type you pass a `DriveSignal` into.
* **Advanced:**

    * Use `MecanumConfig` to tune scaling.
    * Implement your own `DriveSource` if you want custom control mapping.

---

### 2.5 `fw.drive.source` – Ways to produce drive signals

**Main types**

* `StickDriveSource`

    * Turns gamepad sticks into a `DriveSignal`.
    * Handles deadband, expo, scaling, and optional slow mode.

**Usage level**

* **Beginner:**

    * Use `StickDriveSource.defaultMecanum(driverKit)` or
      `StickDriveSource.defaultMecanumWithSlowMode(...)`.
    * Use `TagAim.forTeleOp(...)` (see `fw.sensing`) to get a wrapped `DriveSource` that auto-aims.
* **Advanced:**

    * Build your own `DriveSource` chain (e.g., apply rate limiters, feedforward, or custom auto controllers).

---

### 2.6 `fw.input` – Gamepads, axes, buttons, bindings

**Main types**

* `Gamepads` – wraps `gamepad1`/`gamepad2` into `GamepadDevice`s.

* `GamepadDevice` – handles stick bias calibration and exposes:

    * `leftX()`, `leftY()`, `rightX()`, `rightY()`, `leftTrigger()`, `rightTrigger()`, etc.

* `Axis` – functional wrapper for “read a double”.

* `Button` – functional wrapper for “read a boolean”.

* `DriverKit` – friendly names for “player 1” / “player 2”:

  ```java
  Gamepads pads = Gamepads.create(gamepad1, gamepad2);
  DriverKit driver = DriverKit.of(pads);

  driver.p1().leftX();
  driver.p1().rightBumper();
  driver.p2().a();
  ```

* `input.binding.Bindings` – helper to declare simple button→action mappings (used more in staged/advanced examples).

**Usage level**

* **Beginner:**

    * Mostly interact with `DriverKit` and `StickDriveSource`.
    * May use `Bindings` for simple on/off mechanisms (toggle shooter, etc.).
* **Advanced:**

    * Use `Axis`/`Button` explicitly and compose more complex bindings or input logic.

---

### 2.7 `fw.robot` – Base classes for OpModes

**Main types**

* `PhoenixTeleOpBase`

    * Wires `Gamepads`, `DriverKit`, `Bindings`, `LoopClock`.
    * Provides `onInitRobot()` and `loopRobot(double dtSec)` hooks.

* `PhoenixAutoBase`

    * Similar wiring, plus a `TaskRunner` to run `Task` graphs for autonomous.

**Usage level**

* **Beginner:**

    * Start with the simpler example OpModes that extend `OpMode` directly.
    * Move to `PhoenixTeleOpBase` once you’re comfortable and want less boilerplate.
* **Advanced:**

    * Use `PhoenixTeleOpBase` / `PhoenixAutoBase` everywhere so all your OpModes share the same structure.

---

### 2.8 `fw.stage` – Stages (buffer, setpoint, etc.)

**Main types**

* `stage.setpoint.SetpointStage`

    * Generic “setpoint controller” wrapping a `Plant` and any controller logic you want.
* `stage.buffer.BufferStage`

    * Higher-level behavior for FRC/FTC-style feeders/buffers.

**Usage level**

* **Beginner:**

    * You’ll see these in examples as “subsystem stages” (e.g., shooter, buffer).
    * You don’t have to implement them yourself right away.
* **Advanced:**

    * Use stages to build reusable subsystems with clear start/stop/goal semantics.

---

### 2.9 `fw.task` – Autonomous task graphs

**Main types**

* `Task` – basic interface (something that can be updated until it completes).
* `TaskRunner` – ticks a `Task` tree each loop.
* `SequenceTask`, `ParallelAllTask`, `InstantTask`, `WaitUntilTask` – building blocks.

**Usage level**

* **Beginner:**

    * You can copy a template auton that uses tasks and tweak distances/targets.
* **Advanced:**

    * Express autonomous routines as task graphs instead of giant state machines.

---

### 2.10 `fw.util` – Utilities

**Main types**

* `LoopClock` – tracks loop time (`dtSec`).
* `Units` – simple unit conversions (ticks→meters, etc.).
* `MathUtil` – central math helpers:

    * `clamp`, `clampAbs`, `clamp01`
    * `deadband`

**Usage level**

* **Beginner:**

    * Mostly encounter `LoopClock` indirectly through `DriveSource` / `PhoenixTeleOpBase`.
* **Advanced:**

    * Use `MathUtil` and `LoopClock` when writing controllers and new subsystems.

---

### 2.11 `fw.sensing` – AprilTags and aiming (vision)

> **New layer added for AprilTag support, following the same principles as drive and input.**

**Main types**

* `AprilTagSensor`

    * Interface that provides filtered AprilTag info.
    * Core method you’ll use:

      ```java
      AprilTagObservation obs = sensor.best(idsOfInterest, maxAgeSec);
      if (obs.hasTarget) {
          int id = obs.id;
          double rangeIn = obs.rangeInches;
          double bearingDeg = Math.toDegrees(obs.bearingRad);
      }
      ```

* `AprilTagObservation`

    * `boolean hasTarget`
    * `int id`
    * `double rangeInches`
    * `double bearingRad`
    * `double ageSec`

* `Tags`

    * Beginner-friendly factory:

      ```java
      AprilTagSensor tags = Tags.aprilTags(hardwareMap, "Webcam 1");
      ```

    * Internally uses `FtcVision` and VisionPortal.

* `TagAimController`

    * Uses an `AprilTagSensor` + `PidController` to turn *bearing error* into an omega command.
    * `double update(double dtSec)` – main method.
    * Uses `MathUtil.deadband`, clamping, and tag freshness (`maxTagAgeSec`).

* `TagAim`

    * Simple facades for beginners:

      ```java
      // TeleOp helper: wrap an existing drive source
      DriveSource drive = TagAim.forTeleOp(
              sticks,
              driverKit.p1().leftBumper(),  // button that enables aim
              tags,
              Set.of(1, 2, 3)               // IDs of interest
      );
  
      // Advanced: just get the controller and use omega yourself
      TagAimController aim = TagAim.controllerWithDefaults(tags, Set.of(1, 2, 3));
      ```

**Usage level**

* **Beginner:**

    * Use `Tags.aprilTags(...)` to get a sensor.
    * Use `TagAim.forTeleOp(...)` to auto-aim when a button is held.
    * Read `AprilTagObservation` for ID and distance when you need shooter tuning.
* **Advanced:**

    * Use `TagAimController` directly in autonomous to blend aiming with path-following.
    * Customize PID gains, deadband, `maxTagAgeSec`, and `maxAbsOmega`.

---

### 2.12 `fw.core` (optional, small control layer)

If present in your project, this package contains generic control logic, such as:

* `PidController` – interface for “given error + dt, return correction”.
* `Pid` – concrete implementation with clamp options and D filtering.

**Usage level**

* **Beginner:**

    * Don’t use directly; rely on higher-level helpers (`TagAimController`, staged subsystems).
* **Advanced:**

    * Use when writing your own controllers (e.g., heading hold, shooter velocity control).

---

### 2.13 `fw.examples`

Fully-working reference OpModes showing different levels:

* `TeleOpMecanumBasic` – the minimal drive path: Gamepads → DriverKit → StickDriveSource → MecanumDrivebase.
* `TeleOpSimple` / `TeleOpStaged` – introduce mechanisms, bindings, and stages.
* `TeleOpMecanumTagAim` (once added) – shows how to wire AprilTags + TagAim in TeleOp.
* Autonomous examples – using `TaskRunner` + `Task` graphs.

Use these as starting templates and as “live documentation”.

---

## 3. Beginner Quickstart

This section assumes:

* You have a mecanum robot (4 drive motors).
* You have a webcam configured (for AprilTags).
* You want a simple TeleOp with:

    * Stick driving.
    * Optional “slow mode”.
    * Optional “hold a button to auto-aim at a scoring AprilTag”.

### 3.1 Basic mecanum TeleOp (no AprilTags yet)

Minimal pattern using the raw SDK `OpMode`:

```java
@TeleOp(name = "MyRobot: Basic Drive", group = "Phoenix")
public final class MyBasicTeleOp extends OpMode {

    private Gamepads gamepads;
    private DriverKit driverKit;
    private MecanumDrivebase drivebase;
    private StickDriveSource drive;
    private final LoopClock clock = new LoopClock();

    @Override
    public void init() {
        gamepads = Gamepads.create(gamepad1, gamepad2);
        driverKit = DriverKit.of(gamepads);

        drivebase = Drives
                .mecanum(hardwareMap)
                .frontLeft("fl")
                .frontRight("fr")
                .backLeft("bl")
                .backRight("br")
                .invertRightSide()   // typical wiring; flip if your robot is different
                .build();

        drive = StickDriveSource.defaultMecanum(driverKit);

        telemetry.addLine("Init complete");
        telemetry.update();
    }

    @Override
    public void start() {
        clock.reset(getRuntime());
    }

    @Override
    public void loop() {
        clock.update(getRuntime());
        double dtSec = clock.dtSec();

        gamepads.update(dtSec);

        DriveSignal cmd = drive.get(clock);
        drivebase.drive(cmd);

        telemetry.addData("axial", cmd.axial);
        telemetry.addData("lateral", cmd.lateral);
        telemetry.addData("omega", cmd.omega);
        telemetry.update();
    }

    @Override
    public void stop() {
        drivebase.stop();
    }
}
```

That’s the **minimum stack** most teams should start from.

---

### 3.2 Add slow-mode driving

Swap the drive construction for:

```java
drive = StickDriveSource.defaultMecanumWithSlowMode(
        driverKit,
        driverKit.p1().rightBumper(), // button for slow mode
        0.30                          // 30% speed when held
);
```

Everything else remains the same.

---

### 3.3 Add AprilTags & auto-aim in TeleOp

Now we add:

* A webcam in the FTC configuration (`"Webcam 1"`).
* A set of tag IDs we care about (e.g., scoring tags).
* The `Tags` + `TagAim` helpers.

```java
private AprilTagSensor tags;
private DriveSource drive;   // upgraded: wraps sticks + aim

// In init():
tags = Tags.aprilTags(hardwareMap, "Webcam 1");

StickDriveSource sticks =
        StickDriveSource.defaultMecanumWithSlowMode(
                driverKit,
                driverKit.p1().rightBumper(),
                0.30);

drive = TagAim.forTeleOp(
        sticks,
        driverKit.p1().leftBumper(),  // hold LB to auto-aim
        tags,
        Set.of(1, 2, 3)               // IDs of interest
);
```

In `loop()` you only change one line:

```java
DriveSignal cmd = drive.get(clock);
drivebase.drive(cmd);
```

> **Beginner takeaway:** To “add AprilTags” to your drive, you do **two** new things:
>
> 1. `tags = Tags.aprilTags(hardwareMap, "Webcam 1");`
> 2. `drive = TagAim.forTeleOp(sticks, someButton, tags, Set.of(...));`

---

### 3.4 Reading tag ID & distance (for shooters)

In your loop, you can also read the best observation for your scoring tags:

```java
AprilTagObservation obs = tags.best(Set.of(1, 2, 3), 0.3); // 0.3s max age

if (obs.hasTarget) {
    telemetry.addData("Tag id", obs.id);
    telemetry.addData("range (in)", "%.1f", obs.rangeInches);
    telemetry.addData("bearing (deg)", "%.1f", Math.toDegrees(obs.bearingRad));
} else {
    telemetry.addLine("No scoring tag visible");
}
```

Your shooter code can then map `rangeInches` → flywheel velocity.

---

## 4. Advanced Usage Patterns

Once you’re comfortable with the basic TeleOp patterns, the next layer is:

* Using `PhoenixTeleOpBase` and `PhoenixAutoBase`.
* Using stages (`SetpointStage`, `BufferStage`) to build subsystems.
* Using tasks (`Task`, `SequenceTask`, `ParallelAllTask`) to express auton.

### 4.1 Using `PhoenixTeleOpBase`

Instead of extending `OpMode` directly:

```java
public final class MyTeleOp extends PhoenixTeleOpBase {

    private MecanumDrivebase drivebase;
    private DriveSource drive;
    private AprilTagSensor tags;

    @Override
    protected void onInitRobot() {
        drivebase = Drives
                .mecanum(hardwareMap)
                .frontLeft("fl").frontRight("fr")
                .backLeft("bl").backRight("br")
                .invertRightSide()
                .build();

        tags = Tags.aprilTags(hardwareMap, "Webcam 1");

        StickDriveSource sticks =
                StickDriveSource.defaultMecanumWithSlowMode(
                        driverKit,
                        driverKit.p1().rightBumper(),
                        0.30);

        drive = TagAim.forTeleOp(
                sticks,
                driverKit.p1().leftBumper(),
                tags,
                Set.of(1, 2, 3));
    }

    @Override
    protected void loopRobot(double dtSec) {
        DriveSignal cmd = drive.get(clock);
        drivebase.drive(cmd);

        // Additional subsystems / telemetry here...
    }
}
```

Here `PhoenixTeleOpBase` already initializes:

* `gamepads`, `driverKit`, `bindings`, `clock`, and `telemetry`.

Your subclass only worries about wiring your mechanisms and describing behavior.

---

### 4.2 Autonomous with tasks + TagAim

Outline (pseudo-code):

```java
public final class MyAuto extends PhoenixAutoBase {

    private MecanumDrivebase drivebase;
    private TagAimController aim;
    private Task rootTask;

    @Override
    protected void onInitRobot() {
        drivebase = ...; // same as teleop
        AprilTagSensor tags = Tags.aprilTags(hardwareMap, "Webcam 1");
        aim = TagAim.controllerWithDefaults(tags, Set.of(1, 2, 3));

        rootTask = SequenceTask.of(
                driveToRoughLocation(),
                new Task() {
                    @Override public void update(double dtSec) {
                        double omega = aim.update(dtSec);
                        DriveSignal sig = somePathController.getSignal(dtSec)
                                .withOmega(omega);
                        drivebase.drive(sig);
                    }
                    @Override public boolean isDone() {
                        // e.g., stop when your own bearing-error or timing condition is met.
                        return false; // replace with your condition
                    }
                },
                shootTask()
        );
    }

    @Override
    protected Task getRootTask() {
        return rootTask;
    }
}
```

Advanced teams can:

* Blend `TagAimController` omega into path following.
* Use `Task` graphs to express complex sequencing and parallelism.

---

## 5. How to Think About Beginner vs Advanced APIs

When you’re navigating the code:

* **Beginner-friendly entry points usually look like:**

    * `defaultXxx(...)`
    * `forTeleOp(...)`
    * `aprilTags(...)` (inside `Tags`)
    * `mecanum(hardwareMap)...build()`

* **Advanced / “escape hatch” APIs usually:**

    * Take interfaces like `DriveSource`, `SetpointStage.Plant`, `AprilTagSensor`.
    * Accept explicit configs (`MecanumConfig`, controller gains, tolerances, etc.).
    * Live in `fw.core`, `fw.stage`, `fw.task`, or behind lower-level adapters.

When in doubt, follow this workflow:

1. **Start from an example** in `fw.examples` that is close to what you want.
2. **Copy it into your robot project** and rename it.
3. Change:

    * Hardware names.
    * Tag IDs of interest.
    * Buttons used.
4. Only then look deeper into the framework if you need to tweak behavior.

---

## 6. Philosophy Recap (now including vision)

* **OpModes** should read like a story about your robot’s behavior.
* **Robot containers** (`PhoenixTeleOpBase`, `PhoenixAutoBase`) hide boilerplate.
* **Drive** (`Drives`, `StickDriveSource`, `MecanumDrivebase`) follows one consistent pattern.
* **Mechanisms** use plants and stages instead of raw motor commands.
* **Inputs** are mapped through `DriverKit` and optional `Bindings`, not sprinkled across subsystems.
* **Vision** (AprilTags) follows the exact same pattern:

    * Adapter: `FtcVision` creates the FTC AprilTagProcessor.
    * Sensor interface: `AprilTagSensor`.
    * Beginner factory: `Tags.aprilTags(...)`.
    * Controller: `TagAimController`.
    * TeleOp helper: `TagAim.forTeleOp(...)`.

If you follow these patterns:

* New students can safely modify TeleOp and Auto code without worrying about SDK details.
* Hardware changes (new motor names, new webcam, different drive inversion) are localized to a few lines.
* AprilTags and other advanced features feel like natural extensions of the same framework, not one-off hacks.

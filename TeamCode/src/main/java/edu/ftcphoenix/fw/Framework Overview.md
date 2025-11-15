# Phoenix FTC Framework Overview

Welcome! This document is a guided tour of the Phoenix FTC framework and how you use it to build clear, maintainable robot code.

The overarching goal:

> **Make robot-specific code as simple and readable as possible, while the framework handles the wiring, control patterns, and boilerplate.**
>
> Students write the *story* of what the robot does; the framework handles the plumbing.

Robot stories are expressed primarily through:

* A season-specific **`PhoenixRobot`** class that owns subsystems.
* Small, focused **subsystems** (drive, vision, shooter, etc.).
* **Thin TeleOp and Auto shells** that delegate to `PhoenixRobot`.

---

## 1. Design Principles (short version)

(See **Framework Principles** for the full details. This is the snapshot to keep in your head.)

1. **Simplicity of robot-specific code is paramount**

    * TeleOp / Auto code should be readable by a new student.
    * Robot code should describe *behavior*, not motor math or SDK plumbing.
    * Repeated patterns (drive, intake, shooter, vision, AprilTags, etc.) live in helpers and subsystems.

2. **Clear layering and separation of concerns**

    * FTC SDK (`HardwareMap`, `Gamepad`, `DcMotorEx`, `Servo`, `VisionPortal`) is wrapped by small adapters.
    * Adapters and HAL convert SDK types → framework interfaces.
    * Robot logic talks to simple interfaces (`DriveSource`, `Task`, `AprilTagSensor`, `BearingSource`, `Subsystem`), not raw SDK classes.

3. **Beginner path + advanced escape hatch**

    * **Beginner:** 1–2 obvious helper calls, minimal types.
    * **Advanced:** more control, explicit wiring, access to underlying interfaces.

   If something feels too complex for beginners, we add or improve a helper instead of pushing complexity into OpModes.

4. **Consistency over cleverness**

    * Naming and patterns are consistent across packages.
    * Helpers follow similar shapes: `of(...)`, `defaultXxx(...)`, `forTeleOp(...)`, `forAuto(...)`, `mecanum(...).build()`.
    * Vision / AprilTags follow the same patterns as drive and input.

5. **Single source of time**

    * `LoopClock` is the standard way to get `dtSec` and timestamps.
    * All update-style methods (`DriveSource.get`, `BearingSource.sample`, `TagAimController.update`, subsystem `onXLoop`) take a `LoopClock` rather than calling `System.nanoTime()` directly.

---

## 2. Big Picture Architecture

At a high level, a Phoenix-based robot is structured as:

* **PhoenixTeleOpBase / PhoenixAutoBase**

    * Base OpModes that wire `Gamepads`, `DriverKit`, `LoopClock`, and telemetry.
    * Expose lifecycle hooks: `onInitRobot`, `onStartRobot`, `onLoopRobot`, `onStopRobot`.

* **PhoenixRobot** (season-specific)

    * Constructed once by the base class:

      ```java
      robot = new PhoenixRobot(hardwareMap, driverKit(), telemetry);
      ```

    * Owns all subsystems (drive, vision, shooter, etc.).

    * Exposes lifecycle methods used by TeleOp/Auto shells:

      ```java
      robot.onTeleopInit();
      robot.onTeleopLoop(clock);
      robot.onAutoInit();
      robot.onAutoLoop(clock);
      robot.onStop();
      ```

* **Subsystems**

    * Implement `Subsystem` with lifecycle methods:

      ```java
      void onTeleopInit();
      void onTeleopLoop(LoopClock clock);
      void onAutoInit();
      void onAutoLoop(LoopClock clock);
      void onStop();
      ```

    * Examples: `DriveSubsystem`, `VisionSubsystem`, `ShooterSubsystem`.

    * Each subsystem owns its hardware and logic for one part of the robot.

**Flow in TeleOp:**

1. `PhoenixTeleOpBase.loop()` updates the `LoopClock` and inputs.
2. It calls `onLoopRobot(dtSec)` on the TeleOp shell.
3. The TeleOp shell calls `robot.onTeleopLoop(clock)`.
4. `PhoenixRobot` forwards to each `Subsystem.onTeleopLoop(clock)`.
5. Subsystems read inputs / sensors and command hardware via framework helpers.

Auto follows the same pattern with `onAutoInit` / `onAutoLoop`.

---

## 3. Package Map (what lives where)

This section explains:

* What each package is for.
* Which parts are **beginner** vs **advanced**.

### 3.1 `fw.hal` – Hardware Abstraction Layer

**Main types**

* `MotorOutput` – "thing that accepts a normalized power in [-1, 1]".
* `ServoOutput` – "thing that accepts a normalized position in [0, 1]".

**Who uses this?**

* **Beginners:** almost never directly.
* **Advanced:** when constructing custom mechanisms that aren’t covered by `Plants` or `Drives`.

---

### 3.2 `fw.adapters.ftc` – FTC SDK adapters

**Main types**

* `FtcHardware`

    * `motor(hardwareMap, name, inverted)` → `MotorOutput`
    * `servo(hardwareMap, name, inverted)` → `ServoOutput`

* `FtcVision`

    * Creates VisionPortal / AprilTagProcessor instances.
    * Exposes `AprilTagSensor` implementations used by `Tags.aprilTags(...)`.

**Usage level**

* **Beginner:** do not use directly. Use `Drives` (for motors) and `Tags` (for AprilTags).
* **Advanced:** use when you need custom wiring from SDK hardware to HAL interfaces.

---

### 3.3 `fw.adapters.plants` – Mechanism "plants"

**Main entry point**

* `Plants` – static helpers that create `SetpointStage.Plant` implementations from hardware.

Examples:

* `Plants.power(hw, "intake", false)` – open-loop power plant.
* `Plants.velocity(hw, "shooter", ticksPerRev, inverted)` – velocity plant.
* `Plants.servoPosition(hw, "pusher", false)` – positional servo [0, 1].
* `Plants.powerPair(...)`, `Plants.velocityPair(...)`, `Plants.servoPositionPair(...)`, `Plants.motorPositionPair(...)` – dual-output variants.

**Usage level**

* **Beginner:**

    * Use these helpers from examples to wire shooters, intakes, etc.
    * Don’t need to understand `SetpointStage.Plant` right away.

* **Advanced:**

    * Combine `Plants` with `SetpointStage` and `BufferStage` to build structured subsystems.

---

### 3.4 `fw.drive` – Drivebases and drive signals

**Main types**

* `DriveSignal` – immutable `(axial, lateral, omega)` command.
* `DriveSource` – anything that can produce a `DriveSignal` given a `LoopClock`.
* `MecanumDrivebase` – open-loop 4-wheel mecanum mixer and hardware owner.
* `Drives` – builder helpers for creating drivebases from `HardwareMap`:

  ```java
  drivebase = Drives
          .mecanum(hardwareMap)
          .frontLeft("fl")
          .frontRight("fr")
          .backLeft("bl")
          .backRight("br")
          .invertRightSide()   // typical FTC wiring; change if needed
          .build();
  ```

**Usage level**

* **Beginner:**

    * Use `Drives.mecanum(...)` to build your drivebase.
    * Treat `MecanumDrivebase` as a type you pass a `DriveSignal` into.

* **Advanced:**

    * Implement your own `DriveSource` chains (e.g., path follower + aim + slow mode).

---

### 3.5 `fw.drive.source` – Ways to produce drive signals

**Main types**

* `StickDriveSource`

    * Turns gamepad sticks into a `DriveSignal`.
    * Handles deadband, optional squaring, scaling, and slow mode.

* `TagAimDriveSource`

    * Wraps a base `DriveSource` and overrides `omega` when an aim button is held.

**Usage level**

* **Beginner:**

    * Use `StickDriveSource.defaultMecanumWithSlowMode(driverKit, slowButton, slowScale)`.
    * Let `TagAim.forTeleOp(...)` construct an internal `TagAimDriveSource` when you want auto-aim.

* **Advanced:**

    * Build custom `DriveSource` chains using `LoopClock`, multiple sensors, and controllers.

---

### 3.6 `fw.input` – Gamepads, axes, buttons, bindings

**Main types**

* `Gamepads` – wraps `gamepad1`/`gamepad2` into `GamepadDevice`s.

* `GamepadDevice` – handles stick bias calibration and exposes axes/buttons:

    * `leftX()`, `leftY()`, `rightX()`, `rightY()`, `leftTrigger()`, `rightTrigger()`, etc.

* `Axis` – functional wrapper for “read a double”.

* `Button` – functional wrapper for “read a boolean”.

* `DriverKit` – friendly names for p1/p2 inputs:

  ```java
  Gamepads pads = Gamepads.create(gamepad1, gamepad2);
  DriverKit driver = DriverKit.of(pads);

  driver.p1().leftX();
  driver.p1().rightBumper();
  driver.p2().buttonA();
  ```

* `input.binding.Bindings` – helper for simple button→action mappings.

**Usage level**

* **Beginner:** mostly use `DriverKit` together with `StickDriveSource` and subsystems.
* **Advanced:** use `Axis`/`Button` and `Bindings` for more complex input logic.

---

### 3.7 `fw.robot` – Base classes and subsystem lifecycle

**Main types**

* `PhoenixTeleOpBase`

    * Wires gamepads, `DriverKit`, `LoopClock`, and telemetry.
    * Calls:

      ```java
      onInitRobot();
      onStartRobot();
      onLoopRobot(double dtSec);
      onStopRobot();
      ```

* `PhoenixAutoBase`

    * Similar wiring, plus `TaskRunner` for autonomous `Task` graphs.

* `Subsystem`

    * Lifecycle for robot parts:

      ```java
      void onTeleopInit();
      void onTeleopLoop(LoopClock clock);
      void onAutoInit();
      void onAutoLoop(LoopClock clock);
      void onStop();
      ```

* `PhoenixRobot`

    * Owns subsystems and forwards lifecycle calls from base classes.

**Usage level**

* **Beginner:**

    * Use provided examples as patterns.
    * Focus on editing `PhoenixRobot` and subsystems; treat shells as boilerplate.

* **Advanced:**

    * Extend `PhoenixTeleOpBase` / `PhoenixAutoBase` for all OpModes; keep shells thin.

---

### 3.8 `fw.stage` – Stages (buffer, setpoint, etc.)

**Main types**

* `SetpointStage` – generic “setpoint controller” wrapping a `Plant` and control logic.
* `BufferStage` – higher-level behavior for feeders/buffers.

**Usage level**

* **Beginner:** mostly see these in shooter/buffer examples; treat them as black-box subsystems.
* **Advanced:** compose plants + stages for robust mechanisms with clear goal/at-setpoint semantics.

---

### 3.9 `fw.task` – Autonomous task graphs

**Main types**

* `Task` – interface for something that runs over time.
* `TaskRunner` – drives a root `Task` each loop.
* `SequenceTask`, `ParallelAllTask`, `InstantTask`, `WaitUntilTask`, etc.

**Usage level**

* **Beginner:** tweak distances/targets in a copy of an existing auton.
* **Advanced:** design full autonomous routines as task graphs.

---

### 3.10 `fw.util` – Utilities

**Main types**

* `LoopClock` – tracks loop time and `dtSec`.
* `MathUtil` – central math helpers (`clamp`, `clamp01`, `clampAbs`, `deadband`, etc.).
* `Units` – unit conversions as needed.

**Usage level**

* **Beginner:** encounter `LoopClock` indirectly via base classes and helpers.
* **Advanced:** use `LoopClock` and `MathUtil` directly when writing controllers.

---

### 3.11 `fw.sensing` – AprilTags and aiming

This package provides a clean layering for AprilTags and any other “angle-to-target” sensing.

**Main types**

* `AprilTagSensor`

    * Interface that hides VisionPortal/AprilTagProcessor details.
    * Core method:

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
    * `double rangeInches` (range from `ftcPose.range` in inches when metadata is available)
    * `double bearingRad`
    * `double ageSec`

* `Tags`

    * Beginner-friendly factory:

      ```java
      AprilTagSensor tags = Tags.aprilTags(hardwareMap, "Webcam 1");
      ```

* `BearingSource`

    * Abstraction for “do we have a target, and what is its bearing?”

      ```java
      BearingSample sample = bearingSource.sample(clock);
      if (sample.hasTarget) {
          double bearing = sample.bearingRad;
      }
      ```

* `TagAimController`

    * Pure control logic: bearing → omega.

      ```java
      double omega = controller.update(clock, sample);
      ```

    * Uses a `PidController` internally and honors deadband, max omega, and loss policies.

* `TagAim` / `TagAimDriveSource`

    * **Beginner helper:** wrap an existing `DriveSource` so that holding a button auto-aims omega at scoring tags:

      ```java
      DriveSource drive = TagAim.forTeleOp(
              sticks,
              driverKit.p1().leftBumper(),
              tags,
              Set.of(1, 2, 3));
      ```

    * **Advanced:** construct your own `BearingSource` + `TagAimController` and feed the omega into your own drive logic.

**Usage level**

* **Beginner:**

    * Use `Tags.aprilTags(...)` to create `AprilTagSensor`.
    * Use `TagAim.forTeleOp(...)` to add “hold-to-aim” behavior to your existing stick drive.
    * Use `AprilTagObservation` for ID + range when setting shooter velocity.

* **Advanced:**

    * Customize PID gains, deadband, and loss policy in `TagAimController`.
    * Use `BearingSource` with other sensors in the future.
    * Use `TagAimDriveSource` or your own `DriveSource` that blends aiming with path-following.

---

### 3.12 `fw.core` – Generic control logic

If present, this package contains generic control primitives such as:

* `PidController` – interface for “given error + dt, return correction”.
* `Pid` – reference implementation with clamps and derivative filtering.

**Usage level**

* **Beginner:** rely on higher-level wrappers like `TagAimController`.
* **Advanced:** use directly for heading hold, shooter velocity, arm control, etc.

---

### 3.13 `fw.examples` – Reference OpModes

Fully-working examples at different levels:

* **Basic drive:** Gamepads → DriverKit → StickDriveSource → MecanumDrivebase.
* **Subsystem TeleOp:** Drive + shooter/intake wired as subsystems.
* **TagAim TeleOp:** Drive with AprilTags and TagAim.
* **Task-based Autos:** using `TaskRunner` + `Task` graphs.

Use these as starting templates and as “live documentation”.

---

## 4. Beginner Quickstart (PhoenixRobot + TeleOp)

This quickstart assumes you:

* Have a mecanum robot (4 drive motors named `fl`, `fr`, `bl`, `br`).
* Have a webcam configured as `"Webcam 1"` for AprilTags.
* Want a TeleOp with:

    * Stick driving.
    * Slow mode.
    * “Hold left bumper to auto-aim at scoring AprilTags.”

### 4.1 Thin TeleOp shell

```java
@TeleOp(name = "Phoenix: TeleOp", group = "Phoenix")
public final class PhoenixTeleOp extends PhoenixTeleOpBase {

    private PhoenixRobot robot;

    @Override
    protected void onInitRobot() {
        robot = new PhoenixRobot(hardwareMap, driverKit(), telemetry);
    }

    @Override
    protected void onStartRobot() {
        robot.onTeleopInit();
    }

    @Override
    protected void onLoopRobot(double dtSec) {
        robot.onTeleopLoop(clock());
    }

    @Override
    protected void onStopRobot() {
        robot.onStop();
    }
}
```

### 4.2 PhoenixRobot with drive + vision + shooter

Inside `edu.ftcphoenix.robots.phoenix.PhoenixRobot`:

```java
public final class PhoenixRobot {

    private final DriverKit driverKit;
    private final Telemetry telemetry;

    private final DriveSubsystem drive;
    private final VisionSubsystem vision;
    private final ShooterSubsystem shooter;
    private final List<Subsystem> subsystems = new ArrayList<>();

    public PhoenixRobot(HardwareMap hw,
                        DriverKit driverKit,
                        Telemetry telemetry) {
        this.driverKit = driverKit;
        this.telemetry = telemetry;

        this.vision  = new VisionSubsystem(hw, telemetry);
        this.drive   = new DriveSubsystem(hw, driverKit, vision);
        this.shooter = new ShooterSubsystem(hw, driverKit, telemetry);

        subsystems.add(drive);
        subsystems.add(shooter);
        subsystems.add(vision);
    }

    public void onTeleopInit() {
        for (Subsystem s : subsystems) s.onTeleopInit();
    }

    public void onTeleopLoop(LoopClock clock) {
        for (Subsystem s : subsystems) s.onTeleopLoop(clock);
        telemetry.update();
    }

    public void onAutoInit() {
        for (Subsystem s : subsystems) s.onAutoInit();
    }

    public void onAutoLoop(LoopClock clock) {
        for (Subsystem s : subsystems) s.onAutoLoop(clock);
        telemetry.update();
    }

    public void onStop() {
        for (Subsystem s : subsystems) s.onStop();
    }
}
```

### 4.3 DriveSubsystem with TagAim

```java
public final class DriveSubsystem implements Subsystem {

    private final MecanumDrivebase drivebase;
    private final DriveSource driveSource;

    public DriveSubsystem(HardwareMap hw,
                          DriverKit driverKit,
                          VisionSubsystem vision) {

        this.drivebase = Drives
                .mecanum(hw)
                .frontLeft("fl")
                .frontRight("fr")
                .backLeft("bl")
                .backRight("br")
                .invertRightSide()
                .build();

        StickDriveSource sticks =
                StickDriveSource.defaultMecanumWithSlowMode(
                        driverKit,
                        driverKit.p1().rightBumper(), // slow mode
                        0.30);

        this.driveSource = TagAim.forTeleOp(
                sticks,
                driverKit.p1().leftBumper(),          // hold to aim
                vision.getTagSensor(),
                vision.getScoringTagIds());
    }

    @Override
    public void onTeleopLoop(LoopClock clock) {
        DriveSignal cmd = driveSource.get(clock);
        drivebase.drive(cmd);
    }

    @Override
    public void onStop() {
        drivebase.stop();
    }

    // other lifecycle methods can be empty if not needed
}
```

### 4.4 VisionSubsystem with AprilTagSensor

```java
public final class VisionSubsystem implements Subsystem {

    private final Telemetry telemetry;
    private final AprilTagSensor tags;
    private final Set<Integer> scoringTags;

    public VisionSubsystem(HardwareMap hw, Telemetry telemetry) {
        this.telemetry = telemetry;
        this.tags = Tags.aprilTags(hw, "Webcam 1");
        this.scoringTags = Set.of(1, 2, 3); // adjust per game
    }

    public AprilTagSensor getTagSensor() {
        return tags;
    }

    public Set<Integer> getScoringTagIds() {
        return scoringTags;
    }

    @Override
    public void onTeleopLoop(LoopClock clock) {
        AprilTagObservation obs = tags.best(scoringTags, 0.30);
        if (obs.hasTarget) {
            telemetry.addData("Tag id", obs.id);
            telemetry.addData("range (in)", "%.1f", obs.rangeInches);
            telemetry.addData("bearing (deg)", "%.1f", Math.toDegrees(obs.bearingRad));
        } else {
            telemetry.addLine("No scoring tag visible");
        }
    }
}
```

This gives you a full path:

* Sticks drive the robot.
* Right bumper enables slow mode.
* Left bumper auto-aims at scoring tags.
* Vision subsystem reports tag ID, range in inches, and bearing.

---

## 5. How to Navigate Beginner vs Advanced APIs

When exploring the framework:

* **Beginner-friendly entry points** usually look like:

    * `defaultXxx(...)`
    * `forTeleOp(...)` / `forAuto(...)`
    * `aprilTags(...)` inside `Tags`
    * `mecanum(hardwareMap)...build()`

* **Advanced APIs**:

    * Work directly with interfaces like `DriveSource`, `AprilTagSensor`, `BearingSource`, `PidController`, `Task`.
    * Accept explicit configs and tuning parameters.

A good workflow:

1. Start from an example in `fw.examples` that is close to what you want.
2. Copy it into your team code and rename it.
3. Change hardware names, tag IDs, and buttons.
4. Only then dive into lower-level framework packages if you need custom behavior.

---

## 6. Philosophy Recap (including vision)

* **OpModes** should read like a story about your robot’s behavior.
* **Base classes** (`PhoenixTeleOpBase`, `PhoenixAutoBase`) hide repetitive wiring and provide `LoopClock`.
* **PhoenixRobot + subsystems** concentrate robot logic in one place.
* **Drive** (`Drives`, `StickDriveSource`, `MecanumDrivebase`) has a single clear pattern.
* **Mechanisms** use plants and stages instead of one-off motor commands.
* **Inputs** are mapped through `DriverKit` and optional `Bindings`, not scattered `gamepad1.a` calls.
* **Vision** (AprilTags) follows the same pattern:

    * Adapter: `FtcVision` creates VisionPortal + AprilTagProcessor.
    * Sensor interface: `AprilTagSensor`.
    * Beginner factory: `Tags.aprilTags(...)`.
    * Control logic: `BearingSource` + `TagAimController`.
    * TeleOp helper: `TagAim.forTeleOp(...)` / `TagAimDriveSource`.

If you stick to these patterns, Phoenix stays:

* Approachable for new students.
* Robust for competitions.
* Flexible enough to grow into more advanced robots and control strategies over multiple seasons.

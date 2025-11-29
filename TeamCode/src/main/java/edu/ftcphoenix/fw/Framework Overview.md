# Phoenix FTC Framework Overview

Welcome! This document is a guided tour of the Phoenix FTC framework and how you use it to build
clear, maintainable robot code.

The overarching goal:

> **Make robot-specific code as simple and readable as possible, while the framework handles the
> wiring, control patterns, and boilerplate.**
>
> Students write the *story* of what the robot does; the framework handles the plumbing.

Robot stories are expressed primarily through:

* A season-specific **`PhoenixRobot`** class that owns subsystems.
* Small, focused **subsystems** (drive, vision, shooter, intake, etc.).
* **Thin TeleOp and Auto shells** that delegate to `PhoenixRobot`.

This overview explains how the packages fit together and how your code sits on top of them. For
step‑by‑step "first TeleOp" instructions, see the **Beginner's Guide**.

---

## 1. High-Level Architecture

At a high level, Phoenix is layered like this:

```text
FTC SDK (OpModes, HardwareMap, DcMotorEx, Servo, Gamepad, VisionPortal, etc.)
    │
    ▼
Phoenix adapters + HAL  (fw.adapters.ftc, fw.hal)
    │
    ▼
Core Phoenix APIs        (fw.drive, fw.input, fw.actuation, fw.sensing, fw.task)
    │
    ▼
Robot-specific code      (PhoenixRobot, subsystems, TeleOp/Auto shells)
```

### 1.1 The four main pieces you write

1. **TeleOp shell(s)** – `PhoenixTeleOpBase`

    * Your TeleOp OpModes extend `PhoenixTeleOpBase` in `fw.robot`.
    * The base class owns:

        * `Gamepads`, `DriverKit`, `Bindings` (for button macros).
        * A `LoopClock` for consistent timing.
    * It calls your hooks:

        * `onInitRobot()` – construct `PhoenixRobot`.
        * `onStartRobot()` – optional, when the match starts.
        * `onLoopRobot(double dtSec)` – main loop.
        * `onStopRobot()` – cleanup.

2. **Auto shell(s)** – `PhoenixAutoBase` (later)

    * Your Auto OpModes extend `PhoenixAutoBase`.
    * They build `Task` sequences (often using `DriveTasks` and `PlantTasks`).
    * `PhoenixAutoBase` owns a `TaskRunner` and advances the active task each loop.

3. **`PhoenixRobot`** – your season robot class

    * Lives in your own package (e.g. `edu.ftcphoenix.robots.phoenix`).
    * Knows about motors, servos, sensors, and subsystems.
    * Has methods like:

      ```java
      public final class PhoenixRobot {
          public PhoenixRobot(HardwareMap hw,
                              DriverKit driverKit,
                              Telemetry telemetry) { ... }
 
          public void onTeleopInit() { ... }
          public void onTeleopLoop(LoopClock clock) { ... }
 
          public void onAutoInit() { ... }
          public void onAutoLoop(LoopClock clock) { ... }
 
          public void onStop() { ... }
      }
      ```

4. **Subsystems (optional)** – split `PhoenixRobot` into pieces

    * Subsystems implement a simple `Subsystem` interface (see `fw.robot`):

        * `onTeleopInit()` / `onTeleopLoop()` / `onAutoInit()` / `onAutoLoop()` / `onStop()`.
    * Example subsystems: `DriveSubsystem`, `ShooterSubsystem`, `IntakeSubsystem`.
    * `PhoenixRobot` then becomes mostly wiring + delegation to subsystems.

Your **OpModes** own a `PhoenixRobot`. The robot owns **subsystems**, which use the core Phoenix
APIs for drive, plants, sensing, and tasks.

---

## 2. Design Principles (short version)

(See **Framework Principles** for a deeper discussion. This is the version to keep in your head.)

1. **Simplicity of robot-specific code is paramount**

    * TeleOp / Auto code should be readable by a new student.
    * Robot code should describe *behavior*, not motor math or SDK plumbing.
    * Repeated patterns (drive, intake, shooter, vision, AprilTags, etc.) live in helpers and
      subsystems.

2. **Clear layering and separation of concerns**

    * FTC SDK (`HardwareMap`, `Gamepad`, `DcMotorEx`, `Servo`, `VisionPortal`) is wrapped by small
      adapters.
    * Adapters and HAL convert SDK types → framework interfaces.
    * Core APIs (`DriveSource`, `Plant`, `Task`, etc.) know nothing about FTC classes.

3. **One core abstraction per domain**

    * Drive: **`DriveSource`** outputs a `DriveSignal` each loop.
    * Mechanisms: **`Plant`** wraps one mechanism (intake, lift, shooter, etc.).
    * Behavior: **`Task`** describes multi-step, non-blocking actions.

4. **Provide strong "TeleOp presets" for beginners**

    * We standardize a small number of `teleOp...` helper methods:

        * `GamepadDriveSource.teleOpMecanum(...)`
        * `GamepadDriveSource.teleOpMecanumWithSlowMode(...)`
        * `TagAim.teleOpAim(...)`
    * Beginners wire these together without touching lower-level details.

5. **Composition over inheritance**

    * Complex behaviors should be built by *wrapping* and *blending* simple sources and plants.
    * Examples:

        * Take a manual `DriveSource` and wrap it with `TagAim.teleOpAim(...)`.
        * Add slow mode as an outer wrapper via `scaledWhen(...)`.
        * Use tasks to sequence or parallelize behaviors.

6. **Non-blocking and loop-friendly**

    * Everything runs inside the OpMode loop; nothing calls `sleep()`.
    * TeleOp and Auto both use the same loop-friendly concepts (sources, plants, tasks).

---

## 3. Inputs (`fw.input`)

Phoenix input code wraps the FTC `Gamepad` objects into a more structured layer.

**Key types for beginners:**

* `Gamepads` – samples `gamepad1` and `gamepad2` each loop.
* `DriverKit` – gives you a *named* view of player inputs:

    * `driverKit.p1().leftStickX()` instead of `gamepad1.left_stick_x`.
    * `driverKit.p2().rightTrigger()` instead of `gamepad2.right_trigger`.
* `Button` / `Axis` – represent logical buttons/axes and are used by drive, tasks, and macros.
* `Bindings` – maps buttons to actions (start/cancel tasks, toggle modes, etc.).

`PhoenixTeleOpBase` and `PhoenixAutoBase` construct `Gamepads`, `DriverKit`, `Bindings`, and a
`LoopClock` for you. In `PhoenixTeleOpBase` you can access them with protected helpers:

```java
protected DriverKit driverKit();    // full DriverKit
protected DriverKit.Player p1();    // primary driver
protected DriverKit.Player p2();    // secondary driver
protected Bindings bind();          // for macros (later)
protected LoopClock clock();        // timing helper
```

In your `PhoenixRobot`, you typically inject `DriverKit` from the TeleOp/Auto base and use it to
build drive sources or to read simple controls.

---

## 4. Drive (`fw.drive`)

Phoenix drive abstractions are built around two core types:

* `DriveSignal` – "what the robot should do" this loop.
* `DriveSource` – "how we decide the drive signal" this loop.

### 4.1 `DriveSignal`: blendable drive commands

`DriveSignal` is a small, immutable data object:

```java
public final class DriveSignal {
    public final double axial;
    public final double lateral;
    public final double omega;

    public DriveSignal(double axial, double lateral, double omega) { ... }

    // Common helpers
    public DriveSignal scaled(double scale) { ... }
    public DriveSignal clamped() { ... }

    // Helpers for combining commands
    public DriveSignal plus(DriveSignal other) { ... }
    public DriveSignal lerp(DriveSignal other, double alpha) { ... }

    public static final DriveSignal ZERO = new DriveSignal(0, 0, 0);
}
```

Conceptually:

* `axial`   – forward/backward
* `lateral` – strafe left/right
* `omega`   – rotation

The helpers make it easy to:

* **Scale** a command (slow mode, fine control) with `scaled(k)`.
* **Add** a correction to a base command with `plus(other)`.
* **Blend** between two commands (e.g., driver vs. auto-aim) with `lerp(other, t)`.

Phoenix uses these internally, and advanced examples can use them directly when mixing
behaviors.

### 4.2 `DriveSource`: where drive commands come from

A `DriveSource` produces a `DriveSignal` each loop:

```java
public interface DriveSource {
    DriveSignal get(LoopClock clock);

    // Optional helpers for composition
    default DriveSource scaledWhen(BooleanSupplier when, double scale) { ... }
    default DriveSource blendedWith(DriveSource other, double alpha) { ... }
}
```

Think of it as:

> "Anything that, given time, tells us how to drive right now."

Examples of `DriveSource` implementations or factories:

* `GamepadDriveSource.teleOpMecanum(...)` – maps gamepad sticks → mecanum drive.
* `TagAim.teleOpAim(...)` – wraps an existing drive source to override `omega` when aiming.
* A future trajectory follower.

The default methods make sources easy to compose:

* `scaledWhen(when, scale)` – conditionally apply a global scale (e.g., slow mode) around any
  source.
* `blendedWith(other, alpha)` – linearly blend between two sources using
  `DriveSignal.lerp(...)`.

### 4.3 `Drives` and `MecanumDrivebase`

`MecanumDrivebase` knows how to apply a `DriveSignal` to four mecanum wheels. You build it with
`Drives`:

```java
MecanumDrivebase drivebase = Drives
        .mecanum(hw)
        .names("frontLeft", "frontRight", "backLeft", "backRight")
        .invertFrontRight()
        .invertBackRight()
        .build();
```

Then each loop you simply do:

```java
DriveSignal cmd = driveSource.get(clock);
drivebase.drive(cmd);
```

`Drives` also provides other helpers (e.g., dual-motor shooters, tank drive) that follow the same
pattern: a small builder to wire hardware into a simple, reusable type.

### 4.4 `GamepadDriveSource`: TeleOp presets

`GamepadDriveSource` is the main way beginners drive their robot.

For TeleOp, you only need to remember two static helpers:

```java
// P1: standard robot-centric mecanum
GamepadDriveSource teleOpMecanum(DriverKit kit);

// P1: mecanum with a slow-mode button
GamepadDriveSource teleOpMecanumWithSlowMode(
        DriverKit kit,
        Button slowButton,
        double slowScale);
```

Example wiring in `PhoenixRobot`:

```java
DriveSource driveSource = GamepadDriveSource.teleOpMecanumWithSlowMode(
        driverKit,
        driverKit.p1().rightBumper(), // hold for precision driving
        0.30);
```

`GamepadDriveSource` takes care of:

* Reading sticks/axes from `DriverKit`.
* Deadband.
* Optional shaping (exponential response) near center.
* Mapping `(leftStickY, leftStickX, rightStickX, ...)` into `(axial, lateral, omega)`.

### 4.5 Tag-based auto-aim (`fw.sensing.TagAim`)

Phoenix provides a small helper to add AprilTag-based auto-aim over any `DriveSource`.

* `Tags.aprilTags(...)` constructs an `AprilTagSensor` from FTC VisionPortal + camera config.
* `TagAim.teleOpAim(...)` wraps a base `DriveSource` and overrides `omega` when the aim button is
  held.

Example in `PhoenixRobot`:

```java
DriveSource manual = GamepadDriveSource.teleOpMecanum(driverKit);

DriveSource aimed = TagAim.teleOpAim(
        manual,
        driverKit.p1().leftBumper(), // hold to auto-aim
        tagSensor,
        scoringTagIds);

// Optional: add slow mode around the whole aimed drive
DriveSource drive = aimed.scaledWhen(
        () -> driverKit.p1().rightBumper().isPressed(),
        0.30);
```

Each loop, you still call `drive.get(clock)` and pass the result to `drivebase.drive(...)`. The
layers (manual, aim, slow mode) are composed via `DriveSource`.

---

## 5. Mechanisms and Plants (`fw.actuation`)

Phoenix represents mechanisms with **plants**:

* A `Plant` is something that accepts a target (power, position, velocity, etc.) and updates each
  loop.
* `FtcPlants` builds plants from FTC hardware:

    * Single motors (power-only or velocity-controlled).
    * Dual-motor shooters.
    * Servos and paired servos.

A simple example for an intake:

```java
import com.qualcomm.robotcore.hardware.DcMotorEx;

import edu.ftcphoenix.fw.actuation.Plant;
import edu.ftcphoenix.fw.actuation.Plants;

private static final String HW_INTAKE = "intake";

private final Plant intake;
private final DriverKit driverKit;

public PhoenixRobot(HardwareMap hw, DriverKit driverKit, Telemetry telemetry) {
    this.driverKit = driverKit;

    DcMotorEx intakeMotor = hw.get(DcMotorEx.class, HW_INTAKE);
    this.intake = Plants.powerOnly(intakeMotor);
}

public void onTeleopLoop(LoopClock clock) {
    // Drive (omitted) ...

    double intakePower = driverKit.p1().a().isPressed() ? +1.0 : 0.0;
    intake.setTarget(intakePower);
    intake.update(clock);
}
```

### 5.1 `FtcPlants`

`FtcPlants` contains factory methods such as:

* `powerOnly(DcMotorEx motor)` – simple open-loop power plant.
* `velocity(DcMotorEx motor, double ticksPerRev)` – velocity-controlled plant.
* Dual/paired variants for common shooter and transfer configurations.

The goal is that your robot code rarely touches `DcMotorEx`/`Servo` directly after initialization;
most behavior flows through plants.

---

## 6. Tasks and Macros (`fw.task`)

Tasks provide a **non-blocking** way to express sequences and conditions.

Core types:

* `Task` – something with `update(...)` and `isFinished()` semantics.
* `TaskRunner` – runs a current `Task` each loop.
* `Tasks` – static helpers to build common task patterns (sequence, wait, etc.).
* `DriveTasks` – drive-specific tasks (drive for time, turn to heading, etc.).
* `PlantTasks` – plant-specific tasks (hold power for time, go to setpoint and wait, etc.).

In Auto, you typically:

1. Build a `Task` describing the routine.
2. Give it to `PhoenixAutoBase` / a `TaskRunner`.
3. Let the runner advance the task each loop.

In TeleOp, tasks are often triggered by buttons via `Bindings`.

A simple conceptual example:

```java
Task shootThree = Tasks.sequence(
        PlantTasks.holdForSeconds(intakePlant, +1.0, 0.7),
        PlantTasks.goToSetpointAndWait(shooterPlant, targetVelocity),
        PlantTasks.holdForSeconds(indexerPlant, +1.0, 1.5));

bindings.onPressed(driverKit.p1().y(), () -> taskRunner.start(shootThree));
```

For a deeper dive, see **Tasks & Macros Quickstart** and **Stages & Tasks Quickstart – Shooter
Case Study**.

---

## 7. Sensing (`fw.sensing`)

The sensing package contains framework-level representations of sensors that are *not* simple
one-line reads (i.e., anything more complex than `getVoltage()` / `getDistance()`).

Currently this includes:

* AprilTags:

    * `AprilTagSensor` – high-level interface for tag detections.
    * `Tags` – helpers to build `AprilTagSensor` using VisionPortal.
    * `TagAim` – drive helper that uses tag bearings to turn the robot.
* General feedback sources:

    * `FeedbackSource<T>` – a generic abstraction for time-stamped sensor values.
    * `FeedbackSample<T>` – wraps a value plus timestamp.

The goal is to keep vision and other complex sensing logic **out of** your OpModes. You configure
sensing once (usually in `PhoenixRobot`), then pass the resulting sensor interfaces into drive
helpers like `TagAim` or your own logic.

---

## 8. Adapters and HAL (`fw.adapters.ftc`, `fw.hal`)

These packages hide the raw FTC SDK behind small, implementation-specific wrappers.

* `fw.adapters.ftc` – code that depends directly on FTC classes:

    * Building `DcMotorEx`, `Servo`, `CRServo`, `IMU`, `VisionPortal` instances.
    * `FtcPlants` and `FtcHardware` live here.
* `fw.hal` – generic hardware abstraction layer interfaces:

    * `PowerOutput`, `ServoOutput`, etc.
    * Allows the rest of the framework to be written against stable interfaces rather than SDK
      types.

**Beginners** rarely need to touch these packages directly. They matter when you:

* Introduce new kinds of hardware.
* Port the framework to a different platform.
* Implement very custom mechanisms not covered by `FtcPlants`.

---

## 9. Putting It All Together – A Typical TeleOp

Here is the pattern you’ll see across examples:

1. **TeleOp shell:** extends `PhoenixTeleOpBase`.

    * In `onInitRobot()`, you construct `PhoenixRobot` using `hardwareMap`, `driverKit()`, and
      `telemetry`.
    * In `onLoopRobot(dtSec)`, you call `robot.onTeleopLoop(clock())`.

2. **PhoenixRobot:** wires drive and mechanisms using the core APIs.

    * Drivebase from `Drives.mecanum(...)` → `MecanumDrivebase`.
    * Drive source from `GamepadDriveSource.teleOpMecanum[...]`.
    * Optional tag sensor from `Tags.aprilTags(...)`.
    * Optional tag-aim wrapper via `TagAim.teleOpAim(...)` and/or slow-mode wrapper via
      `DriveSource.scaledWhen(...)`.
    * Mechanisms as `Plant`s via `FtcPlants`.

3. **Loop:** each TeleOp iteration:

    * `PhoenixTeleOpBase` updates `Gamepads`, `DriverKit`, `Bindings`, and the `LoopClock`.
    * `PhoenixRobot.onTeleopLoop(clock)`:

        * Gets a `DriveSignal` from a `DriveSource`.
        * Calls `drivebase.drive(signal)`.
        * Updates plants with targets based on `DriverKit`.
        * Optionally runs a `TaskRunner` for macros.

4. **Auto:** similar loop, but the main behavior is driven by `Task` sequences instead of driver
   sticks.

If you stick to these patterns:

* Robot-specific code stays small and readable.
* Framework code absorbs the complexity.
* The same patterns can carry across seasons with different robots and games, and can scale from
  simple TeleOp to sophisticated autonomous routines and macros.

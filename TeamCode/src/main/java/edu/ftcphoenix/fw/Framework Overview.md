# Phoenix FTC Framework Overview

Welcome! This document is a guided tour of the Phoenix FTC framework and how you use it to build
clear, maintainable robot code.

The overarching goal:

> **Make robot-specific code as simple and readable as possible, while the framework handles the
wiring, control patterns, and boilerplate.**
>
> Students write the *story* of what the robot does; the framework handles the plumbing.

Robot stories are expressed primarily through:

* A season-specific **`PhoenixRobot`** class that owns subsystems.
* Small, focused **subsystems** (drive, vision, shooter, etc.).
* **Thin TeleOp and Auto shells** that delegate to `PhoenixRobot`.

This overview explains how the packages fit together and how your code sits on top of them. For
concrete “how do I start a TeleOp?” steps, see the **Beginner’s Guide**.

---

## 1. Design Principles (short version)

(See **Framework Principles** for the full details. This is the snapshot to keep in your head.)

1. **Simplicity of robot-specific code is paramount**

    * TeleOp / Auto code should be readable by a new student.
    * Robot code should describe *behavior*, not motor math or SDK plumbing.
    * Repeated patterns (drive, intake, shooter, vision, AprilTags, etc.) live in helpers and
      subsystems.

2. **Clear layering and separation of concerns**

    * FTC SDK (`HardwareMap`, `Gamepad`, `DcMotorEx`, `Servo`, `VisionPortal`) is wrapped by small
      adapters.
    * Adapters and HAL convert SDK types → framework interfaces.
    * Robot logic talks to simple interfaces (`DriveSource`, `AprilTagSensor`, `BearingSource`,
      `Subsystem`), not raw SDK classes.

3. **Beginner path + advanced escape hatch**

    * **Beginner:** 1–2 obvious helper calls, minimal types.
    * **Advanced:** more control, explicit wiring, access to underlying interfaces.

   If something feels too complex for beginners, we add or improve a helper instead of pushing
   complexity into OpModes.

4. **Consistency over cleverness**

    * Naming and patterns are consistent across packages.
    * Helpers follow similar shapes: `of(...)`, `defaultXxx(...)`, `forTeleOp(...)`, `forAuto(...)`,
      `mecanum(...).build()`.

5. **Testable, reusable subsystems**

    * Mechanisms (drive, shooter, arm, intake) live in subsystem classes that are reusable across
      OpModes.
    * Subsystems depend on framework interfaces, not FTC SDK classes.

---

## 2. Big-picture layering

At a high level, Phoenix layers look like this:

```text
Your code (PhoenixRobot + subsystems + OpModes)
│
├─ fw.robot       – PhoenixTeleOpBase, PhoenixAutoBase, Subsystem
├─ fw.drive       – drivebases + drive sources
├─ fw.stage       – reusable processing blocks (buffer, setpoint)
├─ fw.task        – autonomous task graphs
├─ fw.sensing     – AprilTags, bearings, etc.
├─ fw.input       – Gamepads, axes, buttons, DriverKit
└─ fw.hal + fw.adapters.ftc
     ├─ HAL interfaces (MotorOutput, ServoOutput, etc.)
     └─ FTC adapters (FtcHardware, FtcPlants, FtcVision)
```

The rest of this document walks through the important packages and their roles.

---

## 3. Package-by-package tour

### 3.1 `fw.hal` – Hardware Abstraction Layer

**Main types**

* `MotorOutput`

    * Minimal interface for “write normalized power in [-1, +1]”.
    * Methods:

        * `setPower(double power)`
        * `double getLastPower()`

* `ServoOutput`

    * Minimal interface for positional servos.
    * Methods:

        * `setPosition(double position)` in [0, 1]
        * `double getLastPosition()`

These interfaces let the rest of the framework treat motors/servos generically, without depending on
`DcMotorEx`, `CRServo`, or `Servo` directly.

**Who uses this?**

* **Beginners:** almost never directly.
* **Advanced:** when constructing custom mechanisms that aren’t covered by `FtcPlants` or `Drives`.

---

### 3.2 `fw.adapters.ftc` – FTC SDK adapters

**Main types**

* `FtcHardware`

    * `motor(hardwareMap, name, inverted)` → `MotorOutput`
    * `servo(hardwareMap, name, inverted)` → `ServoOutput`
    * `crServoMotor(hardwareMap, name, inverted)` → `MotorOutput` backed by a `CRServo`.

* `FtcVision`

    * Creates VisionPortal / AprilTagProcessor instances.
    * Exposes `AprilTagSensor` implementations used by `Tags.aprilTags(...)`.

**Usage level**

* **Beginner:** do not use directly. Use `Drives` (for motors) and `Tags` (for AprilTags).
* **Advanced:** use when you need custom wiring from SDK hardware to HAL interfaces.

---

### 3.3 `FtcPlants` – Mechanism "plants" (in `fw.adapters.ftc`)

**Main entry point**

* `FtcPlants` – static helpers that create `SetpointStage.Plant` implementations from FTC hardware.

Examples:

* `FtcPlants.power(hw, "intake", false)` – open-loop power plant (normalized power in [-1, +1]).
* `FtcPlants.velocity(hw, "shooter", false, ticksPerRev)` – velocity plant (target in rad/s).
* `FtcPlants.servoPosition(hw, "pusher", false)` – positional servo [0, 1].
* `FtcPlants.powerPair(...)`, `FtcPlants.velocityPair(...)`, `FtcPlants.servoPositionPair(...)`,
  `FtcPlants.motorPositionPair(...)` – dual-output variants for mechanisms with two actuators (e.g.,
  dual shooter wheels, dual lift motors).

**Usage level**

* **Beginner:**

    * Use these helpers from examples to wire shooters, intakes, arms, etc.
    * Don’t need to understand `SetpointStage.Plant` right away.

* **Advanced:**

    * Combine `FtcPlants` with `SetpointStage` and `BufferStage` to build structured subsystems.
    * Prefer `FtcPlants.*(hardwareMap, ...)` for simple wiring from configuration names.
    * Use the lower-level overloads (that accept `MotorOutput` / `ServoOutput`) when composing more
      complex mechanisms.

---

### 3.4 `fw.drive` – Drivebases and drive signals

**Main types**

* `DriveSignal`

    * Immutable value type: `(axial, lateral, omega)`.
    * Represents a single “drive command” for a holonomic drive.

* `MecanumDrivebase`

    * Converts `DriveSignal` into per-wheel motor powers.
    * Handles inversion and scaling.

* `Drives`

    * Builder-style helpers for drivebases.

  Example:

  ```java
  MecanumDrivebase drive = Drives
          .mecanum(hardwareMap)
          .frontLeft("fl")
          .frontRight("fr")
          .backLeft("bl")
          .backRight("br")
          .invertRightSide()
          .build();
  ```

**Usage level**

* **Beginner:** use `Drives.mecanum(...)` to construct your drivebase.
* **Advanced:** extend `DriveSignal` usage, or create new drivebase types if needed.

---

### 3.5 `fw.drive.source` – Ways to produce `DriveSignal`

**Main types**

* `DriveSource`

    * Functional interface: given a `LoopClock`, produce a `DriveSignal`.
    * Implementations can incorporate input shaping, PID controllers, etc.

* `StickDriveSource`

    * Maps gamepad sticks to `DriveSignal`.
    * Includes helpers like `defaultMecanumWithSlowMode(...)`.

* `TagAim`

    * Provides helpers that turn an AprilTag bearing into a rotation command.
    * `TagAim.forTeleOp(...)` wraps a base `DriveSource` and lets a button temporarily “take over”
      rotation to aim at a tag.

**Usage level**

* **Beginner:**

    * Use `StickDriveSource.defaultMecanumWithSlowMode(...)` for P1 driving.
    * Use `TagAim.forTeleOp(...)` to add "face the tag" behavior to your drive.

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

    * Base class for TeleOp OpModes.
    * Handles:

        * `LoopClock` creation.
        * Input wiring (`Gamepads` → `DriverKit`).
        * Calling your robot lifecycle methods.

  Skeleton:

  ```java
  @TeleOp
  public final class MyTeleOp extends PhoenixTeleOpBase {
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

**Usage level**

* **Beginner:**

    * Create a `PhoenixRobot` class that owns subsystems.
    * Use `PhoenixTeleOpBase` for TeleOp, `PhoenixAutoBase` for Auto.

* **Advanced:**

    * Add more subsystems and tasks over time.
    * Split large subsystems into smaller ones as the robot grows.

---

### 3.8 `fw.stage` – Stages (buffer, setpoint)

Stages are reusable processing blocks that you can wire together.

**Main types**

* `SetpointStage<G>`

    * Maps high-level goals (typically an enum) to numeric targets and drives a `Plant`.
    * Uses a `Plant` interface that exposes:

        * `setTarget(double target)`
        * `update(double dtSec)`
        * `boolean atSetpoint()`

* `BufferStage`

    * Provides buffering/queuing semantics for commands (e.g., “queue up shooter cycles”).

* `Stage`

    * Lightweight framework for wiring stages into a processing pipeline.

**Usage level**

* **Beginner:** mostly uses plants through helpers (like `FtcPlants.velocityPair(...)`) and example
  subsystems.
* **Advanced:** builds custom `SetpointStage` and `BufferStage` combinations for rich subsystem
  behavior.

---

### 3.9 `fw.task` – Autonomous task graphs

**Main types**

* `Task`

    * Represents a unit of autonomous work (e.g., "drive to pose", "shoot 3 discs").
    * Composable: can wait for, sequence, or run tasks in parallel.

* `TaskRunner`

    * Drives a `Task` graph forward each loop.

**Usage level**

* **Beginner:** can start with simple `Task` examples (e.g., shoot then park).
* **Advanced:** uses tasks to structure complex autonomous routines.

---

### 3.10 `fw.util` – Utilities

**Main types**

* `LoopClock`

    * Tracks absolute time and `dtSec` between loops.

* `InterpolatingTable1D`

    * Simple utility for mapping from a sampled 1D function (e.g., distance → shooter velocity) via
      linear interpolation.

* `MathUtil`, `Filter` types, etc.

**Usage level**

* **Beginner:** mostly uses `LoopClock` indirectly via `PhoenixTeleOpBase` and `PhoenixAutoBase`.
* **Advanced:** uses interpolation and math helpers when building their own controllers.

---

### 3.11 `fw.sensing` – AprilTags and aiming

**Main types**

* `AprilTagSensor`

    * Interface for AprilTag observations (id, pose, bearing, range, age).

* `AprilTagObservation`

    * Holds one observation from an AprilTag sensor.

* `Tags`

    * Beginner factory for AprilTag sensing:

      ```java
      AprilTagSensor tags = Tags.aprilTags(hardwareMap, "Webcam 1");
      ```

* `BearingSource`

    * Interface for “where should we aim?” sources.

* `TagAim` / `TagAimController`

    * Convert tag bearing into a rotation command for the drive.
    * Provide helpers for TeleOp (`TagAim.forTeleOp(...)`).

**Usage level**

* **Beginner:** use `Tags.aprilTags(...)` + `TagAim.forTeleOp(...)`.
* **Advanced:** build custom sensors or aiming logic using `BearingSource` and `TagAimController`.

---

### 3.12 `fw.core` – Generic control logic

**Main types**

* `PidController` / `Pid`

    * Generic PID controller interface + implementation.

* Other generic control or math helpers.

**Usage level**

* **Beginner:** rarely used directly.
* **Advanced:** used in custom subsystems and control algorithms.

---

### 3.13 `fw.examples` – Reference OpModes and subsystems

This package contains example TeleOp / Auto OpModes and subsystems that show recommended patterns.

* How to wire a mecanum drive with `Drives` + `StickDriveSource`.
* How to build a shooter subsystem using `FtcPlants.velocityPair(...)`.
* How to use `Tags.aprilTags(...)` + `TagAim.forTeleOp(...)` for AprilTag-based auto-aim.

**Usage level**

* **Beginner:** copy, rename, and tweak examples for your robot.
* **Advanced:** treat examples as templates and starting points for more complex designs.

---

## 4. How your robot code fits in

Putting it all together for a typical TeleOp:

1. **Base class:** extend `PhoenixTeleOpBase`.
2. **Robot class:** create a `PhoenixRobot` that owns subsystems (`DriveSubsystem`,
   `ShooterSubsystem`, `VisionSubsystem`, etc.).
3. **Subsystems:** use `Drives`, `StickDriveSource`, `Tags`, `TagAim`, `FtcHardware`, and
   `FtcPlants` to wire hardware.
4. **Inputs:** use `Gamepads` + `DriverKit` to read P1/P2 controls.
5. **Behavior:** in each subsystem’s `onTeleopLoop(...)`, read inputs and set setpoints/commands.

If you stick to these patterns:

* Robot-specific code stays small and readable.
* Framework code absorbs the complexity.
* The same patterns can carry across seasons with different robots and games.

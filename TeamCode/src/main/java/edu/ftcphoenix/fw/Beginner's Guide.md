# Phoenix FTC Beginner’s Guide

This guide is for students who are **new to programming FTC robots** using the Phoenix framework.

The goal:

> You focus on what the robot should do.
> Phoenix handles the wiring, math, and boilerplate.

You’ll learn how to:

* Set up your project structure.
* Use `PhoenixTeleOpBase` as your TeleOp base class.
* Create a season-specific `PhoenixRobot` class for your robot.
* Read gamepads using `Gamepads` and `DriverKit`.
* Drive a mecanum robot with `Drives`, `MecanumDrivebase`, and `StickDriveSource`.
* (Optional) Add AprilTag auto-aim with `Tags` and `TagAim`.
* (Optional) Add simple mechanisms using `Plant` and `FtcPlants`.

For tasks/macros and more advanced control, see:

* **`Tasks & Macros Quickstart.md`** – non-blocking sequences and button-activated macros.
* **`Stages & Tasks Quickstart - Shooter Case Study.md`** – staged control for things like shooters and buffers.
* **`Framework Overview.md`** – bigger-picture tour of all packages.

---

## 0. Big Picture: Who Does What?

There are four main pieces you’ll work with:

1. **PhoenixTeleOpBase** (in `edu.ftcphoenix.fw.robot`)

    * Your TeleOp OpModes extend this base class.
    * It wires:

        * `Gamepads` → `DriverKit` → `Bindings`.
        * A `LoopClock` for timing.
    * It calls your robot-specific hooks:

        * `onInitRobot()`
        * `onStartRobot()`
        * `onLoopRobot(double dtSec)`
        * `onStopRobot()`

2. **Your PhoenixRobot class** (in your own package)

    * Represents *your* robot for this season.

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

    * TeleOp and Auto OpModes both delegate to this class.

3. **Drive + input helpers**

    * `Gamepads` + `DriverKit` – reading gamepads.
    * `Drives` and `MecanumDrivebase` – drivebase wiring + holonomic math.
    * `StickDriveSource` – mapping gamepad sticks → `DriveSource` for TeleOp.
    * `Tags` and `TagAim` – optional helpers to aim at AprilTags.

4. **Mechanism + task helpers (later)**

    * `Plant` and `FtcPlants` – mechanisms (intake, lifts, shooters, etc.).
    * `Task`, `TaskRunner`, `Tasks`, `PlantTasks`, `DriveTasks` – for timed/autonomous behaviors and macros.

You mostly work in your own `robots.phoenix` package; the `fw.*` code is the framework.

---

## 1. Recommended Project Structure

To keep big robots manageable, we recommend this structure:

* **OpMode shells** (thin, FTC-facing):

    * Package: `org.firstinspires.ftc.teamcode.robots`

  Example:

  ```text
  org.firstinspires.ftc.teamcode.robots
  ├─ PhoenixTeleOpMain.java
  └─ PhoenixAutoMain.java
  ```

* **Robot implementation** (season-specific):

    * Package: `edu.ftcphoenix.robots.phoenix` (or your own namespace)

  Example:

  ```text
  edu.ftcphoenix.robots.phoenix
  ├─ PhoenixRobot.java
  └─ subsystems/
     ├─ DriveSubsystem.java
     ├─ ShooterSubsystem.java
     └─ IntakeSubsystem.java
  ```

* **Framework** (don’t usually modify):

    * Package: `edu.ftcphoenix.fw.*` – the Phoenix library:

        * `fw.robot` – `PhoenixTeleOpBase`, `PhoenixAutoBase`, `Subsystem`.
        * `fw.drive` – drivebases and drive sources.
        * `fw.input` – gamepads, axes, buttons, `DriverKit`.
        * `fw.sensing` – AprilTags and other sensors.
        * `fw.actuation` – `Plant` and plant helpers.
        * `fw.task` – tasks and macros.
        * `fw.adapters.ftc` + `fw.hal` – hardware abstraction.

---

## 2. TeleOp: The Minimal Phoenix Pattern

### 2.1 Skeleton: TeleOp OpMode using PhoenixTeleOpBase

A typical TeleOp looks like this:

```java
package org.firstinspires.ftc.teamcode.robots;

import com.qualcomm.robotcore.eventloop.opmode.TeleOp;

import edu.ftcphoenix.fw.robot.PhoenixTeleOpBase;
import edu.ftcphoenix.robots.phoenix.PhoenixRobot;

@TeleOp(name = "Phoenix: Main TeleOp", group = "Phoenix")
public final class PhoenixTeleOpMain extends PhoenixTeleOpBase {

    private PhoenixRobot robot;

    @Override
    protected void onInitRobot() {
        // PhoenixTeleOpBase has already created:
        // - Gamepads
        // - DriverKit
        // - Bindings
        // - LoopClock

        robot = new PhoenixRobot(
                hardwareMap,
                driverKit(),   // from base class
                telemetry);
        robot.onTeleopInit();
    }

    @Override
    protected void onStartRobot() {
        // Called once after the referee presses Play.
        // Optional; leave empty if not needed.
    }

    @Override
    protected void onLoopRobot(double dtSec) {
        // Called every loop after gamepads + bindings are updated.
        robot.onTeleopLoop(clock());
    }

    @Override
    protected void onStopRobot() {
        robot.onStop();
    }
}
```

Notes:

* `PhoenixTeleOpBase` handles:

    * Creating `Gamepads` and `DriverKit`.
    * Updating them every loop.
    * Updating `Bindings` (for button-based macros).
    * Maintaining a `LoopClock`.
* You focus on:

    * Constructing `PhoenixRobot` in `onInitRobot()`.
    * Calling its per-loop method in `onLoopRobot(...)`.

---

## 3. PhoenixRobot: One Class for Your Whole Robot

Here’s a minimal `PhoenixRobot` with only drive:

```java
package edu.ftcphoenix.robots.phoenix;

import com.qualcomm.robotcore.hardware.HardwareMap;

import edu.ftcphoenix.fw.drive.DriveSignal;
import edu.ftcphoenix.fw.drive.DriveSource;
import edu.ftcphoenix.fw.drive.Drives;
import edu.ftcphoenix.fw.drive.MecanumDrivebase;
import edu.ftcphoenix.fw.drive.source.StickDriveSource;
import edu.ftcphoenix.fw.input.DriverKit;
import edu.ftcphoenix.fw.util.LoopClock;
import org.firstinspires.ftc.robotcore.external.Telemetry;

public final class PhoenixRobot {

    // --- Drive hardware names (match your configuration) ---
    private static final String HW_FL = "frontLeft";
    private static final String HW_FR = "frontRight";
    private static final String HW_BL = "backLeft";
    private static final String HW_BR = "backRight";

    // --- Framework plumbing ---
    private final Telemetry telemetry;

    // --- Drive ---
    private final MecanumDrivebase drivebase;
    private DriveSource driveSource;

    public PhoenixRobot(HardwareMap hw, DriverKit driverKit, Telemetry telemetry) {
        this.telemetry = telemetry;

        // 1) Build the mecanum drivebase (holonomic math + motor wiring)
        this.drivebase = Drives
                .mecanum(hw)
                .names(HW_FL, HW_FR, HW_BL, HW_BR)
                .invertFrontRight() // typical wiring; adjust for your robot
                .invertBackRight()
                .build();

        // 2) Create a TeleOp drive source: P1 sticks, with slow mode on right bumper
        this.driveSource = StickDriveSource.teleOpMecanumWithSlowMode(
                driverKit,
                driverKit.p1().rightBumper(), // hold for slow mode
                0.30                          // 30% speed in slow mode
        );
    }

    // ---------------------------------------------------------------------
    // TeleOp lifecycle
    // ---------------------------------------------------------------------

    public void onTeleopInit() {
        telemetry.addLine("PhoenixRobot: TeleOp init complete");
        telemetry.update();
    }

    public void onTeleopLoop(LoopClock clock) {
        // 1) Get the drive command for this loop
        DriveSignal cmd = driveSource.get(clock);

        // 2) Apply it to the drivebase
        drivebase.drive(cmd);

        // 3) Push basic drive telemetry (optional)
        telemetry.addData("Drive", "axial=%.2f lateral=%.2f omega=%.2f",
                cmd.axial, cmd.lateral, cmd.omega);
        telemetry.update();
    }

    public void onStop() {
        // Stop the drive and any mechanisms on disable
        drivebase.drive(DriveSignal.ZERO);
        telemetry.addLine("PhoenixRobot: stopped");
        telemetry.update();
    }
}
```

This is your “Hello TeleOp” Phoenix robot:

* Uses `Drives` to build a mecanum drivebase.
* Uses `StickDriveSource.teleOpMecanumWithSlowMode(...)` to map gamepad sticks → `DriveSource`.
* Each loop:

    * `driveSource.get(clock)` produces a `DriveSignal`.
    * `drivebase.drive(signal)` drives the robot.

Later, you can evolve `PhoenixRobot` into:

* A set of subsystems (implementing `Subsystem`).
* A robot that can be used by both `PhoenixTeleOpBase` and `PhoenixAutoBase`.

---

## 4. Inputs: Gamepads and DriverKit

Phoenix wraps the raw FTC gamepads into a small input layer:

* `Gamepads` – samples `gamepad1` / `gamepad2` from the SDK once per loop.
* `DriverKit` – gives you a *named* view of player inputs:

    * `driverKit.p1().leftStickX()` instead of `gamepad1.left_stick_x`.
    * `driverKit.p2().rightTrigger()` instead of `gamepad2.right_trigger`.
* `Button` / `Axis` – represent logical buttons/axes and are used by higher-level code.

In `PhoenixTeleOpBase`:

* `Gamepads`, `DriverKit`, `Bindings`, and `LoopClock` are constructed for you.
* You can access them via protected helpers:

  ```java
  protected DriverKit driverKit() { ... }  // full DriverKit
  protected DriverKit.Player p1() { ... }  // primary driver
  protected DriverKit.Player p2() { ... }  // secondary driver
  protected Bindings bind() { ... }        // for macros (later)
  protected LoopClock clock() { ... }      // timing helper
  ```

In your `PhoenixRobot`, you usually pass in the `DriverKit` from the TeleOp base and keep it in fields or build drive sources directly.

---

## 5. Driving: DriveSignal + DriveSource + MecanumDrivebase

The Phoenix drive stack is:

```text
DriverKit (gamepad inputs)
          │
          ▼
StickDriveSource  ──►  DriveSource  ──►  DriveSignal  ──►  MecanumDrivebase.drive(...)
                                                (axial, lateral, omega)
```

### 5.1 DriveSignal: blendable drive commands

`DriveSignal` is a simple data class:

```java
public final class DriveSignal {
    public final double axial;
    public final double lateral;
    public final double omega;

    // Common helpers:
    public DriveSignal scaled(double scale) { ... }
    public DriveSignal clamped() { ... }

    // New helpers to make it easy to mix commands:
    public DriveSignal plus(DriveSignal other) { ... }
    public DriveSignal lerp(DriveSignal other, double alpha) { ... }

    public static final DriveSignal ZERO = new DriveSignal(0, 0, 0);
}
```

Conceptually:

* `scaled(s)` – “shrink or grow the whole command” (used by slow mode).
* `plus(other)` – “combine two commands” (for example, driver input + auto correction).
* `lerp(other, t)` – “blend between A and B”:

    * `t = 0.0` → pure A,
    * `t = 1.0` → pure B,
    * `0 < t < 1` → smooth mix.

Phoenix uses these helpers internally, and more advanced examples can use them directly when you mix driver control with auto behaviors.

### 5.2 DriveSource: “how to decide the DriveSignal”

`DriveSource` is an interface:

```java
public interface DriveSource {
    DriveSignal get(LoopClock clock);

    // Optional helpers for composition
    default DriveSource scaledWhen(BooleanSupplier when, double scale) { ... }

    default DriveSource blendedWith(DriveSource other, double alpha) { ... }
}
```

You can think of `DriveSource` as:

> “Anything that decides what the drive command should be this loop.”

Some examples:

* `StickDriveSource.teleOpMecanum(...)` – pure TeleOp sticks.
* `TagAim.teleOpAim(...)` – wraps a base drive to add AprilTag auto-aim on a button.
* (Later) trajectory followers, heading hold, auto-alignment helpers, etc.

The default methods:

* `scaledWhen(when, scale)` – apply slow-mode-style scaling to whatever this source outputs, but only if `when.getAsBoolean()` is true.
* `blendedWith(other, alpha)` – blend this source’s output with another source, using `DriveSignal.lerp(...)`. This is useful for “driver assist” behaviors.

For beginners, you don’t need to *implement* `DriveSource`, you just use the factories and helpers provided by `StickDriveSource` and `TagAim`.

### 5.3 StickDriveSource TeleOp helpers

For beginners, you only need to remember these two static factories:

```java
// P1: standard robot-centric mecanum
StickDriveSource teleOpMecanum(DriverKit kit)

// P1: mecanum with a slow-mode button
StickDriveSource teleOpMecanumWithSlowMode(
        DriverKit kit,
        Button slowButton,
        double slowScale)
```

Example:

```java
this.driveSource = StickDriveSource.teleOpMecanumWithSlowMode(
        driverKit,
        driverKit.p1().rightBumper(), // hold for precision driving
        0.30);
```

Internally, `StickDriveSource` handles:

* Reading the sticks from `DriverKit`.
* Deadband.
* Optional shaping (exponent) near center.
* Mapping the sticks to `(axial, lateral, omega)`.

Your TeleOp code stays clean and high-level.

### 5.4 MecanumDrivebase

`MecanumDrivebase`:

* Knows how to convert `(axial, lateral, omega)` to power commands for the four mecanum motors.
* Encapsulates the details of motor inversion / scaling.

You build it through the `Drives` factory:

```java
this.drivebase = Drives
        .mecanum(hw)
        .names(HW_FL, HW_FR, HW_BL, HW_BR)
        .invertFrontRight()
        .invertBackRight()
        .build();
```

Each loop you just call:

```java
DriveSignal cmd = driveSource.get(clock);
drivebase.drive(cmd);
```

---

## 6. Adding AprilTag Auto-Aim (Optional)

Once your robot drives well, you can add AprilTag-based auto-aim.

The Phoenix sensing stack for tags:

```text
VisionPortal / camera (FTC SDK)
         │
         ▼
FtcVision + Tags.aprilTags(...) ──► AprilTagSensor
         │
         ▼
TagAim.teleOpAim(...) ──► DriveSource (wraps your existing drive)
```

### 6.1 Getting an AprilTagSensor

In your `PhoenixRobot`:

```java
import edu.ftcphoenix.fw.adapters.ftc.FtcVision;
import edu.ftcphoenix.fw.sensing.AprilTagSensor;
import edu.ftcphoenix.fw.sensing.Tags;

// ...

private static final String HW_WEBCAM = "Webcam 1";

private final AprilTagSensor tagSensor;

public PhoenixRobot(HardwareMap hw, DriverKit driverKit, Telemetry telemetry) {
    this.telemetry = telemetry;

    // Drivebase as before...
    this.drivebase = Drives
            .mecanum(hw)
            .names(HW_FL, HW_FR, HW_BL, HW_BR)
            .invertFrontRight()
            .invertBackRight()
            .build();

    // Build the tag sensor using the FTC VisionPortal setup
    this.tagSensor = Tags.aprilTags(
            hw,
            HW_WEBCAM,
            FtcVision.defaultTagProcessorConfig());
}
```

(Your actual `FtcVision` helper may differ; see the sensing docs or examples for exact usage.)

### 6.2 Wrapping your drive with TagAim

Once you have:

* A `DriveSource` from `StickDriveSource`.
* An `AprilTagSensor` from `Tags.aprilTags(...)`.

You can wrap the manual drive with tag-based auto-aim:

```java
import edu.ftcphoenix.fw.sensing.TagAim;

private DriveSource driveSource;

public PhoenixRobot(HardwareMap hw, DriverKit driverKit, Telemetry telemetry) {
    // ... drivebase and tagSensor wiring ...

    DriveSource manual = StickDriveSource.teleOpMecanumWithSlowMode(
            driverKit,
            driverKit.p1().rightBumper(),
            0.30);

    // IDs of the tags you care about (example)
    java.util.Set<Integer> scoringTagIds = java.util.Set.of(1, 2, 3);

    // Hold left bumper to auto-aim; otherwise, pure manual control
    this.driveSource = TagAim.teleOpAim(
            manual,
            driverKit.p1().leftBumper(),
            tagSensor,
            scoringTagIds);
}
```

Behavior:

* **No buttons pressed**: pure mecanum stick drive.
* **Right bumper held**: slow mode scaling for precise movement.
* **Left bumper held**: robot rotates to face the nearest matching AprilTag while still using the driver’s axial/lateral commands.

Because `DriveSource` is composable, you could further wrap this with `scaledWhen(...)` or `blendedWith(...)` for more advanced behaviors, but that’s optional.

---

## 7. Adding a Simple Mechanism (Plant) – Optional First Steps

Phoenix uses **plants** to represent mechanisms like intakes, arms, and shooters.

* `Plant` – “something that accepts a target and updates each loop”.
* `FtcPlants` – helpers that build plants from FTC hardware (`DcMotorEx`, `Servo`, etc.).
* `PlantTasks` – higher-level patterns (e.g., “hold this power for 0.7 seconds”).

For your very first TeleOp, you can simply:

* Use raw FTC motors/servos **or**
* Start with a simple plant.

Example: an intake motor controlled by button A:

```java
import com.qualcomm.robotcore.hardware.DcMotorEx;
import edu.ftcphoenix.fw.actuation.Plant;
import edu.ftcphoenix.fw.adapters.ftc.FtcPlants;

// in PhoenixRobot:

private static final String HW_INTAKE = "intake";

private final Plant intake;
private final DriverKit driverKit;

public PhoenixRobot(HardwareMap hw, DriverKit driverKit, Telemetry telemetry) {
    this.telemetry = telemetry;
    this.driverKit = driverKit;

    // ... drive wiring ...

    // Build a simple “power-only” plant for the intake
    DcMotorEx intakeMotor = hw.get(DcMotorEx.class, HW_INTAKE);
    this.intake = FtcPlants.powerOnly(intakeMotor);
}

public void onTeleopLoop(LoopClock clock) {
    // Drive as before...
    DriveSignal cmd = driveSource.get(clock);
    drivebase.drive(cmd);

    // Simple intake control: button A to run, otherwise stop
    double intakePower = driverKit.p1().a().isPressed() ? +1.0 : 0.0;
    intake.setTarget(intakePower);
    intake.update(clock);

    telemetry.addData("IntakePower", "%.2f", intakePower);
    telemetry.update();
}
```

This keeps the pattern consistent: plants get:

* a `setTarget(...)`, and
* an `update(clock)` call each loop.

Later, you can replace simple power plants with PID plants, setpoint controllers, interpolation tables, and tasks.

---

## 8. Where to Go Next

Once your TeleOp robot:

* drives via `StickDriveSource` + `MecanumDrivebase`,
* optionally aims using `TagAim.teleOpAim(...)`, and
* controls one or two mechanisms,

you’re ready for the next layers:

1. **Tasks and macros**
   *File:* `Tasks & Macros Quickstart.md`
   Learn how to create non-blocking sequences using `Task`, `TaskRunner`, `Tasks`, `DriveTasks`, and `PlantTasks`.
   Example: “Press Y to auto-shoot 3 discs, then stop, while still allowing manual driving.”

2. **Stages and advanced shooters**
   *File:* `Stages & Tasks Quickstart - Shooter Case Study.md`
   See how stages help you structure more complex control flows like buffer + shooter velocity + indexing.

3. **Framework overview and principles**
   *Files:*

    * `Framework Overview.md` – tour of all packages and how they fit.
    * `Framework Principles.md` – design philosophy and rationale.

---

## 9. Cheat Sheet: Names Beginners Should Remember

To keep things simple, here’s the short list of names you should know in the first couple of weeks:

**Robot + OpMode**

* `PhoenixTeleOpBase`
* `PhoenixAutoBase` (later)
* Your `PhoenixRobot` class
* Optional `Subsystem` classes

**Inputs**

* `Gamepads`
* `DriverKit` (and `DriverKit.Player` via `p1()` / `p2()`)

**Drive**

* `Drives`
* `MecanumDrivebase`
* `DriveSignal`
* `DriveSource`
* `StickDriveSource.teleOpMecanum(...)`
* `StickDriveSource.teleOpMecanumWithSlowMode(...)`
* `TagAim.teleOpAim(...)`

**Sensing (optional early)**

* `Tags.aprilTags(...)`
* `AprilTagSensor`

**Mechanisms (optional early)**

* `Plant`
* `FtcPlants`

If you can comfortably use these types, you already understand most of the Phoenix “beginner surface”. The rest of the framework builds on top of the same patterns.

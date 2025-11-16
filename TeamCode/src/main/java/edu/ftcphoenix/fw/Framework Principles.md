# Phoenix Framework Principles

This document captures the design principles that guide the Phoenix FTC framework.

It serves three purposes:

1. **Design compass** for people evolving the framework.
2. **Expectation setting** for students and mentors using the framework.
3. **Consistency checklist** when reviewing new code or APIs.

If you are unsure how to design something in the framework, come back here first.

---

## 1. High‑Level Goals

Phoenix is a *robot‑first* library: its only reason to exist is to make it easier to write **clear,
reliable robot code**.

We optimize for three main goals:

1. **Simple for beginners, useful for experts.**
2. **Predictable, explicit behavior.**
3. **Small, orthogonal building blocks.**

### 1.1 Beginner‑first, with progressive disclosure

* There is always a **“happy path”** for new teams:

    * Drive: `Drives.mecanum(...)` + `StickDriveSource.defaultMecanumWithSlowMode(...)`.
    * Vision: `Tags.aprilTags(...)` + `TagAim.forTeleOp(...)`.
    * Robot lifecycle: extend `PhoenixTeleOpBase` / `PhoenixAutoBase` and delegate to a
      `PhoenixRobot` class.
* Advanced usage is built as **extensions of the same ideas**, not a completely different API:

    * You can still build custom `DriveSource`, `MecanumDrivebase`, subsystems, tasks, etc.
    * You can bypass helpers when needed, but the mental model stays the same.

When designing a new feature, always ask: *“What does the beginner path look like?”* If you can’t
show it in a small example, the design is probably too complex.

### 1.2 Robot‑centric structure

Robot code should read like “what the robot does”, not like plumbing:

* Thin OpMode shells (`PhoenixTeleOpBase`, `PhoenixAutoBase`) own interaction with the FTC SDK (
  `LinearOpMode`, `Telemetry`, loop timing).
* Each season gets a **robot class** (for example `PhoenixRobot`) living under
  `edu.ftcphoenix.robots.<season>` that:

    * Creates subsystems (drive, shooter, intake, etc.).
    * Owns driver control mapping via `DriverKit`.
    * Exposes simple lifecycle hooks (`onTeleopInit`, `onTeleopLoop`, `onAutoInit`, ...).
* Subsystems hide details of plants, sensors, and stages from the OpMode.

The end goal is that OpMode code is boring:

```java

@TeleOp(name = "MyRobot: TeleOp", group = "Phoenix")
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

### 1.3 Predictability and explicit wiring

* No hidden threads or background work: the framework assumes a **single control loop**, driven by
  the OpMode.
* Wiring is **explicit in code**:

    * Motors and servos are created from `HardwareMap` in one place (usually the robot or subsystem
      constructor).
    * Sensors (including AprilTag cameras) are created once and owned by a subsystem or the robot.
* Control logic should be **POJO‑style** (plain Java objects) where possible: easy to unit‑test, no
  SDK dependencies, no lifecycle magic.

---

## 2. Layers and Dependencies

Phoenix is intentionally layered. Understanding the layers helps you know **where new code should
live**.

At a high level:

1. **Robots & subsystems** – season‑specific logic.
2. **Core framework** – platform‑agnostic building blocks (math, drive, stages, tasks, sensing
   interfaces, etc.).
3. **FTC adapters** – `adapters.ftc` and `fw.input` code that talks directly to the FTC SDK.

### 2.1 Robots and OpModes

* Robot code lives under:

    * `org.firstinspires.ftc.teamcode.robots` – thin OpMode shells.
    * `edu.ftcphoenix.robots.<season>` – robot classes and subsystems for that season.
* `PhoenixTeleOpBase` and `PhoenixAutoBase` own:

    * OpMode lifecycle (`init`, `start`, `loop`, `stop`).
    * Loop timing (`LoopClock`).
    * Access to `HardwareMap`, `Telemetry`, and `DriverKit`.

**Principle:** OpMode shells should only coordinate *when* things happen. All logic should live in
the robot and subsystems.

### 2.2 Core packages (platform‑agnostic)

These packages do *not* depend on FTC SDK types:

* `fw.core` – math and controllers

    * `PidController`, `Pid` and friends.
* `fw.util` – utilities

    * `LoopClock`, `MathUtil`, `Units`.
* `fw.hal` – hardware abstraction interfaces

    * `MotorOutput`, `ServoOutput`: pure “write‑only” outputs, no SDK dependency.
* `fw.drive` – drive math and abstractions

    * `DriveSignal`, `DriveSource`, `MecanumDrivebase`, `Drives` builder.
* `fw.sensing` – sensor abstractions and derived sources

    * `AprilTagObservation`, `AprilTagSensor`, `BearingSource`, `TagAim`, `Tags`.
* `fw.stage` – reusable update “nodes”

    * `setpoint.SetpointStage` – maps high‑level goals to numeric targets and drives a plant.
    * `buffer.BufferStage` – rate‑limited or buffered commands.
* `fw.task` – small state machines

    * `Task`, `TaskRunner`, `ParallelAndTask`, `SequenceTask`, etc.
* `fw.robot` – base classes and subsystem API

    * `PhoenixTeleOpBase`, `PhoenixAutoBase`, `Subsystem`.

**Principle:** new features that *don’t* need the FTC SDK should go into core packages, not into the
adapters.

### 2.3 FTC adapters and input

Two places are allowed to touch FTC SDK types directly:

* **`fw.adapters.ftc`**

    * `FtcHardware` – wraps `DcMotor`, `DcMotorEx`, `Servo`, `CRServo` into `MotorOutput` /
      `ServoOutput`.
    * `FtcPlants` – builds `Plant` implementations (power, velocity, position, pair
      variants) from FTC hardware.
    * `FtcVision` – builds `VisionPortal` / `AprilTagProcessor` and adapts them to `AprilTagSensor`.
* **`fw.input`**

    * `GamepadDevice`, `Gamepads`, `DriverKit` – wrap the FTC `Gamepad` objects and provide a
      higher‑level view:

        * Debounced buttons.
        * Triggers as axes.
        * Named accessors (`p1()`, `p2()`).

This is a conscious exception: gamepad usage is simple and pervasive enough that we keep it in
`fw.input` instead of adding a second adapter layer.

**Principle:** if your new code needs `HardwareMap`, `Gamepad`, or other SDK types, it probably
belongs in `fw.adapters.ftc`, `fw.input`, or the robot layer – not inside core packages.

### 2.4 Dependency rules

To keep the design understandable:

* Core packages may depend on each other (for example, `drive` depends on `util`).
* Adapters may depend on core packages *and* the FTC SDK.
* Robots and subsystems may depend on both core and adapters.

But:

* Core packages must **never** depend on `fw.adapters.ftc`, `fw.input`, or robot code.
* Adapters should not depend on robot code or season‑specific classes.

A good mental picture is a set of **downward‑pointing arrows**:

```
robot / subsystems
        ↓
   core packages
        ↓
 FTC adapters + fw.input
        ↓
      FTC SDK
```

---

## 3. Actuation Principles

Phoenix has two main levels of actuation abstraction:

1. **HAL outputs** (`MotorOutput`, `ServoOutput`) – dumb, write‑only sinks.
2. **Plants** (`Plant`) – semantically meaningful mechanisms driven by setpoints.

### 3.1 HAL outputs (`fw.hal`)

* `MotorOutput`

    * `setPower(double power)`
    * `getLastPower()`
* `ServoOutput`

    * `setPosition(double position)` in `[0, 1]`
    * `getLastPosition()`

They are intentionally minimal:

* No encoder or velocity methods.
* No FTC types in signatures.

**Principle:** `MotorOutput` / `ServoOutput` represent “what we can command right now,” not the full
physical device.

### 3.2 Plants (`Plant`) and targets

`Plant` represents a **setpoint‑driven mechanism**:

* `setTarget(double target)` – desired target (units are mechanism‑defined).
* `update(double dtSec)` – advance the mechanism toward its target.
* `atSetpoint()` – optional “good enough” check.

Examples:

* Power plant – target is power in `[-1, 1]` (open‑loop).
* Velocity plant – target is angular velocity (rad/s).
* Position plant – target is angle (radians) or servo position `[0, 1]`.
* Paired plants – two motors/servos treated as one mechanism.

**Principle:** once you know the units (power, rad/s, radians, etc.), you should work in setpoints
and plants, not raw `setPower` calls scattered around.

### 3.3 FtcHardware and FtcPlants together

These two classes are designed to be **parallel and complementary**:

* `FtcHardware` – raw outputs

    * `motor(hw, "name", inverted)` → `MotorOutput`.
    * `servo(hw, "name", inverted)` → `ServoOutput`.
    * `crServoMotor(hw, "name", inverted)` → `MotorOutput` backing a `CRServo`.
* `FtcPlants` – plants built from hardware

    * Power:

        * `power(hw, "intake", false)`
        * `powerPair(hw, "leftIntake", false, "rightIntake", true)`
    * Velocity (using `DcMotorEx.setVelocity` under the hood):

        * `velocity(hw, "shooter", false, ticksPerRev)`
        * `velocityPair(hw, "shooterLeft", false, "shooterRight", true, ticksPerRev)`
    * Position:

        * `servoPosition(hw, "pusher", false)`
        * `servoPositionPair(hw, "clawLeft", false, "clawRight", true)`
        * `motorPosition(hw, "arm", false, ticksPerRev)`
        * `motorPositionPair(hw, "armLeft", false, "armRight", true, ticksPerRev)`

**Principles:**

* For **beginners**, prefer `FtcPlants` from `HardwareMap` – they hide SDK details and use physical
  units.
* For more complex mechanisms, you can build custom plants on top of `MotorOutput` / `ServoOutput`.
* Keep units clear in method names and docs (`velocity` = rad/s, `power` = normalized, etc.).

---

## 4. Sensing Principles

Sensors are treated as **sources of simple data types**, with small, focused interfaces.

### 4.1 Value types and sources

* Example: AprilTags

    * `AprilTagObservation` – immutable value: id, pose, range, bearing, timestamp, etc.
    * `AprilTagSensor` – interface for “something that can report tag observations.”
    * `Tags.aprilTags(...)` – FTC‑specific factory that uses `FtcVision` internally.

Code that *uses* sensor data should depend on:

* The value type (e.g., `AprilTagObservation`).
* The source interface (e.g., `AprilTagSensor`).

It should not know about `VisionPortal` or vendor‑specific configuration.

### 4.2 Derived sources and helpers

Sometimes we build **derived sources** on top of raw sensors:

* `TagAim` / `TagAimDriveSource` use AprilTag bearing to produce an angular velocity command.
* Future examples might include gyro‑based heading sources, distance sensors, etc.

**Principles:**

* Keep sensor wrappers small and composable.
* Separate **hardware acquisition** (in `FtcVision`, `Tags`, etc.) from **behavior** (aiming,
  alignment, auto‑lift, etc.).
* Prefer small value types and interfaces over large classes with many responsibilities.

---

## 5. Gamepad Input Principles

Gamepad handling lives in `fw.input` and is intentionally **FTC‑aware**.

### 5.1 Goals

* Provide a **clean, consistent API** for driver code:

    * `driverKit.p1().buttonA().isPressed()`
    * `driverKit.p2().leftTrigger().get()`
* Avoid common pitfalls:

    * Stick center bias.
    * Y‑axis sign conventions.
    * Button debouncing and toggles.

### 5.2 Design

* `GamepadDevice` wraps a single FTC `Gamepad` and:

    * Normalizes stick axes.
    * Exposes button/axis wrappers (e.g., `ButtonInput`, `AxisInput`).
* `Gamepads` owns both gamepads and a registry of inputs.
* `DriverKit` is the main entry point for robot code:

    * `p1()` / `p2()` access primary and secondary drivers.
    * Central place to wire combos or shared semantics if needed.

**Principles:**

* Robot code should talk to `DriverKit`, not directly to `Gamepad`.
* All gamepad sampling should be done in one place per loop (typically in `PhoenixTeleOpBase`), then
  used downstream.
* Keep common patterns (like slow‑mode and tag‑aim hold buttons) consistent across OpModes.

---

## 6. Stages, Tasks, and the Update Model

Phoenix assumes a **single, cooperative control loop** driven from TeleOp/Auto.

### 6.1 Stages as reusable update nodes

Stages live under `fw.stage.*` and follow a common pattern:

* They are **plain Java objects** with:

    * Configuration (provided at construction time).
    * Small update methods (`update(dtSec)`, or similar).
* They **do not** start threads or manage their own loop.

`SetpointStage<G>` is a key example:

* Maps a high‑level goal (`G`) to a numeric target setpoint.
* Drives a `Plant` (via `setTarget()` + `update(dtSec)`).

`BufferStage` is another:

* Applies rate limiting or buffering to commands before they reach the hardware.

**Principle:** stages are “math and state”, not “managers.” They should be cheap to construct and
easy to reason about.

### 6.2 Tasks: structured multi‑step logic

Tasks in `fw.task` package provide a way to write time‑extended behaviors without sprinkling state
everywhere:

* `Task` – interface: `start()`, `update(dtSec)`, `isFinished()`.
* `TaskRunner` – updates a task each loop.
* Combinators:

    * `SequenceTask` – run tasks one after another.
    * `ParallelAndTask` – run tasks in parallel until all finish.
    * `WaitUntilTask` / `InstantTask` – simple building blocks.

Typical usage:

* TeleOp:

    * Small tasks (e.g., “fire one shot” that spins up shooter, pulses feeder, retracts).
* Auto:

    * Longer sequences combining drive, mechanisms, and waits.

**Principle:** if you find yourself writing a lot of boolean flags like `isShooting`,
`hasStartedIntake`, etc., consider making a `Task` instead.

---

## 7. API Style and Naming

Consistency across the framework makes it easier to guess how things work.

### 7.1 Builders and factories

We prefer **builder‑style factories** for complex objects:

* Drives:

    *
    `Drives.mecanum(hw).frontLeft("fl").frontRight("fr").backLeft("bl").backRight("br").invertRightSide().build();`
* Plants:

    * `FtcPlants.velocityPair(hw, "shooterLeft", false, "shooterRight", true, ticksPerRev);`
* Vision:

    * `Tags.aprilTags(hw, "Webcam 1");`

Principles:

* Avoid giant constructors with many positional parameters.
* Use method names to make configuration self‑documenting.
* Prefer immutable configuration after construction (no setters for critical wiring).

### 7.2 Lifecycle naming

Robot and subsystem lifecycle methods are standardized:

* `onTeleopInit`, `onTeleopLoop`, `onAutoInit`, `onAutoLoop`, `onStop`.

Principles:

* Subsystems should avoid exposing their own custom lifecycle names.
* OpModes always call the robot, which calls subsystems – not the other way around.
* If a subsystem needs an extra one‑time hook, consider adding it as a method on the subsystem and
  calling it from the robot, rather than inventing a new global lifecycle concept.

### 7.3 Semantic naming for actuation

Some naming rules we strive to follow:

* **“power”** → normalized, unitless `[-1, 1]` (or `[0, 1]` if one‑sided).
* **“velocity”** → physical angular rate, usually rad/s.
* **“position” / “angle”** → physical angle (radians) or servo position `[0, 1]`.
* **“Pair”** → two physical outputs treated as one mechanism (`powerPair`, `velocityPair`, etc.).

Principles:

* Don’t mix power and velocity semantics in one API.
* Keep naming between single and pair variants consistent.
* Document units clearly in Javadoc.

---

## 8. Checklist for New Code

When you add or change framework code, run through this checklist:

1. **Does this make robot code simpler, or at least more consistent?**
2. **Is there a clear beginner entry point?** If not, should there be?
3. **Does it follow existing naming and API shapes (factories, lifecycle, TagAim patterns, etc.)?**
4. **Is SDK usage confined to adapters and base classes (plus `fw.input` for gamepads)?**
5. **Is math centralized (via `MathUtil`, etc.) rather than duplicated?**
6. **Can I explain this feature to a new student using the package map + examples?**
7. **Did I update or add examples and documentation accordingly?**

If the answer to any of these is “no” or “not sure,” revisit the design or update the docs.

---

By keeping these principles in mind, Phoenix can grow with your robots and your team’s skills
without turning into a tangle of special cases. The framework should feel like a **solid backbone**
that your robot code hangs off of, not a maze you’re afraid to touch.

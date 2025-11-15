# Phoenix Framework Principles

This document captures the design principles that guide the Phoenix FTC framework.

It serves three purposes:

1. **Design compass** for people evolving the framework.
2. **Expectation setting** for students and mentors using the framework.
3. **Consistency checklist** when reviewing new code or APIs.

If you are unsure how to design something in the framework, come back here first.

---

## 1. High-Level Goals

Phoenix exists to make FTC robot code:

* **Friendly for students** – a motivated student should be able to understand and modify TeleOp/Auton code in a few meetings.
* **Stable for competitions** – behavior should be predictable, debuggable, and not fragile.
* **Reusable for future seasons** – good ideas should live in the framework, not be re-written every year.

The guiding vision:

> **Robot-specific code should read like a story about what the robot does.**
>
> Low-level wiring, control patterns, and boilerplate live in the framework.

Robot stories are expressed primarily through **PhoenixRobot + subsystems** and **thin TeleOp/Auto shells**.

---

## 2. Core Principles

### 2.1 Simplicity of Robot-Specific Code Comes First

When there is a tradeoff between framework cleverness and **OpMode simplicity**, we choose simplicity.

Robot code written by students should:

* Avoid long chains of SDK calls (`hardwareMap.get(...)`, `setMode(...)`, etc.).
* Avoid duplicating math (drive mixing, PID details, clamping, AprilTag plumbing).
* Prefer 1–3 obvious helper calls instead.
* Live mostly in **PhoenixRobot** and its **subsystems**, not in the OpMode classes.

**Smell:** if a typical TeleOp or Auto shell needs more than a handful of framework imports and ~30–50 lines of logic, we should consider adding helpers or moving code into PhoenixRobot/subsystems.

---

### 2.2 Separation of Concerns and Layering

We enforce clear layers with narrow responsibilities:

* **FTC SDK layer** – `HardwareMap`, `Gamepad`, `DcMotorEx`, `Servo`, `VisionPortal`, etc.
* **HAL / Adapters** – `MotorOutput`, `ServoOutput`, `FtcHardware`, `FtcVision`.
* **Framework interfaces & patterns** – `DriveSource`, `DriveSignal`, `Plants`, `Task`, `AprilTagSensor`, `PidController`, `BearingSource`, `TagAimController`, `TagAimDriveSource`.
* **Robot logic** – `PhoenixRobot`, subsystems, TeleOp and Auto shells built on top of the interfaces.

Robot logic should depend on the framework interfaces, **not directly on the SDK**, except in very rare cases.

**Rule-of-thumb:**

* If a class references `HardwareMap`, `Gamepad`, or `VisionPortal`, it probably belongs in an adapter or base class, not in a subsystem.

---

### 2.3 Beginner / Advanced Paths for Most Features

For significant features (drive, mechanisms, vision, tasks, aiming), we try to offer:

* A **beginner path**:

    * 1–2 helper calls.
    * Few types to understand.
    * Good defaults.
    * Encourages correct use of the framework.

* An **advanced path**:

    * More explicit configuration and wiring.
    * Access to lower-level interfaces (e.g., `DriveSource`, `TagAimController`, `BearingSource`, `SetpointStage`).

We **do not** hide the advanced layers, but beginners are not forced to understand them on day one.

Examples:

* Drive

    * Beginner: `StickDriveSource.defaultMecanumWithSlowMode(driverKit, button, scale)` and `Drives.mecanum(hardwareMap)...build()`.
    * Advanced: custom `DriveSource`, custom `MecanumDrivebase` configuration.

* AprilTags and aiming

    * Beginner: `Tags.aprilTags(...)` + `TagAim.forTeleOp(sticks, aimButton, sensor, tagIds)`.
    * Advanced: construct `AprilTagSensor`, `BearingSource`, and a tuned `TagAimController`, or use `TagAimDriveSource` directly in TeleOp and Auto.

* Robot lifecycle

    * Beginner: extend `PhoenixTeleOpBase` / `PhoenixAutoBase` and call `new PhoenixRobot(hardwareMap, driverKit(), telemetry)`.
    * Advanced: build additional subsystems, tasks, and planners on top of the same base.

If you add a new powerful primitive, ask: **what is the beginner facade?**

---

### 2.4 Consistent Naming and API Shapes

Naming must be predictable and consistent across packages.

Some conventions:

* **Factory / builder naming**

    * `of(...)` → wrap an existing object into a framework type.
    * `defaultXxx(...)` → common beginner-friendly setup.
    * `forTeleOp(...)`, `forAuto(...)` → helpers that wire several pieces for a specific mode.
    * `mecanum(hardwareMap)` → builder starting point.

* **Pairs / duals**

    * Use `Pair` consistently: `powerPair`, `velocityPair`, `servoPositionPair`, `motorPositionPair`.

* **Input**

    * `leftX`, `leftY`, `rightX`, `rightY`, `leftTrigger`, `rightTrigger`.
    * Buttons: `buttonA`, `buttonB`, `buttonX`, `buttonY`, `leftBumper`, `rightBumper`, `dpadUp`, etc.

* **Sensing & Aiming**

    * `AprilTagSensor`, `AprilTagObservation`, `Tags.aprilTags`, `BearingSource`, `TagAimController`, `TagAim.forTeleOp`, `TagAimDriveSource`.

* **Lifecycle**

    * Robot lifecycle: `onTeleopInit`, `onTeleopLoop`, `onAutoInit`, `onAutoLoop`, `onStop` in `PhoenixRobot` and `Subsystem`.
    * Base OpMode hooks: `onInitRobot`, `onStartRobot`, `onLoopRobot`, `onStopRobot` in `PhoenixTeleOpBase` / `PhoenixAutoBase`.

When adding new APIs, align with existing names unless there's a strong reason not to.

---

### 2.5 Declarative Robot Code, Not Plumbing

Robot TeleOps and Autos should express **what** the robot does, not **how** the SDK is wired.

Bad (too much plumbing in robot code):

```java
DcMotorEx fl = hardwareMap.get(DcMotorEx.class, "fl");
fl.setMode(DcMotor.RunMode.RUN_USING_ENCODER);
// ...repeat for 3 more motors...
```

Better:

```java
drivebase = Drives
        .mecanum(hardwareMap)
        .frontLeft("fl")
        .frontRight("fr")
        .backLeft("bl")
        .backRight("br")
        .invertRightSide()
        .build();
```

Even better in robot structure:

* TeleOp shell: construct `PhoenixRobot` and forward lifecycle methods.
* `PhoenixRobot`: constructs `DriveSubsystem` with a single call to `Drives.mecanum(...)` and a `StickDriveSource` helper.

When you see repeated boilerplate in multiple OpModes, move it into a helper or a subsystem.

---

### 2.6 Predictable Time Semantics (LoopClock)

Phoenix uses **one source of truth for time**:

* `LoopClock` lives in the base OpModes.
* `LoopClock` is passed into:

    * `DriveSource.get(LoopClock)`
    * `BearingSource.sample(LoopClock)`
    * `TagAimController.update(LoopClock, BearingSample)`
    * Subsystem lifecycle: `onTeleopLoop(LoopClock)` / `onAutoLoop(LoopClock)`

We avoid calling `System.nanoTime()` or `ElapsedTime` directly in subsystems and framework code.

**Rule:** If something needs `dtSec` or timing, it should get it via `LoopClock`, not by inventing its own clock.

---

### 2.7 Safe Defaults and Centralized Math

We aim for safe, predictable behavior by default:

* All clamping goes through `MathUtil` (`clamp`, `clamp01`, `clampAbs`, `deadband`).
* All joystick shaping lives in `StickDriveSource`.
* All drive mixing for mecanum lives in `MecanumDrivebase`.
* AprilTag selection policy lives in `AprilTagSensor` implementation.
* Aiming behavior lives in `TagAimController` and `TagAim`.

This avoids copy-pasting math and ensures that fixing a bug in one place improves all users.

When you see copy-pasted math (clamp, deadband, scaling, squaring inputs), consider whether it belongs in `MathUtil` or a central helper instead.

---

### 2.8 Testability and Debuggability

Subsytems should be easy to reason about and debug:

* Pure logic (math, control) is separated from SDK adapters.
* Interfaces (`DriveSource`, `PidController`, `AprilTagSensor`, `BearingSource`) allow test doubles.
* TeleOp / Auto classes should expose meaningful telemetry (bearing error, tag range, at-setpoint flags, etc.).

New framework types should:

* Expose `getLastXxx()` when it helps with telemetry.
* Provide small, composable units that can be tested without hardware.

---

### 2.9 Minimal Dependencies on FTC SDK

The bulk of the framework code should compile and make sense even if the FTC SDK changes.

Rules:

* Only adapters and base OpModes (`PhoenixTeleOpBase`, `PhoenixAutoBase`, `FtcHardware`, `FtcVision`) should mention SDK classes directly.
* Most packages (`fw.drive`, `fw.sensing`, `fw.stage`, `fw.task`, `fw.util`) should be SDK-agnostic.

This keeps our core logic reusable and easier to reason about.

---

### 2.10 Evolution Over Backward Compatibility (Within Reason)

This project is small enough that we can improve designs and update callers rather than support multiple legacy APIs.

Guidelines:

* It's acceptable to break an API if:

    * The new design clearly simplifies usage or reduces bugs.
    * All call sites can be updated in a controlled way.

* When you change an API:

    * Update the examples.
    * Update `Beginner's Guide.md`, `Framework Overview.md` and this `Framework Principles.md` if the change affects how users think about the framework.

We do **not** keep legacy constructors just for historical reasons.

We consciously accept small tradeoffs (e.g., always passing `DriverKit` into `PhoenixRobot`, even in Auto) when it keeps constructors and examples simpler to explain.

---

## 3. Package-Specific Principles

This section applies the core ideas to each package and suggests how to extend them.

### 3.1 `fw.input`

* `Gamepads` and `DriverKit` are the **front door** for controller input.
* All raw `Gamepad` usage should be wrapped in `GamepadDevice`.
* Stick calibration (center bias correction) lives in `GamepadDevice`.
* Drive mapping and shaping live in `StickDriveSource`.
* Both TeleOp and Auto base classes may construct a `DriverKit`, but Auto is not required to use it.

When extending input:

* Prefer to add new helpers to `DriverKit`/`Bindings` instead of accessing `Gamepad` directly.
* Keep naming consistent (`leftX`, `buttonA`, `dpadUp`, etc.).

### 3.2 `fw.drive` and `fw.drive.source`

* `DriveSignal` is the single currency for robot motion.
* `DriveSource` describes anything that can produce a `DriveSignal`.
* `DriveSource.get(LoopClock)` is the standard method signature.
* `MecanumDrivebase` owns all mecanum mixing math.

When adding new drive sources:

* Implement `DriveSource.get(LoopClock)`.
* Avoid taking dependencies on SDK; use `Axis`/`Button` if you need inputs.
* Consider whether a beginner helper is appropriate (`defaultXxx`, `forTeleOp`).

Examples:

* `StickDriveSource` maps driver sticks to `DriveSignal`.
* `TagAimDriveSource` (or `TagAim.forTeleOp`) wraps a base `DriveSource` and overrides omega while aiming.

### 3.3 `fw.adapters.ftc` and `fw.hal`

* This is the **only place** the framework should know about specific FTC hardware classes.
* `FtcHardware` is thin and boring – no complex logic.
* `FtcVision` is the front door for VisionPortal/AprilTagProcessor setup.

When adding new hardware wrappers:

* Keep them simple adapters (no hidden side effects).
* Route all normalization (clamping, inversion) through `MathUtil`.
* Avoid leaking SDK types into other packages.

### 3.4 `fw.adapters.plants` and `fw.stage`

* `Plants` provide standard building blocks for mechanisms.
* `SetpointStage` owns the state machine for achieving a target.

When adding plants:

* Use consistent names (`power`, `velocity`, `servoPosition`, `motorPosition`, and their `Pair` variants).
* Keep units explicit (rad/s, radians, [0,1], etc.).
* Avoid duplicating plant logic; instead, parameterize existing patterns.

### 3.5 `fw.sensing` (AprilTags and beyond)

* `AprilTagSensor` abstracts over the VisionPortal + AprilTagProcessor details.
* `AprilTagObservation` carries ID, bearing (radians), and range (inches) when metadata is available.
* `Tags.aprilTags(...)` is the beginner-friendly factory.
* `BearingSource` is a small abstraction: “do we have a target, and what is its bearing?”.
* `TagAimController` is the control-logic layer: bearing → omega.
* `TagAim.forTeleOp(...)` and `TagAimDriveSource` are the TeleOp convenience layers that wrap an existing `DriveSource`.

When extending sensing (e.g., more vision or sensors):

* Follow the same pattern:

    * Adapter talks to SDK (`FtcVision`).
    * Sensor interface lives in `fw.sensing`.
    * Beginner helper (factory) builds the sensor (`Tags`, etc.).
    * Optional controller layer turns sensor values into commands (`TagAimController`).
    * Optional TeleOp helper wraps existing `DriveSource` or subsystems (`TagAim.forTeleOp`).

### 3.6 `fw.task` and `fw.robot`

* `Task` expresses behavior over time.
* `TaskRunner` drives the task graph.
* `PhoenixAutoBase` glues tasks into an OpMode.
* `PhoenixRobot` owns subsystems and forwards lifecycle events.
* `Subsystem` defines `onTeleopInit/onTeleopLoop/onAutoInit/onAutoLoop/onStop`.

When adding new task or robot patterns:

* Keep semantics obvious: `SequenceTask` runs children in order, `ParallelAllTask` waits for all, etc.
* Prefer composition over large monolithic tasks.
* Keep TeleOp and Auto shells as thin as possible – they should mainly construct `PhoenixRobot` and forward lifecycle calls.

### 3.7 Configuration and Naming

While not fully centralized yet, we aim toward:

* A single place (or small number of places) that define:

    * Motor names (`"fl"`, `"fr"`, shooter motors, etc.).
    * Camera names (`"Webcam 1"`).
    * AprilTag ID sets used by the robot.

When adding new subsystems or robots:

* Prefer constants or configuration objects over scattering magic strings.
* Keep names descriptive and consistent with the rest of the framework.

---

## 4. Documentation and Examples

Code and docs must evolve together.

When changes touch how students *use* the framework:

* Update `Overview.md` with new beginner and advanced usage (including PhoenixRobot + base classes + TagAim).
* Update `Principles.md` if a new concept or pattern becomes central.
* Add or update an example in the examples package that demonstrates the new capability.

Examples are considered part of the documentation contract: **every major pattern should have at least one concrete OpMode that uses it.**

---

## 5. Checklist for New Framework Code

Before merging or committing a new framework feature, ask:

1. **Does this make robot code simpler, or at least more consistent?**
2. **Is there a clear beginner entry point?** If not, should there be?
3. **Does it follow existing naming and API shapes (factories, lifecycle, TagAim patterns, etc.)?**
4. **Is SDK usage confined to adapters and base classes?**
5. **Is math centralized (via `MathUtil`, etc.) rather than duplicated?**
6. **Can I explain this feature to a new student using the package map + examples?**
7. **Did I update or add examples and documentation accordingly?**

If the answer to any of these is “no” or “not sure,” revisit the design or update the docs.

---

By keeping these principles in mind, Phoenix can grow with your team over multiple seasons without turning into a tangle of special cases and one-off hacks. The framework should feel like a *friendly skeleton* your robot code hangs off of, not a maze you’re afraid to touch.

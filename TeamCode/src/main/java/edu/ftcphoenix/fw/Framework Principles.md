# Phoenix Framework Principles

This document captures the design principles that guide the Phoenix FTC framework.

It serves three purposes:

1. **Design compass** for people evolving the framework.
2. **Expectation setting** for students and mentors using the framework.
3. **Consistency checklist** when reviewing new code or APIs.

If you are unsure how to design something in the framework, come back here first.

---

## 1. High‑Level Goals

Phoenix exists to make it easier to write **clear, robust robot code** that can grow from a simple
TeleOp into a complex system without turning into spaghetti.

High‑level goals:

1. **Simple for beginners, powerful for experts**

    * New students should be able to get a driving TeleOp with a few clear types and helpers.
    * Advanced users should be able to build sophisticated controllers and interactions on top of
      the
      same core abstractions.

2. **Robot code describes behavior, not plumbing**

    * Code in your `PhoenixRobot` and subsystems should read like:

        * "drive with mecanum from gamepad sticks"
        * "hold shooter at 2000 RPM while feeding discs".
    * Raw SDK details (`DcMotorEx`, `VisionPortal`, etc.) should live behind adapters and factories.

3. **Clear layering and testable core**

    * Core logic should be **plain Java** (POJOs): no hidden lifecycle, no direct OpMode calls.
    * The framework is layered so most logic can be unit‑tested without an FTC robot controller.

4. **One core abstraction per domain**

    * Drive → `DriveSource` produces `DriveSignal` each loop.
    * Mechanisms → `Plant` wraps a single mechanism.
    * Behavior → `Task` describes non‑blocking actions.
    * Sensing → `FeedbackSource<T>` provides time‑stamped sensor values.

5. **Composition over inheritance and special cases**

    * Build complex behavior by **wrapping** and **blending** small pieces, not by creating one-off
      megaclasses.
    * Example: start from a manual `DriveSource`, wrap it with `TagAim.teleOpAim(...)`, then wrap
      again with slow mode using `scaledWhen(...)`.

6. **Non‑blocking, loop‑friendly design**

    * Nothing in the core should call `sleep()` or block the OpMode thread.
    * TeleOp and Auto both follow the same loop‑driven model: update inputs, compute outputs, update
      plants/tasks.

7. **Documentation and examples are first‑class**

    * Every new concept should ship with at least one small, realistic example.
    * Beginner docs define a **small surface area** of names students must learn (the "beginner
      surface").

---

## 2. Layers and Dependencies

Phoenix is intentionally layered. Understanding the layers helps you know **where new code should
live** and what it may depend on.

High‑level picture:

```text
FTC SDK (OpModes, HardwareMap, Gamepad, DcMotorEx, Servo, VisionPortal, etc.)
    │
    ▼
Phoenix adapters + HAL  (fw.adapters.ftc, fw.hal)
    │
    ▼
Core Phoenix APIs        (fw.drive, fw.input, fw.actuation, fw.sensing, fw.task, fw.robot)
    │
    ▼
Robot‑specific code      (PhoenixRobot, subsystems, TeleOp/Auto shells)
```

### 2.1 Robot & subsystem layer (season‑specific)

* Lives in your own packages, e.g. `edu.ftcphoenix.robots.phoenix`.
* Contains `PhoenixRobot` and optional `Subsystem` classes.
* May depend on:

    * Core Phoenix APIs (`fw.drive`, `fw.input`, `fw.actuation`, `fw.sensing`, `fw.task`,
      `fw.robot`).
    * FTC SDK (for hardware names, if needed at construction time).
* Should **not** depend on low‑level adapter internals beyond factories like `FtcPlants` or
  `Tags.aprilTags(...)`.

### 2.2 Core Phoenix APIs

* Packages:

    * `fw.robot` – base OpModes (`PhoenixTeleOpBase`, `PhoenixAutoBase`), `Subsystem`.
    * `fw.drive` – `DriveSignal`, `DriveSource`, drivebases, drive helpers.
    * `fw.input` – `Gamepads`, `DriverKit`, `Button`, `Axis`, `Bindings`.
    * `fw.actuation` – `Plant` and plant helpers.
    * `fw.sensing` – `FeedbackSource`, `FeedbackSample`, `AprilTagSensor`, `TagAim`, etc.
    * `fw.task` – `Task`, `TaskRunner`, `Tasks`, `DriveTasks`, `PlantTasks`.
* May depend on:

    * `fw.hal` interfaces.
    * Simple math/util packages (`fw.util`).
* Should **not** depend directly on FTC SDK types (`HardwareMap`, `DcMotorEx`, etc.). Those belong
  in adapters.

### 2.3 Adapters and HAL

* `fw.hal` – hardware abstraction layer:

    * Defines small interfaces like `PowerOutput`, `ServoOutput`, etc.
    * Core actuation, drive, and sensing can rely on these instead of concrete SDK types.
* `fw.adapters.ftc` – glue to the FTC SDK:

    * Classes that construct HAL types from FTC hardware (`DcMotorEx`, `Servo`, `VisionPortal`).
    * FTC‑specific plants via `FtcPlants`, vision helpers via `FtcVision`, etc.
* These packages are allowed to depend on the FTC SDK.

### 2.4 Dependencies rule of thumb

1. Robot code → may depend on core APIs + selected adapter factories.
2. Core APIs → may depend on HAL and util packages.
3. HAL + adapters → may depend on the FTC SDK.
4. **Never** depend on robot packages from the framework.

If a new feature breaks layering (e.g., core drive code takes a `DcMotorEx` instead of a HAL type),
rethink the design.

---

## 3. Core Abstractions Per Domain

Phoenix follows a "one core abstraction per domain" rule. This keeps concepts small and reusable.

### 3.1 Input

* `Gamepads` – wraps `gamepad1` / `gamepad2` and samples once per loop.
* `DriverKit` – named view of driver inputs (e.g., `p1().leftStickX()`, `p2().rightTrigger()`).
* `Axis` / `Button` – logical inputs used by higher‑level code.
* `Bindings` – maps buttons to actions (start/cancel tasks, toggle modes, etc.).

**Principles:**

* Robot code should refer to `DriverKit` instead of raw `gamepad` fields.
* Most button logic should live in `Bindings` in combination with `TaskRunner`.

### 3.2 Drive

**Core types:**

* `DriveSignal` – desired motion for this loop: `(axial, lateral, omega)`.
* `DriveSource` – something that produces a `DriveSignal` each loop.
* `MecanumDrivebase` (and other drivebases) – apply a `DriveSignal` to hardware.

**TeleOp presets:**

* `GamepadDriveSource.teleOpMecanum(DriverKit)` – standard P1 mecanum mapping.
* `GamepadDriveSource.teleOpMecanumWithSlowMode(DriverKit, Button slowButton, double slowScale)` –
  same mapping with a slow‑mode button.
* `TagAim.teleOpAim(DriveSource baseDrive, Button aimButton, AprilTagSensor sensor,
  Set<Integer> tagIds)` – wraps a base drive with AprilTag auto‑aim on a button.

These `teleOp...` methods define the **beginner surface** for drive. Examples and docs should use
these names first.

**Blending and composition:**

* `DriveSignal` is immutable and provides:

    * `scaled(double)` – scale a signal (used for slow mode, fine control).
    * `plus(DriveSignal)` – add a correction vector.
    * `lerp(DriveSignal, double alpha)` – blend between two signals.
* `DriveSource` has default methods for composition:

    * `scaledWhen(BooleanSupplier when, double scale)` – apply global scaling when a condition is
      true.
    * `blendedWith(DriveSource other, double alpha)` – mix this source with another using
      `DriveSignal.lerp(...)`.

Patterns to prefer:

* Start from a simple manual `DriveSource` (e.g., `teleOpMecanum`).
* Wrap it with behavior sources (`TagAim.teleOpAim(...)`).
* Add global slow mode via `scaledWhen(...)`.
* Avoid introducing many special‑case "drive helper" classes when simple composition suffices.

### 3.3 Mechanisms (Plants)

**Core types:**

* `Plant` – mechanism that accepts a target and updates each loop.
* `FtcPlants` – factories that wrap FTC hardware (`DcMotorEx`, `Servo`, etc.) into plants.

Examples:

* `FtcPlants.powerOnly(DcMotorEx motor)` – simple power plant.
* `FtcPlants.velocity(DcMotorEx motor, double ticksPerRev)` – velocity‑controlled plant.
* Dual/paired variants for common shooter and conveyor setups.

Principles:

* Robot code should talk to plants, not motors.
* Plants own any necessary control logic (PID, interpolation tables, etc.).
* Plants are updated once per loop with `update(clock)`.

### 3.4 Behavior (Tasks)

**Core types:**

* `Task` – unit of non‑blocking behavior.
* `TaskRunner` – manages a current `Task` and advances it each loop.
* `Tasks` – static helpers to create common patterns (sequence, wait, etc.).
* `DriveTasks` – drive‑oriented tasks.
* `PlantTasks` – plant‑oriented tasks.

Principles:

* Tasks are **non‑blocking**; they never sleep the thread.
* Long or multi‑step behaviors (like "shoot 3 discs" or "drive to stack, intake, back up") should
  be expressed as tasks.
* Tasks are triggered from:

    * Auto code (sequences run at OpMode start).
    * TeleOp via `Bindings` (buttons start/stop tasks).

### 3.5 Sensing

**Core types:**

* `FeedbackSource<T>` – provides time‑stamped sensor samples.
* `FeedbackSample<T>` – value + timestamp pair.
* `AprilTagSensor` – high‑level interface for AprilTag detections.
* `Tags` – helpers to construct `AprilTagSensor` using FTC VisionPortal.
* `TagAim` – uses tag bearings to produce rotation commands for drive.

Principles:

* Complex sensors (vision, fused odometry, etc.) should live in `fw.sensing` and expose stable
  interfaces.
* Drive and control logic should depend on these interfaces, not directly on SDK vision classes.

### 3.6 Stages (advanced)

Stages organize complex control flows (e.g., a shooter with multiple modes) into named states and
transitions.

* `Stage` – describes a single mode of operation.
* Stage machinery – coordinates current stage, transitions, and sub‑tasks.

Stages are an *advanced* concept; they should not appear in the first beginner tutorials. They are
very useful in case studies (like the shooter) once students are comfortable with plants and
tasks.

---

## 4. TeleOp Presets and the Beginner Surface

Phoenix intentionally defines a **small beginner surface** – the minimal set of names students must
know to build a full TeleOp and a simple Auto.

For drive and AprilTags, that surface is:

* `PhoenixTeleOpBase`, `PhoenixAutoBase` (for OpModes).
* `PhoenixRobot` and `Subsystem` (structure).
* `Gamepads`, `DriverKit`, `Bindings` (input).
* `Drives`, `MecanumDrivebase` (hardware + drivebase).
* `DriveSignal`, `DriveSource` (drive abstractions).
* `GamepadDriveSource.teleOpMecanum(...)`.
* `GamepadDriveSource.teleOpMecanumWithSlowMode(...)`.
* `Tags.aprilTags(...)`, `AprilTagSensor`.
* `TagAim.teleOpAim(...)`.

For mechanisms and tasks, the beginner surface is:

* `Plant`, `FtcPlants`.
* `Task`, `TaskRunner`.
* `Tasks`, `DriveTasks`, `PlantTasks`.

**Principles:**

1. Beginner docs and examples should primarily show these names.
2. More advanced entry points should be introduced only in dedicated advanced sections.
3. When we add new helpers, we should decide explicitly whether they are **beginner‑visible** or
   **advanced** and document them accordingly.

---

## 5. Composition and Blending Patterns

Instead of adding many one‑off helper classes, Phoenix prefers small, reusable composition
patterns.

### 5.1 Drive composition

Typical pattern for drive in TeleOp:

1. Start with manual sticks:

   ```java
   DriveSource manual = GamepadDriveSource.teleOpMecanum(driverKit);
   ```

2. Wrap with auto‑aim while a button is held:

   ```java
   DriveSource aimed = TagAim.teleOpAim(
           manual,
           driverKit.p1().leftBumper(),
           tagSensor,
           scoringTagIds);
   ```

3. Add slow mode as an outer wrapper:

   ```java
   DriveSource drive = aimed.scaledWhen(
           () -> driverKit.p1().rightBumper().isPressed(),
           0.30);
   ```

4. Each loop:

   ```java
   DriveSignal cmd = drive.get(clock);
   drivebase.drive(cmd);
   ```

Other common patterns:

* Add a small auto‑correction with `plus(...)`:

  ```java
  DriveSignal corrected = base.plus(correction);
  ```

* Blend driver and auto behaviors with `blendedWith(...)`:

  ```java
  DriveSource mixed = driver.blendedWith(autoAlign, 0.4); // 40% assist
  ```

### 5.2 Tasks and plants

For mechanisms, composition often happens at the task level rather than by blending numeric
outputs directly.

Patterns:

* Use plants to encapsulate low‑level control of motors/servos.
* Use tasks to coordinate plants over time:

    * Wait for shooter at speed, then feed.
    * Move arm to position, then open claw.
* Use `Tasks.sequence(...)` / `Tasks.parallel(...)` (or similar helpers) rather than inlining
  complex state machines in OpMode code.

---

## 6. Naming and API Shape

Consistent naming makes it easier to navigate and guess APIs.

Guidelines:

1. **TeleOp presets use `teleOp...` prefix**

    * Examples:

        * `GamepadDriveSource.teleOpMecanum(...)`.
        * `GamepadDriveSource.teleOpMecanumWithSlowMode(...)`.
        * `TagAim.teleOpAim(...)`.
    * Reserved for high‑level, ready‑to‑use configurations appropriate for tutorials and examples.

2. **Factories vs. builders vs. static helpers**

    * Use **`of`/`from`** for simple factories:

        * `DriverKit.of(Gamepads)`.
    * Use **builder patterns** for multi‑step wiring:

        * `Drives.mecanum(hw).names(...).invertFrontRight().build();`
    * Use **static utility classes** when there is no clear owning type:

        * `Tasks.sequence(...)`, `DriveTasks.driveForSeconds(...)`.

3. **FTC‑specific types are prefixed with `Ftc` or live in `fw.adapters.ftc`**

    * Examples:

        * `FtcPlants`, `FtcVision`.
    * This makes platform‑dependent code easy to spot.

4. **Avoid leaking SDK types into core APIs**

    * Core packages should use HAL interfaces instead of `DcMotorEx`, `Servo`, etc.
    * If a new API needs SDK types, it probably belongs in `fw.adapters.ftc`.

5. **Keep method names descriptive and avoid ambiguous short forms**

    * Prefer `leftBumper()` over `lb()`, `dpadRight()` over `dr()`.
    * Abbreviations are acceptable when they are standard and unambiguous.

6. **Immutability and side‑effect boundaries**

    * Data objects like `DriveSignal` should be immutable.
    * Side effects (writing to hardware) should happen in well‑defined places:

        * Plants writing to motors.
        * Drivebases writing to motors.
    * Helper methods that transform data should not write to hardware.

---

## 7. Code Style and Safety

To keep the framework robust and approachable:

1. **No global mutable state in core packages**

    * Avoid singletons in core code. Configuration should be passed explicitly or built via
      factories.

2. **Clear lifecycle**

    * Base classes (`PhoenixTeleOpBase`, `PhoenixAutoBase`) own the OpMode lifecycle.
    * Other classes should not depend on being called in a particular global order, beyond their
      documented lifecycle methods.

3. **Error reporting**

    * Framework code should fail fast and loudly when misconfigured (e.g., missing hardware names),
      with readable error messages.
    * Prefer throwing clear `IllegalArgumentException` / `IllegalStateException` over silently
      ignoring problems.

4. **Math and units**

    * Centralize math helpers in `fw.util` (`MathUtil`, etc.).
    * Be explicit about units in names and documentation (`ticksPerRev`, `radPerSec`, `dtSec`).

5. **Logging and telemetry**

    * Framework code may add minimal telemetry/logging for diagnostics but should not spam the
      driver station by default.
    * Robot code is free to add more detailed telemetry as needed.

---

## 8. Review Checklist

When adding or changing framework code, use this checklist:

1. **Layering**

    * Does this change respect the layering rules (robot → core → adapters → SDK)?
    * Are any SDK types leaking into core packages?

2. **Core abstractions**

    * Am I reusing `DriveSource`, `DriveSignal`, `Plant`, `Task`, `FeedbackSource`, etc., instead of
      inventing new top‑level concepts?
    * If a new abstraction is needed, can it be explained in terms of existing ones?

3. **TeleOp presets and beginner surface**

    * Does this change affect the beginner surface (the small set of names students must know)?
    * If yes, are the docs and examples updated to reflect the new patterns?
    * Are new `teleOp...` helpers justified and named consistently?

4. **Composition and blending**

    * Am I using composition patterns (`scaledWhen`, `blendedWith`, `Tasks.sequence`, etc.) instead
      of duplicating logic?
    * Could a small helper method or default method remove duplication?

5. **Testability and clarity**

    * Can this logic be unit‑tested without a robot?
    * Does the code read like a description of robot behavior rather than SDK boilerplate?

6. **Naming and documentation**

    * Does the naming follow the guidelines in this document?
    * Is there at least one example or documentation snippet that shows how to use the new code?

If the answer to any of these is “no” or “not sure,” revisit the design or update the docs before
merging.

---

By keeping these principles in mind, Phoenix can grow with your robots and your team’s skills
without turning into a tangle of special cases. The framework should feel like a **solid backbone**
that your robot code hangs off of, not a maze you’re afraid to touch.

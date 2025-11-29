# Phoenix Framework Principles

This document explains the **design principles** behind the Phoenix FTC framework.

It is aimed at:

* Mentors and advanced students who want to understand "why" the framework looks the way it does.
* Contributors who are adding new features or refactoring existing parts.

If you are just trying to get a robot running, start with the **Beginner’s Guide**, then **Tasks &
Macros Quickstart**, then the **Shooter Case Study**. Come back here when you want to reason about
architecture decisions.

---

## 1. Goals and non‑goals

### 1.1 Goals

Phoenix is designed to:

1. **Encourage good structure for student code**

    * Separate hardware access, control logic, and high‑level behavior.
    * Make it easy to see where things live and how they interact.

2. **Make non‑blocking code the default**

    * Multi‑step behaviors use `Task` and `TaskRunner`, not `sleep()`.
    * TeleOp remains responsive even while complex sequences run.

3. **Be small and understandable**

    * Prefer a small set of orthogonal building blocks over a large framework.
    * Code should be readable by students, not just mentors.

4. **Be testable and debuggable**

    * Move FTC SDK specifics behind adapters.
    * Let most logic be plain Java that can be unit‑tested.
    * Provide clear debug/telemetry hooks.

5. **Support incremental learning**

    * New teams can stop at "drive + plants".
    * More advanced teams can layer in tasks, TagAim, vision, and interpolation.

### 1.2 Non‑goals

Phoenix is *not* trying to:

* Replace the FTC SDK or change how OpModes work.
* Be a full command‑based framework with every possible feature.
* Hide all complexity. Some concepts (plants, tasks, TagAim) are explicit so they remain
  understandable.

---

## 2. Separation of concerns

A core principle is **separation of concerns**:

* **Hardware access** lives in **adapters** and the HAL.
* **Actuation** lives in **plants**.
* **Sequencing and behavior over time** lives in **tasks**.
* **Input mapping** lives in **Gamepads + Bindings**.
* **High‑level robot behavior** lives in a **robot class** you own.

### 2.1 Adapters and HAL

Adapters (`FtcHardware`, `FtcVision`, `FtcTelemetryDebugSink`) and the HAL (`PowerOutput`,
`ServoOutput`, etc.) form the boundary to the FTC SDK.

They exist so that:

* The rest of the framework does **not** depend directly on `DcMotor`, `Servo`, or `Telemetry`.
* Most robot logic can be written in terms of abstract outputs/inputs.

When writing framework code:

* New hardware types should be added as HAL abstractions.
* FTC‑specific setup should go in adapters.

### 2.2 Plants

A **plant** is anything that:

* Has a **target** (desired state), and
* Can be **updated each loop** to move toward that target.

Examples:

* Shooter wheel: target velocity.
* Arm: target angle.
* Intake: target power.
* Pusher: target position.

Reasons to use plants:

* Centralize control logic (PID, feedforward, rate limiting) in one place.
* Keep robot code talking about *intent* (targets) instead of raw power levels.
* Make it easy to wrap behavior with safety and rate‑limiting layers.

### 2.3 Tasks

A **task** represents a behavior that unfolds over time:

* Autonomous sequences (drive, drop, shoot, park).
* TeleOp macros (shooting sequences, climb sequences).

Tasks:

* Are **non‑blocking**.
* Are **composable** via `SequenceTask` and `ParallelAllTask`.
* Typically manipulate plants (and possibly drivebases) to express behavior.

A `TaskRunner` owns the currently running task and advances it each loop.

### 2.4 Input mapping

`Gamepads` and `Bindings` separate **input wiring** from **behavior logic**:

* `Gamepads` read the raw FTC `gamepad1`/`gamepad2` state.
* `Bindings` define how buttons/axes trigger actions:

    * change a plant target,
    * start a task,
    * toggle modes.

This makes it easy to change driver controls without touching the core robot logic.

### 2.5 Robot class

The season‑specific robot class (e.g., `PhoenixRobot`) is the **owner** of:

* Plants, drivebase, sensors, tasks.
* The configuration of bindings.
* High‑level helper methods like `shootThreeDiscs()`, `buildSimpleAuto()`, etc.

OpModes are intentionally kept thin so that:

* The same robot code can be reused across multiple TeleOps/Autos.
* The OpMode lifecycle (init / start / loop / stop) is clear and simple.

---

## 3. Plants vs. tasks vs. sensors

### 3.1 Plants: what should be a plant?

A good candidate for a plant is something that:

* Has a meaningful *target* state (angle, velocity, position, power).
* Might require closed‑loop control.
* Might benefit from rate limiting or safety interlocks.

Examples of **good** plant candidates:

* Shooter wheel velocity.
* Arm angle.
* Lift height.
* Claw position.

Examples of things that usually **do not** need their own plant:

* Simple one‑off LEDs or a rumble effect.
* Low‑level sensor reads (IMU, encoders, distance sensor) – those are inputs.

### 3.2 Tasks: what should be a task?

Use tasks for behaviors that are:

* Naturally described as **steps** over **time**.
* Multi‑phase and not instantaneous.

Examples:

* Spin up shooter, feed discs, stop shooter.
* Move an arm to position, wait, then open a claw.
* Drive to a location while running intake.

Tasks should not be used for:

* Per‑loop logic that always runs (that belongs in the robot’s `update…` methods).
* One‑shot variable changes (those can be done directly in bindings or with `InstantTask`).

### 3.3 Sensors and TagAim

Sensors are deliberately kept simple:

* Fetch values (distance, heading, tag pose).
* Provide them in convenient forms (e.g., `AprilTagObservation`, `BearingSource`).

`TagAim` is a small controller that:

* Looks at sensor information (bearing to tag).
* Produces an angular correction (omega) to feed into `DriveSignal`.

It is not a full subsystem; it is a reusable building block that composes with a base `DriveSource`.

---

## 4. Input and bindings philosophy

The input layer is designed to make driver controls:

* Explicit.
* Discoverable.
* Easy to rebind.

### 4.1 Single source of truth for controls

All "which button does what" logic should live in **one place** per robot:

* Usually in a method like `configureBindings()` inside your robot class.

Avoid scattering button logic across many files and random `if (gamepad1.a)` checks.

### 4.2 Level of abstraction

Bindings should usually trigger:

* A high‑level helper method on the robot (e.g., `startShootMacro()`).
* Or directly start a `Task`.

This keeps TeleOp logic at the right level:

```java
bindings.onPress(p1.y(), ()->taskRunner.

start(robot.shootThreeDiscs()));
```

rather than:

```java
if(gamepad1.y &&!prevY){
shooterTarget =2800;
        // and a bunch more logic here...
        }
```

### 4.3 Continuous vs. event‑based bindings

* Use **event‑based** bindings (`onPress`, `onRelease`) for starting/stopping tasks and toggling
  modes.
* Use **while‑held** bindings for simple direct control of plants (e.g., intake power).

`Bindings.update()` is called once per loop to evaluate these patterns.

---

## 5. Debugging and telemetry

Debugging and observability are first‑class concerns.

### 5.1 DebugSink

`DebugSink` is an abstraction over debug output.

* `FtcTelemetryDebugSink` sends information to FTC Telemetry.
* `NullDebugSink` can be used when you don’t need output (or in tests).

Classes are encouraged to:

* Accept a `DebugSink` in their constructor.
* Implement a `debugDump(DebugSink dbg)` method when it makes sense.

### 5.2 Patterns for debugDump()

Guidelines for `debugDump()`:

* Do **not** flood telemetry every loop with giant walls of text.
* Prefer concise key‑value lines (e.g., speeds, setpoints, state enums).
* Tolerate `dbg` being `null` (defensively) even though we normally provide a non‑null sink.

Example pattern:

```java
public void debugDump(DebugSink dbg) {
    if (dbg == null) return;

    dbg.add("Shooter", "vel=%.0f target=%.0f atSetpoint=%b",
            currentVelocity, targetVelocity, atSetpoint());
}
```

Robot code can decide when and how often to call `debugDump()`.

---

## 6. Design patterns and anti‑patterns

### 6.1 Recommended patterns

1. **Robot as composition root**

    * All plants, tasks, drivebases, and bindings are created and wired in the robot class.

2. **Thin OpModes**

    * OpModes mostly forward calls to the robot and manage OpMode lifecycle.

3. **Task‑based multi‑step behavior**

    * Use tasks instead of long `if` chains and timers scattered across the codebase.

4. **Single responsibility**

    * Each class has a small, clear job.
    * Avoid "kitchen sink" classes that know about everything.

5. **Data flows through clear interfaces**

    * Use `DriveSignal`, `Plant`, `Task` interfaces at module boundaries.

### 6.2 Anti‑patterns to avoid

1. **Blocking code (`sleep()`) in control paths**

    * Freezes TeleOp and destroys loop timing.
    * Use `RunForSecondsTask` and `WaitUntilTask`.

2. **Scattered hardware access**

    * Direct `hardwareMap.get()` calls in many files.
    * Use `FtcHardware` and plants instead.

3. **Scattered gamepad checks**

    * `if (gamepad1.a)` sprinkled everywhere.
    * Use `Gamepads` + `Bindings` so control mappings are centralized.

4. **Giant OpModes**

    * All logic inside one TeleOp class.
    * Prefer a robot class that OpModes call into.

5. **Tight coupling between unrelated subsystems**

    * e.g., arm code directly changing drive behavior via static globals.
    * Prefer explicit communication via tasks, shared state on the robot, or well‑defined helpers.

---

## 7. Evolution from older patterns

Earlier versions of Phoenix used base classes and subsystem constructs such as:

* `PhoenixTeleOpBase`
* `PhoenixAutoBase`
* `Subsystem`

These patterns:

* Encouraged OpModes to extend framework classes.
* Split robot logic into multiple subsystem classes.

The current design prefers:

* A **single robot class** that composes plants, drive, sensors, and tasks.
* Plain FTC OpModes that create and use that robot class.

The older base classes are still present for **backward compatibility**, but new examples and docs
use the newer pattern.

Details on migration and legacy support live in **Notes.md**.

---

## 8. Adding new features to the framework

When extending the framework, follow these guidelines:

1. **Fit into the existing layers**

    * New hardware → HAL + adapter.
    * New controller building block → plant wrapper or helper.
    * New multi‑step behavior → task or task helper.

2. **Keep interfaces small and focused**

    * Prefer a few methods with clear semantics over many convenience methods.

3. **Avoid hidden global state**

    * Pass dependencies explicitly via constructors.
    * Minimize singletons.

4. **Preserve non‑blocking behavior**

    * Don’t introduce blocking calls in framework code.

5. **Document intent**

    * Add Javadoc explaining why a class exists and how it’s meant to be used.
    * Where relevant, reference the higher‑level docs (this file, Beginner’s Guide, etc.).

---

## 9. Summary

Phoenix is built around a few key ideas:

* **Plants** model mechanisms with targets and updates.
* **Tasks** describe behaviors over time.
* **Bindings** connect driver input to robot behaviors.
* A **robot class** composes everything; OpModes stay thin.

As long as new code respects these principles:

* Student robot code stays readable.
* Complex behaviors remain non‑blocking and composable.
* The framework can grow without becoming fragile or confusing.

Use this document as a reference when making structural decisions or extending the framework.

# Framework Principles

This document explains the design principles behind the Phoenix framework: **why the APIs look the way they do**, and the usage patterns the framework is optimized for.

If you want to *get running* first, read:

1. **Beginner’s Guide** – loop shape + how to wire Plants.
2. **Tasks & Macros Quickstart** – how to build macros using factory helpers.
3. **Shooter Case Study & Examples Walkthrough** – a complete example tied to real code.

---

## 1. High-level goals

Phoenix is designed around a few core goals:

1. **Non-blocking by design**

   No `sleep(...)`, no long `while (...)` loops inside TeleOp/Auto. Anything that takes time is expressed as a **Task** that advances once per loop.

2. **Clear separation of concerns**

   Robot code is written in terms of a few narrow building blocks:

    * **Drive behavior**: `DriveSource` → `DriveSignal` → `MecanumDrivebase`
    * **Mechanisms**: `Plant`
    * **Behavior over time**: `Task` / `TaskRunner`

   FTC SDK specifics live in the `fw.ftc` boundary layer (and `fw.tools`), not in your robot logic.

3. **Beginner-friendly, mentor-powerful**

   Students primarily use:

    * `Actuators.plant(hardwareMap) ... build()` (to create Plants)
    * `PlantTasks`, `Tasks`, `DriveTasks` (to create Tasks)

   Mentors can go deeper (HAL, adapters, custom Tasks) when needed.

4. **Composable building blocks**

   Drive logic, plants, and tasks are intentionally decoupled so you can swap components:

    * Use `GamepadDriveSource` today, replace with `TagAimDriveSource` tomorrow.
    * Keep the same shooter macro while changing how distance is estimated.

5. **One loop, one heartbeat**

   Phoenix assumes a single loop heartbeat (`LoopClock`) that everything else uses.
   This enables robust button edge detection, predictable task timing, and consistent rate limiting.

---

## 2. Layering: from hardware to behavior

Phoenix is built in layers. Most robot code should live near the **top**.

### 2.1 Hardware abstraction (HAL)

At the bottom is a tiny hardware abstraction layer:

* `PowerOutput` – normalized power (typically `-1..+1`).
* `PositionOutput` – position in native units (servo `0..1`, encoder ticks, etc.).
* `VelocityOutput` – velocity in native units (ticks/sec, etc.).

The FTC adapter `edu.ftcphoenix.fw.ftc.FtcHardware` wraps FTC SDK devices into these outputs.

Most robot code should **not** use these directly.

### 2.2 Plants

A **Plant** is the low-level sink you command with a scalar target.

Key methods (see `edu.ftcphoenix.fw.actuation.Plant`):

* `setTarget(double)` / `getTarget()`
* `update(double dtSec)`
* `stop()`
* `atSetpoint()` and `hasFeedback()`
* optional `reset()` and `debugDump(...)`

A Plant may be open-loop (power, servo set-and-hold) or closed-loop (motor position/velocity with feedback).

### 2.3 The beginner entrypoint: `Actuators.plant(...)`

`edu.ftcphoenix.fw.actuation.Actuators` is the recommended way to create Plants from FTC hardware.

```java
import edu.ftcphoenix.fw.core.hal.Direction;

Plant shooter = Actuators.plant(hardwareMap)
        .motor("shooterLeftMotor", Direction.FORWARD)
        .andMotor("shooterRightMotor", Direction.REVERSE)
        .velocity()   // uses default tolerance in native velocity units
        .build();

Plant transfer = Actuators.plant(hardwareMap)
        .crServo("transferLeftServo", Direction.FORWARD)
        .andCrServo("transferRightServo", Direction.REVERSE)
        .power()
        .build();

Plant pusher = Actuators.plant(hardwareMap)
        .servo("pusherServo", Direction.FORWARD)
        .position()   // servo position set-and-hold (open-loop)
        .build();
```

The builder is staged on purpose:

1. **Pick hardware**: `motor` (optional `andMotor`), `servo` (optional `andServo`), `crServo` (optional `andCrServo`)
2. **Pick control type**: `power()`, `velocity()` / `velocity(tol)`, `position()` / `position(tol)`
3. **Optional modifiers**: `rateLimit(maxDeltaPerSec)`, then `build()`

Internally, Phoenix also has `Plants` factory helpers, but student code should typically prefer `Actuators`.

---

## 3. Drive: sources, signals, and the drivebase

Phoenix drive is split into two parts:

### 3.1 `DriveSource` produces a `DriveSignal`

A `DriveSource` converts “intent” into a robot-centric `DriveSignal`:

* manual TeleOp: `GamepadDriveSource.teleOpMecanumStandard(pads)`
* assisted drive: `TagAimDriveSource` (and other sources)
* autonomous logic: any custom `DriveSource`

### 3.2 `DriveSignal` sign conventions are a contract

`DriveSignal` is robot-centric and aligned with Phoenix pose conventions (+X forward, +Y left):

* `axial > 0` → forward
* `lateral > 0` → left
* `omega > 0` → CCW (turn left)

Driver-facing conversions (e.g., “stick right means strafe right”) happen **at the input boundary** (see `GamepadDriveSource`), not scattered through control code.

### 3.3 `MecanumDrivebase` applies the command

`MecanumDrivebase` mixes a `DriveSignal` into four wheel powers.

If you use rate limiting, call `update(clock)` **before** `drive(signal)` so it uses the most recent `dtSec`:

```java
DriveSignal s = driveSource.get(clock).clamped();

drivebase.update(clock);
drivebase.drive(s);
```

Configuration is via `MecanumDrivebase.Config`. The drivebase makes a **defensive copy** of the config at construction time, so mutating the config object later won’t change an already-created drivebase.

---

## 4. Tasks and macros

### 4.1 Cooperative tasks

A `Task` is a cooperative unit of work driven by the main loop:

* `start(LoopClock clock)` – called once
* `update(LoopClock clock)` – called each cycle while running
* `isComplete()` – true when finished
* `getOutcome()` – optional richer completion info (`TaskOutcome`)

### 4.2 The `TaskRunner`

`TaskRunner` runs tasks sequentially (FIFO). Tasks are assumed to be **single-use**.

A key design choice: `TaskRunner.update(clock)` is **idempotent by `clock.cycle()`**. If nested code accidentally calls `update()` twice in the same loop cycle, tasks do not advance twice.

### 4.3 Prefer factory helpers

Robot code should rarely implement raw tasks directly. Prefer:

* `Tasks.*` for generic composition (`sequence`, `parallelAll`, `waitForSeconds`, `waitUntil`, ...)
* `PlantTasks.*` for commanding Plants (`setInstant`, `holdFor`, `moveTo`, ...)
* `DriveTasks.*` for drive behaviors

---

## 5. Feedback vs open-loop (and why `hasFeedback()` exists)

Phoenix distinguishes:

* **Feedback-capable plants** – implement a meaningful `atSetpoint()` and override `hasFeedback()` to return `true` (motor position/velocity plants).
* **Open-loop plants** – do not expose sensor-based completion and leave `hasFeedback() == false` (power plants, servo set-and-hold plants).

Important consequence:

* `PlantTasks.moveTo(...)` / `moveTo(..., timeout)` / `moveToThen(...)` **require** `plant.hasFeedback() == true` and will throw if used on an open-loop plant.
* Time-based helpers like `holdFor(...)` / `holdForThen(...)` work on any Plant.

This makes it hard to accidentally write a “wait for setpoint” macro on a mechanism that has no feedback.

---

## 6. LoopClock: the per-cycle truth

Phoenix expects:

1. **Advance the clock once per OpMode cycle** (`clock.update(getRuntime())`).
2. **Everything else reads the clock** (dt, cycle id) but does not advance time.

Why the cycle id matters:

* Button edge detection, bindings, task runners, and similar systems need a clear definition of “one loop cycle.”
* Phoenix uses `LoopClock.cycle()` as that identity.

Several core systems are **idempotent by cycle**:

* `Button.updateAllRegistered(clock)`
* `Gamepads.update(clock)`
* `Bindings.update(clock)`
* `TaskRunner.update(clock)`

Idempotency prevents subtle bugs when code is layered (menus, testers, helpers) and multiple layers try to “helpfully” update the same system.

---

## 7. Nomenclature and coordinate conventions

Phoenix is designed so you can read code and know:

* what frame a value is in,
* what units it uses,
* what sign it means.

### 7.1 Frame must appear in the name

If a value depends on a frame, the frame name should appear in the identifier.

Examples:

* `fieldToRobotPose`, `fieldToRobotTargetPose`
* `robotDriveSignal`
* `cameraBearingRad`, `cameraForwardInches`, `robotLeftInches`

Avoid ambiguous names like `pose`, `targetPose`, `x`, `y`, `heading` when the frame is not obvious.

In particular, **data holders should not expose a raw `pose` field** if the frame matters. Prefer names like
`fieldToRobotPose`, `robotToCameraPose`, `cameraToTagPose`, etc.

### 7.2 Transform naming: `fromToToPose` (optionally `p`-prefixed in adapters)

For rigid transforms (`Pose2d` / `Pose3d`) that represent relationships between frames, Phoenix consistently uses:

* `cameraToTagPose`
* `robotToCameraPose`
* `fieldToRobotPose`

In **Phoenix core code**, the `p` prefix is usually unnecessary (everything is already in Phoenix framing), and it
creates noisy method APIs. Prefer names like `robotToTagPose(...)` rather than `pRobotToTag(...)`.

In **adapter code** where multiple coordinate systems coexist (FTC SDK vs Phoenix), it *can* be helpful to prefix
Phoenix-framed values with `p` (for example, `pFieldToRobotPose`) so it’s obvious which convention you’re in.

Rule of thumb: if you see `pAtoB.then(pBtoC)`, the result should be `pAtoC`.

This convention is also called out explicitly in the FTC adapter boundary (`edu.ftcphoenix.fw.ftc.FtcFrames`).

### 7.3 Units must appear in the name

Prefer suffixes like: `Inches`, `Rad`, `Deg`, `Sec`, `PerSec`, `PerSec2`.

Examples:

* `headingRad`, `omegaRadPerSec`
* `maxSpeedInchesPerSec`, `timeoutSec`

### 7.4 Signs are part of the contract

Phoenix uses right-handed conventions:

* +X forward, +Y left, +Z up
* yaw CCW-positive
* `DriveSignal.omega > 0` turns left

Convert “driver intuition” at the boundaries (for example, in `GamepadDriveSource`), not throughout the codebase.

---

## 8. Recommended usage pattern

A typical OpMode loop follows this shape:

> Clock → Inputs → Bindings → Tasks → Drive → Plants → Telemetry

In code (conceptually):

```java
clock.update(getRuntime());

gamepads.update(clock);
bindings.update(clock);

macroRunner.update(clock);

drivebase.update(clock);
drivebase.drive(driveSource.get(clock).clamped());

shooter.update(clock.dtSec());
transfer.update(clock.dtSec());
```

The **Loop Structure** document dives deeper into why this order matters.

---

## 9. Extending Phoenix

Phoenix is designed to be extended safely:

* New `DriveSource` implementations (assist drive, path following, etc.)
* New Plant types (custom control or interlocks)
* New task factories (team-specific high-level macros)

When extending:

* Keep SDK- or vendor-specific calls in adapters.
* Preserve per-cycle semantics (do not “secretly” advance time or consume edges).
* Keep interfaces narrow (`Plant`, `DriveSource`, `Task`) so systems remain composable.

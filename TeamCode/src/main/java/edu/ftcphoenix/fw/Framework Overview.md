# Phoenix FTC Framework – Overview

This document is the **map** of the Phoenix FTC framework. It explains what the framework is trying to do, how the pieces fit together, and where to look next depending on who you are (new student, drive coach, or framework maintainer).

---

## 1. What Phoenix is trying to do

Phoenix is a small framework that sits **on top of the FTC SDK** and aims to:

* Let **students focus on robot behavior**, not boilerplate.
* Encourage **non-blocking, composable logic** (no `sleep()` in the middle of important code).
* Separate concerns:

    * Hardware access and FTC specifics live in **adapters**.
    * Control and sequencing live in **plants** and **tasks**.
    * High‑level robot behavior lives in a **robot class** you own.
* Make code easy to **debug, refactor, and test**.

You can think of the framework as providing:

* **“Nice building blocks”**: Plants, Tasks, Drive helpers, Input helpers.
* **“Adapters”**: small wrappers that hide FTC SDK details.
* **“Patterns”**: recommended ways to structure your TeleOp and Auto code.

---

## 2. How to read these docs

If you are:

* **A new student** – start with **Beginner’s Guide**.
* **Writing multi-step behaviors** (autos, macros, complex buttons) – read **Tasks & Macros Quickstart**.
* **Connecting the docs to real code** – walk through **Shooter Case Study & Examples Walkthrough**.
* **A mentor / framework contributor** – read **Framework Principles** and **Notes**.

This overview stays high‑level and points you at the right place for details.

---

## 3. Big pieces at a glance

The framework lives under the `edu.ftcphoenix.fw` package. The major areas are:

### 3.1 Actuation – plants and controllers (`fw.actuation`)

This is where you model things that **move** on the robot.

* `Plant` – interface for something that has a **desired target** and can be **updated each loop**.
* `Plants` – helpers and utilities for working with plants.
* `Actuators` – helpers for wiring FTC motors/servos into plants.
* `RateLimitedPlant` – wraps a plant to limit how fast its target changes.
* `InterlockPlant` – wraps one or more plants to enforce safety rules.
* `controller.*` – small control helpers for shaping plant behavior.
* `PlantTasks` – ready‑made `Task` factories that drive plants (e.g., “go to this setpoint and wait”, “hold this power for N seconds then stop”).

**Key idea:** Robot code should mostly talk to **plants** instead of direct motor power.

---

### 3.2 Tasks – non‑blocking behaviors (`fw.task`)

Tasks represent **do this behavior over time** in a non‑blocking way.

* `Task` – interface with `start`, `update`, and `isFinished`.
* `TaskRunner` – owns the currently running task and advances it each loop.
* Building blocks:

    * `InstantTask`
    * `RunForSecondsTask`
    * `WaitUntilTask`
    * `SequenceTask` – run tasks in order.
    * `ParallelAllTask` – run multiple tasks together until all finish.

Tasks are used for **TeleOp macros** (e.g., “shoot 3 discs”) and **Autonomous routines** (“drive here, then shoot, then park”).

---

### 3.3 Drive – moving the robot (`fw.drive`)

Helpers for building drivebases and hooking them to driver input or autonomous logic.

* `DriveSignal` – the low-level representation of what the drivebase should do.
* `Drives` – static helpers for different drivebase types.
* `MecanumConfig` / `MecanumDrivebase` – mecanum drive helpers.
* `DriveSource` – interface for “things that can produce a drive signal” (driver input, TagAim, autonomous path, etc.).
* `DriveTasks` – tasks that command drive motion (e.g., drive for a duration).
* `drive.source.*`:

    * `GamepadDriveSource` – drive from sticks/buttons with support for slow‑mode, etc.
    * `TagAimDriveSource` – wraps another drive source and adds TagAim rotation on top.

**Key idea:** The drivebase doesn’t care *where* the command comes from – TeleOp, TagAim, or Auto – as long as it gets a `DriveSignal` each loop.

---

### 3.4 Sensing & TagAim (`fw.sensing`)

This is where vision and other sensors live.

* `AprilTagSensor` & `AprilTagObservation` – access and represent AprilTag detections.
* `Tags` – utilities related to tag IDs and tag geometry.
* `BearingSource` – an abstraction for “give me the direction to something”.
* `TagAim` & `TagAimController` – logic for turning the robot to face a target (often an AprilTag).

TagAim is used heavily in the examples to allow the driver to **hold a button and auto‑face the scoring AprilTag**, while still driving normally otherwise.

---

### 3.5 Input – gamepads & bindings (`fw.input`)

A small input layer that makes controller code more explicit and testable.

* `Gamepads` – global gamepad manager; updated once per loop.
* `GamepadDevice` – one logical gamepad (P1, P2, etc.).
* `Axis` – analog inputs (sticks, triggers).
* `Button` – digital inputs (A/B/X/Y, bumpers, d‑pad, etc.).
* `binding/Bindings` – map buttons and axes to actions (e.g., start a task, change a target).

**Key idea:** Keep all “when I press this button, do that behavior” logic in a predictable, centralized place.

---

### 3.6 Adapters – FTC SDK integration (`fw.adapters.ftc`)

These classes are the thin layer between the Phoenix framework and the FTC SDK.

* `FtcHardware` – wraps the `HardwareMap` and creates outputs/inputs used by plants and sensors.
* `FtcTelemetryDebugSink` – sends debug information to Telemetry.
* `FtcVision` – sets up and provides access to vision (including AprilTag) on FTC hardware.

Your TeleOp/Auto OpModes usually create these adapters once and pass them into your robot class.

---

### 3.7 HAL – hardware abstraction (`fw.hal`)

Very simple abstractions over FTC hardware capabilities.

* `PowerOutput`, `VelocityOutput`, `PositionOutput`, `ServoOutput`, `MotorOutput`.

These are used by `Actuators` and plants so that higher‑level code doesn’t depend directly on the FTC SDK motor and servo types.

---

### 3.8 Utilities, debug, and core math

* `fw.util`

    * `LoopClock` – tracks cycle time (`dt`) and loop rate.
    * `InterpolatingTable1D` – lookup table with interpolation (used for distance→shooter speed curves).
    * `MathUtil`, `Units` – small helpers for math and unit conversions.
* `fw.debug`

    * `DebugSink` – interface for sending debug output.
    * `NullDebugSink` – no‑op implementation.
* `fw.core`

    * `Pid`, `PidController` – basic PID tools used where needed.

---

## 4. Where your robot code lives

The Phoenix framework **does not** own your OpModes or game‑specific robot class. A typical season will have:

* A **season robot class** (often something like `PhoenixRobot` or `SeasonRobot`) that:

    * Holds references to plants, drivebase, sensors, and tasks.
    * Wires them together once during initialization.
    * Exposes high‑level methods like `updateTeleOp(dt)` and `updateAuto(dt)`.
    * Provides helper methods to create tasks/macros (e.g., `shootThreeDiscs()`).
* **Thin TeleOp and Auto OpModes** that:

    * Construct `FtcHardware`, `FtcVision`, and a debug sink.
    * Construct your season robot class.
    * In `loop()`:

        * Update `LoopClock`.
        * Update `Gamepads` and `Bindings`.
        * Call `robot.updateTeleOp(dt)` or `taskRunner.update(...)` as appropriate.
        * Flush debug/telemetry.

The **Shooter TeleOp examples (01–06)** in `fw.examples` show this pattern in concrete code.

---

## 5. Typical TeleOp dataflow

A TeleOp loop using Phoenix typically looks like this (pseudocode):

```java
public void loop() {
    // 1. Update timing.
    clock.update();
    double dt = clock.getDtSeconds();

    // 2. Read input.
    Gamepads.update(gamepad1, gamepad2);
    bindings.update();   // buttons → actions/tasks

    // 3. Robot logic.
    robot.updateTeleOp(dt);  // sets desired targets for plants, etc.

    // 4. Advance tasks (macros, auton fragments).
    taskRunner.update(dt);

    // 5. Telemetry / debug.
    dbg.flush();
}
```

Inside `robot.updateTeleOp(dt)` you typically:

* Read drive input via a `GamepadDriveSource`.
* Optionally wrap it with `TagAimDriveSource` when an aim button is held.
* Convert the resulting `DriveSignal` to motor outputs via `MecanumDrivebase`.
* Update plants based on current targets.

---

## 6. Typical Autonomous dataflow

An Autonomous OpMode is usually built around **one main `Task`**:

1. Build a `Task` (often a `SequenceTask` of drive + mechanism tasks):

    * drive to a distance or for a duration,
    * spin up shooter,
    * feed rings,
    * park.
2. Hand that task to a `TaskRunner`.
3. In `loop()`, just call `taskRunner.update(dt)` and push telemetry.

The same plant/task concepts used in TeleOp are reused in Auto, which keeps the code consistent.

---

## 7. Legacy base classes (kept for compatibility)

The framework still contains some older base classes under `fw.robot`:

* `PhoenixTeleOpBase`
* `PhoenixAutoBase`
* `Subsystem`

These were part of a previous structure where robot code was split into subsystems and OpModes extended framework base classes.

They are **kept for backward compatibility only**. New examples and docs use the **PhoenixRobot + thin OpMode** pattern described above instead. For migration notes and more background on this shift, see **Notes.md**.

---

## 8. Where to go next

Depending on what you’re trying to do, here’s the suggested next step:

* **Just getting started?** Read **Beginner’s Guide** and skim TeleOp Example 01.
* **Want macros or autonomous sequences?** Read **Tasks & Macros Quickstart**.
* **Want to understand the shooter + TagAim examples?** Read **Shooter Case Study & Examples Walkthrough**.
* **Curious about deeper design decisions or legacy pieces?** Read **Framework Principles** and **Notes**.

Use this overview as a reference map whenever you’re unsure where a particular concept or class “belongs” in the Phoenix world.

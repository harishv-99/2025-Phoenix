# Phoenix FTC Framework Overview

This document is a **map of the Phoenix framework**, how the pieces fit together,
and where your robot code should live.

It focuses on:

* The **main runtime pattern** (LoopClock, Gamepads, Bindings, Plants, Tasks, Drive).
* The **package layout** of `edu.ftcphoenix.fw.*`.
* How to connect these pieces in your own project.
* Where legacy base classes (e.g., `PhoenixTeleOpBase`) fit in today.

If you are just starting, read this together with:

* **Beginner’s Guide** – how to write a season robot class + thin OpModes.
* **Tasks & Macros Quickstart** – non‑blocking behaviors.
* **Shooter Case Study & Examples Walkthrough** – full TeleOp examples.
* **Framework Principles** – design philosophy.

---

## 1. Big picture

Phoenix is designed around a small set of **orthogonal building blocks**:

* **Time** – `LoopClock`
* **Input** – `Gamepads`, `GamepadDevice`, `Button`, `Axis`, `Bindings`
* **Drive** – `DriveSource`, `DriveSignal`, `MecanumDrivebase`, `Drives`
* **Mechanisms** – `Plant`, `Actuators`, `Plants`, controller wrappers
* **Tasks** – `Task`, `TaskRunner`, `InstantTask`, `RunForSecondsTask`, etc.
* **Vision & sensors** – `AprilTagSensor`, `AprilTagObservation`, `TagAim`, `BearingSource`
* **Utilities** – `InterpolatingTable1D`, `MathUtil`, `Units`

Your **robot code** lives in your own package (e.g., `edu.ftcphoenix.robots.*`) and
is responsible for:

* Creating/wiring these building blocks.
* Choosing how to expose behavior via gamepad bindings and tasks.

Phoenix itself is mostly stateless helpers + small abstractions.

---

## 2. The main runtime pattern

All Phoenix‑style OpModes end up looking very similar. The key loop looks like this:

```java
public class MyTeleOp extends OpMode {
    private final LoopClock clock = new LoopClock();

    private Gamepads pads;
    private Bindings bindings;

    private PhoenixRobot robot;      // your season robot class

    @Override
    public void init() {
        pads = Gamepads.create(gamepad1, gamepad2);
        robot = new PhoenixRobot(hardwareMap, pads);
        bindings = robot.bindings();

        clock.reset(getRuntime());
    }

    @Override
    public void loop() {
        // 1) Time
        clock.update(getRuntime());
        double dt = clock.dtSec();

        // 2) Inputs
        pads.update(dt);
        bindings.update(dt);

        // 3) Robot logic (drive + plants + tasks)
        robot.updateTeleOp(clock);

        // 4) Telemetry
        telemetry.update();
    }
}
```

On the **robot side**, you have a `PhoenixRobot` that composes everything:

```java
public final class PhoenixRobot {
    private final HardwareMap hw;
    private final Gamepads pads;

    // Drive
    private final MecanumDrivebase drivebase;
    private final DriveSource driveSource;

    // Mechanisms
    private final Plant shooter;
    private final Plant intake;
    private final Plant pusher;

    // Input & tasks
    private final Bindings bindings = new Bindings();
    private final TaskRunner tasks = new TaskRunner();

    public PhoenixRobot(HardwareMap hw, Gamepads pads) {
        this.hw = hw;
        this.pads = pads;

        this.drivebase = Drives.mecanum(hw);
        this.driveSource = GamepadDriveSource.teleOpMecanumStandard(pads);

        this.shooter = buildShooter(hw);
        this.intake = buildIntake(hw);
        this.pusher = buildPusher(hw);

        configureBindings();
    }

    public void updateTeleOp(LoopClock clock) {
        double dt = clock.dtSec();

        // Drive
        DriveSignal signal = driveSource.get(clock).clamped();
        drivebase.drive(signal);
        drivebase.update(clock);

        // Mechanisms
        shooter.update(dt);
        intake.update(dt);
        pusher.update(dt);

        // Macros
        tasks.update(clock);
    }

    public Bindings bindings() { return bindings; }
    public TaskRunner taskRunner() { return tasks; }

    // buildShooter/intake/pusher + configureBindings() use Actuators, PlantTasks, etc.
}
```

Everything else in the framework is there to make this pattern easy and
readable.

---

## 3. Package tour

This section gives a **high‑level map** of `edu.ftcphoenix.fw.*` and what
lives where.

### 3.1 `fw.util` – utilities

* **`LoopClock`** – minimal loop timing helper built around `OpMode.getRuntime()`.

    * `reset(double nowSec)` – usually called in `start()` or `init()`.
    * `update(double nowSec)` – once per loop.
    * `dtSec()` – delta time since last update.
* **`InterpolatingTable1D`** – sorted (x, y) lookup with linear interpolation.

    * Typical use: distance → shooter velocity.
* **`MathUtil`**, **`Units`** – misc math + unit conversions.

These classes have no FTC dependencies and are safe to use anywhere.

---

### 3.2 `fw.input` – gamepads, buttons, bindings

Key classes:

* `Gamepads` – wraps FTC `gamepad1` and `gamepad2`.

    * `static Gamepads create(Gamepad gp1, Gamepad gp2)`.
    * `void update(double dtSec)` – updates axes and button edge states.
    * `GamepadDevice p1()` / `p2()` – access to individual player wrappers.
* `GamepadDevice` – exposes:

    * `Axis` getters (sticks, triggers) and
    * `Button` getters (A/B/X/Y, bumpers, d‑pad, etc.).
* `Button` – an edge‑aware button abstraction with helpers like `onPress`.
* `Axis` – analog input wrapper (sticks, triggers).
* `binding/Bindings` – maps button events to actions.

    * `onPress(Button, Runnable)`
    * `whileHeld(Button, Runnable)` / `whileHeld(Button, Runnable, Runnable)`
    * `toggle(Button, Consumer<Boolean>)`
    * `update(double dtSec)` – run all binding logic once per loop.

The typical pattern:

```java
pads = Gamepads.create(gamepad1, gamepad2);
bindings = new Bindings();

// In configureBindings():
GamepadDevice p1 = pads.p1();
bindings.whileHeld(p1.a(), () -> intake.setTarget(+1.0));
bindings.onRelease(p1.a(), () -> intake.setTarget(0.0));

// In loop():
clock.update(getRuntime());
double dt = clock.dtSec();

pads.update(dt);
bindings.update(dt);
```

All "which button does what" code should live in one place, typically a
`configureBindings()` method on your robot class.

---

### 3.3 `fw.drive` – DriveSource, DriveSignal, Drives, mecanum

This package provides abstractions for drivetrain commands and helpers for
mecanum drive.

* **`DriveSignal`** – 3‑DOF command: axial, lateral, omega.

    * Immutable value type with helpers like `clamped()`.
* **`DriveSource`** – interface:

  ```java
  public interface DriveSource {
      DriveSignal get(LoopClock clock);
  }
  ```

  Implementations include:

    * `GamepadDriveSource` – turn gamepad sticks into a `DriveSignal`.
    * `TagAimDriveSource` – wrap another `DriveSource` with tag‑based auto‑aim.
* **`Drives`** – factory helpers for building drivebases.

    * `MecanumDrivebase Drives.mecanum(HardwareMap hw)` – beginner entrypoint.
    * Overloads that accept explicit `MecanumConfig` if you need custom geometry
      or motor wiring.
* **`MecanumDrivebase`** – low‑level mecanum drivebase helper.

    * Typical pattern: `drive(DriveSignal)` + `update(LoopClock)` each loop.

See the Shooter case study docs and examples (`TeleOp_01...`) for concrete
usage.

---

### 3.4 `fw.actuation` – plants, controllers, Actuators

This package is about **mechanisms**, abstracted as **plants**:

* **`Plant`** – interface for anything that:

    * has a target (double), and
    * is updated each loop to move toward that target.

* **`Actuators`** – fluent builder for constructing plants on top of hardware.

  ```java
  Plant shooter = Actuators.plant(hardwareMap)
          .motorPair("shooterLeftMotor", false,
                     "shooterRightMotor", true)
          .velocity(100.0)   // tolerance in native units
          .build();

  Plant intake = Actuators.plant(hardwareMap)
          .motor("intakeMotor", false)
          .power()
          .build();

  Plant pusher = Actuators.plant(hardwareMap)
          .servo("pusherServo", false)
          .position()
          .build();
  ```

* **Controller wrappers** (`actuation.controller.*`) – small building blocks for
  shaping control (buffering, goal logic, etc.).

* **`InterlockPlant`** – wrapper that enforces safety conditions before
  forwarding targets to an inner plant.

Under the hood, these use the HAL outputs in `fw.hal.*` (see below) and
possibly PID via `fw.core.PidController`.

---

### 3.5 `fw.hal` – hardware abstraction layer

These classes abstract over FTC hardware types:

* `PowerOutput` – something that accepts a power (e.g., `DcMotor` in power mode).
* `VelocityOutput` – velocity‑controlled outputs.
* `PositionOutput` – positional (e.g., servo, motor with encoder).

They are intentionally small – robot code usually works with `Plant` and
`Actuators.plant(...)` instead of raw outputs.

---

### 3.6 `fw.task` – Tasks, TaskRunner, compositions

Key classes:

* **`Task`** – non‑blocking behavior over time:

  ```java
  public interface Task {
      void start(LoopClock clock);
      void update(LoopClock clock);
      boolean isFinished();
  }
  ```

* **`TaskRunner`** – sequential scheduler:

    * `enqueue(Task)` – queue a task.
    * `update(LoopClock)` – advance current task.
    * `clear()` – stop current + drop queued tasks.
    * `isIdle()` / `hasActiveTask()` / `queuedCount()` – introspection.

* **Building blocks**:

    * `InstantTask` – run a `Runnable` once, then finish.
    * `RunForSecondsTask` – time‑boxed behavior.
    * `WaitUntilTask` – wait for a condition.
    * `SequenceTask` – run tasks one after another.
    * `ParallelAllTask` – run tasks in parallel until all are finished.

The **Tasks & Macros Quickstart** shows how to use these together with
`PlantTasks` (which lives in `fw.actuation`) to build TeleOp macros and
autonomous routines.

---

### 3.7 `fw.sensing` – AprilTags, TagAim, bearings

This package covers higher‑level sensing utilities:

* `AprilTagSensor` – abstraction over FTC AprilTag pipelines.

    * Exposes `AprilTagObservation` snapshots.
    * Adapter implementation for FTC lives under `adapters.ftc`.
* `AprilTagObservation` – value type for tag distance, bearing, age, ID, etc.
* `BearingSource` – generic interface for “something that provides a bearing”.
* `TagAim` – helpers to build drive sources that auto‑aim at tags.

    * `DriveSource TagAim.teleOpAim(DriveSource base, Button aimButton,
                                 AprilTagSensor sensor, Set<Integer> ids)`.
* `TagAimController` – underlying P‑controller that turns bearing into omega.

See Example 05/06 TeleOps and the Shooter case study for usage patterns.

---

### 3.8 `fw.adapters` – FTC-specific wiring

Currently this is mostly:

* `adapters.ftc` – helpers for:

    * AprilTag sensors via VisionPortal.
    * (In some versions) telemetry debug sinks and hardware utilities.

Robot code in your project is free to use these, but the **core framework
packages** do not depend directly on FTC SDK classes.

---

### 3.9 `fw.debug` – DebugSink

* `DebugSink` – generic debugging/telemetry sink.

    * `addData(key, value)` style API.
* `NullDebugSink` – no‑op implementation.

In FTC OpModes you will typically wrap telemetry in a debug sink and pass it
into subsystems that want to expose debug info.

Many classes implement a `debugDump(DebugSink dbg)` or similar method.

---

### 3.10 `fw.examples` – reference TeleOps

This package contains the **mecanum + shooter** examples documented in the
Shooter case study doc:

* `TeleOp_01_MecanumBasic`
* `TeleOp_02_ShooterBasic`
* `TeleOp_03_ShooterMacro`
* `TeleOp_04_ShooterInterpolated`
* `TeleOp_05_ShooterTagAimVision`
* `TeleOp_06_ShooterTagAimMacroVision`

These are intended to be read as a progression and as reference patterns when
wiring your own robot.

---

### 3.11 `fw.robot` – legacy base classes

This package contains older base classes that many teams used historically:

* `PhoenixTeleOpBase`
* `PhoenixAutoBase`
* `Subsystem`

They are still present for:

* Backward compatibility with older robots.
* Advanced teams who prefer an inheritance‑based style.

However, the **recommended pattern** for new code is:

* Plain FTC `OpMode`/`LinearOpMode`.
* A season robot class that composes `DriveSource`, `MecanumDrivebase`,
  plants, tasks, and bindings.

See the **Beginner’s Guide**, **Framework Principles**, and **Notes** docs
for details on why and how this shift was made.

---

## 4. How it all fits together in your project

Putting the pieces together for a typical TeleOp:

1. **Create a season robot class** (e.g., `edu.ftcphoenix.robots.season2025.PhoenixRobot`).

    * Fields for drivebase, drive source, plants, task runner, bindings.
    * Constructor wires hardware via `Actuators.plant(...)` and `Drives.mecanum(...)`.
    * Exposes `updateTeleOp(LoopClock)` and `updateAuto(LoopClock)`.

2. **Create thin OpModes**.

    * `@TeleOp` and `@Autonomous` classes that:

        * create `Gamepads` and `PhoenixRobot` in `init()`,
        * reset `LoopClock` in `start()` (or at end of `init()`),
        * call `pads.update(dt)`, `bindings.update(dt)`, and `robot.update...(clock)` in `loop()`.

3. **Centralize input mappings**.

    * Implement `configureBindings()` on your robot class.
    * Use `Bindings` methods (`onPress`, `whileHeld`, `toggle`) to express driver intent.

4. **Use Tasks for multi‑step behaviors**.

    * Add a `TaskRunner` field on the robot.
    * Use `PlantTasks` and `SequenceTask`/`ParallelAllTask` to build macros.
    * Bind buttons to `taskRunner.enqueue(...)` calls.

5. **Layer on advanced features as needed**.

    * Interpolated shooter speeds via `InterpolatingTable1D`.
    * TagAim + AprilTags via `TagAim.teleOpAim(...)`.

---

## 5. Summary

Phoenix is intentionally small. Most of the complexity lives in **your robot
class** and the decisions you make there.

* `LoopClock` gives you consistent timing.
* `Gamepads` + `Bindings` make driver input explicit.
* `DriveSource` + `MecanumDrivebase` handle drive.
* `Actuators` + `Plant` model mechanisms.
* `Task` + `TaskRunner` + `PlantTasks` give you non‑blocking behavior over time.
* `TagAim`, `AprilTagSensor`, and `InterpolatingTable1D` let you add vision‑based
  aiming and distance‑based shooter control without rewriting your drive or
  mechanism code.

The other docs dive deeper into each topic; this overview is the map to help
you find where everything lives.

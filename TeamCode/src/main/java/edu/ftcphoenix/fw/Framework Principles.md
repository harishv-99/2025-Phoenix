# Phoenix Framework Principles

This document explains the **design principles** behind the Phoenix FTC framework.

It is aimed at:

* Mentors and advanced students who want to understand *why* the framework looks the way it does.
* Contributors who are adding new features or refactoring existing parts.

If you are just trying to get a robot running, start with:

* **Beginner’s Guide**
* **Tasks & Macros Quickstart**
* **Shooter Case Study & Examples Walkthrough**

Come back here when you want to reason about architecture decisions.

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

    * Keep most logic as plain Java, with minimal direct FTC dependencies.
    * Allow unit testing of key pieces (interpolating tables, TagAim controller, etc.).
    * Provide clear debug/telemetry hooks.

5. **Support incremental learning**

    * New teams can stop at "drive + plants".
    * More advanced teams can layer in tasks, TagAim, vision, and interpolation.

### 1.2 Non‑goals

Phoenix is *not* trying to:

* Replace the FTC SDK or change how OpModes work.
* Be a giant, batteries‑included command‑based framework.
* Hide all complexity behind magic annotations or reflection.

Instead, it offers a **thin, explicit** set of tools that you wire together in your
own `PhoenixRobot` class.

---

## 2. Separation of concerns

A core principle is **separation of concerns**:

* **Time** → `LoopClock`
* **Input** → `Gamepads`, `GamepadDevice`, `Button`, `Bindings`
* **Drive** → `DriveSource`, `DriveSignal`, `MecanumDrivebase`, `Drives`
* **Mechanisms** → `Plant`, `Actuators`, controller wrappers
* **Behavior over time** → `Task`, `TaskRunner`, `SequenceTask`, `ParallelAllTask`
* **Sensors & vision** → `AprilTagSensor`, `AprilTagObservation`, `TagAim`, `BearingSource`
* **Robot assembly** → your season‑specific robot class (e.g. `PhoenixRobot`)

Each layer has a small, clear responsibility.

### 2.1 Time: LoopClock

`LoopClock` is the single source of truth for timing:

* Wraps `OpMode.getRuntime()`.
* Computes `dtSec` each loop.
* Is passed to drivebases, tasks, and sometimes sensors.

Pattern:

```java
clock.reset(getRuntime());   // in start() or end of init()

// each loop()
clock.update(getRuntime());

double dt = clock.dtSec();
```

This keeps everything in the robot synchronized to the same notion of time.

### 2.2 Input: Gamepads & Bindings

The input layer separates **raw controllers** from **what they do**.

* `Gamepads` owns the FTC `gamepad1` and `gamepad2`.

    * `Gamepads.create(gamepad1, gamepad2)` in `init()`.
    * `pads.update(dt)` in `loop()`.
* `GamepadDevice` exposes buttons and axes for P1, P2.
* `Bindings` maps button events to actions.

    * `onPress(Button, Runnable)`
    * `whileHeld(Button, Runnable)`
    * `toggle(Button, Consumer<Boolean>)`
    * `update(dt)` in `loop()`.

**Principle:** there should be **one place** where you define
"which button does what" – typically a `configureBindings()` method on your robot.

### 2.3 Drive: DriveSource & drivebases

Drive is decomposed into:

* `DriveSignal` – an axial / lateral / omega command.
* `DriveSource` – "where drive signals come from" (sticks, TagAim, auto trajectories).
* `MecanumDrivebase` – drivebase wrapper around 4 motors.
* `Drives` – helpers for constructing drivebases.

Pattern:

```java
MecanumDrivebase drivebase = Drives.mecanum(hardwareMap);
DriveSource driveSource = GamepadDriveSource.teleOpMecanumStandard(pads);

// each loop()
DriveSignal cmd = driveSource.get(clock).clamped();
drivebase.drive(cmd);
drivebase.update(clock);
```

Other drive sources (e.g. TagAim, autonomous path following) wrap or replace the
base `DriveSource`.

### 2.4 Mechanisms: plants and Actuators

A **plant** is anything that:

* Has a **target** (double), and
* Moves toward that target when you call `update(dt)`.

Examples:

* Shooter wheel: target velocity.
* Intake: target power.
* Pusher: target position.
* Arm: target angle.

Plants are typically constructed via `Actuators.plant(hardwareMap)`:

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
```

**Principle:** robot code should mostly talk about **targets**, not raw power.
Control logic (PID, rate limits, safety interlocks) should be encapsulated inside
plants or small wrappers.

### 2.5 Behavior over time: Tasks & TaskRunner

Multi‑step behaviors that unfold over time (shooting, placing a preload, an auto
routine) are expressed as **Tasks**:

```java
public interface Task {
    void start(LoopClock clock);
    void update(LoopClock clock);
    boolean isFinished();
}
```

A `TaskRunner` manages a **queue** of tasks and advances them each loop:

```java
TaskRunner runner = new TaskRunner();

runner.enqueue(task);      // add to queue
runner.update(clock);      // advance current task
runner.clear();            // cancel current + queued
```

Small building blocks (`InstantTask`, `RunForSecondsTask`, `WaitUntilTask`,
`SequenceTask`, `ParallelAllTask`, `PlantTasks`) let you express complex macros
without ever calling `sleep()`.

### 2.6 Robot assembly: your PhoenixRobot class

Your season robot class (often called `PhoenixRobot`) is the **composition root**:

* Owns `Gamepads`, `Bindings`, `TaskRunner`, drive, and mechanism plants.
* Wires hardware via `Actuators` and `Drives`.
* Exposes methods like `updateTeleOp(LoopClock)` and `updateAuto(LoopClock)`.
* Provides helper methods that construct tasks (`shootOneDisc(...)`,
  `buildCenterAuto()`, etc.).

OpModes are intentionally thin: they create the robot, manage `LoopClock`, and
call into the robot each loop.

---

## 3. Plants vs. tasks vs. sensors

Understanding what should be a plant, what should be a task, and what should be
"just a sensor" is core to using Phoenix well.

### 3.1 What should be a plant?

Good plant candidates:

* Have a meaningful *target* (angle, velocity, position, power).
* May require closed‑loop control or rate limiting.
* Typically drive hardware outputs.

Examples:

* Shooter wheel (velocity).
* Intake (power).
* Arm (position / angle).
* Lift (position).
* Pusher / claw (position).

Things that usually **don’t** need a dedicated plant:

* Simple one‑shot outputs (LED blink, gamepad rumble) – those can be small helpers.
* Raw sensor readings (IMU, distance sensors) – those feed into controllers.

### 3.2 What should be a Task?

Use a `Task` when behavior is naturally described as **steps over time**:

* Spin up shooter, feed disc, spin down.
* Move arm to position, wait, open claw.
* Drive to location while running intake.
* Entire autonomous routines.

Tasks should **not** be used for:

* Per‑loop logic that always runs (that belongs directly in `updateTeleOp/Auto`).
* Simple one‑shot variable changes (bindings + `Plant.setTarget(...)` is enough).

### 3.3 Sensors and TagAim

Phoenix treats sensors as **inputs**, not subsystems:

* AprilTag detection is exposed via `AprilTagSensor` and `AprilTagObservation`.
* "Bearing to target" is exposed via `BearingSource`.

`TagAim` is a small controller that:

* Takes a base `DriveSource` and an `AprilTagSensor`.
* Controls omega when the driver holds an aim button.

This keeps TagAim as **just another drive source**, not a giant subsystem.

---

## 4. Input & bindings philosophy

The bindings layer expresses "what driver input means" in a single, explicit place.

### 4.1 Single source of truth

All gamepad → behavior logic should live in one method per robot:

```java
private void configureBindings() {
    GamepadDevice p1 = pads.p1();

    bindings.whileHeld(p1.a(), () -> intake.setTarget(+1.0));
    bindings.onRelease(p1.a(), () -> intake.setTarget(0.0));

    bindings.onPress(p1.y(), () -> {
        tasks.clear();
        tasks.enqueue(shootThreeDiscs());
    });
}
```

This avoids scattering `if (gamepad1.a)` checks around multiple files.

### 4.2 Level of abstraction

Bindings should typically call **robot‑level methods** or **enqueue tasks**, not
fiddle with low‑level details:

* ✅ `bindings.onPress(p1.y(), () -> tasks.enqueue(robot.shootThreeDiscs()));`
* 🚫 `if (gamepad1.y && !prevY) { shooterTarget = 2800; /* plus lots more */ }`

This keeps TeleOp logic easy to read and change.

### 4.3 Continuous vs. event‑based

Phoenix bindings support both:

* **Event‑based** (`onPress`, `onRelease`, `toggle`) – great for starting macros
  or toggling modes.
* **Continuous** (`whileHeld`) – good for simple plant power control.

`Bindings.update(dt)` handles button state transitions every loop.

---

## 5. Tasks & TaskRunner design

Tasks are designed to be simple and explicit:

* No hidden threads or background schedulers.
* No blocking calls; everything is driven from the main `loop()`.

### 5.1 Queue semantics

`TaskRunner` maintains a **FIFO queue**:

* `enqueue(task)` appends to the queue.
* `update(clock)`:

    * Starts the next task when the current one finishes.
    * Calls `start(clock)` *once* for each task.
    * Calls `update(clock)` once per loop until `isFinished()`.

This makes it possible to choose between:

* **"Latest wins"** – call `clear()` before `enqueue(...)`.
* **Queueing** – just keep calling `enqueue(...)` as events arrive.

### 5.2 Non‑blocking pattern

To keep tasks non‑blocking:

* Use `LoopClock` time instead of `sleep()`.
* Use `RunForSecondsTask` and `WaitUntilTask` instead of manual timers.
* Keep per‑loop `update(clock)` work small.

Example pattern:

```java
public final class WaitForShooterTask implements Task {
    private final Plant shooter;
    private final double target;

    public WaitForShooterTask(Plant shooter, double target) {
        this.shooter = shooter;
        this.target = target;
    }

    @Override
    public void start(LoopClock clock) {
        shooter.setTarget(target);
    }

    @Override
    public void update(LoopClock clock) {
        // nothing extra to do; plant.update(dt) happens in robot code
    }

    @Override
    public boolean isFinished() {
        return shooter.atSetpoint();
    }
}
```

In practice you’ll rarely write this by hand – `PlantTasks` provides helpers for
common patterns – but the underlying idea stays simple.

---

## 6. Debugging and telemetry

Phoenix treats observability as a first‑class concern.

### 6.1 DebugSink

`DebugSink` is a light abstraction over "where debug info goes":

* In FTC OpModes, you often wrap telemetry in a `DebugSink`.
* In tests, you can use `NullDebugSink` or a custom implementation.

Many classes provide a `debugDump(DebugSink dbg)` or similar method where they
log their current state.

Guidelines:

* Avoid huge walls of telemetry every loop.
* Prefer concise key‑value lines (e.g., `vel=`, `target=`, `atSetpoint=`).
* Be defensive if `dbg` is null (even though most robot code will pass a real sink).

### 6.2 Where to put debug calls

The robot class is usually the best place to trigger debug dumps:

* It knows when readings are useful (every loop, or only in certain modes).
* It can coordinate debug output across drive + mechanisms + tasks.

---

## 7. Design patterns and anti‑patterns

### 7.1 Recommended patterns

1. **Robot as composition root**

    * A single season robot class wires plants, drive, tasks, bindings.

2. **Thin OpModes**

    * OpModes create `Gamepads`, `PhoenixRobot`, and `LoopClock`.
    * `loop()` just updates time, inputs, robot, and telemetry.

3. **Tasks for multi‑step behavior**

    * Use `TaskRunner`, `PlantTasks`, `SequenceTask` / `ParallelAllTask` for macros.

4. **Single responsibility**

    * Each class has a small, clear job.
    * Avoid "god" classes that know about everything.

5. **Explicit data flow via interfaces**

    * Use `DriveSignal`, `Plant`, `Task`, and `AprilTagObservation` at module boundaries.

### 7.2 Anti‑patterns to avoid

1. **Blocking code (`sleep()`) in control paths**

    * Freezes TeleOp and destroys loop timing.
    * Use Tasks and `LoopClock` instead.

2. **Scattered hardware access**

    * `hardwareMap.get()` sprinkled across many files.
    * Prefer `Actuators.plant(...)` and central wiring in your robot class.

3. **Scattered gamepad checks**

    * `if (gamepad1.a)` logic everywhere.
    * Prefer `Gamepads` + `Bindings` so controls are centralized.

4. **Giant OpModes**

    * All robot logic inside a single TeleOp class.
    * Prefer a robot class plus thin OpModes.

5. **Tight coupling between unrelated subsystems**

    * E.g., arm code reaching into drivebase via globals.
    * Prefer explicit interaction via shared state on the robot or Tasks.

---

## 8. Legacy patterns and evolution

Earlier versions of Phoenix emphasized:

* Base classes: `PhoenixTeleOpBase`, `PhoenixAutoBase`.
* Subsystem classes derived from a `Subsystem` base.

These patterns:

* Hid some important behavior in base classes (harder for students to follow).
* Encouraged splitting robot logic across many small classes.

The current design prefers:

* **Composition over inheritance** – a single robot class that owns everything.
* Plain FTC OpModes instead of framework base classes.

Legacy base classes remain for backward compatibility and teams who still
use them, but new code and docs focus on the composition‑based approach.

See **Notes & Migration Guide** for more on moving older code to the new style.

---

## 9. Summary

Phoenix is built around a few key ideas:

* **Plants** model mechanisms with targets and per‑loop updates.
* **Tasks** describe behaviors over time; `TaskRunner` orchestrates them.
* **Bindings** connect driver input to plant targets and task queues.
* A **robot class** composes everything; OpModes stay thin.
* Tools like **TagAim**, **AprilTagSensor**, and **InterpolatingTable1D** layer on
  advanced features without changing these fundamentals.

As long as new code respects these principles:

* Student robot code stays readable.
* Complex behaviors remain non‑blocking and composable.
* The framework can grow without becoming fragile or confusing.

Use this document as a reference when making structural decisions or extending
Phoenix with new capabilities.

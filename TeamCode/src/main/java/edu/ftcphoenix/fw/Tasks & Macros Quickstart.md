# Tasks & Macros Quickstart

This guide explains how to use **Tasks** in the Phoenix framework to build non‑blocking behaviors:

* TeleOp **macros** (e.g., shooting sequences).
* **Autonomous routines** built out of reusable pieces.

We assume you already have a `PhoenixRobot` wired as in the Beginner’s Guide:

* `LoopClock` for timing.
* `Gamepads` and `Bindings` for input.
* Drive and mechanisms modeled as `DriveSource` / `MecanumDrivebase` and `Plant`s.

Everything here is **non‑blocking** – there is no `sleep()` and no `while` loops that stall TeleOp.

---

## 1. Why Tasks?

In FTC it’s tempting to write blocking code like:

```java
// DON'T DO THIS
shooterMotor.setPower(1.0);
sleep(1000);
transferMotor.setPower(1.0);
sleep(300);
transferMotor.setPower(0.0);
```

This freezes the OpMode:

* Driver input is ignored while `sleep()` runs.
* Loop timing becomes unpredictable.
* Telemetry and safety checks are delayed.

Phoenix Tasks solve this by making multi‑step behaviors **non‑blocking** and **composable**:

* A `Task` represents *“do this behavior over time”*.
* A `TaskRunner` advances the task a little bit each loop.
* You can combine tasks into sequences and parallel groups.

---

## 2. Core concepts

Phoenix uses a small set of types for time‑based behavior:

* `Task` – interface for non‑blocking behaviors.
* `TaskRunner` – runs queued tasks in order.
* `LoopClock` – timing helper (stores `dtSec`, current time, etc.).
* Building‑block task classes:

    * `InstantTask`
    * `RunForSecondsTask`
    * `WaitUntilTask`
    * `SequenceTask`
    * `ParallelAllTask`
* `PlantTasks` – helpers for building tasks that command plants.

### 2.1 `Task`

```java
public interface Task {
    void start(LoopClock clock);
    void update(LoopClock clock);
    boolean isFinished();
}
```

Rules:

* `start(...)` and `update(...)` must **return quickly** (no blocking).
* Use internal state (fields, enums, timers) to track progress.
* `isFinished()` tells the runner when the task is done.

### 2.2 `TaskRunner`

`TaskRunner` manages a **queue** of tasks and runs them one at a time.

Key methods:

```java
TaskRunner runner = new TaskRunner();

runner.enqueue(task);      // add to the end of the queue
runner.update(clock);      // advance current task
runner.clear();            // drop current + queued tasks
boolean idle = runner.isIdle();
```

Semantics of `update(clock)`:

1. If there is no current task or the current task is finished, it:

    * pulls the next task from the queue,
    * calls its `start(clock)`.
2. If that task finished immediately in `start(...)`, it keeps pulling the next one.
3. If there is a task that is not finished, it calls `update(clock)` on it **once**.

You can enqueue multiple tasks in a row; they will run sequentially.

> **Note:** There is no explicit `cancel()` on `Task`. If you call `clear()`, the runner simply stops
> calling `update()` on the abandoned task. Tasks should tolerate this.

### 2.3 `LoopClock`

`LoopClock` is a tiny helper that works with `OpMode.getRuntime()`:

```java
LoopClock clock = new LoopClock();

// In init():
clock.reset(getRuntime());

// In loop():
clock.update(getRuntime());
double dt = clock.dtSec();
```

Use the same `LoopClock` instance for:

* drive and plants,
* tasks (`TaskRunner.update(clock)`),
* gamepads / bindings (passing `dt` through if desired).

---

## 3. Tasks and macros in TeleOp

We’ll start with simple TeleOp patterns and then build up to more complex macros.

### 3.1 Adding a TaskRunner to your robot

In your `PhoenixRobot` class:

```java
public final class PhoenixRobot {
    // ... other fields ...

    private final TaskRunner taskRunner = new TaskRunner();

    public TaskRunner taskRunner() {
        return taskRunner;
    }

    public void updateTeleOp(LoopClock clock) {
        double dt = clock.dtSec();

        // Drive + plant updates...
        DriveSignal signal = driveSource.get(clock);
        drivebase.apply(signal);

        shooterPlant.update(dt);
        intakePlant.update(dt);
        pusherPlant.update(dt);

        // Advance any running tasks/macros.
        taskRunner.update(clock);
    }
}
```

> In your TeleOp OpMode, you’ve already got a `LoopClock`, `Gamepads`, and `Bindings` wired as in the
> Beginner’s Guide – we’ll just add bindings that enqueue tasks.

---

## 4. Simple example: timed intake pulse

Goal: **run intake at full power for 0.7 seconds, then stop**, when the driver presses A.

Assumptions:

* `intakePlant` is a `Plant` for your intake motor.
* `PhoenixRobot` has a `TaskRunner` (as above).

### 4.1 Define a helper Task on your robot

```java
public Task intakePulse() {
    // Run intake at full power for 0.7 seconds, then stop.
    return PlantTasks.holdForSeconds(intakePlant, +1.0, 0.7);
}
```

`holdForSeconds` is a helper that internally builds a `RunForSecondsTask` and handles the
“start at this target, then stop” pattern.

### 4.2 Bind a button to enqueue the Task

In `PhoenixRobot.configureBindings()`:

```java
private void configureBindings() {
    GamepadDevice p1 = pads.p1();

    // Other bindings...

    bindings.onPress(p1.a(), () -> {
        // Optional: cancel any existing macros for "intake channel".
        // taskRunner.clear();

        taskRunner.enqueue(intakePulse());
    });
}
```

Now, when the driver **taps A**:

* The intake macro is enqueued.
* `updateTeleOp(clock)` calls `taskRunner.update(clock)` each loop.
* Intake runs for ~0.7 seconds, then stops, without blocking TeleOp.

---

## 5. Shooter macro: single‑disc example

Now let’s build a slightly more interesting macro: **shoot one disc**.

Assume you have plants:

* `shooterPlant` – velocity plant for shooter wheel.
* `transferPlant` – power plant for transfer motor.

We want the macro to:

1. Command shooter to target velocity and wait until `atSetpoint()`.
2. Run transfer forward to feed a disc.
3. Stop transfer (and optionally slow/stop shooter).

### 5.1 Helper Tasks: spin‑up and feed

In `PhoenixRobot` (or a helper class), you can compose from `PlantTasks`:

```java
public Task spinUpShooter(double targetVel) {
    // Set shooter target and wait until atSetpoint().
    return PlantTasks.setTargetAndWaitForSetpoint(shooterPlant, targetVel);
}

public Task feedOneDisc(double feedPower, double feedDurationSec) {
    // Run transfer at feedPower for feedDurationSec, then stop.
    return PlantTasks.holdForSeconds(transferPlant, feedPower, feedDurationSec);
}
```

Now you can build a single macro from these.

### 5.2 Compose with `SequenceTask`

```java
public Task shootOneDisc(double targetVel, double feedPower, double feedDurationSec) {
    List<Task> steps = new ArrayList<>();

    // 1. Spin up.
    steps.add(spinUpShooter(targetVel));

    // 2. Feed one disc.
    steps.add(feedOneDisc(feedPower, feedDurationSec));

    // 3. Optionally stop transfer explicitly (PlantTasks.holdForSeconds with duration 0).
    steps.add(PlantTasks.holdForSeconds(transferPlant, 0.0, 0.0));

    return new SequenceTask(steps);
}
```

### 5.3 Bind a button to enqueue the shooting macro

```java
private static final double SHOOTER_VELOCITY = 3000.0; // example units
private static final double FEED_POWER = 1.0;
private static final double FEED_TIME = 0.3;          // seconds

private void configureBindings() {
    GamepadDevice p1 = pads.p1();

    // ...other bindings...

    bindings.onPress(p1.y(), () -> {
        // If you want "latest press wins" semantics, clear existing tasks first.
        taskRunner.clear();

        Task shootTask = shootOneDisc(SHOOTER_VELOCITY, FEED_POWER, FEED_TIME);
        taskRunner.enqueue(shootTask);
    });
}
```

Pressing **Y** enqueues the macro. While it runs:

* The driver can still steer (drive code is independent).
* Other bindings can still fire and adjust plants unrelated to the macro.

If you **don’t** clear the runner before enqueuing, each Y press will queue another
single‑disc shot after the previous one finishes.

---

## 6. Multi‑disc and composite Tasks

Once you have single‑disc helpers, building more complex macros is just composition.

### 6.1 Shoot three discs in sequence

```java
public Task shootThreeDiscs(double targetVel,
                            double feedPower,
                            double feedDurationSec,
                            double pauseBetweenSec) {
    List<Task> steps = new ArrayList<>();

    // Spin up once and wait.
    steps.add(spinUpShooter(targetVel));

    for (int i = 0; i < 3; i++) {
        // Feed disc i.
        steps.add(feedOneDisc(feedPower, feedDurationSec));

        // Small pause with transfer stopped between feeds.
        if (i < 2) {
            steps.add(PlantTasks.holdForSeconds(transferPlant, 0.0, pauseBetweenSec));
        }
    }

    // Optionally stop shooter at the end.
    steps.add(PlantTasks.setTargetInstant(shooterPlant, 0.0));

    return new SequenceTask(steps);
}
```

Bind to a button similarly:

```java
bindings.onPress(p1.rightBumper(), () -> {
    taskRunner.clear();
    taskRunner.enqueue(shootThreeDiscs(
            SHOOTER_VELOCITY,
            FEED_POWER,
            FEED_TIME,
            0.1
    ));
});
```

### 6.2 Doing things in parallel

Sometimes you want two behaviors at once, e.g. **drive forward while intaking**.

If you build a `Task` for the drive part, you can combine with `ParallelAllTask`:

```java
public Task driveForwardForSeconds(double axialPower, double durationSec) {
    return new RunForSecondsTask(
            durationSec,
            // onStart
            () -> driveSignalOverride = new DriveSignal(axialPower, 0.0, 0.0),
            // onUpdate (no-op; plants + drive update elsewhere)
            null,
            // onFinish
            () -> driveSignalOverride = null
    );
}

public Task driveAndIntake(double axialPower,
                           double durationSec,
                           double intakePower) {
    List<Task> tasks = new ArrayList<>();
    tasks.add(driveForwardForSeconds(axialPower, durationSec));
    tasks.add(PlantTasks.holdForSeconds(intakePlant, intakePower, durationSec));
    return new ParallelAllTask(tasks);
}
```

In `updateTeleOp(clock)`, your drive code might check `driveSignalOverride` first and
fall back to `driveSource.get(clock)` when it is `null`.

> Parallel behavior is an advanced pattern. For many robots, just using `SequenceTask` and
> separate drive tasks is enough.

---

## 7. Tasks in Autonomous

Tasks are not just for TeleOp. A clean way to write Autonomous is:

1. Build a **single Task** that describes your whole routine.
2. Enqueue it on a `TaskRunner`.
3. Call `taskRunner.update(clock)` each loop.

### 7.1 Auto OpMode structure

```java
@Autonomous(name = "Phoenix Auto Center")
public class PhoenixAutoCenter extends OpMode {
    private final LoopClock clock = new LoopClock();

    private PhoenixRobot robot;
    private Gamepads pads;
    private TaskRunner taskRunner;

    @Override
    public void init() {
        pads = Gamepads.create(gamepad1, gamepad2);

        robot = new PhoenixRobot(hardwareMap, pads);
        taskRunner = robot.taskRunner();

        // Build the autonomous routine once.
        Task autoTask = robot.buildCenterAuto();
        taskRunner.clear();
        taskRunner.enqueue(autoTask);

        clock.reset(getRuntime());
    }

    @Override
    public void loop() {
        clock.update(getRuntime());

        // Auto-specific robot logic (drive + plants).
        robot.updateAuto(clock);

        // Advance the auto Task.
        taskRunner.update(clock);

        telemetry.update();
    }
}
```

### 7.2 Building the auto Task on your robot

In `PhoenixRobot`:

```java
public Task buildCenterAuto() {
    List<Task> steps = new ArrayList<>();

    steps.add(driveForwardForSeconds(0.5, 1.2));      // drive to spike
    steps.add(placePreloadTask());                    // arm + pusher sequence
    steps.add(driveToShootingPositionTask());         // more drive
    steps.add(shootThreeDiscs(autoVelocity(), 1.0, 0.3, 0.1));
    steps.add(parkInSafeZoneTask());

    return new SequenceTask(steps);
}
```

Each helper (`placePreloadTask`, `parkInSafeZoneTask`, etc.) is itself a `Task` that uses
plants and, optionally, small drive tasks.

The important point: **TeleOp macros and Autonomous routines both use the same Task model.**

---

## 8. Design guidelines and best practices

A few guidelines to keep your task code clean and robust:

### 8.1 Keep tasks non‑blocking

* Never call `sleep()` or busy‑wait inside a `Task`.
* Use `RunForSecondsTask`, `WaitUntilTask`, and `PlantTasks` helpers instead.
* `start(clock)` / `update(clock)` should return quickly.

### 8.2 Prefer small, composable tasks

* Write small tasks for “spin up shooter”, “feed one disc”, “drive forward 1s”, etc.
* Combine them with `SequenceTask` and `ParallelAllTask` instead of one giant state machine.

### 8.3 Decide how you want to handle interruptions

* If you call `taskRunner.clear()` before enqueueing, you get **“latest macro wins”** semantics.
* If you just `enqueue(...)` without clearing, you get **queueing** semantics.
* For critical actions (e.g., climbing), consider restricting what other bindings can do while the
  sequence is running (e.g., by checking `taskRunner.hasActiveTask()`).

### 8.4 Keep plant updates outside tasks

* Tasks should typically **set targets**, not call `plant.update(...)` directly.
* Your robot’s `updateTeleOp(clock)` / `updateAuto(clock)` should update all plants once per loop.

### 8.5 Centralize task creation on the robot

* Helper methods like `intakePulse()`, `shootOneDisc(...)`, `buildCenterAuto()` belong on your
  robot class.
* Bindings (`Bindings`) should just **enqueue** those tasks.

---

## 9. Summary

* **`Task`** is the basic unit of non‑blocking behavior over time.
* **`TaskRunner`** manages a queue of tasks and advances them with `update(clock)`.
* **`InstantTask`**, **`RunForSecondsTask`**, **`WaitUntilTask`**, **`SequenceTask`**, and
  **`ParallelAllTask`** are the main building blocks.
* **`PlantTasks`** make it easy to build tasks around existing mechanism plants.
* **`Bindings`** connect driver inputs to enqueueing tasks in TeleOp.
* TeleOp macros and Autonomous routines both use the same task patterns.

Once you are comfortable with these patterns, you can layer in more advanced pieces like
TagAim + vision and interpolated shooter speeds; they all compose on top of the same
Task/Plant/Drive structure.

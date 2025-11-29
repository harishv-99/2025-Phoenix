# Tasks & Macros Quickstart

This document explains how to use **Tasks** in the Phoenix framework to build non‑blocking
behaviors:

* TeleOp **macros** (e.g., shooting sequences).
* **Autonomous routines** built out of reusable pieces.

The goal is that your robot code reads like:

```java
// TeleOp: when Y is pressed, run a shooting macro.
bindings.onPress(p1.y(), ()->taskRunner.

start(robot.shootThreeDiscs()));

// Auto: build a sequence once, then run it each loop.
Task autoRoutine = robot.buildCenterSpikeAuto();
```

---

## 1. Why Tasks?

In FTC it’s very tempting to write code like:

```java
// DON'T DO THIS
shooter.setPower(1.0);

sleep(1000);
transfer.

setPower(1.0);

sleep(300);
transfer.

setPower(0.0);
```

This looks simple, but it **blocks** the robot:

* Your robot can’t respond to driver input while it’s sleeping.
* The loop rate becomes unpredictable.
* Telemetry updates and safeguards (like watchdogs) are delayed.

Phoenix Tasks solve this by making all multi‑step behaviors **non‑blocking** and **composable**:

* A `Task` represents *“do this behavior over time”*.
* A `TaskRunner` advances the task a little bit each loop.
* You can combine tasks into sequences and parallel groups.

---

## 2. Core concepts

All task classes live under `edu.ftcphoenix.fw.task`, with helpers under
`edu.ftcphoenix.fw.actuation.PlantTasks` and (optionally) drive helpers.

### 2.1 Task

The core interface is `Task`:

* `start()` – called once when the task begins.
* `update(double dt)` – called every loop with the timestep in seconds.
* `isFinished()` – returns true once the task is done.

Tasks should be **non‑blocking**:

* `start()` and `update(...)` must return quickly.
* Use internal state (enums, timers) to keep track of progress.

### 2.2 TaskRunner

`TaskRunner` owns a single active task and is responsible for advancing it.

Typical usage:

```java
// Create once, probably in your robot or OpMode.
TaskRunner taskRunner = new TaskRunner();

// Start a new task (replaces any currently running task).
taskRunner.

start(robot.shootThreeDiscs());

// In your loop(), every cycle:
        clock.

update();

double dt = clock.getDtSeconds();
taskRunner.

update(dt);
```

When `taskRunner.update(dt)` is called:

* If there is no active task, nothing happens.
* If there is an active task:

    * `start()` is called the first time.
    * `update(dt)` is called every loop.
    * Once `isFinished()` returns true, the task is cleared.

### 2.3 Building blocks

Phoenix provides several ready‑made task types:

* `InstantTask` – does something instantly and finishes immediately.
  Use for "fire‑and‑forget" actions or to glue other tasks together.

* `RunForSecondsTask` – runs a piece of logic for a fixed time.

* `WaitUntilTask` – waits until a condition becomes true.

* `SequenceTask` – runs a list of tasks **in order**.

* `ParallelAllTask` – runs a list of tasks **at the same time**, and finishes when **all** are done.

On top of these, `PlantTasks` give you convenient factories for tasks that drive plants.

### 2.4 PlantTasks – driving mechanisms from tasks

`PlantTasks` (in `fw.actuation`) contains helpers like:

* `holdForSeconds(plant, target, durationSeconds)` – set a plant’s target, hold it for some time,
  then usually restore or stop.
* `goToSetpointAndWait(plant, target)` – command a plant to a setpoint and wait until it reports
  `atSetpoint()`.

You can use these directly in your own higher‑level tasks.

---

## 3. Simple example: timed intake pulse

Let’s start with a very simple macro: **run intake for 0.7s, then stop**.

Assume you already have:

* `Plant intakePlant;` – a power plant for your intake motor.
* A `TaskRunner` instance called `taskRunner`.

### 3.1 Define the macro on your robot

In your robot class:

```java
public Task intakePulse() {
    // Run intake at full power for 0.7 seconds, then stop.
    return PlantTasks.holdForSeconds(intakePlant, +1.0, 0.7);
}
```

### 3.2 Bind a button to the macro

In your TeleOp setup code (where you configure `Bindings`):

```java
bindings.onPress(p1.a(), ()->taskRunner.

start(robot.intakePulse()));
```

Now, whenever the driver presses **A**, the intake will run for ~0.7 seconds then stop, without
blocking the rest of TeleOp.

### 3.3 Update the TaskRunner each loop

In your OpMode’s `loop()` (or equivalent):

```java
clock.update();

double dt = clock.getDtSeconds();

Gamepads.

update(gamepad1, gamepad2);
bindings.

update();

robot.

updateTeleOp(dt);

taskRunner.

update(dt);
```

The macro runs “in the background” while the driver can still steer the robot.

---

## 4. Shooter macro: single‑disc example

Now let’s build a slightly more interesting macro: **shoot one disc**.

We’ll assume you already have plants for:

* `shooterPlant` – controls shooter wheel velocity.
* `transferPlant` – controls the transfer motor.

We’ll also assume `shooterPlant` has some notion of `atSetpoint()` (e.g., within a tolerance of the
target velocity).

### 4.1 Robot helper: build the task

In your robot class:

```java
public Task shootOneDisc(double targetVelocity, double feedDurationSeconds) {
    return new SequenceTask(
            // 1. Go to shooter speed and wait until at setpoint.
            PlantTasks.goToSetpointAndWait(shooterPlant, targetVelocity),

            // 2. Run transfer forward to feed one disc.
            PlantTasks.holdForSeconds(transferPlant, +1.0, feedDurationSeconds),

            // 3. Stop transfer.
            PlantTasks.holdForSeconds(transferPlant, 0.0, 0.0)
    );
}
```

You can adjust this pattern to your mechanism (pushers, multiple feeds, etc.).

### 4.2 Binding a button to shoot

In TeleOp setup:

```java
private static final double SHOOTER_VELOCITY = 3000.0; // example units
private static final double FEED_TIME = 0.3;           // seconds

bindings.

onPress(p1.y(), ()->{
Task shootTask = robot.shootOneDisc(SHOOTER_VELOCITY, FEED_TIME);
    taskRunner.

start(shootTask);
});
```

Now pressing **Y** triggers a non‑blocking shooting sequence.

### 4.3 While the macro runs

* The driver can still move the robot (drive input is independent of the task).
* The macro might still be running when the driver presses other buttons.
* If you call `taskRunner.start(...)` again while a task is running, the new task **replaces** the
  old one.

If you need multiple overlapping behaviors, you can:

* Use `ParallelAllTask` inside a single composite task, or
* Own multiple `TaskRunner` instances (for truly independent subsystems) — but start with one until
  you have a clear reason.

---

## 5. Multi‑disc and composite tasks

Once you have a single‑disc macro, building more complex macros is just composition.

### 5.1 Shoot three discs in sequence

In your robot class:

```java
public Task shootThreeDiscs(double targetVelocity, double feedDurationSeconds) {
    return new SequenceTask(
            // Optionally: spin up once.
            PlantTasks.goToSetpointAndWait(shooterPlant, targetVelocity),

            // Disc 1.
            PlantTasks.holdForSeconds(transferPlant, +1.0, feedDurationSeconds),
            PlantTasks.holdForSeconds(transferPlant, 0.0, 0.1),

            // Disc 2.
            PlantTasks.holdForSeconds(transferPlant, +1.0, feedDurationSeconds),
            PlantTasks.holdForSeconds(transferPlant, 0.0, 0.1),

            // Disc 3.
            PlantTasks.holdForSeconds(transferPlant, +1.0, feedDurationSeconds),
            PlantTasks.holdForSeconds(transferPlant, 0.0, 0.1)

            // Optionally add: slow down shooter or stop here.
    );
}
```

Then map it to a button exactly like the single‑disc version.

### 5.2 Adding parallel behavior

You might want to do two things at once, such as:

* Drive forward while running an intake, or
* Hold shooter speed while feeding discs (if they share a time window).

You can use `ParallelAllTask` for this:

```java
Task driveAndIntake = new ParallelAllTask(
        driveForwardForSeconds(1.0),
        PlantTasks.holdForSeconds(intakePlant, +1.0, 1.0)
);
```

This assumes you have a `driveForwardForSeconds(...)` task that controls your drivebase. The Phoenix
framework includes helpers like `RunForSecondsTask` and drive‑related building blocks that make
writing such tasks straightforward.

---

## 6. Tasks in Autonomous

Tasks are not just for TeleOp macros. A very clean way to write Autonomous is:

1. Build a **single Task** that describes your whole routine.
2. Hand it to a `TaskRunner`.
3. On each `loop()`, just `update(dt)` the runner.

### 6.1 Example Auto structure

In your Auto OpMode:

```java
public class AutoCenterSpike extends LinearOpMode { /* or OpMode-style */
    private final LoopClock clock = new LoopClock();
    private final TaskRunner taskRunner = new TaskRunner();
    private PhoenixRobot robot;

    @Override
    public void runOpMode() {
        FtcHardware hw = new FtcHardware(hardwareMap);
        DebugSink dbg = new FtcTelemetryDebugSink(telemetry);
        robot = new PhoenixRobot(hw, dbg);

        Task autoTask = robot.buildCenterSpikeAuto();
        taskRunner.start(autoTask);

        waitForStart();
        clock.reset();

        while (opModeIsActive() && !taskRunner.isIdle()) {
            clock.update();
            double dt = clock.getDtSeconds();

            robot.updateAuto(dt);
            taskRunner.update(dt);

            telemetry.update();
        }
    }
}
```

The important part is that **Autonomous code looks just like a big TeleOp macro**.

### 6.2 Building the Auto task on your robot

On your robot class, you might have something like:

```java
public Task buildCenterSpikeAuto() {
    return new SequenceTask(
            driveForwardDistance(0.8),      // drive to spike
            placePreload(),                 // arm + pusher sequence
            driveToShootingPosition(),      // another drive task
            shootThreeDiscs(autoVelocity(), 0.3),
            parkInSafeZone()
    );
}
```

Each of those helpers (`driveForwardDistance`, `placePreload`, etc.) is itself a `Task` that may use
`PlantTasks` and drive tasks.

This keeps Autonomous routines **declarative** and easy to tweak.

---

## 7. Binding Tasks to Inputs with `Bindings`

In TeleOp, you rarely call `taskRunner.start(...)` directly from the OpMode. Instead, you:

1. Configure **Bindings** at initialization.
2. Call `bindings.update()` once per loop.

### 7.1 Example binding configuration

```java
public void configureBindings(Bindings bindings, TaskRunner taskRunner) {
    GamepadDevice p1 = Gamepads.player1();

    // Intake pulse on A.
    bindings.onPress(p1.a(), () -> taskRunner.start(intakePulse()));

    // Shoot three discs on Y.
    bindings.onPress(p1.y(), () -> taskRunner.start(
            shootThreeDiscs(getTeleOpShooterVelocity(), 0.3)
    ));
}
```

In your OpMode:

```java
public void loop() {
    clock.update();
    double dt = clock.getDtSeconds();

    Gamepads.update(gamepad1, gamepad2);
    bindings.update();

    robot.updateTeleOp(dt);
    taskRunner.update(dt);
}
```

This separates **input wiring** from **behavior definition**.

---

## 8. Design guidelines and best practices

A few guidelines to keep your task code clean and robust:

### 8.1 Keep tasks non‑blocking

* Never call `sleep()`, `Thread.sleep()`, or busy loops inside a task.
* Use `RunForSecondsTask` and `WaitUntilTask` instead.
* Your `update(dt)` methods should return quickly.

### 8.2 Make tasks small and composable

* Prefer multiple small tasks combined with `SequenceTask` / `ParallelAllTask`.
* This makes them easier to test and reuse.

### 8.3 Drive through plants and helpers

* In general, tasks should manipulate **plants** and `DriveSignal`s, not raw hardware.
* This keeps hardware access localized to a few places (`FtcHardware`, `Actuators`, your drivebase).

### 8.4 Use your robot class as a task factory

* Methods like `shootOneDisc(...)`, `shootThreeDiscs(...)`, `driveForwardDistance(...)` belong on
  your robot.
* This keeps OpModes and Bindings very simple.

### 8.5 Think about interruptions

* Starting a new task while another is running usually **replaces** the current one.
* For critical sequences (e.g., climbing), consider whether you want to allow interruption or
  require the sequence to finish.

---

## 9. Where to go next

After you’re comfortable with the patterns in this quickstart:

* Look at **Example 03** in the Shooter Case Study, which shows a concrete shooter macro.
* Look at **Example 06** to see tasks combined with TagAim and interpolated shooter speeds.
* For deeper architectural background and patterns, read **Framework Principles**.

Tasks are one of the main “glue pieces” in Phoenix. Once you have them set up, both TeleOp macros
and Autonomous routines become straightforward compositions of the same building blocks.

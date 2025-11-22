# Phoenix Tasks & Macros Quickstart

This guide shows how to use **tasks** and **macros** in the Phoenix framework.

The goals:

* Express multi‑step behaviors ("shoot 3 discs", "intake for 0.7s and stop") without blocking the
  OpMode.
* Trigger those behaviors from TeleOp buttons **while** the driver can still steer normally.
* Build clean Autonomous routines from the same building blocks.

Everything here is **non‑blocking** – there is no `sleep()` or `while` loop inside your tasks.
Tasks run alongside your normal TeleOp loop.

---

## 1. Core Concepts

Phoenix uses a small set of core types for behavior:

* **`Task`** – an object that represents a non‑blocking behavior.
* **`TaskRunner`** – advances a `Task` each loop until it completes.
* **`Tasks`** – static helpers to build common patterns (sequences, waits, parallels, etc.).
* **`PlantTasks`** – helpers that create tasks around mechanism plants.
* **`DriveTasks`** – helpers that create tasks around drive.
* **`Bindings`** – maps buttons (`Button`) to actions (start tasks, cancel tasks, toggle modes).

At a high level:

```text
TeleOp loop
  ├─ Gamepads & DriverKit updated (PhoenixTeleOpBase)
  ├─ Bindings evaluated (PhoenixTeleOpBase)
  ├─ Your robot reads driver input (manual drive, simple plants)
  └─ TaskRunner.update(clock) (advances any active tasks)
```

If you call `TaskRunner.update(...)` every loop, tasks tick along automatically.

---

## 2. The Core Types

### 2.1 Task

A `Task` encapsulates a piece of non‑blocking behavior. Conceptually, a task:

* has an internal state,
* is updated each loop, and
* eventually reports that it is finished.

You rarely implement `Task` directly; instead, you use helper factories (`Tasks`, `PlantTasks`,
`DriveTasks`).

### 2.2 TaskRunner

A `TaskRunner` owns a **current** task and advances it each loop.

Typical usage in TeleOp:

```java
import edu.ftcphoenix.fw.task.TaskRunner;

public final class PhoenixTeleOpWithMacros extends PhoenixTeleOpBase {

    private PhoenixRobot robot;
    private final TaskRunner taskRunner = new TaskRunner();

    @Override
    protected void onInitRobot() {
        robot = new PhoenixRobot(hardwareMap, driverKit(), telemetry);
        configureBindings();
    }

    private void configureBindings() {
        // Configure button bindings here (see Section 4)
    }

    @Override
    protected void onLoopRobot(double dtSec) {
        LoopClock clock = clock();

        // Let the robot do its usual TeleOp work (drive, simple plants, etc.)
        robot.onTeleopLoop(clock);

        // Advance any active task
        taskRunner.update(clock);
    }
}
```

Tasks do **not** run by themselves; they only move forward when `taskRunner.update(clock)` is
called.

### 2.3 Tasks helpers

`Tasks` is a small static utility class that builds common task patterns out of lower‑level
building blocks:

* `Tasks.sequence(Task... tasks)` – run tasks one after another.
* `Tasks.parallel(Task... tasks)` – run tasks in parallel; complete when all finish.
* `Tasks.race(Task... tasks)` – run tasks in parallel; complete when the first finishes.
* `Tasks.waitSeconds(double seconds)` – wait for a period of time.
* `Tasks.waitUntil(BooleanSupplier condition)` – wait until a condition is true.

These helpers wrap internal types like `SequenceTask`, `ParallelAllTask`, `RunForSecondsTask`,
etc. Robot code should **prefer** the `Tasks` façade and avoid referencing the lower‑level
classes directly.

### 2.4 PlantTasks and DriveTasks

`PlantTasks` and `DriveTasks` are domain‑specific helpers built on top of `Task` and `Tasks`.

Examples of typical helpers:

* `PlantTasks.holdForSeconds(Plant plant, double target, double seconds)`
* `PlantTasks.goToSetpointAndWait(Plant plant, double setpoint)`
* `DriveTasks.driveForSeconds(MecanumDrivebase drivebase, DriveSource source, double seconds)`

These helpers read in the necessary pieces (plants, drivebase + drive sources) and produce `Task`
instances that can be run in Auto or TeleOp macros.

---

## 3. TeleOp Macro Example: Shoot 3 Discs

In this example we’ll build a TeleOp macro:

> When the driver presses **Y**, run a non‑blocking 3‑disc auto‑shoot sequence while allowing
> manual driving to continue.

### 3.1 Robot fields

We’ll assume your `PhoenixRobot` has:

* A `MecanumDrivebase` + `DriveSource` already wired as in the Beginner’s Guide.
* Plants for shooter and intake created via `FtcPlants`.

```java
public final class PhoenixRobot {

    // Hardware names
    private static final String HW_FL = "frontLeft";
    private static final String HW_FR = "frontRight";
    private static final String HW_BL = "backLeft";
    private static final String HW_BR = "backRight";
    private static final String HW_SHOOTER_LEFT = "shooterLeft";
    private static final String HW_SHOOTER_RIGHT = "shooterRight";
    private static final String HW_INTAKE = "intake";

    // Drive
    private final MecanumDrivebase drivebase;
    private DriveSource driveSource;

    // Mechanisms
    private final Plant shooter; // dual‑motor velocity plant
    private final Plant intake;  // power‑only plant

    public PhoenixRobot(HardwareMap hw, DriverKit driverKit, Telemetry telemetry) {
        // Drive wiring
        this.drivebase = Drives
                .mecanum(hw)
                .names(HW_FL, HW_FR, HW_BL, HW_BR)
                .invertFrontRight()
                .invertBackRight()
                .build();

        this.driveSource = StickDriveSource.teleOpMecanumWithSlowMode(
                driverKit,
                driverKit.p1().rightBumper(),
                0.30);

        // Mechanism wiring
        DcMotorEx leftShooter = hw.get(DcMotorEx.class, HW_SHOOTER_LEFT);
        DcMotorEx rightShooter = hw.get(DcMotorEx.class, HW_SHOOTER_RIGHT);
        DcMotorEx intakeMotor = hw.get(DcMotorEx.class, HW_INTAKE);

        this.shooter = FtcPlants.dualVelocityShooter(leftShooter, rightShooter, /*ticksPerRev*/ 28);
        this.intake = FtcPlants.powerOnly(intakeMotor);
    }

    public void onTeleopLoop(LoopClock clock) {
        // Drive manually every loop
        DriveSignal cmd = driveSource.get(clock);
        drivebase.drive(cmd);

        // Continuous plants (if any) would be updated here
        shooter.update(clock);
        intake.update(clock);
    }

    // Expose factory methods for tasks (see next section)
}
```

### 3.2 A robot‑provided Task factory

Instead of constructing tasks in the OpMode directly, it’s often cleaner for the robot to expose
methods that *create* tasks using its plants and constants.

```java
public final class PhoenixRobot {
    // ...fields and constructor from above...

    private static final double SHOOT_RPS = 35.0; // example value
    private static final double FEED_POWER = +1.0;
    private static final double FEED_TIME_S = 1.0;

    /** Build a task that shoots 3 discs non‑blocking. */
    public Task buildShootThreeTask() {
        Task spinUp = PlantTasks.goToSetpointAndWait(shooter, SHOOT_RPS);

        // Feed once per disc
        Task feedOne = PlantTasks.holdForSeconds(intake, FEED_POWER, FEED_TIME_S);

        return Tasks.sequence(
                spinUp,               // 1) spin up
                feedOne,              // 2) feed #1
                feedOne,              // 3) feed #2
                feedOne,              // 4) feed #3
                PlantTasks.holdForSeconds(shooter, 0.0, 0.3)); // 5) short spin‑down
    }
}
```

### 3.3 TeleOp shell with TaskRunner + Bindings

Now we wire a TeleOp that:

* Drives the robot manually (using `PhoenixRobot.onTeleopLoop`).
* Owns a `TaskRunner` for macros.
* Uses `Bindings` to start the `shootThree` task when Y is pressed.

```java

@TeleOp(name = "Phoenix: TeleOp with Macros", group = "Phoenix")
public final class PhoenixTeleOpWithMacros extends PhoenixTeleOpBase {

    private PhoenixRobot robot;
    private final TaskRunner taskRunner = new TaskRunner();

    @Override
    protected void onInitRobot() {
        robot = new PhoenixRobot(hardwareMap, driverKit(), telemetry);

        configureBindings();

        telemetry.addLine("PhoenixTeleOpWithMacros: init complete");
        telemetry.update();
    }

    private void configureBindings() {
        // Shortcuts from base class
        DriverKit.Player p1 = p1();

        // When Y is pressed, start a new shootThree task.
        bind().onPressed(p1.y(), () -> {
            Task shootThree = robot.buildShootThreeTask();
            taskRunner.start(shootThree);
        });

        // Optional: allow B to cancel any active task.
        bind().onPressed(p1.b(), taskRunner::cancel);
    }

    @Override
    protected void onLoopRobot(double dtSec) {
        LoopClock loopClock = clock();

        // Normal TeleOp behavior (drive + continuous plants)
        robot.onTeleopLoop(loopClock);

        // Advance any active tasks
        taskRunner.update(loopClock);
    }

    @Override
    protected void onStopRobot() {
        taskRunner.cancel();
        robot.onStop();
    }
}
```

Behavior:

* The driver can **always** steer with sticks.
* Press Y once → `shootThree` task starts.
* The shooter + intake plants are controlled by the task **while** the driver still controls
  movement.
* Once the sequence completes, the TaskRunner returns to idle.
* Press B to cancel any running task.

No blocking loops; everything is driven by the main TeleOp loop.

---

## 4. Bindings: Mapping Buttons to Tasks

`Bindings` is a small helper that maps `Button` objects to actions.

Common patterns:

* `onPressed(button, Runnable action)` – runs whenever the button transitions from **not pressed**
  to **pressed**.
* `onReleased(button, Runnable action)` – runs when the button transitions from **pressed** to
  **not pressed**.
* `whileHeld(button, Runnable action)` – runs every loop while the button is pressed.

In the tasks context, you typically:

* Start a task when a button is pressed.
* Optionally cancel the current task when another button is pressed.

Examples:

```java
private void configureBindings() {
    DriverKit.Player p1 = p1();

    // Start an intake pulse when X is pressed
    bind().onPressed(p1.x(), () -> {
        Task intakePulse = PlantTasks.holdForSeconds(intakePlant, +1.0, 0.7);
        taskRunner.start(intakePulse);
    });

    // Toggle a "hold shooter at speed" task on A
    bind().onPressed(p1.a(), () -> {
        if (taskRunner.isRunning()) {
            taskRunner.cancel();
        } else {
            Task holdShooter = PlantTasks.goToSetpointAndWait(shooterPlant, SHOOT_RPS);
            taskRunner.start(holdShooter);
        }
    });
}
```

Remember: `Bindings` lives in the **OpMode base** (`PhoenixTeleOpBase` / `PhoenixAutoBase`), not in
`PhoenixRobot`. Robot code typically exposes methods that create tasks; the shell decides **when**
to start them.

---

## 5. Simple Autonomous with Tasks

The same `Task` building blocks work in Autonomous.

In Auto, you usually:

1. Build a top‑level `Task` when the OpMode starts.
2. Give it to a `TaskRunner`.
3. Call `taskRunner.update(clock)` in each `loop()` until it finishes.

### 5.1 Example: Drive forward, shoot, park

Conceptual autonomous routine:

1. Drive forward for 2 seconds.
2. Shoot 3 discs (using the same `buildShootThreeTask()` as TeleOp).
3. Strafe right and park.

```java

@Autonomous(name = "Phoenix: Simple Auto", group = "Phoenix")
public final class PhoenixSimpleAuto extends PhoenixAutoBase {

    private PhoenixRobot robot;

    @Override
    protected void onInitRobot() {
        robot = new PhoenixRobot(hardwareMap, driverKit(), telemetry);
    }

    @Override
    protected Task buildAutoTask() {
        // 1) Drive forward for 2 seconds
        Task driveForward = DriveTasks.driveForSeconds(
                robot.getDrivebase(),              // MecanumDrivebase
                robot.getAutoForwardSource(),      // DriveSource that commands forward motion
                2.0);

        // 2) Shoot 3 discs using the same method as TeleOp macros
        Task shootThree = robot.buildShootThreeTask();

        // 3) Strafe right and park
        Task strafeRight = DriveTasks.driveForSeconds(
                robot.getDrivebase(),
                robot.getAutoStrafeRightSource(),  // DriveSource that strafes right
                1.5);

        return Tasks.sequence(driveForward, shootThree, strafeRight);
    }
}
```

Here `PhoenixAutoBase` owns a `TaskRunner` internally:

* When the OpMode starts, it calls `buildAutoTask()`.
* It starts that task.
* Each loop, it calls `taskRunner.update(clock)` until the task finishes.

Robot code provides:

* `getDrivebase()` – exposes its `MecanumDrivebase`.
* `getAutoForwardSource()` – returns a `DriveSource` that commands constant forward motion.
* `getAutoStrafeRightSource()` – returns a `DriveSource` for strafing.

These `DriveSource`s can be built using simple helper functions (for example, by returning a
constant `DriveSignal`:

```java
public DriveSource getAutoForwardSource() {
    return clock -> new DriveSignal(+0.6, 0.0, 0.0);
}

public DriveSource getAutoStrafeRightSource() {
    return clock -> new DriveSignal(0.0, +0.6, 0.0);
}
```

Because `DriveSignal` is immutable and blendable (`scaled`, `plus`, `lerp`), you can easily adjust
these behaviors or mix them with others if needed.

---

## 6. Design Guidelines for Tasks and Macros

When designing tasks, keep these guidelines in mind:

1. **Keep tasks non‑blocking**

    * A task update should never call `sleep()` or spin in a loop.
    * Use `Tasks.waitSeconds(...)` or `Tasks.waitUntil(...)` instead of explicit time checks.

2. **Let the OpMode own TaskRunner and Bindings**

    * Keep the control wiring ("when button X is pressed, start task Y") in the TeleOp/Auto shells.
    * Let `PhoenixRobot` and subsystems provide **task factory methods** that know about hardware.

3. **Use Tasks/PlantTasks/DriveTasks instead of building everything manually**

    * These helpers encapsulate common patterns and keep robot code readable.
    * If you find yourself repeating the same pattern across robots, consider adding a helper.

4. **Compose behaviors, don’t monolith them**

    * Build small tasks ("spin up shooter", "feed once", "drive forward for 1 second") and compose
      them with `Tasks.sequence(...)` / `Tasks.parallel(...)`.
    * Avoid giant tasks with many nested `if`/`switch` blocks if they can be broken into smaller
      pieces.

5. **Reuse tasks between TeleOp and Auto**

    * Any task that represents a sensor‑ or time‑based behavior (like `buildShootThreeTask()`)
      should
      be usable in both TeleOp macros and Auto routines.
    * This reduces duplicated logic and helps keep behavior consistent between modes.

---

## 7. Summary

* **`Task`** is the basic unit of non‑blocking behavior.
* **`TaskRunner`** runs a current task, advancing it each loop.
* **`Tasks`**, **`PlantTasks`**, and **`DriveTasks`** build common sequences for you.
* **`Bindings`** connect driver buttons to tasks in TeleOp.
* TeleOp macros and Auto both use the same task model.
* Drive and plants are still controlled by `DriveSource`, `DriveSignal`, and `Plant` – tasks simply
  coordinate them over time.

Once you are comfortable with basic tasks and macros, you can explore **stages** for structuring
more complex subsystems (see the Shooter Case Study document).

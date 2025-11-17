# Phoenix Tasks & Macros Quickstart

This mini-guide shows how to use the Phoenix **task framework** to build simple, non-blocking "macros" in your OpModes.

You should already be somewhat familiar with:

* `Gamepads` → `DriverKit` → `Bindings`
* `MecanumDrivebase` and `StickDriveSource`

If not, start with the **Beginner’s Guide** and `fw.examples.TeleOpMecanumBasic` first.

---

## 1. What is a Task?

Phoenix tasks are small objects that represent **time-extended behaviors**:

> "Do this over time, without blocking the main loop."

Key interfaces:

* `Task`

  * `void start(LoopClock clock)` – called once when the task begins.
  * `void update(LoopClock clock)` – called every loop while the task is active.
  * `boolean isFinished()` – tells the `TaskRunner` when the task is done.
* `TaskRunner`

  * Owns a queue of tasks and runs them in order.
  * Non-blocking: you call `update(clock)` once per loop.

Instead of writing your own state machine with flags and timers inside an OpMode, you:

1. Build one or more `Task` objects.
2. Enqueue them on a `TaskRunner`.
3. Call `TaskRunner.update(clock)` in your loop.

---

## 2. Task building blocks

Phoenix ships with a few basic task types in `fw.task`:

* `InstantTask` – do something once and finish immediately.
* `RunForSecondsTask` – run callbacks for a fixed duration.
* `SequenceTask` – run tasks one after another.
* (Others may be added over time.)

On top of these, we define **domain-specific helpers**:

* `fw.drive.DriveTasks`

  * `driveForSeconds(...)` – hold a `DriveSignal` for a fixed time.
  * `stop(...)` – stop the drivebase once and finish.
* `fw.actuation.PlantTasks`

  * `holdForSeconds(...)` – hold a `Plant` at a target for some time.
  * `goToSetpointAndWait(...)` – move a `Plant` toward a setpoint and finish when `atSetpoint()` is true.
  * `setTargetInstant(...)` – set the target once and finish.

You rarely need to implement `Task` yourself; you compose these building blocks instead.

---

## 3. Basic task pattern in an OpMode

The core pattern is always the same:

1. **Create a `TaskRunner`** as a field in your OpMode.
2. **Create `Task` instances** (often using `DriveTasks` / `PlantTasks`).
3. **Enqueue tasks** when buttons are pressed.
4. **Call `macroRunner.update(clock)`** in `loop()`.
5. **Fall back to manual control** when no task is active.

### 3.1. Fields

```java
public final class TeleOpMacroDrive extends OpMode {
    private final LoopClock clock = new LoopClock();

    private Gamepads gamepads;
    private DriverKit driverKit;
    private Bindings bindings;

    private MecanumDrivebase drivebase;
    private StickDriveSource stickDrive;

    private final TaskRunner macroRunner = new TaskRunner();

    // ...
}
```

### 3.2. In `init()`: wire inputs and bind buttons

```java
@Override
public void init() {
    gamepads = Gamepads.create(gamepad1, gamepad2);
    driverKit = DriverKit.of(gamepads);
    bindings = new Bindings();

    // Create drivebase (motors + config) ...
    // Create stickDrive from DriverKit ...

    // Y: start macro
    bindings.onPress(
            driverKit.p1().buttonY(),
            () -> startMacro()
    );

    // B: cancel macro
    bindings.onPress(
            driverKit.p1().buttonB(),
            () -> cancelMacro()
    );
}
```

### 3.3. In `loop()`: update everything

```java
@Override
public void loop() {
    clock.update(getRuntime());
    double dtSec = clock.dtSec();

    gamepads.update(dtSec);
    bindings.update(dtSec);

    // Let tasks run first
    macroRunner.update(clock);

    // If no macro is active, use manual drive
    if (!macroRunner.hasActiveTask()) {
        DriveSignal cmd = stickDrive.get(clock).clamped();
        drivebase.drive(cmd);
        drivebase.update(clock);
    }
}
```

That’s the entire pattern. All that changes between examples is **what tasks you enqueue**.

---

## 4. DriveTasks: timed drive segments

`DriveTasks` lives in `fw.drive` and gives you simple drive-related tasks.

### 4.1. Timed drive helpers

```java
Task segment = DriveTasks.driveForSeconds(
        drivebase,
        new DriveSignal(+0.7, 0.0, 0.0),  // forward
        0.8                               // seconds
);
```

Under the hood, this uses `RunForSecondsTask` to:

* On start – call `drivebase.drive(signal)` once.
* Each update – call `drivebase.update(clock)` and subtract `dtSec`.
* On finish – call `drivebase.stop()`.

### 4.2. Building a macro from segments

You can chain segments into one macro using `SequenceTask`:

```java
Task macro = SequenceTask.of(
        // Forward
        DriveTasks.driveForSeconds(
                drivebase,
                new DriveSignal(+0.7, 0.0, 0.0),
                0.8
        ),
        // Strafe right (lateral < 0)
        DriveTasks.driveForSeconds(
                drivebase,
                new DriveSignal(0.0, -0.7, 0.0),
                0.8
        ),
        // Rotate CCW (omega > 0)
        DriveTasks.driveForSeconds(
                drivebase,
                new DriveSignal(0.0, 0.0, +0.7),
                0.6
        )
);

macroRunner.clear();   // optional: cancel previous macro
macroRunner.enqueue(macro);
```

This is the pattern used in `fw.examples.TeleOpMacroDrive`.

---

## 5. PlantTasks: timed and setpoint behaviors

`PlantTasks` lives in `fw.actuation` and wraps common patterns for any `Plant`.

### 5.1. Timed hold

```java
// Run intake at full power for 0.7s, then turn it off.
Task intakePulse = PlantTasks.holdForSeconds(intakePlant, +1.0, 0.7);

macroRunner.enqueue(intakePulse);
```

This internally uses `RunForSecondsTask` to:

* On start – `plant.setTarget(target)`.
* Each update – `plant.update(dtSec)`.
* On finish – `plant.setTarget(0.0)`.

### 5.2. Go to setpoint and wait

```java
// Move arm to target angle; wait until atSetpoint() or 1.0s timeout.
Task moveArm = PlantTasks.goToSetpointAndWait(
        armPlant,
        targetAngle,
        1.0,
        () -> telemetry.addLine("Arm move timed out!")
);

macroRunner.enqueue(moveArm);
```

This is a good fit for autonomous steps: “move mechanism, but don’t wait forever if something goes wrong.”

---

## 6. Where to look in the codebase

If you want to see real, complete examples:

* `fw.examples.TeleOpMecanumBasic`

  * Minimal TeleOp using the drive framework.
* `fw.examples.TeleOpMacroDrive`

  * TeleOp with a gamepad-triggered macro using `TaskRunner` and `DriveTasks`.
* `fw.examples.ShooterInterpolationExample`

  * Shows how to use `InterpolatingTable1D` for shooter tuning.

These examples are designed to be read alongside the Beginner’s Guide and Framework Overview.

---

## 7. Summary

* Tasks let you express multi-step, time-extended behaviors **without blocking** the FTC loop.
* `TaskRunner` is the object that owns and executes those tasks.
* `RunForSecondsTask` is the core “timed action” primitive.
* `DriveTasks` and `PlantTasks` provide beginner-friendly helpers so your robot code reads like:

  ```java
  Task macro = SequenceTask.of(
      DriveTasks.driveForSeconds(drivebase, new DriveSignal(+0.7, 0.0, 0.0), 0.8),
      PlantTasks.holdForSeconds(intakePlant, +1.0, 0.7)
  );

  macroRunner.enqueue(macro);
  ```

Use this guide as a quick reference while you read the main docs and explore the examples.

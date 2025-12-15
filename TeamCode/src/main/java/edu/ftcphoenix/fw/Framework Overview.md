# Framework Overview

Phoenix is a small FTC framework that helps you structure robot code around a clean, repeatable loop.

The big idea is: **advance a single `LoopClock` once per OpMode cycle**, then run everything else (inputs, bindings, tasks, drive, mechanisms) off that clock.

---

## The layers (top → bottom)

Think of Phoenix as a few thin layers you stack:

1. **OpMode / Robot code (you)**

    * Owns the loop and decides what updates when.
2. **Input** (`fw.input`)

    * `Gamepads`, `GamepadDevice`, `Axis`, `Button`.
3. **Bindings** (`fw.input.binding`)

    * `Bindings` turns button edges into actions (often: enqueue a macro).
4. **Tasks / Macros** (`fw.task`, plus helpers in other packages)

    * `Task`, `TaskRunner`, `Tasks`, `PlantTasks`, `DriveTasks`.
5. **Drive behavior** (`fw.drive` + `fw.drive.source`)

    * `DriveSource` produces a `DriveSignal` (stick drive, TagAim, etc.).
6. **Actuation**

    * Drivebase: `MecanumDrivebase`.
    * Mechanisms: `Plant`.
7. **HAL** (`fw.hal`)

    * Tiny device-neutral interfaces: `PowerOutput`, `PositionOutput`, `VelocityOutput`.
8. **FTC adapters** (`fw.adapters.ftc`)

    * `FtcHardware` wraps FTC SDK hardware into Phoenix HAL outputs.

---

## The loop clock (and why it matters)

`LoopClock` (in `fw.util`) tracks:

* `nowSec()` — current time
* `dtSec()` — delta time since last loop
* `cycle()` — a monotonically increasing **per-loop id**

Several Phoenix systems are **idempotent by `clock.cycle()`** (safe if accidentally called twice in the same loop), including:

* `Gamepads.update(clock)`
* `Bindings.update(clock)`
* `TaskRunner.update(clock)`
* `Button.updateAllRegistered(clock)`

This prevents bugs like “button press fired twice” or “tasks advanced twice” when helper code gets layered.

---

## Hardware and mechanisms: `Actuators`, `Plant`, and the HAL

### HAL outputs (lowest level)

Phoenix abstracts FTC hardware into small output interfaces:

* `PowerOutput` — normalized power (typically `[-1, +1]`)
* `PositionOutput` — native position units (servo `0..1`, motor encoder ticks, etc.)
* `VelocityOutput` — native velocity units (e.g., ticks/sec)

### FTC adapter: `FtcHardware`

`FtcHardware` provides factories like:

* `FtcHardware.motorPower(hw, name, inverted)`
* `FtcHardware.motorVelocity(hw, name, inverted)`
* `FtcHardware.motorPosition(hw, name, inverted)`
* `FtcHardware.servoPosition(hw, name, inverted)`
* `FtcHardware.crServoPower(hw, name, inverted)`

These return HAL outputs.

### Beginner entrypoint: `Actuators`

Most teams should **not** call `FtcHardware` directly. Use the staged builder in `Actuators`:

```java
import edu.ftcphoenix.fw.actuation.Actuators;
import edu.ftcphoenix.fw.actuation.Plant;

// Shooter: dual-motor velocity plant (native units) with a rate limit.
Plant shooter = Actuators.plant(hardwareMap)
        .motorPair("shooterLeftMotor",  false,
                   "shooterRightMotor", true)
        .velocity()            // default tolerance (native units)
        .rateLimit(500.0)      // max delta in native units per second
        .build();

// Transfer: CR servo power plant.
Plant transfer = Actuators.plant(hardwareMap)
        .crServo("transferServo", false)
        .power()
        .build();

// Pusher: positional servo plant (0..1).
Plant pusher = Actuators.plant(hardwareMap)
        .servo("pusherServo", false)
        .position()
        .build();
```

**Important:** tasks can set targets on plants, but *your loop* must still call `plant.update(dtSec)` each cycle.

---

## Drive: `DriveSignal`, `DriveSource`, and `MecanumDrivebase`

### `DriveSignal` (robot-centric command)

A `DriveSignal` is **robot-centric** and follows Phoenix pose conventions:

* `axial > 0` → forward
* `lateral > 0` → left
* `omega > 0` → counter-clockwise (CCW)

### `DriveSource` (where commands come from)

A `DriveSource` produces a `DriveSignal` each loop:

* Manual TeleOp: `GamepadDriveSource`
* Assisted aiming: `TagAimDriveSource` / `TagAim.teleOpAim(...)`
* Autonomous logic: anything implementing `DriveSource`

`DriveSource` also supports composition helpers (like scaling and blending) via default methods.

### `MecanumDrivebase` + `Drives`

`Drives.mecanum(hardwareMap)` is the beginner-friendly way to wire a mecanum drivetrain.

```java
import edu.ftcphoenix.fw.drive.*;
import edu.ftcphoenix.fw.drive.source.GamepadDriveSource;
import edu.ftcphoenix.fw.input.Gamepads;

Gamepads pads = Gamepads.create(gamepad1, gamepad2);

MecanumDrivebase drivebase = Drives.mecanum(hardwareMap);
DriveSource drive = GamepadDriveSource.teleOpMecanumStandard(pads);
```

**Rate limiting note:** `MecanumDrivebase` can rate-limit components using the most recent `dtSec`. Call `drivebase.update(clock)` once per loop. If you want rate limiting to use the *current* loop’s `dt`, call `update(clock)` **before** `drive(...)`.

---

## Tasks and macros

### `Task` and `TaskRunner`

A `Task` is non-blocking work that progresses over multiple loop cycles.

A `TaskRunner` runs tasks **sequentially** (FIFO): start one task, update it each cycle until it completes, then move to the next.

### Factories: `Tasks`, `PlantTasks`, `DriveTasks`

Phoenix gives you factories so your code reads like intent:

* `Tasks` — general composition (`sequence`, `parallelAll`, `waitForSeconds`, `waitUntil`, `runOnce`, …)
* `PlantTasks` — patterns that command a `Plant` (`setInstant`, `holdFor`, `moveTo`, …)
* `DriveTasks` — patterns that command a `MecanumDrivebase` (`driveForSeconds`, `goToPoseFieldRelative`, …)

Example macro (shoot one disc):

```java
import edu.ftcphoenix.fw.actuation.Plant;
import edu.ftcphoenix.fw.actuation.PlantTasks;
import edu.ftcphoenix.fw.task.Task;
import edu.ftcphoenix.fw.task.Tasks;

private Task buildShootOneDiscMacro(Plant shooter, Plant transfer) {
    return Tasks.sequence(
            PlantTasks.setInstant(shooter, 3200.0),
            Tasks.waitUntil(shooter::atSetpoint, 1.0),
            PlantTasks.holdForThen(transfer, 1.0, 0.20, 0.0)
    );
}
```

---

## Inputs and bindings

### `Gamepads`

`Gamepads` wraps FTC `gamepad1` / `gamepad2` and exposes calibrated axes and edge-tracked buttons.

Call **once per loop**:

```java
gamepads.update(clock);
```

### `Bindings`

`Bindings` lets you map button edges to actions.

Most commonly: **enqueue a macro** on press.

```java
import edu.ftcphoenix.fw.input.binding.Bindings;
import edu.ftcphoenix.fw.task.TaskRunner;

Bindings bindings = new Bindings();
TaskRunner macros = new TaskRunner();

bindings.onPress(gamepads.p1().y(), () ->
        macros.enqueue(buildShootOneDiscMacro(shooter, transfer))
);
```

Call **once per loop** (after `gamepads.update(clock)`):

```java
bindings.update(clock);
```

---

## A standard OpMode loop shape

This is the “everything has a place” pattern Phoenix is built around:

```java
@Override
public void start() {
    clock.reset(getRuntime());
}

@Override
public void loop() {
    // 1) Clock
    clock.update(getRuntime());

    // 2) Inputs
    gamepads.update(clock);

    // 3) Bindings (may enqueue macros)
    bindings.update(clock);

    // 4) Tasks / macros
    macroRunner.update(clock);

    // 5) Drive
    DriveSignal cmd = driveSource.get(clock).clamped();
    drivebase.update(clock);   // call before drive(...) if you want current-dt rate limiting
    drivebase.drive(cmd);

    // 6) Mechanisms
    double dtSec = clock.dtSec();
    shooter.update(dtSec);
    transfer.update(dtSec);

    // 7) Telemetry
    telemetry.update();
}
```

---

## Where to go next

* **Beginner’s Guide** — first setup + “how to write a Phoenix OpMode”.
* **Framework Principles** — the rules-of-thumb Phoenix expects you to follow.
* **Loop Structure** — deeper reasoning about update order and idempotency.
* **Tasks & Macros Quickstart** — how to build task graphs quickly.
* **Shooter Case Study & Examples Walkthrough** — maps concepts to real examples in `fw.examples`.

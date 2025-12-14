# Loop Structure

Phoenix is designed around a **single, non-blocking control loop**. Each loop does a small amount of work and then returns control back to the FTC runtime.

The purpose of this document is to define a consistent, repeatable ordering so:

* Tasks behave predictably.
* Drive behavior is stable.
* Sensor timestamps and debug telemetry make sense.
* Students don’t accidentally introduce hidden sign flips or blocking code.

---

## Core rule: do a little work every loop

Avoid anything that blocks the loop:

* No `sleep(...)`.
* No long `while (...) { ... }` loops inside `loop()`.

Instead:

* Use `TaskRunner` + `Tasks`/`PlantTasks`/`DriveTasks` to express “do X, then Y, with timeouts”.

---

## Recommended loop pipeline

The framework works best when your loop follows this order:

1. **Clock / time step**
2. **Raw inputs** (gamepads, sensor reads, vision)
3. **Decisions**

    * 3a. User-driven decisions (bindings) that may enqueue tasks
    * 3b. Task processing (task runners)
    * 3c. Drive computation + application
    * 3d. Non-drive mechanism application
4. **Telemetry / debug**

This order is intentionally opinionated.

### Why this order?

* **Clock first** so everything else uses the same `dtSec` and timestamps.
* **Inputs before decisions** so controllers and tasks see the latest sensors.
* **Bindings before task update** so newly-triggered tasks start immediately.
* **Drivebase after decisions** so the final chosen `DriveSignal` (manual or autonomous) is what gets applied.
* **Telemetry last** so it reflects what actually happened in this loop.

---

## TeleOp: canonical loop template

Below is a template that matches Phoenix’s intended flow. You can adapt naming to your OpMode base class.

```java
@Override
public void loop() {
    // 1) Clock
    clock.update();
    // If you use dtSec, take it from the clock.
    // double dtSec = clock.dtSec();   // (use your clock API)

    // 2) Raw inputs
    // - Update your gamepad wrapper (if you have one)
    // - Update sensors/vision targets that cache state
    gamepads.update();
    tagTarget.update();        // if you use TagTarget
    poseEstimator.update();    // if you have one

    // 3a) User-driven decisions (bindings)
    // - Read button edges
    // - Enqueue tasks/macros
    bindings.update(clock);

    // 3b) Tasks
    // - Update task runners once per loop
    runner.update(clock);

    // 3c) Drive
    // - Choose a DriveSource for this moment (manual, aim-assist, etc.)
    DriveSignal cmd = driveSource.get(clock);
    drivebase.drive(cmd);
    drivebase.update(clock);

    // 3d) Mechanisms
    // - If your plants are not updated inside TaskRunner, update them here.
    // - Many teams do mechanism updates inside subsystem update methods.
    shooter.update(clock.dtSec());
    transfer.update(clock.dtSec());
    pusher.update(clock.dtSec());

    // 4) Telemetry / debug
    debugDump(debug, "teleop");
    telemetry.update();
}
```

### Where do stick sign conversions belong?

At the **input boundary** only (e.g., `GamepadDriveSource`).

Everything downstream (controllers, tasks, drive mixing) should use the framework conventions:

* `axial > 0` = forward
* `lateral > 0` = left
* `omega > 0` = CCW (turn left)

---

## Auto: canonical loop template

Autonomous is the same pipeline. The difference is that tasks/macros are usually the *source* of drive and mechanism commands.

```java
@Override
public void loop() {
    // 1) Clock
    clock.update();

    // 2) Raw inputs
    // Keep sensors and pose estimation current.
    tagTarget.update();
    poseEstimator.update();

    // 3) Decisions
    // 3a) (Optional) Auto state machine or trigger conditions
    // Example: enqueue a macro when a condition becomes true.

    // 3b) Tasks (primary driver of actions)
    runner.update(clock);

    // 3c) Drive
    // Some autos drive via a DriveTask (which internally calls drivebase).
    // Others compute a DriveSignal from a controller each loop.
    DriveSignal cmd = autoDriveSource.get(clock);
    drivebase.drive(cmd);
    drivebase.update(clock);

    // 3d) Mechanisms
    shooter.update(clock.dtSec());
    transfer.update(clock.dtSec());
    pusher.update(clock.dtSec());

    // 4) Telemetry
    debugDump(debug, "auto");
    telemetry.update();
}
```

---

## Single-source-of-truth: who owns the motors this loop?

A common bug is having *two different systems* commanding the same hardware in the same loop.

Recommended pattern:

* **Drivebase motors** are commanded only by `drivebase.drive(cmd)`.
* **Mechanism motors/servos** are commanded only through Plants (often via `PlantTasks`).

If you need manual override:

* Switch the active `DriveSource` (manual vs aim-assist).
* Or enqueue/cancel tasks intentionally (don’t “fight” the task runner by also writing outputs directly).

---

## Common pitfalls and how to avoid them

### 1) Calling `update()` twice

Examples that cause subtle bugs:

* Updating a sensor target twice per loop.
* Updating `TaskRunner` twice.
* Updating the drivebase in two places.

Rule: **each subsystem-like object is updated exactly once per loop**.

### 2) Mixing frames or hidden sign flips

Rule: **controllers and tasks use framework conventions**.

If a human-facing input “feels wrong”, fix it at the boundary (gamepad → drive signal), not inside controllers.

### 3) Telemetry that doesn’t match behavior

Telemetry should happen after decisions and outputs. If you print state too early, you’ll be looking at last loop’s decisions.

---

## Debug dump recommendation

If you use `DebugSink`, a useful pattern is:

* Call `debugDump(...)` on key objects at the end of the loop.
* Keep keys consistent (prefix with subsystem name).

Example (conceptual):

```java
public void debugDump(DebugSink dbg) {
    drivebase.debugDump(dbg, "drive");
    runner.debugDump(dbg, "tasks");
    tagTarget.debugDump(dbg, "vision.tags");
}
```

---

## Summary

A clean Phoenix loop looks like:

1. **clock** → 2) **inputs** → 3a) **bindings** → 3b) **tasks** → 3c) **drive** → 3d) **mechanisms** → 4) **telemetry**

If you stick to this, the robot stays responsive, tasks behave consistently, and sign/frame bugs become way easier to spot.

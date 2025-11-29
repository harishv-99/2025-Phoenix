# Shooter Case Study & Examples Walkthrough

This document explains the **Shooter TeleOp examples (01–06)** as a single case study.

The goal is to show how you can start from:

> “Just drive the robot with mecanum”

and grow it step‑by‑step into:

> “Drive the robot, auto‑aim at an AprilTag, compute shooter speed from distance, and run
> non‑blocking shooting macros.”

Each example adds one new idea while keeping the previous ones intact.

---

## 1. Example ladder – what each step adds

At a high level, the examples progress like this:

1. **Example 01 – Mecanum Drive**

    * Basic mecanum driving from gamepad sticks.
    * Introduces `MecanumDrivebase`, `DriveSignal`, and `GamepadDriveSource`.

2. **Example 02 – Shooter Mechanisms**

    * Adds shooter, transfer, and pusher mechanisms as **plants**.
    * Buttons directly control plants (no tasks yet).

3. **Example 03 – Shooter Macro**

    * Introduces `Task` and `TaskRunner` for a non‑blocking shooter macro.
    * Example macro: “spin up shooter, feed discs, then stop.”

4. **Example 04 – Interpolated Shooter Speed**

    * Adds an `InterpolatingTable1D` for distance → shooter velocity.
    * Uses a simple distance source (e.g., a manual slider/axis) to demonstrate the idea.

5. **Example 05 – TagAim + Vision Distance**

    * Uses vision + AprilTags to aim the robot and measure distance.
    * Introduces `TagAim`, `TagAimDriveSource`, and `AprilTagSensor`.

6. **Example 06 – Putting It All Together**

    * Combines:

        * mecanum drive,
        * shooter plants,
        * distance‑based speed,
        * TagAim auto‑aim,
        * and shooting tasks/macros.

You can run any of these examples on your robot (after wiring hardware) and see each concept in
isolation.

---

## 2. Common pieces used in all examples

Across the examples you’ll see the same general structure:

* A **thin TeleOp OpMode** that:

    * Creates `FtcHardware`, `LoopClock`, `Gamepads`, and a debug sink.
    * Creates the example robot logic class (for that example).
    * In `loop()`: updates time, updates inputs, calls into the robot logic, and flushes debug.

* A **robot logic class** (per example) that:

    * Holds the drivebase, plants, and tasks for that example.
    * Wires everything together once in its constructor or an `init()` method.
    * Provides an `updateTeleOp(dt)` method called from the OpMode.

By the time you reach Example 06, this robot logic class looks very close to what a season robot
would look like.

---

## 3. Example 01 – Mecanum Drive

**Goal:** Drive a mecanum robot with the primary gamepad.

### 3.1 Key classes and concepts

* `MecanumConfig` – describes your wheelbase layout.
* `MecanumDrivebase` – converts a `DriveSignal` into motor outputs.
* `Drives` – has helpers to build drivebases.
* `DriveSignal` – holds linear (forward/strafe) and rotational commands.
* `Gamepads` / `GamepadDevice` – read P1 sticks.
* `GamepadDriveSource` – turns sticks + buttons into a `DriveSignal`.

### 3.2 Dataflow

On each `loop()`:

1. `Gamepads.update(gamepad1, gamepad2)` reads raw controller inputs.
2. `GamepadDriveSource` reads axes from P1:

    * left stick: drive/strafe
    * right stick X: rotation
    * optional: bumper or trigger for “slow mode”.
3. `GamepadDriveSource` returns a `DriveSignal`.
4. `MecanumDrivebase` maps the `DriveSignal` to the four wheel motors.

### 3.3 What to look for in the code

* Where `MecanumConfig` is created with your motor directions and geometry.
* The creation of a `GamepadDriveSource` with:

    * which axes control translation,
    * which axis controls rotation,
    * which button, if any, activates slow mode.
* A simple `updateTeleOp(dt)` method that:

    * gets a `DriveSignal` from the drive source,
    * sends it to the drivebase.

This example is entirely about **drive** – there are no shooter mechanisms yet.

---

## 4. Example 02 – Shooter Mechanisms as Plants

**Goal:** Add a basic shooter, transfer, and pusher to Example 01, controlled by buttons.

### 4.1 New concepts

* **Plants** (`fw.actuation.Plant`): abstraction for things that move.
* `Actuators` / `Plants` helpers to create:

    * a **velocity plant** for the shooter flywheel,
    * **power plants** for transfer / intake motors,
    * a **position plant** for a pusher servo or linear actuator.

### 4.2 Dataflow

On each loop, in addition to driving:

1. Buttons on the gamepad set **desired targets** of each plant, for example:

    * Shooter button:

        * pressed → set shooter target velocity to a pre‑chosen RPM.
        * released → set target to zero (stop).
    * Transfer button:

        * pressed → set transfer power to +1.
        * released → set to 0.
    * Pusher button:

        * toggles between retracted/extended positions.
2. Each plant’s `update(dt)` method runs, taking the desired target and sending appropriate commands
   to hardware.

### 4.3 What to look for in the code

* How motors/servos from `FtcHardware` are wrapped into plants.
* Where gamepad buttons (via `Gamepads` or `Bindings`) are used to change plant targets.
* The order of operations:

    * handle input → set plant targets → update plants.

At this stage there are **no tasks**. You can hold buttons to run the shooter and transfer, then
manually control the pusher.

---

## 5. Example 03 – Shooter Macro with Tasks

**Goal:** Turn a multi‑step shooting sequence into a **non‑blocking macro**.

### 5.1 New concepts

* `Task` – behavior that runs over time.
* `TaskRunner` – manages the currently running task.
* `SequenceTask`, `RunForSecondsTask`, `InstantTask`, `WaitUntilTask`.
* `PlantTasks` – helpers that create tasks that drive plants.

### 5.2 Example macro: “shoot one disc”

A simple pattern for a single‑disc macro might look like:

1. Spin up shooter to target velocity and **wait until at setpoint**.
2. Run transfer motor for a short duration to feed a disc.
3. Stop transfer.
4. Optionally wait a little and stop shooter.

In code, this is expressed as a `Task`, often built in a helper method on your robot class, e.g.:

```java
public Task shootOneDisc() {
    return new SequenceTask(
            PlantTasks.goToSetpointAndWait(shooterPlant, targetVelocity),
            PlantTasks.holdForSeconds(transferPlant, +1.0, 0.3),
            PlantTasks.holdForSeconds(transferPlant, 0.0, 0.0)
    );
}
```

### 5.3 Dataflow

* A **button press** starts the macro:

    * e.g. `taskRunner.start(robot.shootOneDisc());`
* Each loop, `taskRunner.update(dt)` advances the task.
* The task uses `PlantTasks` to set targets on plants.
* Meanwhile, the driver **still has control** of driving, because tasks are non‑blocking.

### 5.4 What to look for in the code

* The `TaskRunner` instance owned by the robot or TeleOp.
* At least one robot helper method that builds a `Task` using `PlantTasks`.
* A binding that starts the macro on button press.
* A `loop()` method that always calls `taskRunner.update(dt)`.

If you understand this example, you understand the core of how **TeleOp macros** and **Autonomous
routines** work in Phoenix.

---

## 6. Example 04 – Interpolated Shooter Speed

**Goal:** Use a distance input to choose shooter velocity from a curve instead of a constant.

### 6.1 New concept: `InterpolatingTable1D`

* A simple table of (x, y) points, with interpolation between them.
* In this example:

    * x ≈ distance to target,
    * y ≈ desired shooter velocity.

### 6.2 Distance source

To keep the example focused, the distance can come from something simple, like:

* a “virtual distance” controlled by a joystick, or
* a fixed test value, or
* a placeholder method you later replace with real vision distance.

### 6.3 Dataflow

1. Each loop, read a **distance** value.
2. Use `InterpolatingTable1D` to get a shooter velocity:

    * `double shooterVelocity = table.interpolate(distance);`
3. Use that velocity as the target in your shooter plant, either:

    * directly in TeleOp (hold a “shoot” button), or
    * inside a macro task from Example 03.

### 6.4 What to look for in the code

* How the table is populated with a small set of tuning points.
* Where the distance value is read or computed.
* How the interpolated result is used as `targetVelocity` for the shooter plant.

This example shows how to turn calibration data into code **without hard‑coding one magic RPM**.

---

## 7. Example 05 – TagAim + Vision Distance

**Goal:** Use vision and AprilTags to both **auto‑aim** the robot and **measure distance**.

### 7.1 New concepts

* `FtcVision` – sets up the vision pipeline.
* `AprilTagSensor` / `AprilTagObservation` – access tag detections.
* `Tags` – utilities around tag IDs and geometry.
* `TagAim` + `TagAimController` – compute angular correction to face a tag.
* `TagAimDriveSource` – wraps a base drive source and injects TagAim rotation when enabled.

### 7.2 Auto‑aim behavior

The drive behavior usually looks like this:

* When the **aim button is NOT pressed**:

    * use the base `GamepadDriveSource` unmodified.
* When the **aim button IS pressed**:

    * use `TagAimDriveSource` that:

        * keeps translational commands from the driver (forward/strafe),
        * overrides rotational command (`omega`) to turn toward the target tag.

### 7.3 Distance from vision

Using the pose estimate from AprilTags, you can compute:

* the **distance** from the robot to the tag, and
* possibly other useful information (heading, lateral offset).

The example extracts a distance value each loop and stores it somewhere the shooter logic can read.

### 7.4 What to look for in the code

* Where `FtcVision` is created and connected to the camera.
* A method or helper that finds the relevant tag (e.g., scoring tag) from observations.
* A `TagAim` instance configured for that tag.
* How `TagAimDriveSource` is constructed from the base `GamepadDriveSource` plus TagAim.
* A button mapping that chooses between base drive and TagAim drive.
* A distance value computed from the tag pose.

At this point, you can **hold a button** to auto‑aim at the tag while driving normally in
translation.

---

## 8. Example 06 – Everything Together

**Goal:** Combine all previous pieces into a more realistic TeleOp:

* Mecanum drive with slow mode.
* Shooter, transfer, pusher plants.
* Distance‑based shooter speed via `InterpolatingTable1D`.
* TagAim auto‑aim when holding an aim button.
* A shooting macro that uses the distance‑based speed.

### 8.1 Typical controls

While your exact bindings might differ, a common pattern is:

* **Drive:** left stick (translation), right stick X (rotation).
* **Slow mode:** right bumper.
* **Aim at tag:** hold left bumper.
* **Run shooting macro:** press Y.
* **Manual override buttons:** some buttons to run intake/transfer independently.

### 8.2 Dataflow, step‑by‑step

Each loop, roughly:

1. `LoopClock` updates `dt`.
2. `Gamepads.update(...)` reads controllers.
3. `Bindings.update()` interprets button presses:

    * maybe starts a macro: `taskRunner.start(shootMacro());`
    * toggles manual modes.
4. Robot logic:

    * picks the active drive source (base vs TagAim) based on the aim button.
    * computes a `DriveSignal` and sends it to the drivebase.
    * reads distance from vision.
    * uses the distance table to compute shooter target velocity.
    * sets plant targets for shooter, transfer, pusher.
5. `taskRunner.update(dt)` advances any active Task.
6. Debug/telemetry is updated.

### 8.3 What to look for in the code

* A single robot logic class that owns:

    * a drivebase and one or more drive sources,
    * plants for shooter/transfer/pusher,
    * TagAim and vision objects,
    * a distance table,
    * a `TaskRunner` and macro factory methods.
* Clean separation between:

    * **input** (Gamepads + Bindings),
    * **decision** (robot logic, tasks),
    * **actuation** (plants, drivebase).

This example is a good model for how you might structure your competition TeleOp.

---

## 9. Adapting the case study to your robot

To adapt these examples to your own robot:

1. **Hardware mapping**

    * Update `FtcHardware` usage to match your motor/servo names.
    * Check wheel directions and distances in `MecanumConfig`.

2. **Shooter & mechanism tuning**

    * Start with safe, low velocities/powers.
    * Measure real distances and preferred shooter speeds.
    * Populate `InterpolatingTable1D` with those points.

3. **Tag and field configuration**

    * Confirm tag IDs and positions for your game.
    * Adjust TagAim configuration accordingly.

4. **Controls & bindings**

    * Choose button mappings your drivers like.
    * Keep the macro start buttons clear and hard to mis‑press.

5. **Iterate in small steps**

    * Get Example 01 working first (drive only).
    * Then add Example 02 pieces (mechanisms).
    * Then macros, distance, TagAim, and finally combine everything.

If you keep the example structure while swapping out hardware details, you’ll have a clean,
extensible TeleOp and a solid foundation for autonomous routines.

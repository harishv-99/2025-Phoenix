# Phoenix FTC Beginner’s Guide

This guide is for **new students** writing robot code with the Phoenix framework.

We’ll walk through:

1. How Phoenix fits with the FTC SDK.
2. The overall project structure.
3. The idea of a **PhoenixRobot** class.
4. How to write **thin TeleOp and Auto OpModes**.
5. How to wire **mecanum drive** and **basic mechanisms**.
6. Where to go next once this is working.

You do **not** need to understand every part of the framework to get started. Focus on:

* Your **robot class** (we’ll call it `PhoenixRobot` here).
* A few helpers: `Gamepads`, `Bindings`, `Drives.mecanum(...)`, and **Actuators** for mechanisms.

---

## 1. Where Phoenix fits

Phoenix is a small library that sits **on top of the FTC SDK**:

* You still create normal `@TeleOp` and `@Autonomous` OpModes.
* You still use `hardwareMap`, `telemetry`, and `gamepad1` / `gamepad2`.
* Phoenix gives you **helper classes** so your code is:

    * less repetitive,
    * easier to read,
    * and easier to extend later.

The high‑level pattern is:

* The FTC SDK calls your OpMode.
* Your OpMode calls into **your robot class**.
* Your robot class uses Phoenix building blocks.

---

## 2. Project structure

A typical Phoenix‑based project has:

* `edu.ftcphoenix.fw.*` – the **framework** (don’t modify this).
* `edu.ftcphoenix.robots.*` – your **team’s robot code**:

    * a season‑specific robot class (we’ll call it `PhoenixRobot` here),
    * TeleOp and Auto OpModes that use that class.

You own everything under `edu.ftcphoenix.robots.*`. That’s where you’ll spend most of your time.

---

## 3. The PhoenixRobot idea

Instead of putting all your logic inside the OpMode, we recommend creating a **single robot class**
that owns:

* The **drivebase** (mecanum in this guide).
* **Mechanisms** as plants (shooter, intake, arm, etc.).
* Input helpers: `Gamepads` and `Bindings`.
* Optional **vision** and TagAim for advanced use.

Your OpModes become very thin:

* They create `PhoenixRobot` once in `init()`.
* Each `loop()`, they:

    * update time,
    * update inputs,
    * call `robot.updateTeleOp(clock)` or `robot.updateAuto(clock)`.

### 3.1 PhoenixRobot skeleton

Here is a simplified version of what a season robot class might look like:

```java
public final class PhoenixRobot {
    private final HardwareMap hw;
    private final Gamepads pads;

    // Drive
    private final MecanumDrivebase drivebase;
    private final DriveSource driveSource;

    // Mechanisms (plants)
    private final Plant shooterPlant;
    private final Plant intakePlant;
    private final Plant pusherPlant;

    // Input & tasks
    private final Bindings bindings = new Bindings();
    private final TaskRunner taskRunner = new TaskRunner();

    public PhoenixRobot(HardwareMap hw, Gamepads pads) {
        this.hw = hw;
        this.pads = pads;

        // 1) Build drive.
        this.drivebase = buildDrivebase(hw);
        this.driveSource = buildDriveSource(pads);

        // 2) Wire mechanisms as plants.
        this.shooterPlant = buildShooterPlant(hw);
        this.intakePlant = buildIntakePlant(hw);
        this.pusherPlant = buildPusherPlant(hw);

        // 3) Configure gamepad bindings.
        configureBindings();
    }

    public void updateTeleOp(LoopClock clock) {
        double dt = clock.dtSec();

        // Drive.
        DriveSignal signal = driveSource.getDriveSignal();
        drivebase.apply(signal);

        // Update mechanism plants.
        shooterPlant.update(dt);
        intakePlant.update(dt);
        pusherPlant.update(dt);

        // Update any running Task (macros / auto sequences).
        taskRunner.update(clock);
    }

    public void updateAuto(LoopClock clock) {
        double dt = clock.dtSec();

        // For many robots, this looks similar to updateTeleOp.
        // Auto-specific logic (like default plant targets) can live here.

        DriveSignal signal = driveSource.getDriveSignal();
        drivebase.apply(signal);

        shooterPlant.update(dt);
        intakePlant.update(dt);
        pusherPlant.update(dt);

        taskRunner.update(clock);
    }

    public Bindings bindings() {
        return bindings;
    }

    public TaskRunner taskRunner() {
        return taskRunner;
    }

    // The methods below are explained in later sections.
    private MecanumDrivebase buildDrivebase(HardwareMap hw) { /* ... */
        return null;
    }

    private DriveSource buildDriveSource(Gamepads pads) { /* ... */
        return null;
    }

    private Plant buildShooterPlant(HardwareMap hw) { /* ... */
        return null;
    }

    private Plant buildIntakePlant(HardwareMap hw) { /* ... */
        return null;
    }

    private Plant buildPusherPlant(HardwareMap hw) { /* ... */
        return null;
    }

    private void configureBindings() { /* ... */ }
}
```

Don’t worry if you don’t understand every piece yet; we’ll fill in the blanks next.

---

## 4. Thin TeleOp OpMode

Here’s what a **minimal TeleOp** looks like with Phoenix and `LoopClock`:

```java

@TeleOp(name = "Phoenix TeleOp")
public class PhoenixTeleOp extends OpMode {
    private final LoopClock clock = new LoopClock();

    private PhoenixRobot robot;
    private Gamepads pads;
    private Bindings bindings;

    @Override
    public void init() {
        // Wrap FTC gamepads.
        pads = Gamepads.create(gamepad1, gamepad2);

        // Create the season robot and share its bindings.
        robot = new PhoenixRobot(hardwareMap, pads);
        bindings = robot.bindings();

        // Initialize loop timing.
        clock.reset(getRuntime());
    }

    @Override
    public void loop() {
        // Update timing from FTC's OpMode clock.
        clock.update(getRuntime());

        // 1. Update gamepads (axes + button edge state).
        pads.update(clock.dtSec());

        // 2. Process button bindings (start tasks, set plant targets, etc.).
        bindings.update(clock.dtSec());

        // 3. Robot logic (drive + mechanisms + tasks).
        robot.updateTeleOp(clock);

        telemetry.update();
    }
}
```

All the interesting behavior lives inside `PhoenixRobot`.

---

## 5. Thin Autonomous OpMode

Autonomous uses the same robot class, plus a `TaskRunner` to run a pre‑built sequence.

One common pattern is to build the auto `Task` inside `PhoenixRobot` and start it when Auto begins.

Example (iterative style):

```java

@Autonomous(name = "Phoenix Auto Example")
public class PhoenixAuto extends OpMode {
    private final LoopClock clock = new LoopClock();

    private PhoenixRobot robot;
    private Gamepads pads;
    private TaskRunner taskRunner;

    @Override
    public void init() {
        // Create gamepads for consistency, even if we don't use them much in auto.
        pads = Gamepads.create(gamepad1, gamepad2);

        robot = new PhoenixRobot(hardwareMap, pads);
        taskRunner = robot.taskRunner();

        // Ask the robot for an autonomous routine.
        Task autoTask = robot.buildSimpleAuto();
        taskRunner.enqueue(autoTask);

        // Initialize loop timing.
        clock.reset(getRuntime());
    }

    @Override
    public void loop() {
        // Update timing from FTC's OpMode clock.
        clock.update(getRuntime());

        // (Optional) If you want gamepad overrides during auto:
        // pads.update(clock.dtSec());
        // bindings.update(clock.dtSec());

        robot.updateAuto(clock);   // auto-specific per-loop logic
        taskRunner.update(clock);  // advance the autonomous Task(s)

        telemetry.update();
    }
}
```

The details of `buildSimpleAuto()` are up to you; it will use the same plants and tasks you use in
TeleOp.

---

## 6. Wiring the drivebase (mecanum)

Phoenix includes helpers for **mecanum drive** in `Drives` and `GamepadDriveSource`.

### 6.1 Beginner entrypoint: `Drives.mecanum(hardwareMap)`

The simplest way to get a mecanum drivebase is:

```java
private MecanumDrivebase buildDrivebase(HardwareMap hw) {
    // Beginner-friendly helper that assumes standard motor names and directions:
    //   "frontLeftMotor", "frontRightMotor", "backLeftMotor", "backRightMotor".
    // If your robot uses different names or needs different inversion,
    // see Drives.mecanum(...) overloads in the code.
    return Drives.mecanum(hw);
}
```

This uses a default `MecanumConfig` and a common inversion pattern. If your robot doesn’t drive
correctly, you can:

* Fix motor names in the Robot Configuration.
* Or later switch to a more advanced `Drives.mecanum(...)` overload and explicitly set inversion
  flags.

### 6.2 Drive source: `GamepadDriveSource.teleOpMecanumStandard`

Phoenix provides a standard mecanum stick mapping with slow mode:

```java
private DriveSource buildDriveSource(Gamepads pads) {
    // Standard stick mapping + shaping + slow mode on P1 right bumper.
    // For many robots, this is all you need.
    return GamepadDriveSource.teleOpMecanumStandard(pads);
}
```

Under the hood, this uses a `GamepadDriveSourceConfig.defaults()` and maps:

* P1 left stick: forward/back + strafe.
* P1 right stick X: rotation.
* P1 right bumper: slow mode.

If you later want to customize shaping or slow‑mode behavior, you can use the more configurable
factory method:

```java
private DriveSource buildDriveSource(Gamepads pads) {
    GamepadDriveSourceConfig cfg = GamepadDriveSourceConfig.defaults();

    // Optional: tweak cfg here (deadband, expo, etc.).

    return GamepadDriveSource.teleOpMecanum(
            pads,
            cfg,
            pads.p1().rightBumper(),  // slow‑mode button (can be null)
            0.4                        // slow‑mode scale
    );
}
```

Either way, in `updateTeleOp(clock)` you do:

```java
DriveSignal signal = driveSource.getDriveSignal();
drivebase.

apply(signal);
```

Once this is working, you have a solid driving TeleOp.

---

## 7. Wiring basic mechanisms as plants

Mechanisms (shooter, intake, arm, etc.) are modeled as **plants**.

Instead of setting motor power all over your code, you:

* Use `Actuators.plant(hardwareMap)` to choose hardware and control type.
* Get back a `Plant` that knows how to control that hardware.

### 7.1 Intake motor as a power plant

```java
private Plant buildIntakePlant(HardwareMap hw) {
    // Intake: single motor, open‑loop power control.
    return Actuators.plant(hw)
            .motor("intakeMotor", false)
            .power()
            .build();
}
```

A simple binding might be:

```java
private static final double INTAKE_POWER = 1.0;

private void configureBindings() {
    GamepadDevice p1 = pads.p1();

    // Hold A to run intake, release to stop.
    bindings.whileHeld(p1.a(), () -> intakePlant.setTarget(INTAKE_POWER));
    bindings.onRelease(p1.a(), () -> intakePlant.setTarget(0.0));
}
```

In `updateTeleOp(clock)` you always call:

```java
intakePlant.update(clock.dtSec());
```

### 7.2 Shooter wheel as a velocity plant

```java
private Plant buildShooterPlant(HardwareMap hw) {
    // Shooter: single velocity‑controlled motor.
    return Actuators.plant(hw)
            .motor("shooterMotor", false)
            .velocity(100.0)   // tolerance in native units
            .build();
}
```

And bindings:

```java
private static final double SHOOTER_TELEOP_VELOCITY = 3000.0; // example units

private void configureBindings() {
    GamepadDevice p1 = pads.p1();

    // Hold B to spin up shooter.
    bindings.whileHeld(p1.b(), () -> shooterPlant.setTarget(SHOOTER_TELEOP_VELOCITY));
    bindings.onRelease(p1.b(), () -> shooterPlant.setTarget(0.0));
}
```

Again, remember to call `shooterPlant.update(clock.dtSec());` each loop.

### 7.3 Pusher servo as a position plant

```java
private Plant buildPusherPlant(HardwareMap hw) {
    // Pusher: positional servo plant (0..1).
    return Actuators.plant(hw)
            .servo("pusherServo", false)
            .position()
            .build();
}
```

And a toggle binding:

```java
private static final double PUSHER_RETRACTED = 0.1;
private static final double PUSHER_EXTENDED = 0.7;

private boolean pusherExtended = false;

private void configureBindings() {
    GamepadDevice p1 = pads.p1();

    // ... other bindings ...

    // Toggle pusher position on X.
    bindings.onPress(p1.x(), () -> {
        pusherExtended = !pusherExtended;
        double target = pusherExtended ? PUSHER_EXTENDED : PUSHER_RETRACTED;
        pusherPlant.setTarget(target);
    });
}
```

And in `updateTeleOp(clock)`:

```java
pusherPlant.update(clock.dtSec());
```

---

## 8. Adding macros later (Tasks)

Once basic driving and plants are working, you can add **macros** using `Task` and `TaskRunner`:

* Example: a macro that spins up the shooter, feeds discs, then stops.
* This is covered in detail in **Tasks & Macros Quickstart**.

For now, the important idea is:

* **Plants** know how to move hardware.
* **Tasks** sequence plant targets over time.
* **Bindings** connect buttons to enqueueing tasks on the `TaskRunner`.

If you keep to this separation, your code stays easy to change.

---

## 9. Suggested order for new teams

If you’re new to Phoenix, try this order:

1. **Get mecanum drive working**

    * Implement `buildDrivebase` using `Drives.mecanum(hw)`.
    * Implement `buildDriveSource` using `GamepadDriveSource.teleOpMecanumStandard(pads)`.
    * Drive around, tune slow mode, fix motor directions if needed.

2. **Add one simple mechanism as a plant**

    * For example, an intake power plant.
    * Control it with one button (hold to run).

3. **Add more plants (shooter, arm, pusher)**

    * Keep each one simple.
    * Use clear binding rules (which button does what).

4. **Refine your PhoenixRobot class**

    * Keep OpModes thin.
    * Move robot‑specific constants into the robot class.

5. **Add simple macros using Tasks**

    * Start with a timed intake pulse.
    * Then move to a single‑disc shooting macro.

6. **Explore advanced features**

    * Interpolated shooter speeds (`InterpolatingTable1D`).
    * TagAim + vision for auto‑aiming at AprilTags.

---

## 10. Where to go next

Once you have a basic PhoenixRobot working:

* Read **Tasks & Macros Quickstart** to add non‑blocking behaviors.
* Read **Shooter Case Study & Examples Walkthrough** to see full TeleOp examples from basic drive to
  TagAim + vision.
* Skim **Framework Overview** whenever you’re curious about where something lives.

If you keep everything centered around a single robot class with:

* plants for mechanisms,
* a drivebase and drive source,
* clear bindings,

then the rest of the framework will slot into place naturally as you add more features.

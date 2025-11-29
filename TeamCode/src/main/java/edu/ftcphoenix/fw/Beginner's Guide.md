# Phoenix FTC Beginner’s Guide

This guide is for **new students** writing robot code with the Phoenix framework.

We’ll walk through:

1. How Phoenix fits with the FTC SDK.
2. The overall project structure.
3. The idea of a **PhoenixRobot** class.
4. How to write **thin TeleOp and Auto OpModes**.
5. How to wire **drive** and **basic mechanisms**.
6. Where to go next once this is working.

You do **not** need to understand every part of the framework to get started. Focus on:

* Your **robot class**.
* A few helpers: `Gamepads`, `Bindings`, `MecanumDrivebase`, and **plants** for mechanisms.

---

## 1. Where Phoenix fits

Phoenix is a small library that sits **on top of the FTC SDK**:

* You still create normal `@TeleOp` and `@Autonomous` OpModes.
* You still use `hardwareMap`, `telemetry`, and `gamepad1/gamepad2`.
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

* The **drivebase** (mecanum or tank).
* **Mechanisms** as plants (shooter, intake, arm, etc.).
* Input helpers: `Gamepads` and `Bindings`.
* Optional **vision** and TagAim.

Your OpModes become very thin:

* They create `PhoenixRobot` once in `init()`.
* Each `loop()`, they:

    * update time,
    * update inputs,
    * call `robot.updateTeleOp(dt)` or `robot.updateAuto(dt)`.

### 3.1 Example PhoenixRobot skeleton

```java
public final class PhoenixRobot {
    private final FtcHardware hw;
    private final DebugSink dbg;

    // Drive
    private final MecanumDrivebase drivebase;
    private final DriveSource driveSource;

    // Mechanisms (plants)
    private final Plant shooterPlant;
    private final Plant intakePlant;
    private final Plant pusherPlant;

    // Input & tasks
    private final Bindings bindings;
    private final TaskRunner taskRunner;

    public PhoenixRobot(FtcHardware hw, DebugSink dbg) {
        this.hw = hw;
        this.dbg = dbg;

        this.bindings = new Bindings();
        this.taskRunner = new TaskRunner();

        // 1) Build drive.
        this.drivebase = buildDrivebase(hw, dbg);
        this.driveSource = buildDriveSource();

        // 2) Wire mechanisms as plants.
        this.shooterPlant = buildShooterPlant();
        this.intakePlant = buildIntakePlant();
        this.pusherPlant = buildPusherPlant();

        // 3) Configure gamepad bindings.
        configureBindings();
    }

    public void updateTeleOp(double dt) {
        // Called from TeleOp loop.

        // Drive.
        DriveSignal signal = driveSource.getDriveSignal();
        drivebase.apply(signal);

        // Update mechanism plants.
        shooterPlant.update(dt);
        intakePlant.update(dt);
        pusherPlant.update(dt);

        // Update any running Task (macros / auto sequences).
        taskRunner.update(dt);
    }

    public Bindings bindings() {
        return bindings;
    }

    public TaskRunner taskRunner() {
        return taskRunner;
    }
}
```

Don’t worry if you don’t understand every line yet. The next sections break this down.

---

## 4. Thin TeleOp OpMode

Here’s what a **minimal TeleOp** looks like with Phoenix:

```java

@TeleOp(name = "Phoenix TeleOp")
public class PhoenixTeleOp extends OpMode {
    private final LoopClock clock = new LoopClock();

    private PhoenixRobot robot;
    private Bindings bindings;

    @Override
    public void init() {
        FtcHardware hw = new FtcHardware(hardwareMap);
        DebugSink dbg = new FtcTelemetryDebugSink(telemetry);

        robot = new PhoenixRobot(hw, dbg);
        bindings = robot.bindings();

        clock.reset();
    }

    @Override
    public void loop() {
        clock.update();
        double dt = clock.getDtSeconds();

        // 1. Read gamepads.
        Gamepads.update(gamepad1, gamepad2);

        // 2. Process button bindings.
        bindings.update();

        // 3. Robot logic (drive + mechanisms + tasks).
        robot.updateTeleOp(dt);

        telemetry.update();
    }
}
```

All the interesting behavior lives inside `PhoenixRobot`.

---

## 5. Thin Autonomous OpMode

Autonomous uses the same robot class, plus a `TaskRunner` to run a pre‑built sequence.

One common pattern is to build the auto `Task` inside `PhoenixRobot` and start it when Auto begins.

Example (OpMode skeleton, Linear or iterative style):

```java

@Autonomous(name = "Phoenix Auto Example")
public class PhoenixAuto extends OpMode {
    private final LoopClock clock = new LoopClock();

    private PhoenixRobot robot;
    private TaskRunner taskRunner;

    @Override
    public void init() {
        FtcHardware hw = new FtcHardware(hardwareMap);
        DebugSink dbg = new FtcTelemetryDebugSink(telemetry);

        robot = new PhoenixRobot(hw, dbg);
        taskRunner = robot.taskRunner();

        // Ask the robot for an autonomous routine.
        Task autoTask = robot.buildSimpleAuto();
        taskRunner.start(autoTask);

        clock.reset();
    }

    @Override
    public void loop() {
        clock.update();
        double dt = clock.getDtSeconds();

        robot.updateAuto(dt);      // similar to updateTeleOp but for auto
        taskRunner.update(dt);     // advance the autonomous Task

        telemetry.update();
    }
}
```

The details of `buildSimpleAuto()` and `updateAuto()` are up to you; they use the same plants and
tasks you use in TeleOp.

---

## 6. Wiring the drivebase (mecanum)

Phoenix includes helpers for **mecanum drive**. You typically:

1. Define a `MecanumConfig` for your robot.
2. Create a `MecanumDrivebase` from that config.
3. Create a `GamepadDriveSource` that turns sticks into a `DriveSignal`.

### 6.1 MecanumConfig

In your robot class:

```java
private MecanumDrivebase buildDrivebase(FtcHardware hw, DebugSink dbg) {
    MecanumConfig config = new MecanumConfig(
            hw.motor("frontLeft"),
            hw.motor("frontRight"),
            hw.motor("backLeft"),
            hw.motor("backRight"),
            /* trackWidthMeters = */ 0.35,
            /* wheelBaseMeters  = */ 0.35,
            /* wheelRadiusMeters = */ 0.05
    );

    return Drives.mecanum(config, dbg);
}
```

Change motor names and dimensions to match your robot.

### 6.2 GamepadDriveSource

```java
private DriveSource buildDriveSource() {
    GamepadDevice p1 = Gamepads.player1();

    return new GamepadDriveSource.Builder()
            .withTranslationAxes(p1.leftStickY().negated(), p1.leftStickX())
            .withRotationAxis(p1.rightStickX())
            .withSlowModeButton(p1.rightBumper())
            .withSlowModeScale(0.4)
            .build();
}
```

* Left stick: forward/back + strafe.
* Right stick X: rotation.
* Right bumper: slow mode.

In `updateTeleOp(dt)` you then:

```java
DriveSignal signal = driveSource.getDriveSignal();
drivebase.

apply(signal);
```

Once this is working, you have a solid driving TeleOp.

---

## 7. Wiring basic mechanisms as plants

Mechanisms (shooter, intake, arm, etc.) are modelled as **plants**.

Instead of setting motor power all over your code, you:

* Wrap hardware outputs using helpers from `Actuators`.
* Create plants that know how to control those outputs.

### 7.1 Example: intake motor as a power plant

```java
private Plant buildIntakePlant() {
    PowerOutput intakeMotor = hw.powerMotor("intake");
    return Actuators.powerPlant(intakeMotor);
}
```

Then you can set its target from buttons:

```java
private void configureBindings() {
    GamepadDevice p1 = Gamepads.player1();

    // Hold A to run intake, release to stop.
    bindings.whileHeld(p1.a(), () -> intakePlant.setTarget(+1.0));
    bindings.onRelease(p1.a(), () -> intakePlant.setTarget(0.0));
}
```

And in `updateTeleOp(dt)` you always call:

```java
intakePlant.update(dt);
```

### 7.2 Example: shooter wheel as a velocity plant

```java
private Plant buildShooterPlant() {
    VelocityOutput shooterMotor = hw.velocityMotor("shooter");
    return Actuators.velocityPlant(shooterMotor);
}
```

You might bind a button to set a fixed shooter velocity:

```java
private static final double SHOOTER_TELEOP_VELOCITY = 3000.0; // example units

private void configureBindings() {
    GamepadDevice p1 = Gamepads.player1();

    // Hold B to spin up shooter.
    bindings.whileHeld(p1.b(), () -> shooterPlant.setTarget(SHOOTER_TELEOP_VELOCITY));
    bindings.onRelease(p1.b(), () -> shooterPlant.setTarget(0.0));
}
```

### 7.3 Example: pusher servo as a position plant

```java
private Plant buildPusherPlant() {
    ServoOutput pusherServo = hw.servo("pusher");
    return Actuators.positionPlant(pusherServo);
}
```

Then map a button to toggle between two positions:

```java
private static final double PUSHER_RETRACTED = 0.1;
private static final double PUSHER_EXTENDED = 0.7;

private boolean pusherExtended = false;

private void configureBindings() {
    GamepadDevice p1 = Gamepads.player1();

    bindings.onPress(p1.x(), () -> {
        pusherExtended = !pusherExtended;
        double target = pusherExtended ? PUSHER_EXTENDED : PUSHER_RETRACTED;
        pusherPlant.setTarget(target);
    });
}
```

Again, just remember to call `pusherPlant.update(dt);` each loop.

---

## 8. Adding macros later (Tasks)

Once basic driving and plants are working, you can add **macros** using `Task` and `TaskRunner`:

* Example: a macro that spins up the shooter, feeds discs, then stops.
* This is covered in detail in **Tasks & Macros Quickstart**.

For now, the important idea is:

* **Plants** know how to move hardware.
* **Tasks** sequence plant targets over time.
* **Bindings** connect buttons to starting tasks.

If you keep to this separation, your code stays easy to change.

---

## 9. Suggested order for new teams

If you’re new to Phoenix, try this order:

1. **Get mecanum drive working**

    * Implement `buildDrivebase` and `buildDriveSource`.
    * Drive around, tune slow mode, fix motor directions.

2. **Add one simple mechanism as a plant**

    * For example, intake power plant.
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
    * TagAim + vision.

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

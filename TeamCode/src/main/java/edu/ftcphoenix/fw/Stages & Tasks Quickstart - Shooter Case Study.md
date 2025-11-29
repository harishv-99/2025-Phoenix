# Stages & Tasks Quickstart – Shooter Case Study

This document shows how to combine **plants**, **tasks**, and **stages** to build a robust shooter
subsystem.

We assume you already understand the basics from:

* **Beginner’s Guide** – TeleOp structure, `PhoenixTeleOpBase`, `PhoenixRobot`.
* **Framework Overview** – core abstractions (`DriveSource`, `Plant`, `Task`).
* **Tasks & Macros Quickstart** – how to build and trigger tasks.

Here we go one level deeper and show how to structure a realistic shooter:

* Dual flywheel motors (velocity control).
* Transfer / buffer (continuous servos).
* Indexer / pusher (positional servo).
* Multiple modes: IDLE, HOLD_SPEED, FEEDING, COOLDOWN.

We’ll use:

* **Plants** for low‑level motor/servo control.
* **Stages** to represent discrete shooter modes.
* **Tasks** to express multi‑step actions (e.g., "shoot N discs").

---

## 1. Shooter Requirements

Imagine a typical FTC shooter:

* Two flywheel motors for launching discs / rings.
* A transfer system that moves game pieces from intake to the flywheels.
* A pusher/indexer servo that pushes one piece at a time into the wheels.

Common behaviors you want:

1. **Idle** – everything stopped.
2. **Hold speed** – keep flywheels at a target RPM, transfer stopped, pusher retracted.
3. **Single shot** – from hold:

    * Nudge pusher forward briefly.
    * Retract pusher.
4. **Burst** – shoot several discs in sequence while keeping flywheels at speed.
5. **Cooldown/stop** – spin down flywheels and return to idle.

We’ll model this using:

* Plants: `shooterPlant`, `transferPlant`, `pusherPlant`.
* Stages: `IdleStage`, `HoldStage`, `FeedStage`, `CooldownStage`.
* Tasks: `ShootOneTask`, `ShootBurstTask`.

---

## 2. Plants: Low‑Level Control

First, we build plants for the shooter hardware using `FtcPlants`.

```java
public final class ShooterSubsystem implements Subsystem {

    // Hardware names (match your Robot Configuration)
    private static final String HW_SHOOTER_LEFT = "shooterLeft";
    private static final String HW_SHOOTER_RIGHT = "shooterRight";
    private static final String HW_TRANSFER_LEFT = "transferLeft";
    private static final String HW_TRANSFER_RIGHT = "transferRight";
    private static final String HW_PUSHER = "pusher";

    // Plants
    private final Plant shooterPlant;   // dual‑motor velocity plant
    private final Plant transferPlant;  // pair of CR servos for transfer
    private final Plant pusherPlant;    // positional servo plant

    public ShooterSubsystem(HardwareMap hw) {
        // Shooter flywheels
        DcMotorEx left = hw.get(DcMotorEx.class, HW_SHOOTER_LEFT);
        DcMotorEx right = hw.get(DcMotorEx.class, HW_SHOOTER_RIGHT);

        double ticksPerRev = 28.0; // example; use your encoder spec

        this.shooterPlant = FtcPlants.dualVelocityShooter(left, right, ticksPerRev);

        // Transfer – pair of continuous rotation servos
        CRServo leftTransfer = hw.get(CRServo.class, HW_TRANSFER_LEFT);
        CRServo rightTransfer = hw.get(CRServo.class, HW_TRANSFER_RIGHT);

        this.transferPlant = FtcPlants.pairCrServo(leftTransfer, rightTransfer);

        // Pusher – positional servo
        Servo pusher = hw.get(Servo.class, HW_PUSHER);
        this.pusherPlant = FtcPlants.servoPosition(pusher);
    }

    // Called every loop by the robot or subsystem manager
    public void update(LoopClock clock) {
        shooterPlant.update(clock);
        transferPlant.update(clock);
        pusherPlant.update(clock);
    }

    // Getters used by stages/tasks
    public Plant shooter() {
        return shooterPlant;
    }

    public Plant transfer() {
        return transferPlant;
    }

    public Plant pusher() {
        return pusherPlant;
    }
}
```

At this level, the plants only know about targets (velocity, power, position). They do **not** know
about "modes" or "how many discs" – that’s where stages and tasks come in.

---

## 3. Stages: Shooter Modes

A **stage** represents one mode of shooter operation:

* IDLE: shooter off, transfer off, pusher retracted.
* HOLD: shooter at target speed, transfer off.
* FEED: shooter at speed, transfer on, pusher pulsing.
* COOLDOWN: shooter spinning down, then back to IDLE.

We’ll assume a simple `Stage` interface:

```java
public interface Stage {
    /** Called when we first enter this stage. */
    void onEnter();

    /** Called every loop while this stage is active. */
    void onTick(LoopClock clock);

    /** Called when we leave this stage. */
    void onExit();

    /** Optional hook: is this stage finished? */
    default boolean isDone() {
        return false;
    }
}
```

We’ll also use a tiny `StageController` to hold the current stage:

```java
public final class StageController {
    private Stage current;

    public StageController(Stage initial) {
        this.current = initial;
        if (current != null) current.onEnter();
    }

    public void setStage(Stage next) {
        if (next == current) return;
        if (current != null) current.onExit();
        current = next;
        if (current != null) current.onEnter();
    }

    public Stage getStage() {
        return current;
    }

    public void update(LoopClock clock) {
        if (current != null) current.onTick(clock);
    }
}
```

This controller is generic; you can reuse it for other subsystems.

### 3.1 Shooter stages

Now we define shooter‑specific stages that use the plants.

We’ll assume some constants:

```java
public final class ShooterConstants {
    public static final double SHOOT_RPS = 35.0;  // target wheel speed
    public static final double TRANSFER_POWER = +1.0;  // transfer wheels power
    public static final double PUSH_OUT_POS = 0.75;  // pusher forward
    public static final double PUSH_HOME_POS = 0.20;  // pusher retracted
}
```

#### IdleStage

```java
public final class IdleStage implements Stage {
    private final ShooterSubsystem shooter;

    public IdleStage(ShooterSubsystem shooter) {
        this.shooter = shooter;
    }

    @Override
    public void onEnter() {
        shooter.shooter().setTarget(0.0);            // stop flywheels
        shooter.transfer().setTarget(0.0);           // stop transfer
        shooter.pusher().setTarget(ShooterConstants.PUSH_HOME_POS);
    }

    @Override
    public void onTick(LoopClock clock) {
        // Nothing special; plants are updated by the subsystem
    }

    @Override
    public void onExit() {
        // No cleanup needed for now
    }
}
```

#### HoldStage – maintain shooter velocity

```java
public final class HoldStage implements Stage {
    private final ShooterSubsystem shooter;

    public HoldStage(ShooterSubsystem shooter) {
        this.shooter = shooter;
    }

    @Override
    public void onEnter() {
        shooter.shooter().setTarget(ShooterConstants.SHOOT_RPS);
        shooter.transfer().setTarget(0.0);           // transfer off
        shooter.pusher().setTarget(ShooterConstants.PUSH_HOME_POS);
    }

    @Override
    public void onTick(LoopClock clock) {
        // Could add logic here to detect "at speed" via a feedback source.
    }

    @Override
    public void onExit() {
    }
}
```

#### FeedStage – feed continuously while stage is active

```java
public final class FeedStage implements Stage {
    private final ShooterSubsystem shooter;

    public FeedStage(ShooterSubsystem shooter) {
        this.shooter = shooter;
    }

    @Override
    public void onEnter() {
        shooter.shooter().setTarget(ShooterConstants.SHOOT_RPS);
        shooter.transfer().setTarget(ShooterConstants.TRANSFER_POWER);
        shooter.pusher().setTarget(ShooterConstants.PUSH_HOME_POS);
    }

    @Override
    public void onTick(LoopClock clock) {
        // For a simple continuous feed, we might just run transfer.
        // For pulsed feeding, this stage could also manage a small timing state machine.
    }

    @Override
    public void onExit() {
        shooter.transfer().setTarget(0.0);
        shooter.pusher().setTarget(ShooterConstants.PUSH_HOME_POS);
    }
}
```

#### CooldownStage – spin down over a short time

```java
public final class CooldownStage implements Stage {
    private final ShooterSubsystem shooter;
    private final double cooldownSec;

    private double elapsed = 0.0;

    public CooldownStage(ShooterSubsystem shooter, double cooldownSec) {
        this.shooter = shooter;
        this.cooldownSec = cooldownSec;
    }

    @Override
    public void onEnter() {
        shooter.transfer().setTarget(0.0);
        shooter.pusher().setTarget(ShooterConstants.PUSH_HOME_POS);
        // Let shooter spin down naturally or command a ramp depending on plant design.
        shooter.shooter().setTarget(0.0);
        elapsed = 0.0;
    }

    @Override
    public void onTick(LoopClock clock) {
        elapsed += clock.dtSec();
    }

    @Override
    public boolean isDone() {
        return elapsed >= cooldownSec;
    }

    @Override
    public void onExit() {
        shooter.shooter().setTarget(0.0);
    }
}
```

These stage classes express **mode logic** but never talk about buttons or OpModes. They work with
a generic `ShooterSubsystem` and `LoopClock`.

---

## 4. ShooterSubsystem with StageController

Now we integrate stages into the `ShooterSubsystem`.

```java
public final class ShooterSubsystem implements Subsystem {

    private final Plant shooterPlant;
    private final Plant transferPlant;
    private final Plant pusherPlant;

    private final StageController stages;
    private final IdleStage idleStage;
    private final HoldStage holdStage;
    private final FeedStage feedStage;

    public ShooterSubsystem(HardwareMap hw) {
        // Plants as before
        DcMotorEx left = hw.get(DcMotorEx.class, HW_SHOOTER_LEFT);
        DcMotorEx right = hw.get(DcMotorEx.class, HW_SHOOTER_RIGHT);
        double ticksPerRev = 28.0;

        this.shooterPlant = FtcPlants.dualVelocityShooter(left, right, ticksPerRev);

        CRServo leftTransfer = hw.get(CRServo.class, HW_TRANSFER_LEFT);
        CRServo rightTransfer = hw.get(CRServo.class, HW_TRANSFER_RIGHT);

        this.transferPlant = FtcPlants.pairCrServo(leftTransfer, rightTransfer);

        Servo pusher = hw.get(Servo.class, HW_PUSHER);
        this.pusherPlant = FtcPlants.servoPosition(pusher);

        // Stages
        idleStage = new IdleStage(this);
        holdStage = new HoldStage(this);
        feedStage = new FeedStage(this);

        stages = new StageController(idleStage);
    }

    // Subsystem lifecycle

    @Override
    public void onTeleopInit() {
        stages.setStage(idleStage);
    }

    @Override
    public void onTeleopLoop(LoopClock clock) {
        // Update plants
        shooterPlant.update(clock);
        transferPlant.update(clock);
        pusherPlant.update(clock);

        // Update current stage
        stages.update(clock);
    }

    @Override
    public void onAutoInit() {
        stages.setStage(idleStage);
    }

    @Override
    public void onAutoLoop(LoopClock clock) {
        onTeleopLoop(clock);
    }

    @Override
    public void onStop() {
        stages.setStage(idleStage);
    }

    // Accessors used by tasks and external code

    public Plant shooter() {
        return shooterPlant;
    }

    public Plant transfer() {
        return transferPlant;
    }

    public Plant pusher() {
        return pusherPlant;
    }

    public StageController stageController() {
        return stages;
    }

    public Stage idleStage() {
        return idleStage;
    }

    public Stage holdStage() {
        return holdStage;
    }

    public Stage feedStage() {
        return feedStage;
    }
}
```

Now the shooter subsystem has a clean lifecycle and stage management built‑in.

---

## 5. Tasks Built on Top of Stages

Stages define how the shooter behaves in each mode; tasks define **when** we move between modes and
for how long.

### 5.1 Single‑shot Task

A single shot from HOLD can be expressed as a task:

* Ensure we are in HOLD.
* Pulse the pusher out and back.

```java
public final class ShooterTasks {

    private ShooterTasks() {
    }

    public static Task shootOne(ShooterSubsystem shooter) {
        // Ensure we are in HOLD stage
        Task goHold = Tasks.runOnce(() -> shooter.stageController().setStage(shooter.holdStage()));

        // Pusher pulse task (using PlantTasks + wait)
        Task pushOut = Tasks.runOnce(
                () -> shooter.pusher().setTarget(ShooterConstants.PUSH_OUT_POS));
        Task pushHome = Tasks.runOnce(
                () -> shooter.pusher().setTarget(ShooterConstants.PUSH_HOME_POS));

        return Tasks.sequence(
                goHold,
                // short delay to ensure setpoint is applied
                Tasks.waitSeconds(0.05),
                pushOut,
                Tasks.waitSeconds(0.15),
                pushHome);
    }
}
```

We lean on `Tasks.runOnce(...)` and `Tasks.waitSeconds(...)` instead of writing our own timing
logic.

### 5.2 Burst Task (shoot N discs)

```java
public static Task shootBurst(ShooterSubsystem shooter, int count) {
    Task[] shots = new Task[count];
    for (int i = 0; i < count; i++) {
        shots[i] = shootOne(shooter);
    }

    return Tasks.sequence(
            Tasks.runOnce(() -> shooter.stageController().setStage(shooter.holdStage())),
            Tasks.waitSeconds(0.3), // allow spin‑up; you could also wait on a velocity condition
            Tasks.sequence(shots),
            Tasks.runOnce(() -> shooter.stageController().setStage(shooter.idleStage())));
}
```

You can later improve this example by:

* Waiting for "at speed" using a feedback source from the shooter plant.
* Adding transfer power during the pulses.

The pattern stays the same: **stages encode modes, tasks encode sequences**.

---

## 6. Integrating Shooter with PhoenixRobot and TeleOp

Now we connect everything in `PhoenixRobot` and a TeleOp shell.

### 6.1 PhoenixRobot with ShooterSubsystem

```java
public final class PhoenixRobot {

    private final MecanumDrivebase drivebase;
    private DriveSource driveSource;

    private final ShooterSubsystem shooter;

    public PhoenixRobot(HardwareMap hw, DriverKit driverKit, Telemetry telemetry) {
        // Drive wiring (standard teleOp + slow mode)
        this.drivebase = Drives
                .mecanum(hw)
                .names("frontLeft", "frontRight", "backLeft", "backRight")
                .invertFrontRight()
                .invertBackRight()
                .build();

        DriveSource manual = GamepadDriveSource.teleOpMecanum(driverKit);

        // Optional: AprilTag aim + slow mode
        // AprilTagSensor tagSensor = ... via Tags.aprilTags(...)
        // DriveSource aimed = TagAim.teleOpAim(manual, driverKit.p1().leftBumper(), tagSensor, ids);
        // this.driveSource = aimed.scaledWhen(
        //        () -> driverKit.p1().rightBumper().isPressed(), 0.30);

        this.driveSource = GamepadDriveSource.teleOpMecanumWithSlowMode(
                driverKit,
                driverKit.p1().rightBumper(),
                0.30);

        // Shooter subsystem
        this.shooter = new ShooterSubsystem(hw);
    }

    public void onTeleopInit() {
        shooter.onTeleopInit();
    }

    public void onTeleopLoop(LoopClock clock) {
        // Drive
        DriveSignal cmd = driveSource.get(clock);
        drivebase.drive(cmd);

        // Shooter
        shooter.onTeleopLoop(clock);
    }

    public void onStop() {
        shooter.onStop();
        drivebase.drive(DriveSignal.ZERO);
    }

    public ShooterSubsystem shooterSubsystem() {
        return shooter;
    }

    public MecanumDrivebase drivebase() {
        return drivebase;
    }
}
```

### 6.2 TeleOp shell with macros

We can now use `TaskRunner` + `Bindings` to trigger shooter tasks from buttons.

```java

@TeleOp(name = "Phoenix: Shooter TeleOp", group = "Phoenix")
public final class PhoenixShooterTeleOp extends PhoenixTeleOpBase {

    private PhoenixRobot robot;
    private final TaskRunner tasks = new TaskRunner();

    @Override
    protected void onInitRobot() {
        robot = new PhoenixRobot(hardwareMap, driverKit(), telemetry);
        robot.onTeleopInit();

        configureBindings();
    }

    private void configureBindings() {
        DriverKit.Player p1 = p1();
        ShooterSubsystem shooter = robot.shooterSubsystem();

        // Right trigger: hold shooter stage
        bind().whileHeld(p1.rightTrigger(), () ->
                shooter.stageController().setStage(shooter.holdStage()));

        // When right trigger released → go idle
        bind().onReleased(p1.rightTrigger(), () ->
                shooter.stageController().setStage(shooter.idleStage()));

        // Press Y: shoot 3 discs burst (non‑blocking)
        bind().onPressed(p1.y(), () -> {
            Task burst = ShooterTasks.shootBurst(shooter, 3);
            tasks.start(burst);
        });

        // Press B: cancel any running shooter task
        bind().onPressed(p1.b(), tasks::cancel);
    }

    @Override
    protected void onLoopRobot(double dtSec) {
        LoopClock clock = clock();

        robot.onTeleopLoop(clock);
        tasks.update(clock);
    }

    @Override
    protected void onStopRobot() {
        tasks.cancel();
        robot.onStop();
    }
}
```

Behavior summary:

* Driver controls the robot as usual with sticks.
* Holding right trigger keeps shooter in HOLD (wheels at speed, pusher home).
* Releasing right trigger returns shooter to IDLE.
* Pressing Y starts a 3‑disc burst task while the driver can still move.
* Pressing B cancels any running burst.

Stages + tasks keep the logic organized:

* Stages handle "what does HOLD/IDLE/FEED mean in terms of plant targets?".
* Tasks handle "in what order do we move between stages and tweak targets over time?".

---

## 7. Design Notes and Extensions

A few notes on extending this pattern safely:

1. **Feedback‑based "at speed" detection**

    * Instead of a fixed `waitSeconds(0.3)` after entering HOLD, use a `FeedbackSource<Double>`
      wrapped around shooter velocity.
    * Add a `Tasks.waitUntil(() -> shooterVelocitySource.sample(clock).value >= threshold)` step
      before feeding.

2. **Transfer timing and anti‑jam logic**

    * The `FeedStage` can be made smarter:

        * Pulse transfer power.
        * Reverse briefly on a jam.
    * These behaviors are still expressed as plant targets within the stage.

3. **Auto reuse**

    * The `ShooterTasks.shootBurst(...)` task can be used in Auto just like TeleOp.
    * Auto code simply builds a top‑level sequence:

      ```java
      Task auto = Tasks.sequence(
              DriveTasks.driveForSeconds(...),
              ShooterTasks.shootBurst(shooter, 3),
              DriveTasks.driveForSeconds(...));
      ```

4. **Keep stages and tasks small**

    * Each stage should have a clear, single responsibility.
    * Tasks should be composed from small building blocks (`shootOne`, `waitUntilAtSpeed`, etc.).

5. **Drive integration remains unchanged**

    * Shooter logic is independent of drive; it lives entirely in plants, stages, and tasks.
    * Drive continues to use `DriveSource` + `DriveSignal`, with TeleOp helpers like
      `GamepadDriveSource.teleOpMecanumWithSlowMode(...)` and optional `TagAim.teleOpAim(...)`.

---

## 8. Summary

In this case study, we:

* Built **plants** for a dual‑motor shooter, transfer, and pusher.
* Defined **stages** to represent shooter modes (IDLE, HOLD, FEED, COOLDOWN).
* Used a **StageController** to manage the active stage.
* Built **tasks** on top of stages for single and burst shots.
* Integrated everything into `PhoenixRobot` and a TeleOp shell with `TaskRunner` + `Bindings`.

The key pattern is:

> **Plants handle low‑level control. Stages express modes. Tasks express sequences.**
>
> Drive stays in its own lane with `DriveSource` and `DriveSignal`, and you blend everything at the
> robot level.

You can reuse this structure for other complex subsystems (intake + elevator, arm + wrist + claw,
etc.) by following the same separation of responsibilities.

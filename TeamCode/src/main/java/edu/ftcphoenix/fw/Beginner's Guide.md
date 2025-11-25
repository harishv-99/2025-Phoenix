# Phoenix FTC Beginner’s Guide (PhoenixRobot-based)

This guide is for students who are **new to programming FTC robots** using the Phoenix framework.

The goal:

> You focus on what the robot should do.
> Phoenix handles the wiring, math, and boilerplate.

In this version of the guide, we standardize around **one central robot class** called `PhoenixRobot` and a few **thin OpModes** (TeleOp and Autos) that delegate to it.

* All your real robot logic lives in **one place**: `PhoenixRobot`.
* TeleOp and Auto OpModes are tiny wrappers that call into `PhoenixRobot`.
* Under the hood, Phoenix uses:

    * `Gamepads` + `Bindings` for inputs
    * `DriveSource` / `DriveSignal` + `MecanumDrivebase` for driving
    * `Plant` and helpers for mechanisms
    * Optional **Tasks**, **TagAim**, and lookup tables for more advanced behavior.

---

## 1. Project structure

We recommend a simple, consistent project layout. You don’t have to use this, but it makes examples easier to share.

* **OpModes (thin shells)**

    * `org.firstinspires.ftc.teamcode.robots.phoenix.PhoenixTeleOp`
    * `org.firstinspires.ftc.teamcode.robots.phoenix.PhoenixAutoClose`
    * `org.firstinspires.ftc.teamcode.robots.phoenix.PhoenixAutoFar`
    * `org.firstinspires.ftc.teamcode.robots.phoenix.PhoenixAutoTwelveBall`

* **Robot logic (central class)**

    * `edu.ftcphoenix.robots.phoenix.PhoenixRobot`

* **Framework (do not edit in season unless you know what you’re doing)**

    * `edu.ftcphoenix.fw.*`

As a **student**, you will spend almost all your time in:

1. `PhoenixRobot` (the main brain)
2. Occasionally the thin OpModes (to add new autos or rename them)

---

## 2. The core idea: PhoenixRobot + thin OpModes

In the Phoenix pattern:

* `PhoenixRobot` owns **everything** about your robot:

    * hardware mapping
    * gamepad input
    * driving (mecanum, slow mode, auto-aim)
    * mechanisms (intake, transfer, shooter, pusher)
    * vision (AprilTags)
    * macros and autos

* TeleOp and Auto OpModes are very small and just call methods on `PhoenixRobot`.

### 2.1 A thin TeleOp

```java
@TeleOp(name = "Phoenix TeleOp", group = "Phoenix")
public final class PhoenixTeleOp extends OpMode {
    private final PhoenixRobot robot = new PhoenixRobot();

    @Override
    public void init() {
        robot.initHardware(hardwareMap, telemetry, gamepad1, gamepad2);
    }

    @Override
    public void start() {
        robot.startTeleOp(getRuntime());
    }

    @Override
    public void loop() {
        robot.loopTeleOp(getRuntime());
    }
}
```

Notice how **simple** this class is. The only state it owns is a `PhoenixRobot`.

### 2.2 A thin Autonomous

```java
@Autonomous(name = "Phoenix Auto Close", group = "Phoenix")
public final class PhoenixAutoClose extends LinearOpMode {
    private final PhoenixRobot robot = new PhoenixRobot();

    @Override
    public void runOpMode() throws InterruptedException {
        robot.initHardware(hardwareMap, telemetry, gamepad1, gamepad2);

        waitForStart();
        double t0 = getRuntime();
        robot.startAuto(t0, PhoenixRobot.AutoMode.CLOSE);

        while (opModeIsActive()) {
            robot.loopAuto(getRuntime(), PhoenixRobot.AutoMode.CLOSE);
        }
    }
}
```

Other autos (far side, 12-ball, etc.) look the same, but pass different `AutoMode` values.

---

## 3. PhoenixRobot: the one class students care about

`PhoenixRobot` is where you:

* wire hardware
* define button mappings
* implement drive logic
* implement auto-aim and shooter behavior
* implement macros and autos

A typical `PhoenixRobot` has these sections:

```java
public final class PhoenixRobot {
    // 1) Public AutoMode enum
    public enum AutoMode { CLOSE, FAR, TWELVE_BALL }

    // 2) FTC wiring: HardwareMap, Telemetry, Gamepads, Bindings, Clock
    private HardwareMap hardwareMap;
    private Telemetry telemetry;
    private Gamepads gamepads;
    private Bindings bindings;
    private final LoopClock clock = new LoopClock();

    // 3) Drive: mecanum + drive sources
    private MecanumDrivebase drivebase;
    private DriveSource manualDrive;    // sticks + slow mode
    private DriveSource driveWithAim;   // manualDrive wrapped with TagAim

    // 4) Mechanisms as Plants
    private Plant intake;
    private Plant transfer;
    private Plant shooter;
    private Plant pusher;

    // 5) Task runners
    private final TaskRunner macroRunner = new TaskRunner();
    private final TaskRunner autoRunner  = new TaskRunner();

    // 6) Vision + auto-aim
    private AprilTagSensor tagSensor;
    // private InterpolatingTable1D shooterLookup; // optional

    // 7) Constructor
    public PhoenixRobot() { }

    // 8) initHardware / startTeleOp / loopTeleOp / startAuto / loopAuto
}
```

The **important idea** is: **all behavior is here**, not scattered across multiple base classes or subsystems.

---

## 4. initHardware: wiring the robot once

`initHardware` is called from both TeleOp and Auto `init()`.

```java
public void initHardware(HardwareMap hw,
                         Telemetry tel,
                         Gamepad gp1,
                         Gamepad gp2) {
    this.hardwareMap = hw;
    this.telemetry   = tel;

    initInputs(gp1, gp2);
    initDrive();
    initMechanisms();
    initVision();
    wireBindings();

    telemetry.addLine("PhoenixRobot: initHardware complete");
    telemetry.update();
}
```

We split it into small helpers for clarity.

### 4.1 Inputs: Gamepads + Bindings

```java
private void initInputs(Gamepad gp1, Gamepad gp2) {
    gamepads = Gamepads.create(gp1, gp2);
    bindings = new Bindings();
}
```

* `Gamepads` is a small wrapper that normalizes stick directions, handles calibration, etc.
* `Bindings` lets you say things like:

    * "when I press A, run this macro"
    * "while held, do X; otherwise do Y"
    * "toggle this boolean every time I press X"

### 4.2 Drive: mecanum, slow mode, and rate limiting

We recommend using the **builder** and the **stick mapping helper**:

```java
private void initDrive() {
    drivebase = Drives
            .mecanum(hardwareMap)
            .names("fl", "fr", "bl", "br")   // standard naming
            .invertFrontRight()
            .invertBackRight()
            .build();

    // Base TeleOp drive: sticks + slow mode on right bumper.
    DriveSource sticks = StickDriveSource.teleOpMecanumWithSlowMode(
            gamepads,
            gamepads.p1().rightBumper(),
            0.30   // slow mode scale: 30% speed
    );

    manualDrive = sticks;
}
```

**Rate filtering:**

* `StickDriveSource.teleOpMecanumWithSlowMode(...)` internally wraps the raw stick mapping in a **rate-limited drive source**.
* The lateral axis (strafing) is automatically smoothed.
* You don’t have to do anything special in your code to get this behavior.

### 4.3 Vision and TagAim (optional but powerful)

You can layer **auto-aim** on top of the manual drive using `TagAim`:

```java
private static final Set<Integer> SCORING_TAGS = Set.of(1, 2, 3);

private void initVision() {
    tagSensor = Tags.aprilTags(hardwareMap, "Webcam 1");

    // Wrap manual drive with TagAim: while LB is held, robot auto-aims.
    driveWithAim = TagAim.teleOpAim(
            manualDrive,
            gamepads.p1().leftBumper(),
            tagSensor,
            SCORING_TAGS
    );
}
```

During TeleOp, you’ll usually drive with `driveWithAim`:

```java
private void updateTeleOpDrive() {
    DriveSignal cmd = driveWithAim.get(clock).clamped();
    drivebase.drive(cmd);
}
```

* When **left bumper is not held**, `driveWithAim` behaves just like `manualDrive`.
* When **left bumper is held**, `driveWithAim` adjusts the rotation to align with the best scoring tag.

---

## 5. Mechanisms as Plants

Mechanisms (intake, transfer, shooter, pusher) are modeled as `Plant`s.

* A `Plant` is a setpoint-driven mechanism with methods:

    * `setTarget(double target)`
    * `update(double dtSec)`
    * `boolean atSetpoint()`
    * `double getTarget()`

### 5.1 Wiring basic Plants

```java
private void initMechanisms() {
    // Intake: open-loop power
    intake = FtcPlants.power(hardwareMap, "intake", /*inverted=*/false);

    // Transfer/indexer: open-loop power
    transfer = FtcPlants.power(hardwareMap, "transfer", false);

    // Shooter: velocity pair (left + right motors)
    shooter = FtcPlants.velocityPair(
            hardwareMap,
            "shooterL",
            "shooterR",
            /*invertLeft=*/false,
            /*invertRight=*/true,
            /*ticksPerRev=*/28.0   // tune to your hardware
    );

    // Pusher: positional servo (0..1)
    pusher = FtcPlants.servoPosition(hardwareMap, "pusher", false);
}
```

### 5.2 Updating Plants each loop

In TeleOp and Auto loops, you’ll typically:

1. Decide your targets (based on buttons, tasks, or autos).
2. Call `setTarget(...)` as needed.
3. Call `update(dtSec)` once per loop.

For example, a very simple TeleOp intake control:

```java
private void updateMechanisms(double dtSec) {
    // Update any macros first
    macroRunner.update(clock);

    if (!macroRunner.hasActiveTask()) {
        // Manual intake on right trigger when no macro is running
        double intakePower = gamepads.p1().rightTrigger().get(); // 0..1
        intake.setTarget(intakePower);
    }

    // Update all plants once
    intake.update(dtSec);
    transfer.update(dtSec);
    shooter.update(dtSec);
    pusher.update(dtSec);
}
```

Later, you can replace the simple `intake.setTarget` logic with **macros** built from `PlantTasks` and `TaskRunner`.

---

## 6. Buttons and Bindings

All your **button mappings** live in one place: `wireBindings()`.

```java
private void wireBindings() {
    // Shooter: A = shoot, B = stop (very simple to start)
    bindings.onPress(
            gamepads.p1().buttonA(),
            new Runnable() {
                @Override
                public void run() {
                    // Example: set shooter target to SHOOT_RPM
                    // shooter.setTarget(shootRpmRadPerSec);
                }
            }
    );

    bindings.onPress(
            gamepads.p1().buttonB(),
            new Runnable() {
                @Override
                public void run() {
                    // Stop shooter
                    // shooter.setTarget(0.0);
                }
            }
    );

    // Intake macro on X (e.g., intake forward for 0.7 s)
    bindings.onPress(
            gamepads.p1().buttonX(),
            new Runnable() {
                @Override
                public void run() {
                    // startIntakePulseForward();
                }
            }
    );

    // Fire one ring on Y (pusher + transfer + shooter ready)
    bindings.onPress(
            gamepads.p1().buttonY(),
            new Runnable() {
                @Override
                public void run() {
                    // fireOneRing();
                }
            }
    );
}
```

In `loopTeleOp`, you always call:

```java
private void updateInputs(double dtSec) {
    gamepads.update(dtSec);   // stick sampling (future: filters)
    bindings.update(dtSec);   // press/held/release/toggle logic
}
```

This pattern makes the TeleOp loop very regular and easy to read.

---

## 7. TeleOp and Auto loops inside PhoenixRobot

### 7.1 TeleOp

```java
public void startTeleOp(double runtimeSec) {
    clock.reset(runtimeSec);
    macroRunner.clear();
    autoRunner.clear();
    // Set any TeleOp-specific initial goals here (e.g., shooter off)
}

public void loopTeleOp(double runtimeSec) {
    clock.update(runtimeSec);
    double dtSec = clock.dtSec();

    updateInputs(dtSec);
    updateTeleOpDrive();
    updateMechanisms(dtSec);
    updateTeleOpVisionAndShooter(dtSec);
    updateTelemetry();
}
```

### 7.2 Auto

Autos use the same robot, just in a different mode:

```java
public void startAuto(double runtimeSec, AutoMode mode) {
    clock.reset(runtimeSec);
    macroRunner.clear();
    autoRunner.clear();

    // Build your autonomous script here based on mode
    // e.g. enqueue Tasks into autoRunner
}

public void loopAuto(double runtimeSec, AutoMode mode) {
    clock.update(runtimeSec);
    double dtSec = clock.dtSec();

    updateInputs(dtSec);    // often minimal in auto but safe to call
    updateAutoScript(dtSec, mode);
    updateTelemetry();
}
```

`updateAutoScript` is where you’ll use **Tasks** and **TaskRunner** to define sequences like:

* Drive to pick up rings
* Spin up shooter
* Fire N rings
* Park in a specific spot

---

## 8. Auto-aim and auto shooter velocity (advanced)

Once you’re comfortable with basic TeleOp, you can enable more advanced behaviors **without** changing your overall structure.

### 8.1 Auto-aim using TagAim

We already saw how `driveWithAim` wraps your manual drive. You don’t need to change your TeleOp loop; just drive with `driveWithAim`.

In `initVision`:

```java
private void initVision() {
    tagSensor = Tags.aprilTags(hardwareMap, "Webcam 1");

    driveWithAim = TagAim.teleOpAim(
            manualDrive,
            gamepads.p1().leftBumper(),
            tagSensor,
            SCORING_TAGS
    );
}
```

In `updateTeleOpDrive`:

```java
private void updateTeleOpDrive() {
    DriveSignal cmd = driveWithAim.get(clock).clamped();
    drivebase.drive(cmd);
}
```

That’s it: hold LB to aim.

### 8.2 Auto shooter velocity with a lookup table

You can use `InterpolatingTable1D` to map distance to target velocity.

Conceptually:

```java
private InterpolatingTable1D shooterLookup;

private void initShooterLookup() {
    double[] distancesIn = {24, 30, 36, 42};
    double[] rpm         = {3000, 3200, 3400, 3600};
    shooterLookup = new InterpolatingTable1D(distancesIn, rpm);
}

private void updateTeleOpVisionAndShooter(double dtSec) {
    AprilTagObservation obs = tagSensor.best(SCORING_TAGS, 0.3);
    if (!obs.hasTarget) {
        return; // keep current shooter target
    }

    double rangeIn = obs.rangeInches;
    double targetRpm = shooterLookup.interpolate(rangeIn);

    double targetRadPerSec = Units.rpmToRadPerSec(targetRpm);
    shooter.setTarget(targetRadPerSec);
}
```

The exact details will depend on your field and shooter, but the pattern stays the same.

---

## 9. Debugging with debugDump (optional)

Phoenix includes a lightweight `DebugSink` interface so you can print structured debug information from core classes without manually writing a lot of telemetry code.

* Many core classes implement `debugDump(DebugSink dbg, String prefix)`:

    * `LoopClock`
    * `MecanumDrivebase`
    * `Pid`
    * `TagAim` and related classes
    * `Plant` has a default `debugDump` implementation
* You can pass a `NullDebugSink` (does nothing), a telemetry-backed sink, or your own filtered sink.

For beginners, this is **optional**. It becomes more useful later when you want to inspect the internal state of the framework quickly.

---

## 10. Summary

* You work mainly in **one class**: `PhoenixRobot`.
* TeleOp and Autos are **thin shells** delegating to `PhoenixRobot`.
* **Inputs**: `Gamepads` + `Bindings`.
* **Drive**: `StickDriveSource` + `MecanumDrivebase`, with **rate filtering** built in.
* **Mechanisms**: modeled as `Plant`s for consistent setpoint-style control.
* **Advanced features** (optional):

    * Tasks and macros
    * TagAim auto-aim
    * InterpolatingTable1D for shooter velocity
    * debugDump for introspection

This structure aims to be:

* **Approachable for beginners** (everything in one place)
* **Reusable across TeleOp and multiple Autos**
* **Powerful enough** for advanced features as your robot grows.

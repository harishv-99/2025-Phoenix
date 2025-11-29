# Shooter Case Study & Examples Walkthrough

This document walks through the **mecanum + shooter** examples in
`edu.ftcphoenix.fw.examples` and explains how they build on each other:

1. Example 01 – `TeleOp_01_MecanumBasic`
2. Example 02 – `TeleOp_02_ShooterBasic`
3. Example 03 – `TeleOp_03_ShooterMacro`
4. Example 04 – `TeleOp_04_ShooterInterpolated`
5. Example 05 – `TeleOp_05_ShooterTagAimVision`
6. Example 06 – `TeleOp_06_ShooterTagAimMacroVision`

All six examples share the same **loop shape**, and each adds a new idea:

* Plants and Actuators
* Bindings and Tasks
* Interpolating shooter tables
* TagAim and vision-based distance

Use this as a **reading guide** and a design reference.

---

## 1. Shared structure across all examples

All TeleOp examples follow this pattern:

```java
// Fields
private final LoopClock clock = new LoopClock();

private Gamepads gamepads;
private Bindings bindings;          // from Example 02 onward

private MecanumDrivebase drivebase;
private DriveSource stickDrive;     // or baseDrive / driveWithAim

// Mechanism plants (from Example 02 onward)
private Plant shooter;
private Plant transfer;
private Plant pusher;

// Optional: TaskRunner for macros (Examples 03 & 06)
private final TaskRunner macroRunner = new TaskRunner();
```

Lifecycle hooks:

```java
@Override
public void init() {
    // 1) Inputs
    gamepads = Gamepads.create(gamepad1, gamepad2);
    bindings = new Bindings();

    // 2) Drive wiring
    drivebase = Drives.mecanum(hardwareMap);
    stickDrive = GamepadDriveSource.teleOpMecanumStandard(gamepads);

    // 3) Mechanisms (plants) and bindings – see per-example sections.
}

@Override
public void start() {
    clock.reset(getRuntime());
}

@Override
public void loop() {
    // 1) Clock
    clock.update(getRuntime());
    double dtSec = clock.dtSec();

    // 2) Inputs + bindings
    gamepads.update(dtSec);
    bindings.update(dtSec);           // when bindings are used

    // 3) Macros (if any)
    macroRunner.update(clock);        // Examples 03 & 06

    // 4) Drive
    DriveSignal driveCmd = stickDrive.get(clock).clamped();
    drivebase.drive(driveCmd);
    drivebase.update(clock);

    // 5) Mechanism plants
    shooter.update(dtSec);
    transfer.update(dtSec);
    pusher.update(dtSec);

    // 6) Telemetry
}
```

Each example adds more to **sections 3–5** (bindings, macros, plants) while keeping
the overall structure the same.

---

## 2. Example 01 – TeleOp_01_MecanumBasic

**Goal:** pure mecanum driving with Phoenix helpers.

Key API usage:

```java
// Inputs
private Gamepads gamepads;

// Drive
private MecanumDrivebase drivebase;
private DriveSource stickDrive;

@Override
public void init() {
    gamepads = Gamepads.create(gamepad1, gamepad2);

    // Beginner-friendly helper:
    //   - Uses default motor names (frontLeftMotor, frontRightMotor, ...)
    //   - Applies a standard inversion pattern
    //   - Uses the default MecanumConfig
    drivebase = Drives.mecanum(hardwareMap);

    // Standard mecanum TeleOp mapping with slow-mode on P1 RB.
    stickDrive = GamepadDriveSource.teleOpMecanumStandard(gamepads);
}

@Override
public void loop() {
    clock.update(getRuntime());
    double dtSec = clock.dtSec();

    // 1) Inputs
    gamepads.update(dtSec);

    // 2) Logic: sticks → drive signal
    DriveSignal cmd = stickDrive.get(clock).clamped();
    lastDrive = cmd;

    // 3) Actuation
    drivebase.drive(cmd);
    drivebase.update(clock);

    // 4) Telemetry (axial, lateral, omega)
}
```

Controls (Example 01):

* P1 left stick X/Y: strafe + forward/back.
* P1 right stick X: rotate.
* P1 right bumper: slow mode.

This is the base pattern that all later examples reuse.

---

## 3. Example 02 – TeleOp_02_ShooterBasic

**Goal:** add a shooter mechanism (shooter + transfer + pusher) using:

* `Actuators` to create **plants**.
* `Bindings` to map buttons to **modes**.

### 3.1 Hardware assumptions

* Drive: same mecanum configuration as Example 01.
* Shooter motors: `"shooterLeftMotor"`, `"shooterRightMotor"`.
* Transfer CR servos: `"transferLeftServo"`, `"transferRightServo"`.
* Pusher servo: `"pusherServo"`.

Constants (simplified):

```java
private static final String HW_SHOOTER_LEFT  = "shooterLeftMotor";
private static final String HW_SHOOTER_RIGHT = "shooterRightMotor";

private static final String HW_TRANSFER_LEFT  = "transferLeftServo";
private static final String HW_TRANSFER_RIGHT = "transferRightServo";

private static final String HW_PUSHER = "pusherServo";

private static final double SHOOTER_VELOCITY_NATIVE = 2200.0; // example
private static final double SHOOTER_VELOCITY_TOLERANCE_NATIVE = 100.0;

private static final double TRANSFER_POWER_LOAD  = 0.3;
private static final double TRANSFER_POWER_SHOOT = 0.7;

private static final double PUSHER_POS_RETRACT = 0.0;
private static final double PUSHER_POS_LOAD    = 0.3;
private static final double PUSHER_POS_SHOOT   = 0.6;
```

### 3.2 Wiring plants with Actuators

Example 02 uses the **builder pattern** from `Actuators.plant(hardwareMap)`:

```java
// Shooter: velocity-controlled motor pair.
shooter = Actuators.plant(hardwareMap)
        .motorPair(HW_SHOOTER_LEFT, false,
                   HW_SHOOTER_RIGHT, true)
        .velocity(SHOOTER_VELOCITY_TOLERANCE_NATIVE)
        .build();

// Transfer: CR-servo pair as a power plant.
transfer = Actuators.plant(hardwareMap)
        .crServoPair(HW_TRANSFER_LEFT, false,
                     HW_TRANSFER_RIGHT, true)
        .power()
        .build();

// Pusher: positional servo.
pusher = Actuators.plant(hardwareMap)
        .servo(HW_PUSHER, false)
        .position()
        .build();
```

### 3.3 Modes + bindings

Instead of directly commanding plants in `loop()`, Example 02 uses
**high-level modes**:

```java
private enum TransferMode {
    STOP,
    LOAD,
    SHOOT
}

private enum PusherMode {
    RETRACT,
    LOAD,
    SHOOT
}

private TransferMode transferMode = TransferMode.STOP;
private PusherMode pusherMode = PusherMode.RETRACT;
private boolean shooterEnabled = false;
```

Bindings:

```java
// Toggle shooter on/off with right bumper.
bindings.onPress(
        gamepads.p1().rightBumper(),
        () -> shooterEnabled = !shooterEnabled
);

// A = LOAD: gentle transfer + pusher in load position.
        bindings.onPress(
        gamepads.p1().a(),
        () -> {
transferMode = TransferMode.LOAD;
pusherMode = PusherMode.LOAD;
        }
                );

// B = SHOOT: faster transfer + pusher in shoot position.
                bindings.onPress(
        gamepads.p1().b(),
        () -> {
transferMode = TransferMode.SHOOT;
pusherMode = PusherMode.SHOOT;
        }
                );

// X = RETRACT: stop transfer + retract pusher.
                bindings.onPress(
        gamepads.p1().x(),
        () -> {
transferMode = TransferMode.STOP;
pusherMode = PusherMode.RETRACT;
        }
                );
```

Mechanism logic in `loop()` translates modes → plant targets:

```java
// Shooter
shooter.setTarget(shooterEnabled ? SHOOTER_VELOCITY_NATIVE : 0.0);

// Transfer
switch (transferMode) {
        case STOP:
        transfer.setTarget(0.0);
        break;
                case LOAD:
        transfer.setTarget(TRANSFER_POWER_LOAD);
        break;
                case SHOOT:
        transfer.setTarget(TRANSFER_POWER_SHOOT);
        break;
                }

// Pusher
                switch (pusherMode) {
        case RETRACT:
        pusher.setTarget(PUSHER_POS_RETRACT);
        break;
                case LOAD:
        pusher.setTarget(PUSHER_POS_LOAD);
        break;
                case SHOOT:
        pusher.setTarget(PUSHER_POS_SHOOT);
        break;
                }

// Finally, update all plants once per loop.
                shooter.update(dtSec);
transfer.update(dtSec);
pusher.update(dtSec);
```

Controls (Example 02):

* Drive: same sticks + RB slow mode as Example 01.
* RB: toggle shooter on/off.
* A: LOAD (gentle transfer, pusher load position).
* B: SHOOT (faster transfer, pusher shoot position).
* X: RETRACT (stop transfer, retract pusher).

---

## 4. Example 03 – TeleOp_03_ShooterMacro

**Goal:** introduce **non-blocking macros** for the shooter using:

* `Task`, `TaskRunner`.
* `PlantTasks` helpers.
* `SequenceTask` and `ParallelAllTask`.

The key idea: **macros only call `Plant.setTarget(...)`**, while the
main loop still calls `plant.update(dt)` once per loop.

### 4.1 Macro runner and bindings

Add a `TaskRunner` for shooter macros:

```java
private final TaskRunner macroRunner = new TaskRunner();
```

Bindings use macros instead of raw modes:

```java
// P1 Y: run "shoot one ball" macro.
bindings.onPress(
        gamepads.p1().y(),
        this::startShootOneBallMacro
);

// P1 B: cancel any shooting macro and stop mechanism.
        bindings.onPress(
        gamepads.p1().b(),
        this::cancelShootMacros
);
```

In `loop()`:

```java
macroRunner.update(clock);   // before mechanism logic

// When no macro is running, apply safe defaults.
if (!macroRunner.hasActiveTask()) {
        shooter.setTarget(0.0);
    transfer.setTarget(0.0);
    pusher.setTarget(PUSHER_POS_RETRACT);
}

        shooter.update(dtSec);
transfer.update(dtSec);
pusher.update(dtSec);
```

### 4.2 Building the "shoot one ball" macro

`TeleOp_03_ShooterMacro` uses `PlantTasks` and `SequenceTask.of(...)` to express
multi-step behavior:

1. Spin up shooter to target and wait for `atSetpoint()` (with timeout).
2. Feed one ball using transfer + pusher (in parallel).
3. Hold shooter briefly.
4. Spin down shooter to 0.

Skeleton:

```java
private void startShootOneBallMacro() {
    if (macroRunner.hasActiveTask()) {
        // Already running; ignore, or queue more if you want.
        return;
    }

    Task macro = buildShootOneBallMacro();
    macroRunner.clear();       // "latest macro wins"
    macroRunner.enqueue(macro);
}

private void cancelShootMacros() {
    macroRunner.clear();
    shooter.setTarget(0.0);
    transfer.setTarget(0.0);
    pusher.setTarget(PUSHER_POS_RETRACT);
}

private Task buildShootOneBallMacro() {
    Task spinUp = PlantTasks.setTargetAndWaitForSetpoint(
            shooter,
            SHOOTER_VELOCITY_NATIVE,
            SHOOTER_SPINUP_TIMEOUT_SEC,
            null  // onTimeout: optional hook
    );

    Task feedTransfer = PlantTasks.holdForSeconds(
            transfer,
            TRANSFER_POWER_SHOOT,
            FEED_DURATION_SEC
    );

    Task pusherLoad = PlantTasks.holdForSeconds(
            pusher,
            PUSHER_POS_LOAD,
            PUSHER_STAGE_SEC,
            PUSHER_POS_LOAD
    );

    Task pusherShoot = PlantTasks.holdForSeconds(
            pusher,
            PUSHER_POS_SHOOT,
            PUSHER_STAGE_SEC,
            PUSHER_POS_RETRACT
    );

    Task feedPusher = SequenceTask.of(
            pusherLoad,
            pusherShoot
    );

    Task feedBoth = ParallelAllTask.of(
            feedTransfer,
            feedPusher
    );

    Task holdBeforeSpinDown = PlantTasks.holdForSeconds(
            shooter,
            SHOOTER_VELOCITY_NATIVE,
            SHOOTER_SPINDOWN_HOLD_SEC,
            SHOOTER_VELOCITY_NATIVE
    );

    Task spinDown = PlantTasks.setTargetInstant(shooter, 0.0);

    return SequenceTask.of(
            spinUp,
            feedBoth,
            holdBeforeSpinDown,
            spinDown
    );
}
```

Controls (Example 03):

* Drive: same as Example 01.
* Y: run "shoot one ball" macro.
* B: cancel macro and stop shooter/transfer/pusher.

---

## 5. Example 04 – TeleOp_04_ShooterInterpolated

**Goal:** choose shooter velocity from an **interpolating table** based on
an abstract "distance to goal".

This example introduces:

* `InterpolatingTable1D` for calibration.
* A driver-adjusted `distanceInches` using the D-pad.
* Shooter target velocity derived from the table.

### 5.1 Shooter velocity table

```java
private static final InterpolatingTable1D SHOOTER_VELOCITY_TABLE =
        InterpolatingTable1D.ofSortedPairs(
                24.0, 170.0,  // close shot
                30.0, 180.0,
                36.0, 195.0,
                42.0, 210.0,
                48.0, 225.0   // farther shot
        );

private static final double MIN_DISTANCE_INCHES = 20.0;
private static final double MAX_DISTANCE_INCHES = 60.0;

// Start in the middle of the table.
private double distanceInches = 36.0;
```

The docstring explains that the table **clamps** outside the range and
linearly interpolates between points.

### 5.2 Bindings: distance adjustment and shooter toggle

```java
// RB: toggle shooter on/off.
bindings.onPress(
        gamepads.p1().rightBumper(),
        () -> shooterEnabled = !shooterEnabled
);

// D-pad UP: increase "distance".
        bindings.onPress(
        gamepads.p1().dpadUp(),
        () -> distanceInches = MathUtil.clamp(
        distanceInches + STEP_DISTANCE_INCHES,
        MIN_DISTANCE_INCHES,
        MAX_DISTANCE_INCHES)
);

// D-pad DOWN: decrease "distance".
        bindings.onPress(
        gamepads.p1().dpadDown(),
        () -> distanceInches = MathUtil.clamp(
        distanceInches - STEP_DISTANCE_INCHES,
        MIN_DISTANCE_INCHES,
        MAX_DISTANCE_INCHES)
);
```

### 5.3 Loop: distance → table → shooter target

Inside `loop()`:

```java
// After clock + inputs + drive...

if (shooterEnabled) {
double targetVel = SHOOTER_VELOCITY_TABLE.interpolate(distanceInches);
    shooter.setTarget(targetVel);
} else {
        shooter.setTarget(0.0);
}

        shooter.update(dtSec);
```

Controls (Example 04):

* Drive: same as Example 01.
* RB: toggle shooter on/off.
* D-pad UP/DOWN: adjust "distance to target" in inches.

---

## 6. Example 05 – TeleOp_05_ShooterTagAimVision

**Goal:** add **real vision**:

* TagAim-wrapped drive: hold LB to auto-aim rotation at scoring tags.
* AprilTag-based distance → shooter velocity via `InterpolatingTable1D`.

### 6.1 Wiring TagAim and AprilTag sensor

```java
private MecanumDrivebase drivebase;
private DriveSource baseDrive;
private DriveSource driveWithAim;

private AprilTagSensor tagSensor;

// In init():

drivebase = Drives.mecanum(hardwareMap);
baseDrive = GamepadDriveSource.teleOpMecanumStandard(gamepads);

// Tag sensor using FTC VisionPortal adapter.
// Replace "Webcam 1" with your configured camera name.
tagSensor = FtcVision.aprilTags(hardwareMap, "Webcam 1");

// Wrap baseDrive with TagAim: hold LB to auto-aim omega.
driveWithAim = TagAim.teleOpAim(
        baseDrive,
        gamepads.p1().leftBumper(),
tagSensor,
SCORING_TAG_IDS
);
```

* `SCORING_TAG_IDS` is a small `Set<Integer>` of tag IDs you care about.
* When the driver holds LB, TagAim adjusts **omega** to face the best tag.

### 6.2 Shooter velocity from vision

Example 05 reuses an `InterpolatingTable1D` table like Example 04, but now
uses the **observed tag distance** instead of manual d-pad distance:

```java
AprilTagObservation obs = tagSensor.best(SCORING_TAG_IDS, MAX_TAG_AGE_SEC);
lastHasTarget = obs.hasTarget;
lastTagRangeInches = obs.rangeInches;
lastTagBearingRad = obs.bearingRad;
lastTagAgeSec = obs.ageSec;
lastTagId = obs.tagId;

if (shooterEnabled && obs.hasTarget) {
double targetVel = SHOOTER_VELOCITY_TABLE.interpolate(obs.rangeInches);
lastShooterTargetVel = targetVel;
    shooter.setTarget(targetVel);
} else {
lastShooterTargetVel = 0.0;
        shooter.setTarget(0.0);
}

        shooter.update(dtSec);
```

### 6.3 Bindings and controls

Bindings are simple:

```java
// Shooter toggle on A.
bindings.onPress(
        gamepads.p1().a(),
        new Runnable() {
    @Override
    public void run() {
        shooterEnabled = !shooterEnabled;
    }
}
);
```

Controls (Example 05):

* Drive sticks: same as Example 01.
* Hold LB: TagAim auto-aims omega at scoring tags.
* A: toggle shooter on/off (velocity picked from vision distance).

---

## 7. Example 06 – TeleOp_06_ShooterTagAimMacroVision

**Goal:** combine **everything**:

* Mecanum drive + TagAim (LB auto-aim).
* AprilTag-based distance → shooter velocity via `InterpolatingTable1D`.
* A **one-button shooting macro** (TaskRunner + PlantTasks).

### 7.1 Shared pieces

The wiring in `init()` looks similar to Example 05:

* `drivebase = Drives.mecanum(hardwareMap);`
* `baseDrive = GamepadDriveSource.teleOpMecanumStandard(gamepads);`
* `driveWithAim = TagAim.teleOpAim(baseDrive, gamepads.p1().leftBumper(), tagSensor, SCORING_TAG_IDS);`
* Shooter, transfer, pusher plants built via `Actuators.plant(hardwareMap)...`.
* Shooter velocity table via `InterpolatingTable1D.ofSortedPairs(...)`.

There is also a `TaskRunner` for macros:

```java
private final TaskRunner macroRunner = new TaskRunner();
```

### 7.2 Vision distance → shooter target velocity

Inside `loop()`, Example 06 computes a shooter target velocity from tag
range (similar to Example 05) and then passes that into a macro:

```java
AprilTagObservation obs = tagSensor.best(SCORING_TAG_IDS, MAX_TAG_AGE_SEC);
lastHasTarget = obs.hasTarget;
lastTagRangeInches = obs.rangeInches;

// If we have a tag, pick shooter target from table.
if (obs.hasTarget) {
shooterTargetVel = SHOOTER_VELOCITY_TABLE.interpolate(obs.rangeInches);
} else {
shooterTargetVel = DEFAULT_SHOOTER_VELOCITY_NATIVE; // or 0
}

lastShooterMacroTargetVel = shooterTargetVel;
```

### 7.3 One-button shoot macro

Bindings:

```java
// Y = shoot one ball (vision distance).
bindings.onPress(
        gamepads.p1().y(),
        () -> {
                if (macroRunner.hasActiveTask()) {
        return; // already running
        }
Task macro = buildShootOneBallMacro(shooterTargetVel);
            macroRunner.clear();
            macroRunner.enqueue(macro);
lastMacroStatus = "running";
        }
        );

// B = cancel macro + stop shooter.
        bindings.onPress(
        gamepads.p1().b(),
        this::cancelShootMacros
);
```

Macro structure (simplified):

```java
private Task buildShootOneBallMacro(double shooterTargetVel) {
    // Step 1: set shooter target + wait for atSetpoint or timeout.
    Task spinUp = PlantTasks.setTargetAndWaitForSetpoint(
            shooter,
            shooterTargetVel,
            SHOOTER_SPINUP_TIMEOUT_SEC,
            null
    );

    // Step 2: feed one ball (transfer + pusher in parallel).
    Task feedTransfer = PlantTasks.holdForSeconds(
            transfer,
            TRANSFER_POWER_SHOOT,
            FEED_DURATION_SEC
    );

    Task pusherLoad = PlantTasks.holdForSeconds(
            pusher,
            PUSHER_POS_LOAD,
            PUSHER_STAGE_SEC,
            PUSHER_POS_LOAD
    );

    Task pusherShoot = PlantTasks.holdForSeconds(
            pusher,
            PUSHER_POS_SHOOT,
            PUSHER_STAGE_SEC,
            PUSHER_POS_RETRACT
    );

    Task feedPusher = SequenceTask.of(pusherLoad, pusherShoot);

    Task feedBoth = ParallelAllTask.of(feedTransfer, feedPusher);

    // Step 3: hold shooter briefly, then spin down.
    Task holdBeforeSpinDown = PlantTasks.holdForSeconds(
            shooter,
            shooterTargetVel,
            SHOOTER_SPINDOWN_HOLD_SEC,
            shooterTargetVel
    );

    Task spinDown = PlantTasks.setTargetInstant(shooter, 0.0);

    return SequenceTask.of(
            spinUp,
            feedBoth,
            holdBeforeSpinDown,
            spinDown
    );
}
```

`loop()` then:

```java
clock.update(getRuntime());
        macroRunner.update(clock);

DriveSignal cmd = driveWithAim.get(clock).clamped();
lastDrive = cmd;

drivebase.drive(cmd);
drivebase.update(clock);

// Plants updated once per loop
shooter.update(dtSec);
transfer.update(dtSec);
pusher.update(dtSec);
```

Controls (Example 06):

* Drive: mecanum + TagAim (hold LB to auto-aim).
* Y: shoot one ball (using current vision-based shooter velocity).
* B: cancel macro and stop shooter.

---

## 8. How to adapt this to your robot

A practical way to use these examples:

1. **Start from Example 01** and make sure mecanum drive works with your
   motor names and orientations.
2. **Copy the mechanism wiring and bindings from Example 02** and adapt
   motor/servo names and target values.
3. **Add macros from Example 03** once basic shooter control is solid.
4. If your shooter is sensitive to distance, use **Example 04** as a pattern
   for creating and using `InterpolatingTable1D`.
5. If you have a camera and AprilTags, use **Example 05** as the base for
   TagAim + vision distance.
6. When you’re comfortable with Tasks and TagAim, use **Example 06** as a
   template for one-button shooting with vision-based distance.

All of these examples keep the same overall loop shape:

* `LoopClock` for timing.
* `Gamepads` + `Bindings` for inputs.
* `DriveSource` + `MecanumDrivebase` for drive.
* `Plant`s for mechanisms.
* Optional `TaskRunner` + `PlantTasks` for macros.

You can mix and match these building blocks to fit your actual season robot.

# Phoenix FTC Beginner’s Guide (Robot + Base Classes & Structure)

This guide is for students who are **new to programming FTC robots** using the Phoenix framework.

The goal:

> You focus on what the robot should do.
>
> Phoenix handles the wiring, math, and boilerplate.

In this version of the guide, we also adopt a **standard project structure**:

* Thin OpMode “shells” live in:

    * `org.firstinspires.ftc.teamcode.robots`
* The real robot logic lives in:

    * `edu.ftcphoenix.robots.phoenix` (and its subpackages)
* We use the framework base classes:

    * `PhoenixTeleOpBase` for TeleOp
    * `PhoenixAutoBase` for Autonomous
    * A `PhoenixRobot` class that represents *your robot*.

You don’t have to understand everything at once. Start with TeleOp and a single `PhoenixRobot` class. As your robot grows, you can split it into subsystems (see Section 9).

---

## 1. Big Picture: Who Does What?

There are three main pieces you’ll work with:

1. **PhoenixRobot** (in `edu.ftcphoenix.robots.phoenix`)

    * Knows about your motors, servos, sensors.
    * Knows how to drive, aim, and use mechanisms.
    * Has methods like `teleopInit()`, `teleopLoop(...)`, `stop()`.

2. **PhoenixTeleOpBase / PhoenixAutoBase** (in `edu.ftcphoenix.fw.robot`)

    * Base classes that create `Gamepads`, `DriverKit`, `LoopClock`, etc.
    * Call your robot’s methods each loop.

3. **Thin OpMode shells** (in `org.firstinspires.ftc.teamcode.robots`)

    * Very small classes with `@TeleOp` / `@Autonomous` annotations.
    * Just glue the base classes to your `PhoenixRobot`.

Think of it like this:

> OpMode shell → PhoenixTeleOpBase / PhoenixAutoBase → PhoenixRobot → motors, sensors, etc.

---

## 2. Project Structure for Students

A simple structure could look like this:

```text
TeamCode/src/main/java
└── org/firstinspires/ftc/teamcode/robots
    ├── PhoenixTeleOp.java         // thin TeleOp shell
    └── PhoenixAuto.java           // thin Auto shell

└── edu/ftcphoenix/robots/phoenix
    └── PhoenixRobot.java          // main robot class
```

All the interesting logic (drive, AprilTags, mechanisms) should live in `PhoenixRobot` (or classes it owns), **not** in the OpMode shells.

As your robot gets more complex, you can grow this into:

```text
edu/ftcphoenix/robots/phoenix
    PhoenixRobot.java

    subsystem/
        DriveSubsystem.java
        VisionSubsystem.java
        ShooterSubsystem.java
        // later: IntakeSubsystem, TransferSubsystem, etc.
```

In the beginning, it’s fine to keep everything in `PhoenixRobot`. Section 9 shows how to split it into subsystems later.

---

## 3. Building a PhoenixRobot (TeleOp with Drive Only)

Your first goal: get a robot driving with sticks using `PhoenixRobot` + `PhoenixTeleOpBase`.

### 3.1 PhoenixRobot.java

```java
package edu.ftcphoenix.robots.phoenix;

import com.qualcomm.robotcore.hardware.HardwareMap;
import org.firstinspires.ftc.robotcore.external.Telemetry;

import edu.ftcphoenix.fw.drive.DriveSignal;
import edu.ftcphoenix.fw.drive.MecanumDrivebase;
import edu.ftcphoenix.fw.drive.Drives;
import edu.ftcphoenix.fw.drive.source.StickDriveSource;
import edu.ftcphoenix.fw.input.DriverKit;
import edu.ftcphoenix.fw.util.LoopClock;

/**
 * PhoenixRobot represents your whole robot for this season.
 *
 * Students write robot behavior here. TeleOps and Autos are thin shells
 * that call into this class.
 */
public final class PhoenixRobot {

    private final DriverKit driverKit;
    private final Telemetry telemetry;
    private final MecanumDrivebase drivebase;
    private final StickDriveSource drive;

    public PhoenixRobot(HardwareMap hw, DriverKit driverKit, Telemetry telemetry) {
        this.driverKit = driverKit;
        this.telemetry = telemetry;

        // 1) Build the mecanum drivebase from motor names
        this.drivebase = Drives
                .mecanum(hw)
                .frontLeft("fl")
                .frontRight("fr")
                .backLeft("bl")
                .backRight("br")
                .invertRightSide()   // typical wiring; change if needed
                .build();

        // 2) Use left/right sticks to drive (no slow mode yet)
        this.drive = StickDriveSource.defaultMecanum(driverKit);
    }

    /** Called once when TeleOp starts. */
    public void teleopInit() {
        // For now, nothing extra to do.
    }

    /** Called every loop during TeleOp. */
    public void teleopLoop(LoopClock clock) {
        DriveSignal cmd = drive.get(clock);
        drivebase.drive(cmd);

        telemetry.addData("axial", cmd.axial);
        telemetry.addData("lateral", cmd.lateral);
        telemetry.addData("omega", cmd.omega);
    }

    /** Called when TeleOp or Auto is stopping. */
    public void stop() {
        drivebase.stop();
    }
}
```

### 3.2 Thin TeleOp Shell using PhoenixTeleOpBase

```java
package org.firstinspires.ftc.teamcode.robots;

import com.qualcomm.robotcore.eventloop.opmode.TeleOp;

import edu.ftcphoenix.fw.robot.PhoenixTeleOpBase;
import edu.ftcphoenix.robots.phoenix.PhoenixRobot;

/**
 * Thin TeleOp layer that delegates to PhoenixRobot.
 */
@TeleOp(name = "Phoenix: TeleOp", group = "Phoenix")
public final class PhoenixTeleOp extends PhoenixTeleOpBase {

    private PhoenixRobot robot;

    @Override
    protected void onInitRobot() {
        // PhoenixTeleOpBase provides hardwareMap, driverKit, and telemetry
        robot = new PhoenixRobot(hardwareMap, driverKit, telemetry);
        robot.teleopInit();
    }

    @Override
    protected void loopRobot(double dtSec) {
        // clock is provided by PhoenixTeleOpBase
        robot.teleopLoop(clock);
        telemetry.update();
    }

    @Override
    protected void onStopRobot() {
        robot.stop();
    }
}
```

That’s it:

* The TeleOp shell is tiny.
* All real logic lives in `PhoenixRobot`.

---

## 4. Adding Slow Mode (Precision Driving)

To add slow mode (reduced speed while holding a button), you only touch `PhoenixRobot`.

```java
public PhoenixRobot(HardwareMap hw, DriverKit driverKit, Telemetry telemetry) {
    this.driverKit = driverKit;
    this.telemetry = telemetry;

    this.drivebase = Drives
            .mecanum(hw)
            .frontLeft("fl")
            .frontRight("fr")
            .backLeft("bl")
            .backRight("br")
            .invertRightSide()
            .build();

    // Slow mode: press right bumper to drive at 30% speed
    this.drive = StickDriveSource.defaultMecanumWithSlowMode(
            driverKit,
            driverKit.p1().rightBumper(),
            0.30
    );
}
```

The TeleOp shell does **not** change. It still calls `robot.teleopLoop(clock)`.

---

## 5. Adding AprilTags (Auto-Aim in TeleOp)

Phoenix gives you helpers to use AprilTags without dealing with VisionPortal details.

We’ll extend `PhoenixRobot` so that:

* It owns an `AprilTagSensor`.
* It wraps the stick drive with `TagAim.forTeleOp(...)`.
* The TeleOp shell stays the same.

### 5.1 PhoenixRobot with Tags and Aim

```java
import org.firstinspires.ftc.robotcore.external.Telemetry;

import edu.ftcphoenix.fw.drive.DriveSignal;
import edu.ftcphoenix.fw.drive.DriveSource;
import edu.ftcphoenix.fw.drive.MecanumDrivebase;
import edu.ftcphoenix.fw.drive.Drives;
import edu.ftcphoenix.fw.drive.source.StickDriveSource;
import edu.ftcphoenix.fw.input.DriverKit;
import edu.ftcphoenix.fw.sensing.AprilTagSensor;
import edu.ftcphoenix.fw.sensing.Tags;
import edu.ftcphoenix.fw.sensing.TagAim;
import edu.ftcphoenix.fw.sensing.AprilTagObservation;
import edu.ftcphoenix.fw.util.LoopClock;

import java.util.Set;

public final class PhoenixRobot {

    private final DriverKit driverKit;
    private final Telemetry telemetry;
    private final MecanumDrivebase drivebase;
    private final DriveSource drive;        // NOTE: now DriveSource
    private final AprilTagSensor tags;
    private final Set<Integer> scoringTags = Set.of(1, 2, 3);

    public PhoenixRobot(HardwareMap hw, DriverKit driverKit, Telemetry telemetry) {
        this.driverKit = driverKit;
        this.telemetry = telemetry;

        this.drivebase = Drives
                .mecanum(hw)
                .frontLeft("fl")
                .frontRight("fr")
                .backLeft("bl")
                .backRight("br")
                .invertRightSide()
                .build();

        // 1) AprilTag sensor from webcam
        this.tags = Tags.aprilTags(hw, "Webcam 1");

        // 2) Base drive from sticks with slow mode
        StickDriveSource sticks =
                StickDriveSource.defaultMecanumWithSlowMode(
                        driverKit,
                        driverKit.p1().rightBumper(),
                        0.30);

        // 3) Wrap sticks with auto-aim when left bumper is held
        this.drive = TagAim.forTeleOp(
                sticks,
                driverKit.p1().leftBumper(),
                tags,
                scoringTags
        );
    }

    public void teleopInit() {
        // nothing extra for now
    }

    public void teleopLoop(LoopClock clock) {
        DriveSignal cmd = drive.get(clock);
        drivebase.drive(cmd);

        // Optional: show tag info for students
        AprilTagObservation obs = tags.best(scoringTags, 0.3);
        if (obs.hasTarget) {
            telemetry.addData("Tag id", obs.id);
            telemetry.addData("range (in)", "%.1f", obs.rangeInches);
            telemetry.addData("bearing (deg)", "%.1f", Math.toDegrees(obs.bearingRad));
        } else {
            telemetry.addLine("No scoring tag visible");
        }
    }

    public void stop() {
        drivebase.stop();
    }
}
```

The TeleOp shell remains the same. All the AprilTag wiring lives inside `PhoenixRobot`.

---

## 6. Autonomous with PhoenixAutoBase and PhoenixRobot

Once TeleOp is working, you can reuse the same `PhoenixRobot` for Autonomous.

### 6.1 Thin Auto Shell

```java
package org.firstinspires.ftc.teamcode.robots;

import com.qualcomm.robotcore.eventloop.opmode.Autonomous;

import edu.ftcphoenix.fw.robot.PhoenixAutoBase;
import edu.ftcphoenix.robots.phoenix.PhoenixRobot;
import edu.ftcphoenix.fw.task.Task;
import edu.ftcphoenix.fw.task.SequenceTask;

@Autonomous(name = "Phoenix: Auto", group = "Phoenix")
public final class PhoenixAuto extends PhoenixAutoBase {

    private PhoenixRobot robot;
    private Task rootTask;

    @Override
    protected void onInitRobot() {
        robot = new PhoenixRobot(hardwareMap, driverKit, telemetry);

        // Build your autonomous task sequence here
        rootTask = SequenceTask.of(
                // drive out of starting zone, aim, shoot, etc.
        );
    }

    @Override
    protected Task getRootTask() {
        return rootTask;
    }

    @Override
    protected void onStopRobot() {
        robot.stop();
    }
}
```

### 6.2 Using TagAim in Autonomous (Conceptual)

Inside one of your `Task` implementations, you can do:

```java
import edu.ftcphoenix.fw.sensing.TagAimController;
import edu.ftcphoenix.fw.task.Task;

public final class AimAndShootTask implements Task {
    private final PhoenixRobot robot;
    private final TagAimController aim;

    public AimAndShootTask(PhoenixRobot robot, TagAimController aim) {
        this.robot = robot;
        this.aim = aim;
    }

    @Override
    public void update(double dtSec) {
        double omega = aim.update(dtSec);
        // combine omega with your path-following or drive logic
    }

    @Override
    public boolean isDone() {
        // decide when you are aimed well enough to shoot
        return false;
    }
}
```

As a beginner, you don’t have to use tasks immediately—start with simple timed or one-step autos and gradually adopt the task system.

---

## 7. How Students Should Think About Files

When you’re editing code, remember:

* **OpMode shells** (`PhoenixTeleOp`, `PhoenixAuto`)

    * Very small.
    * Mostly boilerplate.
    * You rarely change these once they’re set up.

* **PhoenixRobot** (and its helper classes)

    * Where you spend most of your time.
    * Where you add new mechanisms and behavior.
    * Where you wire drive, AprilTags, shooter, intake, etc.

If your TeleOp shell starts to grow more than ~30–40 lines of code, it probably means logic is leaking out of `PhoenixRobot` and should be moved back.

---

## 8. Suggested Learning Path

1. **Step 1: Robot + TeleOp skeleton**

    * Create `PhoenixRobot` with drive only.
    * Use `PhoenixTeleOpBase` + TeleOp shell to move the robot.

2. **Step 2: Slow mode**

    * Modify `PhoenixRobot` to use `defaultMecanumWithSlowMode`.
    * Test precise driving.

3. **Step 3: AprilTags in TeleOp**

    * Add `Tags.aprilTags(...)` to `PhoenixRobot`.
    * Wrap drive with `TagAim.forTeleOp(...)`.
    * Print tag ID and range to telemetry.

4. **Step 4: Simple Autonomous**

    * Create a `PhoenixAuto` shell using `PhoenixAutoBase`.
    * Reuse `PhoenixRobot` inside auto.
    * Start with a very simple sequence (drive forward, park).

5. **Step 5: More mechanisms**

    * Add shooter/intake classes that `PhoenixRobot` owns.
    * Use buttons from `driverKit` to control them.

You don’t need to know every package or class in Phoenix to get started. Focus on:

* `PhoenixRobot`
* `PhoenixTeleOpBase` / TeleOp shell
* `Drives` + `StickDriveSource`
* `Tags` + `TagAim` (when you’re ready for AprilTags)

The rest of the framework is there to support you as your robot and ideas get more advanced.

---

## 9. Going Further: Splitting PhoenixRobot into Subsystems

Once your robot starts to have *many* mechanisms (drive, shooter, intake, arm, vision, etc.), keeping everything in a single `PhoenixRobot` file can get crowded.

A natural next step is to split behavior into **subsystems**:

* `DriveSubsystem` – owns mecanum drive and stick mapping (and optional TagAim).
* `VisionSubsystem` – owns the AprilTag sensor and tag IDs.
* `ShooterSubsystem` – owns the shooter motors and button control.
* (Later) `IntakeSubsystem`, `TransferSubsystem`, etc.

### 9.1 Expanded Project Structure

```text
TeamCode/src/main/java
└── org/firstinspires/ftc/teamcode/robots
    ├── PhoenixTeleOp.java          // thin TeleOp shell
    └── PhoenixAuto.java            // thin Auto shell

└── edu/ftcphoenix/robots/phoenix
    PhoenixRobot.java               // main robot class

    subsystem/
        DriveSubsystem.java
        VisionSubsystem.java
        ShooterSubsystem.java
```

### 9.2 PhoenixRobot with Subsystems

You’ve already seen a single-class PhoenixRobot. Here’s what it looks like when it owns subsystems instead:

```java
package edu.ftcphoenix.robots.phoenix;

import com.qualcomm.robotcore.hardware.HardwareMap;
import org.firstinspires.ftc.robotcore.external.Telemetry;

import edu.ftcphoenix.fw.input.DriverKit;
import edu.ftcphoenix.fw.util.LoopClock;
import edu.ftcphoenix.robots.phoenix.subsystem.DriveSubsystem;
import edu.ftcphoenix.robots.phoenix.subsystem.VisionSubsystem;
import edu.ftcphoenix.robots.phoenix.subsystem.ShooterSubsystem;

public final class PhoenixRobot {

    private final DriverKit driverKit;
    private final Telemetry telemetry;

    private final DriveSubsystem drive;
    private final VisionSubsystem vision;
    private final ShooterSubsystem shooter;

    public PhoenixRobot(HardwareMap hw, DriverKit driverKit, Telemetry telemetry) {
        this.driverKit = driverKit;
        this.telemetry = telemetry;

        this.vision  = new VisionSubsystem(hw, telemetry);
        this.drive   = new DriveSubsystem(hw, driverKit, vision);
        this.shooter = new ShooterSubsystem(hw, driverKit, telemetry);
    }

    public void teleopInit() {
        drive.teleopInit();
        vision.teleopInit();
        shooter.teleopInit();
    }

    public void teleopLoop(LoopClock clock) {
        drive.teleopLoop(clock);
        shooter.teleopLoop(clock);
        vision.teleopLoop(clock);

        telemetry.update();
    }

    public void autoInit() {
        drive.autoInit();
        vision.autoInit();
        shooter.autoInit();
    }

    public void autoLoop(LoopClock clock) {
        drive.autoLoop(clock);
        shooter.autoLoop(clock);
        vision.autoLoop(clock);

        telemetry.update();
    }

    public void stop() {
        drive.stop();
        shooter.stop();
        vision.stop();
    }
}
```

Your TeleOp and Auto shells **do not change**—they still just create a `PhoenixRobot` and call `teleopLoop` / `autoLoop`.

### 9.3 Example: DriveSubsystem (with TagAim)

This is essentially the same logic you saw earlier inside PhoenixRobot, but split into its own class:

```java
package edu.ftcphoenix.robots.phoenix.subsystem;

import com.qualcomm.robotcore.hardware.HardwareMap;

import edu.ftcphoenix.fw.drive.DriveSignal;
import edu.ftcphoenix.fw.drive.MecanumDrivebase;
import edu.ftcphoenix.fw.drive.Drives;
import edu.ftcphoenix.fw.drive.DriveSource;
import edu.ftcphoenix.fw.drive.source.StickDriveSource;
import edu.ftcphoenix.fw.input.DriverKit;
import edu.ftcphoenix.fw.util.LoopClock;
import edu.ftcphoenix.fw.sensing.TagAim;

public final class DriveSubsystem {

    private final MecanumDrivebase drivebase;
    private final DriveSource driveSource;

    public DriveSubsystem(HardwareMap hw,
                          DriverKit driverKit,
                          VisionSubsystem vision) {

        this.drivebase = Drives
                .mecanum(hw)
                .frontLeft("fl")
                .frontRight("fr")
                .backLeft("bl")
                .backRight("br")
                .invertRightSide()
                .build();

        StickDriveSource sticks =
                StickDriveSource.defaultMecanumWithSlowMode(
                        driverKit,
                        driverKit.p1().rightBumper(), // slow mode
                        0.30
                );

        this.driveSource = TagAim.forTeleOp(
                sticks,
                driverKit.p1().leftBumper(),      // hold LB to aim
                vision.getTagSensor(),
                vision.getScoringTagIds()
        );
    }

    public void teleopInit() { }

    public void teleopLoop(LoopClock clock) {
        DriveSignal cmd = driveSource.get(clock);
        drivebase.drive(cmd);
    }

    public void autoInit() { }

    public void autoLoop(LoopClock clock) {
        // can be empty or use simple auto drive
    }

    public void stop() {
        drivebase.stop();
    }
}
```

### 9.4 Example: VisionSubsystem (AprilTags)

This wraps `Tags.aprilTags(...)` and keeps tag-related telemetry in one place:

```java
package edu.ftcphoenix.robots.phoenix.subsystem;

import com.qualcomm.robotcore.hardware.HardwareMap;
import org.firstinspires.ftc.robotcore.external.Telemetry;

import edu.ftcphoenix.fw.sensing.AprilTagSensor;
import edu.ftcphoenix.fw.sensing.AprilTagObservation;
import edu.ftcphoenix.fw.sensing.Tags;
import edu.ftcphoenix.fw.util.LoopClock;

import java.util.Set;

public final class VisionSubsystem {

    private final Telemetry telemetry;
    private final AprilTagSensor tags;
    private final Set<Integer> scoringTags = Set.of(1, 2, 3);

    public VisionSubsystem(HardwareMap hw, Telemetry telemetry) {
        this.telemetry = telemetry;
        this.tags = Tags.aprilTags(hw, "Webcam 1");
    }

    public AprilTagSensor getTagSensor() {
        return tags;
    }

    public Set<Integer> getScoringTagIds() {
        return scoringTags;
    }

    public void teleopInit() { }

    public void teleopLoop(LoopClock clock) {
        AprilTagObservation obs = tags.best(scoringTags, 0.3);
        if (obs.hasTarget) {
            telemetry.addData("Tag id", obs.id);
            telemetry.addData("range (in)", "%.1f", obs.rangeInches);
            telemetry.addData("bearing (deg)", "%.1f", Math.toDegrees(obs.bearingRad));
        } else {
            telemetry.addLine("No scoring tag visible");
        }
    }

    public void autoInit() { }

    public void autoLoop(LoopClock clock) {
        // optional: reuse same tag telemetry in auto
    }

    public void stop() { }
}
```

### 9.5 Example: ShooterSubsystem (Simple Buttons)

Same behavior as before, wrapped into its own class:

```java
package edu.ftcphoenix.robots.phoenix.subsystem;

import com.qualcomm.robotcore.hardware.HardwareMap;

import org.firstinspires.ftc.robotcore.external.Telemetry;

import edu.ftcphoenix.fw.adapters.ftc.FtcHardware;
import edu.ftcphoenix.fw.hal.MotorOutput;
import edu.ftcphoenix.fw.input.DriverKit;
import edu.ftcphoenix.fw.util.LoopClock;

public final class ShooterSubsystem {

    private final Telemetry telemetry;
    private final MotorOutput left;
    private final MotorOutput right;
    private final DriverKit.Player gunner;

    private double lastPower = 0.0;

    public ShooterSubsystem(HardwareMap hw,
                            DriverKit driverKit,
                            Telemetry telemetry) {
        this.telemetry = telemetry;
        this.gunner = driverKit.p2(); // or p1 if single driver

        this.left = FtcHardware.motor(hw, "shooterLeft", false);
        this.right = FtcHardware.motor(hw, "shooterRight", true);
    }

    public void teleopInit() {
        setPower(0.0);
    }

    public void teleopLoop(LoopClock clock) {
        double power = 0.0;
        if (gunner.a().isPressed()) {
            power = 0.5;
        } else if (gunner.b().isPressed()) {
            power = 1.0;
        }
        setPower(power);

        telemetry.addData("Shooter power", "%.2f", lastPower);
    }

    public void autoInit() {
        setPower(0.0);
    }

    public void autoLoop(LoopClock clock) {
        // later: add auto shooter behavior
    }

    public void stop() {
        setPower(0.0);
    }

    private void setPower(double power) {
        lastPower = power;
        left.setPower(power);
        right.setPower(power);
    }
}
```

---

### 9.6 When to Move to Subsystems

You should consider moving to the subsystem structure when:

* `PhoenixRobot` starts getting long (150–200+ lines).
* You find yourself scrolling a lot to find drive vs shooter vs vision code.
* Multiple students want to work on different parts of the robot at the same time.

Until then, it’s perfectly fine to stay with a single-class `PhoenixRobot`.

The **key pattern** stays the same:

* Thin shells (`PhoenixTeleOp`, `PhoenixAuto`) use `PhoenixTeleOpBase` / `PhoenixAutoBase`.
* Those shells create a `PhoenixRobot`.
* `PhoenixRobot` owns your behavior (either directly or via subsystems).
* Phoenix framework handles the wiring, math, and boilerplate behind the scenes.

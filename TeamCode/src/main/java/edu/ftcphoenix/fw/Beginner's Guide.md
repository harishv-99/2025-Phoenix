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

You don’t have to understand everything at once. Start with TeleOp and a single `PhoenixRobot` class. As your robot grows, you can split it into subsystems (see Section 6). Later, you can add tasks and macros (see Section 9).

---

## 1. Big Picture: Who Does What?

There are three main pieces you’ll work with:

1. **PhoenixRobot** (in `edu.ftcphoenix.robots.phoenix`)

    * Knows about your motors, servos, sensors.
    * Knows how to drive, aim, and use mechanisms.
    * Has lifecycle methods like:

      ```java
      public final class PhoenixRobot {
          public PhoenixRobot(HardwareMap hw, DriverKit driverKit, Telemetry telemetry) { ... }
 
          public void onTeleopInit() { ... }
          public void onTeleopLoop(LoopClock clock) { ... }
          public void onAutoInit() { ... }
          public void onAutoLoop(LoopClock clock) { ... }
          public void onStop() { ... }
      }
      ```

2. **PhoenixTeleOpBase / PhoenixAutoBase** (in `edu.ftcphoenix.fw.robot`)

    * Base classes that hide FTC OpMode boilerplate.
    * They own the main loop and call into your `PhoenixRobot` methods.
    * `PhoenixAutoBase` also owns a `TaskRunner` for your autonomous task sequences.

3. **Framework adapters** (in `edu.ftcphoenix.fw.adapters.*`)

    * `FtcHardware` turns FTC SDK objects (`DcMotorEx`, `Servo`, etc.) into simple outputs.
    * `FtcVision` turns camera + processors into an `AprilTagSensor`.
    * Higher-level helpers like `Drives`, `Tags`, `TagAim`, `FtcPlants`, etc. wire common patterns.

Your robot code should mostly use **DriverKit**, **Drives**, **Tags**, your own subsystems, and (later) simple **Tasks** via helpers like `DriveTasks` and `PlantTasks`. The adapters hide the low‑level details.

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

`PhoenixRobot` then owns these subsystems and forwards lifecycle calls to them.

You don’t have to start with subsystems. It’s fine to begin with everything in `PhoenixRobot`. Section 6 shows how to split it into subsystems later.

---

## 3. Building a PhoenixRobot (TeleOp with Drive Only)

Your first goal: get a robot driving with sticks using `PhoenixRobot` + `PhoenixTeleOpBase`.

We’ll start with **drive only**, no AprilTags yet.

### 3.1 PhoenixRobot.java (drive only)

```java
package edu.ftcphoenix.robots.phoenix;

import com.qualcomm.robotcore.hardware.HardwareMap;
import org.firstinspires.ftc.robotcore.external.Telemetry;

import edu.ftcphoenix.fw.drive.DriveSignal;
import edu.ftcphoenix.fw.drive.DriveSource;
import edu.ftcphoenix.fw.drive.Drives;
import edu.ftcphoenix.fw.drive.MecanumDrivebase;
import edu.ftcphoenix.fw.drive.source.StickDriveSource;
import edu.ftcphoenix.fw.input.DriverKit;
import edu.ftcphoenix.fw.util.LoopClock;

public final class PhoenixRobot {

    private final DriverKit driverKit;
    private final Telemetry telemetry;

    private final MecanumDrivebase drivebase;
    private final DriveSource drive;

    public PhoenixRobot(HardwareMap hw, DriverKit driverKit, Telemetry telemetry) {
        this.driverKit = driverKit;
        this.telemetry = telemetry;

        // 1) Build a mecanum drivebase from hardware
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

    /** Called once when TeleOp starts (PLAY is pressed). */
    public void onTeleopInit() {
        // For now, nothing extra to do.
    }

    /** Called every loop during TeleOp. */
    public void onTeleopLoop(LoopClock clock) {
        DriveSignal cmd = drive.get(clock);
        drivebase.drive(cmd);

        telemetry.addData("axial", cmd.axial);
        telemetry.addData("lateral", cmd.lateral);
        telemetry.addData("omega", cmd.omega);
        telemetry.update();
    }

    /** Called when TeleOp or Auto is stopping. */
    public void onStop() {
        drivebase.stop();
    }
}
```

Key ideas:

* `PhoenixRobot` **does not extend** `OpMode`. It’s just a plain Java class.
* `Drives.mecanum(hw)` uses your hardware map to wire wheels correctly.
* `StickDriveSource.defaultMecanum(driverKit)` reads sticks from gamepad 1.

You never talk to `Gamepad` directly. You always go through **DriverKit** and **GamepadDevice**, which provide higher-level buttons/axes and handle debouncing.

---

### 3.2 PhoenixTeleOp.java (thin shell)

```java
package org.firstinspires.ftc.teamcode.robots;

import com.qualcomm.robotcore.eventloop.opmode.TeleOp;

import edu.ftcphoenix.fw.robot.PhoenixTeleOpBase;
import edu.ftcphoenix.robots.phoenix.PhoenixRobot;

@TeleOp(name = "Phoenix: Drive Only", group = "Phoenix")
public final class PhoenixTeleOp extends PhoenixTeleOpBase {

    private PhoenixRobot robot;

    @Override
    protected void onInitRobot() {
        // PhoenixTeleOpBase gives us hardwareMap, driverKit(), telemetry
        robot = new PhoenixRobot(hardwareMap, driverKit(), telemetry);
    }

    @Override
    protected void onStartRobot() {
        robot.onTeleopInit();
    }

    @Override
    protected void onLoopRobot(double dtSec) {
        robot.onTeleopLoop(clock());
    }

    @Override
    protected void onStopRobot() {
        robot.onStop();
    }
}
```

This class is intentionally tiny. It:

* Extends `PhoenixTeleOpBase` instead of `OpMode`.
* Wires the base to your `PhoenixRobot`.
* Forwards lifecycle events (`onInitRobot`, `onStartRobot`, `onLoopRobot`, `onStopRobot`).

Your students will mostly be editing `PhoenixRobot`, not this shell.

---

## 4. Adding Slow Mode and Better Drive Controls

Now we add a slow-mode trigger and make the drive feel better for drivers.

We’ll use:

* `StickDriveSource.defaultMecanumWithSlowMode(...)` for square/limited inputs.
* A slow mode that activates while the driver holds a bumper.

```java
// Inside PhoenixRobot constructor, replace the drive line

this.drive = StickDriveSource.defaultMecanumWithSlowMode(
        driverKit,
        driverKit.p1().rightBumper(), // hold for slow mode
        0.30                          // 30% speed in slow mode
);
```

Now driver 1 can:

* Drive normally with the sticks.
* Hold the right bumper to make fine adjustments near the goal.

---

## 5. Adding AprilTag Aiming (TagAim)

Phoenix includes helpers to aim the robot at AprilTags during TeleOp.

We’ll use:

* `FtcVision` + `Tags.aprilTags(...)` to create an `AprilTagSensor`.
* `TagAim.forTeleOp(...)` to wrap the drive controls.

### 5.1 Wiring vision in PhoenixRobot

```java
import edu.ftcphoenix.fw.sensing.AprilTagSensor;
import edu.ftcphoenix.fw.sensing.TagAim;
import edu.ftcphoenix.fw.sensing.Tags;

...

public final class PhoenixRobot {

    private final AprilTagSensor tags;
    private final Set<Integer> scoringTags = Set.of(1, 2, 3);

    public PhoenixRobot(HardwareMap hw, DriverKit driverKit, Telemetry telemetry) {
        ...

        // 1) Build mecanum drivebase (same as before)
        this.drivebase = Drives
                .mecanum(hw)
                .frontLeft("fl")
                .frontRight("fr")
                .backLeft("bl")
                .backRight("br")
                .invertRightSide()
                .build();

        // 2) Create Tag sensor (uses FtcVision under the hood)
        this.tags = Tags.aprilTags(hw, "Webcam 1");

        // 3) Base drive source (sticks + slow mode)
        var baseDrive = StickDriveSource.defaultMecanumWithSlowMode(
                driverKit,
                driverKit.p1().rightBumper(),
                0.30);

        // 4) Wrap with TagAim for auto-aiming when left bumper is held
        this.drive = TagAim.forTeleOp(
                baseDrive,
                driverKit.p1().leftBumper(),  // hold to aim
                tags,
                scoringTags);
    }

    ... // onTeleopInit/onTeleopLoop/onStop as before
}
```

Now driver 1 can:

* Drive with sticks as before.
* Hold **left bumper** to make the robot automatically rotate to face a scoring AprilTag.

You don’t need to worry about the math for converting tag bearing to robot rotation. `TagAim` and `Tags` handle that.

---

## 6. Splitting into Subsystems (Drive, Vision, Shooter)

As your robot adds mechanisms, a single `PhoenixRobot` class can get long.

A common pattern is to create a `subsystem` package and move each “chunk” into its own class.

```text
edu/ftcphoenix/robots/phoenix
    PhoenixRobot.java

    subsystem/
        DriveSubsystem.java
        VisionSubsystem.java
        ShooterSubsystem.java
```

Each subsystem:

* Knows its own hardware.
* Has its own lifecycle methods (`onTeleopInit`, `onTeleopLoop`, `onAutoInit`, `onAutoLoop`, `onStop`).
* Uses the same Phoenix helpers.

### 6.1 Subsystem interface

The Phoenix framework provides a simple `Subsystem` interface:

```java
package edu.ftcphoenix.fw.robot;

import edu.ftcphoenix.fw.util.LoopClock;

public interface Subsystem {
    default void onTeleopInit() {}
    default void onTeleopLoop(LoopClock clock) {}
    default void onAutoInit() {}
    default void onAutoLoop(LoopClock clock) {}
    default void onStop() {}
}
```

You can implement this in your own classes.

### 6.2 Example: DriveSubsystem

```java
package edu.ftcphoenix.robots.phoenix.subsystem;

import com.qualcomm.robotcore.hardware.HardwareMap;

import org.firstinspires.ftc.robotcore.external.Telemetry;

import java.util.Set;

import edu.ftcphoenix.fw.drive.DriveSignal;
import edu.ftcphoenix.fw.drive.DriveSource;
import edu.ftcphoenix.fw.drive.Drives;
import edu.ftcphoenix.fw.drive.MecanumDrivebase;
import edu.ftcphoenix.fw.drive.source.StickDriveSource;
import edu.ftcphoenix.fw.input.DriverKit;
import edu.ftcphoenix.fw.robot.Subsystem;
import edu.ftcphoenix.fw.sensing.AprilTagSensor;
import edu.ftcphoenix.fw.sensing.TagAim;
import edu.ftcphoenix.fw.util.LoopClock;

public final class DriveSubsystem implements Subsystem {

    private final Telemetry telemetry;
    private final DriverKit driverKit;

    private final MecanumDrivebase drivebase;
    private final DriveSource drive;

    public DriveSubsystem(HardwareMap hw,
                          DriverKit driverKit,
                          Telemetry telemetry,
                          AprilTagSensor tags,
                          Set<Integer> scoringTags) {
        this.telemetry = telemetry;
        this.driverKit = driverKit;

        this.drivebase = Drives
                .mecanum(hw)
                .frontLeft("fl")
                .frontRight("fr")
                .backLeft("bl")
                .backRight("br")
                .invertRightSide()
                .build();

        var baseDrive = StickDriveSource.defaultMecanumWithSlowMode(
                driverKit,
                driverKit.p1().rightBumper(),
                0.30);

        this.drive = TagAim.forTeleOp(
                baseDrive,
                driverKit.p1().leftBumper(),
                tags,
                scoringTags);
    }

    @Override
    public void onTeleopLoop(LoopClock clock) {
        DriveSignal cmd = drive.get(clock);
        drivebase.drive(cmd);

        telemetry.addData("axial", cmd.axial);
        telemetry.addData("lateral", cmd.lateral);
        telemetry.addData("omega", cmd.omega);
    }

    @Override
    public void onStop() {
        drivebase.stop();
    }
}
```

### 6.3 Example: VisionSubsystem

```java
package edu.ftcphoenix.robots.phoenix.subsystem;

import com.qualcomm.robotcore.hardware.HardwareMap;

import org.firstinspires.ftc.robotcore.external.Telemetry;

import java.util.Set;

import edu.ftcphoenix.fw.robot.Subsystem;
import edu.ftcphoenix.fw.sensing.AprilTagObservation;
import edu.ftcphoenix.fw.sensing.AprilTagSensor;
import edu.ftcphoenix.fw.sensing.Tags;
import edu.ftcphoenix.fw.util.LoopClock;

public final class VisionSubsystem implements Subsystem {

    private static final double MAX_AGE_SEC = 0.3;

    private final Telemetry telemetry;
    private final AprilTagSensor tags;
    private final Set<Integer> scoringTags;

    public VisionSubsystem(HardwareMap hw,
                           Telemetry telemetry,
                           Set<Integer> scoringTags) {
        this.telemetry = telemetry;
        this.tags = Tags.aprilTags(hw, "Webcam 1");
        this.scoringTags = scoringTags;
    }

    public AprilTagSensor sensor() {
        return tags;
    }

    public AprilTagObservation bestScoringTag() {
        return tags.best(scoringTags, MAX_AGE_SEC);
    }

    @Override
    public void onTeleopLoop(LoopClock clock) {
        AprilTagObservation obs = bestScoringTag();
        if (obs.hasTarget) {
            telemetry.addData("tagId", obs.id);
            telemetry.addData("rangeIn", "%.1f", obs.rangeInches);
            telemetry.addData("bearingDeg", "%.1f", Math.toDegrees(obs.bearingRad));
        } else {
            telemetry.addLine("No scoring tag visible");
        }
    }
}
```

### 6.4 Example: ShooterSubsystem (power-based)

This simple version just powers two motors together. You can later upgrade it to a velocity-based shooter using `FtcPlants.velocityPair(...)`.

```java
package edu.ftcphoenix.robots.phoenix.subsystem;

import com.qualcomm.robotcore.hardware.HardwareMap;

import org.firstinspires.ftc.robotcore.external.Telemetry;

import edu.ftcphoenix.fw.adapters.ftc.FtcHardware;
import edu.ftcphoenix.fw.hal.MotorOutput;
import edu.ftcphoenix.fw.input.DriverKit;
import edu.ftcphoenix.fw.robot.Subsystem;
import edu.ftcphoenix.fw.util.LoopClock;

public final class ShooterSubsystem implements Subsystem {

    private final Telemetry telemetry;
    private final MotorOutput left;
    private final MotorOutput right;
    private final DriverKit.Player gunner;

    private double lastPower = 0.0;

    public ShooterSubsystem(HardwareMap hw,
                            DriverKit driverKit,
                            Telemetry telemetry) {
        this.telemetry = telemetry;
        this.left = FtcHardware.motor(hw, "shooterLeft", false);
        this.right = FtcHardware.motor(hw, "shooterRight", true);
        this.gunner = driverKit.p2();
    }

    @Override
    public void onTeleopLoop(LoopClock clock) {
        double trigger = gunner.rightTrigger().get();
        double power = trigger;  // simple: power = trigger position

        left.setPower(power);
        right.setPower(power);

        lastPower = power;

        telemetry.addData("Shooter power", "%.2f", power);
    }

    @Override
    public void onStop() {
        left.setPower(0.0);
        right.setPower(0.0);
    }
}
```

You can swap this implementation out later for one that uses a velocity plant and an interpolation table.

---

## 7. PhoenixRobot with Subsystems

Once you have subsystems, `PhoenixRobot` becomes a small “owner” that wires them together and forwards lifecycle calls.

```java
package edu.ftcphoenix.robots.phoenix;

import com.qualcomm.robotcore.hardware.HardwareMap;

import org.firstinspires.ftc.robotcore.external.Telemetry;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import edu.ftcphoenix.fw.input.DriverKit;
import edu.ftcphoenix.fw.robot.Subsystem;
import edu.ftcphoenix.fw.util.LoopClock;
import edu.ftcphoenix.robots.phoenix.subsystem.DriveSubsystem;
import edu.ftcphoenix.robots.phoenix.subsystem.ShooterSubsystem;
import edu.ftcphoenix.robots.phoenix.subsystem.VisionSubsystem;

public final class PhoenixRobot {

    private final DriverKit driverKit;
    private final Telemetry telemetry;

    private final List<Subsystem> subsystems = new ArrayList<>();

    public PhoenixRobot(HardwareMap hw, DriverKit driverKit, Telemetry telemetry) {
        this.driverKit = driverKit;
        this.telemetry = telemetry;

        var scoringTags = Set.of(1, 2, 3);

        var vision = new VisionSubsystem(hw, telemetry, scoringTags);
        var drive = new DriveSubsystem(hw, driverKit, telemetry, vision.sensor(), scoringTags);
        var shooter = new ShooterSubsystem(hw, driverKit, telemetry);

        subsystems.add(vision);
        subsystems.add(drive);
        subsystems.add(shooter);
    }

    public void onTeleopInit() {
        for (Subsystem s : subsystems) {
            s.onTeleopInit();
        }
    }

    public void onTeleopLoop(LoopClock clock) {
        for (Subsystem s : subsystems) {
            s.onTeleopLoop(clock);
        }
        telemetry.update();
    }

    public void onAutoInit() {
        for (Subsystem s : subsystems) {
            s.onAutoInit();
        }
    }

    public void onAutoLoop(LoopClock clock) {
        for (Subsystem s : subsystems) {
            s.onAutoLoop(clock);
        }
        telemetry.update();
    }

    public void onStop() {
        for (Subsystem s : subsystems) {
            s.onStop();
        }
    }
}
```

Now students mostly learn:

* How to write a `Subsystem`.
* How to wire it into `PhoenixRobot`.
* How to use Phoenix helpers (`Drives`, `Tags`, `TagAim`, `FtcHardware`) instead of touching FTC SDK classes directly.

---

## 8. Where to Go Next

Once you have this basic structure working, you can:

* Add more subsystems (intake, transfer, arm, etc.).
* Replace power-based mechanisms with velocity or position control using `FtcPlants` and `Plant`.
* Add simple tasks / sequences (e.g., “one-button shoot” that runs shooter + feeder + pusher) using the Phoenix task framework (`Task`, `TaskRunner`, `DriveTasks`, `PlantTasks`).
* Build Auto paths that reuse the same subsystems.

The structure stays the same:

* Thin OpMode shells (`PhoenixTeleOp`, `PhoenixAuto`).
* A `PhoenixRobot` that owns subsystems.
* Subsystems that own hardware and behavior.
* Phoenix framework providing the plumbing so you can focus on **how the robot should play the game**.

---

## 9. Going Further: Tasks & Macros (Optional)

Once you are comfortable with TeleOp and subsystems, you can use Phoenix **tasks** to build small "macros" and multi-step routines.

Tasks live in the `fw.task` package and are used for behaviors that take more than one loop to complete, such as:

* Driving in a short scripted path in TeleOp when a button is pressed.
* Running a shooter sequence (spin up → feed → stop).
* Moving a mechanism to a target and waiting until it reaches `atSetpoint()`.

You will mostly interact with tasks through **helpers**, not by writing your own `Task` implementations:

* `fw.drive.DriveTasks`

    * `driveForSeconds(...)` – drive with a fixed `DriveSignal` for some time, then stop.
* `fw.actuation.PlantTasks`

    * `holdForSeconds(...)` – hold a plant (like an intake or shooter) at a target for some time.
    * `goToSetpointAndWait(...)` – move a plant to a setpoint and finish when it reports `atSetpoint()`.

A typical TeleOp pattern is:

1. Create a `TaskRunner` field in your OpMode.
2. Build one or more `Task` objects (often using `DriveTasks` / `PlantTasks`).
3. Use `Bindings` to start a macro when a gamepad button is pressed.
4. Call `taskRunner.update(clock)` in `loop()`.
5. If no macro is active, fall back to normal manual control.

For a concrete example, see `fw.examples.TeleOpMacroDrive`, where **button Y** starts a simple drive macro built from `DriveTasks.driveForSeconds(...)`, and **button B** cancels it.

For more details and code snippets, see the **"Phoenix Tasks & Macros Quickstart"** guide, which is meant to be read *after* this Beginner’s Guide.

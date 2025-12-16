package edu.ftcphoenix.fw.examples;

import com.qualcomm.robotcore.eventloop.opmode.Disabled;
import com.qualcomm.robotcore.eventloop.opmode.OpMode;
import com.qualcomm.robotcore.eventloop.opmode.TeleOp;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import edu.ftcphoenix.fw.actuation.Actuators;
import edu.ftcphoenix.fw.actuation.Plant;
import edu.ftcphoenix.fw.adapters.ftc.FtcVision;
import edu.ftcphoenix.fw.drive.DriveSignal;
import edu.ftcphoenix.fw.drive.DriveSource;
import edu.ftcphoenix.fw.drive.Drives;
import edu.ftcphoenix.fw.drive.MecanumDrivebase;
import edu.ftcphoenix.fw.drive.source.GamepadDriveSource;
import edu.ftcphoenix.fw.input.Gamepads;
import edu.ftcphoenix.fw.input.binding.Bindings;
import edu.ftcphoenix.fw.sensing.AprilTagObservation;
import edu.ftcphoenix.fw.sensing.AprilTagSensor;
import edu.ftcphoenix.fw.sensing.CameraMountConfig;
import edu.ftcphoenix.fw.sensing.CameraMountLogic;
import edu.ftcphoenix.fw.sensing.TagAim;
import edu.ftcphoenix.fw.sensing.TagTarget;
import edu.ftcphoenix.fw.util.InterpolatingTable1D;
import edu.ftcphoenix.fw.util.LoopClock;

/**
 * <h1>Example 05: Shooter + TagAim + Vision Distance</h1>
 *
 * <p>This example combines three ideas:</p>
 *
 * <ol>
 *   <li><b>Mecanum drive</b> using {@link Drives#mecanum} +
 *       {@link GamepadDriveSource} (same as Example 01).</li>
 *   <li><b>Tag-based auto-aim</b> using {@link TagAim#teleOpAim}:
 *     <ul>
 *       <li>Hold a button to override omega and face a scoring AprilTag.</li>
 *       <li>Axial/lateral motion still come from the driver.</li>
 *     </ul>
 *   </li>
 *   <li><b>Shooter velocity from AprilTag distance</b>:
 *     <ul>
 *       <li>Use an {@link AprilTagSensor} created by {@link FtcVision#aprilTags}.</li>
 *       <li>Read {@link AprilTagObservation#cameraRangeInches()}.</li>
 *       <li>Use an {@link InterpolatingTable1D} to map
 *           {@code distance → shooter velocity} (native units).</li>
 *     </ul>
 *   </li>
 * </ol>
 *
 * <h2>Camera offset note</h2>
 * <p>
 * This example uses {@link CameraMountConfig} so TagAim aims the <b>robot center</b>
 * at the tag even if the camera is not mounted at the robot center.
 * </p>
 */
@TeleOp(name = "FW Ex 05: Shooter TagAim Vision", group = "Framework Examples")
@Disabled
public final class TeleOp_05_ShooterTagAimVision extends OpMode {

    // ----------------------------------------------------------------------
    // Calibration: distance (inches) → shooter velocity (native units)
    // ----------------------------------------------------------------------

    private static final InterpolatingTable1D SHOOTER_VELOCITY_TABLE =
            InterpolatingTable1D.ofSortedPairs(
                    // Example calibration data (distanceInches, velocityNative):
                    24.0, 3500.0,
                    30.0, 3600.0,
                    36.0, 3700.0,
                    42.0, 3800.0,
                    48.0, 3900.0
            );

    // ----------------------------------------------------------------------
    // Tag age constraint
    // ----------------------------------------------------------------------

    private static final double MAX_TAG_AGE_SEC = 0.5;

    // ----------------------------------------------------------------------
    // Tag IDs we care about (example values; adjust per game) – Java 8 style
    // ----------------------------------------------------------------------

    private static final Set<Integer> SCORING_TAG_IDS;

    static {
        HashSet<Integer> ids = new HashSet<Integer>();
        ids.add(1);
        ids.add(2);
        ids.add(3);
        SCORING_TAG_IDS = Collections.unmodifiableSet(ids);
    }

    // ----------------------------------------------------------------------
    // Hardware names
    // ----------------------------------------------------------------------

    private static final String HW_SHOOTER_LEFT = "shooterLeftMotor";
    private static final String HW_SHOOTER_RIGHT = "shooterRightMotor";

    // ----------------------------------------------------------------------
    // Framework plumbing
    // ----------------------------------------------------------------------

    private final LoopClock clock = new LoopClock();

    private Gamepads gamepads;
    private Bindings bindings;

    private MecanumDrivebase drivebase;
    private DriveSource baseDrive;
    private DriveSource driveWithAim;

    private AprilTagSensor tagSensor;
    private TagTarget scoringTarget;

    private CameraMountConfig cameraMount;

    private Plant shooter;

    private boolean shooterEnabled = false;

    private DriveSignal lastDrive = DriveSignal.zero();

    // For telemetry about the last tag observation used for shooter control.
    private boolean lastHasTarget = false;
    private double lastTagRangeInches = 0.0;
    private double lastCameraBearingRad = 0.0;
    private double lastRobotBearingRad = 0.0;
    private double lastTagAgeSec = 0.0;
    private int lastTagId = -1;

    private double lastShooterTargetVel = 0.0;

    @Override
    public void init() {
        // 1) Inputs
        gamepads = Gamepads.create(gamepad1, gamepad2);
        bindings = new Bindings();

        // 2) Drive wiring: mecanum + sticks.
        drivebase = Drives.mecanum(hardwareMap);
        baseDrive = GamepadDriveSource.teleOpMecanumSlowRb(gamepads);

        // 3) Tag sensor: real FTC VisionPortal + AprilTagProcessor adapter.
        //
        // NOTE: Replace "Webcam 1" with your actual camera name in the Robot Configuration.
        tagSensor = FtcVision.aprilTags(hardwareMap, "Webcam 1");

        // Track scoring tags with a freshness window.
        scoringTarget = new TagTarget(tagSensor, SCORING_TAG_IDS, MAX_TAG_AGE_SEC);

        // 3b) Camera mount: robot→camera extrinsics (Phoenix axes: +X forward, +Y left, +Z up).
        //
        // IMPORTANT: Update these values for your robot.
        // Example: camera is 6" forward, 3" to the RIGHT (so y = -3), 8" up, facing forward.
        cameraMount = CameraMountConfig.of(
                /*xInches=*/6.0,
                /*yInches=*/-3.0,
                /*zInches=*/8.0,
                /*yawRad=*/0.0,
                /*pitchRad=*/0.0,
                /*rollRad=*/0.0
        );

        // Wrap baseDrive with TagAim: hold left bumper to auto-aim omega.
        //
        // This overload uses cameraMount so the ROBOT CENTER faces the tag, not just the camera.
        driveWithAim = TagAim.teleOpAim(
                baseDrive,
                gamepads.p1().leftBumper(),
                scoringTarget,
                cameraMount
        );

        // 4) Shooter wiring using Actuators.
        shooter = Actuators.plant(hardwareMap)
                .motorPair(HW_SHOOTER_LEFT, false,
                        HW_SHOOTER_RIGHT, true)
                .velocity(/*toleranceNative=*/100.0)
                .build();

        shooter.setTarget(0.0);

        // 5) Bindings: shooter toggle.
        bindings.onPress(
                gamepads.p1().a(),
                new Runnable() {
                    @Override
                    public void run() {
                        shooterEnabled = !shooterEnabled;
                    }
                }
        );

        telemetry.addLine("FW Example 05: Shooter TagAim Vision");
        telemetry.addLine("Drive: mecanum + TagAim (hold LB to auto-aim)");
        telemetry.addLine("Shooter: A = toggle on/off");
        telemetry.addLine("TagAim: mount-aware (robot center aims at tag)");
        telemetry.update();
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

        // 2) Sense (inputs & sensors)
        gamepads.update(clock);
        scoringTarget.update(clock);      // TagTarget → AprilTagSensor.best(...)

        // 3) Decide (high-level logic)

        // User-input driven decisions (e.g., shooterEnabled toggle on A)
        bindings.update(clock);

        // Vision-based shooter target decision
        AprilTagObservation obs = scoringTarget.last();
        lastHasTarget = obs.hasTarget;
        lastTagRangeInches = obs.cameraRangeInches();
        lastCameraBearingRad = obs.cameraBearingRad();
        lastRobotBearingRad = obs.hasTarget
                ? CameraMountLogic.robotBearingRad(obs, cameraMount)
                : 0.0;
        lastTagAgeSec = obs.ageSec;
        lastTagId = obs.id;

        if (shooterEnabled && obs.hasTarget) {
            double targetVel = SHOOTER_VELOCITY_TABLE.interpolate(obs.cameraRangeInches());
            lastShooterTargetVel = targetVel;
            shooter.setTarget(targetVel);
        } else {
            lastShooterTargetVel = 0.0;
            shooter.setTarget(0.0);
        }

        // 4) Control / Actuate (subsystems)

        // Drive: TagAim-wrapped drive source
        DriveSignal cmd = driveWithAim.get(clock).clamped();
        lastDrive = cmd;

        drivebase.update(clock);
        drivebase.drive(cmd);

        // Shooter plant executes whatever target we just decided above
        shooter.update(dtSec);

        // 5) Report (telemetry only, no behavior changes)
        telemetry.addLine("FW Example 05: Shooter TagAim Vision");

        telemetry.addLine("Drive (axial / lateral / omega)")
                .addData("axial", lastDrive.axial)
                .addData("lateral", lastDrive.lateral)
                .addData("omega", lastDrive.omega);

        telemetry.addLine("Tag observation")
                .addData("hasTarget", lastHasTarget)
                .addData("id", lastTagId)
                .addData("rangeIn", lastTagRangeInches)
                .addData("cameraBearingRad", lastCameraBearingRad)
                .addData("robotBearingRad", lastRobotBearingRad)
                .addData("ageSec", lastTagAgeSec);

        telemetry.addLine("Shooter")
                .addData("enabled", shooterEnabled)
                .addData("targetVelNative", lastShooterTargetVel);

        telemetry.update();
    }

    @Override
    public void stop() {
        shooterEnabled = false;
        shooter.setTarget(0.0);
        shooter.update(0.0);
        drivebase.stop();
    }
}

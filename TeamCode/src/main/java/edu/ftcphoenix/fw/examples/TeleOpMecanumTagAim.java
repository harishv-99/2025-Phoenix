package edu.ftcphoenix.fw.examples;

import com.qualcomm.robotcore.eventloop.opmode.Disabled;
import com.qualcomm.robotcore.eventloop.opmode.OpMode;
import com.qualcomm.robotcore.eventloop.opmode.TeleOp;

import java.util.Set;

import edu.ftcphoenix.fw.drive.DriveSignal;
import edu.ftcphoenix.fw.drive.DriveSource;
import edu.ftcphoenix.fw.drive.Drives;
import edu.ftcphoenix.fw.drive.MecanumConfig;
import edu.ftcphoenix.fw.drive.MecanumDrivebase;
import edu.ftcphoenix.fw.drive.source.StickDriveSource;
import edu.ftcphoenix.fw.input.Gamepads;
import edu.ftcphoenix.fw.sensing.AprilTagObservation;
import edu.ftcphoenix.fw.sensing.AprilTagSensor;
import edu.ftcphoenix.fw.sensing.TagAim;
import edu.ftcphoenix.fw.sensing.Tags;
import edu.ftcphoenix.fw.util.LoopClock;

/**
 * Example TeleOp showing how to:
 * <ul>
 *   <li>Wire a mecanum drive using Phoenix framework helpers.</li>
 *   <li>Read AprilTags (ID, distance, bearing) from a webcam.</li>
 *   <li>Hold a button to auto-aim the robot at scoring tags.</li>
 * </ul>
 *
 * <h2>Input mapping (Driver 1)</h2>
 *
 * <ul>
 *   <li>Left stick X: strafe left/right.</li>
 *   <li>Left stick Y: forward/back (up is +).</li>
 *   <li>Right stick X: rotate (when not auto-aiming).</li>
 *   <li>Left bumper: auto-aim at tags 1, 2, or 3 while held.</li>
 *   <li>Right bumper: slow mode (scales all axes while held).</li>
 * </ul>
 *
 * <h2>Design notes</h2>
 *
 * <ul>
 *   <li>All stick shaping (deadband/expo, etc.) lives in {@link StickDriveSource},
 *       configured via {@code StickConfig}.</li>
 *   <li>The base TeleOp mapping is created via
 *       {@link StickDriveSource#teleOpMecanumStandard(Gamepads)}, which:
 *       <ul>
 *         <li>uses P1 left stick X/Y for lateral/axial,</li>
 *         <li>uses P1 right stick X for rotation,</li>
 *         <li>applies Phoenix default deadband/expo/scale, and</li>
 *         <li>enables slow mode on P1 right bumper at 30% speed.</li>
 *       </ul>
 *   </li>
 *   <li>All holonomic math, motor mixing, and optional time-based smoothing
 *       live in {@link MecanumDrivebase}, configured via {@link MecanumConfig}.
 *       This example enables modest lateral rate limiting to make strafing
 *       less “twitchy.”</li>
 *   <li>AprilTag details (VisionPortal, AprilTagProcessor) are handled by
 *       the FTC adapter layer and exposed via {@link AprilTagSensor} returned
 *       by {@link Tags#aprilTags}.</li>
 *   <li>Tag-based aiming PID is encapsulated in
 *       {@link edu.ftcphoenix.fw.sensing.TagAimController} and used through
 *       {@link TagAim#teleOpAim(DriveSource, edu.ftcphoenix.fw.input.Button, AprilTagSensor, Set)}.</li>
 * </ul>
 *
 * <p>This OpMode is marked {@link Disabled} so it does not appear by default
 * in the driver station menu. To use it on your robot, remove the
 * {@link Disabled} annotation and adjust hardware names and tag IDs as needed.</p>
 */
@Disabled
@TeleOp(name = "FW Example: Mecanum + Tag Aim", group = "Phoenix")
public final class TeleOpMecanumTagAim extends OpMode {

    // --- Hardware names (match these to your configuration) ---
    private static final String HW_FL = "fl";
    private static final String HW_FR = "fr";
    private static final String HW_BL = "bl";
    private static final String HW_BR = "br";

    // Webcam name from Robot Configuration
    private static final String HW_WEBCAM = "Webcam 1";

    // Tag IDs we care about (example values: scoring tags)
    private static final Set<Integer> SCORING_TAGS = Set.of(1, 2, 3);

    // Inputs & drive
    private Gamepads gamepads;
    private MecanumDrivebase drivebase;
    private DriveSource drive; // base drive wrapped with TagAim

    // AprilTags
    private AprilTagSensor tags;

    // Loop timing
    private final LoopClock clock = new LoopClock();

    @Override
    public void init() {
        // 1) Inputs
        gamepads = Gamepads.create(gamepad1, gamepad2);

        // 2) Drive configuration
        //
        // Start from Phoenix defaults and optionally enable lateral rate limiting.
        // Setting maxLateralRatePerSec > 0 makes strafing less “twitchy”
        // without affecting axial or rotational response.
        MecanumConfig driveCfg = MecanumConfig.defaults();
        driveCfg.maxLateralRatePerSec = 4.0; // try 0.0 to disable smoothing

        // 3) Drivebase via helper: no explicit MotorOutput wiring here.
        //
        // This assumes the standard inversion pattern:
        //   - left side normal,
        //   - right side inverted.
        // If your robot uses a different pattern, use the full overload of
        // Drives.mecanum(...) that exposes per-motor inversion flags.
        drivebase = Drives.mecanum(
                hardwareMap,
                HW_FL,
                HW_FR,
                HW_BL,
                HW_BR,
                driveCfg
        );

        // 4) Base mecanum mapping from Gamepads.
        //
        // StickDriveSource.teleOpMecanumStandard(...) uses:
        //   - P1 left stick X for lateral,
        //   - P1 left stick Y for axial,
        //   - P1 right stick X for omega,
        //   - StickConfig.defaults() for shaping,
        //   - P1 right bumper as slow-mode at 30% speed.
        DriveSource sticks = StickDriveSource.teleOpMecanumStandard(gamepads);

        // 5) AprilTags: create an AprilTagSensor from the webcam.
        tags = Tags.aprilTags(hardwareMap, HW_WEBCAM);

        // 6) Wrap base drive with tag-based auto-aim.
        // While left bumper is held, the robot will automatically rotate
        // to face the closest tag with ID in SCORING_TAGS.
        drive = TagAim.teleOpAim(
                sticks,
                gamepads.p1().leftBumper(), // aim button
                tags,
                SCORING_TAGS
        );

        telemetry.addLine("FW Mecanum + Tag Aim: init complete");
        telemetry.update();
    }

    @Override
    public void start() {
        clock.reset(getRuntime());
    }

    @Override
    public void loop() {
        // 1) Update timing & inputs
        clock.update(getRuntime());
        double dtSec = clock.dtSec();

        gamepads.update(dtSec);

        // 2) Compute drive signal (includes auto-aim when LB is held)
        DriveSignal cmd = drive.get(clock);

        // 3) Apply to drivebase (MecanumDrivebase handles scaling & smoothing)
        drivebase.drive(cmd);
        drivebase.update(clock);

        // 4) AprilTag telemetry (ID, distance, bearing)
        AprilTagObservation obs = tags.best(SCORING_TAGS, 0.3);

        if (obs.hasTarget) {
            telemetry.addLine("Tag")
                    .addData("id", obs.id)
                    .addData("range (in)", "%.1f", obs.rangeInches)
                    .addData("bearing (deg)", "%.1f", Math.toDegrees(obs.bearingRad));
        } else {
            telemetry.addLine("Tag: none (or too old)");
        }

        telemetry.addLine("Drive")
                .addData("axial", cmd.axial)
                .addData("lateral", cmd.lateral)
                .addData("omega", cmd.omega);
        telemetry.update();
    }

    @Override
    public void stop() {
        drivebase.stop();
    }
}

package edu.ftcphoenix.fw.examples;

import com.qualcomm.robotcore.eventloop.opmode.Disabled;
import com.qualcomm.robotcore.eventloop.opmode.OpMode;
import com.qualcomm.robotcore.eventloop.opmode.TeleOp;

import java.util.Set;

import edu.ftcphoenix.fw.drive.DriveSignal;
import edu.ftcphoenix.fw.drive.DriveSource;
import edu.ftcphoenix.fw.drive.Drives;
import edu.ftcphoenix.fw.drive.MecanumDrivebase;
import edu.ftcphoenix.fw.drive.source.StickDriveSource;
import edu.ftcphoenix.fw.input.DriverKit;
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
 * </ul>
 *
 * <h2>Design notes</h2>
 *
 * <ul>
 *   <li>All drive shaping (deadband/expo, etc.) lives in {@link StickDriveSource}.</li>
 *   <li>All holonomic math and motor mixing is in {@link MecanumDrivebase} via
 *       the {@link Drives} helper.</li>
 *   <li>AprilTag details (VisionPortal, AprilTagProcessor) are handled by
 *       {@code FtcVision} and exposed via {@link AprilTagSensor} returned
 *       by {@link Tags#aprilTags}.</li>
 *   <li>Tag-based aiming PID is encapsulated in
 *       {@link edu.ftcphoenix.fw.sensing.TagAimController} and used through
 *       {@link TagAim#teleOpAim(DriveSource, edu.ftcphoenix.fw.input.Button, AprilTagSensor, Set)}.</li>
 * </ul>
 *
 * <p>This OpMode is marked {@link Disabled} so it does not appear by default
 * in the driver station menu. To use it on your robot, remove the
 * {@link Disabled} annotation and adjust hardware names as needed.</p>
 */
@Disabled
@TeleOp(name = "FW Example: Mecanum + Tag Aim", group = "Phoenix")
public final class TeleOpMecanumTagAim extends OpMode {

    // --- Hardware names (match these to your configuration) ---
    private static final String HW_FL = "fl";
    private static final String HW_FR = "fr";
    private static final String HW_BL = "bl";
    private static final String HW_BR = "br";

    // Webcam name from the config
    private static final String HW_WEBCAM = "Webcam 1";

    // Tag IDs we care about (example values: scoring tags)
    private static final Set<Integer> SCORING_TAGS = Set.of(1, 2, 3);

    // Input & drive plumbing
    private Gamepads gamepads;
    private DriverKit driverKit;
    private MecanumDrivebase drivebase;
    private DriveSource drive; // wrapped with TagAim

    // AprilTags
    private AprilTagSensor tags;

    // Loop timing
    private final LoopClock clock = new LoopClock();

    @Override
    public void init() {
        // 1) Inputs
        gamepads = Gamepads.create(gamepad1, gamepad2);
        driverKit = DriverKit.of(gamepads);

        // 2) Drivebase via helper: no explicit MotorOutput wiring here.
        // Adjust the inversion flags to match your robot if needed.
        drivebase = Drives
                .mecanum(hardwareMap)
                .names(HW_FL, HW_FR, HW_BL, HW_BR)
                .invertFrontRight()
                .invertBackRight()
                .build();

        // 3) Base mecanum mapping from DriverKit sticks
        StickDriveSource.Params params = new StickDriveSource.Params();
        // You can tweak params (deadband, expo, etc.) here if desired.
        StickDriveSource sticks = new StickDriveSource(driverKit.p1(), params);

        // 4) AprilTags: create an AprilTagSensor from the webcam
        tags = Tags.aprilTags(hardwareMap, HW_WEBCAM);

        // 5) Wrap base drive with tag-based auto-aim.
        // While left bumper is held, the robot will automatically rotate
        // to face the closest tag with ID in SCORING_TAGS.
        drive = TagAim.teleOpAim(
                sticks,
                driverKit.p1().leftBumper(),
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

        // 3) Apply to drivebase
        drivebase.drive(cmd);

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

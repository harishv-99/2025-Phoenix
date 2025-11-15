package edu.ftcphoenix.robots.phoenix.subsystem;

import com.qualcomm.robotcore.hardware.HardwareMap;

import org.firstinspires.ftc.robotcore.external.Telemetry;

import java.util.Set;

import edu.ftcphoenix.fw.robot.Subsystem;
import edu.ftcphoenix.fw.sensing.AprilTagObservation;
import edu.ftcphoenix.fw.sensing.AprilTagSensor;
import edu.ftcphoenix.fw.sensing.Tags;
import edu.ftcphoenix.fw.util.LoopClock;

/**
 * VisionSubsystem: owns the AprilTag sensor and basic tag telemetry.
 *
 * <h2>Role</h2>
 * <p>This subsystem is responsible for:
 * <ul>
 *   <li>Constructing an {@link AprilTagSensor} from the FTC camera.</li>
 *   <li>Providing the sensor and tag ID set to other subsystems (e.g. drive).</li>
 *   <li>(Optionally) printing tag info to driver telemetry in TeleOp/Auto.</li>
 * </ul>
 *
 * <p>It does <strong>not</strong> contain aiming logic; that is handled by
 * {@code TagAim} and {@code TagAimController} in the sensing package. This
 * subsystem is a thin owner/wrapper around the vision sensor.
 */
public final class VisionSubsystem implements Subsystem {

    private final Telemetry telemetry;
    private final AprilTagSensor tags;
    private final Set<Integer> scoringTags;

    /**
     * Construct the vision subsystem.
     *
     * @param hw        FTC hardware map (used to locate the camera)
     * @param telemetry OpMode telemetry for optional debug output
     */
    public VisionSubsystem(HardwareMap hw, Telemetry telemetry) {
        this.telemetry = telemetry;

        // Create an AprilTag sensor using the beginner-friendly Tags helper.
        // "Webcam 1" should match the configured camera name in the config.
        this.tags = Tags.aprilTags(hw, "Webcam 1");

        // Tag IDs this robot cares about (e.g., scoring targets).
        // Adjust this set based on the current game.
        this.scoringTags = Set.of(1, 2, 3);
    }

    /**
     * Expose the underlying {@link AprilTagSensor} for other subsystems,
     * such as DriveSubsystem with {@code TagAim}.
     */
    public AprilTagSensor getTagSensor() {
        return tags;
    }

    /**
     * Tag IDs this robot is interested in (e.g., scoring tags).
     *
     * <p>Drive or auto code can use this set when choosing which tags
     * to aim at or use for distance estimation.
     */
    public Set<Integer> getScoringTagIds() {
        return scoringTags;
    }

    // ------------------------------------------------------------------------
    // Subsystem lifecycle
    // ------------------------------------------------------------------------

    @Override
    public void onTeleopInit() {
        // No special initialization required for vision at TeleOp start.
    }

    @Override
    public void onTeleopLoop(LoopClock clock) {
        // Optional driver-facing telemetry about the best scoring tag.
        AprilTagObservation obs = tags.best(scoringTags, 0.30);
        if (obs.hasTarget) {
            telemetry.addData("Tag id", obs.id);
            telemetry.addData("Tag range (in)", "%.1f", obs.rangeInches);
            telemetry.addData("Tag bearing (deg)", "%.1f", Math.toDegrees(obs.bearingRad));
        } else {
            telemetry.addLine("No scoring tag visible");
        }
    }

    @Override
    public void onAutoInit() {
        // Nothing special for autonomous start at the moment.
    }

    @Override
    public void onAutoLoop(LoopClock clock) {
        // Optionally mirror the same telemetry in autonomous, or leave empty.
        AprilTagObservation obs = tags.best(scoringTags, 0.30);
        if (obs.hasTarget) {
            telemetry.addData("Tag id", obs.id);
            telemetry.addData("Tag range (in)", "%.1f", obs.rangeInches);
            telemetry.addData("Tag bearing (deg)", "%.1f", Math.toDegrees(obs.bearingRad));
        } else {
            telemetry.addLine("No scoring tag visible");
        }
    }

    @Override
    public void onStop() {
        // No explicit shutdown required for vision here; the FTC SDK
        // will handle webcam/VisionPortal shutdown when the OpMode ends.
    }
}

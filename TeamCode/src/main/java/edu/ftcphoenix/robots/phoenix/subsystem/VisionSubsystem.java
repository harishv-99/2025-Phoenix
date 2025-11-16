package edu.ftcphoenix.robots.phoenix.subsystem;

import com.qualcomm.robotcore.hardware.HardwareMap;

import org.firstinspires.ftc.robotcore.external.Telemetry;

import java.util.Collections;
import java.util.Set;

import edu.ftcphoenix.fw.robot.Subsystem;
import edu.ftcphoenix.fw.sensing.AprilTagObservation;
import edu.ftcphoenix.fw.sensing.AprilTagSensor;
import edu.ftcphoenix.fw.sensing.Tags;
import edu.ftcphoenix.fw.util.LoopClock;

/**
 * Owns the AprilTag sensor and exposes helpers for scoring tags.
 */
public final class VisionSubsystem implements Subsystem {

    private static final double DEFAULT_MAX_AGE_SEC = 0.30;

    private final Telemetry telemetry;
    private final AprilTagSensor tags;
    private final Set<Integer> scoringTagIds;

    public VisionSubsystem(HardwareMap hw,
                           Telemetry telemetry,
                           Set<Integer> scoringTagIds) {
        this.telemetry = telemetry;
        this.tags = Tags.aprilTags(hw, "Webcam 1"); // TODO: match your config name
        this.scoringTagIds = Collections.unmodifiableSet(scoringTagIds);
    }

    /**
     * Expose sensor so other subsystems (drive) can use TagAim.
     */
    public AprilTagSensor sensor() {
        return tags;
    }

    /**
     * Tag IDs representing scoring goals.
     */
    public Set<Integer> getScoringTagIds() {
        return scoringTagIds;
    }

    /**
     * Best scoring tag within the given age limit.
     */
    public AprilTagObservation getBestScoringTag(double maxAgeSec) {
        return tags.best(scoringTagIds, maxAgeSec);
    }

    /**
     * Best scoring tag with a default freshness window.
     */
    public AprilTagObservation getBestScoringTag() {
        return getBestScoringTag(DEFAULT_MAX_AGE_SEC);
    }

    @Override
    public void onTeleopLoop(LoopClock clock) {
        // Optional debugging; safe to comment out later.
        AprilTagObservation obs = getBestScoringTag(DEFAULT_MAX_AGE_SEC);
        if (obs.hasTarget) {
            telemetry.addData("Vision/tagId", obs.id);
            telemetry.addData("Vision/rangeIn", "%.1f", obs.rangeInches);
            telemetry.addData("Vision/bearingDeg", "%.1f",
                    Math.toDegrees(obs.bearingRad));
        } else {
            telemetry.addLine("Vision: no scoring tag visible");
        }
    }
}

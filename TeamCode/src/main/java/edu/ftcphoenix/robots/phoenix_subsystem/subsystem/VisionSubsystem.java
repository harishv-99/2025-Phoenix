package edu.ftcphoenix.robots.phoenix_subsystem.subsystem;

import com.qualcomm.robotcore.hardware.HardwareMap;

import java.util.Collections;
import java.util.Set;

import edu.ftcphoenix.fw.robot.Subsystem;
import edu.ftcphoenix.fw.sensing.AprilTagObservation;
import edu.ftcphoenix.fw.sensing.AprilTagSensor;
import edu.ftcphoenix.fw.debug.DebugSink;
import edu.ftcphoenix.fw.util.LoopClock;

/**
 * Owns the AprilTag sensor and exposes helpers for scoring tags.
 */
public final class VisionSubsystem implements Subsystem {

    private static final double DEFAULT_MAX_AGE_SEC = 0.30;

    private final AprilTagSensor tags;
    private final Set<Integer> scoringTagIds;

    private AprilTagObservation lastObs = null;

    public VisionSubsystem(HardwareMap hw,
                           Set<Integer> scoringTagIds) {
        this.tags = null;
//        this.tags = Tags.aprilTags(hw, "Webcam 1"); // TODO: match your config name
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
        lastObs = getBestScoringTag(DEFAULT_MAX_AGE_SEC);
    }

    public void debugDump(DebugSink dbg, String prefix) {
        // Optional debugging; safe to comment out later.
        if (lastObs != null && lastObs.hasTarget) {
            dbg.addData("Vision/tagId", lastObs.id);
            dbg.addData("Vision/rangeIn", "%.1f", lastObs.rangeInches);
            dbg.addData("Vision/bearingDeg", "%.1f",
                    Math.toDegrees(lastObs.bearingRad));
        } else {
            dbg.addLine("Vision: no scoring tag visible");
        }
    }
}

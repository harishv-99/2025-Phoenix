package edu.ftcphoenix.fw.sensing.vision.apriltag;

import java.util.Objects;
import java.util.Set;

import edu.ftcphoenix.fw.core.debug.DebugSink;
import edu.ftcphoenix.fw.drive.assist.BearingSource.BearingSample;
import edu.ftcphoenix.fw.core.time.LoopClock;
import edu.ftcphoenix.fw.sensing.vision.CameraMountConfig;
import edu.ftcphoenix.fw.sensing.vision.CameraMountLogic;

/**
 * Tracks the "best" AprilTag target across loops for a specific set of IDs.
 *
 * <h2>Role</h2>
 * <p>
 * {@code TagTarget} wraps an {@link AprilTagSensor} and remembers the latest
 * {@link AprilTagObservation} that matches a set of tag IDs and a freshness constraint.
 * </p>
 *
 * <p>
 * It is intended to be the single place in your robot code that answers:
 * </p>
 *
 * <ul>
 *   <li>Which tag are we currently tracking?</li>
 *   <li>How old is the observation?</li>
 *   <li>What is the bearing and range to the tag?</li>
 * </ul>
 *
 * <h2>Frame &amp; sign conventions</h2>
 * <p>
 * {@link AprilTagObservation} stores {@code cameraToTagPose} (camera→tag) in Phoenix framing:
 * +X forward, +Y left, +Z up.
 * </p>
 *
 * <ul>
 *   <li><b>Camera-centric bearing</b> is computed from {@code cameraToTagPose} as
 *       {@code atan2(left, forward)} in the camera frame:
 *       {@code bearingRad > 0} means the tag is to the <b>left</b>.</li>
 *   <li><b>Robot-centric bearing</b> (optional) accounts for camera offset using
 *       {@link CameraMountConfig} so the <b>robot center</b> faces the tag.</li>
 * </ul>
 *
 * <h2>Typical usage</h2>
 *
 * <pre>{@code
 * // Wiring in init():
 * AprilTagSensor tagSensor = FtcVision.aprilTags(hardwareMap, "Webcam 1");
 * TagTarget target = new TagTarget(tagSensor, Set.of(1, 2, 3), 0.5);
 *
 * // In loop():
 * target.update(clock);
 * if (target.hasTarget()) {
 *     double cameraBearing = target.bearingRad();
 *     double rangeLosInches = target.lineOfSightRangeInches();
 * }
 * }</pre>
 *
 * <h2>Per-cycle idempotency</h2>
 * <p>
 * {@link #update(LoopClock)} is idempotent by {@link LoopClock#cycle()} to prevent
 * accidental double polling (e.g., multiple layers calling update in the same loop).
 * </p>
 */
public final class TagTarget {

    private final AprilTagSensor sensor;
    private final Set<Integer> idsOfInterest;
    private final double maxAgeSec;

    // Last observation returned by the sensor for this ID set + age constraint.
    private AprilTagObservation lastObs =
            AprilTagObservation.noTarget(Double.POSITIVE_INFINITY);

    /**
     * Tracks which loop cycle we last updated for, to prevent double-polling in a single cycle.
     */
    private long lastUpdatedCycle = Long.MIN_VALUE;

    /**
     * Create a tag target tracker.
     *
     * @param sensor        AprilTag sensor backing this tracker; must not be null
     * @param idsOfInterest set of tag IDs this tracker cares about; must not be null or empty
     * @param maxAgeSec     maximum acceptable age in seconds; observations older than
     *                      this will be reported as {@code hasTarget == false}
     * @throws NullPointerException     if {@code sensor} or {@code idsOfInterest} is null
     * @throws IllegalArgumentException if {@code idsOfInterest} is empty
     * @throws IllegalArgumentException if {@code maxAgeSec} is negative
     */
    public TagTarget(AprilTagSensor sensor, Set<Integer> idsOfInterest, double maxAgeSec) {
        this.sensor = Objects.requireNonNull(sensor, "sensor is required");
        this.idsOfInterest = Objects.requireNonNull(idsOfInterest, "idsOfInterest is required");
        if (idsOfInterest.isEmpty()) {
            throw new IllegalArgumentException("idsOfInterest must not be empty");
        }
        if (maxAgeSec < 0.0) {
            throw new IllegalArgumentException("maxAgeSec must be non-negative");
        }
        this.maxAgeSec = maxAgeSec;
    }

    /**
     * Update the tracked target using the underlying sensor.
     *
     * <p>
     * Call this once per control loop, before reading {@link #last()},
     * {@link #hasTarget()}, {@link #bearingRad()}, etc.
     * </p>
     *
     * <p>This method is idempotent by {@link LoopClock#cycle()}.</p>
     *
     * @param clock loop clock (must not be {@code null})
     */
    public void update(LoopClock clock) {
        Objects.requireNonNull(clock, "clock is required");

        long c = clock.cycle();
        if (c == lastUpdatedCycle) {
            return;
        }
        lastUpdatedCycle = c;

        lastObs = sensor.best(idsOfInterest, maxAgeSec);
    }

    /**
     * Latest observation from this tracker.
     *
     * @return last observation returned by {@link #update(LoopClock)}, or an initial
     * "no target" observation if {@link #update(LoopClock)} has not yet been called
     */
    public AprilTagObservation last() {
        return lastObs;
    }

    /**
     * Whether the tracker currently has a valid target.
     *
     * @return {@code true} if the latest observation has a target (and met the age constraint)
     */
    public boolean hasTarget() {
        return lastObs.hasTarget;
    }

    /**
     * Camera-centric horizontal bearing to the tracked tag, in radians.
     *
     * <p>
     * This bearing is relative to the camera forward axis (derived from {@code cameraToTagPose}).
     * If your camera is offset and you want the <b>robot center</b> to face the tag,
     * use {@link #robotBearingRad(CameraMountConfig)}.
     * </p>
     *
     * @return bearing in radians (positive = left/CCW). Only meaningful when {@link #hasTarget()} is true.
     */
    public double bearingRad() {
        return lastObs.cameraBearingRad();
    }

    /**
     * Convenience alias for {@link #bearingRad()}.
     *
     * <p>Spells out that this is camera-centric bearing.</p>
     */
    public double cameraBearingRad() {
        return bearingRad();
    }

    /**
     * Robot-centric bearing to the tracked tag, accounting for camera mount offset.
     *
     * <p>
     * This computes robot-centric bearing by applying the mount extrinsics:
     * {@code robotToTagPose = robotToCameraPose.then(cameraToTagPose)}, then computing
     * {@code atan2(left, forward)} in the robot frame.
     * </p>
     *
     * @param cameraMount robot→camera extrinsics; must not be null
     * @return robot-centric bearing (positive = left/CCW). Returns 0 if {@link #hasTarget()} is false.
     */
    public double robotBearingRad(CameraMountConfig cameraMount) {
        Objects.requireNonNull(cameraMount, "cameraMount is required");
        return CameraMountLogic.robotBearingRad(lastObs, cameraMount);
    }

    public boolean isBearingWithin(double toleranceRad) {
        if (toleranceRad < 0.0) {
            throw new IllegalArgumentException("toleranceRad must be non-negative");
        }
        if (!lastObs.hasTarget) {
            return false;
        }
        return Math.abs(lastObs.cameraBearingRad()) <= toleranceRad;
    }

    public boolean isRobotBearingWithin(CameraMountConfig cameraMount, double toleranceRad) {
        Objects.requireNonNull(cameraMount, "cameraMount is required");
        if (toleranceRad < 0.0) {
            throw new IllegalArgumentException("toleranceRad must be non-negative");
        }
        if (!lastObs.hasTarget) {
            return false;
        }
        return Math.abs(CameraMountLogic.robotBearingRad(lastObs, cameraMount)) <= toleranceRad;
    }

    public double rangeInches() {
        double f = lastObs.cameraForwardInches();
        double l = lastObs.cameraLeftInches();
        return Math.sqrt(f * f + l * l);
    }

    public double lineOfSightRangeInches() {
        return lastObs.cameraRangeInches();
    }

    public double maxAgeSec() {
        return maxAgeSec;
    }

    public Set<Integer> idsOfInterest() {
        return idsOfInterest;
    }

    public BearingSample toBearingSample() {
        if (!lastObs.hasTarget) {
            return new BearingSample(false, 0.0);
        }
        return new BearingSample(true, lastObs.cameraBearingRad());
    }

    public BearingSample toRobotBearingSample(CameraMountConfig cameraMount) {
        Objects.requireNonNull(cameraMount, "cameraMount is required");
        return CameraMountLogic.robotBearingSample(lastObs, cameraMount);
    }

    public void debugDump(DebugSink dbg, String prefix) {
        if (dbg == null) {
            return;
        }
        String p = (prefix == null || prefix.isEmpty()) ? "tagTarget" : prefix;

        dbg.addLine(p + ": TagTarget");

        dbg.addData(p + ".ids", idsOfInterest.toString());
        dbg.addData(p + ".maxAgeSec", maxAgeSec);
        dbg.addData(p + ".sensor.class", sensor.getClass().getSimpleName());
        dbg.addData(p + ".lastUpdatedCycle", lastUpdatedCycle);

        AprilTagObservation o = lastObs;
        dbg.addData(p + ".obs.hasTarget", o.hasTarget);
        dbg.addData(p + ".obs.id", o.id);
        dbg.addData(p + ".obs.ageSec", o.ageSec);

        dbg.addData(p + ".obs.cameraForwardInches", o.cameraForwardInches());
        dbg.addData(p + ".obs.cameraLeftInches", o.cameraLeftInches());
        dbg.addData(p + ".obs.cameraUpInches", o.cameraUpInches());

        dbg.addData(p + ".obs.cameraBearingRad", o.cameraBearingRad());
        dbg.addData(p + ".obs.cameraRangeInches", o.cameraRangeInches());
        dbg.addData(p + ".obs.cameraPlanarRangeInches", rangeInches());
    }

    public void debugDump(DebugSink dbg, String prefix, CameraMountConfig cameraMount) {
        if (dbg == null) {
            return;
        }
        Objects.requireNonNull(cameraMount, "cameraMount is required");

        debugDump(dbg, prefix);

        String p = (prefix == null || prefix.isEmpty()) ? "tagTarget" : prefix;
        if (lastObs.hasTarget) {
            dbg.addData(p + ".obs.robotBearingRad", CameraMountLogic.robotBearingRad(lastObs, cameraMount));
        } else {
            dbg.addData(p + ".obs.robotBearingRad", 0.0);
        }
    }
}

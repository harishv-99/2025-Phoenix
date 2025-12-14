package edu.ftcphoenix.fw.sensing;

import java.util.Objects;
import java.util.Set;

import edu.ftcphoenix.fw.debug.DebugSink;

/**
 * Tracks the "best" AprilTag target across loops for a specific set of IDs.
 *
 * <h2>Role</h2>
 * <p>
 * {@code TagTarget} is a small helper that wraps an {@link AprilTagSensor} and
 * remembers the latest {@link AprilTagObservation} that matches a given set of
 * tag IDs and a freshness constraint. It is intended to be the single place in
 * your robot code that answers:
 * </p>
 * <p>
 * <em>"Which tag are we currently tracking, and what is its distance and
 * bearing?"</em>
 * </p>
 *
 * <h2>Frame &amp; sign conventions</h2>
 * <p>
 * This class reports bearing/range derived from {@link AprilTagObservation#pCameraToTag}
 * (i.e., in the <b>camera frame</b>, using Phoenix framing: +X forward, +Y left, +Z up).
 * </p>
 * <ul>
 *   <li>{@code bearingRad &gt; 0} means the tag appears to the <b>left</b>.</li>
 *   <li>{@code bearingRad &lt; 0} means the tag appears to the <b>right</b>.</li>
 *   <li>{@code rangeInches} is planar range in the camera X/Y plane.</li>
 * </ul>
 *
 * <h2>Typical usage</h2>
 *
 * <pre>{@code
 * // Wiring in init():
 * AprilTagSensor tagSensor = FtcVision.aprilTags(hardwareMap, "Webcam 1");
 * Set<Integer> scoringTags = Set.of(1, 2, 3);
 *
 * // Track scoring tags with a 0.5s freshness window.
 * TagTarget scoringTarget = new TagTarget(tagSensor, scoringTags, 0.5);
 *
 * // Create a BearingSource view for TagAim.
 * BearingSource bearingSource = clock -> scoringTarget.toBearingSample();
 *
 * // Build a controller once (not shown here; see TagAimController docs),
 * // then wire TagAim using the advanced overload:
 * DriveSource driveWithAim = TagAim.teleOpAim(
 *         baseDrive,
 *         pads.p1().leftBumper(),  // hold to aim
 *         bearingSource,
 *         controller
 * );
 *
 * // In loop():
 * while (opModeIsActive()) {
 *     // 1) Update inputs/sensors.
 *     clock.update();
 *     gamepads.update(dtSec);
 *     scoringTarget.update();
 *
 *     // 2) Drive with TagAim (uses scoringTarget's bearing).
 *     DriveSignal cmd = driveWithAim.get(clock);
 *     drivebase.drive(cmd);
 *
 *     // 3) Use scoringTarget.hasTarget(), bearingRad(), rangeInches()
 *     //    for shooter decisions, telemetry, etc.
 * }
 * }</pre>
 *
 * <h2>Design notes</h2>
 * <ul>
 *   <li>Create a {@code TagTarget} once at init time.</li>
 *   <li>Call {@link #update()} exactly once per control loop.</li>
 *   <li>After {@code update()}, read {@link #last()}, {@link #hasTarget()},
 *       {@link #bearingRad()}, or {@link #rangeInches()} as needed.</li>
 *   <li>The same tracked observation can be used for aiming, shooter control,
 *       and debugging so all three features agree on "which tag" is in use.</li>
 * </ul>
 */
public final class TagTarget {

    private final AprilTagSensor sensor;
    private final Set<Integer> idsOfInterest;
    private final double maxAgeSec;

    // Last observation returned by the sensor for this ID set + age constraint.
    // Initialized to "no target" with effectively infinite age to signal
    // that update() hasn't been called yet.
    private AprilTagObservation lastObs =
            AprilTagObservation.noTarget(Double.POSITIVE_INFINITY);

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
     * This should be called exactly once per control loop, <b>before</b> any
     * code that reads {@link #last()}, {@link #hasTarget()},
     * {@link #bearingRad()}, or {@link #rangeInches()}.
     * </p>
     *
     * <p>
     * Internally this simply calls {@link AprilTagSensor#best(Set, double)}
     * with the configured ID set and {@code maxAgeSec}, and stores the result.
     * </p>
     */
    public void update() {
        lastObs = sensor.best(idsOfInterest, maxAgeSec);
    }

    /**
     * Latest observation from this tracker.
     *
     * <p>
     * This will always return a non-null {@link AprilTagObservation}. When
     * {@link AprilTagObservation#hasTarget} is {@code false}, the ID and pose
     * are not meaningful, but {@link AprilTagObservation#ageSec} can still be
     * useful for debugging.
     * </p>
     *
     * @return last observation returned by {@link #update()}, or an initial
     * "no target" observation if {@link #update()} has not yet been called
     */
    public AprilTagObservation last() {
        return lastObs;
    }

    /**
     * Whether the tracker currently has a valid target.
     *
     * <p>
     * This is equivalent to {@code last().hasTarget}, but provided as a small
     * convenience.
     * </p>
     *
     * @return {@code true} if the latest observation has a target (and met the age constraint)
     */
    public boolean hasTarget() {
        return lastObs.hasTarget;
    }

    /**
     * Current horizontal bearing to the tracked tag, in radians.
     *
     * <p>
     * Only meaningful when {@link #hasTarget()} is {@code true}. Callers should
     * always guard on {@link #hasTarget()}.
     * </p>
     *
     * <p>
     * Sign convention: positive means the tag is to the left of the camera forward axis.
     * </p>
     *
     * @return bearing in radians (positive = left)
     */
    public double bearingRad() {
        return lastObs.cameraBearingRad();
    }

    /**
     * Test whether the current target bearing is within the given angular tolerance.
     *
     * <p>Semantics:</p>
     * <ul>
     *   <li>If {@link #hasTarget()} is {@code false}, this always returns {@code false}.</li>
     *   <li>If {@code toleranceRad} is negative, this method throws an
     *       {@link IllegalArgumentException}.</li>
     *   <li>Otherwise this returns {@code true} when the absolute bearing is
     *       less than or equal to {@code toleranceRad}.</li>
     * </ul>
     *
     * @param toleranceRad non-negative angular tolerance in radians
     * @return {@code true} if there is a target and its bearing is within the
     * specified tolerance
     * @throws IllegalArgumentException if {@code toleranceRad} is negative
     */
    public boolean isBearingWithin(double toleranceRad) {
        if (toleranceRad < 0.0) {
            throw new IllegalArgumentException("toleranceRad must be non-negative");
        }
        if (!lastObs.hasTarget) {
            return false;
        }
        return Math.abs(lastObs.cameraBearingRad()) <= toleranceRad;
    }

    /**
     * Planar range to the tracked tag, in inches, derived from the camera-frame pose.
     *
     * <p>
     * This is the distance in the camera X/Y plane (forward/left). If you want 3D line-of-sight
     * range, use {@code last().cameraRangeInches()}.
     * </p>
     *
     * <p>
     * Only meaningful when {@link #hasTarget()} is {@code true}. Callers should
     * always guard on {@link #hasTarget()}.
     * </p>
     *
     * @return planar range in inches (camera X/Y plane)
     */
    public double rangeInches() {
        // Planar distance in the camera X/Y plane (forward/left).
        double f = lastObs.cameraForwardInches();
        double l = lastObs.cameraLeftInches();
        return Math.sqrt(f * f + l * l);
    }

    /**
     * Maximum acceptable tag age in seconds for this tracker.
     *
     * @return configured maximum age
     */
    public double maxAgeSec() {
        return maxAgeSec;
    }

    /**
     * IDs this tracker is currently considering.
     *
     * <p>
     * The returned set is the same instance that was passed to the constructor;
     * callers should treat it as read-only.
     * </p>
     *
     * @return ID set passed to the constructor (not a defensive copy)
     */
    public Set<Integer> idsOfInterest() {
        return idsOfInterest;
    }

    /**
     * Convenience helper: interpret the current observation as a
     * {@link BearingSource.BearingSample}.
     *
     * @return a bearing sample representing the current observation
     */
    public BearingSource.BearingSample toBearingSample() {
        if (!lastObs.hasTarget) {
            return new BearingSource.BearingSample(false, 0.0);
        }
        return new BearingSource.BearingSample(true, lastObs.cameraBearingRad());
    }

    /**
     * Emit debug information about this tracker and its last observation.
     *
     * <p>
     * This follows the framework-wide {@code debugDump} pattern: it uses
     * {@link DebugSink} with a configurable key prefix and is defensive
     * against {@code null}.
     * </p>
     *
     * <pre>{@code
     * tagTarget.debugDump(debugSink, "tags.scoring");
     * }</pre>
     *
     * @param dbg    debug sink to write to; if {@code null}, this method does nothing
     * @param prefix key prefix to use; if {@code null} or empty, {@code "tagTarget"}
     *               is used as the default prefix
     */
    public void debugDump(DebugSink dbg, String prefix) {
        if (dbg == null) {
            return;
        }
        String p = (prefix == null || prefix.isEmpty()) ? "tagTarget" : prefix;

        dbg.addLine(p + ": TagTarget");

        // Static configuration.
        dbg.addData(p + ".ids", idsOfInterest.toString());
        dbg.addData(p + ".maxAgeSec", maxAgeSec);
        dbg.addData(p + ".sensor.class", sensor.getClass().getSimpleName());

        // Dynamic state – last observation.
        AprilTagObservation o = lastObs;
        dbg.addData(p + ".obs.hasTarget", o.hasTarget);
        dbg.addData(p + ".obs.id", o.id);
        dbg.addData(p + ".obs.ageSec", o.ageSec);

        // Derived (camera-frame) targeting helpers.
        dbg.addData(p + ".obs.cameraForwardInches", o.cameraForwardInches());
        dbg.addData(p + ".obs.cameraLeftInches", o.cameraLeftInches());
        dbg.addData(p + ".obs.cameraUpInches", o.cameraUpInches());
        dbg.addData(p + ".obs.cameraBearingRad", o.cameraBearingRad());
        dbg.addData(p + ".obs.cameraRangeInches", o.cameraRangeInches());
        dbg.addData(p + ".obs.cameraPlanarRangeInches", rangeInches());
    }

    /**
     * Extended debug helper that also reports whether the current bearing is
     * within a specified angular tolerance.
     *
     * <p>
     * This is a thin convenience wrapper around
     * {@link #debugDump(DebugSink, String)} and
     * {@link #isBearingWithin(double)}.
     * </p>
     *
     * @param dbg          debug sink to write to; if {@code null}, this method does nothing
     * @param prefix       key prefix to use; if {@code null} or empty, {@code "tagTarget"}
     *                     is used as the default prefix
     * @param toleranceRad angular tolerance in radians; negative values are treated
     *                     as invalid and reported as not within tolerance
     */
    public void debugDump(DebugSink dbg, String prefix, double toleranceRad) {
        if (dbg == null) {
            return;
        }

        // First emit the standard dump so callers get the full picture.
        debugDump(dbg, prefix);

        String p = (prefix == null || prefix.isEmpty()) ? "tagTarget" : prefix;

        dbg.addData(p + ".aim.toleranceRad", toleranceRad);
        if (toleranceRad < 0.0) {
            dbg.addData(p + ".aim.withinTolerance", false);
        } else {
            dbg.addData(p + ".aim.withinTolerance", isBearingWithin(toleranceRad));
        }
    }
}

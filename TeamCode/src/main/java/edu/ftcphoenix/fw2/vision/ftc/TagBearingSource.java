package edu.ftcphoenix.fw2.vision.ftc;

import org.firstinspires.ftc.vision.apriltag.AprilTagDetection;

import java.util.Optional;

import edu.ftcphoenix.fw2.sensing.AngleSource;
import edu.ftcphoenix.fw2.sensing.FeedbackSample;

/**
 * TagBearingSource — exposes AprilTag bearing (degrees, +CCW) as an {@link AngleSource}.
 *
 * <p><b>Role:</b> platform-specific adapter that reads from {@link TagVision} (FTC VisionPortal)
 * and presents a time-stamped {@link FeedbackSample} suitable for controllers.</p>
 *
 * <p><b>Selection modes:</b></p>
 * <ul>
 *   <li><b>Closest-bearing</b>: track whichever detected tag has the smallest absolute bearing.</li>
 *   <li><b>Target ID</b>: track the tag with a specific ID whose bearing is closest to 0.</li>
 * </ul>
 *
 * <p><b>Units:</b> returns bearing in <b>degrees</b> as reported by FTC's {@link AprilTagDetection#ftcPose}.
 * Positive is CCW (target to the left). Controllers that expect radians should convert upstream.</p>
 *
 * <p><b>Validity:</b> if no matching detection is available on a sample, the returned
 * {@code FeedbackSample} has {@code valid=false} and a dummy value of 0.</p>
 */
public final class TagBearingSource implements AngleSource {
    private final TagVision vision;
    private Optional<Integer> targetId = Optional.empty();

    /**
     * Create a bearing source bound to an FTC {@link TagVision} instance.
     *
     * @param vision FTC vision wrapper providing AprilTag detections (not null)
     */
    public TagBearingSource(TagVision vision) {
        this.vision = vision;
    }

    /**
     * Track a specific AprilTag ID. Pass {@link Optional#empty()} to track the closest-bearing tag.
     *
     * @param id target ID or empty for closest-bearing mode
     * @return this for chaining
     */
    public TagBearingSource setTargetId(Optional<Integer> id) {
        this.targetId = id;
        return this;
    }

    /**
     * Track a specific AprilTag ID.
     *
     * @param id tag ID to track
     * @return this for chaining
     */
    public TagBearingSource setTargetId(int id) {
        this.targetId = Optional.of(id);
        return this;
    }

    /**
     * Switch to closest-bearing mode (no fixed tag ID).
     *
     * @return this for chaining
     */
    public TagBearingSource clearTargetId() {
        this.targetId = Optional.empty();
        return this;
    }

    /**
     * Sample the current tag bearing.
     *
     * <p>On success, {@code valid=true} and {@code value} is the bearing in degrees.
     * If no appropriate detection is available, returns {@code valid=false} and {@code value=0}.</p>
     *
     * @param nanoTime timestamp from {@link System#nanoTime()}
     * @return feedback sample containing validity, value, and timestamp
     */
    @Override
    public FeedbackSample<Double> getAngle(long nanoTime) {
        Optional<AprilTagDetection> opt = targetId.isPresent()
                ? vision.getClosestBearingTagWithId(targetId.get())
                : vision.getClosestBearingTag();

        if (opt.isPresent()) {
            return new FeedbackSample<>(true, opt.get().ftcPose.bearing, nanoTime);
        }
        return new FeedbackSample<>(false, 0.0, nanoTime);
    }
}

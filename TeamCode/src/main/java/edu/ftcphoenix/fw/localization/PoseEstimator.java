package edu.ftcphoenix.fw.localization;

import edu.ftcphoenix.fw.util.LoopClock;

/**
 * Common interface for components that estimate the robot's pose on the field.
 *
 * <p>A {@code PoseEstimator} typically consumes one or more sensing sources
 * (e.g., AprilTags, wheel encoders, IMU) and produces a {@link PoseEstimate}
 * that can be used by drive controllers and tasks. Different implementations
 * may have very different internal logic, but all share the same basic
 * contract:</p>
 *
 * <ul>
 *   <li>Call {@link #update(LoopClock)} once per control loop to advance the
 *       estimator's internal state.</li>
 *   <li>Call {@link #getEstimate()} to retrieve the most recent pose estimate.</li>
 * </ul>
 *
 * <p>This keeps the estimator usage simple and consistent with the rest of
 * the Phoenix framework's loop-based design.</p>
 */
public interface PoseEstimator {

    /**
     * Advance the estimator's internal state using the current time.
     *
     * <p>Implementations are responsible for:
     * <ul>
     *   <li>Reading any sensors they depend on (directly or via adapters),</li>
     *   <li>Updating their internal filters / state machines, and</li>
     *   <li>Preparing the next {@link PoseEstimate} that
     *       {@link #getEstimate()} will return.</li>
     * </ul>
     *
     * <p>The framework expects this method to be called once per main
     * control loop, typically from a robot's OpMode loop body or a
     * higher-level framework loop.</p>
     *
     * @param clock loop clock providing the current time in seconds
     */
    void update(LoopClock clock);

    /**
     * Returns the most recent pose estimate.
     *
     * <p>This method must be safe to call multiple times between {@link #update(LoopClock)}
     * calls; it should simply return the last estimate computed by the most
     * recent {@code update()}.</p>
     *
     * @return the latest {@link PoseEstimate}; callers should check
     *         {@link PoseEstimate#hasPose} before using the pose for control
     */
    PoseEstimate getEstimate();
}

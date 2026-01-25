package edu.ftcphoenix.fw.spatial;

import edu.ftcphoenix.fw.core.control.HysteresisLatch;
import edu.ftcphoenix.fw.core.geometry.Pose2d;

/**
 * A convenience wrapper that answers “am I inside this zone?” with hysteresis.
 *
 * <p>This is a <b>spatial predicate</b>. It does not create motion commands. It simply
 * turns a geometric test into a stable boolean suitable for safety gating.</p>
 */
public final class ZoneLatch {

    private final Region2d region;
    private final HysteresisLatch latch;

    /**
     * Creates a zone latch using signed distance hysteresis.
     *
     * <p>Because {@link Region2d#signedDistanceInches(double, double)} is positive inside,
     * you typically use something like:</p>
     * <ul>
     *   <li>{@code enterDistanceInches = +2} (must be at least 2" inside to turn on)</li>
     *   <li>{@code exitDistanceInches = -2} (must be at least 2" outside to turn off)</li>
     * </ul>
     *
     * @param region              region to test
     * @param enterDistanceInches latch turns ON when signedDistance >= enterDistanceInches
     * @param exitDistanceInches  latch turns OFF when signedDistance <= exitDistanceInches
     */
    public ZoneLatch(Region2d region, double enterDistanceInches, double exitDistanceInches) {
        this.region = region;
        this.latch = HysteresisLatch.onWhenAboveOffWhenBelow(enterDistanceInches, exitDistanceInches);
    }

    /**
     * Update the latch with a point.
     */
    public boolean update(double xInches, double yInches) {
        double s = region.signedDistanceInches(xInches, yInches);
        return latch.update(s);
    }

    /**
     * Update the latch with a pose (translation only).
     */
    public boolean update(Pose2d pose) {
        return update(pose.xInches, pose.yInches);
    }

    /**
     * @return current latched state
     */
    public boolean get() {
        return latch.get();
    }

    /**
     * Reset to OFF.
     */
    public void resetOff() {
        latch.reset(false);
    }
}

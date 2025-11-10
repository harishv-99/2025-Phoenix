package edu.ftcphoenix.fw.hal;

import edu.ftcphoenix.fw.core.Updatable;
import edu.ftcphoenix.fw.util.LoopClock;

/**
 * Minimal yaw provider (radians, CCW positive).
 */
public interface ImuYaw extends Updatable {
    double yawRad();

    /**
     * Zero the yaw such that current heading becomes 0.
     */
    void zero();

    @Override
    void update(LoopClock clock);
}

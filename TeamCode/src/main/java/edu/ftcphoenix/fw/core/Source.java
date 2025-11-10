package edu.ftcphoenix.fw.core;

import edu.ftcphoenix.fw.util.LoopClock;

/**
 * Produces a value each tick (e.g., DriveIntent from gamepad or planner).
 */
public interface Source<T> {
    T get(LoopClock clock);
}

package edu.ftcphoenix.fw.core;

import edu.ftcphoenix.fw.util.LoopClock;

/**
 * Objects that advance with time. Never block or sleep inside {@link #update(LoopClock)}.
 */
public interface Updatable {
    void update(LoopClock clock);
}

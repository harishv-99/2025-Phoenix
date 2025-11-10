package edu.ftcphoenix.fw.drive;

import edu.ftcphoenix.fw.core.Updatable;
import edu.ftcphoenix.fw.util.LoopClock;

/** Minimal drivebase contract. Non-blocking; no sleeps in {@link #update(LoopClock)}. */
public interface Drivebase extends Updatable {
    /** Apply the latest robot-centric drive signal. Implementation may normalize before sending to motors. */
    void drive(DriveSignal signal);

    /** Immediately stop all actuators (e.g., on disable). */
    void stop();
}

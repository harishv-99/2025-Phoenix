package edu.ftcphoenix.fw.intent.task;

import edu.ftcphoenix.fw.core.Updatable;
import edu.ftcphoenix.fw.util.LoopClock;

/**
 * Minimal task lifecycle for non-piece maneuvers (e.g., hang, timed waits, drive actions).
 *
 * <h3>Contract</h3>
 * <ul>
 *   <li>{@link #start()} is called once.</li>
 *   <li>{@link #update(LoopClock)} is called every loop; never block or sleep.</li>
 *   <li>{@link #isFinished()} must become true; {@link #stop()} should be idempotent.</li>
 * </ul>
 */
public interface Task extends Updatable {
    void start();

    void stop();

    boolean isFinished();

    @Override
    void update(LoopClock clock);
}

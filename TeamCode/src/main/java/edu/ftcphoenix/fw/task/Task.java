package edu.ftcphoenix.fw.task;

import edu.ftcphoenix.fw.util.LoopClock;

/**
 * Small non-blocking task interface for time-based actions.
 *
 * <p>Tasks are designed to be scheduled and updated by a {@link TaskRunner} or
 * similar loop:
 *
 * <pre>{@code
 * TaskRunner runner = new TaskRunner();
 * runner.enqueue(new InstantTask(() -> doSomethingOnce()));
 * runner.enqueue(new WaitUntilTask(() -> sensorReady()));
 *
 * // In your main loop:
 * runner.update(clock);
 * }</pre>
 *
 * <p>Lifecycle:
 * <ul>
 *   <li>{@link #start(LoopClock)} – called exactly once, when the task begins.</li>
 *   <li>{@link #update(LoopClock)} – called every loop while the task is active.</li>
 *   <li>{@link #isFinished()} – when this returns true, the scheduler stops
 *       calling {@code update} and may advance to the next task.</li>
 * </ul>
 */
public interface Task {
    /**
     * Called once when the task is first scheduled/started.
     *
     * @param clock loop timing helper (for dt, timestamps, etc.)
     */
    void start(LoopClock clock);

    /**
     * Called on each loop while the task is active.
     *
     * @param clock loop timing helper (for dt, timestamps, etc.)
     */
    void update(LoopClock clock);

    /**
     * @return true when the task has completed and no further updates are required.
     */
    boolean isFinished();
}

package edu.ftcphoenix.fw.task;

import java.util.ArrayList;
import java.util.List;

import edu.ftcphoenix.fw.debug.DebugSink;
import edu.ftcphoenix.fw.util.LoopClock;

/**
 * Simple sequential task runner.
 *
 * <p>Responsibilities:
 * <ul>
 *   <li>Maintain a queue of {@link Task} instances.</li>
 *   <li>Start tasks one at a time in FIFO order.</li>
 *   <li>Update the current task each loop until it reports finished.</li>
 * </ul>
 *
 * <p>Usage pattern:
 * <pre>{@code
 * TaskRunner runner = new TaskRunner();
 * runner.enqueue(new InstantTask(() -> log("start")));
 * runner.enqueue(new WaitUntilTask(() -> sensorReady()));
 *
 * // In loop:
 * clock.update(getRuntime());
 * runner.update(clock);
 * }</pre>
 *
 * <p>Tasks may finish immediately in {@link Task#start(LoopClock)} (e.g. {@link InstantTask});
 * the runner will automatically advance to the next task without calling
 * {@link Task#update(LoopClock)} on a finished task.</p>
 */
public final class TaskRunner {

    private final List<Task> queue = new ArrayList<Task>();
    private Task current;

    /**
     * Enqueue a task to be run after any already-enqueued tasks.
     *
     * <p>If no task is currently active, the new task will be started on the
     * next call to {@link #update(LoopClock)}.</p>
     */
    public void enqueue(Task task) {
        if (task == null) {
            throw new IllegalArgumentException("task is required");
        }
        queue.add(task);
    }

    /**
     * Clear all pending tasks and drop the current task (if any).
     *
     * <p>Note: {@link Task} has no explicit "cancel" hook, so tasks should be
     * written to tolerate being abandoned between calls to update().</p>
     */
    public void clear() {
        queue.clear();
        current = null;
    }

    /**
     * @return true if there is no current task and no tasks queued.
     */
    public boolean isIdle() {
        return current == null && queue.isEmpty();
    }

    /**
     * @return the number of tasks remaining in the queue (not counting the
     * current task, if any).
     */
    public int queuedCount() {
        return queue.size();
    }

    /**
     * @return true if a task is currently active (started and not finished).
     */
    public boolean hasActiveTask() {
        return current != null && !current.isFinished();
    }

    /**
     * Update the task runner and the current task.
     *
     * <p>Semantics:
     * <ul>
     *   <li>If there is no current task, or the current task is finished,
     *       the runner will pull the next task from the queue and call its
     *       {@link Task#start(LoopClock)} method.</li>
     *   <li>If the task finishes immediately in {@code start()}, the runner
     *       will advance to the next queued task (if any) before returning.</li>
     *   <li>If there is an active (not finished) current task after this
     *       process, its {@link Task#update(LoopClock)} method is called
     *       exactly once.</li>
     * </ul>
     */
    public void update(LoopClock clock) {
        // Ensure we have a current task that is not yet finished.
        while ((current == null || current.isFinished()) && !queue.isEmpty()) {
            current = queue.remove(0);
            current.start(clock);

            // If the task finished immediately in start(), loop to pick another.
            if (current.isFinished()) {
                current = null;
            }
        }

        // If we now have an active task, update it.
        if (current != null && !current.isFinished()) {
            current.update(clock);
        }
    }

    /**
     * Emit a compact summary of queue + current task for debugging.
     *
     * @param dbg    debug sink (may be {@code null}; if null, no output is produced)
     * @param prefix base key prefix, e.g. "tasks"
     */
    public void debugDump(DebugSink dbg, String prefix) {
        if (dbg == null) {
            return;
        }
        String p = (prefix == null || prefix.isEmpty()) ? "tasks" : prefix;

        dbg.addData(p + ".queueSize", queue.size())
                .addData(p + ".hasCurrent", current != null);

        if (current != null) {
            dbg.addData(p + ".currentClass", current.getClass().getSimpleName())
                    .addData(p + ".currentFinished", current.isFinished());
        }
    }
}

package edu.ftcphoenix.fw.task;

import java.util.ArrayList;
import java.util.List;

import edu.ftcphoenix.fw.util.LoopClock;

/**
 * A {@link Task} that runs multiple child tasks in parallel and finishes
 * only when <b>all</b> children have finished.
 *
 * <p>Semantics:
 * <ul>
 *   <li>On {@link #start(LoopClock)}, all children are started.</li>
 *   <li>On each {@link #update(LoopClock)}, all children that are not yet
 *       finished are updated once.</li>
 *   <li>The parallel group finishes when every child task reports complete.</li>
 * </ul>
 *
 * <p>Note that this class does not currently support cancellation semantics;
 * if you wish to stop a running parallel group early, you should implement
 * that logic in your own code (for example, by not calling
 * {@link #update(LoopClock)} any longer).</p>
 */
public final class ParallelAllTask implements Task {

    private final List<Task> tasks = new ArrayList<>();

    private boolean started = false;
    private boolean finished = false;

    /**
     * Create a parallel group from a list of tasks.
     *
     * <p>The list is copied; subsequent modifications to {@code tasks}
     * do not affect this parallel group.</p>
     *
     * @param tasks list of child tasks to run in parallel; must not be {@code null}
     */
    public ParallelAllTask(List<Task> tasks) {
        if (tasks == null) {
            throw new IllegalArgumentException("tasks is required");
        }
        this.tasks.addAll(tasks);
    }

    /**
     * Convenience factory for a parallel group from varargs.
     */
    public static ParallelAllTask of(Task... tasks) {
        if (tasks == null) {
            throw new IllegalArgumentException("tasks is required");
        }
        List<Task> list = new ArrayList<Task>(tasks.length);
        for (Task t : tasks) {
            if (t == null) {
                throw new IllegalArgumentException("task element must not be null");
            }
            list.add(t);
        }
        return new ParallelAllTask(list);
    }

    @Override
    public void start(LoopClock clock) {
        if (started) {
            return; // single-use; ignore repeated start calls
        }
        started = true;

        // Start all children.
        for (int i = 0; i < tasks.size(); i++) {
            Task t = tasks.get(i);
            t.start(clock);
        }

        // If all children finished immediately in start(), mark finished.
        if (allFinished()) {
            finished = true;
        }
    }

    @Override
    public void update(LoopClock clock) {
        if (!started || finished) {
            return;
        }

        // Update all children that are not yet complete.
        for (int i = 0; i < tasks.size(); i++) {
            Task t = tasks.get(i);
            if (!t.isComplete()) {
                t.update(clock);
            }
        }

        // Check if all have now completed.
        if (allFinished()) {
            finished = true;
        }
    }

    @Override
    public boolean isComplete() {
        return finished;
    }

    // --------------------------------------------------------------------
    // Internal helpers
    // --------------------------------------------------------------------

    private boolean allFinished() {
        for (int i = 0; i < tasks.size(); i++) {
            if (!tasks.get(i).isComplete()) {
                return false;
            }
        }
        return true;
    }
}

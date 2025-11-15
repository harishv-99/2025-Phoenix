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
 *   <li>The parallel group finishes when every child reports finished.</li>
 * </ul>
 *
 * <p>Typical usage:
 * <pre>{@code
 * TaskRunner runner = new TaskRunner();
 *
 * runner.enqueue(new ParallelAllTask(Arrays.asList(
 *     new WaitUntilTask(() -> shooterReady()),
 *     new WaitUntilTask(() -> intakeDown())
 * )));
 * }</pre>
 */
public final class ParallelAllTask implements Task {

    private final List<Task> tasks = new ArrayList<Task>();

    private boolean started = false;
    private boolean finished = false;

    /**
     * Create a parallel group from a list of tasks.
     *
     * <p>The list is copied; subsequent modifications to {@code tasks}
     * do not affect this parallel group.</p>
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
        finished = allFinished();
    }

    @Override
    public void update(LoopClock clock) {
        if (!started) {
            start(clock);
        }
        if (finished) {
            return;
        }

        // Update all children that are not yet finished.
        for (int i = 0; i < tasks.size(); i++) {
            Task t = tasks.get(i);
            if (!t.isFinished()) {
                t.update(clock);
            }
        }

        // If all children are now finished, mark finished.
        finished = allFinished();
    }

    @Override
    public boolean isFinished() {
        return finished;
    }

    // --------------------------------------------------------------------
    // Internal helpers
    // --------------------------------------------------------------------

    private boolean allFinished() {
        for (int i = 0; i < tasks.size(); i++) {
            if (!tasks.get(i).isFinished()) {
                return false;
            }
        }
        return true;
    }
}

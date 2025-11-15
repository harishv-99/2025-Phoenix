package edu.ftcphoenix.fw.task;

import java.util.ArrayList;
import java.util.List;

import edu.ftcphoenix.fw.util.LoopClock;

/**
 * A {@link Task} that runs a sequence of child tasks one after another.
 *
 * <p>Semantics:
 * <ul>
 *   <li>On {@link #start(LoopClock)}, the first child is started.</li>
 *   <li>On each {@link #update(LoopClock)}, the current child is updated.</li>
 *   <li>When the current child finishes, the next child is started on the
 *       next call to {@code update()} (or immediately if it finishes in
 *       {@code start()}).</li>
 *   <li>The sequence finishes when all children have finished.</li>
 * </ul>
 *
 * <p>Typical usage:
 * <pre>{@code
 * TaskRunner runner = new TaskRunner();
 *
 * runner.enqueue(SequenceTask.of(
 *     new InstantTask(() -> log("start")),
 *     new WaitUntilTask(() -> sensorReady()),
 *     new InstantTask(() -> log("done"))
 * ));
 * }</pre>
 */
public final class SequenceTask implements Task {

    private final List<Task> tasks = new ArrayList<Task>();

    private boolean started = false;
    private int index = -1; // index of current task, -1 before first

    /**
     * Create a sequence from a list of tasks.
     *
     * <p>The list is copied; subsequent modifications to {@code tasks}
     * do not affect this sequence.</p>
     */
    public SequenceTask(List<Task> tasks) {
        if (tasks == null) {
            throw new IllegalArgumentException("tasks is required");
        }
        this.tasks.addAll(tasks);
    }

    /**
     * Convenience factory for a sequence from varargs.
     */
    public static SequenceTask of(Task... tasks) {
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
        return new SequenceTask(list);
    }

    @Override
    public void start(LoopClock clock) {
        if (started) {
            return; // single-use; ignore repeated start calls
        }
        started = true;
        advanceToNextTask(clock);
    }

    @Override
    public void update(LoopClock clock) {
        if (!started) {
            start(clock);
        }
        if (isFinished()) {
            return;
        }

        Task current = currentTask();
        if (current == null) {
            return;
        }

        current.update(clock);

        // If the current task finished during update, advance to the next one.
        if (current.isFinished()) {
            advanceToNextTask(clock);
        }
    }

    @Override
    public boolean isFinished() {
        // Finished when we've advanced past the last child.
        return started && index >= tasks.size();
    }

    // --------------------------------------------------------------------
    // Internal helpers
    // --------------------------------------------------------------------

    private Task currentTask() {
        if (index < 0 || index >= tasks.size()) {
            return null;
        }
        return tasks.get(index);
    }

    /**
     * Advance to the next task, starting it immediately.
     *
     * <p>If the next task finishes in its {@code start()}, this method will
     * continue advancing until it finds a non-finished task or runs out of
     * tasks.</p>
     */
    private void advanceToNextTask(LoopClock clock) {
        while (true) {
            index++;

            if (index >= tasks.size()) {
                // No more tasks; sequence is finished.
                return;
            }

            Task next = tasks.get(index);
            next.start(clock);

            // If this task finished immediately in start(), loop to pick the next one.
            if (!next.isFinished()) {
                return;
            }
        }
    }
}

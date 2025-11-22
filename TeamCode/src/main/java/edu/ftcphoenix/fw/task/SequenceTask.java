package edu.ftcphoenix.fw.task;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

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
    /**
     * Index of current task; -1 before the first task is started.
     */
    private int index = -1;

    /**
     * Create a sequence from a list of tasks.
     *
     * <p>The list is copied; subsequent modifications to {@code tasks}
     * do not affect this sequence.</p>
     *
     * @param tasks ordered list of child tasks to run; must not be {@code null}
     * @throws IllegalArgumentException if {@code tasks} is {@code null}
     */
    public SequenceTask(List<Task> tasks) {
        if (tasks == null) {
            throw new IllegalArgumentException("tasks is required");
        }
        this.tasks.addAll(tasks);
    }

    /**
     * Convenience factory for a sequence from varargs.
     *
     * <p>Each element in {@code tasks} must be non-null. The array is copied
     * into an internal list.</p>
     *
     * @param tasks ordered child tasks to run; must not be {@code null} and
     *              must not contain {@code null} elements
     * @return a new {@link SequenceTask} containing the given tasks
     * @throws IllegalArgumentException if {@code tasks} is {@code null} or
     *                                  any element is {@code null}
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

    /**
     * Create a sequence that repeats a pattern task a fixed number of times.
     *
     * <p>This helper is useful when you want to run the same logical behavior
     * multiple times in a row (for example, "fire one shot" repeated three
     * times), but you still want each repetition to be a <em>fresh</em>
     * {@link Task} instance.</p>
     *
     * <p>Usage example:</p>
     *
     * <pre>{@code
     * // Factory that creates a new one-shot macro each time.
     * Supplier<Task> oneShotFactory = () -> createOneShotTask();
     *
     * // Sequence that fires three shots in a row.
     * Task tripleShot = SequenceTask.repeat(oneShotFactory, 3);
     *
     * taskRunner.enqueue(tripleShot);
     * }</pre>
     *
     * <p><strong>Why a Supplier?</strong><br/>
     * {@link Task} instances are generally assumed to be single-use. Reusing
     * the same Task instance multiple times in a sequence can lead to subtle
     * bugs, because most Task implementations are not designed to be restarted.
     * This helper therefore accepts a {@link Supplier} that creates a new
     * Task instance for each repetition.</p>
     *
     * @param factory factory that creates a fresh {@link Task} instance for
     *                each repetition; must not be {@code null} and must not
     *                return {@code null}
     * @param times   number of repetitions; must be &gt; 0
     * @return a {@link SequenceTask} containing {@code times} child tasks
     * created by {@code factory}
     * @throws IllegalArgumentException if {@code factory} is {@code null},
     *                                  {@code times} &lt;= 0, or the factory
     *                                  returns {@code null} for any repetition
     */
    public static SequenceTask repeat(Supplier<? extends Task> factory, int times) {
        if (factory == null) {
            throw new IllegalArgumentException("factory is required");
        }
        if (times <= 0) {
            throw new IllegalArgumentException("times must be > 0 (was " + times + ")");
        }
        List<Task> list = new ArrayList<Task>(times);
        for (int i = 0; i < times; i++) {
            Task t = factory.get();
            if (t == null) {
                throw new IllegalArgumentException("factory must not create null tasks (at index " + i + ")");
            }
            list.add(t);
        }
        return new SequenceTask(list);
    }

    @Override
    public void start(LoopClock clock) {
        if (started) {
            // SequenceTask is single-use; ignore repeated start calls.
            return;
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

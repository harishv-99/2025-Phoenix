package edu.ftcphoenix.fw.intent.task;

import edu.ftcphoenix.fw.util.LoopClock;

/**
 * Runs all tasks in parallel; finishes when all are finished.
 *
 * <p>Use carefully: avoid conflicting hardware ownership inside subtasks.</p>
 */
public final class ParallelAllTask implements Task {
    private final Task[] tasks;
    private boolean started = false;

    public ParallelAllTask(Task... tasks) {
        this.tasks = tasks == null ? new Task[0] : tasks;
    }

    @Override
    public void start() {
        started = true;
        for (Task t : tasks) t.start();
    }

    @Override
    public void stop() {
        for (Task t : tasks) t.stop();
    }

    @Override
    public boolean isFinished() {
        if (!started) return false;
        for (Task t : tasks) if (!t.isFinished()) return false;
        return true;
    }

    @Override
    public void update(LoopClock clock) {
        for (Task t : tasks) t.update(clock);
    }
}

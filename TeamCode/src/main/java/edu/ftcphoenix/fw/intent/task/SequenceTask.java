package edu.ftcphoenix.fw.intent.task;

import edu.ftcphoenix.fw.util.LoopClock;

/**
 * Runs tasks sequentially, advancing when each finishes.
 *
 * <p>Idempotent: stopping mid-way will stop the current task and mark complete.</p>
 */
public final class SequenceTask implements Task {
    private final Task[] tasks;
    private int idx = -1;
    private boolean done = false;

    public SequenceTask(Task... tasks) {
        this.tasks = tasks == null ? new Task[0] : tasks;
    }

    @Override
    public void start() {
        idx = -1;
        done = false;
        advance();
    }

    private void advance() {
        idx++;
        if (idx >= tasks.length) {
            done = true;
            return;
        }
        tasks[idx].start();
    }

    @Override
    public void stop() {
        if (!done && idx >= 0 && idx < tasks.length) {
            tasks[idx].stop();
        }
        done = true;
    }

    @Override
    public boolean isFinished() {
        return done;
    }

    @Override
    public void update(LoopClock clock) {
        if (done) return;
        Task t = tasks[idx];
        t.update(clock);
        if (t.isFinished()) {
            t.stop();
            advance();
        }
    }
}

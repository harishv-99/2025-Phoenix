package edu.ftcphoenix.fw.intent.task;

import edu.ftcphoenix.fw.util.LoopClock;

/**
 * Runs a Runnable on start and finishes immediately.
 */
public final class InstantTask implements Task {
    private final Runnable action;
    private boolean done = false;

    public InstantTask(Runnable action) {
        this.action = action;
    }

    @Override
    public void start() {
        if (action != null) action.run();
        done = true;
    }

    @Override
    public void stop() { /* no-op */ }

    @Override
    public boolean isFinished() {
        return done;
    }

    @Override
    public void update(LoopClock clock) { /* no-op */ }
}

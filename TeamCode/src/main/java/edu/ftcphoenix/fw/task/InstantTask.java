package edu.ftcphoenix.fw.task;

import java.util.Objects;

import edu.ftcphoenix.fw.util.LoopClock;

/**
 * A {@link Task} that runs a single action once, immediately on {@link #start(LoopClock)},
 * and then finishes.
 *
 * <p>Typical usage:
 * <pre>{@code
 * TaskRunner runner = new TaskRunner();
 * runner.enqueue(new InstantTask(() -> telemetry.addLine("Auto start")));
 * }</pre>
 *
 * <p>Semantics:
 * <ul>
 *   <li>The action is guaranteed to run at most once per {@code InstantTask} instance.</li>
 *   <li>Additional calls to {@link #start(LoopClock)} after the first are ignored.</li>
 *   <li>{@link #update(LoopClock)} does nothing; the task finishes during {@code start()}.</li>
 * </ul>
 */
public final class InstantTask implements Task {

    private final Runnable action;
    private boolean started = false;
    private boolean finished = false;

    /**
     * @param action action to run once when the task starts
     */
    public InstantTask(Runnable action) {
        this.action = Objects.requireNonNull(action, "action is required");
    }

    @Override
    public void start(LoopClock clock) {
        // Idempotent start: only run the action once.
        if (started) {
            return;
        }
        started = true;

        action.run();
        finished = true;
    }

    @Override
    public void update(LoopClock clock) {
        // No periodic work; instant tasks finish in start().
    }

    @Override
    public boolean isFinished() {
        return finished;
    }
}

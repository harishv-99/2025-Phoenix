package edu.ftcphoenix.fw.intent.task;

import edu.ftcphoenix.fw.util.LoopClock;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Runs at most one Task at a time (queue). Useful for TeleOp button-triggered actions.
 *
 * <p>Non-blocking: the runner only calls {@code update} and checks status each loop.</p>
 */
public final class TaskRunner {
    private final Deque<Task> queue = new ArrayDeque<Task>();
    private Task active = null;

    public void enqueue(Task t) {
        if (t != null) queue.addLast(t);
    }

    public void clear() {
        if (active != null) {
            active.stop();
            active = null;
        }
        queue.clear();
    }

    public boolean isIdle() {
        return active == null && queue.isEmpty();
    }

    public void update(LoopClock clock) {
        if (active == null) {
            active = queue.pollFirst();
            if (active != null) active.start();
        }
        if (active != null) {
            active.update(clock);
            if (active.isFinished()) {
                active.stop();
                active = null;
            }
        }
    }
}

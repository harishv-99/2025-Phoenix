package edu.ftcphoenix.fw.task;

import java.util.Objects;
import java.util.function.BooleanSupplier;

import edu.ftcphoenix.fw.util.LoopClock;

/**
 * A {@link Task} that runs until a condition becomes true, optionally
 * with a timeout and timeout callback.
 *
 * <p>Typical usage:
 * <pre>{@code
 * TaskRunner runner = new TaskRunner();
 *
 * // Wait until a sensor reports ready
 * runner.enqueue(new WaitUntilTask(() -> sensor.isReady()));
 *
 * // Wait until shooter ready, with a 3-second timeout
 * runner.enqueue(new WaitUntilTask(
 *         () -> shooterStage.atSetpoint(),
 *         3.0,
 *         () -> telemetry.addLine("Shooter timeout")
 * ));
 * }</pre>
 */
public final class WaitUntilTask implements Task {

    private final BooleanSupplier condition;
    private final double timeoutSec;
    private final Runnable onTimeout;

    private boolean finished = false;
    private boolean timedOut = false;
    private double elapsedSec = 0.0;

    /**
     * Create a wait-until task with no timeout.
     *
     * @param condition condition to wait for; task finishes when this returns true
     */
    public WaitUntilTask(BooleanSupplier condition) {
        this(condition, 0.0, null);
    }

    /**
     * Create a wait-until task with an optional timeout.
     *
     * @param condition  condition to wait for; task finishes when this returns true
     * @param timeoutSec timeout in seconds; if {@code <= 0}, no timeout is applied
     * @param onTimeout  optional callback invoked once if the timeout elapses
     *                   before {@code condition} is satisfied
     */
    public WaitUntilTask(BooleanSupplier condition, double timeoutSec, Runnable onTimeout) {
        this.condition = Objects.requireNonNull(condition, "condition is required");
        this.timeoutSec = timeoutSec;
        this.onTimeout = onTimeout;
    }

    @Override
    public void start(LoopClock clock) {
        elapsedSec = 0.0;
        timedOut = false;

        // If condition is already true, finish immediately.
        if (condition.getAsBoolean()) {
            finished = true;
        }
    }

    @Override
    public void update(LoopClock clock) {
        if (finished) {
            return;
        }

        // Check condition first.
        if (condition.getAsBoolean()) {
            finished = true;
            return;
        }

        // If there's a timeout configured, check it.
        if (timeoutSec > 0.0) {
            elapsedSec += clock.dtSec();
            if (elapsedSec >= timeoutSec) {
                timedOut = true;
                finished = true;
                if (onTimeout != null) {
                    onTimeout.run();
                }
            }
        }
    }

    @Override
    public boolean isFinished() {
        return finished;
    }

    /**
     * @return true if the task completed due to timeout rather than the condition.
     */
    public boolean isTimedOut() {
        return timedOut;
    }
}

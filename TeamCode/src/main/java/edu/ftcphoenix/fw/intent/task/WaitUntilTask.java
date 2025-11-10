package edu.ftcphoenix.fw.intent.task;

import edu.ftcphoenix.fw.util.LoopClock;

import java.util.function.BooleanSupplier;

/**
 * Completes when {@code condition.getAsBoolean()} becomes true, or an optional timeout elapses.
 *
 * <p>Use to gate on sensors/global flags without blocking the loop.</p>
 */
public final class WaitUntilTask implements Task {
    private final BooleanSupplier condition;
    private final double timeoutSec; // <=0 means no timeout
    private double t = 0.0;
    private boolean started = false;

    public WaitUntilTask(BooleanSupplier condition, double timeoutSec) {
        this.condition = condition;
        this.timeoutSec = timeoutSec;
    }

    @Override
    public void start() {
        started = true;
        t = 0.0;
    }

    @Override
    public void stop() { /* no-op */ }

    @Override
    public boolean isFinished() {
        return started && (condition.getAsBoolean() || (timeoutSec > 0 && t >= timeoutSec));
    }

    @Override
    public void update(LoopClock clock) {
        t += Math.max(0, clock.dtSec());
    }
}

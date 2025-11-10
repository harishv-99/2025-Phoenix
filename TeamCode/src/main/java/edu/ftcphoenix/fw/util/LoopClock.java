package edu.ftcphoenix.fw.util;

/**
 * Simple loop clock. Call {@link #reset(double)} once, then {@link #update(double)} each loop
 * with a monotonically increasing time base (seconds). Use {@link #dtSec()} inside updates.
 */
public final class LoopClock {
    private double last = 0.0;
    private double dt = 0.0;
    private boolean started = false;

    /**
     * Initialize the clock at {@code nowSec}.
     */
    public void reset(double nowSec) {
        started = true;
        last = nowSec;
        dt = 0.0;
    }

    /**
     * Advance the clock; {@code nowSec} must be >= previous value.
     */
    public void update(double nowSec) {
        if (!started) {
            reset(nowSec);
            return;
        }
        double d = nowSec - last;
        if (d < 0) d = 0;
        dt = d;
        last = nowSec;
    }

    /**
     * Delta time (seconds) since the previous {@link #update(double)}.
     */
    public double dtSec() {
        return dt;
    }
}

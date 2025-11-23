package edu.ftcphoenix.fw2.core;

import edu.ftcphoenix.fw2.util.MathUtil;

/**
 * A thread-safe double setpoint that also acts as a {@link Source} for the raw target value.
 *
 * <p>Use this to store targets shared between UI/code and controllers. Optional clamping
 * enforces allowed ranges.</p>
 *
 * <h2>When to use</h2>
 * <ul>
 *   <li>As the live setpoint for a {@link FeedbackController} or PID loop.</li>
 *   <li>To expose a tunable target to dashboards or commands.</li>
 * </ul>
 *
 * <h2>Notes</h2>
 * <ul>
 *   <li>{@link #peek()} returns the latest value without shaping; {@link #get(FrameClock)} returns
 *       the same value and exists so this can plug into APIs expecting a {@link Source}.</li>
 *   <li>Value is {@code volatile} for simple cross-thread visibility.</li>
 * </ul>
 */
public final class DoubleSetpoint implements Setpoint<Double> {
    private volatile double value;
    private final boolean clampEnabled;
    private final double min, max;

    /**
     * Unclamped setpoint.
     */
    public DoubleSetpoint(double initial) {
        this.value = initial;
        this.clampEnabled = false;
        this.min = 0;
        this.max = 0;
    }

    /**
     * Clamped setpoint to [min,max].
     */
    public DoubleSetpoint(double initial, double min, double max) {
        this.value = MathUtil.clamp(initial, min, max);
        this.clampEnabled = true;
        this.min = Math.min(min, max);
        this.max = Math.max(min, max);
    }

    @Override
    public void set(Double v) {
        value = clampEnabled ? MathUtil.clamp(v, min, max) : v;
    }

    @Override
    public Double peek() {
        return value;
    }

    /**
     * As a Source: returns the raw setpoint (filters will shape it using dt).
     */
    @Override
    public Double get(FrameClock clock) {
        return value;
    }
}

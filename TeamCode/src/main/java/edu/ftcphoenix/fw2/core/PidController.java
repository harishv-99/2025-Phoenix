package edu.ftcphoenix.fw2.core;

/**
 * Error-centric PID-like API. Implementations may keep internal state.
 *
 * <p>Defines the minimal contract required by controllers. The interface is intentionally narrow
 * so it can be implemented by classic PID, PI-only, PD-only, or model-based regulators that
 * still accept an error and dt.</p>
 *
 * <h2>When to use</h2>
 * <ul>
 *   <li>As the controller input for {@link FeedbackController} or any loop computing corrections.</li>
 * </ul>
 *
 * <h2>Notes</h2>
 * <ul>
 *   <li>{@link #updateFrom(double, DoubleSetpoint, double)} uses {@link DoubleSetpoint#peek()} intentionally
 *       to avoid injecting frame timing into the setpoint read.</li>
 *   <li>Use {@link #reset()} when (re)initializing mechanisms to clear integral/derivative state.</li>
 * </ul>
 */
public interface PidController {
    /**
     * Core: compute correction from error and dt (seconds).
     */
    double update(double error, double dtSec);

    /**
     * Convenience: compute error from measurement & simple setpoint.
     */
    default double updateFrom(double measurement, DoubleSetpoint setpoint, double dtSec) {
        return update(setpoint.peek() - measurement, dtSec); // peek() is correct here
    }

    /**
     * Convenience: compute error from measurement & a frame-driven Source setpoint.
     */
    default double updateFrom(double measurement, Source<Double> setpoint, FrameClock clock) {
        return update(setpoint.get(clock) - measurement, clock.dtSec());
    }

    /**
     * Optional lifecycle hook.
     */
    default void reset() {
    }
}

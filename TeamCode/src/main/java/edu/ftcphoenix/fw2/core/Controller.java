package edu.ftcphoenix.fw2.core;

/**
 * A clock-aware component that updates once per frame.
 *
 * <p>Implementations should perform minimal, deterministic work based on the provided
 * {@link FrameClock} and avoid blocking. Use this for subsystems, control loops, and
 * coordinators that must be ticked in a known order.</p>
 *
 * <h2>When to use</h2>
 * <ul>
 *   <li>Anything that needs periodic updates tied to a shared loop clock.</li>
 *   <li>PID loops, sensor fusion, estimators, feedforward schedulers, etc.</li>
 * </ul>
 */
public interface Controller {
    /** clock-aware update */
    void update(FrameClock clock);
}
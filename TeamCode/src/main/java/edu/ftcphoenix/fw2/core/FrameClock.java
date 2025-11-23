package edu.ftcphoenix.fw2.core;

/**
 * Monotonic frame timing source passed to controllers and sources.
 *
 * <p>Provides both an absolute, monotonic nanosecond timestamp and the elapsed time since the
 * previous frame. All control code should use this instead of wall-clock time to ensure
 * determinism and correct dt handling.</p>
 *
 * <h2>When to use</h2>
 * <ul>
 *   <li>Every control/update call should receive the same {@link FrameClock} instance for that frame.</li>
 * </ul>
 */
public interface FrameClock {
    /** Monotonic, frame-stamped timestamp (nanoseconds). */
    long nanoTime();

    /** Elapsed seconds since the previous frame. */
    double dtSec();
}
package edu.ftcphoenix.fw.hal;

/**
 * Minimal power-based motor port for framework code.
 *
 * <h3>Best practices baked in</h3>
 * <ul>
 *   <li>Power is normalized in [-1, +1]. Implementations should clamp inputs.</li>
 *   <li>Non-blocking: calls must return quickly; no sleeps/polls inside.</li>
 *   <li>Threading: FTC loop is single-threaded; avoid synchronization here.</li>
 * </ul>
 */
public interface Motor {
    /** Set normalized power in [-1, +1]. Implementations should clamp. */
    void setPower(double power);

    /** Optional: last commanded power (for telemetry / debug). */
    double getLastPower();
}

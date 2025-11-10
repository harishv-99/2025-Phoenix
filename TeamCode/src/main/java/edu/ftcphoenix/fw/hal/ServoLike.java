package edu.ftcphoenix.fw.hal;

/**
 * Abstract positional actuator with a normalized position in [0, 1].
 *
 * <p>Use for things like gates, pushers, and wrist setpoints. For continuous
 * rotation servos, implement {@link edu.ftcphoenix.fw.hal.Motor} instead.</p>
 */
public interface ServoLike {
    /**
     * Set normalized position in [0, 1]. Implementations should clamp.
     */
    void setPosition(double position);

    /**
     * Optional: last commanded position (for telemetry / debug).
     */
    double getLastPosition();
}

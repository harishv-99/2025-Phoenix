package edu.ftcphoenix.fw.hal;

import edu.ftcphoenix.fw.adapters.ftc.FtcHardware;

/**
 * Positional servo output abstraction with position in [0, 1].
 *
 * <p>Platform-specific devices (e.g., FTC SDK {@code Servo}) should be wrapped
 * behind this interface (see
 * {@link FtcHardware#servo}).</p>
 */
public interface ServoOutput {
    /**
     * Command a new servo position.
     *
     * @param position normalized position in [0, 1]
     */
    void setPosition(double position);

    /**
     * @return last commanded (and possibly clamped) position.
     */
    double getLastPosition();
}

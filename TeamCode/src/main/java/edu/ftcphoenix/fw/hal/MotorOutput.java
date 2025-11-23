package edu.ftcphoenix.fw.hal;

import edu.ftcphoenix.fw.adapters.ftc.FtcHardware;

/**
 * Power-based motor output abstraction with normalized power in [-1, +1].
 *
 * <p>Platform-specific devices (e.g., FTC SDK {@code DcMotorEx}) should be
 * wrapped behind this interface (see
 * {@link FtcHardware#motor}).</p>
 */
public interface MotorOutput {
    /**
     * Command a new power level.
     *
     * @param power normalized power, typically in [-1, +1]
     */
    void setPower(double power);

    /**
     * @return last commanded (and possibly clamped) power value.
     */
    double getLastPower();
}

package edu.ftcphoenix.fw2.platform;

public interface PowerActuator {
    /** Write normalized power in [0,1] (or clamp internally). */
    void setPower(double power);
    double getLastPower();
}
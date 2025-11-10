package edu.ftcphoenix.fw2.platform;

public interface RotationActuator {
    /** Write normalized turn command in [-1, 1]. */
    void setRotate(double turn);
    /** Last commanded turn. */
    double getLastRotate();
}

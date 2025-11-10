package edu.ftcphoenix.fw2.drive.hw;

public interface DriveIO {
    /**
     * Apply individual wheel powers in [-1, 1]. Order is FL, FR, BL, BR.
     */
    void setWheelPowers(double fl, double fr, double bl, double br);

    /**
     * Returns the last set of wheel powers used. Order is FL, FR, BL, BR.
     */
    double[] getLastWheelPowers();

    /**
     * Optional heading (rad). Return Double.NaN if unavailable.
     */
    double getHeadingRad();

    /**
     * Optional battery voltage. Return Double.NaN if unavailable.
     */
    double getVoltage();

    /**
     * Optional total drive current (A) or Double.NaN if unavailable.
     */
    double getCurrentAmps();

    /**
     * Stop (e.g., set all powers to zero).
     */
    default void stop() {
        setWheelPowers(0, 0, 0, 0);
    }

    default public boolean hasImu() {
        return !Double.isNaN(getHeadingRad());
    }

    default public boolean hasVoltage() {
        return !Double.isNaN(getVoltage());
    }

}

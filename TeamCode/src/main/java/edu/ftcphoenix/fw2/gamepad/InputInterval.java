package edu.ftcphoenix.fw2.gamepad;

public interface InputInterval {
    /**
     * Get the value in the interval.
     * @return The current value.
     */
    double getValue();

    /**
     * Get the maximum value for the interval's range.
     * @return The max value of the range.
     */
    double getIntervalRangeMax();

    /**
     * Get the minimmum value for the interval's range.
     * @return The min value of the range.
     */
    double getIntervalRangeMin();
}

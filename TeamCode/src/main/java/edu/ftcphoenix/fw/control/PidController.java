package edu.ftcphoenix.fw.control;

public interface PidController {
    double update(double error, double dtSec);

    default void reset() {
    }
}

package edu.ftcphoenix.fw.core.math;

/**
 * Unit helpers.
 */
public final class Units {
    private Units() {
    }

    public static double rpmToRadPerSec(double rpm) {
        return rpm * (2.0 * Math.PI) / 60.0;
    }
}

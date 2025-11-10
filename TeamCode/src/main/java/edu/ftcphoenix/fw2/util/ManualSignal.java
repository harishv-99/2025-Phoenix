package edu.ftcphoenix.fw2.util;

import java.util.function.DoubleSupplier;

/**
 * Simple, writable scalar signal.
 */
public class ManualSignal implements DoubleSupplier {
    private double value;

    public ManualSignal(double initial) {
        this.value = initial;
    }

    public void set(double value) {
        this.value = value;
    }

    @Override
    public double getAsDouble() {
        return value;
    }

    public double get() {
        return value;
    } // alias for readability
}

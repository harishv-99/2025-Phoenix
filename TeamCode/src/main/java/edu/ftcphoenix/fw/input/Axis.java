package edu.ftcphoenix.fw.input;

import java.util.function.BooleanSupplier;

/**
 * Continuous input in [-1..+1] or [0..1].
 */
public interface Axis {
    /** Latest value. */
    double get();

    /**
     * Threshold this axis into a digital button without exposing any registry.
     * Threshold in [0..1].
     */
    default Button asButton(final double threshold) {
        final Axis self = this;
        return new Button() {
            public boolean isPressed() { return self.get() >= threshold; }
        };
    }

    /** Factory for an Axis backed by a DoubleSupplier. */
    static Axis of(final java.util.function.DoubleSupplier supplier) {
        return new Axis() { public double get() { return supplier.getAsDouble(); } };
    }
}

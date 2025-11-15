package edu.ftcphoenix.fw.input;

/** Simple digital input. Edge detection is handled by Bindings. */
public interface Button {
    /** Whether the button is currently pressed. */
    boolean isPressed();

    /** Factory for a Button backed by a BooleanSupplier. */
    static Button of(final java.util.function.BooleanSupplier supplier) {
        return new Button() { public boolean isPressed() { return supplier.getAsBoolean(); } };
    }
}

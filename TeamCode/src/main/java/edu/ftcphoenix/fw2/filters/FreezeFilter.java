package edu.ftcphoenix.fw2.filters;

import java.util.function.BooleanSupplier;

/**
 * Freezes a filter when disabled: returns the last computed output.
 * On the first call while disabled, it returns the current input (no history).
 */
public final class FreezeFilter<T> implements Filter<T> {
    private final Filter<T> inner;
    private final BooleanSupplier enabled;
    private T lastOut;
    private boolean hasLast = false;

    public FreezeFilter(Filter<T> inner, BooleanSupplier enabled) {
        this.inner = inner; this.enabled = enabled;
    }

    @Override public T apply(T in, double dt) {
        if (enabled.getAsBoolean()) {
            lastOut = inner.apply(in, dt);
            hasLast = true;
            return lastOut;
        }
        return hasLast ? lastOut : in;
    }
}

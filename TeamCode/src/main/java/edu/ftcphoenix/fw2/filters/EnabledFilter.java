package edu.ftcphoenix.fw2.filters;

import java.util.function.BooleanSupplier;

/**
 * Wraps a Filter<T> and bypasses it (returns input) when disabled.
 * <p>
 * Use when:
 * - You want to toggle a single filter without changing its internals.
 * - Keep filters simple; put enable logic here.
 */
public final class EnabledFilter<T> implements Filter<T> {
    private final Filter<T> inner;
    private final BooleanSupplier enabled;

    public EnabledFilter(Filter<T> inner, BooleanSupplier enabled) {
        this.inner = inner;
        this.enabled = enabled;
    }

    @Override
    public T apply(T in, double dt) {
        return enabled.getAsBoolean() ? inner.apply(in, dt) : in;
    }
}

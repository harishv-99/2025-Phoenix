package edu.ftcphoenix.fw2.sensing.impl;

import java.util.function.DoubleSupplier;

import edu.ftcphoenix.fw2.sensing.DoubleFeedbackSource;
import edu.ftcphoenix.fw2.sensing.FeedbackSample;

/**
 * DoubleSupplierSource — adapts a {@link DoubleSupplier} into a {@link DoubleFeedbackSource}.
 *
 * <p><b>When to use:</b></p>
 * <ul>
 *   <li>To expose any simple numeric reading (lambda or method ref) as a time-stamped feedback source.</li>
 *   <li>Great for computed signals (e.g., pose fields, cached sensors) or simulation stubs.</li>
 * </ul>
 *
 * <p><b>Behavior:</b></p>
 * <ul>
 *   <li>Marks samples as {@code valid=true} unconditionally; supply {@link Double#NaN} from the supplier
 *       if you need to encode “invalid”.</li>
 *   <li>Does not do any filtering or range checks; compose filters upstream if needed.</li>
 * </ul>
 */
public final class DoubleSupplierSource implements DoubleFeedbackSource {
    private final DoubleSupplier supplier;

    /**
     * Create an adapter around a {@link DoubleSupplier}.
     *
     * @param supplier source of the current value (should be fast and side-effect free)
     */
    public DoubleSupplierSource(DoubleSupplier supplier) {
        this.supplier = supplier;
    }

    /**
     * Convenience factory.
     *
     * @param supplier source of the current value
     * @return a new {@code DoubleSupplierSource}
     */
    public static DoubleSupplierSource of(DoubleSupplier supplier) {
        return new DoubleSupplierSource(supplier);
    }

    @Override
    public FeedbackSample<Double> sample(long nanoTime) {
        return new FeedbackSample<>(true, supplier.getAsDouble(), nanoTime);
    }
}

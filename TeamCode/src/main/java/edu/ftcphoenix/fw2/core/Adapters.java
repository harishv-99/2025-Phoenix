package edu.ftcphoenix.fw2.core;

import java.util.function.DoubleSupplier;

import edu.ftcphoenix.fw2.sensing.DoubleFeedbackSource;
import edu.ftcphoenix.fw2.sensing.FeedbackSample;

/**
 * Canonical adapters between framework {@link Source} / feedback sources and Java functional types.
 *
 * <p>Use these helpers to bridge Phoenix's pull-based, clock-aware APIs with standard Java
 * functional interfaces when integrating with libraries, tests, or simple lambdas.</p>
 *
 * <h2>When to use</h2>
 * <ul>
 *   <li>Expose a {@link Source} as a {@link DoubleSupplier} for simple consumers that don’t care about {@link FrameClock}.</li>
 *   <li>Wrap a {@link DoubleSupplier} or a timestamped sensor as a {@link Source} to participate in your frame loop.</li>
 *   <li>Surface full {@link FeedbackSample} streams when downstream logic needs validity/timestamps.</li>
 * </ul>
 */
public final class Adapters {
    private Adapters() {
    }

    /* ---------- DoubleSupplier ↔ Source<Double> ---------- */

    /**
     * Wrap a {@link DoubleSupplier} as a {@link Source}&lt;Double&gt; (no dt usage).
     * <p>Useful to pull joystick or simple numeric inputs inside the frame loop.</p>
     */
    public static Source<Double> sourceFrom(DoubleSupplier sup) {
        return clock -> sup.getAsDouble();
    }

    /**
     * Wrap a {@link Source}&lt;Double&gt; as a {@link DoubleSupplier} bound to a fixed {@link FrameClock}.
     * <p><b>Caller responsibility:</b> advance {@code clock} once per loop.</p>
     */
    public static DoubleSupplier supplierFrom(Source<Double> src, FrameClock clock) {
        return () -> src.get(clock);
    }

    /* ---------- DoubleFeedbackSource ↔ Source ---------- */

    /**
     * Treat a timestamped scalar sensor as a {@link Source}&lt;Double&gt; returning the sensor value when valid,
     * otherwise a provided fallback (e.g., 0 or last command).
     */
    public static Source<Double> sourceFrom(DoubleFeedbackSource fb, double fallback) {
        return clock -> fb.sampleOr(fallback, clock.nanoTime());
    }

    /**
     * Treat a timestamped scalar sensor as a {@link Source}&lt;Double&gt; that holds the last valid value.
     * <p>Returns {@code initial} until the first valid sample, then holds the most recent valid sample.</p>
     */
    public static Source<Double> sourceHolding(DoubleFeedbackSource fb, double initial) {
        return new Source<Double>() {
            private double last = initial;

            @Override
            public Double get(FrameClock clock) {
                FeedbackSample<Double> s = fb.sample(clock.nanoTime());
                if (s.valid) last = s.value;
                return last;
            }
        };
    }

    /**
     * Expose the full {@link FeedbackSample} stream as a {@link Source}.
     * <p>Useful when downstream code cares about validity/timestamp.</p>
     */
    public static Source<FeedbackSample<Double>> sourceAsSample(DoubleFeedbackSource fb) {
        return clock -> fb.sample(clock.nanoTime());
    }
}

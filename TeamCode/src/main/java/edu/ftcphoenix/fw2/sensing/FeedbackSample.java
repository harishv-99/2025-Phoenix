package edu.ftcphoenix.fw2.sensing;

/**
 * Immutable feedback sample carrying validity, value, and timestamp.
 *
 * <p>Used by feedback sources to report a (possibly invalid) reading captured
 * at a specific {@code System.nanoTime()} instant.</p>
 *
 * @param <T> value type (e.g., {@link Double}, a pose struct, etc.)
 */
public final class FeedbackSample<T> {

    /**
     * True if this sample is based on a valid measurement at this poll.
     */
    public final boolean valid;

    /**
     * The measured value (meaningful only when {@link #valid} is true).
     */
    public final T value;

    /**
     * Capture time in nanoseconds from {@link System#nanoTime()}.
     */
    public final long nanoTime;

    /**
     * Create a new feedback sample.
     *
     * @param valid    whether the measurement is valid
     * @param value    measured value (undefined if {@code valid == false})
     * @param nanoTime timestamp from {@link System#nanoTime()}
     */
    public FeedbackSample(boolean valid, T value, long nanoTime) {
        this.valid = valid;
        this.value = value;
        this.nanoTime = nanoTime;
    }
}

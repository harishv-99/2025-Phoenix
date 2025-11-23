package edu.ftcphoenix.fw2.sensing;

/** A numeric source (Double) with convenience accessors. */
public interface DoubleFeedbackSource extends FeedbackSource<Double> {
    @Override
    FeedbackSample<Double> sample(long nanoTime);

    /** Returns value if valid, else fallback (does not cache). */
    default double sampleOr(double fallback, long nanoTime) {
        FeedbackSample<Double> s = sample(nanoTime);
        return s.valid ? s.value : fallback;
    }

    /** Returns value or NaN if invalid. */
    default double sampleOrNaN(long nanoTime) {
        FeedbackSample<Double> s = sample(nanoTime);
        return s.valid ? s.value : Double.NaN;
    }
}
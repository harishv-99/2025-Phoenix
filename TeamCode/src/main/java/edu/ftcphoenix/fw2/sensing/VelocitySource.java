package edu.ftcphoenix.fw2.sensing;

public interface VelocitySource extends DoubleFeedbackSource {
    FeedbackSample<Double> getVelocity(long nanoTime);

    default double getVelocityOrNan(long nanoTime) {
        return sampleOrNaN(nanoTime);
    }

    @Override
    default FeedbackSample<Double> sample(long nanoTime) {
        return getVelocity(nanoTime);
    }
}

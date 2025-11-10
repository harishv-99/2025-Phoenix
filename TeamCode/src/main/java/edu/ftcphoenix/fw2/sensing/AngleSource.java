package edu.ftcphoenix.fw2.sensing;

public interface AngleSource extends DoubleFeedbackSource {
    FeedbackSample<Double> getAngle(long nanoTime);

    default double getAngleOrNaN(long nanoTime) {
        return sampleOrNaN(nanoTime);
    }

    @Override
    default FeedbackSample<Double> sample(long nanoTime) {
        return getAngle(nanoTime);
    }
}

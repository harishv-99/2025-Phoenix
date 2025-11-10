package edu.ftcphoenix.fw2.sensing;

/** Generic self-describing feedback source. */
public interface FeedbackSource<T> {
    FeedbackSample<T> sample(long nanoTime);
}
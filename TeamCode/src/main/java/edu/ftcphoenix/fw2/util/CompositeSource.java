package edu.ftcphoenix.fw2.util;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import edu.ftcphoenix.fw2.sensing.FeedbackSample;
import edu.ftcphoenix.fw2.sensing.DoubleFeedbackSource;

/**
 * Combines multiple FeedbackSources via average, weighted average, min, or max.
 * Useful for dual encoders, redundant sensors, or sensor fusion (simple form).
 */
public class CompositeSource implements DoubleFeedbackSource {
    public enum Mode { AVERAGE, WEIGHTED_AVERAGE, MIN, MAX }

    private final Mode mode;
    private final List<DoubleFeedbackSource> sources;
    private final double[] weights; // nullable unless WEIGHTED_AVERAGE

    public CompositeSource(Mode mode, List<DoubleFeedbackSource> sources) {
        this(mode, sources, null);
    }

    public CompositeSource(Mode mode, List<DoubleFeedbackSource> sources, double[] weights) {
        if (sources == null || sources.isEmpty())
            throw new IllegalArgumentException("sources must not be empty");
        this.mode = Objects.requireNonNull(mode);
        this.sources = new ArrayList<>(sources);
        this.weights = weights;

        if (mode == Mode.WEIGHTED_AVERAGE) {
            if (weights == null || weights.length != sources.size())
                throw new IllegalArgumentException("weights must match sources size for WEIGHTED_AVERAGE");
            double sum = 0;
            for (double w : weights) sum += w;
            if (Math.abs(sum) < 1e-12) throw new IllegalArgumentException("weights sum must be non-zero");
        }
    }

    @Override
    public FeedbackSample<Double> sample(long nanoTime) {
        long t = System.nanoTime();

        switch (mode) {
            case AVERAGE:
                return new FeedbackSample<>(true, average(nanoTime), nanoTime);
            case WEIGHTED_AVERAGE:
                return new FeedbackSample(true, weightedAverage(nanoTime), nanoTime);
            case MIN:
                return new FeedbackSample<>(true, min(nanoTime), nanoTime);
            case MAX: return new FeedbackSample<>(true, max(nanoTime), nanoTime);
            default: throw new IllegalStateException("Unknown mode: " + mode);
        }
    }

    private double average(long nanoTime) {
        double s = 0;
        int validSize = 0;
        for (DoubleFeedbackSource fs : sources) {
            FeedbackSample<Double> sample = fs.sample(nanoTime);
            if (sample.valid) {
                s += sample.value.doubleValue();
                validSize++;
            }
        }
        return s / validSize;
    }

    private double weightedAverage(long nanoTime) {
        double num = 0, den = 0;
        for (int i = 0; i < sources.size(); i++) {
            double w = weights[i];
            num += w * sources.get(i).sampleOr(0, nanoTime);
            den += w;
        }
        return num / den;
    }

    private double min(long nanoTime) {
        double v = Double.POSITIVE_INFINITY;
        for (DoubleFeedbackSource fs : sources) {
            v = Math.min(v, fs.sampleOrNaN(nanoTime));
        }
        return v;
    }

    private double max(long nanoTime) {
        double v = Double.NEGATIVE_INFINITY;
        for (DoubleFeedbackSource fs : sources) v = Math.max(v, fs.sampleOrNaN(nanoTime));
        return v;
    }
}

package edu.ftcphoenix.fw2.actuation;

public interface AngleEffortSink extends EffortSink {
    void applyAngle(double angle);

    @Override
    default void applyEffort(double effort) { applyAngle(effort); }
}

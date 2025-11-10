package edu.ftcphoenix.fw2.actuation;

public interface VelocityEffortSink extends EffortSink {
    void applyVelocity(double velocity);

    @Override
    default void applyEffort(double effort) { applyVelocity(effort); }
}

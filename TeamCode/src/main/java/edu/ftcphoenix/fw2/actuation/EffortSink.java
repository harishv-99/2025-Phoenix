package edu.ftcphoenix.fw2.actuation;

public interface EffortSink {
    /** effort meaning is subsystem-defined (power, torque, velocity cmd, etc.) */
    void applyEffort(double effort);
}

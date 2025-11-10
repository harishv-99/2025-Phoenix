package edu.ftcphoenix.fw2.platform;

import java.util.List;
import edu.ftcphoenix.fw2.actuation.VelocityEffortSink;

public class MultiMotorSink implements VelocityEffortSink {
    private final List<VelocityEffortSink> sinks;

    public MultiMotorSink(List<VelocityEffortSink> sinks) { this.sinks = sinks; }

    @Override
    public void applyVelocity(double velocity) {
        for (VelocityEffortSink s : sinks) s.applyVelocity(velocity);
    }
}

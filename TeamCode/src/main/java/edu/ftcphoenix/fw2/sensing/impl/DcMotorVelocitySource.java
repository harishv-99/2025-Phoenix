package edu.ftcphoenix.fw2.sensing.impl;

import com.qualcomm.robotcore.hardware.DcMotorEx;

import edu.ftcphoenix.fw2.sensing.FeedbackSample;
import edu.ftcphoenix.fw2.sensing.VelocitySource;

public class DcMotorVelocitySource implements VelocitySource {
    private final DcMotorEx motor;
    private final double ticksPerRev;

    public DcMotorVelocitySource(DcMotorEx motor, double ticksPerRev) {
        this.motor = motor;
        this.ticksPerRev = ticksPerRev;
    }

    @Override
    public FeedbackSample<Double> getVelocity(long nanoTime) {
        double tps = motor.getVelocity(); // ticks/sec
        double rpm = (tps * 60.0) / ticksPerRev; // RPM
        return new FeedbackSample<>(true, rpm, nanoTime);
    }
}

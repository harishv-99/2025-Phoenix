package edu.ftcphoenix.fw2.actuation;

import com.qualcomm.robotcore.hardware.DcMotorEx;

public class DcMotorVelocitySink implements VelocityEffortSink {
    private final DcMotorEx motor;
    private final double ticksPerRev;

    public DcMotorVelocitySink(DcMotorEx motor, double ticksPerRev) {
        this.motor = motor;
        this.ticksPerRev = ticksPerRev;
    }

    @Override
    public void applyVelocity(double velocityRpm) {
        double tps = (velocityRpm / 60.0) * ticksPerRev;
        motor.setVelocity(tps);
    }
}

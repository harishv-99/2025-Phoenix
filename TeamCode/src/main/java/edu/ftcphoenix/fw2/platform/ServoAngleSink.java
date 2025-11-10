package edu.ftcphoenix.fw2.platform;

import com.qualcomm.robotcore.hardware.Servo;
import edu.ftcphoenix.fw2.actuation.AngleEffortSink;

/**
 * Applies an angular setpoint to a Servo.
 * Converts input degrees to normalized [0,1] servo position.
 */
public class ServoAngleSink implements AngleEffortSink {
    private final Servo servo;
    private final double minDeg;
    private final double maxDeg;

    public ServoAngleSink(Servo servo, double minDeg, double maxDeg) {
        this.servo = servo;
        this.minDeg = minDeg;
        this.maxDeg = maxDeg;
    }

    @Override
    public void applyAngle(double angle) {
        double clamped = Math.max(minDeg, Math.min(maxDeg, angle));
        double pos = (clamped - minDeg) / (maxDeg - minDeg);
        servo.setPosition(pos);
    }
}

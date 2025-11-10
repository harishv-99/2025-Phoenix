package edu.ftcphoenix.fw.adapters.ftc;

import com.qualcomm.robotcore.hardware.Servo;

import edu.ftcphoenix.fw.hal.ServoLike;

/**
 * Servo → {@link ServoLike} (position 0..1).
 * <p>Optional inversion maps pos to (1-pos) to match geometry.</p>
 */
public final class FtcServoLike implements ServoLike {
    private final Servo servo;
    private final boolean inverted;
    private double last = 0.5;

    public FtcServoLike(Servo servo, boolean inverted) {
        this.servo = servo;
        this.inverted = inverted;
    }

    @Override
    public void setPosition(double position) {
        double p = Math.max(0, Math.min(1, position));
        if (inverted) p = 1.0 - p;
        last = p;
        servo.setPosition(p);
    }

    @Override
    public double getLastPosition() {
        return last;
    }
}

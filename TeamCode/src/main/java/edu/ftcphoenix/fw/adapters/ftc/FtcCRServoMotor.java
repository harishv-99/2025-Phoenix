package edu.ftcphoenix.fw.adapters.ftc;

import com.qualcomm.robotcore.hardware.CRServo;

import edu.ftcphoenix.fw.hal.Motor;

/**
 * CRServo → {@link Motor} (power), with optional inversion.
 */
public final class FtcCRServoMotor implements Motor {
    private final CRServo servo;
    private final boolean inverted;
    private double last = 0.0;

    public FtcCRServoMotor(CRServo servo, boolean inverted) {
        this.servo = servo;
        this.inverted = inverted;
    }

    @Override
    public void setPower(double power) {
        double p = Math.max(-1, Math.min(1, power));
        if (inverted) p = -p;
        last = p;
        servo.setPower(p);
    }

    @Override
    public double getLastPower() {
        return last;
    }
}

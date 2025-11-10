package edu.ftcphoenix.fw.adapters.ftc;

import com.qualcomm.robotcore.hardware.DcMotorEx;

import edu.ftcphoenix.fw.hal.Motor;

/**
 * DcMotorEx → {@link Motor} (power in [-1,+1]).
 * <p>Non-blocking; clamps input; optional inversion for wiring parity.</p>
 */
public final class FtcMotor implements Motor {
    private final DcMotorEx motor;
    private final boolean inverted;
    private double last = 0.0;

    public FtcMotor(DcMotorEx motor, boolean inverted) {
        this.motor = motor;
        this.inverted = inverted;
    }

    @Override
    public void setPower(double power) {
        double p = Math.max(-1, Math.min(1, power));
        if (inverted) p = -p;
        last = p;
        motor.setPower(p);
    }

    @Override
    public double getLastPower() {
        return last;
    }
}

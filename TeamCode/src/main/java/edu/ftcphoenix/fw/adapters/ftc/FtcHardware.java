package edu.ftcphoenix.fw.adapters.ftc;

import com.qualcomm.robotcore.hardware.CRServo;
import com.qualcomm.robotcore.hardware.DcMotorEx;
import com.qualcomm.robotcore.hardware.HardwareMap;
import com.qualcomm.robotcore.hardware.Servo;

import edu.ftcphoenix.fw.hal.MotorOutput;
import edu.ftcphoenix.fw.hal.ServoOutput;
import edu.ftcphoenix.fw.util.MathUtil;

/**
 * Adapters from FTC SDK devices to Phoenix HAL output interfaces.
 *
 * <p>The {@code adapters.ftc} package (this class, {@link FtcPlants},
 * {@link FtcVision}, etc.) is where the Phoenix framework talks
 * directly to FTC SDK hardware classes. All other framework code
 * should depend on {@link MotorOutput}, {@link ServoOutput},
 * {@link edu.ftcphoenix.fw.actuation.Plant}, and other framework
 * interfaces instead.</p>
 *
 * <h2>Inversion</h2>
 * <p>Each factory accepts an {@code inverted} flag. When true, the
 * logical sign of the command is flipped (e.g., power is negated),
 * allowing you to fix wiring or configuration differences without
 * sprinkling minus signs throughout your robot code.</p>
 */
public final class FtcHardware {

    private FtcHardware() {
        // utility class
    }

    /**
     * Wrap an FTC {@link DcMotorEx} as a normalized {@link MotorOutput}.
     *
     * @param hw       hardware map
     * @param name     configured device name
     * @param inverted whether to invert the power sign
     */
    public static MotorOutput motor(HardwareMap hw, String name, boolean inverted) {
        final DcMotorEx m = hw.get(DcMotorEx.class, name);
        return new MotorOutput() {
            private double last;

            @Override
            public void setPower(double p) {
                double cmd = MathUtil.clampAbs(inverted ? -p : p, 1.0);
                last = cmd;
                m.setPower(cmd);
            }

            @Override
            public double getLastPower() {
                return last;
            }
        };
    }

    /**
     * Wrap an FTC {@link CRServo} as a normalized {@link MotorOutput}.
     *
     * <p>Useful when you want to treat a continuous rotation servo as a motor
     * in the Phoenix HAL.</p>
     */
    public static MotorOutput crServoMotor(HardwareMap hw, String name, boolean inverted) {
        final CRServo s = hw.get(CRServo.class, name);
        return new MotorOutput() {
            private double last;

            @Override
            public void setPower(double p) {
                double cmd = MathUtil.clampAbs(inverted ? -p : p, 1.0);
                last = cmd;
                s.setPower(cmd);
            }

            @Override
            public double getLastPower() {
                return last;
            }
        };
    }

    /**
     * Wrap an FTC {@link Servo} as a normalized {@link ServoOutput}.
     *
     * @param hw       hardware map
     * @param name     configured device name
     * @param inverted whether to invert the position (p → 1 - p)
     */
    public static ServoOutput servo(HardwareMap hw, String name, boolean inverted) {
        final Servo s = hw.get(Servo.class, name);
        return new ServoOutput() {
            private double last;

            @Override
            public void setPosition(double pos) {
                double p = inverted ? (1.0 - pos) : pos;
                double cmd = MathUtil.clamp01(p);
                last = cmd;
                s.setPosition(cmd);
            }

            @Override
            public double getLastPosition() {
                return last;
            }
        };
    }
}

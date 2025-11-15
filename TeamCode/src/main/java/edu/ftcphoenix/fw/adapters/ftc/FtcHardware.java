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
 * <p>This is the only place where the Phoenix framework knows about FTC SDK
 * hardware classes. All other code should depend on {@link MotorOutput} and
 * {@link ServoOutput} instead.</p>
 *
 * <h2>Inversion</h2>
 * <ul>
 *   <li>Motors: {@code inverted == true} inverts the sign of the power.</li>
 *   <li>Servos: {@code inverted == true} maps position {@code p} to
 *       {@code 1 - p} before writing.</li>
 * </ul>
 *
 * <p>All methods clamp power/position to their expected ranges:
 * <ul>
 *   <li>Motors: [-1, +1]</li>
 *   <li>Servos: [0, 1]</li>
 * </ul>
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

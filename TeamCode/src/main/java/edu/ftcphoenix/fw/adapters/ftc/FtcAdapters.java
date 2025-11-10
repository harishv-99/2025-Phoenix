package edu.ftcphoenix.fw.adapters.ftc;

import com.qualcomm.robotcore.hardware.CRServo;
import com.qualcomm.robotcore.hardware.DcMotorEx;
import com.qualcomm.robotcore.hardware.DigitalChannel;
import com.qualcomm.robotcore.hardware.HardwareMap;
import com.qualcomm.robotcore.hardware.Servo;

import edu.ftcphoenix.fw.hal.Motor;
import edu.ftcphoenix.fw.hal.ServoLike;
import edu.ftcphoenix.fw.hal.BeamBreak;
import edu.ftcphoenix.fw.stages.shooter.ShooterStage;

/**
 * Small factory helpers to reduce robot-layer boilerplate.
 * <p>All methods are thin passthroughs; they do not change SDK modes. Configure modes in your OpMode init.</p>
 */
public final class FtcAdapters {
    private FtcAdapters() {
    }

    public static Motor motor(HardwareMap hw, String name, boolean inverted) {
        return new FtcMotor(hw.get(DcMotorEx.class, name), inverted);
    }

    public static Motor crServoMotor(HardwareMap hw, String name, boolean inverted) {
        return new FtcCRServoMotor(hw.get(CRServo.class, name), inverted);
    }

    public static ServoLike servoLike(HardwareMap hw, String name, boolean inverted) {
        return new FtcServoLike(hw.get(Servo.class, name), inverted);
    }

    public static BeamBreak beamBreak(HardwareMap hw, String name, boolean activeLow) {
        return new FtcBeamBreakDigital(hw.get(DigitalChannel.class, name), activeLow);
    }

    public static ShooterStage.Spooler spooler(HardwareMap hw, String name,
                                               double ticksPerRevEff, double atSpeedTol, boolean inverted) {
        return new FtcSpooler(hw.get(DcMotorEx.class, name), ticksPerRevEff, atSpeedTol, inverted);
    }
}

package edu.ftcphoenix.fw.adapters.ftc;

import com.qualcomm.robotcore.hardware.IMU;

import org.firstinspires.ftc.robotcore.external.navigation.AngleUnit;

import edu.ftcphoenix.fw.hal.ImuYaw;
import edu.ftcphoenix.fw.util.LoopClock;

/**
 * IMU yaw adapter. Wraps {@link IMU#getRobotYawPitchRollAngles()}.
 * <p>Encodes zero-offset internally; returned yaw is (raw - zero).</p>
 */
public final class FtcImuYaw implements ImuYaw {
    private final IMU imu;
    private double zero = 0.0;

    public FtcImuYaw(IMU imu) {
        this.imu = imu;
    }

    @Override
    public double yawRad() {
        double raw = imu.getRobotYawPitchRollAngles().getYaw(AngleUnit.RADIANS);
        return normalize(raw - zero);
    }

    @Override
    public void zero() {
        double raw = imu.getRobotYawPitchRollAngles().getYaw(AngleUnit.RADIANS);
        zero = raw;
    }

    @Override
    public void update(LoopClock clock) { /* IMU is polled on demand */ }

    private static double normalize(double a) {
        while (a > Math.PI) a -= 2 * Math.PI;
        while (a < -Math.PI) a += 2 * Math.PI;
        return a;
    }
}

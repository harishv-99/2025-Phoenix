package edu.ftcphoenix.fw2.sensing.impl;

import com.qualcomm.hardware.bosch.BNO055IMU;

import edu.ftcphoenix.fw2.sensing.AngleSource;
import edu.ftcphoenix.fw2.sensing.FeedbackSample;

/**
 * Reads yaw angle (heading) from a BNO055 IMU.
 * Returns degrees by default.
 */
public class ImuAngleSource implements AngleSource {
    private final BNO055IMU imu;

    public ImuAngleSource(BNO055IMU imu) {
        this.imu = imu;
    }

    @Override
    public FeedbackSample<Double> getAngle(long nanoTime) {
        // Assuming IMU is configured with units = degrees
        double headingDeg = imu.getAngularOrientation().firstAngle;
        return new FeedbackSample<Double>(true, headingDeg, nanoTime);
    }
}

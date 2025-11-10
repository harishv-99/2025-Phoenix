package edu.ftcphoenix.robots.phoenix.pose;

/**
 * Simple joint-space pose for the Phoenix arm stack.
 *
 * <p>Units: radians for angles, meters for vertical lift if applicable. If your arm
 * uses normalized servo positions instead of radians, you can redefine the fields accordingly.</p>
 */
public final class PhoenixPose {
    public final double liftMeters;     // 0 if no linear lift
    public final double shoulderRad;
    public final double wristRad;

    public PhoenixPose(double liftMeters, double shoulderRad, double wristRad) {
        this.liftMeters = liftMeters;
        this.shoulderRad = shoulderRad;
        this.wristRad = wristRad;
    }

    @Override
    public String toString() {
        return "Pose{lift=" + liftMeters + ", shoulder=" + shoulderRad + ", wrist=" + wristRad + "}";
    }
}

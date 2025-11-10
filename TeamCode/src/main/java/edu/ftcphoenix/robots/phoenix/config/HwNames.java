package edu.ftcphoenix.robots.phoenix.config;

/**
 * Centralized hardware map names for this robot instance.
 *
 * <p>Keeping names here avoids string duplication and typos across OpModes.</p>
 */
public final class HwNames {
    private HwNames() {
    }

    // Intake (human pusher)
    public static final String SERVO_PUSHER = "pusher";
    public static final String MOTOR_INTAKE_TRANSPORT = "intakeTransport";
    public static final String SENSOR_ENTRY = "entryBeam";

    // Chamber
    public static final String SERVO_FEED_WINDOW = "feedWindow";
    public static final String MOTOR_CHAMBER = "chamberMotor";
    public static final String SENSOR_CHAMBER = "chamberBeam";

    // Shooter
    public static final String MOTOR_SHOOTER = "shooter";

    // Optional: pose/localization sources
    public static final String IMU = "imu";
    // Add drive motors if your pose provider needs them (Road Runner, etc.)
}

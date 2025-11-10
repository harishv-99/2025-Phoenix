package edu.ftcphoenix.robots.phoenix.config;

/**
 * Tunable constants for this robot instance.
 *
 * <p>These are deliberately explicit to keep the OpMode wiring clean and readable.</p>
 */
public final class Tuning {
    private Tuning() {
    }

    // Shooter spooler
    public static final double SHOOTER_TICKS_PER_REV_AT_WHEEL = 28.0 * 3.0; // e.g., 28 CPR motor * 3:1 upgear
    public static final double SHOOTER_AT_SPEED_TOL_RAD_PER_S = 15.0;
    public static final double SHOOTER_TARGET_RAD_PER_S = 365.0;

    // Basket pose gate
    public static final double BASKET_POS_TOL_METERS = 0.05;
    public static final double BASKET_YAW_TOL_RAD = 0.06;

    // Human pusher intake timing/power
    public static final double PUSH_HOME_BASE = 0.20;
    public static final double PUSH_HOME_STEP = 0.03;
    public static final double PUSH_POS_BASE = 0.55;
    public static final double PUSH_POS_STEP = 0.03;
    public static final double PUSH_FWD_SEC = 0.08;
    public static final double PUSH_RET_SEC = 0.10;
    public static final double PUSH_FEED_PWR = +0.35;
    public static final double REJECT_PWR = -0.60;
    public static final double REJECT_EJECT_S = 0.25;

    // Roller intake
    public static final double ROLLER_IN_PWR = +0.85;
    public static final double ROLLER_OUT_PWR = -0.75;
    public static final double ROLLER_INGEST_S = 0.35;
    public static final double ROLLER_EJECT_S = 0.30;
}

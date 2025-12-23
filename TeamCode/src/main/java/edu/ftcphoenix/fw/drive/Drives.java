package edu.ftcphoenix.fw.drive;

import com.qualcomm.robotcore.hardware.HardwareMap;

import edu.ftcphoenix.fw.core.hal.Direction;
import edu.ftcphoenix.fw.core.hal.PowerOutput;
import edu.ftcphoenix.fw.ftc.FtcHardware;

/**
 * High-level helpers for creating common drivebases.
 *
 * <h2>Beginner entrypoint for mecanum drive</h2>
 *
 * <p>For most teams, this class should be the <b>main way</b> to construct
 * a {@link MecanumDrivebase}. In beginner TeleOp code, you will usually see
 * just a single line:</p>
 *
 * <pre>{@code
 * MecanumDrivebase drive = Drives.mecanum(hardwareMap);
 * }</pre>
 *
 * <p>This call:</p>
 * <ul>
 *   <li>Assumes the <b>standard motor names</b>:
 *       <ul>
 *         <li>{@link #DEFAULT_FRONT_LEFT_MOTOR_NAME}</li>
 *         <li>{@link #DEFAULT_FRONT_RIGHT_MOTOR_NAME}</li>
 *         <li>{@link #DEFAULT_BACK_LEFT_MOTOR_NAME}</li>
 *         <li>{@link #DEFAULT_BACK_RIGHT_MOTOR_NAME}</li>
 *       </ul>
 *   </li>
 *   <li>Applies a sensible default direction pattern (typically right side
 *       {@link Direction#REVERSE}).</li>
 *   <li>Uses {@link MecanumDrivebase.Config#defaults()} for all drive tuning.</li>
 * </ul>
 *
 * <p>If the robot does not drive correctly with this default setup, teams
 * can <b>flip motors</b> without touching any low-level details by changing
 * the per-motor {@link Direction} values:</p>
 *
 * <pre>{@code
 * // Same standard names, but custom directions:
 * MecanumDrivebase drive = Drives.mecanum(
 *         hardwareMap,
 *         Direction.REVERSE,  // frontLeftMotor direction
 *         Direction.REVERSE,  // frontRightMotor direction
 *         Direction.FORWARD,  // backLeftMotor direction
 *         Direction.REVERSE   // backRightMotor direction
 * );
 * }</pre>
 *
 * <p>More advanced teams can:</p>
 *
 * <ul>
 *   <li>Pass a custom {@link MecanumDrivebase.Config} to enable rate limiting or other
 *       tuning options.</li>
 *   <li>Use the overloads that accept custom motor names if they do not
 *       follow the standard naming convention.</li>
 *   <li>Bypass this helper entirely and construct {@link MecanumDrivebase}
 *       directly with {@link FtcHardware#motorPower} and a custom config.</li>
 * </ul>
 *
 * <p>However, for teaching and most examples, <b>prefer using
 * {@link #mecanum(HardwareMap)} or the simple overloads here</b>. This keeps
 * robot code focused on behavior (how the robot should move) instead of
 * wiring details.</p>
 */
public final class Drives {

    private Drives() {
        // utility class; no instances
    }

    // ======================================================================
    // Standard mecanum motor name constants
    // ======================================================================

    /**
     * Default front-left motor name used by {@link #mecanum(HardwareMap)} helpers.
     */
    public static final String DEFAULT_FRONT_LEFT_MOTOR_NAME = "frontLeftMotor";

    /**
     * Default front-right motor name used by {@link #mecanum(HardwareMap)} helpers.
     */
    public static final String DEFAULT_FRONT_RIGHT_MOTOR_NAME = "frontRightMotor";

    /**
     * Default back-left motor name used by {@link #mecanum(HardwareMap)} helpers.
     */
    public static final String DEFAULT_BACK_LEFT_MOTOR_NAME = "backLeftMotor";

    /**
     * Default back-right motor name used by {@link #mecanum(HardwareMap)} helpers.
     */
    public static final String DEFAULT_BACK_RIGHT_MOTOR_NAME = "backRightMotor";

    // ======================================================================
    // Mecanum drive helpers
    // ======================================================================

    /**
     * Create a mecanum drivebase using the <b>standard motor names</b> and a
     * typical direction pattern, with {@link MecanumDrivebase.Config#defaults()}.
     *
     * <ul>
     *   <li>{@link #DEFAULT_FRONT_LEFT_MOTOR_NAME}:  {@link Direction#FORWARD}</li>
     *   <li>{@link #DEFAULT_FRONT_RIGHT_MOTOR_NAME}: {@link Direction#REVERSE}</li>
     *   <li>{@link #DEFAULT_BACK_LEFT_MOTOR_NAME}:   {@link Direction#FORWARD}</li>
     *   <li>{@link #DEFAULT_BACK_RIGHT_MOTOR_NAME}:  {@link Direction#REVERSE}</li>
     * </ul>
     *
     * @param hw FTC hardware map
     */
    public static MecanumDrivebase mecanum(HardwareMap hw) {
        return mecanum(
                hw,
                Direction.FORWARD,
                Direction.REVERSE,
                Direction.FORWARD,
                Direction.REVERSE,
                MecanumDrivebase.Config.defaults()
        );
    }

    /**
     * Create a mecanum drivebase using the <b>standard motor names</b>, but with explicit
     * {@link Direction} values for each motor, and {@link MecanumDrivebase.Config#defaults()}.
     */
    public static MecanumDrivebase mecanum(HardwareMap hw,
                                           Direction frontLeftDirection,
                                           Direction frontRightDirection,
                                           Direction backLeftDirection,
                                           Direction backRightDirection) {
        return mecanum(
                hw,
                frontLeftDirection,
                frontRightDirection,
                backLeftDirection,
                backRightDirection,
                MecanumDrivebase.Config.defaults()
        );
    }

    /**
     * Create a mecanum drivebase with <b>custom motor names</b> and directions,
     * using {@link MecanumDrivebase.Config#defaults()}.
     */
    public static MecanumDrivebase mecanum(HardwareMap hw,
                                           String flName, Direction flDirection,
                                           String frName, Direction frDirection,
                                           String blName, Direction blDirection,
                                           String brName, Direction brDirection) {
        return mecanum(
                hw,
                flName, flDirection,
                frName, frDirection,
                blName, blDirection,
                brName, brDirection,
                MecanumDrivebase.Config.defaults()
        );
    }

    // ------------------------------------------------------------------
    // Overloads that accept a custom MecanumDrivebase.Config (rate limiting, etc.)
    // ------------------------------------------------------------------

    /**
     * Create a mecanum drivebase using standard names, default directions,
     * and a custom {@link MecanumDrivebase.Config}.
     */
    public static MecanumDrivebase mecanum(HardwareMap hw,
                                           MecanumDrivebase.Config config) {
        return mecanum(
                hw,
                Direction.FORWARD,
                Direction.REVERSE,
                Direction.FORWARD,
                Direction.REVERSE,
                config
        );
    }

    /**
     * Create a mecanum drivebase using standard names, explicit directions,
     * and a custom {@link MecanumDrivebase.Config}.
     */
    public static MecanumDrivebase mecanum(HardwareMap hw,
                                           Direction frontLeftDirection,
                                           Direction frontRightDirection,
                                           Direction backLeftDirection,
                                           Direction backRightDirection,
                                           MecanumDrivebase.Config config) {
        return mecanum(
                hw,
                DEFAULT_FRONT_LEFT_MOTOR_NAME, frontLeftDirection,
                DEFAULT_FRONT_RIGHT_MOTOR_NAME, frontRightDirection,
                DEFAULT_BACK_LEFT_MOTOR_NAME, backLeftDirection,
                DEFAULT_BACK_RIGHT_MOTOR_NAME, backRightDirection,
                config
        );
    }

    /**
     * Create a mecanum drivebase with custom names, directions, and config.
     */
    public static MecanumDrivebase mecanum(HardwareMap hw,
                                           String flName, Direction flDirection,
                                           String frName, Direction frDirection,
                                           String blName, Direction blDirection,
                                           String brName, Direction brDirection,
                                           MecanumDrivebase.Config config) {
        if (hw == null) {
            throw new IllegalArgumentException("HardwareMap is required");
        }
        if (flName == null || frName == null || blName == null || brName == null) {
            throw new IllegalArgumentException("All motor names are required");
        }
        if (flDirection == null || frDirection == null || blDirection == null || brDirection == null) {
            throw new IllegalArgumentException("All motor directions are required");
        }

        PowerOutput fl = FtcHardware.motorPower(hw, flName, flDirection);
        PowerOutput fr = FtcHardware.motorPower(hw, frName, frDirection);
        PowerOutput bl = FtcHardware.motorPower(hw, blName, blDirection);
        PowerOutput br = FtcHardware.motorPower(hw, brName, brDirection);

        MecanumDrivebase.Config cfg = (config != null) ? config : MecanumDrivebase.Config.defaults();
        return new MecanumDrivebase(fl, fr, bl, br, cfg);
    }

    // ----------------------------------------------------------------------
    // Other drive helpers can live here as needed.
    // ----------------------------------------------------------------------
}

package edu.ftcphoenix.robots.phoenix;

import com.qualcomm.robotcore.hardware.HardwareMap;

import edu.ftcphoenix.fw.adapters.plants.Plants;
import edu.ftcphoenix.fw.drive.Drives;
import edu.ftcphoenix.fw.drive.MecanumDrivebase;
import edu.ftcphoenix.fw.stage.buffer.BufferStage;
import edu.ftcphoenix.fw.stage.setpoint.SetpointStage;
import edu.ftcphoenix.fw.util.Units;

/**
 * Robot container for the "Phoenix" robot.
 *
 * <p>This class is responsible for wiring FTC hardware into framework
 * subsystems:
 * <ul>
 *   <li>Mecanum drivebase</li>
 *   <li>Shooter (velocity-based)</li>
 *   <li>Buffer / feeder (timed pulses)</li>
 * </ul>
 *
 * <p>Typical usage from an OpMode:
 * <pre>{@code
 * public final class PhoenixTeleOp extends OpMode {
 *     private PhoenixRobot robot;
 *
 *     @Override public void init() {
 *         robot = new PhoenixRobot(hardwareMap);
 *         // set up inputs, bindings, StickDriveSource, etc.
 *     }
 *
 *     @Override public void loop() {
 *         double dtSec = ...; // from LoopClock
 *
 *         // Drive:
 *         DriveSignal cmd = sticks.get(clock).clamped();
 *         robot.drivebase.drive(cmd);
 *         robot.drivebase.update(clock);
 *
 *         // Stages:
 *         robot.shooter.update(dtSec);
 *         robot.buffer.update(dtSec);
 *     }
 * }
 * }</pre>
 *
 * <p>All hardware names and constants below are examples; tune as needed for
 * your robot.</p>
 */
public final class PhoenixRobot {

    // ---------------------------------------------------------------------
    // Hardware names (tune to match your configuration)
    // ---------------------------------------------------------------------

    public static final String HW_FRONT_LEFT = "fl";
    public static final String HW_FRONT_RIGHT = "fr";
    public static final String HW_BACK_LEFT = "bl";
    public static final String HW_BACK_RIGHT = "br";

    public static final String HW_SHOOTER = "shooter";
    public static final String HW_BUFFER = "feeder";

    // ---------------------------------------------------------------------
    // Shooter configuration (example values)
    // ---------------------------------------------------------------------

    /**
     * Encoder ticks per motor shaft revolution (including gear ratio).
     */
    public static final double SHOOTER_TICKS_PER_REV = 28.0;

    /**
     * Idle RPM (wheel speed) for shooter.
     */
    public static final double SHOOTER_IDLE_RPM = 1000.0;

    /**
     * Full SHOOT RPM for shooter.
     */
    public static final double SHOOTER_SHOOT_RPM = 3500.0;

    // ---------------------------------------------------------------------
    // Buffer pulse configuration (example values)
    // ---------------------------------------------------------------------

    /**
     * Forward pulse power for buffer (0..1).
     */
    public static final double BUFFER_POWER = 1.0;

    /**
     * Pulse duration (seconds) to feed one game piece.
     */
    public static final double BUFFER_SECONDS = 0.40;

    // ---------------------------------------------------------------------
    // Public goal enums
    // ---------------------------------------------------------------------

    /**
     * High-level shooter goals.
     *
     * <p>These are mapped to target velocities via {@link SetpointStage}.</p>
     */
    public enum ShooterGoal {
        STOP,
        IDLE,
        SHOOT
    }

    // ---------------------------------------------------------------------
    // Public subsystems
    // ---------------------------------------------------------------------

    /**
     * Drivetrain (mecanum).
     */
    public final MecanumDrivebase drivebase;

    /**
     * Shooter stage (velocity-based).
     */
    public final SetpointStage<ShooterGoal> shooter;

    /**
     * Buffer / feeder stage (timed pulses).
     */
    public final BufferStage buffer;

    // ---------------------------------------------------------------------
    // Construction
    // ---------------------------------------------------------------------

    /**
     * Wire all Phoenix subsystems from the FTC {@link HardwareMap}.
     *
     * @param hw hardware map provided by the OpMode
     */
    public PhoenixRobot(HardwareMap hw) {
        // ---- Drivebase: use Drives helper to hide FtcHardware from robot code.
        //
        // Adjust inversion as needed for your robot. Example here:
        // - Names:  fl, fr, bl, br
        // - Only front-right is inverted (common when wiring is asymmetric).
        drivebase = Drives.mecanum(hw)
                .frontLeft(HW_FRONT_LEFT)
                .frontRight(HW_FRONT_RIGHT)
                .backLeft(HW_BACK_LEFT)
                .backRight(HW_BACK_RIGHT)
                .invertFrontRight()   // tweak per your robot's motor orientations
                .build();

        // ---- Shooter: SetpointStage + Plants.velocity (rad/s)
        shooter = SetpointStage.enumBuilder(ShooterGoal.class)
                .name("Shooter")
                .plant(Plants.velocity(
                        hw,
                        HW_SHOOTER,
                        SHOOTER_TICKS_PER_REV,
                        false))
                .target(ShooterGoal.STOP, 0.0)
                .target(ShooterGoal.IDLE, Units.rpmToRadPerSec(SHOOTER_IDLE_RPM))
                .target(ShooterGoal.SHOOT, Units.rpmToRadPerSec(SHOOTER_SHOOT_RPM))
                .build();

        // ---- Buffer: BufferStage with timed pulses, gated on shooter readiness.
        buffer = BufferStage.builder()
                .name("Buffer")
                .egress(
                        BufferStage.TransferSpecs
                                .powerFor(hw, HW_BUFFER, false, BUFFER_POWER, BUFFER_SECONDS)
                                .downstreamReady(shooter::atSetpoint)
                )
                .build();
    }
}

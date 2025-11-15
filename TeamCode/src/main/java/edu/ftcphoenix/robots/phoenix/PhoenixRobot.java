package edu.ftcphoenix.robots.phoenix;

import com.qualcomm.robotcore.hardware.HardwareMap;

import org.firstinspires.ftc.robotcore.external.Telemetry;

import java.util.ArrayList;
import java.util.List;

import edu.ftcphoenix.fw.input.DriverKit;
import edu.ftcphoenix.fw.robot.Subsystem;
import edu.ftcphoenix.fw.util.LoopClock;
import edu.ftcphoenix.robots.phoenix.subsystem.DriveSubsystem;
import edu.ftcphoenix.robots.phoenix.subsystem.ShooterSubsystem;
import edu.ftcphoenix.robots.phoenix.subsystem.VisionSubsystem;

/**
 * PhoenixRobot represents your whole robot for this season.
 *
 * <h2>Role</h2>
 * <p>This class owns all of the robot's subsystems and provides a simple
 * lifecycle that TeleOp and Autonomous OpModes can call into.
 *
 * <p>Typical pattern:
 * <ul>
 *   <li>TeleOp shell (extends PhoenixTeleOpBase) calls:
 *     <ul>
 *       <li>{@link #onTeleopInit()} once at start.</li>
 *       <li>{@link #onTeleopLoop(LoopClock)} every loop.</li>
 *       <li>{@link #onStop()} when stopping.</li>
 *     </ul>
 *   </li>
 *   <li>Auto shell (extends PhoenixAutoBase) similarly calls
 *       {@link #onAutoInit()}, {@link #onAutoLoop(LoopClock)}, and {@link #onStop()}.</li>
 * </ul>
 *
 * <p>All timing is derived from {@link LoopClock}, so subsystems never need
 * to call {@code nanoTime()} directly.
 */
public final class PhoenixRobot {

    private final DriverKit driverKit;
    private final Telemetry telemetry;

    // Subsystems owned by this robot.
    private final DriveSubsystem drive;
    private final VisionSubsystem vision;
    private final ShooterSubsystem shooter;

    // Registry to forward lifecycle calls uniformly.
    private final List<Subsystem> subsystems = new ArrayList<>();

    /**
     * Construct the PhoenixRobot and all of its subsystems.
     *
     * @param hw        FTC hardware map
     * @param driverKit driver input helpers created by the base OpMode
     * @param telemetry telemetry from the base OpMode
     */
    public PhoenixRobot(HardwareMap hw,
                        DriverKit driverKit,
                        Telemetry telemetry) {
        this.driverKit = driverKit;
        this.telemetry = telemetry;

        // Construct subsystems.
        this.vision = new VisionSubsystem(hw, telemetry);
        this.drive = new DriveSubsystem(hw, driverKit, vision);
        this.shooter = new ShooterSubsystem(hw, driverKit, telemetry);

        // Register them in the order we want to update them.
        subsystems.add(drive);
        subsystems.add(shooter);
        subsystems.add(vision);
    }

    // ------------------------------------------------------------------------
    // TeleOp lifecycle
    // ------------------------------------------------------------------------

    /**
     * Called once when TeleOp starts.
     *
     * <p>Forwards to {@link Subsystem#onTeleopInit()} on each subsystem.
     */
    public void onTeleopInit() {
        for (Subsystem s : subsystems) {
            s.onTeleopInit();
        }
    }

    /**
     * Called every loop during TeleOp.
     *
     * <p>Forwards {@link LoopClock} to each subsystem's
     * {@link Subsystem#onTeleopLoop(LoopClock)} and then updates telemetry.
     *
     * @param clock loop clock for the current TeleOp iteration
     */
    public void onTeleopLoop(LoopClock clock) {
        for (Subsystem s : subsystems) {
            s.onTeleopLoop(clock);
        }
        telemetry.update();
    }

    // ------------------------------------------------------------------------
    // Autonomous lifecycle
    // ------------------------------------------------------------------------

    /**
     * Called once when Autonomous starts.
     *
     * <p>Forwards to {@link Subsystem#onAutoInit()} on each subsystem.
     */
    public void onAutoInit() {
        for (Subsystem s : subsystems) {
            s.onAutoInit();
        }
    }

    /**
     * Called every loop during Autonomous.
     *
     * <p>Forwards {@link LoopClock} to each subsystem's
     * {@link Subsystem#onAutoLoop(LoopClock)} and then updates telemetry.
     *
     * @param clock loop clock for the current Autonomous iteration
     */
    public void onAutoLoop(LoopClock clock) {
        for (Subsystem s : subsystems) {
            s.onAutoLoop(clock);
        }
        telemetry.update();
    }

    // ------------------------------------------------------------------------
    // Common shutdown
    // ------------------------------------------------------------------------

    /**
     * Called when TeleOp or Auto is stopping.
     *
     * <p>Forwards to {@link Subsystem#onStop()} on each subsystem.
     */
    public void onStop() {
        for (Subsystem s : subsystems) {
            s.onStop();
        }
    }

    // ------------------------------------------------------------------------
    // Optional accessors (for tasks or higher-level logic)
    // ------------------------------------------------------------------------

    public DriveSubsystem drive() {
        return drive;
    }

    public VisionSubsystem vision() {
        return vision;
    }

    public ShooterSubsystem shooter() {
        return shooter;
    }
}

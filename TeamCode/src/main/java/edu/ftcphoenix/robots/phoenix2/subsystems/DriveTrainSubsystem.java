package edu.ftcphoenix.robots.phoenix2.subsystems;

import com.qualcomm.hardware.rev.RevHubOrientationOnRobot;
import com.qualcomm.robotcore.hardware.HardwareMap;

import org.firstinspires.ftc.robotcore.external.Telemetry;

import edu.ftcphoenix.fw2.core.FrameClock;
import edu.ftcphoenix.fw2.drive.DriveSignal;
import edu.ftcphoenix.fw2.drive.DriveSource;
import edu.ftcphoenix.fw2.drive.hw.DriveIO;
import edu.ftcphoenix.fw2.drive.hw.rev.MecanumDrive;
import edu.ftcphoenix.fw2.drive.hw.rev.MecanumIO;
import edu.ftcphoenix.fw2.subsystems.Subsystem;
import edu.ftcphoenix.robots.phoenix2.Constants;

/**
 * DriveTrainSubsystem — robot-level subsystem that owns drivetrain IO and driving loop.
 *
 * <p>Responsibilities:</p>
 * <ul>
 *   <li>Create and configure {@link MecanumIO} (motors, optional IMU/voltage) via its Builder.</li>
 *   <li>Hold a {@link MecanumDrive} facade that converts {@link DriveSignal} to wheel powers.</li>
 *   <li>Consume a {@link DriveSource} set by higher-level code (e.g., DriveGraph).</li>
 *   <li>Push commands to hardware once per frame from {@link #update(FrameClock)}.</li>
 * </ul>
 *
 * <p>Non-responsibilities:</p>
 * <ul>
 *   <li>Input shaping / field-centric / assist mixing (keep those in DriveGraph + AxisChains).</li>
 *   <li>OpMode wiring — just call {@link #setSource(DriveSource)} once during initTeleOp().</li>
 * </ul>
 *
 * <p>Usage:</p>
 * <pre>{@code
 * // In Robot.initRobot():
 * driveTrainSubsystem = new DriveTrainSubsystem(hw, telemetry);
 * registerSubsystems(driveTrainSubsystem);
 *
 * // In TeleOpController.buildDriveGraph():
 * graph = DriveGraph.build(driver, opt); // or with assists
 * robot.driveTrainSubsystem.setSource(graph.source);
 * }</pre>
 */
public final class DriveTrainSubsystem implements Subsystem {

    private final Telemetry telemetry;
    private final MecanumIO io;
    private final MecanumDrive drive;

    /**
     * The upstream producer of DriveSignal (e.g., DriveGraph). May be null until set.
     */
    private volatile DriveSource source;

    public DriveTrainSubsystem(HardwareMap hw, Telemetry telemetry) {
        this.telemetry = telemetry;

        // Build IO from your robot constants. Adjust names/orientations as needed.
        this.io = new MecanumIO.Builder(hw)
                .motors(
                        Constants.MOTOR_NAME_FRONT_LEFT,
                        Constants.MOTOR_NAME_FRONT_RIGHT,
                        Constants.MOTOR_NAME_BACK_LEFT,
                        Constants.MOTOR_NAME_BACK_RIGHT)
                // Choose directions to make +axial = forward for your wiring
                .motorDirections(
                        com.qualcomm.robotcore.hardware.DcMotorSimple.Direction.FORWARD,
                        com.qualcomm.robotcore.hardware.DcMotorSimple.Direction.FORWARD,
                        com.qualcomm.robotcore.hardware.DcMotorSimple.Direction.REVERSE,
                        com.qualcomm.robotcore.hardware.DcMotorSimple.Direction.FORWARD)
                // Optional IMU; harmless to omit if not used
                .imu(Constants.IMU_NAME, new RevHubOrientationOnRobot(
                        RevHubOrientationOnRobot.LogoFacingDirection.UP,
                        RevHubOrientationOnRobot.UsbFacingDirection.BACKWARD))
                // Optional voltage sensor; safe to omit
                .voltageSensor("Control Hub")
                .build();

        // Tiny facade that converts DriveSignal -> wheel powers and writes to IO
        this.drive = new MecanumDrive(io);
    }

    // ---------------- Subsystem lifecycle ----------------

    @Override
    public void onEnable() {
        // No-op. If you want a startup animation or a zero-power safety, put it here.
    }

    @Override
    public void update(FrameClock clock) {
        // If no source is set yet, hold still defensively
        DriveSource s = this.source;
        if (s == null) {
            drive.stop();
            return;
        }
        // Pull one DriveSignal per frame and push it to hardware
        DriveSignal cmd = s.get(clock);
        drive.drive(cmd);
    }

    @Override
    public void onDisable() {
        // Coasts to stop; nothing persistent to tear down
        drive.stop();
    }

    @Override
    public void stop() {
        // Hard stop for shutdown
        drive.stop();
    }

    // ---------------- Public API ----------------

    /**
     * Install the upstream {@link DriveSource} (e.g., DriveGraph.build(...).source).
     * Safe to call from initTeleOp() after building your graph.
     */
    public void setSource(DriveSource src) {
        this.source = src;
    }

    /**
     * Optional: override the current source with a fixed command (e.g., for tests).
     */
    public void driveFixed(DriveSignal s) {
        // Bypass the source for this frame
        drive.drive(s);
    }

    /**
     * Add drivetrain telemetry.
     *
     * @param t       Telemetry instance
     * @param label   base label (e.g., "drive")
     * @param verbose set true for IMU/voltage extras if available
     */
    public void addTelemetry(Telemetry t, String label, boolean verbose) {
        double[] w = io.getLastWheelPowers(); // FL, FR, BL, BR (may be zeros before first update)
        t.addData(label + "/wheels", "FL=%.2f FR=%.2f BL=%.2f BR=%.2f", w[0], w[1], w[2], w[3]);

        if (verbose) {
            if (io.hasImu()) {
                t.addData(label + "/heading(rad)", "%.3f", io.getHeadingRad());
            }
            if (io.hasVoltage()) {
                t.addData(label + "/voltage(V)", "%.2f", io.getVoltage());
            }
            double amps = io.getCurrentAmps();
            if (!Double.isNaN(amps)) {
                t.addData(label + "/current(A)", "%.1f", amps);
            }
        }
    }

    /**
     * Expose raw IO if you need low-level queries (e.g., heading) in other parts of your code.
     * Prefer {@link #addTelemetry} for routine status.
     */
    public DriveIO io() {
        return io;
    }
}

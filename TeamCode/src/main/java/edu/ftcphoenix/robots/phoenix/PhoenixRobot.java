package edu.ftcphoenix.robots.phoenix;

import com.qualcomm.robotcore.hardware.Gamepad;
import com.qualcomm.robotcore.hardware.HardwareMap;

import org.firstinspires.ftc.robotcore.external.Telemetry;

import edu.ftcphoenix.fw.adapters.ftc.FtcTelemetryDebugSink;
import edu.ftcphoenix.fw.debug.DebugSink;
import edu.ftcphoenix.fw.drive.DriveSignal;
import edu.ftcphoenix.fw.drive.DriveSource;
import edu.ftcphoenix.fw.drive.Drives;
import edu.ftcphoenix.fw.drive.MecanumDrivebase;
import edu.ftcphoenix.fw.drive.source.GamepadDriveSource;
import edu.ftcphoenix.fw.input.Gamepads;
import edu.ftcphoenix.fw.input.binding.Bindings;
import edu.ftcphoenix.fw.task.TaskRunner;
import edu.ftcphoenix.fw.util.LoopClock;

/**
 * Central robot class for Phoenix-based robots.
 *
 * <p>Beginners should mostly edit <b>this file</b>. TeleOp and Auto OpModes are
 * kept very thin and simply delegate into the methods here.</p>
 *
 * <h2>Responsibilities</h2>
 * <ul>
 *   <li>Wire all hardware once (drive, intake, transfer, shooter, pusher, vision).</li>
 *   <li>Define gamepad mappings in one place via {@link Bindings}.</li>
 *   <li>Own shared logic: auto-aim, shooter velocity, macros, autos.</li>
 *   <li>Expose simple entry points for TeleOp and Auto.</li>
 * </ul>
 */
public final class PhoenixRobot {
    private final LoopClock clock = new LoopClock();
    private final HardwareMap hardwareMap;
    private final Telemetry telemetry;
    private final Gamepads gamepads;
    private final Bindings bindings = new Bindings();
    private final TaskRunner taskRunnerTeleOp = new TaskRunner();
    private final DebugSink dbg;
    private Shooter shooter;
    private MecanumDrivebase drivebase;
    private DriveSource stickDrive;

    public PhoenixRobot(HardwareMap hardwareMap, Telemetry telemetry, Gamepad gamepad1, Gamepad gamepad2) {
        this.hardwareMap = hardwareMap;
        this.gamepads = Gamepads.create(gamepad1, gamepad2);
        this.telemetry = telemetry;
        this.dbg = new FtcTelemetryDebugSink(telemetry);
    }

    public void initAny() {
    }

    public void initTeleOp() {

        // --- Create mechanisms ---
        drivebase = Drives.mecanum(hardwareMap,
                RobotConfig.DriveTrain.invertMotorFrontLeft,
                RobotConfig.DriveTrain.invertMotorFrontRight,
                RobotConfig.DriveTrain.invertMotorBackLeft,
                RobotConfig.DriveTrain.invertMotorBackRight,
                null);
        shooter = new Shooter(hardwareMap, telemetry, gamepads);

        // --- Use the standard TeleOp stick mapping for mecanum.
        stickDrive = GamepadDriveSource.teleOpMecanumStandard(gamepads);

        telemetry.addLine("FW Example 01: Mecanum Basic");
        telemetry.addLine("Left stick: drive, Right stick: turn, RB: slow mode");
        telemetry.update();

        // Create bindings
        createBindings();
    }

    private void createBindings() {
        bindings.onPress(gamepads.p2().y(),
                () -> taskRunnerTeleOp.enqueue(shooter.setPusherFront()));

        bindings.onPress(gamepads.p2().a(),
                () -> taskRunnerTeleOp.enqueue(shooter.setPusherBack()));

        bindings.whileHeld(gamepads.p2().b(),
                () -> taskRunnerTeleOp.enqueue(shooter.startTransfer(Shooter.TransferDirection.FORWARD)),
                () -> taskRunnerTeleOp.enqueue(shooter.stopTransfer()));

        bindings.whileHeld(gamepads.p2().x(),
                () -> taskRunnerTeleOp.enqueue(shooter.startTransfer(Shooter.TransferDirection.BACKWARD)),
                () -> taskRunnerTeleOp.enqueue(shooter.stopTransfer()));


        bindings.toggle(gamepads.p2().rightBumper(),
                (isOn) -> {
            if(isOn) {
                taskRunnerTeleOp.enqueue(shooter.startShooter());
            }

            else {
                taskRunnerTeleOp.enqueue(shooter.stopShooter());
            }
                });

        bindings.onPress(gamepads.p2().dpadUp(),
                () -> taskRunnerTeleOp.enqueue(shooter.increaseVelocity()));

        bindings.onPress(gamepads.p2().dpadDown(),
                () -> taskRunnerTeleOp.enqueue(shooter.decreaseVelocity()));
    }

    public void startAny(double runtime) {
        // Initialize loop timing.
        clock.reset(runtime);
    }

    public void startTeleOp() {
    }

    public void updateAny(double runtime) {
        // --- 1) Clock ---
        clock.update(runtime);
    }

    public void updateTeleOp() {
        // --- 2) Inputs + bindings ---
        gamepads.update(clock.dtSec());
        bindings.update(clock.dtSec());

        // --- 3) TeleOp Macros ---
        taskRunnerTeleOp.update(clock);

        // When no macro is active, hold a safe default state.
        if (!taskRunnerTeleOp.hasActiveTask()) {
        }

        // --- 4) Drive: TagAim-wrapped drive source (LB may override omega) ---
        DriveSignal cmd = stickDrive.get(clock);
        drivebase.drive(cmd);
        drivebase.update(clock);

        // --- 4) Other mechanisms ---


        // --- 5) Telemetry / debug ---
        telemetry.addData("shooter velocity", shooter.getVelocity());
        telemetry.update();
    }

    public void stopAny() {
        drivebase.stop();
    }

    public void stopTeleOp() {
    }
}

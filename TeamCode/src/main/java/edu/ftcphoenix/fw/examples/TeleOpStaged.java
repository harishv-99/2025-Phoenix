package edu.ftcphoenix.fw.examples;

import com.qualcomm.robotcore.eventloop.opmode.Disabled;
import com.qualcomm.robotcore.eventloop.opmode.OpMode;
import com.qualcomm.robotcore.eventloop.opmode.TeleOp;

import edu.ftcphoenix.fw.adapters.ftc.FtcHardware;
import edu.ftcphoenix.fw.adapters.plants.Plants;
import edu.ftcphoenix.fw.drive.DriveSignal;
import edu.ftcphoenix.fw.drive.MecanumConfig;
import edu.ftcphoenix.fw.drive.MecanumDrivebase;
import edu.ftcphoenix.fw.drive.source.StickDriveSource;
import edu.ftcphoenix.fw.hal.MotorOutput;
import edu.ftcphoenix.fw.input.DriverKit;
import edu.ftcphoenix.fw.input.Gamepads;
import edu.ftcphoenix.fw.input.binding.Bindings;
import edu.ftcphoenix.fw.stage.buffer.BufferStage;
import edu.ftcphoenix.fw.stage.setpoint.SetpointStage;
import edu.ftcphoenix.fw.util.LoopClock;
import edu.ftcphoenix.fw.util.Units;

/**
 * Staged TeleOp example showing:
 * <ul>
 *   <li>Mecanum drive via {@link StickDriveSource}.</li>
 *   <li>Shooter modeled as a {@link SetpointStage} with velocity plant.</li>
 *   <li>Buffer/indexer modeled as a {@link BufferStage} with timed pulses.</li>
 *   <li>Gamepad bindings via {@link DriverKit} and {@link Bindings}.</li>
 * </ul>
 *
 * <p>This is intended as a "happy path" example for the framework staging
 * concepts. Hardware names and RPMs are examples; tune for your robot.</p>
 */
@Disabled
@TeleOp(name = "FW Example: Staged TeleOp")
public final class TeleOpStaged extends OpMode {

    // ---------------------------------------------------------------------
    // Hardware names (tune to your configuration)
    // ---------------------------------------------------------------------

    private static final String HW_FL = "fl";
    private static final String HW_FR = "fr";
    private static final String HW_BL = "bl";
    private static final String HW_BR = "br";

    private static final String HW_SHOOTER = "shooter";
    private static final String HW_BUFFER = "feeder";

    // Shooter configuration (example values)
    private static final double SHOOTER_TICKS_PER_REV = 28.0;   // tune to your motor+gearbox
    private static final double SHOOTER_IDLE_RPM = 1000.0;
    private static final double SHOOTER_SHOOT_RPM = 3500.0;

    // Buffer pulse configuration (example values)
    private static final double BUFFER_POWER = 1.0;   // forward pulse power
    private static final double BUFFER_SECONDS = 0.40;  // pulse duration

    // ---------------------------------------------------------------------
    // Shooter goals
    // ---------------------------------------------------------------------

    private enum ShooterGoal {
        STOP,
        IDLE,
        SHOOT
    }

    // ---------------------------------------------------------------------
    // Framework plumbing
    // ---------------------------------------------------------------------

    private final LoopClock clock = new LoopClock();

    private Gamepads gamepads;
    private DriverKit driverKit;
    private Bindings bindings;

    private MecanumDrivebase drivebase;
    private StickDriveSource stickDrive;

    private SetpointStage<ShooterGoal> shooterStage;
    private BufferStage bufferStage;

    @Override
    public void init() {
        // 1) Inputs
        gamepads = Gamepads.create(gamepad1, gamepad2);
        driverKit = DriverKit.of(gamepads);
        bindings = new Bindings();

        // 2) Drivebase: use HAL adapters for drive motors
        MotorOutput fl = FtcHardware.motor(hardwareMap, HW_FL, false);
        MotorOutput fr = FtcHardware.motor(hardwareMap, HW_FR, false);
        MotorOutput bl = FtcHardware.motor(hardwareMap, HW_BL, false);
        MotorOutput br = FtcHardware.motor(hardwareMap, HW_BR, false);

        drivebase = new MecanumDrivebase(fl, fr, bl, br, MecanumConfig.defaults());
        stickDrive = StickDriveSource.defaultMecanum(driverKit);

        // 3) Shooter: SetpointStage + velocity Plant
        shooterStage = SetpointStage.enumBuilder(ShooterGoal.class)
                .name("Shooter")
                .plant(Plants.velocity(
                        hardwareMap,
                        HW_SHOOTER,
                        SHOOTER_TICKS_PER_REV,
                        false))
                .target(ShooterGoal.STOP, 0.0)
                .target(ShooterGoal.IDLE, Units.rpmToRadPerSec(SHOOTER_IDLE_RPM))
                .target(ShooterGoal.SHOOT, Units.rpmToRadPerSec(SHOOTER_SHOOT_RPM))
                .build();

        // 4) Buffer: BufferStage with timed pulses, gated on shooter ready
        bufferStage = BufferStage.builder()
                .name("Buffer")
                .egress(
                        BufferStage.TransferSpecs
                                .powerFor(hardwareMap, HW_BUFFER, false, BUFFER_POWER, BUFFER_SECONDS)
                                .downstreamReady(() -> shooterStage.atSetpoint())
                )
                .build();

        // 5) Bindings: map gamepad to goals/commands
        wireBindings();

        telemetry.addLine("Staged TeleOp: init complete");
        telemetry.update();
    }

    private void wireBindings() {
        // Shooter goals:
        // A → SHOOT, B → STOP, X → IDLE (coast/hold speed)
        bindings.onPress(
                driverKit.p1().buttonA(),
                () -> shooterStage.setGoal(ShooterGoal.SHOOT)
        );
        bindings.onPress(
                driverKit.p1().buttonB(),
                () -> shooterStage.setGoal(ShooterGoal.STOP)
        );
        bindings.onPress(
                driverKit.p1().buttonX(),
                () -> shooterStage.setGoal(ShooterGoal.IDLE)
        );

        // Buffer:
        // Right bumper → queue SEND (fire toward shooter)
        // Left bumper → queue EJECT (reverse)
        bindings.onPress(
                driverKit.p1().rightBumper(),
                () -> bufferStage.handle(BufferStage.BufferCmd.SEND)
        );
        bindings.onPress(
                driverKit.p1().leftBumper(),
                () -> bufferStage.handle(BufferStage.BufferCmd.EJECT)
        );
    }

    @Override
    public void start() {
        // Initialize the loop clock with current runtime
        clock.reset(getRuntime());
    }

    @Override
    public void loop() {
        // 1) Timing
        clock.update(getRuntime());
        double dtSec = clock.dtSec();

        // 2) Inputs + bindings
        gamepads.update(dtSec);   // currently a no-op; kept for future filters
        bindings.update(dtSec);   // fire press/release/held/toggle callbacks

        // 3) Drive
        DriveSignal driveCmd = stickDrive.get(clock).clamped();
        drivebase.drive(driveCmd);
        drivebase.update(clock);

        // 4) Mechanisms (stages)
        shooterStage.update(dtSec);
        bufferStage.update(dtSec);

        // 5) Telemetry
        telemetry.addLine("Drive")
                .addData("axial", driveCmd.axial)
                .addData("lateral", driveCmd.lateral)
                .addData("omega", driveCmd.omega);

        telemetry.addLine("Shooter")
                .addData("goal", shooterStage.getGoal())
                .addData("ready", shooterStage.atSetpoint());

        telemetry.addLine("Buffer")
                .addData("active", bufferStage.isActive())
                .addData("queuedSend", bufferStage.getQueuedSendCount())
                .addData("queuedEject", bufferStage.getQueuedEjectCount());

        telemetry.update();
    }

    @Override
    public void stop() {
        // Ensure everything is safe on stop.
        drivebase.stop();
        // BufferStage will stop its motor on next update; force it now:
        bufferStage.handle(BufferStage.BufferCmd.CANCEL);
        bufferStage.update(0.0);
    }
}

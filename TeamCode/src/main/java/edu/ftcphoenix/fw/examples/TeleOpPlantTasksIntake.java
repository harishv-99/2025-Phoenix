package edu.ftcphoenix.fw.examples;

import com.qualcomm.robotcore.eventloop.opmode.Disabled;
import com.qualcomm.robotcore.eventloop.opmode.OpMode;
import com.qualcomm.robotcore.eventloop.opmode.TeleOp;

import edu.ftcphoenix.fw.adapters.ftc.FtcHardware;
import edu.ftcphoenix.fw.adapters.ftc.FtcPlants;
import edu.ftcphoenix.fw.actuation.Plant;
import edu.ftcphoenix.fw.actuation.PlantTasks;
import edu.ftcphoenix.fw.drive.DriveSignal;
import edu.ftcphoenix.fw.drive.MecanumConfig;
import edu.ftcphoenix.fw.drive.MecanumDrivebase;
import edu.ftcphoenix.fw.drive.source.StickDriveSource;
import edu.ftcphoenix.fw.hal.MotorOutput;
import edu.ftcphoenix.fw.input.DriverKit;
import edu.ftcphoenix.fw.input.Gamepads;
import edu.ftcphoenix.fw.input.binding.Bindings;
import edu.ftcphoenix.fw.task.SequenceTask;
import edu.ftcphoenix.fw.task.Task;
import edu.ftcphoenix.fw.task.TaskRunner;
import edu.ftcphoenix.fw.util.LoopClock;

/**
 * TeleOp example showing how to use {@link PlantTasks} with {@link TaskRunner}
 * to build simple intake macros.
 *
 * <ul>
 *   <li>Mecanum drive via {@link StickDriveSource}.</li>
 *   <li>Intake modeled as a {@link Plant} from {@link FtcPlants#power}.</li>
 *   <li>One-shot intake macros (forward / forward+reverse) using {@link PlantTasks}.</li>
 *   <li>Manual intake control via trigger when no macro is active.</li>
 * </ul>
 *
 * <p>This is intended as a beginner-friendly "happy path" for {@code Plant} +
 * tasks. Hardware names and durations are examples; tune for your robot.</p>
 *
 * <h2>Input mapping (Driver 1)</h2>
 * <ul>
 *   <li>Left stick X: strafe left/right.</li>
 *   <li>Left stick Y: forward/back (up is +).</li>
 *   <li>Right stick X: rotate.</li>
 *   <li>Right trigger: manual intake (0..1 power) when no macro is running.</li>
 *   <li>A: intake forward pulse.</li>
 *   <li>Y: intake forward-then-reverse macro.</li>
 *   <li>B: cancel intake macros and stop intake.</li>
 * </ul>
 */
@Disabled
@TeleOp(name = "FW Example: PlantTasks Intake Macro", group = "Phoenix")
public final class TeleOpPlantTasksIntake extends OpMode {

    // --- Hardware names (match to your robot configuration) ---
    private static final String HW_FL = "fl";
    private static final String HW_FR = "fr";
    private static final String HW_BL = "bl";
    private static final String HW_BR = "br";

    private static final String HW_INTAKE = "intake";

    // --- Intake macro tuning (seconds and power) ---
    private static final double INTAKE_FWD_POWER = +1.0;
    private static final double INTAKE_REV_POWER = -1.0;

    private static final double INTAKE_PULSE_FWD_SEC = 0.7;
    private static final double INTAKE_PULSE_REV_SEC = 0.4;

    // Input & drive plumbing
    private Gamepads gamepads;
    private DriverKit driverKit;
    private Bindings bindings;

    private StickDriveSource stickDrive;
    private MecanumDrivebase drivebase;

    // Intake modeled as a Plant (open-loop power)
    private Plant intake;

    // Task runner for intake macros
    private final TaskRunner macroRunner = new TaskRunner();

    // Loop timing
    private final LoopClock clock = new LoopClock();

    @Override
    public void init() {
        // 1) Inputs
        gamepads = Gamepads.create(gamepad1, gamepad2);
        driverKit = DriverKit.of(gamepads);
        bindings = new Bindings();

        // 2) Drive hardware via HAL adapters
        MotorOutput fl = FtcHardware.motor(hardwareMap, HW_FL, false);
        MotorOutput fr = FtcHardware.motor(hardwareMap, HW_FR, true);   // typical inversion
        MotorOutput bl = FtcHardware.motor(hardwareMap, HW_BL, false);
        MotorOutput br = FtcHardware.motor(hardwareMap, HW_BR, true);

        drivebase = new MecanumDrivebase(fl, fr, bl, br, MecanumConfig.defaults());

        // 3) Default mecanum mapping from DriverKit sticks (with slow mode)
        stickDrive = StickDriveSource.teleOpMecanumWithSlowMode(
                driverKit,
                driverKit.p1().rightBumper(), // hold for slow mode
                0.30
        );

        // 4) Intake plant: simple power plant from hardware map
        intake = FtcPlants.power(hardwareMap, HW_INTAKE, false);

        // 5) Wire button bindings for intake macros
        wireBindings();

        telemetry.addLine("FW PlantTasks Intake Macro: init complete");
        telemetry.update();
    }

    /**
     * Bind gamepad buttons to intake macro actions.
     */
    private void wireBindings() {
        // A → simple forward intake pulse
        bindings.onPress(
                driverKit.p1().buttonA(),
                () -> startIntakePulseForward()
        );

        // Y → forward then reverse macro (e.g., collect then bump/un-jam)
        bindings.onPress(
                driverKit.p1().buttonY(),
                () -> startIntakeForwardThenReverse()
        );

        // B → cancel macros and stop intake
        bindings.onPress(
                driverKit.p1().buttonB(),
                () -> cancelIntakeMacros()
        );
    }

    @Override
    public void start() {
        clock.reset(getRuntime());
    }

    @Override
    public void loop() {
        // 1) Loop timing
        clock.update(getRuntime());
        double dtSec = clock.dtSec();

        // 2) Inputs + bindings
        gamepads.update(dtSec);   // currently a no-op; kept for future filters
        bindings.update(dtSec);   // fire press/held/toggle callbacks

        // 3) Let intake macros run first
        macroRunner.update(clock);

        // 4) Drive is always under manual control
        DriveSignal driveCmd = stickDrive.get(clock).clamped();
        drivebase.drive(driveCmd);
        drivebase.update(clock);

        // 5) Manual intake when no macro is active
        if (!macroRunner.hasActiveTask()) {
            double trigger = driverKit.p1().rightTrigger().get();  // 0..1
            intake.setTarget(trigger);
            intake.update(dtSec);
        }

        // 6) Telemetry for debugging
        telemetry.addLine("Drive")
                .addData("axial", driveCmd.axial)
                .addData("lateral", driveCmd.lateral)
                .addData("omega", driveCmd.omega);
        telemetry.addLine("Intake")
                .addData("macroActive", macroRunner.hasActiveTask())
                .addData("queued", macroRunner.queuedCount());
        telemetry.update();
    }

    @Override
    public void stop() {
        // Stop drive and intake safely
        drivebase.stop();
        cancelIntakeMacros();
        intake.setTarget(0.0);
    }

    // ---------------------------------------------------------------------
    // Intake macro helpers (built using PlantTasks)
    // ---------------------------------------------------------------------

    /**
     * One-shot forward intake pulse.
     *
     * <p>Semantics:
     * <ul>
     *   <li>On start: set intake target to {@link #INTAKE_FWD_POWER}.</li>
     *   <li>For {@link #INTAKE_PULSE_FWD_SEC} seconds: keep updating the plant.</li>
     *   <li>On finish: set intake target back to 0.</li>
     * </ul>
     */
    private void startIntakePulseForward() {
        Task pulse = PlantTasks.holdForSeconds(
                intake,
                INTAKE_FWD_POWER,
                INTAKE_PULSE_FWD_SEC
        );
        macroRunner.clear();
        macroRunner.enqueue(pulse);
    }

    /**
     * Two-stage macro: intake forward, then brief reverse.
     *
     * <p>This pattern is useful for things like "collect then bump" or
     * un-jamming mechanisms.</p>
     */
    private void startIntakeForwardThenReverse() {
        Task macro = SequenceTask.of(
                // Forward pulse
                PlantTasks.holdForSeconds(
                        intake,
                        INTAKE_FWD_POWER,
                        INTAKE_PULSE_FWD_SEC
                ),
                // Short reverse pulse
                PlantTasks.holdForSeconds(
                        intake,
                        INTAKE_REV_POWER,
                        INTAKE_PULSE_REV_SEC
                )
        );
        macroRunner.clear();
        macroRunner.enqueue(macro);
    }

    /**
     * Cancel any running/queued intake macros and stop the intake.
     */
    private void cancelIntakeMacros() {
        macroRunner.clear();
        intake.setTarget(0.0);
    }
}

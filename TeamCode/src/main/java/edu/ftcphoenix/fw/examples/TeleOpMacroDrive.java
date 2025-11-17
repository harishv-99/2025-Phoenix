package edu.ftcphoenix.fw.examples;

import com.qualcomm.robotcore.eventloop.opmode.Disabled;
import com.qualcomm.robotcore.eventloop.opmode.OpMode;
import com.qualcomm.robotcore.eventloop.opmode.TeleOp;

import edu.ftcphoenix.fw.adapters.ftc.FtcHardware;
import edu.ftcphoenix.fw.drive.DriveSignal;
import edu.ftcphoenix.fw.drive.DriveTasks;
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
 * Example TeleOp showing how to trigger a simple drive macro using Tasks.
 *
 * <p>Normal behavior:</p>
 * <ul>
 *   <li>Player 1 uses sticks to drive a mecanum robot.</li>
 *   <li>Right bumper enables "slow mode" for precise movement.</li>
 * </ul>
 *
 * <p>Macro behavior (Player 1):</p>
 * <ul>
 *   <li>Button Y: run an "L-shaped" macro:
 *       <ol>
 *         <li>Drive forward for a short time.</li>
 *         <li>Strafe right for a short time.</li>
 *         <li>Rotate in place for a short time.</li>
 *       </ol>
 *   </li>
 *   <li>Button B: cancel the macro early and return to manual control.</li>
 * </ul>
 *
 * <p>The macro itself is implemented using:</p>
 * <ul>
 *   <li>{@link Task} + {@link TaskRunner} from {@code fw.task} for sequencing.</li>
 *   <li>{@link DriveTasks#driveForSeconds(MecanumDrivebase, DriveSignal, double)}
 *       for timed drive segments.</li>
 *   <li>{@link SequenceTask} to chain multiple segments into one macro task.</li>
 * </ul>
 *
 * <h2>Why this example exists</h2>
 * <ul>
 *   <li>Shows how to keep TeleOp loop non-blocking (no {@code while} loops).</li>
 *   <li>Demonstrates the recommended task pattern:
 *       "build Tasks → enqueue on {@link TaskRunner} → call {@link TaskRunner#update(LoopClock)} each loop."</li>
 *   <li>Uses the same input/drive wiring as {@link TeleOpMecanumBasic}:
 *       {@link Gamepads} → {@link DriverKit} → {@link StickDriveSource} → {@link MecanumDrivebase}.</li>
 * </ul>
 *
 * <p>This OpMode is marked {@link Disabled} so it does not appear by default
 * in the driver station menu. To use it on your robot, remove the
 * {@link Disabled} annotation and adjust hardware names as needed.</p>
 */
@Disabled
@TeleOp(name = "FW Example: Macro Drive", group = "Phoenix")
public final class TeleOpMacroDrive extends OpMode {

    // ----------------------------------------------------------------------
    // Hardware names (match these to your FTC Robot Configuration)
    // ----------------------------------------------------------------------

    private static final String HW_FL = "fl";
    private static final String HW_FR = "fr";
    private static final String HW_BL = "bl";
    private static final String HW_BR = "br";

    // ----------------------------------------------------------------------
    // Framework plumbing: inputs, drive, tasks
    // ----------------------------------------------------------------------

    private final LoopClock clock = new LoopClock();

    private Gamepads gamepads;
    private DriverKit driverKit;
    private Bindings bindings;

    private MecanumDrivebase drivebase;
    private StickDriveSource stickDrive;

    /**
     * Runner that executes our macro tasks (one at a time, in order).
     */
    private final TaskRunner macroRunner = new TaskRunner();

    // Last manual drive command, for telemetry only.
    private DriveSignal lastManualCmd = new DriveSignal(0.0, 0.0, 0.0);

    @Override
    public void init() {
        // --- Inputs ---
        gamepads = Gamepads.create(gamepad1, gamepad2);
        driverKit = DriverKit.of(gamepads);
        bindings = new Bindings();

        // --- Drive hardware via HAL adapters ---
        MotorOutput fl = FtcHardware.motor(hardwareMap, HW_FL, false);
        MotorOutput fr = FtcHardware.motor(hardwareMap, HW_FR, true);   // typical inversion
        MotorOutput bl = FtcHardware.motor(hardwareMap, HW_BL, false);
        MotorOutput br = FtcHardware.motor(hardwareMap, HW_BR, true);

        drivebase = new MecanumDrivebase(fl, fr, bl, br, MecanumConfig.defaults());

        // --- Default mecanum stick mapping with slow mode on right bumper ---
        stickDrive = StickDriveSource.defaultMecanumWithSlowMode(
                driverKit,
                driverKit.p1().rightBumper(), // slow-mode button
                0.30                          // slow-mode scale (30% speed)
        );

        // --- Bindings: macro control on Y / B ---
        // Y: start (or restart) the L-shaped macro.
        bindings.onPress(
                driverKit.p1().buttonY(),
                new Runnable() {
                    @Override
                    public void run() {
                        startMacro();
                    }
                }
        );

        // B: cancel macro and return to manual control.
        bindings.onPress(
                driverKit.p1().buttonB(),
                new Runnable() {
                    @Override
                    public void run() {
                        cancelMacro();
                    }
                }
        );

        telemetry.addLine("TeleOpMacroDrive: init complete");
        telemetry.update();
    }

    @Override
    public void start() {
        clock.reset(getRuntime());
    }

    @Override
    public void loop() {
        // 1) Update timing
        clock.update(getRuntime());
        double dtSec = clock.dtSec();

        // 2) Update inputs and bindings
        gamepads.update(dtSec);   // (currently a no-op, kept for future filters)
        bindings.update(dtSec);   // fires press/held/release/toggle callbacks

        // 3) Let tasks run first (they own the drive when active)
        macroRunner.update(clock);

        // 4) If no macro is active, fall back to manual stick driving
        if (!macroRunner.hasActiveTask()) {
            DriveSignal cmd = stickDrive.get(clock).clamped();
            drivebase.drive(cmd);
            drivebase.update(clock);
            lastManualCmd = cmd;
        }

        // 5) Telemetry for learning and debugging
        telemetry.addLine("Drive (manual)")
                .addData("axial", lastManualCmd.axial)
                .addData("lateral", lastManualCmd.lateral)
                .addData("omega", lastManualCmd.omega);

        telemetry.addLine("Macro")
                .addData("active", macroRunner.hasActiveTask())
                .addData("idle", macroRunner.isIdle())
                .addData("queued", macroRunner.queuedCount());

        telemetry.update();
    }

    @Override
    public void stop() {
        cancelMacro();
        drivebase.stop();
    }

    // ----------------------------------------------------------------------
    // Macro helpers
    // ----------------------------------------------------------------------

    /**
     * Build and enqueue the "L-shaped" macro.
     *
     * <p>The macro consists of three timed drive segments:</p>
     * <ol>
     *   <li>Forward for 0.8 seconds.</li>
     *   <li>Strafe right for 0.8 seconds.</li>
     *   <li>Rotate counterclockwise for 0.6 seconds.</li>
     * </ol>
     *
     * <p>If a macro is already in progress, this method clears it and starts
     * from the beginning.</p>
     */
    private void startMacro() {
        // Clear any existing tasks so we always start from a clean slate.
        macroRunner.clear();

        // Create a macro as a sequence of drive tasks.
        Task macro = SequenceTask.of(
                // Forward (+axial)
                DriveTasks.driveForSeconds(
                        drivebase,
                        new DriveSignal(+0.7, 0.0, 0.0),
                        0.8
                ),
                // Strafe right (negative lateral; +lateral is left)
                DriveTasks.driveForSeconds(
                        drivebase,
                        new DriveSignal(0.0, -0.7, 0.0),
                        0.8
                ),
                // Rotate CCW (+omega)
                DriveTasks.driveForSeconds(
                        drivebase,
                        new DriveSignal(0.0, 0.0, +0.7),
                        0.6
                )
        );

        // Enqueue the macro; TaskRunner will start it on the next update().
        macroRunner.enqueue(macro);
    }

    /**
     * Cancel any running or queued macro tasks and stop the drivebase.
     */
    private void cancelMacro() {
        macroRunner.clear();
        drivebase.stop();
    }
}

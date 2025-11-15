package edu.ftcphoenix.fw.examples;

import com.qualcomm.robotcore.eventloop.opmode.Disabled;
import com.qualcomm.robotcore.eventloop.opmode.OpMode;
import com.qualcomm.robotcore.eventloop.opmode.TeleOp;

import edu.ftcphoenix.fw.adapters.ftc.FtcHardware;
import edu.ftcphoenix.fw.drive.DriveSignal;
import edu.ftcphoenix.fw.drive.MecanumConfig;
import edu.ftcphoenix.fw.drive.MecanumDrivebase;
import edu.ftcphoenix.fw.drive.source.StickDriveSource;
import edu.ftcphoenix.fw.hal.MotorOutput;
import edu.ftcphoenix.fw.input.DriverKit;
import edu.ftcphoenix.fw.input.Gamepads;
import edu.ftcphoenix.fw.util.LoopClock;

/**
 * Minimal Phoenix FW teleop:
 * <ul>
 *   <li>Mecanum drive using {@link Gamepads}, {@link DriverKit},
 *       {@link StickDriveSource}, and {@link MecanumDrivebase}.</li>
 *   <li>No mechanisms, no bindings, no stages.</li>
 * </ul>
 *
 * <h2>Input mapping (Driver 1)</h2>
 * <ul>
 *   <li>Left stick X: strafe left/right.</li>
 *   <li>Left stick Y: forward/back (up is +).</li>
 *   <li>Right stick X: rotate.</li>
 * </ul>
 *
 * <h2>Design notes</h2>
 * <ul>
 *   <li>All drive shaping (deadband/expo, etc.) lives in {@link StickDriveSource}.</li>
 *   <li>All holonomic math is in {@link MecanumDrivebase}.</li>
 *   <li>OpMode code only wires layers together and pushes telemetry.</li>
 * </ul>
 */
@Disabled
@TeleOp(name = "FW Example: Mecanum Basic", group = "Phoenix")
public final class TeleOpMecanumBasic extends OpMode {

    // --- Hardware names (match to your robot configuration) ---
    private static final String HW_FL = "fl";
    private static final String HW_FR = "fr";
    private static final String HW_BL = "bl";
    private static final String HW_BR = "br";

    // Input & drive plumbing
    private Gamepads gamepads;
    private DriverKit driverKit;
    private StickDriveSource stickDrive;
    private MecanumDrivebase drivebase;
    private final LoopClock clock = new LoopClock();

    @Override
    public void init() {
        // 1) Inputs
        gamepads = Gamepads.create(gamepad1, gamepad2);
        driverKit = DriverKit.of(gamepads);

        // 2) Drive hardware via HAL adapters
        MotorOutput fl = FtcHardware.motor(hardwareMap, HW_FL, false);
        MotorOutput fr = FtcHardware.motor(hardwareMap, HW_FR, true);   // typical inversion
        MotorOutput bl = FtcHardware.motor(hardwareMap, HW_BL, false);
        MotorOutput br = FtcHardware.motor(hardwareMap, HW_BR, true);

        drivebase = new MecanumDrivebase(fl, fr, bl, br, MecanumConfig.defaults());

        // 3) Default mecanum mapping from DriverKit sticks
        stickDrive = StickDriveSource.defaultMecanumWithSlowMode(
                driverKit,
                driverKit.p1().rightBumper(),
                0.30
        );

        telemetry.addLine("FW Mecanum Basic: init complete");
        telemetry.update();
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

        // 2) Update inputs (hook for future filters if you add them)
        gamepads.update(dtSec);

        // 3) Compute drive command from sticks and apply
        DriveSignal cmd = stickDrive.get(clock).clamped();
        drivebase.drive(cmd);
        drivebase.update(clock);

        // 4) Telemetry for debugging
        telemetry.addLine("Drive")
                .addData("axial", cmd.axial)
                .addData("lateral", cmd.lateral)
                .addData("omega", cmd.omega);
        telemetry.update();
    }

    @Override
    public void stop() {
        drivebase.stop();
    }
}

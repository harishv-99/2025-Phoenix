package edu.ftcphoenix.fw.examples;

import com.qualcomm.robotcore.eventloop.opmode.Disabled;
import com.qualcomm.robotcore.eventloop.opmode.OpMode;
import com.qualcomm.robotcore.eventloop.opmode.TeleOp;

import edu.ftcphoenix.fw.adapters.ftc.FtcHardware;
import edu.ftcphoenix.fw.drive.DriveSignal;
import edu.ftcphoenix.fw.drive.DriveSource;
import edu.ftcphoenix.fw.drive.MecanumConfig;
import edu.ftcphoenix.fw.drive.MecanumDrivebase;
import edu.ftcphoenix.fw.drive.source.StickDriveSource;
import edu.ftcphoenix.fw.hal.MotorOutput;
import edu.ftcphoenix.fw.input.Gamepads;
import edu.ftcphoenix.fw.util.LoopClock;

/**
 * Minimal Phoenix FW teleop:
 * <ul>
 *   <li>Mecanum drive using {@link Gamepads},
 *       {@link StickDriveSource}, and {@link MecanumDrivebase}.</li>
 *   <li>No mechanisms, no bindings, no stages.</li>
 *   <li>Showcases the standard Phoenix stick shaping and optional
 *       lateral rate limiting built into {@link MecanumDrivebase}
 *       via {@link MecanumConfig}.</li>
 * </ul>
 *
 * <h2>Big picture</h2>
 *
 * <p>The goal of this example is to show the “happy path” for a basic
 * mecanum TeleOp:</p>
 *
 * <ol>
 *   <li>Wrap FTC gamepads in {@link Gamepads}.</li>
 *   <li>Wire four drive motors using {@link FtcHardware#motor}.</li>
 *   <li>Create a {@link MecanumDrivebase} with an optional
 *       {@link MecanumConfig} for scaling and smoothing.</li>
 *   <li>Create a {@link DriveSource} from P1 sticks via
 *       {@link StickDriveSource#teleOpMecanumStandard(Gamepads)}.</li>
 *   <li>In {@link #loop()}, feed the {@link DriveSignal} to the drivebase.</li>
 * </ol>
 *
 * <h2>Design notes</h2>
 *
 * <ul>
 *   <li>All stick shaping (deadband / expo / slow mode) lives in
 *       {@link StickDriveSource} and is configured via {@link edu.ftcphoenix.fw.drive.source.StickConfig}.</li>
 *   <li>All holonomic math (axial/lateral/omega → wheel powers) and any
 *       time-based smoothing live in {@link MecanumDrivebase}, configured via
 *       {@link MecanumConfig}.</li>
 *   <li>Robot-specific code in this OpMode only:
 *     <ul>
 *       <li>chooses motor names and inversions,</li>
 *       <li>chooses whether to enable lateral rate limiting,</li>
 *       <li>wires the layers together and pushes telemetry.</li>
 *     </ul>
 *   </li>
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
    private DriveSource stickDrive;
    private MecanumDrivebase drivebase;
    private final LoopClock clock = new LoopClock();

    @Override
    public void init() {
        // 1) Inputs
        gamepads = Gamepads.create(gamepad1, gamepad2);

        // 2) Drive hardware via HAL adapters
        MotorOutput fl = FtcHardware.motor(hardwareMap, HW_FL, false);
        MotorOutput fr = FtcHardware.motor(hardwareMap, HW_FR, true);   // typical inversion
        MotorOutput bl = FtcHardware.motor(hardwareMap, HW_BL, false);
        MotorOutput br = FtcHardware.motor(hardwareMap, HW_BR, true);

        // 3) Drive behavior configuration
        //
        // Start from Phoenix defaults and optionally enable lateral rate limiting.
        // Setting maxLateralRatePerSec > 0 makes strafing less “twitchy”
        // without affecting axial or rotational response.
        MecanumConfig driveCfg = MecanumConfig.defaults();
        driveCfg.maxLateralRatePerSec = 4.0;  // try 0.0 to disable smoothing

        drivebase = new MecanumDrivebase(fl, fr, bl, br, driveCfg);

        // 4) Default mecanum mapping from P1 sticks, with standard deadband/expo and
        //    a slow-mode on P1 right bumper at 30% speed.
        stickDrive = StickDriveSource.teleOpMecanumStandard(gamepads);

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

        // 2) Read driver intent from sticks
        DriveSignal cmd = stickDrive.get(clock);

        // 3) Apply drive signal (MecanumDrivebase handles scaling & smoothing)
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

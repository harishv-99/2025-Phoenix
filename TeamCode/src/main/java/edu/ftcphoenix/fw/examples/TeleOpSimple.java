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
import edu.ftcphoenix.fw.hal.ServoOutput;
import edu.ftcphoenix.fw.input.Button;
import edu.ftcphoenix.fw.input.DriverKit;
import edu.ftcphoenix.fw.input.Gamepads;
import edu.ftcphoenix.fw.input.binding.Bindings;
import edu.ftcphoenix.fw.util.LoopClock;

/**
 * Simple Phoenix robot teleop to demonstrate the FW happy path:
 * <ul>
 *   <li>Mecanum drive using {@link StickDriveSource} and {@link MecanumDrivebase}.</li>
 *   <li>Pusher (positional servo) feeding from a human-load feeder.</li>
 *   <li>Feeder wheel (continuous-rotation / motor) to move balls forward/backward.</li>
 *   <li>Shooter motor toggled on/off by a single button.</li>
 * </ul>
 *
 * <h2>Input mapping (Driver 1)</h2>
 * <ul>
 *   <li>Left stick X: strafe left/right.</li>
 *   <li>Left stick Y: forward/back (up is +).</li>
 *   <li>Right stick X: rotate.</li>
 *   <li>Button A: extend pusher (push ball toward feeder).</li>
 *   <li>Button B: retract pusher.</li>
 *   <li>Left trigger &gt;= 0.40: run feeder forward (in). Release: stop.</li>
 *   <li>Right bumper: run feeder backward (out) while held.</li>
 *   <li>Button X: toggle shooter on/off.</li>
 * </ul>
 *
 * <h2>Design notes</h2>
 * <ul>
 *   <li>All edge detection and while-held logic is in {@link Bindings}.</li>
 *   <li>All drive shaping (deadband/expo) is in {@link StickDriveSource}.</li>
 *   <li>OpMode code reads as pure intent: “on press → do X”, “while held → do Y”.</li>
 * </ul>
 */
@Disabled
@TeleOp(name = "Phoenix: Simple TeleOp", group = "Phoenix")
public final class TeleOpSimple extends OpMode {

    // --- Hardware names (match to configuration) ---
    private static final String HW_FL = "fl";
    private static final String HW_FR = "fr";
    private static final String HW_BL = "bl";
    private static final String HW_BR = "br";
    private static final String HW_PUSHER = "pusher";
    private static final String HW_FEEDER = "feeder";
    private static final String HW_SHOOTER = "shooter";

    // --- Pusher positions ---
    private static final double PUSHER_RETRACT = 0.0;
    private static final double PUSHER_EXTEND = 1.0;

    // --- Feeder powers ---
    private static final double FEED_FORWARD = 1.0;   // balls in
    private static final double FEED_REVERSE = -1.0;  // balls out

    // --- Shooter power ---
    private static final double SHOOT_POWER = 1.0;

    // --- Trigger threshold for “pressed” as a button ---
    private static final double FEED_TRIGGER_THRESHOLD = 0.40;

    // --- Input / bindings ---
    private Gamepads gamepads;
    private DriverKit driverKit;
    private Bindings bindings;
    private final LoopClock clock = new LoopClock();

    // --- Drive ---
    private MecanumDrivebase drivebase;
    private StickDriveSource stickDrive;

    // --- Mechanisms ---
    private ServoOutput pusher;
    private MotorOutput feeder;
    private MotorOutput shooter;
    private boolean shooterOn = false;

    @Override
    public void init() {
        // 1) Inputs
        gamepads = Gamepads.create(gamepad1, gamepad2);
        driverKit = DriverKit.of(gamepads);
        bindings = new Bindings();

        // 2) Drivebase wiring (HAL adapters hide FTC details)
        MotorOutput fl = FtcHardware.motor(hardwareMap, HW_FL, false);
        MotorOutput fr = FtcHardware.motor(hardwareMap, HW_FR, false);
        MotorOutput bl = FtcHardware.motor(hardwareMap, HW_BL, false);
        MotorOutput br = FtcHardware.motor(hardwareMap, HW_BR, false);
        drivebase = new MecanumDrivebase(fl, fr, bl, br, MecanumConfig.defaults());

        // Default mecanum mapping: leftX, leftY (up is +), rightX
        stickDrive = StickDriveSource.teleOpMecanum(driverKit);

        // 3) Mechanism wiring
        pusher = FtcHardware.servo(hardwareMap, HW_PUSHER, false);
        feeder = FtcHardware.crServoMotor(hardwareMap, HW_FEEDER, false);
        shooter = FtcHardware.motor(hardwareMap, HW_SHOOTER, false);

        // 4) Safe startup positions
        pusher.setPosition(PUSHER_RETRACT);
        feeder.setPower(0.0);
        shooter.setPower(0.0);
        shooterOn = false;

        // 5) Bindings: buttons -> behavior (no lambdas)

        // Pusher control: A extend, B retract
        bindings.onPress(
                driverKit.p1().buttonA(),
                new Runnable() {
                    @Override
                    public void run() {
                        pusher.setPosition(PUSHER_EXTEND);
                    }
                }
        );

        bindings.onPress(
                driverKit.p1().buttonB(),
                new Runnable() {
                    @Override
                    public void run() {
                        pusher.setPosition(PUSHER_RETRACT);
                    }
                }
        );

        // Feeder forward while left trigger is past threshold
        Button feedForwardButton = driverKit.p1().leftTriggerOver(FEED_TRIGGER_THRESHOLD);
        bindings.whileHeld(
                feedForwardButton,
                new Runnable() {
                    @Override
                    public void run() {
                        feeder.setPower(FEED_FORWARD);
                    }
                },
                new Runnable() {
                    @Override
                    public void run() {
                        feeder.setPower(0.0);
                    }
                }
        );

        // Feeder reverse while right bumper held
        bindings.whileHeld(
                driverKit.p1().rightBumper(),
                new Runnable() {
                    @Override
                    public void run() {
                        feeder.setPower(FEED_REVERSE);
                    }
                },
                new Runnable() {
                    @Override
                    public void run() {
                        feeder.setPower(0.0);
                    }
                }
        );

        // Shooter toggle on X
        bindings.toggle(
                driverKit.p1().buttonX(),
                new java.util.function.Consumer<Boolean>() {
                    @Override
                    public void accept(Boolean on) {
                        shooterOn = (on != null) && on.booleanValue();
                        shooter.setPower(shooterOn ? SHOOT_POWER : 0.0);
                    }
                }
        );

        telemetry.addLine("Phoenix Simple TeleOp: init complete");
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

        // 2) Update inputs and bindings
        gamepads.update(dtSec);   // currently a no-op, kept for future filters
        bindings.update(dtSec);   // fire press/release/held/toggle callbacks

        // 3) Compute drive command from sticks and apply
        DriveSignal driveCmd = stickDrive.get(clock).clamped();
        drivebase.drive(driveCmd);
        drivebase.update(clock);

        // 4) Telemetry for debugging
        telemetry.addLine("Drive")
                .addData("axial", driveCmd.axial)
                .addData("lateral", driveCmd.lateral)
                .addData("omega", driveCmd.omega);
        telemetry.addLine("Shooter")
                .addData("on", shooterOn);
        telemetry.addLine("Pusher")
                .addData("pos", pusher.getLastPosition());
        telemetry.addLine("Feeder")
                .addData("power", feeder.getLastPower());
        telemetry.update();
    }

    @Override
    public void stop() {
        // Ensure everything is safe on stop
        drivebase.stop();
        feeder.setPower(0.0);
        shooter.setPower(0.0);
    }
}

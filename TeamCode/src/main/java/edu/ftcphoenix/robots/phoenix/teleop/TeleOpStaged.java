package edu.ftcphoenix.robots.phoenix.teleop;

import com.qualcomm.robotcore.eventloop.opmode.OpMode;
import com.qualcomm.robotcore.eventloop.opmode.TeleOp;

import edu.ftcphoenix.fw.drive.DriveSignal;
import edu.ftcphoenix.fw.drive.source.StickDriveSource;
import edu.ftcphoenix.fw.input.DriverKit;
import edu.ftcphoenix.fw.input.Gamepads;
import edu.ftcphoenix.fw.input.binding.Bindings;
import edu.ftcphoenix.fw.stage.buffer.BufferStage;
import edu.ftcphoenix.fw.util.LoopClock;
import edu.ftcphoenix.robots.phoenix.PhoenixRobot;

/**
 * Example TeleOp that demonstrates an "auto shoot N then stop" sequence
 * using {@link PhoenixRobot}.
 *
 * <p>Behavior:
 * <ul>
 *   <li>Driver controls mecanum drive with sticks.</li>
 *   <li>Press A to automatically:
 *     <ul>
 *       <li>spin the shooter up to SHOOT speed,</li>
 *       <li>feed a fixed number of game pieces via the buffer,</li>
 *       <li>then stop the shooter.</li>
 *     </ul>
 *   </li>
 *   <li>Press B to cancel the sequence and stop shooter/buffer.</li>
 * </ul>
 *
 * <p>The buffer uses its own internal queue and downstreamReady() gate
 * (configured in PhoenixRobot) to fire shots safely when the shooter
 * is at speed.</p>
 */
@TeleOp(name = "FW Example: TeleOpStaged")
public final class TeleOpStaged extends OpMode {

    // How many shots to fire when auto sequence starts.
    private static final int AUTO_SHOT_COUNT = 3;

    // Simple state machine for the auto sequence.
    private enum AutoState {
        IDLE,
        FEEDING,   // queued shots are being processed by BufferStage
        SPINDOWN   // we've finished feeding; spinning down shooter
    }

    // Framework plumbing
    private final LoopClock clock = new LoopClock();

    private PhoenixRobot robot;
    private Gamepads gamepads;
    private DriverKit driver;
    private Bindings bindings;
    private StickDriveSource sticks;

    private AutoState autoState = AutoState.IDLE;
    private int shotsRequested = 0;

    @Override
    public void init() {
        // 1) Robot container: all hardware wiring lives inside PhoenixRobot.
        robot = new PhoenixRobot(hardwareMap);

        // 2) Inputs
        gamepads = Gamepads.create(gamepad1, gamepad2);
        driver   = DriverKit.of(gamepads);
        bindings = new Bindings();

        // 3) Drive source
        sticks = StickDriveSource.defaultMecanum(driver);

        // 4) Input bindings
        wireBindings();

        telemetry.addLine("AutoShootAndStop init complete");
        telemetry.update();
    }

    private void wireBindings() {
        // Start auto sequence on A (only if not already running).
        bindings.onPress(
                driver.p1().buttonA(),
                new Runnable() {
                    @Override
                    public void run() {
                        startAutoSequence(AUTO_SHOT_COUNT);
                    }
                }
        );

        // Cancel sequence + stop shooter on B.
        bindings.onPress(
                driver.p1().buttonB(),
                new Runnable() {
                    @Override
                    public void run() {
                        cancelAutoSequence();
                    }
                }
        );

        // Optional: manual single SEND on right bumper (only when idle).
        bindings.onPress(
                driver.p1().rightBumper(),
                new Runnable() {
                    @Override
                    public void run() {
                        if (autoState == AutoState.IDLE) {
                            robot.buffer.handle(BufferStage.BufferCmd.SEND);
                        }
                    }
                }
        );

        // Optional: manual EJECT on left bumper (only when idle).
        bindings.onPress(
                driver.p1().leftBumper(),
                new Runnable() {
                    @Override
                    public void run() {
                        if (autoState == AutoState.IDLE) {
                            robot.buffer.handle(BufferStage.BufferCmd.EJECT);
                        }
                    }
                }
        );
    }

    @Override
    public void start() {
        clock.reset(getRuntime());
    }

    @Override
    public void loop() {
        // 1) Timing
        clock.update(getRuntime());
        double dtSec = clock.dtSec();

        // 2) Inputs
        gamepads.update(dtSec);
        bindings.update(dtSec);

        // 3) Drive (driver always has control)
        DriveSignal driveCmd = sticks.get(clock).clamped();
        robot.drivebase.drive(driveCmd);
        robot.drivebase.update(clock);

        // 4) Mechanisms
        robot.shooter.update(dtSec);
        robot.buffer.update(dtSec);

        // 5) Auto-sequence state machine
        updateAutoSequence();

        // 6) Telemetry
        telemetry.addLine("AutoShoot")
                .addData("state", autoState)
                .addData("shotsRequested", shotsRequested)
                .addData("bufferActive", robot.buffer.isActive())
                .addData("queuedSend", robot.buffer.getQueuedSendCount())
                .addData("queuedEject", robot.buffer.getQueuedEjectCount());

        telemetry.addLine("Shooter")
                .addData("goal", robot.shooter.getGoal())
                .addData("ready", robot.shooter.atSetpoint());

        telemetry.update();
    }

    @Override
    public void stop() {
        // Make things safe on stop.
        cancelAutoSequence();
    }

    // ---------------------------------------------------------------------
    // Auto-sequence helpers
    // ---------------------------------------------------------------------

    /** Start an auto sequence to fire {@code shotCount} shots, if idle. */
    private void startAutoSequence(int shotCount) {
        if (autoState != AutoState.IDLE) {
            return; // ignore if already running
        }
        if (shotCount <= 0) {
            return;
        }

        // Clear any existing buffer commands and set shooter to SHOOT.
        robot.buffer.handle(BufferStage.BufferCmd.CANCEL);
        robot.shooter.setGoal(PhoenixRobot.ShooterGoal.SHOOT);

        // Queue N SEND commands; BufferStage will gate them on shooter readiness.
        for (int i = 0; i < shotCount; i++) {
            robot.buffer.handle(BufferStage.BufferCmd.SEND);
        }

        shotsRequested = shotCount;
        autoState = AutoState.FEEDING;
    }

    /** Cancel the auto sequence and stop shooter/buffer. */
    private void cancelAutoSequence() {
        autoState = AutoState.IDLE;
        shotsRequested = 0;

        robot.shooter.setGoal(PhoenixRobot.ShooterGoal.STOP);
        robot.buffer.handle(BufferStage.BufferCmd.CANCEL);
        robot.buffer.update(0.0); // force motor stop
    }

    /** Advance the auto state machine based on buffer/shooter state. */
    private void updateAutoSequence() {
        switch (autoState) {
            case IDLE:
                // Nothing to do.
                break;

            case FEEDING:
                // We consider feeding done when:
                //  - buffer has no queued SEND/EJECT, and
                //  - buffer is not currently active.
                if (!robot.buffer.isActive()
                        && robot.buffer.getQueuedSendCount() == 0
                        && robot.buffer.getQueuedEjectCount() == 0) {

                    // Done feeding; spin the shooter down.
                    robot.shooter.setGoal(PhoenixRobot.ShooterGoal.STOP);
                    autoState = AutoState.SPINDOWN;
                }
                break;

            case SPINDOWN:
                // For now, we just immediately declare we're idle after commanding STOP.
                // You could optionally wait for shooter.atSetpoint() == true for STOP.
                autoState = AutoState.IDLE;
                shotsRequested = 0;
                break;
        }
    }
}

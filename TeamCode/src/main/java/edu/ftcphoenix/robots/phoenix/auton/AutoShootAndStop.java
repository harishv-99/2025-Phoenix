package edu.ftcphoenix.robots.phoenix.auton;

import com.qualcomm.robotcore.eventloop.opmode.Autonomous;
import com.qualcomm.robotcore.eventloop.opmode.LinearOpMode;

import edu.ftcphoenix.fw.stage.buffer.BufferStage;
import edu.ftcphoenix.fw.util.LoopClock;
import edu.ftcphoenix.robots.phoenix.PhoenixRobot;

/**
 * Autonomous example that demonstrates an "auto shoot N then stop" sequence
 * using {@link PhoenixRobot}.
 *
 * <p>Behavior:
 * <ul>
 *   <li>Robot remains stationary (no drive commands in this example).</li>
 *   <li>On start:
 *     <ul>
 *       <li>spins the shooter up to SHOOT speed,</li>
 *       <li>feeds a fixed number of game pieces via the buffer,</li>
 *       <li>then stops the shooter.</li>
 *     </ul>
 *   </li>
 * </ul>
 *
 * <p>The buffer uses its own internal queue and downstreamReady() gate
 * (configured in PhoenixRobot) to fire shots safely when the shooter
 * is at speed.</p>
 */
@Autonomous(name = "FW Auto: AutoShootAndStop")
public final class AutoShootAndStop extends LinearOpMode {

    // How many shots to fire when auto sequence starts.
    private static final int AUTO_SHOT_COUNT = 3;

    // Simple state machine for the auto sequence.
    private enum AutoState {
        IDLE,
        FEEDING,   // queued shots are being processed by BufferStage
        SPINDOWN   // we've finished feeding; spinning down shooter
    }

    private final LoopClock clock = new LoopClock();

    private PhoenixRobot robot;
    private AutoState autoState = AutoState.IDLE;
    private int shotsRequested = 0;

    @Override
    public void runOpMode() throws InterruptedException {
        // 1) Robot container: all hardware wiring lives inside PhoenixRobot.
        robot = new PhoenixRobot(hardwareMap);

        telemetry.addLine("AutoShootAndStopAuto init complete");
        telemetry.update();

        // 2) Wait for start
        waitForStart();
        if (isStopRequested()) {
            return;
        }

        // 3) Initialize timing and start the auto sequence
        clock.reset(getRuntime());
        startAutoSequence(AUTO_SHOT_COUNT);

        // 4) Main autonomous loop
        while (opModeIsActive() && !isStopRequested()) {
            // Timing
            clock.update(getRuntime());
            double dtSec = clock.dtSec();

            // Keep drivebase safe/stationary in this example
            robot.drivebase.stop();
            robot.drivebase.update(clock);

            // Mechanisms
            robot.shooter.update(dtSec);
            robot.buffer.update(dtSec);

            // Auto sequence state machine
            updateAutoSequence();

            // Telemetry
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

            idle(); // yield to SDK
        }

        // 5) Make things safe when the OpMode ends
        cancelAutoSequence();
    }

    // ---------------------------------------------------------------------
    // Auto-sequence helpers
    // ---------------------------------------------------------------------

    /**
     * Start an auto sequence to fire {@code shotCount} shots, if idle.
     */
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

    /**
     * Cancel the auto sequence and stop shooter/buffer.
     */
    private void cancelAutoSequence() {
        autoState = AutoState.IDLE;
        shotsRequested = 0;

        robot.shooter.setGoal(PhoenixRobot.ShooterGoal.STOP);
        robot.buffer.handle(BufferStage.BufferCmd.CANCEL);
        robot.buffer.update(0.0); // force motor stop
    }

    /**
     * Advance the auto state machine based on buffer/shooter state.
     */
    private void updateAutoSequence() {
        switch (autoState) {
            case IDLE:
                // Nothing to do.
                break;

            case FEEDING:
                // Feeding is done when:
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
                // For now, we immediately consider ourselves done after commanding STOP.
                // You could optionally wait for shooter.atSetpoint() == true for STOP.
                autoState = AutoState.IDLE;
                shotsRequested = 0;
                break;
        }
    }
}

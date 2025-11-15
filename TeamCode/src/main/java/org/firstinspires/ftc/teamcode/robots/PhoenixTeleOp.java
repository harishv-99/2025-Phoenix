package org.firstinspires.ftc.teamcode.robots;

import com.qualcomm.robotcore.eventloop.opmode.TeleOp;

import edu.ftcphoenix.fw.robot.PhoenixTeleOpBase;
import edu.ftcphoenix.fw.util.LoopClock;
import edu.ftcphoenix.robots.phoenix.PhoenixRobot;

/**
 * Thin TeleOp OpMode shell for the Phoenix robot.
 *
 * <h2>Role</h2>
 * <p>This class is intentionally small. It:
 * <ul>
 *   <li>Extends {@link PhoenixTeleOpBase}, which wires:
 *     <ul>
 *       <li>{@code hardwareMap}</li>
 *       <li>{@code driverKit()} and {@code gamepads()}</li>
 *       <li>input bindings</li>
 *       <li>{@code clock()} ({@link LoopClock})</li>
 *       <li>{@code telemetry}</li>
 *     </ul>
 *   </li>
 *   <li>Constructs a {@link PhoenixRobot} in {@link #onInitRobot()}.</li>
 *   <li>Forwards TeleOp lifecycle calls to the robot.</li>
 * </ul>
 *
 * <p>All real robot behavior lives in {@code PhoenixRobot} and its subsystems.
 */
@TeleOp(name = "Phoenix: TeleOp", group = "Phoenix")
public final class PhoenixTeleOp extends PhoenixTeleOpBase {

    private PhoenixRobot robot;

    /**
     * Called once from {@link #init()} after the base has wired hardware,
     * inputs, and the loop clock.
     *
     * <p>We construct the {@link PhoenixRobot} here but do not yet call
     * {@code teleopInit()} — that should happen when the driver presses
     * PLAY (in {@link #onStartRobot()}), not at INIT.
     */
    @Override
    protected void onInitRobot() {
        // PhoenixTeleOpBase provides hardwareMap, driverKit(), telemetry.
        robot = new PhoenixRobot(hardwareMap, driverKit(), telemetry);
    }

    /**
     * Called once from {@link #start()} when the driver presses PLAY.
     *
     * <p>The base class should reset the {@link LoopClock} before this is
     * called, so TeleOp starts with a clean time reference.
     */
    @Override
    protected void onStartRobot() {
        if (robot != null) {
            robot.onTeleopInit();
        }
    }

    /**
     * Called every loop during TeleOp, from {@link #loop()}.
     *
     * <p>{@code dtSec} is computed by the base class from the internal
     * {@link LoopClock}. We forward the shared {@link #clock()} to the
     * robot so all subsystems can use a single time source.
     *
     * @param dtSec delta time in seconds since the last loop
     */
    @Override
    protected void onLoopRobot(double dtSec) {
        if (robot != null) {
            robot.onTeleopLoop(clock());
        }
        // PhoenixRobot.teleopLoop(...) calls telemetry.update().
    }

    /**
     * Called once when the OpMode is stopping.
     *
     * <p>Forwards to {@link PhoenixRobot#onStop()} so subsystems can shut
     * down cleanly (for example, stop motors).
     */
    @Override
    protected void onStopRobot() {
        if (robot != null) {
            robot.onStop();
        }
    }
}

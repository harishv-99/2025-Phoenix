package edu.ftcphoenix.fw.robot;

import com.qualcomm.robotcore.eventloop.opmode.OpMode;

import edu.ftcphoenix.fw.input.DriverKit;
import edu.ftcphoenix.fw.input.Gamepads;
import edu.ftcphoenix.fw.input.binding.Bindings;
import edu.ftcphoenix.fw.util.LoopClock;

/**
 * Framework base class for Phoenix teleop OpModes.
 *
 * <p>Responsibilities:
 * <ul>
 *   <li>Wire up {@link Gamepads}, {@link DriverKit}, {@link Bindings},
 *       and {@link LoopClock}.</li>
 *   <li>Ensure inputs and bindings are updated every loop.</li>
 *   <li>Expose simple helpers for subclasses:
 *       {@link #p1()}, {@link #p2()}, {@link #bind()}.</li>
 * </ul>
 *
 * <p>Subclassing pattern:
 * <ul>
 *   <li>Annotate your subclass with {@code @TeleOp(...)}.</li>
 *   <li>Override {@link #onInitRobot()} to:
 *       <ul>
 *         <li>Map hardware.</li>
 *         <li>Create drive sources / stages.</li>
 *         <li>Define bindings using {@link #bind()} and {@link #p1()} / {@link #p2()}.</li>
 *       </ul>
 *   </li>
 *   <li>Override {@link #onLoopRobot(double)} to:
 *       <ul>
 *         <li>Update drive / stages / subsystems.</li>
 *         <li>Publish telemetry.</li>
 *       </ul>
 *   </li>
 * </ul>
 *
 * <p>This class is reusable for all teleop OpModes and keeps robot-specific
 * code focused on intent instead of input plumbing.
 */
public abstract class PhoenixTeleOpBase extends OpMode {

    private Gamepads gamepads;
    private DriverKit driverKit;
    private Bindings bindings;
    private LoopClock clock;

    public final void init() {
        // Core input plumbing
        gamepads = Gamepads.create(gamepad1, gamepad2);
        driverKit = DriverKit.of(gamepads);
        bindings = new Bindings();
        clock = new LoopClock();

        // Let subclass wire hardware + bindings
        onInitRobot();

        telemetry.addLine("PhoenixTeleOpBase: init complete");
        telemetry.update();
    }

    public final void start() {
        clock.reset(getRuntime());
        onStartRobot();
    }

    public final void loop() {
        // Update timing
        clock.update(getRuntime());
        double dtSec = clock.dtSec();

        // Update inputs + bindings
        gamepads.update(dtSec);
        bindings.update(dtSec);

        // Delegate to subclass for robot behavior
        onLoopRobot(dtSec);
    }

    public final void stop() {
        onStopRobot();
    }

    // --------------------------------------------------------------------
    // Subclass hooks
    // --------------------------------------------------------------------

    /**
     * Called once from {@link #init()} after FW wiring is done.
     *
     * <p>Use this to:
     * <ul>
     *   <li>Map hardware (motors, servos, sensors).</li>
     *   <li>Construct stages/subsystems.</li>
     *   <li>Set up {@link Bindings} using {@link #bind()} and {@link #p1()} / {@link #p2()}.</li>
     * </ul>
     */
    protected abstract void onInitRobot();

    /**
     * Called once from {@link #start()} after the internal loop clock is reset.
     *
     * <p>Optional; default implementation does nothing.</p>
     */
    protected void onStartRobot() {
        // default no-op
    }

    /**
     * Called every loop after inputs and bindings have been updated.
     *
     * @param dtSec approximate time step since last loop, in seconds
     */
    protected abstract void onLoopRobot(double dtSec);

    /**
     * Called once from {@link #stop()}. Optional cleanup hook.
     */
    protected void onStopRobot() {
        // default no-op
    }

    // --------------------------------------------------------------------
    // Protected helpers for subclasses
    // --------------------------------------------------------------------

    /**
     * Player 1 view (primary driver).
     */
    protected DriverKit.Player p1() {
        return driverKit.p1();
    }

    /**
     * Player 2 view (co-driver).
     */
    protected DriverKit.Player p2() {
        return driverKit.p2();
    }

    /**
     * Binding manager: use this to configure onPress/whileHeld/toggle behaviors.
     */
    protected Bindings bind() {
        return bindings;
    }

    /**
     * Loop clock, in case you need access to the raw object.
     */
    protected LoopClock clock() {
        return clock;
    }

    /**
     * Full DriverKit, if needed.
     */
    protected DriverKit driverKit() {
        return driverKit;
    }

    /**
     * Full Gamepads, if needed.
     */
    protected Gamepads gamepads() {
        return gamepads;
    }
}

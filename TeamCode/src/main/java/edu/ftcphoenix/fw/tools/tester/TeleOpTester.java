package edu.ftcphoenix.fw.tools.tester;

/**
 * Minimal lifecycle for Phoenix "tester programs" that can run inside an FTC OpMode.
 *
 * <p>Design goals:
 * <ul>
 *   <li>Testers live outside TeamCode (e.g., in robots.*) and are reusable.</li>
 *   <li>TeamCode provides a single OpMode wrapper that runs a tester or a tester suite.</li>
 *   <li>Support INIT-time menus (camera selection, mode selection) before the match starts.</li>
 * </ul>
 */
public interface TeleOpTester {

    /**
     * Human-friendly name used by menus / telemetry.
     */
    String name();

    /**
     * Called once during OpMode init.
     */
    void init(TesterContext ctx);

    /**
     * Called repeatedly during the FTC OpMode INIT phase (before start is pressed).
     *
     * <p>Use this for selection screens (e.g., choose a camera from config names) and
     * for any HardwareMap lookups that you want to keep in init.</p>
     *
     * <p><b>Important:</b> Avoid commanding actuators here. Keep it to UI/selection and setup.</p>
     *
     * @param dtSec Time since last init-loop (seconds).
     */
    default void initLoop(double dtSec) {
    }

    /**
     * Called once when the OpMode transitions from INIT to RUNNING.
     */
    default void start() {
    }

    /**
     * Called every OpMode loop while RUNNING.
     *
     * @param dtSec Time since last loop (seconds).
     */
    void loop(double dtSec);

    /**
     * Called once when the OpMode stops.
     */
    default void stop() {
    }
}

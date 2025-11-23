package edu.ftcphoenix.fw2.subsystems;

import org.firstinspires.ftc.robotcore.external.Telemetry;

import edu.ftcphoenix.fw2.core.FrameClock;

/**
 * Minimal lifecycle contract for reusable components that update every loop.
 *
 * <p>Framework intent:
 * <ul>
 *   <li>Keep mechanisms and app-level subsystems consistent and easy to wire.</li>
 *   <li>Allow {@code RobotBase} (or your Robot class) to uniformly manage enable/disable/stop.</li>
 * </ul>
 *
 * <h2>Typical usage</h2>
 * <pre>{@code
 * public final class DriveSubsystem implements Subsystem {
 *   @Override public void onEnable() { /* e.g., zero encoders, clear filters *-/ }
 *   @Override public void update(FrameClock clock) { /* push commands to hardware *-/ }
 *   @Override public void stop() { /* set outputs safe (0 power) *-/ }
 * }
 * }</pre>
 *
 * <h2>Lifecycle</h2>
 * <ul>
 *   <li>{@link #onEnable()} is called once when the OpMode starts (after init).</li>
 *   <li>{@link #update(FrameClock)} is called once per loop.</li>
 *   <li>{@link #onDisable()} is called once when the OpMode ends (before {@link #stop()}).</li>
 *   <li>{@link #stop()} is called last and must leave hardware in a safe state.</li>
 * </ul>
 *
 * <h2>Telemetry</h2>
 * <p>{@link #addTelemetry(Telemetry, String, boolean)} is optional and meant for lightweight taps.
 * Avoid heavy computation here; prefer to compute inside {@link #update(FrameClock)} and just report.</p>
 */
public interface Subsystem {

    /**
     * Called once when the OpMode transitions to active.
     * Default: no-op.
     */
    default void onEnable() {}

    /**
     * Called once per loop. Push targets to hardware here.
     *
     * @param clock frame clock providing dt and timestamp.
     */
    default void update(FrameClock clock) {}

    /**
     * Called once when the OpMode transitions out of active (before {@link #stop()}).
     * Default: no-op.
     */
    default void onDisable() {}

    /**
     * Must leave hardware safe (e.g., power=0). Called exactly once at shutdown.
     * Default: no-op.
     */
    default void stop() {}

    /**
     * Optional telemetry hook.
     *
     * @param tel telemetry sink.
     * @param label short label/prefix for metrics.
     * @param verbose include extra details if true.
     */
    default void addTelemetry(Telemetry tel, String label, boolean verbose) {}
}

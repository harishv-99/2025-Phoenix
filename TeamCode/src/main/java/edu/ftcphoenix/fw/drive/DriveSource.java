package edu.ftcphoenix.fw.drive;

import edu.ftcphoenix.fw.util.LoopClock;

/**
 * Source of high-level drive commands for a drivetrain.
 *
 * <p>A {@link DriveSource} takes in the current loop timing (via
 * {@link LoopClock}) and produces a {@link DriveSignal} each loop.
 *
 * <p>Typical implementations include:
 * <ul>
 *   <li>{@code StickDriveSource} – map gamepad sticks to a drive signal.</li>
 *   <li>A motion planner – follow a trajectory and emit commands.</li>
 *   <li>A closed-loop heading controller – maintain or turn to a target angle.</li>
 * </ul>
 *
 * <p>Semantics:
 * <ul>
 *   <li>{@link #get(LoopClock)} is called once per loop by the OpMode.</li>
 *   <li>Implementations may be stateless (pure function of current inputs) or
 *       stateful (e.g., with internal filters, rate limiters, etc.).</li>
 *   <li>The returned {@link DriveSignal} is usually expected to be in the
 *       range [-1, +1] for each component, but callers may clamp if needed.</li>
 * </ul>
 */
public interface DriveSource {

    /**
     * Produce a drive signal for the current loop.
     *
     * @param clock loop timing helper; implementations may use this for
     *              dt-based smoothing or rate limiting
     * @return drive command for this loop (never null)
     */
    DriveSignal get(LoopClock clock);
}

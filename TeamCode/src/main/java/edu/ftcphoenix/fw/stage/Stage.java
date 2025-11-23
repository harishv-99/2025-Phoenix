package edu.ftcphoenix.fw.stage;

import edu.ftcphoenix.fw.util.LoopClock;

/**
 * Minimal interface for a time-updated pipeline stage.
 *
 * <p>Stages are small units of behavior that are advanced once per loop
 * with a time step. Examples in this project include:</p>
 * <ul>
 *   <li>{@link edu.ftcphoenix.fw.stage.setpoint.SetpointStage}</li>
 *   <li>{@link edu.ftcphoenix.fw.stage.buffer.BufferStage}</li>
 * </ul>
 *
 * <p>The interface is intentionally tiny so that stages remain easy to
 * compose and test.</p>
 */
public interface Stage {
    /**
     * Advance the stage by {@code dtSec} seconds of simulated time.
     *
     * <p>Callers are expected to pass the loop delta time in seconds
     * (for example from {@link LoopClock#dtSec()}).</p>
     */
    void update(double dtSec);

    /**
     * Optional lifecycle hook to reset any internal state.
     *
     * <p>Stages that do not maintain internal state may leave this as the
     * default no-op implementation.</p>
     */
    default void reset() {
        // default no-op
    }
}

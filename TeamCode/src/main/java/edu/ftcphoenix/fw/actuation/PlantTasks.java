package edu.ftcphoenix.fw.actuation;

import java.util.Objects;

import edu.ftcphoenix.fw.task.InstantTask;
import edu.ftcphoenix.fw.task.RunForSecondsTask;
import edu.ftcphoenix.fw.task.Task;
import edu.ftcphoenix.fw.util.LoopClock;

/**
 * Helper methods for creating common {@link Task} patterns that command a {@link Plant}.
 *
 * <p>The goal is to make robot code read like:</p>
 *
 * <pre>{@code
 * // Intake at full power for 0.7 seconds, then stop.
 * Task intakePulse = PlantTasks.holdForSeconds(intakePlant, +1.0, 0.7);
 *
 * // Arm: set a target angle and wait until atSetpoint() (no timeout).
 * Task moveArm = PlantTasks.setTargetAndWaitForSetpoint(armPlant, Math.toRadians(45.0));
 * }</pre>
 *
 * <p>All helpers here are <b>non-blocking</b> and are intended to be used with
 * {@link edu.ftcphoenix.fw.task.TaskRunner} and the rest of the {@code fw.task}
 * package.</p>
 *
 * <h2>Design principles</h2>
 * <ul>
 *   <li>Keep <b>Plant update timing</b> in your main robot loop or mechanism
 *       (<code>plant.update(dtSec)</code> is <b>not</b> called from these tasks).</li>
 *   <li>Use tasks only for <b>targets and waiting</b>: “hold for time” and
 *       “set target and wait for setpoint (with optional timeout).”</li>
 *   <li>Keep this class domain-specific to actuation; it lives in
 *       {@code fw.actuation} and simply reuses the generic task primitives.</li>
 * </ul>
 */
public final class PlantTasks {

    private PlantTasks() {
        // Utility holder; do not instantiate.
    }

    // ------------------------------------------------------------------------
    // Timed hold patterns
    // ------------------------------------------------------------------------

    /**
     * Hold a plant at a given target value for a fixed amount of time, then
     * optionally change the target again when time is up.
     *
     * <p>Semantics:</p>
     * <ul>
     *   <li>On start:
     *     <ul>
     *       <li>Calls {@link Plant#setTarget(double)} with {@code target}.</li>
     *     </ul>
     *   </li>
     *   <li>While the task is running:
     *     <ul>
     *       <li><b>Does not</b> call {@link Plant#update(double)}; your robot
     *           code is responsible for updating the plant each loop.</li>
     *     </ul>
     *   </li>
     *   <li>When {@code durationSec} elapses:
     *     <ul>
     *       <li>Calls {@link Plant#setTarget(double)} with {@code afterTarget}.</li>
     *       <li>{@link Task#isFinished()} becomes {@code true}.</li>
     *     </ul>
     *   </li>
     * </ul>
     *
     * <p>This is a good fit for:</p>
     * <ul>
     *   <li>Intake power pulses (target = +1.0, {@code afterTarget} = 0.0).</li>
     *   <li>Momentary “kick” motions.</li>
     *   <li>Timed velocity bursts for flywheels (if you want them to spin down
     *       automatically afterward).</li>
     * </ul>
     *
     * <p><b>Note:</b> Your main loop or mechanism should still call
     * {@link Plant#update(double)} each iteration; this task only changes targets
     * and waits for time to elapse.</p>
     *
     * @param plant       the plant to command
     * @param target      target value to hold during the timed interval
     * @param durationSec duration in seconds; must be {@code >= 0}
     * @param afterTarget target value to apply once the time elapses
     * @return a {@link Task} that performs the timed hold
     */
    public static Task holdForSeconds(final Plant plant,
                                      final double target,
                                      final double durationSec,
                                      final double afterTarget) {
        Objects.requireNonNull(plant, "plant is required");

        return new RunForSecondsTask(
                durationSec,
                // onStart: command initial target
                () -> plant.setTarget(target),
                // onUpdate: no-op; plant.update(dt) is handled elsewhere
                null,
                // onFinish: command follow-up target
                () -> plant.setTarget(afterTarget)
        );
    }

    /**
     * Convenience overload: hold a plant at a given target for a fixed amount
     * of time, then automatically set the target back to 0.0.
     *
     * <p>Equivalent to:</p>
     *
     * <pre>{@code
     * PlantTasks.holdForSeconds(plant, target, durationSec, 0.0);
     * }</pre>
     *
     * @param plant       the plant to command
     * @param target      target value to hold during the timed interval
     * @param durationSec duration in seconds; must be {@code >= 0}
     * @return a {@link Task} that performs the timed hold and then sets target
     * back to 0.0
     */
    public static Task holdForSeconds(final Plant plant,
                                      final double target,
                                      final double durationSec) {
        return holdForSeconds(plant, target, durationSec, 0.0);
    }

    // ------------------------------------------------------------------------
    // Set target + wait-for-setpoint patterns
    // ------------------------------------------------------------------------

    /**
     * Create a {@link Task} that:
     * <ol>
     *   <li>Sets the plant target once at start.</li>
     *   <li>Relies on your main loop / mechanism to call
     *       {@link Plant#update(double)} each iteration.</li>
     *   <li>Finishes when {@link Plant#atSetpoint()} returns {@code true}.</li>
     * </ol>
     *
     * <p><b>Important:</b> this variant has <b>no timeout</b>. If
     * {@link Plant#atSetpoint()} never becomes true (e.g., a mechanism is
     * blocked), this task will never finish. For competition robots, prefer
     * the timeout overload below.</p>
     *
     * @param plant  plant to command
     * @param target target setpoint (e.g., angle radians, velocity, power)
     * @return a {@link Task} that sets the target and finishes when
     * {@code plant.atSetpoint()} is true
     */
    public static Task setTargetAndWaitForSetpoint(final Plant plant,
                                                   final double target) {
        return setTargetAndWaitForSetpoint(plant, target, 0.0, null);
    }

    /**
     * Create a {@link Task} that:
     * <ol>
     *   <li>Sets the plant target once at start.</li>
     *   <li>Relies on your main loop / mechanism to call
     *       {@link Plant#update(double)} each iteration.</li>
     *   <li>Finishes when {@link Plant#atSetpoint()} returns {@code true},</li>
     *   <li>and optionally enforces a timeout.</li>
     * </ol>
     *
     * <p>Semantics:</p>
     * <ul>
     *   <li>On start:
     *     <ul>
     *       <li>Calls {@link Plant#setTarget(double)} with {@code target}.</li>
     *       <li>Resets internal elapsed-time tracking.</li>
     *     </ul>
     *   </li>
     *   <li>On each update while running:
     *     <ul>
     *       <li>Accumulates elapsed time using {@link LoopClock#dtSec()}.</li>
     *       <li><b>Does not</b> call {@link Plant#update(double)}; your robot
     *           code is responsible for updating the plant each loop.</li>
     *       <li>Checks {@link Plant#atSetpoint()}; if true, the task finishes.</li>
     *       <li>If {@code timeoutSec > 0}:
     *         <ul>
     *           <li>The task finishes when {@code atSetpoint()} is true, <b>or</b>
     *               when the timeout elapses, whichever comes first.</li>
     *           <li>If the timeout elapses first and {@code onTimeout} is not null,
     *               {@code onTimeout.run()} is called exactly once.</li>
     *         </ul>
     *       </li>
     *     </ul>
     *   </li>
     * </ul>
     *
     * <p>This pattern is useful for autonomous scripts such as “set arm angle,
     * but don’t wait forever if something goes wrong.”</p>
     *
     * @param plant      plant to command
     * @param target     target setpoint
     * @param timeoutSec timeout in seconds; {@code <= 0} means “no timeout”
     * @param onTimeout  optional callback invoked once if the timeout elapses
     *                   before {@code plant.atSetpoint()} is true (may be null)
     * @return a {@link Task} implementing the described behavior
     */
    public static Task setTargetAndWaitForSetpoint(final Plant plant,
                                                   final double target,
                                                   final double timeoutSec,
                                                   final Runnable onTimeout) {
        Objects.requireNonNull(plant, "plant is required");

        return new Task() {
            private boolean started = false;
            private boolean finished = false;
            private double elapsedSec = 0.0;

            @Override
            public void start(LoopClock clock) {
                if (started) {
                    return;
                }
                started = true;
                elapsedSec = 0.0;
                finished = false;

                plant.setTarget(target);

                // Edge case: zero-timeout and already at setpoint
                if (timeoutSec <= 0.0 && plant.atSetpoint()) {
                    finished = true;
                }
            }

            @Override
            public void update(LoopClock clock) {
                if (!started || finished) {
                    return;
                }

                double dtSec = clock.dtSec();
                elapsedSec += dtSec;

                // 1) Check if we've reached the setpoint.
                if (plant.atSetpoint()) {
                    finished = true;
                    return;
                }

                // 2) Check timeout (only if enabled).
                if (timeoutSec > 0.0 && elapsedSec >= timeoutSec) {
                    finished = true;
                    if (onTimeout != null) {
                        onTimeout.run();
                    }
                }
            }

            @Override
            public boolean isFinished() {
                return finished;
            }
        };
    }

    // ------------------------------------------------------------------------
    // Instant target set
    // ------------------------------------------------------------------------

    /**
     * Convenience method: create a {@link Task} that simply sets the plant
     * target once and then finishes immediately.
     *
     * <p>This is a thin wrapper over {@link InstantTask}, provided mainly so
     * robot code can stay in the “PlantTasks.*” vocabulary when building
     * sequences.</p>
     *
     * <pre>{@code
     * TaskRunner runner = new TaskRunner();
     * runner.enqueue(PlantTasks.setTargetInstant(armPlant, Math.toRadians(30.0)));
     * }</pre>
     *
     * @param plant  plant to command
     * @param target target value to set
     * @return a {@link Task} that sets the target once and then finishes
     */
    public static Task setTargetInstant(final Plant plant, final double target) {
        Objects.requireNonNull(plant, "plant is required");
        return new InstantTask(() -> plant.setTarget(target));
    }
}

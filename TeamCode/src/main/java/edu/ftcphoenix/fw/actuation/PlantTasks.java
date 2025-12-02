package edu.ftcphoenix.fw.actuation;

import java.util.Objects;

import edu.ftcphoenix.fw.task.Task;
import edu.ftcphoenix.fw.task.TaskOutcome;
import edu.ftcphoenix.fw.task.Tasks;
import edu.ftcphoenix.fw.util.LoopClock;

/**
 * Helper methods and builders for creating common {@link Task} patterns
 * that command a {@link Plant}.
 *
 * <p>The goal is to make robot code read like:</p>
 *
 * <pre>{@code
 * // Intake at full power for 0.7 seconds, then stop.
 * Task intakePulse = PlantTasks.holdForSeconds(intakePlant, +1.0, 0.7, 0.0);
 *
 * // Arm: move to an angle and wait until atSetpoint() (with timeout).
 * Task moveArm = PlantTasks.moveTo(armPlant, Math.toRadians(45.0))
 *         .waitUntilAtSetpointOrTimeout(1.5)
 *         .thenHold()
 *         .build();
 * }</pre>
 *
 * <p>All helpers here are <b>non-blocking</b> and are intended to be used with
 * {@link edu.ftcphoenix.fw.task.TaskRunner} and the rest of the {@code fw.task}
 * package. These tasks set targets on plants and rely on some other mechanism
 * to call {@link Plant#update(double)} each loop.</p>
 */
public final class PlantTasks {

    private PlantTasks() {
        // Utility class; do not instantiate.
    }

    // ------------------------------------------------------------------------
    // Timed hold patterns (simple helpers backed by the builder)
    // ------------------------------------------------------------------------

    /**
     * Create a {@link Task} that:
     * <ol>
     *   <li>Sets the plant target once at the start.</li>
     *   <li>Relies on your main loop / mechanism to call
     *       {@link Plant#update(double)} each iteration.</li>
     *   <li>Holds that target for a fixed duration (purely by time).</li>
     *   <li>After the duration elapses, sets a follow-up target.</li>
     * </ol>
     *
     * <p>This is implemented using the builder:</p>
     *
     * <pre>{@code
     * PlantTasks.moveTo(plant, target)
     *     .waitSeconds(durationSec)
     *     .thenGoTo(afterTarget)
     *     .build();
     * }</pre>
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

        return PlantTasks.moveTo(plant, target)
                .waitSeconds(durationSec)
                .thenGoTo(afterTarget)
                .build();
    }

    /**
     * Convenience overload that holds the same target before and after the
     * timed interval.
     *
     * <p>Equivalent to:</p>
     *
     * <pre>{@code
     * PlantTasks.holdForSeconds(plant, target, durationSec, target);
     * }</pre>
     *
     * @param plant       the plant to command
     * @param target      target value to hold during the timed interval
     * @param durationSec duration in seconds; must be {@code >= 0}
     * @return a {@link Task} that performs the timed hold and leaves the target
     * at the commanded value once the time has elapsed
     */
    public static Task holdForSeconds(final Plant plant,
                                      final double target,
                                      final double durationSec) {
        return holdForSeconds(plant, target, durationSec, target);
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
     *   <li>or when {@code timeoutSec} seconds elapse (whichever comes first).</li>
     * </ol>
     *
     * <p>When a timeout occurs and {@code onTimeout} is non-null, the
     * {@code onTimeout.run()} callback is invoked exactly once via a
     * follow-up {@link Task}.</p>
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

        Task move;
        if (timeoutSec > 0.0) {
            move = PlantTasks.moveTo(plant, target)
                    .waitUntilAtSetpointOrTimeout(timeoutSec)
                    .thenHold()
                    .build();
        } else {
            move = PlantTasks.moveTo(plant, target)
                    .waitUntilAtSetpoint()
                    .thenHold()
                    .build();
        }

        // No timeout or no callback: just return the move task directly.
        if (timeoutSec <= 0.0 || onTimeout == null) {
            return move;
        }

        // Otherwise, branch to call onTimeout exactly once if we timed out.
        return Tasks.branchOnOutcome(
                move,
                /* onSuccess */ Tasks.noop(),
                /* onTimeout */ Tasks.runOnce(onTimeout)
        );
    }

    // ------------------------------------------------------------------------
    // Instant target set (simple helper backed by the builder)
    // ------------------------------------------------------------------------

    /**
     * Convenience method: create a {@link Task} that simply sets the plant
     * target once and then finishes immediately.
     *
     * <p>This is implemented using the builder as:</p>
     *
     * <pre>{@code
     * PlantTasks.moveTo(plant, target)
     *     .dontWait()
     *     .build();
     * }</pre>
     *
     * @param plant  plant to command
     * @param target target value
     * @return a {@link Task} that sets the target once and then completes
     */
    public static Task setTargetInstant(final Plant plant,
                                        final double target) {
        Objects.requireNonNull(plant, "plant is required");
        return PlantTasks.moveTo(plant, target)
                .dontWait()
                .build();
    }

    // ------------------------------------------------------------------------
    // Builder API
    // ------------------------------------------------------------------------

    /**
     * Begin building a move-to-target task for a plant.
     *
     * <p>The builder lets you choose:</p>
     * <ul>
     *   <li>How the move decides it is complete:
     *     <ul>
     *       <li>{@link MoveStart#waitUntilAtSetpoint()}</li>
     *       <li>{@link MoveStart#waitUntilAtSetpointOrTimeout(double)}</li>
     *       <li>{@link MoveStart#waitSeconds(double)}</li>
     *       <li>{@link MoveStart#dontWait()}</li>
     *     </ul>
     *   </li>
     *   <li>What happens to the plant once the move is complete:
     *     <ul>
     *       <li>{@link MovePost#thenHold()}</li>
     *       <li>{@link MovePost#thenGoTo(double)}</li>
     *     </ul>
     *   </li>
     * </ul>
     *
     * @param plant  plant to command
     * @param target target setpoint
     * @return the first stage of the builder
     */
    public static MoveStart moveTo(final Plant plant,
                                   final double target) {
        Objects.requireNonNull(plant, "plant is required");
        return new MoveBuilder(plant, target);
    }

    /**
     * First builder stage: choose how the move decides it is complete
     * (setpoint, time, timeout, or instant).
     */
    public interface MoveStart {
        /**
         * Complete when {@link Plant#atSetpoint()} first becomes true.
         */
        MovePost waitUntilAtSetpoint();

        /**
         * Complete when {@link Plant#atSetpoint()} becomes true, or when the
         * given timeout elapses, whichever happens first.
         *
         * @param timeoutSec timeout in seconds; must be {@code > 0}
         */
        MovePost waitUntilAtSetpointOrTimeout(double timeoutSec);

        /**
         * Complete after a fixed amount of time has elapsed.
         *
         * @param seconds duration in seconds; must be {@code >= 0}
         */
        MovePost waitSeconds(double seconds);

        /**
         * Complete immediately after setting the target once.
         *
         * <p>This implicitly behaves like {@code thenHold()} and skips the
         * post-behavior stage, returning the final build step directly.</p>
         */
        MoveBuildStep dontWait();
    }

    /**
     * Second builder stage: choose what happens to the plant once the move is
     * complete.
     */
    public interface MovePost {
        /**
         * Leave the plant holding the last commanded target.
         */
        MoveBuildStep thenHold();

        /**
         * After completion, command the given safe target.
         *
         * @param safeTarget target to apply once the move is done
         */
        MoveBuildStep thenGoTo(double safeTarget);
    }

    /**
     * Final builder stage.
     */
    public interface MoveBuildStep {
        /**
         * Build a {@link Task} implementing the configured behavior.
         */
        Task build();
    }

    private enum MoveCompletionMode {
        INSTANT,
        WAIT_SETPOINT,
        WAIT_TIME,
        WAIT_SETPOINT_OR_TIMEOUT
    }

    private enum PostBehavior {
        HOLD,
        SAFE_TARGET
    }

    /**
     * Concrete builder implementation.
     */
    private static final class MoveBuilder
            implements MoveStart, MovePost, MoveBuildStep {

        private final Plant plant;
        private final double target;

        private MoveCompletionMode completionMode = MoveCompletionMode.INSTANT;
        private double waitSeconds = 0.0;
        private double timeoutSec = 0.0;

        private PostBehavior postBehavior = PostBehavior.HOLD;
        private double postTarget = 0.0;

        MoveBuilder(final Plant plant, final double target) {
            this.plant = plant;
            this.target = target;
        }

        @Override
        public MovePost waitUntilAtSetpoint() {
            this.completionMode = MoveCompletionMode.WAIT_SETPOINT;
            this.waitSeconds = 0.0;
            this.timeoutSec = 0.0;
            return this;
        }

        @Override
        public MovePost waitUntilAtSetpointOrTimeout(final double timeoutSec) {
            if (timeoutSec <= 0.0) {
                throw new IllegalArgumentException(
                        "timeoutSec must be > 0, got " + timeoutSec);
            }
            this.completionMode = MoveCompletionMode.WAIT_SETPOINT_OR_TIMEOUT;
            this.waitSeconds = 0.0;
            this.timeoutSec = timeoutSec;
            return this;
        }

        @Override
        public MovePost waitSeconds(final double seconds) {
            if (seconds < 0.0) {
                throw new IllegalArgumentException(
                        "seconds must be >= 0, got " + seconds);
            }
            this.completionMode = MoveCompletionMode.WAIT_TIME;
            this.waitSeconds = seconds;
            this.timeoutSec = 0.0;
            return this;
        }

        @Override
        public MoveBuildStep dontWait() {
            this.completionMode = MoveCompletionMode.INSTANT;
            this.waitSeconds = 0.0;
            this.timeoutSec = 0.0;
            this.postBehavior = PostBehavior.HOLD;
            this.postTarget = 0.0;
            return this;
        }

        @Override
        public MoveBuildStep thenHold() {
            this.postBehavior = PostBehavior.HOLD;
            this.postTarget = 0.0;
            return this;
        }

        @Override
        public MoveBuildStep thenGoTo(final double safeTarget) {
            this.postBehavior = PostBehavior.SAFE_TARGET;
            this.postTarget = safeTarget;
            return this;
        }

        @Override
        public Task build() {
            return new MoveTask(
                    plant,
                    target,
                    completionMode,
                    waitSeconds,
                    timeoutSec,
                    postBehavior,
                    postTarget
            );
        }
    }

    /**
     * Unified move task that supports both time-based and setpoint-based
     * completion modes and reports its outcome via {@link Task#getOutcome()}.
     */
    private static final class MoveTask implements Task {

        private final Plant plant;
        private final double target;
        private final MoveCompletionMode completionMode;
        private final double waitSeconds;
        private final double timeoutSec;
        private final PostBehavior postBehavior;
        private final double postTarget;

        private boolean started = false;
        private boolean finished = false;
        private boolean postApplied = false;
        private double elapsedSec = 0.0;
        private TaskOutcome outcome = TaskOutcome.NOT_DONE;

        MoveTask(final Plant plant,
                 final double target,
                 final MoveCompletionMode completionMode,
                 final double waitSeconds,
                 final double timeoutSec,
                 final PostBehavior postBehavior,
                 final double postTarget) {

            this.plant = plant;
            this.target = target;
            this.completionMode = completionMode;
            this.waitSeconds = waitSeconds;
            this.timeoutSec = timeoutSec;
            this.postBehavior = postBehavior;
            this.postTarget = postTarget;
        }

        @Override
        public void start(final LoopClock clock) {
            if (started) {
                return;
            }
            started = true;
            finished = false;
            postApplied = false;
            elapsedSec = 0.0;
            outcome = TaskOutcome.NOT_DONE;

            plant.setTarget(target);

            // Handle immediate completion cases.
            switch (completionMode) {
                case INSTANT:
                    finished = true;
                    outcome = TaskOutcome.SUCCESS;
                    applyPostIfNeeded();
                    break;

                case WAIT_TIME:
                    if (waitSeconds <= 0.0) {
                        finished = true;
                        outcome = TaskOutcome.SUCCESS;
                        applyPostIfNeeded();
                    }
                    break;

                case WAIT_SETPOINT:
                case WAIT_SETPOINT_OR_TIMEOUT:
                    if (plant.atSetpoint()) {
                        finished = true;
                        outcome = TaskOutcome.SUCCESS;
                        applyPostIfNeeded();
                    }
                    break;

                default:
                    // no-op
            }
        }

        @Override
        public void update(final LoopClock clock) {
            if (!started || finished) {
                return;
            }

            double dt = clock.dtSec();
            if (dt < 0.0) {
                dt = 0.0;
            }
            elapsedSec += dt;

            switch (completionMode) {
                case INSTANT:
                    finished = true;
                    outcome = TaskOutcome.SUCCESS;
                    break;

                case WAIT_TIME:
                    if (elapsedSec >= waitSeconds) {
                        finished = true;
                        outcome = TaskOutcome.SUCCESS;
                    }
                    break;

                case WAIT_SETPOINT:
                    if (plant.atSetpoint()) {
                        finished = true;
                        outcome = TaskOutcome.SUCCESS;
                    }
                    break;

                case WAIT_SETPOINT_OR_TIMEOUT:
                    if (plant.atSetpoint()) {
                        finished = true;
                        outcome = TaskOutcome.SUCCESS;
                    } else if (elapsedSec >= timeoutSec) {
                        finished = true;
                        outcome = TaskOutcome.TIMEOUT;
                    }
                    break;

                default:
                    // no-op
            }

            if (finished) {
                applyPostIfNeeded();
            }
        }

        @Override
        public boolean isComplete() {
            return finished;
        }

        @Override
        public TaskOutcome getOutcome() {
            return outcome;
        }

        private void applyPostIfNeeded() {
            if (postApplied) {
                return;
            }
            postApplied = true;

            if (postBehavior == PostBehavior.SAFE_TARGET) {
                plant.setTarget(postTarget);
            }
            // HOLD: do nothing; keep last target.
        }

        @Override
        public String getDebugName() {
            return "PlantMove(target=" + target + ")";
        }
    }
}

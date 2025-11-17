package edu.ftcphoenix.fw.drive;

import edu.ftcphoenix.fw.task.InstantTask;
import edu.ftcphoenix.fw.task.RunForSecondsTask;
import edu.ftcphoenix.fw.task.Task;

/**
 * Small helper class for creating common drive-related {@link Task} patterns.
 *
 * <p>The goal is to give robot code very simple, readable building blocks like:</p>
 *
 * <pre>{@code
 * // Example: simple "L-shape" macro in TeleOp
 * Task macro = SequenceTask.of(
 *     DriveTasks.driveForSeconds(drivebase,
 *         new DriveSignal(+0.7, 0.0, 0.0), 0.8),   // forward
 *     DriveTasks.driveForSeconds(drivebase,
 *         new DriveSignal(0.0, -0.7, 0.0), 0.8),   // strafe right
 *     DriveTasks.driveForSeconds(drivebase,
 *         new DriveSignal(0.0, 0.0, +0.7), 0.6)    // rotate CCW
 * );
 *
 * macroRunner.enqueue(macro);
 * }</pre>
 *
 * <p>All methods here are <b>non-blocking</b> and designed to be used with
 * {@link edu.ftcphoenix.fw.task.TaskRunner} and the rest of the {@code fw.task}
 * package.</p>
 *
 * <h2>Design principles</h2>
 * <ul>
 *   <li>Keep the API <b>beginner-friendly</b>: robot code should look like
 *       “drive for 0.8 seconds”, not “construct three callbacks and wire them
 *       into a task”.</li>
 *   <li>Keep this class <b>domain-specific</b> to drive, so it lives in
 *       {@code fw.drive} and does not pollute the generic {@code fw.task}
 *       package.</li>
 *   <li>Internally reuse {@link RunForSecondsTask} and {@link InstantTask} so
 *       that timing and control flow are centralized.</li>
 * </ul>
 */
public final class DriveTasks {

    private DriveTasks() {
        // Utility class; no instances.
    }

    // ------------------------------------------------------------------------
    // Timed drive commands
    // ------------------------------------------------------------------------

    /**
     * Create a {@link Task} that applies a fixed {@link DriveSignal} for a
     * specific amount of time, then stops the drivebase.
     *
     * <p>Semantics:</p>
     * <ul>
     *   <li>On start:
     *     <ul>
     *       <li>Calls {@link MecanumDrivebase#drive(DriveSignal)} once with
     *           {@code signal}.</li>
     *     </ul>
     *   </li>
     *   <li>On each update while running:
     *     <ul>
     *       <li>Calls {@link MecanumDrivebase#update(edu.ftcphoenix.fw.util.LoopClock)}.</li>
     *       <li>Counts down {@code durationSec} using {@code clock.dtSec()}.</li>
     *     </ul>
     *   </li>
     *   <li>When time elapses:
     *     <ul>
     *       <li>Calls {@link MecanumDrivebase#stop()} once.</li>
     *       <li>{@link Task#isFinished()} becomes {@code true}.</li>
     *     </ul>
     *   </li>
     * </ul>
     *
     * <p>This is the main primitive used in macro examples. You can combine
     * multiple {@code driveForSeconds(...)} tasks with
     * {@link edu.ftcphoenix.fw.task.SequenceTask#of(Task...)} to build
     * longer scripted paths.</p>
     *
     * @param drivebase   the drivebase to command
     * @param signal      drive command to hold (axial, lateral, omega)
     * @param durationSec duration in seconds; must be {@code >= 0}
     * @return a {@link Task} that performs the timed drive action
     */
    public static Task driveForSeconds(final MecanumDrivebase drivebase,
                                       final DriveSignal signal,
                                       final double durationSec) {
        return new RunForSecondsTask(
                durationSec,
                // onStart: latch the drive command once.
                () -> drivebase.drive(signal),
                // onUpdate: keep updating the drivebase while time is running.
                clock -> drivebase.update(clock),
                // onFinish: stop the drive and allow the next task to run.
                drivebase::stop
        );
    }

    /**
     * Convenience overload of {@link #driveForSeconds(MecanumDrivebase, DriveSignal, double)}
     * that takes the individual {@link DriveSignal} components directly.
     *
     * <p>Example:</p>
     *
     * <pre>{@code
     * // Forward for 0.8s
     * Task forward = DriveTasks.driveForSeconds(
     *     drivebase,
     *     +0.7,  // axial
     *     0.0,   // lateral
     *     0.0,   // omega
     *     0.8
     * );
     * }</pre>
     *
     * @param drivebase   the drivebase to command
     * @param axial       forward/backward command (+ is forward)
     * @param lateral     strafe command (+ is left)
     * @param omega       rotation command (+ is counterclockwise)
     * @param durationSec duration in seconds; must be {@code >= 0}
     * @return a {@link Task} that performs the timed drive action
     */
    public static Task driveForSeconds(MecanumDrivebase drivebase,
                                       double axial,
                                       double lateral,
                                       double omega,
                                       double durationSec) {
        return driveForSeconds(drivebase, new DriveSignal(axial, lateral, omega), durationSec);
    }

    // ------------------------------------------------------------------------
    // Simple helper tasks
    // ------------------------------------------------------------------------

    /**
     * Create a {@link Task} that immediately stops the drivebase and finishes.
     *
     * <p>This is just a small wrapper around {@link InstantTask} for readability
     * in robot code and examples.</p>
     *
     * <pre>{@code
     * TaskRunner runner = new TaskRunner();
     * runner.enqueue(DriveTasks.stop(drivebase));
     * }</pre>
     *
     * @param drivebase the drivebase to stop
     * @return a {@link Task} that stops the drivebase once and then finishes
     */
    public static Task stop(final MecanumDrivebase drivebase) {
        return new InstantTask(drivebase::stop);
    }
}

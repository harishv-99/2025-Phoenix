package edu.ftcphoenix.fw.sensing;

import java.util.Objects;
import java.util.Set;

import edu.ftcphoenix.fw.core.PidController;
import edu.ftcphoenix.fw.core.Pid;
import edu.ftcphoenix.fw.util.MathUtil;

/**
 * Controller that generates a rotational (omega) command to aim the robot
 * at an AprilTag.
 *
 * <p>This class is deliberately independent of drivebase and gamepad details:
 * it only knows how to:</p>
 *
 * <ul>
 *   <li>Ask an {@link AprilTagSensor} for the best tag from a set of IDs.</li>
 *   <li>Use a {@link PidController} on the tag bearing to compute an omega
 *       command.</li>
 *   <li>Apply a deadband, freshness check, and magnitude limit.</li>
 * </ul>
 *
 * <p>Higher-level code is responsible for deciding when to use this omega:
 * for example, a TeleOp {@code DriveSource} can override the driver's
 * turn input while a button is held, or an autonomous routine can apply
 * the omega continuously while approaching a goal.</p>
 *
 * <h2>Typical usage (TeleOp / auto)</h2>
 *
 * <pre>{@code
 * // TeleOp or auto init:
 * AprilTagSensor sensor = Tags.aprilTags(hardwareMap, "Webcam 1");
 * Set<Integer> scoringTags = Set.of(1, 2, 3);
 *
 * TagAimController aim = TagAimController.withDefaults(sensor, scoringTags);
 *
 * // In your loop:
 * double omega = aim.update(dtSec);
 *
 * if (aim.hasTarget()) {
 *     telemetry.addData("Aim error (deg)", aim.getErrorDeg());
 * }
 *
 * // Use omega as your rotational command (e.g. driveBase.drive(...omega...)).
 * }</pre>
 *
 * <p>Most teams will not instantiate this class directly. Instead, they will
 * use a higher-level helper such as {@code TagAim.forTeleOp(...)} that wraps
 * a base {@code DriveSource} and a {@code Button} and uses this controller
 * internally.</p>
 */
public final class TagAimController {

    private final AprilTagSensor sensor;
    private final Set<Integer> idsOfInterest;
    private final PidController pid;

    // Tuning parameters
    private double maxTagAgeSec;
    private double deadbandRad;
    private double maxAbsOmega;

    // State for telemetry / inspection
    private boolean lastHasTarget = false;
    private double lastErrorRad = 0.0;

    /**
     * Construct a TagAimController with explicit configuration and PID.
     *
     * <p>Most teams should use {@link #withDefaults(AprilTagSensor, Set)}
     * instead, which provides reasonable defaults for FTC robots.</p>
     *
     * @param sensor        AprilTag sensor to query
     * @param idsOfInterest set of tag IDs to aim at
     * @param pid           PID controller for bearing error
     * @param maxTagAgeSec  maximum acceptable tag age in seconds
     * @param deadbandRad   deadband around zero bearing (radians) where
     *                      omega will be zero
     * @param maxAbsOmega   maximum absolute omega command (normalized turn)
     */
    public TagAimController(AprilTagSensor sensor,
                            Set<Integer> idsOfInterest,
                            PidController pid,
                            double maxTagAgeSec,
                            double deadbandRad,
                            double maxAbsOmega) {
        this.sensor = Objects.requireNonNull(sensor, "sensor");
        this.idsOfInterest = Objects.requireNonNull(idsOfInterest, "idsOfInterest");
        this.pid = Objects.requireNonNull(pid, "pid");
        this.maxTagAgeSec = maxTagAgeSec;
        this.deadbandRad = deadbandRad;
        this.maxAbsOmega = Math.abs(maxAbsOmega);
    }

    /**
     * Construct a TagAimController with framework-default tuning.
     *
     * <p>Defaults are chosen to be reasonable starting points for FTC robots:</p>
     *
     * <ul>
     *   <li>kP = 1.5, kI = 0.0, kD = 0.2 (bearing in radians &rarr; omega).</li>
     *   <li>Maximum tag age = 0.3 s.</li>
     *   <li>Deadband = 2 degrees (in radians).</li>
     *   <li>Maximum |omega| = 0.7 (normalized turn command).</li>
     * </ul>
     *
     * @param sensor        AprilTag sensor to query
     * @param idsOfInterest set of tag IDs to aim at
     * @return a controller with default tuning
     */
    public static TagAimController withDefaults(AprilTagSensor sensor,
                                                Set<Integer> idsOfInterest) {
        Pid pid = Pid.withGains(1.5, 0.0, 0.2);
        double maxTagAgeSec = 0.3;
        double deadbandRad = Math.toRadians(2.0);
        double maxAbsOmega = 0.7;
        return new TagAimController(sensor, idsOfInterest, pid,
                maxTagAgeSec, deadbandRad, maxAbsOmega);
    }

    /**
     * Update the controller and compute a new omega command.
     *
     * <p>This method should typically be called once per loop, using the same
     * {@code dtSec} that you pass to your other controllers.</p>
     *
     * <p>Behavior:</p>
     *
     * <ul>
     *   <li>If no suitable tag is visible (ID not in the set or too old),
     *       this returns 0 and {@link #hasTarget()} will be {@code false}.</li>
     *   <li>If a tag is visible but its bearing is within {@link #getDeadbandRad()},
     *       this returns 0 to avoid small oscillations.</li>
     *   <li>Otherwise, the bearing (radians) is passed as the error into the
     *       underlying {@link PidController}, and its output is clamped to
     *       [-{@link #getMaxAbsOmega()}, +{@link #getMaxAbsOmega()}].</li>
     * </ul>
     *
     * @param dtSec loop time step in seconds
     * @return rotational command (omega), typically interpreted as a
     * normalized turn input
     */
    public double update(double dtSec) {
        AprilTagObservation obs = sensor.best(idsOfInterest, maxTagAgeSec);

        if (!obs.hasTarget) {
            lastHasTarget = false;
            lastErrorRad = 0.0;
            pid.reset(); // clear integral/derivative state when we lose the tag
            return 0.0;
        }

        lastHasTarget = true;
        lastErrorRad = obs.bearingRad;

        // Apply deadband around zero to avoid micro-oscillations.
        double error = MathUtil.deadband(lastErrorRad, deadbandRad);
        if (error == 0.0) {
            // Aligned within deadband: no turn command needed.
            return 0.0;
        }

        double rawOmega = pid.update(error, dtSec);

        // Clamp omega magnitude for driver comfort / stability.
        return MathUtil.clampAbs(rawOmega, maxAbsOmega);
    }

    // ---------------------------------------------------------------------
    // Tuning accessors (optional)
    // ---------------------------------------------------------------------

    /**
     * @return maximum acceptable tag age in seconds
     */
    public double getMaxTagAgeSec() {
        return maxTagAgeSec;
    }

    /**
     * Set the maximum acceptable tag age in seconds.
     *
     * @param maxTagAgeSec maximum age; values &lt;= 0 will effectively require
     *                     tags to be from the most recent frame
     * @return this controller, for chaining
     */
    public TagAimController setMaxTagAgeSec(double maxTagAgeSec) {
        this.maxTagAgeSec = maxTagAgeSec;
        return this;
    }

    /**
     * @return deadband radius around zero bearing, in radians
     */
    public double getDeadbandRad() {
        return deadbandRad;
    }

    /**
     * Set the deadband radius around zero bearing, in radians.
     *
     * @param deadbandRad deadband radius; negative values are treated as
     *                    their absolute value
     * @return this controller, for chaining
     */
    public TagAimController setDeadbandRad(double deadbandRad) {
        this.deadbandRad = Math.abs(deadbandRad);
        return this;
    }

    /**
     * @return maximum absolute omega command (normalized)
     */
    public double getMaxAbsOmega() {
        return maxAbsOmega;
    }

    /**
     * Set the maximum absolute omega command (normalized).
     *
     * @param maxAbsOmega maximum magnitude; negative values are treated as
     *                    their absolute value
     * @return this controller, for chaining
     */
    public TagAimController setMaxAbsOmega(double maxAbsOmega) {
        this.maxAbsOmega = Math.abs(maxAbsOmega);
        return this;
    }

    // ---------------------------------------------------------------------
    // Telemetry helpers
    // ---------------------------------------------------------------------

    /**
     * @return whether the last call to {@link #update(double)} used a valid
     * tag observation
     */
    public boolean hasTarget() {
        return lastHasTarget;
    }

    /**
     * @return last bearing error (radians) seen by the controller; 0 if
     * no target was available
     */
    public double getErrorRad() {
        return lastErrorRad;
    }

    /**
     * @return last bearing error (degrees) seen by the controller; 0 if
     * no target was available
     */
    public double getErrorDeg() {
        return Math.toDegrees(lastErrorRad);
    }
}

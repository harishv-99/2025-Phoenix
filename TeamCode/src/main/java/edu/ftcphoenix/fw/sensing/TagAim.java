package edu.ftcphoenix.fw.sensing;

import java.util.Set;

import edu.ftcphoenix.fw.core.PidController;
import edu.ftcphoenix.fw.drive.DriveSignal;
import edu.ftcphoenix.fw.drive.DriveSource;
import edu.ftcphoenix.fw.input.Button;
import edu.ftcphoenix.fw.sensing.BearingSource.BearingSample;
import edu.ftcphoenix.fw.util.LoopClock;

/**
 * Helpers for tag-based auto-aim that wrap an existing {@link DriveSource}.
 *
 * <h2>Role</h2>
 * <p>{@code TagAim} is a convenience facade that lets you say:
 *
 * <pre>{@code
 * DriveSource sticks = StickDriveSource.defaultMecanumWithSlowMode(...);
 * DriveSource drive  = TagAim.forTeleOp(
 *         sticks,
 *         driverKit.p1().leftBumper(),  // hold to aim
 *         tags,                         // AprilTagSensor
 *         Set.of(1, 2, 3));             // IDs we care about
 * }</pre>
 *
 * <p>Under the hood it:
 * <ul>
 *   <li>Builds a {@link BearingSource} from an {@link AprilTagSensor}.</li>
 *   <li>Uses a {@link TagAimController} to turn bearing into omega.</li>
 *   <li>Wraps the original {@link DriveSource} so that when the aim
 *       button is held, {@code omega} is overridden to aim at the tag,
 *       while axial/lateral commands come from the driver sticks.</li>
 * </ul>
 *
 * <p>Only the robot's <em>turn rate</em> (omega) is modified; this keeps
 * behavior simple and predictable: drivers still choose how fast to
 * drive forward/sideways while the framework auto-aims the heading.
 *
 * <h2>Beginner vs advanced</h2>
 * <ul>
 *   <li><b>Beginner:</b> call
 *   {@link #forTeleOp(DriveSource, Button, AprilTagSensor, Set)} and
 *   ignore the controller details.</li>
 *   <li><b>Advanced:</b> build your own {@link BearingSource} and
 *   {@link TagAimController}, then call
 *   {@link #forTeleOp(DriveSource, Button, BearingSource, TagAimController)}.</li>
 * </ul>
 */
public final class TagAim {

    /**
     * Default maximum age (seconds) for a tag observation in TeleOp.
     */
    private static final double DEFAULT_MAX_AGE_SEC = 0.30;

    /**
     * Default deadband for aiming in degrees (converted to radians internally).
     */
    private static final double DEFAULT_DEADBAND_DEG = 1.0;

    /**
     * Default maximum turn rate command for TeleOp aiming.
     */
    private static final double DEFAULT_MAX_OMEGA = 0.8;

    /**
     * Default proportional gain for simple P-only TeleOp aiming.
     */
    private static final double DEFAULT_KP = 0.02;

    private TagAim() {
        // utility class
    }

    // ------------------------------------------------------------------------
    // Beginner API: AprilTags directly
    // ------------------------------------------------------------------------

    /**
     * Wrap an existing drive source with AprilTag-based auto-aim for TeleOp.
     *
     * <p>This is the simplest entry point:
     * <ul>
     *   <li>The driver uses sticks as usual for axial/lateral motion.</li>
     *   <li>When {@code aimButton} is held:
     *     <ul>
     *       <li>The robot turns to face the best visible tag from {@code tagIds}.</li>
     *       <li>{@code omega} is computed by an internal {@link TagAimController}
     *           using a simple proportional control law.</li>
     *     </ul>
     *   </li>
     * </ul>
     *
     * @param baseDrive existing drive source (e.g., stick mapping)
     * @param aimButton button that enables aiming while pressed
     * @param sensor    AprilTag sensor providing detections
     * @param tagIds    set of tag IDs the robot cares about (e.g., scoring tags)
     * @return a new {@link DriveSource} that adds aiming behavior on top of {@code baseDrive}
     */
    public static DriveSource forTeleOp(
            DriveSource baseDrive,
            Button aimButton,
            AprilTagSensor sensor,
            Set<Integer> tagIds) {

        BearingSource bearing = bearingFromAprilTags(sensor, tagIds, DEFAULT_MAX_AGE_SEC);
        TagAimController controller = defaultTeleOpController();
        return forTeleOp(baseDrive, aimButton, bearing, controller);
    }

    // ------------------------------------------------------------------------
    // Advanced API: generic bearing source + controller
    // ------------------------------------------------------------------------

    /**
     * Wrap an existing drive source with generic bearing-based auto-aim for TeleOp.
     *
     * <p>This overload lets advanced users customize:
     * <ul>
     *   <li>Where bearing information comes from ({@link BearingSource}).</li>
     *   <li>How bearing is converted into omega ({@link TagAimController}).</li>
     * </ul>
     *
     * <p>Behavior:
     * <ul>
     *   <li>If {@code aimButton} is <b>not</b> pressed, this returns the
     *       {@link DriveSignal} from {@code baseDrive} unchanged.</li>
     *   <li>If {@code aimButton} <b>is</b> pressed:
     *     <ul>
     *       <li>Call {@link BearingSource#sample(LoopClock)} to get bearing.</li>
     *       <li>Call {@link TagAimController#update(LoopClock, BearingSample)} to get omega.</li>
     *       <li>Return a {@link DriveSignal} with axial/lateral from {@code baseDrive}
     *           but omega from the controller.</li>
     *     </ul>
     *   </li>
     * </ul>
     *
     * @param baseDrive  existing drive source (e.g., sticks, path planner, etc.)
     * @param aimButton  button that enables aiming while pressed
     * @param bearing    bearing source (AprilTags or otherwise)
     * @param controller controller that turns bearing into omega
     * @return a new {@link DriveSource} that adds aiming behavior on top of {@code baseDrive}
     */
    public static DriveSource forTeleOp(
            DriveSource baseDrive,
            Button aimButton,
            BearingSource bearing,
            TagAimController controller) {

        return new DriveSource() {
            @Override
            public DriveSignal get(LoopClock clock) {
                DriveSignal base = baseDrive.get(clock);

                if (!aimButton.isPressed()) {
                    // No aiming; pass through base drive command.
                    return base;
                }

                BearingSample sample = bearing.sample(clock);
                double omega = controller.update(clock, sample);

                // Keep axial/lateral from driver sticks, override omega to aim at target.
                return new DriveSignal(base.axial, base.lateral, omega);
            }
        };
    }

    // ------------------------------------------------------------------------
    // Internal helpers
    // ------------------------------------------------------------------------

    /**
     * Build a {@link BearingSource} from an {@link AprilTagSensor}.
     *
     * <p>This helper:
     * <ul>
     *   <li>Uses {@link AprilTagSensor#best(Set, double)} to pick the best tag from
     *       {@code tagIds} whose observation is younger than {@code maxAgeSec}.</li>
     *   <li>Converts that into a {@link BearingSample} for aiming.</li>
     * </ul>
     *
     * <p>If no suitable tag is found, it returns a sample with
     * {@code hasTarget = false}.
     *
     * @param sensor    AprilTag sensor
     * @param tagIds    set of tag IDs the robot cares about
     * @param maxAgeSec maximum allowed observation age in seconds
     * @return a bearing source backed by {@code sensor}
     */
    private static BearingSource bearingFromAprilTags(
            AprilTagSensor sensor,
            Set<Integer> tagIds,
            double maxAgeSec) {

        return clock -> {
            AprilTagObservation obs = sensor.best(tagIds, maxAgeSec);
            if (!obs.hasTarget) {
                return new BearingSample(false, 0.0);
            }
            return new BearingSample(true, obs.bearingRad);
        };
    }

    /**
     * Construct a reasonable default {@link TagAimController} for TeleOp use.
     *
     * <p>This uses:
     * <ul>
     *   <li>A simple proportional-only PID-style implementation with gain {@link #DEFAULT_KP}.</li>
     *   <li>A deadband of {@link #DEFAULT_DEADBAND_DEG} degrees around zero.</li>
     *   <li>Maximum omega of {@link #DEFAULT_MAX_OMEGA}.</li>
     *   <li>{@link TagAimController.LossPolicy#ZERO_OUTPUT_RESET_I} for target loss.</li>
     * </ul>
     *
     * <p>Teams can always provide their own PID and controller if they want more
     * precise tuning via the advanced API.
     */
    private static TagAimController defaultTeleOpController() {
        // Simple proportional controller to keep dependencies light.
        PidController pid = new PidController() {
            @Override
            public double update(double error, double dtSec) {
                // P-only: omega = Kp * error
                return DEFAULT_KP * error;
            }
            // Default reset() is fine (no internal state).
        };

        double deadbandRad = Math.toRadians(DEFAULT_DEADBAND_DEG);

        return new TagAimController(
                pid,
                deadbandRad,
                DEFAULT_MAX_OMEGA,
                TagAimController.LossPolicy.ZERO_OUTPUT_RESET_I
        );
    }
}

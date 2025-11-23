package edu.ftcphoenix.fw.sensing;

import java.util.Objects;
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
 * <p>{@code TagAim} is a convenience facade that lets you say:</p>
 *
 * <pre>{@code
 * DriveSource manual = StickDriveSource.teleOpMecanum(driverKit);
 * DriveSource drive  = TagAim.teleOpAim(
 *         manual,
 *         driverKit.p1().leftBumper(),
 *         tagSensor,
 *         scoringTagIds);
 * }</pre>
 *
 * <p>and get all of the following behavior:</p>
 *
 * <ul>
 *   <li>The driver still controls axial/lateral motion with sticks.</li>
 *   <li>When {@code aimButton} is held and a valid tag is visible:
 *     <ul>
 *       <li>The robot automatically turns (omega) to aim at that tag.</li>
 *       <li>Axial/lateral commands from the driver are preserved.</li>
 *     </ul>
 *   </li>
 *   <li>When the tag is not visible or the button is not held:
 *     <ul>
 *       <li>The underlying {@code baseDrive} behavior is passed through unchanged.</li>
 *     </ul>
 *   </li>
 * </ul>
 *
 * <h2>Beginner vs advanced</h2>
 * <ul>
 *   <li><b>Beginner:</b> call
 *   {@link #teleOpAim(DriveSource, Button, AprilTagSensor, Set)} and
 *   ignore the controller details.</li>
 *   <li><b>Advanced:</b> build your own {@link BearingSource} and
 *   {@link TagAimController}, then call
 *   {@link #teleOpAim(DriveSource, Button, BearingSource, TagAimController)}.</li>
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
     * <p>This is the simplest entry point:</p>
     * <ul>
     *   <li>The driver uses sticks as usual for axial/lateral motion.</li>
     *   <li>When {@code aimButton} is held:
     *     <ul>
     *       <li>We find the \"best\" AprilTag from {@code tagIds} using
     *           {@link AprilTagSensor#best(Set, double)} with a default
     *           age limit ({@value #DEFAULT_MAX_AGE_SEC} seconds).</li>
     *       <li>We compute a bearing to that tag and feed it into a default
     *           P-only {@link TagAimController}.</li>
     *       <li>We override the {@code omega} component of {@code baseDrive}
     *           while preserving axial and lateral commands.</li>
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
    public static DriveSource teleOpAim(
            DriveSource baseDrive,
            Button aimButton,
            AprilTagSensor sensor,
            Set<Integer> tagIds) {

        Objects.requireNonNull(baseDrive, "baseDrive must not be null");
        Objects.requireNonNull(aimButton, "aimButton must not be null");
        Objects.requireNonNull(sensor, "sensor must not be null");
        Objects.requireNonNull(tagIds, "tagIds must not be null");

        BearingSource bearing = bearingFromAprilTags(sensor, tagIds, DEFAULT_MAX_AGE_SEC);
        TagAimController controller = defaultTeleOpController();
        return teleOpAim(baseDrive, aimButton, bearing, controller);
    }

    // ------------------------------------------------------------------------
    // Advanced API: generic bearing source + controller
    // ------------------------------------------------------------------------

    /**
     * Wrap an existing drive source with generic bearing-based auto-aim for TeleOp.
     *
     * <p>This overload lets advanced users customize:</p>
     * <ul>
     *   <li>Where bearing information comes from ({@link BearingSource}).</li>
     *   <li>How bearing is converted into omega ({@link TagAimController}).</li>
     * </ul>
     *
     * <p>Behavior is the same as
     * {@link #teleOpAim(DriveSource, Button, AprilTagSensor, Set)}:
     * we preserve the driver's axial/lateral commands and only override
     * {@code omega} while {@code aimButton} is pressed.</p>
     *
     * @param baseDrive  existing drive source (e.g., sticks, path planner, etc.)
     * @param aimButton  button that enables aiming while pressed
     * @param bearing    bearing source (AprilTags or otherwise)
     * @param controller controller that turns bearing into omega
     * @return a new {@link DriveSource} that adds aiming behavior on top of {@code baseDrive}
     */
    public static DriveSource teleOpAim(
            DriveSource baseDrive,
            Button aimButton,
            BearingSource bearing,
            TagAimController controller) {

        Objects.requireNonNull(baseDrive, "baseDrive must not be null");
        Objects.requireNonNull(aimButton, "aimButton must not be null");
        Objects.requireNonNull(bearing, "bearing must not be null");
        Objects.requireNonNull(controller, "controller must not be null");

        // We implement this in-place instead of delegating to a separate
        // class to keep the beginner surface small. Advanced users can still
        // use TagAimController and BearingSource directly if they wish.
        return new DriveSource() {
            @Override
            public DriveSignal get(LoopClock clock) {
                // Start with the underlying drive behavior
                DriveSignal base = baseDrive.get(clock);

                if (!aimButton.isPressed()) {
                    // No aiming; pass through base drive command.
                    return base;
                }

                BearingSample sample = bearing.sample(clock);
                double omega = controller.update(clock, sample);

                // Keep axial/lateral from driver sticks, override omega to aim at target.
                return base.withOmega(omega);
            }
        };
    }

    // ------------------------------------------------------------------------
    // Internal helpers
    // ------------------------------------------------------------------------

    /**
     * Build a {@link BearingSource} that derives bearing information from
     * AprilTag observations returned by {@link AprilTagSensor}.
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
     * Construct a default controller for TeleOp aiming.
     *
     * <p>This uses a simple P-only controller with gain
     * {@value #DEFAULT_KP}, a deadband of
     * {@value #DEFAULT_DEADBAND_DEG} degrees around zero, and a maximum
     * turn rate of {@value #DEFAULT_MAX_OMEGA}.</p>
     */
    private static TagAimController defaultTeleOpController() {
        // Simple P-only controller: omega = Kp * error
        PidController pid = new PidController() {
            @Override
            public double update(double error, double dtSec) {
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

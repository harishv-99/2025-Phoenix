package edu.ftcphoenix.fw.sensing;

import java.util.Set;

import edu.ftcphoenix.fw.core.PidController;
import edu.ftcphoenix.fw.drive.DriveSource;
import edu.ftcphoenix.fw.drive.source.TagAimDriveSource;
import edu.ftcphoenix.fw.input.Button;

/**
 * Helpers for tag-based auto-aim that wrap an existing {@link DriveSource}.
 *
 * <h2>Role</h2>
 * <p>{@code TagAim} is a convenience facade that lets you say:</p>
 *
 * <pre>{@code
 * edu.ftcphoenix.fw.input.Gamepads pads =
 *         edu.ftcphoenix.fw.input.Gamepads.create(gamepad1, gamepad2);
 *
 * // Driver sticks for mecanum with slow mode on right bumper.
 * edu.ftcphoenix.fw.drive.DriveSource sticks =
 *         edu.ftcphoenix.fw.drive.source.GamepadDriveSource
 *                 .teleOpMecanumWithSlowMode(
 *                         pads,
 *                         pads.p1().rightBumper(), // hold for slow mode
 *                         0.30                     // 30% speed
 *                 );
 *
 * // Wrap with TagAim: hold left bumper to auto-aim at scoring tags.
 * edu.ftcphoenix.fw.drive.DriveSource drive =
 *         edu.ftcphoenix.fw.sensing.TagAim.teleOpAim(
 *                 sticks,
 *                 pads.p1().leftBumper(), // hold to aim
 *                 tagsSensor,             // AprilTagSensor
 *                 java.util.Set.of(1, 2, 3) // IDs we care about
 *         );
 * }</pre>
 *
 * <p>Under the hood it:</p>
 * <ul>
 *   <li>Builds a {@link BearingSource} from an {@link AprilTagSensor}.</li>
 *   <li>Uses a {@link TagAimController} to turn bearing into omega.</li>
 *   <li>Preserves the original {@link DriveSource} so that when the aim
 *       button is held, {@code omega} is overridden to aim at the tag,
 *       but axial/lateral still come from the driver.</li>
 * </ul>
 *
 * <h2>Beginner vs advanced</h2>
 * <ul>
 *   <li><b>Beginner:</b> call
 *       {@link #teleOpAim(DriveSource, Button, AprilTagSensor, Set)} and
 *       ignore the controller details.</li>
 *   <li><b>Advanced:</b> build your own {@link BearingSource} and
 *       {@link TagAimController}, then call
 *       {@link #teleOpAim(DriveSource, Button, BearingSource, TagAimController)}.</li>
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
    private static final double DEFAULT_KP = 4.0;

    private TagAim() {
        // Utility class; no instances.
    }

    // ------------------------------------------------------------------------
    // Beginner TeleOp API: AprilTagSensor + tag IDs
    // ------------------------------------------------------------------------

    /**
     * TeleOp helper: wrap an existing drive source with tag-based auto-aim,
     * using an {@link AprilTagSensor} and a set of tag IDs.
     *
     * <p>This overload is intended for most teams. It:</p>
     *
     * <ul>
     *   <li>Builds a {@link BearingSource} from {@link AprilTagSensor#best(Set, double)}.</li>
     *   <li>Uses a reasonable default {@link TagAimController}:
     *     <ul>
     *       <li>P-only control with gain {@link #DEFAULT_KP}.</li>
     *       <li>Deadband of {@link #DEFAULT_DEADBAND_DEG} degrees.</li>
     *       <li>Maximum omega of {@link #DEFAULT_MAX_OMEGA}.</li>
     *       <li>{@link TagAimController.LossPolicy#ZERO_OUTPUT_RESET_I}.</li>
     *     </ul>
     *   </li>
     *   <li>Returns a {@link DriveSource} that:
     *     <ul>
     *       <li>Forwards the base drive command when {@code aimButton} is not pressed.</li>
     *       <li>Overrides only omega while {@code aimButton} is pressed.</li>
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

        BearingSource bearing = bearingFromAprilTags(sensor, tagIds, DEFAULT_MAX_AGE_SEC);
        TagAimController controller = defaultTeleOpController();
        return teleOpAim(baseDrive, aimButton, bearing, controller);
    }

    // ------------------------------------------------------------------------
    // Advanced API: generic bearing source + controller
    // ------------------------------------------------------------------------

    /**
     * TeleOp helper: wrap an existing drive source with generic bearing-based
     * auto-aim.
     *
     * <p>This overload lets advanced users customize:</p>
     * <ul>
     *   <li>Where bearing information comes from ({@link BearingSource}).</li>
     *   <li>How bearing is converted into omega ({@link TagAimController}).</li>
     * </ul>
     *
     * <p>Behavior:</p>
     * <ul>
     *   <li>On each call to {@link DriveSource#get(edu.ftcphoenix.fw.util.LoopClock)}:
     *     <ul>
     *       <li>Compute the base command from {@code baseDrive}.</li>
     *       <li>If {@code aimButton} is not pressed, return base unchanged.</li>
     *       <li>Otherwise:
     *         <ul>
     *           <li>Sample bearing via {@link BearingSource#sample(edu.ftcphoenix.fw.util.LoopClock)}.</li>
     *           <li>Call {@link TagAimController#update(edu.ftcphoenix.fw.util.LoopClock, edu.ftcphoenix.fw.sensing.BearingSource.BearingSample)} to get omega.</li>
     *           <li>Return a new command that keeps base axial/lateral but uses the aiming omega.</li>
     *         </ul>
     *       </li>
     *     </ul>
     *   </li>
     * </ul>
     *
     * @param baseDrive existing drive source (e.g., sticks, planner)
     * @param aimButton button that enables aiming while pressed
     * @param bearing   bearing source (target angle) used for aiming
     * @param controller controller that turns bearing into omega
     * @return a {@link DriveSource} that adds aiming behavior on top of {@code baseDrive}
     */
    public static DriveSource teleOpAim(
            DriveSource baseDrive,
            Button aimButton,
            BearingSource bearing,
            TagAimController controller) {

        // Delegate to the reusable TagAimDriveSource so we don't duplicate logic
        // and we get debugDump() support for free.
        return new TagAimDriveSource(baseDrive, aimButton, bearing, controller);
    }

    // ------------------------------------------------------------------------
    // Internal helpers
    // ------------------------------------------------------------------------

    /**
     * Build a {@link BearingSource} from an {@link AprilTagSensor}.
     *
     * <p>This helper:</p>
     * <ul>
     *   <li>Uses {@link AprilTagSensor#best(Set, double)} to choose the "best"
     *       observation from the set of desired tags.</li>
     *   <li>Converts that into a {@link BearingSource.BearingSample} for aiming.</li>
     * </ul>
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
                return new BearingSource.BearingSample(false, 0.0);
            }
            return new BearingSource.BearingSample(true, obs.bearingRad);
        };
    }

    /**
     * Construct a reasonable default {@link TagAimController} for TeleOp use.
     *
     * <p>This uses:</p>
     * <ul>
     *   <li>A simple proportional-only PID-style implementation with gain {@link #DEFAULT_KP}.</li>
     *   <li>A deadband of {@link #DEFAULT_DEADBAND_DEG} degrees around zero.</li>
     *   <li>Maximum omega of {@link #DEFAULT_MAX_OMEGA}.</li>
     *   <li>{@link TagAimController.LossPolicy#ZERO_OUTPUT_RESET_I} for target loss.</li>
     * </ul>
     *
     * <p>Teams can always provide their own PID and controller if they want more
     * sophisticated behavior; this is just a good starting point.</p>
     *
     * @return a default-configured {@link TagAimController} for TeleOp
     */
    private static TagAimController defaultTeleOpController() {
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

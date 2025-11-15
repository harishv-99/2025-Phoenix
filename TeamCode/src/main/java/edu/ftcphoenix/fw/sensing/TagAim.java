package edu.ftcphoenix.fw.sensing;

import java.util.Objects;
import java.util.Set;

import edu.ftcphoenix.fw.drive.DriveSignal;
import edu.ftcphoenix.fw.drive.DriveSource;
import edu.ftcphoenix.fw.drive.source.TagAimDriveSource;
import edu.ftcphoenix.fw.input.Button;

/**
 * High-level helpers for AprilTag-based auto-aim behavior.
 *
 * <p>This class provides a small number of static factory methods that
 * connect:</p>
 *
 * <ul>
 *   <li>Human input (a button).</li>
 *   <li>A base {@link DriveSource} (for example, stick-based driving).</li>
 *   <li>An {@link AprilTagSensor} (ID, distance, bearing).</li>
 *   <li>A {@link TagAimController} that turns tag bearing into an omega
 *       (turn) command.</li>
 * </ul>
 *
 * <p>The intent is to give students a single, easy-to-discover entry point
 * for adding "hold a button to aim at a tag" to an existing drive setup.</p>
 *
 * <h2>Beginner TeleOp usage</h2>
 *
 * <pre>{@code
 * public final class TeleOpMecanumWithTagAim extends PhoenixTeleOpBase {
 *     private MecanumDrivebase drivebase;
 *     private DriveSource drive;
 *     private AprilTagSensor tags;
 *
 *     @Override
 *     protected void onInitRobot() {
 *         drivebase = Drives
 *                 .mecanum(hardwareMap)
 *                 .names("fl", "fr", "bl", "br")
 *                 .build();
 *
 *         // Create an AprilTag sensor for a webcam named "Webcam 1".
 *         tags = Tags.aprilTags(hardwareMap, "Webcam 1");
 *
 *         // Base drive from sticks.
 *         StickDriveSource sticks =
 *                 new StickDriveSource(p1(), new StickDriveSource.Params());
 *
 *         // While left bumper is held, auto-aim at tags 1, 2, or 3.
 *         drive = TagAim.forTeleOp(
 *                 sticks,
 *                 p1().leftBumper(),
 *                 tags,
 *                 Set.of(1, 2, 3)); // IDs of interest
 *     }
 *
 *     @Override
 *     protected void loopRobot(double dtSec) {
 *         drivebase.drive(drive.get(clock()));
 *     }
 * }
 * }</pre>
 *
 * <p>In this pattern, students do not need to know about {@link TagAimController}
 * or the PID internals. They only choose:</p>
 *
 * <ul>
 *   <li>Which button enables aiming.</li>
 *   <li>Which tags (IDs) they want to aim at.</li>
 * </ul>
 *
 * <h2>Advanced usage</h2>
 *
 * <p>Teams that want to customize tuning (gains, deadband, max omega, etc.)
 * can use {@link #controllerWithDefaults(AprilTagSensor, Set)} as a starting
 * point, or instantiate {@link TagAimController} directly.</p>
 */
public final class TagAim {

    private TagAim() {
        // utility holder; not instantiable
    }

    /**
     * Create a {@link DriveSource} that adds AprilTag-based auto-aim behavior
     * on top of a base {@link DriveSource} for TeleOp.
     *
     * <p>Behavior:</p>
     *
     * <ul>
     *   <li>Each loop, the base drive source is asked for a {@link DriveSignal}
     *       (for example, from gamepad sticks).</li>
     *   <li>While {@code aimButton} is pressed:
     *     <ul>
     *       <li>The robot queries {@code sensor} for the "best" tag whose ID
     *           is in {@code idsOfInterest} and not older than the controller's
     *           freshness limit.</li>
     *       <li>If such a tag exists, {@link TagAimController} computes an
     *           omega command based on bearing error, and that omega replaces
     *           the base signal's omega.</li>
     *       <li>If no suitable tag exists, the base signal is used unchanged.</li>
     *     </ul>
     *   </li>
     *   <li>When {@code aimButton} is not pressed, the base signal is passed
     *       through unchanged.</li>
     * </ul>
     *
     * <p>The tagging policy (which tag is "best") and the PID tuning are
     * handled by {@link TagAimController#withDefaults(AprilTagSensor, Set)},
     * so most teams do not need to configure them manually.</p>
     *
     * @param base          base drive source (e.g., stick-based driving);
     *                      must not be {@code null}
     * @param aimButton     button that enables auto-aim while pressed;
     *                      must not be {@code null}
     * @param sensor        AprilTag sensor; must not be {@code null}
     * @param idsOfInterest tag IDs to aim at; must not be {@code null}
     * @return a new {@link DriveSource} that wraps {@code base} with
     * auto-aim behavior
     */
    public static DriveSource forTeleOp(DriveSource base,
                                        Button aimButton,
                                        AprilTagSensor sensor,
                                        Set<Integer> idsOfInterest) {
        Objects.requireNonNull(base, "base");
        Objects.requireNonNull(aimButton, "aimButton");
        Objects.requireNonNull(sensor, "sensor");
        Objects.requireNonNull(idsOfInterest, "idsOfInterest");

        TagAimController controller =
                TagAimController.withDefaults(sensor, idsOfInterest);

        return new TagAimDriveSource(base, aimButton, controller);
    }

    /**
     * Convenience helper for creating a {@link TagAimController} with the
     * framework's default tuning.
     *
     * <p>This is useful in autonomous or advanced TeleOp code where you want
     * to handle how omega is applied yourself but still reuse the default
     * aiming logic.</p>
     *
     * <pre>{@code
     * // Auto example:
     * AprilTagSensor sensor = Tags.aprilTags(hardwareMap, "Webcam 1");
     * Set<Integer> scoringTags = Set.of(1, 2, 3);
     *
     * TagAimController aim = TagAim.controllerWithDefaults(sensor, scoringTags);
     *
     * @Override
     * protected void loopAuto(double dtSec) {
     *     double omega = aim.update(dtSec);
     *     DriveSignal signal = someAutoPathController.getSignal(dtSec)
     *             .withOmega(omega);
     *     drivebase.drive(signal);
     * }
     * }</pre>
     *
     * @param sensor        AprilTag sensor; must not be {@code null}
     * @param idsOfInterest tag IDs to aim at; must not be {@code null}
     * @return a {@link TagAimController} with default gains and thresholds
     */
    public static TagAimController controllerWithDefaults(AprilTagSensor sensor,
                                                          Set<Integer> idsOfInterest) {
        Objects.requireNonNull(sensor, "sensor");
        Objects.requireNonNull(idsOfInterest, "idsOfInterest");
        return TagAimController.withDefaults(sensor, idsOfInterest);
    }
}

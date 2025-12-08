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
 * <p>{@code TagAim} is a convenience facade that wires together:</p>
 *
 * <ul>
 *   <li>an {@link AprilTagSensor} that sees tags in the camera image,</li>
 *   <li>a {@link BearingSource} that exposes the current target bearing,</li>
 *   <li>a {@link TagAimController} that turns bearing error into an omega command, and</li>
 *   <li>a {@link TagAimDriveSource} that overrides only the turn (omega) portion of an
 *       existing {@link DriveSource} while an aim button is held.</li>
 * </ul>
 *
 * <p>Typical TeleOp usage:</p>
 *
 * <pre>{@code
 * Gamepads pads = Gamepads.create(gamepad1, gamepad2);
 *
 * // Driver sticks for mecanum with slow mode on right bumper.
 * DriveSource sticks = GamepadDriveSource.teleOpMecanumWithSlowMode(
 *         pads,
 *         pads.p1().rightBumper(),  // hold for slow mode
 *         0.30                     // 30% speed
 * );
 *
 * // Wrap with TagAim: hold left bumper to auto-aim at scoring tags.
 * DriveSource drive = TagAim.teleOpAim(
 *         sticks,
 *         pads.p1().leftBumper(),   // hold to aim
 *         tagsSensor,               // AprilTagSensor
 *         scoringTagIds             // Set<Integer> of tag IDs we care about
 * );
 * }</pre>
 *
 * <p>Under the hood it:</p>
 * <ul>
 *   <li>builds a {@link BearingSource} from an {@link AprilTagSensor},</li>
 *   <li>uses a {@link TagAimController} to turn bearing into omega, and</li>
 *   <li>preserves the original {@link DriveSource} so that when the aim button is held
 *       {@code omega} is overridden to aim at the tag, but axial/lateral still come
 *       from the driver.</li>
 * </ul>
 *
 * <h2>Usage levels</h2>
 * <ul>
 *   <li><b>Beginner:</b> call
 *       {@link #teleOpAim(DriveSource, Button, AprilTagSensor, Set)} and ignore the
 *       controller details.</li>
 *   <li><b>Tunable:</b> use
 *       {@link #teleOpAim(DriveSource, Button, AprilTagSensor, Set, Config)} to tweak
 *       gain, deadband, max omega, or max tag age without re-wiring the pipeline.</li>
 *   <li><b>Advanced:</b> build your own {@link BearingSource} and
 *       {@link TagAimController}, then call
 *       {@link #teleOpAim(DriveSource, Button, BearingSource, TagAimController)}.
 *       You can also use {@link #controllerFromConfig(Config)} directly in
 *       autonomous code.</li>
 * </ul>
 */
public final class TagAim {

    /**
     * Default maximum age (seconds) for a tag observation.
     */
    private static final double DEFAULT_MAX_AGE_SEC = 0.30;

    /**
     * Default deadband for aiming in degrees (converted to radians internally).
     */
    private static final double DEFAULT_DEADBAND_DEG = 1.0;

    /**
     * Default maximum turn rate command for aiming.
     */
    private static final double DEFAULT_MAX_OMEGA = 0.6;

    /**
     * Default proportional gain for the simple P-only aiming controller.
     */
    private static final double DEFAULT_KP = 1.5;

    private TagAim() {
        // Utility class; no instances.
    }

    // ------------------------------------------------------------------------
    // Generic configuration
    // ------------------------------------------------------------------------

    /**
     * Generic configuration for tag-based aiming.
     *
     * <p>
     * This configuration is <b>not</b> TeleOp-specific. The same object can be
     * used to:
     * </p>
     * <ul>
     *   <li>tune the built-in TeleOp helper
     *       ({@link #teleOpAim(DriveSource, Button, AprilTagSensor, Set, Config)}), or</li>
     *   <li>build a {@link TagAimController} directly for autonomous logic via
     *       {@link #controllerFromConfig(Config)}.</li>
     * </ul>
     */
    public static final class Config {

        /**
         * Maximum age (seconds) for a tag observation. Readings older than
         * this are ignored by helpers that use an {@link AprilTagSensor}.
         *
         * <p>Default: {@value TagAim#DEFAULT_MAX_AGE_SEC}.</p>
         */
        public double maxAgeSec = DEFAULT_MAX_AGE_SEC;

        /**
         * Deadband for aiming, in degrees. Bearing errors whose magnitude is
         * below this threshold are treated as on-target and produce zero output
         * from the default controller.
         *
         * <p>Default: {@value TagAim#DEFAULT_DEADBAND_DEG} degrees.</p>
         */
        public double deadbandDeg = DEFAULT_DEADBAND_DEG;

        /**
         * Absolute maximum turn-rate command (omega).
         *
         * <p>Default: {@value TagAim#DEFAULT_MAX_OMEGA}.</p>
         */
        public double maxOmega = DEFAULT_MAX_OMEGA;

        /**
         * Proportional gain for the simple P-only controller
         * ({@code omega = kp * bearingError}).
         *
         * <p>Default: {@value TagAim#DEFAULT_KP}.</p>
         */
        public double kp = DEFAULT_KP;

        /**
         * Policy used when the target is lost.
         *
         * <p>Default: {@link TagAimController.LossPolicy#ZERO_OUTPUT_RESET_I}.</p>
         */
        public TagAimController.LossPolicy lossPolicy =
                TagAimController.LossPolicy.ZERO_OUTPUT_RESET_I;

        private Config() {
            // Use defaults().
        }

        /**
         * Create a new configuration instance with default values.
         *
         * @return a new {@link Config} instance
         */
        public static Config defaults() {
            return new Config();
        }

        /**
         * Create a shallow copy of this configuration.
         *
         * <p>
         * Useful when you want to start from a base config, tweak a few fields,
         * and keep the original unchanged.
         * </p>
         *
         * @return a new {@link Config} with the same field values
         */
        public Config copy() {
            Config c = new Config();
            c.maxAgeSec   = this.maxAgeSec;
            c.deadbandDeg = this.deadbandDeg;
            c.maxOmega    = this.maxOmega;
            c.kp          = this.kp;
            c.lossPolicy  = this.lossPolicy;
            return c;
        }
    }

    // ------------------------------------------------------------------------
    // Beginner / tunable TeleOp API: AprilTagSensor + tag IDs
    // ------------------------------------------------------------------------

    /**
     * TeleOp helper: wrap an existing drive source with tag-based auto-aim,
     * using an {@link AprilTagSensor} and a set of tag IDs.
     *
     * <p>This overload is intended for most teams. It:</p>
     *
     * <ul>
     *   <li>builds a {@link BearingSource} from
     *       {@link AprilTagSensor#best(Set, double)},</li>
     *   <li>uses a reasonable default {@link TagAimController}:
     *     <ul>
     *       <li>P-only control with gain {@link #DEFAULT_KP},</li>
     *       <li>deadband of {@link #DEFAULT_DEADBAND_DEG} degrees,</li>
     *       <li>maximum omega of {@link #DEFAULT_MAX_OMEGA}, and</li>
     *       <li>{@link TagAimController.LossPolicy#ZERO_OUTPUT_RESET_I} for
     *           target loss.</li>
     *     </ul>
     *   </li>
     *   <li>returns a {@link DriveSource} that:
     *     <ul>
     *       <li>forwards the base drive command when {@code aimButton} is not pressed, and</li>
     *       <li>overrides only omega while {@code aimButton} is pressed.</li>
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

        return teleOpAim(baseDrive, aimButton, sensor, tagIds, Config.defaults());
    }

    /**
     * TeleOp helper: same as
     * {@link #teleOpAim(DriveSource, Button, AprilTagSensor, Set)} but with
     * an explicit {@link Config} to override the defaults.
     *
     * <p>
     * This is useful when you want the convenience of the beginner API
     * (automatic {@link AprilTagSensor} wiring) but need to tune parameters such as
     * proportional gain, deadband, maximum omega, or allowed tag age.
     * </p>
     *
     * @param baseDrive existing drive source (e.g., stick mapping)
     * @param aimButton button that enables aiming while pressed
     * @param sensor    AprilTag sensor providing detections
     * @param tagIds    set of tag IDs the robot cares about (e.g., scoring tags)
     * @param config    configuration for aiming; if {@code null},
     *                  {@link Config#defaults()} is used
     * @return a new {@link DriveSource} that adds aiming behavior on top of {@code baseDrive}
     */
    public static DriveSource teleOpAim(
            DriveSource baseDrive,
            Button aimButton,
            AprilTagSensor sensor,
            Set<Integer> tagIds,
            Config config) {

        Config cfg = (config != null) ? config : Config.defaults();

        BearingSource bearing = bearingFromAprilTags(sensor, tagIds, cfg.maxAgeSec);
        TagAimController controller = controllerFromConfig(cfg);

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
     *   <li>where bearing information comes from ({@link BearingSource}), and</li>
     *   <li>how bearing is converted into omega ({@link TagAimController}).</li>
     * </ul>
     *
     * <p>Behavior:</p>
     * <ul>
     *   <li>On each call to {@link DriveSource#get(edu.ftcphoenix.fw.util.LoopClock)}:
     *     <ul>
     *       <li>compute the base command from {@code baseDrive},</li>
     *       <li>if {@code aimButton} is not pressed, return base unchanged, else:
     *         <ul>
     *           <li>sample bearing via
     *               {@link BearingSource#sample(edu.ftcphoenix.fw.util.LoopClock)},</li>
     *           <li>call
     *               {@link TagAimController#update(edu.ftcphoenix.fw.util.LoopClock,
     *               edu.ftcphoenix.fw.sensing.BearingSource.BearingSample)}
     *               to get omega, and</li>
     *           <li>return a new command that keeps base axial/lateral but uses
     *               the aiming omega.</li>
     *         </ul>
     *       </li>
     *     </ul>
     *   </li>
     * </ul>
     *
     * @param baseDrive  existing drive source (e.g., sticks, planner)
     * @param aimButton  button that enables aiming while pressed
     * @param bearing    bearing source (target angle) used for aiming
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
     *   <li>uses {@link AprilTagSensor#best(Set, double)} to choose the "best"
     *       observation from the set of desired tags, and</li>
     *   <li>converts that into a {@link BearingSource.BearingSample} for aiming.</li>
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
     * Build a {@link TagAimController} from a {@link Config}.
     *
     * <p>
     * This is a general-purpose helper: it is used by the TeleOp helpers, and
     * can also be used directly in autonomous code that wants to control
     * {@code omega} from tag bearing without going through
     * {@link TagAimDriveSource}.
     * </p>
     *
     * @param cfg configuration; if {@code null}, {@link Config#defaults()} is used
     * @return a new {@link TagAimController} instance
     */
    public static TagAimController controllerFromConfig(Config cfg) {
        if (cfg == null) {
            cfg = Config.defaults();
        }

        final double kp = cfg.kp;

        PidController pid = new PidController() {
            @Override
            public double update(double error, double dtSec) {
                // P-only: omega = Kp * error
                return kp * error;
            }
            // Default reset() is fine (no state).
        };

        double deadbandRad = Math.toRadians(cfg.deadbandDeg);
        TagAimController.LossPolicy loss =
                (cfg.lossPolicy != null)
                        ? cfg.lossPolicy
                        : TagAimController.LossPolicy.ZERO_OUTPUT_RESET_I;

        return new TagAimController(
                pid,
                deadbandRad,
                cfg.maxOmega,
                loss
        );
    }
}

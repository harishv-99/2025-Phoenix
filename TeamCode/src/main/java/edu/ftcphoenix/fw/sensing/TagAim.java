package edu.ftcphoenix.fw.sensing;

import java.util.Objects;

import edu.ftcphoenix.fw.core.PidController;
import edu.ftcphoenix.fw.drive.DriveSource;
import edu.ftcphoenix.fw.drive.source.TagAimDriveSource;
import edu.ftcphoenix.fw.input.Button;
import edu.ftcphoenix.fw.task.Task;
import edu.ftcphoenix.fw.task.WaitUntilTask;

/**
 * Helpers for tag-based auto-aim that wrap an existing {@link DriveSource}.
 *
 * <h2>Role</h2>
 * <p>{@code TagAim} is a convenience facade that wires together:</p>
 *
 * <ul>
 *   <li>A source of target bearing (typically built from a {@link TagTarget} or
 *       directly from an {@link AprilTagSensor} via a {@link BearingSource}).</li>
 *   <li>A {@link TagAimController} that turns bearing error into an omega command.</li>
 *   <li>A {@link TagAimDriveSource} that overrides only the turn (omega) portion
 *       of an existing {@link DriveSource} while an aim button is held.</li>
 * </ul>
 *
 * <h2>Camera offset note (why CameraMountConfig matters)</h2>
 * <p>
 * A {@link TagTarget} reports bearing derived from {@link AprilTagObservation#pCameraToTag}
 * (i.e., bearing relative to the <b>camera</b> forward axis). If the camera is offset from the
 * robot center, “camera faces the tag” is not always the same as “robot center faces the tag”.
 * </p>
 *
 * <p>
 * To aim the <b>robot center</b> at the tag, use the overloads that accept a
 * {@link CameraMountConfig}. These compute robot-centric bearing by applying the mount extrinsics
 * (robot→camera) to the camera measurement (camera→tag), producing robot→tag, then taking
 * {@code atan2(left, forward)} in the robot frame.
 * </p>
 *
 * <h2>Usage levels</h2>
 * <ul>
 *   <li><b>Beginner (camera-centric):</b> create a {@link TagTarget} and call
 *       {@link #teleOpAim(DriveSource, Button, TagTarget)}.</li>
 *   <li><b>Beginner (robot-centric):</b> create a {@link TagTarget} and pass your
 *       {@link CameraMountConfig} via
 *       {@link #teleOpAim(DriveSource, Button, TagTarget, CameraMountConfig)}.</li>
 *   <li><b>Tunable:</b> provide a {@link Config} to adjust gain, deadband, maximum omega, or
 *       lost-target behavior.</li>
 *   <li><b>Advanced:</b> build your own {@link BearingSource} and {@link TagAimController}, then call
 *       {@link #teleOpAim(DriveSource, Button, BearingSource, TagAimController)}.</li>
 * </ul>
 */
public final class TagAim {

    /**
     * Default proportional gain for simple P-only aiming.
     */
    private static final double DEFAULT_KP = 4.0;

    /**
     * Default deadband for aiming, in radians.
     *
     * <p>This corresponds to roughly 1 degree.</p>
     */
    private static final double DEFAULT_DEADBAND_RAD = Math.toRadians(1.0);

    /**
     * Default maximum turn rate command for aiming.
     */
    private static final double DEFAULT_MAX_OMEGA = 0.8;

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
     * This configuration is not TeleOp-specific. The same object can be used
     * to:
     * </p>
     * <ul>
     *   <li>Tune the built-in TeleOp helper
     *       ({@link #teleOpAim(DriveSource, Button, TagTarget, Config)}), or</li>
     *   <li>Build a {@link TagAimController} directly for autonomous logic via
     *       {@link #controllerFromConfig(Config)}.</li>
     * </ul>
     */
    public static final class Config {

        /**
         * Proportional gain for the simple P-only controller
         * ({@code omega = kp * bearingError}).
         *
         * <p>Default: {@value TagAim#DEFAULT_KP}.</p>
         */
        public double kp = DEFAULT_KP;

        /**
         * Deadband for aiming, in radians.
         *
         * <p>
         * When {@code |bearing| < deadbandRad}, the controller outputs zero
         * omega and does not update the PID state.
         * </p>
         *
         * <p>Default: {@link TagAim#DEFAULT_DEADBAND_RAD}.</p>
         */
        public double deadbandRad = DEFAULT_DEADBAND_RAD;

        /**
         * Absolute maximum turn-rate command (omega).
         *
         * <p>Default: {@value TagAim#DEFAULT_MAX_OMEGA}.</p>
         */
        public double maxOmega = DEFAULT_MAX_OMEGA;

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
         * @return a new {@link Config} with the same field values
         */
        public Config copy() {
            Config c = new Config();
            c.kp = this.kp;
            c.deadbandRad = this.deadbandRad;
            c.maxOmega = this.maxOmega;
            c.lossPolicy = this.lossPolicy;
            return c;
        }
    }

    // ------------------------------------------------------------------------
    // Beginner / tunable TeleOp API: TagTarget (camera-centric)
    // ------------------------------------------------------------------------

    /**
     * TeleOp helper: wrap an existing drive source with tag-based auto-aim,
     * using a {@link TagTarget}'s <b>camera-centric</b> bearing.
     *
     * <p>If your camera is offset and you want the <b>robot center</b> to face the tag,
     * use {@link #teleOpAim(DriveSource, Button, TagTarget, CameraMountConfig)} instead.</p>
     */
    public static DriveSource teleOpAim(
            DriveSource baseDrive,
            Button aimButton,
            TagTarget target) {

        return teleOpAim(baseDrive, aimButton, target, Config.defaults());
    }

    /**
     * TeleOp helper (camera-centric): same as {@link #teleOpAim(DriveSource, Button, TagTarget)}
     * but with explicit {@link Config}.
     */
    public static DriveSource teleOpAim(
            DriveSource baseDrive,
            Button aimButton,
            TagTarget target,
            Config config) {

        if (target == null) {
            throw new IllegalArgumentException("target must not be null");
        }

        Config cfg = (config != null) ? config : Config.defaults();

        BearingSource bearing = clock -> target.toBearingSample();
        TagAimController controller = controllerFromConfig(cfg);

        return teleOpAim(baseDrive, aimButton, bearing, controller);
    }

    // ------------------------------------------------------------------------
    // Beginner / tunable TeleOp API: TagTarget + CameraMountConfig (robot-centric)
    // ------------------------------------------------------------------------

    /**
     * TeleOp helper: wrap an existing drive source with tag-based auto-aim,
     * using a {@link TagTarget} but computing <b>robot-centric</b> bearing by applying
     * {@link CameraMountConfig} (camera extrinsics).
     *
     * <p>This aims the <b>robot center</b> at the tag even if the camera is offset.</p>
     *
     * @param baseDrive   existing drive source (e.g., stick mapping)
     * @param aimButton   button that enables aiming while pressed
     * @param target      tag target providing the current observation
     * @param cameraMount robot→camera extrinsics (mount config)
     * @return a new {@link DriveSource} that adds robot-centric aiming behavior on top of {@code baseDrive}
     */
    public static DriveSource teleOpAim(
            DriveSource baseDrive,
            Button aimButton,
            TagTarget target,
            CameraMountConfig cameraMount) {

        return teleOpAim(baseDrive, aimButton, target, cameraMount, Config.defaults());
    }

    /**
     * TeleOp helper (robot-centric): same as
     * {@link #teleOpAim(DriveSource, Button, TagTarget, CameraMountConfig)} but with an explicit
     * {@link Config} to override the defaults.
     *
     * @param baseDrive   existing drive source (e.g., stick mapping)
     * @param aimButton   button that enables aiming while pressed
     * @param target      tag target providing the current observation; must not be null
     * @param cameraMount robot→camera extrinsics (mount config); must not be null
     * @param config      configuration for aiming; if {@code null}, {@link Config#defaults()} is used
     * @return a new {@link DriveSource} that adds robot-centric aiming behavior on top of {@code baseDrive}
     */
    public static DriveSource teleOpAim(
            DriveSource baseDrive,
            Button aimButton,
            TagTarget target,
            CameraMountConfig cameraMount,
            Config config) {

        Objects.requireNonNull(target, "target must not be null");
        Objects.requireNonNull(cameraMount, "cameraMount must not be null");

        Config cfg = (config != null) ? config : Config.defaults();

        // Robot-centric bearing derived from mount + observation pose:
        // pRobotToTag = pRobotToCamera.then(pCameraToTag)
        BearingSource bearing = clock ->
                CameraMountLogic.robotBearingSample(target.last(), cameraMount);

        TagAimController controller = controllerFromConfig(cfg);

        return teleOpAim(baseDrive, aimButton, bearing, controller);
    }

    // ------------------------------------------------------------------------
    // Aim readiness helpers: tasks that wait for alignment
    // ------------------------------------------------------------------------

    /**
     * Create a {@link Task} that waits until the given {@link TagTarget}'s
     * <b>camera-centric</b> bearing is within a specified angular tolerance.
     *
     * <p>If you are using camera-mount-aware aiming (robot-centric), prefer
     * {@link #waitForAim(TagTarget, CameraMountConfig, double)}.</p>
     */
    public static Task waitForAim(TagTarget target, double toleranceRad) {
        Objects.requireNonNull(target, "target is required");
        if (toleranceRad < 0.0) {
            throw new IllegalArgumentException("toleranceRad must be >= 0, got " + toleranceRad);
        }

        return new WaitUntilTask(() -> target.isBearingWithin(toleranceRad));
    }

    /**
     * Create a {@link Task} that waits until the given {@link TagTarget}'s
     * <b>camera-centric</b> bearing is within a specified angular tolerance, but
     * gives up if it takes longer than {@code timeoutSec}.
     */
    public static Task waitForAim(TagTarget target,
                                  double toleranceRad,
                                  double timeoutSec) {
        Objects.requireNonNull(target, "target is required");
        if (toleranceRad < 0.0) {
            throw new IllegalArgumentException("toleranceRad must be >= 0, got " + toleranceRad);
        }
        if (timeoutSec < 0.0) {
            throw new IllegalArgumentException("timeoutSec must be >= 0, got " + timeoutSec);
        }

        return new WaitUntilTask(
                () -> target.isBearingWithin(toleranceRad),
                timeoutSec
        );
    }

    /**
     * Create a {@link Task} that waits until the tracked tag is within tolerance using
     * <b>robot-centric</b> bearing computed from {@link CameraMountConfig}.
     *
     * <p>Semantics:</p>
     * <ul>
     *   <li>If {@link TagTarget#hasTarget()} is {@code false}, the condition is treated as not satisfied.</li>
     *   <li>Completes successfully when {@code |robotBearing| <= toleranceRad}.</li>
     *   <li>No timeout.</li>
     * </ul>
     */
    public static Task waitForAim(TagTarget target,
                                  CameraMountConfig cameraMount,
                                  double toleranceRad) {

        Objects.requireNonNull(target, "target is required");
        Objects.requireNonNull(cameraMount, "cameraMount is required");
        if (toleranceRad < 0.0) {
            throw new IllegalArgumentException("toleranceRad must be >= 0, got " + toleranceRad);
        }

        return new WaitUntilTask(() -> {
            AprilTagObservation obs = target.last();
            if (obs == null || !obs.hasTarget) {
                return false;
            }
            double robotBearing = CameraMountLogic.robotBearingRad(obs, cameraMount);
            return Math.abs(robotBearing) <= toleranceRad;
        });
    }

    /**
     * Create a {@link Task} that waits until the tracked tag is within tolerance using
     * <b>robot-centric</b> bearing computed from {@link CameraMountConfig}, with a timeout.
     */
    public static Task waitForAim(TagTarget target,
                                  CameraMountConfig cameraMount,
                                  double toleranceRad,
                                  double timeoutSec) {

        Objects.requireNonNull(target, "target is required");
        Objects.requireNonNull(cameraMount, "cameraMount is required");
        if (toleranceRad < 0.0) {
            throw new IllegalArgumentException("toleranceRad must be >= 0, got " + toleranceRad);
        }
        if (timeoutSec < 0.0) {
            throw new IllegalArgumentException("timeoutSec must be >= 0, got " + timeoutSec);
        }

        return new WaitUntilTask(() -> {
            AprilTagObservation obs = target.last();
            if (obs == null || !obs.hasTarget) {
                return false;
            }
            double robotBearing = CameraMountLogic.robotBearingRad(obs, cameraMount);
            return Math.abs(robotBearing) <= toleranceRad;
        }, timeoutSec);
    }

    // ------------------------------------------------------------------------
    // Advanced API: generic bearing source + controller
    // ------------------------------------------------------------------------

    /**
     * TeleOp helper: wrap an existing drive source with generic bearing-based
     * auto-aim.
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

        // Delegate to the reusable TagAimDriveSource so we don't duplicate logic.
        return new TagAimDriveSource(baseDrive, aimButton, bearing, controller);
    }

    // ------------------------------------------------------------------------
    // Internal helpers
    // ------------------------------------------------------------------------

    /**
     * Build a {@link TagAimController} from a {@link Config}.
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
            // Default reset() is fine (no internal state).
        };

        double deadbandRad = cfg.deadbandRad;
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

package edu.ftcphoenix.robots.phoenix;

import com.qualcomm.robotcore.hardware.Gamepad;
import com.qualcomm.robotcore.hardware.DcMotor;
import com.qualcomm.robotcore.hardware.HardwareMap;

import org.firstinspires.ftc.robotcore.external.Telemetry;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.function.BooleanSupplier;

import edu.ftcphoenix.fw.ftc.FtcTelemetryDebugSink;
import edu.ftcphoenix.fw.ftc.FtcVision;
import edu.ftcphoenix.fw.ftc.localization.PinpointPoseEstimator;
import edu.ftcphoenix.fw.core.debug.DebugSink;
import edu.ftcphoenix.fw.drive.DriveSignal;
import edu.ftcphoenix.fw.drive.DriveOverlayMask;
import edu.ftcphoenix.fw.drive.DriveSource;
import edu.ftcphoenix.fw.drive.Drives;
import edu.ftcphoenix.fw.drive.MecanumDrivebase;
import edu.ftcphoenix.fw.drive.guidance.DriveGuidance;
import edu.ftcphoenix.fw.drive.guidance.DriveGuidancePlan;
import edu.ftcphoenix.fw.drive.source.GamepadDriveSource;
import edu.ftcphoenix.fw.input.Gamepads;
import edu.ftcphoenix.fw.input.binding.Bindings;
import edu.ftcphoenix.fw.sensing.observation.ObservationSource2d;
import edu.ftcphoenix.fw.sensing.observation.ObservationSources;
import edu.ftcphoenix.fw.sensing.vision.apriltag.AprilTagObservation;
import edu.ftcphoenix.fw.sensing.vision.apriltag.AprilTagSensor;
import edu.ftcphoenix.fw.sensing.vision.CameraMountConfig;
import edu.ftcphoenix.fw.sensing.vision.apriltag.TagTarget;
import edu.ftcphoenix.fw.task.TaskRunner;
import edu.ftcphoenix.fw.task.TaskBindings;
import edu.ftcphoenix.fw.task.Tasks;
import edu.ftcphoenix.fw.core.time.LoopClock;

/**
 * Central robot class for Phoenix-based robots.
 *
 * <p>Beginners should mostly edit <b>this file</b>. TeleOp and Auto OpModes are
 * kept very thin and simply delegate into the methods here.</p>
 *
 * <h2>Responsibilities</h2>
 * <ul>
 *   <li>Wire all hardware once (drive, intake, transfer, shooter, pusher, vision).</li>
 *   <li>Define gamepad mappings in one place via {@link Bindings}.</li>
 *   <li>Own shared logic: auto-aim, shooter velocity, macros, autos.</li>
 *   <li>Expose simple entry points for TeleOp and Auto.</li>
 * </ul>
 */
public final class PhoenixRobot {
    private final LoopClock clock = new LoopClock();
    private final HardwareMap hardwareMap;
    private final Telemetry telemetry;
    private final Gamepads gamepads;
    private final Bindings bindings = new Bindings();
    private final TaskRunner taskRunnerTeleOp = new TaskRunner();
    private final DebugSink dbg;
    private Shooter shooter;
    private MecanumDrivebase drivebase;
    private PinpointPoseEstimator pinpoint;
    private DriveSource stickDrive;
    private DriveSource driveWithAim;
    private CameraMountConfig cameraMountConfig;
    private AprilTagSensor tagSensor;
    private TagTarget scoringTarget;
    private DriveGuidancePlan aimPlan;
    private DriveGuidancePlan.Tuning aimTuning;

    // "Shoot brace" pose-lock: latch-on while the shooter is spinning and the driver is not
    // commanding translation. This lets the driver still nudge/strafe to line up, but once they
    // let go, the robot resists being bumped off the spot.
    private boolean shootBraceEnabled = false;
    private boolean shootBraceLatched = false;
    private static final double SHOOT_BRACE_ENTER_MAG = 0.06;
    private static final double SHOOT_BRACE_EXIT_MAG = 0.10;


    // ----------------------------------------------------------------------
    // Tag IDs we care about (example values; adjust per game) – Java 8 style
    // ----------------------------------------------------------------------

    private static final Set<Integer> SCORING_TAG_IDS;

    static {
        HashSet<Integer> ids = new HashSet<Integer>();
        ids.add(20);
        ids.add(24);
        SCORING_TAG_IDS = Collections.unmodifiableSet(ids);
    }

    /**
     * Create the robot container.
     *
     * <p>This class owns the major subsystems (drive, shooter, etc.) and wires them to gamepads.
     * It is intended to be constructed once in an OpMode {@code init()} method.</p>
     *
     * @param hardwareMap FTC hardware map
     * @param telemetry   FTC telemetry sink
     * @param gamepad1    primary driver controller
     * @param gamepad2    secondary operator controller
     */
    public PhoenixRobot(HardwareMap hardwareMap, Telemetry telemetry, Gamepad gamepad1, Gamepad gamepad2) {
        this.hardwareMap = hardwareMap;
        this.gamepads = Gamepads.create(gamepad1, gamepad2);
        this.telemetry = telemetry;
        this.dbg = new FtcTelemetryDebugSink(telemetry);
    }

    /**
     * Initialize components shared by all OpModes.
     */
    public void initAny() {
    }

    /**
     * Initialize TeleOp-specific state and bindings.
     */
    public void initTeleOp() {

        // --- Create mechanisms ---
        MecanumDrivebase.Config mecanumConfig = MecanumDrivebase.Config.defaults();
        drivebase = Drives.mecanum(
                hardwareMap,
                RobotConfig.DriveTrain.mecanumWiring(),
                mecanumConfig);

        // Use motor braking to help resist small pushes when commanded power is 0.
        // PoseLock will actively correct position, but BRAKE helps reduce "coast".
        setDriveBrake(RobotConfig.DriveTrain.zeroPowerBrake);

        shooter = new Shooter(hardwareMap, telemetry, gamepads);

        // --- Use the standard TeleOp stick mapping for mecanum.
        stickDrive = GamepadDriveSource.teleOpMecanumSlowRb(gamepads);

        // --- Odometry (goBILDA Pinpoint) ---
        // This is used for pose-lock (resist bumps while shooting). It can also be used for
        // autonomous / fusion later.
//        pinpoint = new PinpointPoseEstimator(hardwareMap, RobotConfig.Localization.pinpoint);

        // --- Vision ---
        cameraMountConfig = RobotConfig.Vision.cameraMount;
        tagSensor = FtcVision.aprilTags(hardwareMap, RobotConfig.Vision.nameWebcam);

        // Track scoring tags with a freshness window.
        scoringTarget = new TagTarget(tagSensor, SCORING_TAG_IDS, 0.5);

        // --- Drive guidance (replaces TagAim): hold P2 LB to auto-aim omega at the best scoring tag.
        // Use the framework's helper so robot code stays simple.
        // (This updates the TagTarget each loop and converts the latest AprilTag measurement into a
        // robot-relative planar observation used by DriveGuidance.)
        ObservationSource2d obs2d = ObservationSources.aprilTag(scoringTarget, cameraMountConfig);

        // Tuning for the auto-aim assist. Start with defaults and tweak only what you need.
        aimTuning = DriveGuidancePlan.Tuning.defaults()
                .withAimKp(2.0)            // how strongly we turn toward the target
                .withAimDeadbandDeg(0.25); // stop turning when we're within this many degrees

        aimPlan = DriveGuidance.plan()
                .aimTo()
                .tagCenter()
                .doneAimTo()
                .tuning(aimTuning)
                .feedback()
                .observation(obs2d, 0.50, 0.0)
                .lossPolicy(DriveGuidancePlan.LossPolicy.PASS_THROUGH)
                .doneFeedback()
                .build();

        // Enable condition for the guidance overlay.
        //
        // Hold-to-enable is the simplest for drivers. If you prefer a toggle (press once to
        // enable, press again to disable), use: gamepads.p2().leftBumper()::isToggled
        BooleanSupplier autoAimEnabled = gamepads.p2().leftBumper()::isHeld;

        driveWithAim = DriveGuidance.overlayOn(
                stickDrive
                        .overlayWhen(
                                () -> shootBraceEnabled,
                                DriveGuidance.poseLock(
                                        pinpoint,
                                        DriveGuidancePlan.Tuning.defaults()
                                                .withTranslateKp(0.08)
                                                .withMaxTranslateCmd(0.35)
                                ),
                                DriveOverlayMask.TRANSLATION_ONLY
                        ),
                autoAimEnabled,
                aimPlan,
                DriveOverlayMask.OMEGA_ONLY
        );

        telemetry.addLine("Phoenix TeleOp with AutoAim");
        telemetry.addLine("Left stick: drive, Right stick: turn, RB: slow mode");
        telemetry.addLine("P2 LB: auto-aim + lock translation (shoot brace)");
        telemetry.update();

        // Create bindings
        createBindings();
    }

    private void createBindings() {
        // Most bindings in TeleOp simply enqueue a Task. TaskBindings removes the
        // repeated "() -> runner.enqueue(... )" boilerplate.
        TaskBindings tb = TaskBindings.of(bindings, taskRunnerTeleOp);

        tb.onPress(gamepads.p2().y(), shooter::instantSetPusherFront);
        tb.onPress(gamepads.p2().a(), shooter::instantSetPusherBack);

        // Hold to run transfer; release to stop.
        tb.onPressAndRelease(
                gamepads.p2().b(),
                () -> shooter.instantStartTransfer(Shooter.TransferDirection.FORWARD),
                shooter::instantStopTransfer
        );

        tb.onPressAndRelease(
                gamepads.p2().x(),
                () -> shooter.instantStartTransfer(Shooter.TransferDirection.BACKWARD),
                shooter::instantStopTransfer
        );

        // While held: continuously update shooter velocity based on the latest tag range.
        tb.whileHeld(gamepads.p2().leftBumper(), () -> {
            AprilTagObservation obs = scoringTarget.last();
            return obs.hasTarget
                    ? shooter.instantSetVelocityByDist(obs.cameraRangeInches())
                    : Tasks.noop();
        });

        tb.onToggle(gamepads.p2().rightBumper(), shooter::instantStartShooter, shooter::instantStopShooter);

        tb.onPress(gamepads.p2().dpadUp(), shooter::instantIncreaseVelocity);
        tb.onPress(gamepads.p2().dpadDown(), shooter::instantDecreaseVelocity);
    }

    /**
     * Start hook shared by all OpModes.
     */
    public void startAny(double runtime) {
        // Initialize loop timing.
        clock.reset(runtime);
    }

    /**
     * Start hook for TeleOp.
     */
    public void startTeleOp() {
    }

    /**
     * Periodic update shared by all OpModes.
     */
    public void updateAny(double runtime) {
        // --- 1) Clock ---
        clock.update(runtime);
    }

    /**
     * Periodic update for TeleOp.
     */
    public void updateTeleOp() {
        // --- 2) Inputs + bindings ---
        gamepads.update(clock);

        // Update tracked tag once per loop.
        scoringTarget.update(clock);

        bindings.update(clock);

        // --- Odometry update (needed for pose lock) ---
        if (pinpoint != null) {
            pinpoint.update(clock);
        }

        // --- Shoot-brace latch (pose lock translation only) ---
        updateShootBraceEnabled();


        // --- 3) TeleOp Macros ---
        taskRunnerTeleOp.update(clock);

        // When no macro is active, hold a safe default state.
        if (!taskRunnerTeleOp.hasActiveTask()) {
        }

        // --- 4) Drive: guidance overlay (P2 LB may override omega) ---
        DriveSignal cmd = driveWithAim.get(clock).clamped();
        drivebase.update(clock);
        drivebase.drive(cmd);

        // --- 4) Other mechanisms ---


        // --- 5) Telemetry / debug ---
        telemetry.addData("shooter velocity", shooter.getVelocity());
        telemetry.addData("shootBrace", shootBraceEnabled);
        if (pinpoint != null) {
            telemetry.addData("pose", pinpoint.getEstimate());
        }
//        driveWithAim.debugDump(dbg, "drive");
        AprilTagObservation obs = scoringTarget.last();
        if (obs.hasTarget) {
            if (Math.abs(scoringTarget.robotBearingRad(cameraMountConfig)) <= (aimTuning.aimDeadbandRad * 5))
                telemetry.addLine(">>> AIMED <<<");
            telemetry.addData("tagId", obs.id);
            telemetry.addData("distIn", obs.cameraRangeInches());
            telemetry.addData("bearingDeg", Math.toDegrees(obs.cameraBearingRad()));
        }

        telemetry.update();
    }

    /**
     * Latches pose-lock while P2 is holding auto-aim (LB) and the driver is not commanding translation.
     *
     * <p>This makes "hold LB" a single driver action for: aim + brace + shoot.</p>
     * <p>Hysteresis prevents chatter when sticks hover near center.</p>
     */
    private void updateShootBraceEnabled() {
        // Only brace while the aiming driver (P2) is actively holding the aim button.
        // This avoids surprise "fighting the driver" behavior during normal driving.
        if (!gamepads.p2().leftBumper().isHeld()) {
            shootBraceLatched = false;
            shootBraceEnabled = false;
            return;
        }

        double lx = gamepads.p1().leftX().get();
        double ly = gamepads.p1().leftY().get();
        double mag = Math.hypot(lx, ly);

        if (shootBraceLatched) {
            if (mag > SHOOT_BRACE_EXIT_MAG) {
                shootBraceLatched = false;
            }
        } else {
            if (mag < SHOOT_BRACE_ENTER_MAG) {
                shootBraceLatched = true;
            }
        }

        shootBraceEnabled = shootBraceLatched;
    }

    /**
     * Sets ZeroPowerBehavior on the drivetrain motors.
     */
    private void setDriveBrake(boolean brake) {
        DcMotor.ZeroPowerBehavior behavior = brake
                ? DcMotor.ZeroPowerBehavior.BRAKE
                : DcMotor.ZeroPowerBehavior.FLOAT;

        hardwareMap.get(DcMotor.class, RobotConfig.DriveTrain.nameMotorFrontLeft)
                .setZeroPowerBehavior(behavior);
        hardwareMap.get(DcMotor.class, RobotConfig.DriveTrain.nameMotorFrontRight)
                .setZeroPowerBehavior(behavior);
        hardwareMap.get(DcMotor.class, RobotConfig.DriveTrain.nameMotorBackLeft)
                .setZeroPowerBehavior(behavior);
        hardwareMap.get(DcMotor.class, RobotConfig.DriveTrain.nameMotorBackRight)
                .setZeroPowerBehavior(behavior);
    }

    /**
     * Stop hook shared by all OpModes.
     */
    public void stopAny() {
        drivebase.stop();
    }

    /**
     * Stop hook for TeleOp.
     */
    public void stopTeleOp() {
        // Release camera resources so future OpModes/testers can start vision cleanly.
        if (tagSensor != null) {
            tagSensor.close();
            tagSensor = null;
        }
    }
}

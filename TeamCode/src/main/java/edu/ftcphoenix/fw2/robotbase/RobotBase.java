package edu.ftcphoenix.fw2.robotbase;

import com.acmerobotics.dashboard.FtcDashboard;
import com.acmerobotics.dashboard.telemetry.TelemetryPacket;
import com.acmerobotics.roadrunner.Action;
import com.qualcomm.hardware.lynx.LynxModule;
import com.qualcomm.robotcore.eventloop.opmode.LinearOpMode;
import com.qualcomm.robotcore.hardware.HardwareMap;

import org.firstinspires.ftc.robotcore.external.Telemetry;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import edu.ftcphoenix.fw2.gamepad.GamepadInputs;
import edu.ftcphoenix.fw2.gamepad.rev.GamepadController;
import edu.ftcphoenix.fw2.platform.rev.LynxBulkCacheManager;
import edu.ftcphoenix.fw2.robotbase.periodicrunner.PeriodicRunner;
import edu.ftcphoenix.fw2.robotbase.statehistory.RobotStateHistory;
import edu.ftcphoenix.fw2.subsystems.Subsystem;
import edu.ftcphoenix.fw2.util.ElapsedTimeMillis;
import edu.ftcphoenix.fw2.util.LoopClock;

/**
 * Base class for FTC robots.
 *
 * <p>Responsibilities:</p>
 * <ul>
 *   <li>Owns shared services: {@link LoopClock}, {@link PeriodicRunner}, bulk cache manager, dashboard, etc.</li>
 *   <li>Provides structured lifecycle: init → waitForStart → periodic loop → exit.</li>
 *   <li>Manages a registry of {@link Subsystem}s and runs their lifecycle:
 *       {@code onEnable()}, {@code update(clock)}, {@code onDisable()}, {@code stop()}.</li>
 * </ul>
 *
 * <h2>Typical usage</h2>
 * <pre>{@code
 * public final class Robot extends RobotBase<Robot.Components> {
 *   DriveTrainSubsystem drive;
 *   ShooterSubsystem shooter;
 *
 *   @Override protected void initRobot() {
 *     drive = new DriveTrainSubsystem(getHardwareMap(), getTelemetry());
 *     shooter = new ShooterSubsystem(getHardwareMap(), getTelemetry());
 *     registerSubsystems(drive, shooter);
 *   }
 *
 *   @Override protected void initTeleOp() { /* bind controllers, graphs, etc. *-/ }
 *   @Override protected void onPeriodicTeleOp() { /* set targets; subsystems push on update() *-/ }
 * }
 * }</pre>
 *
 * <p><b>Order of operations per loop:</b></p>
 * <ol>
 *   <li>Bulk cache clear &amp; periodic runner tasks.</li>
 *   <li>Op-mode specific periodic ({@link #onPeriodicTeleOp()} or {@link #onPeriodicAutonomous()})
 *       — set setpoints/commands.</li>
 *   <li><b>Subsystem updates</b> — each registered subsystem {@code update(clock)} pushes commands to hardware.</li>
 *   <li>Robot-wide periodic ({@link #onPeriodicRobot()}).</li>
 * </ol>
 *
 * @param <C> enum describing your robot's components for state history (if used).
 */
public abstract class RobotBase<C extends Enum<C>> {

    private final int INTERVAL_MS_PERIODIC = 20;
    private final int NUM_COMPONENT_STATE_HISTORY_ITEMS = 500;

    // --- Timing / loop ---
    private final ElapsedTimeMillis timeStartOfOpMode = new ElapsedTimeMillis();
    private final ElapsedTimeMillis timeLastPeriodic = new ElapsedTimeMillis();
    private LoopClock clock = new LoopClock();

    // --- Services / utils ---
    private final FtcDashboard dash = FtcDashboard.getInstance();
    private final RobotStateHistory<C> robotStateHistory = new RobotStateHistory<>(NUM_COMPONENT_STATE_HISTORY_ITEMS);
    private final PeriodicRunner periodicRunner = new PeriodicRunner();
    private final LynxBulkCacheManager bulkCacheManager;

    // --- FTC plumbing ---
    private final LinearOpMode ftcRobot;
    private final OpModeType opModeType;
    private final AllianceColor allianceColor;
    private final StartPosition startPosition;
    private final GamepadController gamepad1;
    private final GamepadController gamepad2;
    private final GamepadInputs gamepadInputs;
    private final HardwareMap hardwareMap;
    private final Telemetry telemetry;

    // --- Actions (RoadRunner) ---
    private List<Action> runningActions = new ArrayList<>();

    // --- Lifecycle flags ---
    private boolean hasInitializedTimeStartOfOpMode = false;
    private boolean started = false;

    // --- NEW: Subsystem registry ---
    private final List<Subsystem> subsystems = new ArrayList<>();

    /**
     * Create a robot base with the given opmode, alliance, and start position.
     */
    protected RobotBase(LinearOpMode ftcRobot, OpModeType opModeType,
                        AllianceColor allianceColor,
                        StartPosition startPosition) {

        // Save opmode parameters
        this.ftcRobot = ftcRobot;
        this.opModeType = opModeType;
        this.allianceColor = allianceColor;
        this.startPosition = startPosition;

        // Common FTC objects
        this.hardwareMap = ftcRobot.hardwareMap;
        this.telemetry = ftcRobot.telemetry;
        this.gamepadInputs = new GamepadInputs();
        this.gamepad1 = new GamepadController(ftcRobot.gamepad1, gamepadInputs, getPeriodicRunner());
        this.gamepad2 = new GamepadController(ftcRobot.gamepad2, gamepadInputs, getPeriodicRunner());

        // Bulk cache manager set early (before hardware init inside initRobot()).
        this.bulkCacheManager = new LynxBulkCacheManager(
                hardwareMap, LynxModule.BulkCachingMode.MANUAL, periodicRunner);
    }

    // -------------------- Subsystem registry (NEW) --------------------

    /**
     * Register a subsystem. Call from {@link #initRobot()} after construction.
     *
     * @throws IllegalStateException if called after the OpMode has started.
     */
    protected final void registerSubsystem(Subsystem s) {
        if (started) {
            throw new IllegalStateException("registerSubsystem() must be called before start");
        }
        if (s != null) subsystems.add(s);
    }

    /**
     * Register multiple subsystems.
     */
    protected final void registerSubsystems(Subsystem... ss) {
        if (ss == null) return;
        for (Subsystem s : ss) registerSubsystem(s);
    }

    /**
     * Register a collection of subsystems.
     */
    protected final void registerSubsystems(Collection<? extends Subsystem> ss) {
        if (ss == null) return;
        for (Subsystem s : ss) registerSubsystem(s);
    }

    // -------------------- Main run loop --------------------

    /**
     * Call this from your OpMode's {@code runOpMode()}.
     */
    public final void runOpMode() {
        // Init phase
        initRobot();
        switch (opModeType) {
            case TELEOP:
                initTeleOp();
                break;
            case AUTONOMOUS:
                initAutonomous();
                break;
        }

        // Wait for start
        ftcRobot.waitForStart();
        started = true;

        // Robot-level enable first
        onEnable();

        // Mark start time and enable all subsystems once.
        saveTimeStartOfOpMode();
        for (Subsystem s : subsystems) {
            try {
                s.onEnable();
            } catch (Throwable t) { /* keep the robot alive */ }
        }

        // Periodic loop
        while (ftcRobot.opModeIsActive()) {
            resetTimePeriodic();                 // stamps clock, clamps dt if needed
            periodicRunner.runAllPeriodicRunnables(); // e.g., clear bulk cache

            // Mode-specific periodic (set targets, react to inputs)
            switch (opModeType) {
                case TELEOP:
                    onPeriodicTeleOp();
                    executeTeleOpActions();
                    break;
                case AUTONOMOUS:
                    onPeriodicAutonomous();
                    break;
            }

            // Subsystems push commands to hardware (consistent place each frame)
            for (Subsystem s : subsystems) {
                try {
                    s.update(clock);
                } catch (Throwable t) { /* keep running */ }
            }

            // Robot-wide periodic after subsystems
            onPeriodicRobot();

            // Throttle to fixed-ish period
            long wait = getMillisToRunPeriodicAgain();
            if (wait > 0) ftcRobot.sleep(wait);
        }

        // Shutdown phase — disable then stop subsystems

        // ...Robot-level disable first
        try {
            onDisable();
        } catch (Throwable t) { /* ignore */ }

        // ...Disable subsystems
        for (Subsystem s : subsystems) {
            try {
                s.onDisable();
            } catch (Throwable t) { /* ignore */ }
        }

        // ...Stop subsystems
        for (Subsystem s : subsystems) {
            try {
                s.stop();
            } catch (Throwable t) { /* ignore */ }
        }

        // Mode-specific exit
        switch (opModeType) {
            case TELEOP:
                exitTeleOp();
                break;
            case AUTONOMOUS:
                exitAutonomous();
                break;
        }
        exitRobot();
    }

    // -------------------- Timing helpers --------------------

    private void saveTimeStartOfOpMode() {
        hasInitializedTimeStartOfOpMode = true;
        timeStartOfOpMode.reset();
    }

    private void resetTimePeriodic() {
        timeLastPeriodic.reset();
        clock.beginFrame();
    }

    private long getMillisToRunPeriodicAgain() {
        long remain = INTERVAL_MS_PERIODIC - timeLastPeriodic.getElapsedMilliseconds();
        return Math.max(remain, 0);
    }

    // -------------------- Actions (RoadRunner) --------------------

    /**
     * Enqueue a RoadRunner {@link Action} to be driven during TeleOp.
     */
    public void addTeleOpAction(Action action) {
        if (action != null) runningActions.add(action);
    }

    /**
     * Drive queued {@link Action}s; called each TeleOp loop.
     */
    private void executeTeleOpActions() {
        TelemetryPacket packet = new TelemetryPacket();
        List<Action> next = new ArrayList<>();
        for (Action a : runningActions) {
            a.preview(packet.fieldOverlay());
            if (a.run(packet)) next.add(a);
        }
        runningActions = next;
        dash.sendTelemetryPacket(packet);
    }

    // -------------------- Accessors --------------------

    public LoopClock getClock() {
        return clock;
    }

    public AllianceColor getAllianceColor() {
        return allianceColor;
    }

    public StartPosition getStartPosition() {
        return startPosition;
    }

    public GamepadController getGamepad1() {
        return gamepad1;
    }

    public GamepadController getGamepad2() {
        return gamepad2;
    }

    public GamepadInputs getGamepadInputs() {
        return gamepadInputs;
    }

    public HardwareMap getHardwareMap() {
        return hardwareMap;
    }

    public Telemetry getTelemetry() {
        return telemetry;
    }

    public RobotStateHistory<C> getRobotStateHistory() {
        return robotStateHistory;
    }

    public PeriodicRunner getPeriodicRunner() {
        return periodicRunner;
    }

    public double getSecondsSinceStartOfOpMode() {
        return hasInitializedTimeStartOfOpMode ? timeStartOfOpMode.getElapsedSeconds() : 0.0;
    }

    public void sleep(long ms) {
        ftcRobot.sleep(ms);
    }

    // -------------------- Abstract lifecycle --------------------

    /**
     * Called once before {@link #initTeleOp()} or {@link #initAutonomous()}.
     */
    protected abstract void initRobot();

    /**
     * Called once right after waitForStart(), before subsystems onEnable().
     */
    protected void onEnable() {
    }

    /**
     * Called every loop (both modes) after subsystems update.
     */
    protected abstract void onPeriodicRobot();

    /**
     * Called once right after the loop ends, before subsystems onDisable().
     */
    protected void onDisable() {
    }

    /**
     * Called once at the very end after mode-specific exits.
     */
    protected abstract void exitRobot();

    /**
     * Called once after {@link #initRobot()} for Autonomous.
     */
    protected abstract void initAutonomous();

    /**
     * Called each loop during Autonomous.
     */
    protected abstract void onPeriodicAutonomous();

    /**
     * Called once when exiting Autonomous.
     */
    protected abstract void exitAutonomous();

    /**
     * Called once after {@link #initRobot()} for TeleOp.
     */
    protected abstract void initTeleOp();

    /**
     * Called each loop during TeleOp (set setpoints/commands here).
     */
    protected abstract void onPeriodicTeleOp();

    /**
     * Called once when exiting TeleOp.
     */
    protected abstract void exitTeleOp();

    // -------------------- Enums --------------------

    public enum OpModeType {TELEOP, AUTONOMOUS}

    public enum AllianceColor {RED, BLUE}

    public enum StartPosition {NEAR_TO_AUDIENCE, AWAY_FROM_AUDIENCE}
}

package edu.ftcphoenix.fw.robotbase;

import com.acmerobotics.dashboard.FtcDashboard;
import com.acmerobotics.dashboard.telemetry.TelemetryPacket;
import com.acmerobotics.roadrunner.Action;
import com.qualcomm.hardware.lynx.LynxModule;
import com.qualcomm.robotcore.eventloop.opmode.LinearOpMode;
import com.qualcomm.robotcore.hardware.HardwareMap;

import org.firstinspires.ftc.robotcore.external.Telemetry;

import java.util.ArrayList;
import java.util.List;

import edu.ftcphoenix.fw.gamepad.GamepadController;
import edu.ftcphoenix.fw.gamepad.GamepadInputs;
import edu.ftcphoenix.fw.hardware.LynxBulkCacheManager;
import edu.ftcphoenix.fw.robotbase.periodicrunner.PeriodicRunner;
import edu.ftcphoenix.fw.robotbase.statehistory.RobotStateHistory;
import edu.ftcphoenix.fw.util.ElapsedTimeMillis;

/**
 * The main Robot class should be derived from this, and this has structured methods
 * like init..(), onPeriodic...(), and exit() that should be implemented by the
 * derived class.
 * <p>
 * The derived class should have:
 * 1) Instances of all the subsystems
 * 2) Instances of the teleop and autonomous controllers
 * 3) State information that the controllers modify.
 *
 * @param <C> Enum defining the various components of the robot.
 */
public abstract class RobotBase<C extends Enum<C>> {
    private final int INTERVAL_MS_PERIODIC = 20;
    private final int NUM_COMPONENT_STATE_HISTORY_ITEMS = 500;
    /**
     * The amount of time elapsed since the start of the current op-mode.
     */
    private final ElapsedTimeMillis timeStartOfOpMode = new ElapsedTimeMillis();
    private final ElapsedTimeMillis timeLastPeriodic = new ElapsedTimeMillis();
    private long timePeriodicStartNanoseconds;
    private final FtcDashboard dash = FtcDashboard.getInstance();
    private final RobotStateHistory<C> robotStateHistory = new RobotStateHistory<>(NUM_COMPONENT_STATE_HISTORY_ITEMS);
    private final PeriodicRunner periodicRunner = new PeriodicRunner();
    /**
     * Reference to the FTC robot.  This is provided by the FTC op-mode robot.
     */
    private final LinearOpMode ftcRobot;
    /**
     * The op-mode being run right now.  This is provided by the FTC op-mode robot.
     */
    private final OpModeType opModeType;
    /**
     * The alliance color for the match.  This is provided by the FTC op-mode robot.
     */
    private final AllianceColor allianceColor;
    /**
     * The starting position with respect to the audience.  This is provided by the FTC op-mode
     * robot.
     */
    private final StartPosition startPosition;
    /**
     * Gamepad 1 controller.  This is set when the robot is initialized.
     */
    private final GamepadController gamepad1;
    /**
     * Gamepad 2 controller.  This is set when the robot is initialized.
     */
    private final GamepadController gamepad2;
    /**
     * The collection of inputs being created from the gamepad controllers.  This is set when the
     * robot is initialized.
     */
    private final GamepadInputs gamepadInputs;
    /**
     * The hardware map to use to access devices.  This is set when the robot is initialized.
     */
    private final HardwareMap hardwareMap;
    /**
     * The telemetry object to use to output info.  This is set when the robot is initialized.
     */
    private final Telemetry telemetry;
    /**
     * The Lynx's bulk cache manager for the controller hub.  This will manage the
     * cache clearing, if required and based on the mode selected.  This is set when the robot
     * is initialized.
     * <p/>
     * TODO: If we use an expansion hub, this requires another cache manager.  Allow for it.
     */
    private final LynxBulkCacheManager bulkCacheManager;

    private boolean hasInitializedTimeStartOfOpMode = false;
    private List<Action> runningActions = new ArrayList<>();


    /**
     * Create a new ftcRobot that is made for a specific set of starting conditions.
     *
     * @param ftcRobot      The {@link LinearOpMode} ftcRobot making the call -- i.e. pass "this"
     * @param opModeType    The op mode of the ftcRobot making the call.
     * @param allianceColor The alliance color for this match
     * @param startPosition The starting position of the ftcRobot for this match
     */
    protected RobotBase(LinearOpMode ftcRobot, OpModeType opModeType,
                        AllianceColor allianceColor,
                        StartPosition startPosition) {

        // Save the ftcRobot, opmode type, alliance color and start position
        this.ftcRobot = ftcRobot;
        this.opModeType = opModeType;
        this.allianceColor = allianceColor;
        this.startPosition = startPosition;

        // Save the gamepad, hardware map, and telemetry
        hardwareMap = ftcRobot.hardwareMap;
        gamepadInputs = new GamepadInputs();
        gamepad1 = new GamepadController(ftcRobot.gamepad1, gamepadInputs, getPeriodicRunner());
        gamepad2 = new GamepadController(ftcRobot.gamepad2, gamepadInputs, getPeriodicRunner());
        telemetry = ftcRobot.telemetry;

        // Set the ftcRobot to bulk read and clear cache manually.
        //
        //    Note: This has to happen before initializing the hardware, which will probably
        //       happen in initRobot() below.
        bulkCacheManager = new LynxBulkCacheManager(hardwareMap, LynxModule.BulkCachingMode.MANUAL,
                periodicRunner);
    }

    private void saveTimeStartOfOpMode() {
        hasInitializedTimeStartOfOpMode = true;
        timeStartOfOpMode.reset();
    }

    private void resetTimePeriodic() {
        timeLastPeriodic.reset();
        timePeriodicStartNanoseconds = System.nanoTime();
    }

    /**
     * Get the time remaining (in milliseconds) until the next periodic loop.  The
     * periodic methods run every interval as defined by {@link #INTERVAL_MS_PERIODIC}.
     *
     * @return Milliseconds till next periodic loop.
     */
    private long getMillisToRunPeriodicAgain() {
        long timeRemaining = INTERVAL_MS_PERIODIC - timeLastPeriodic.getElapsedMilliseconds();
        if (timeRemaining > 0)
            return timeRemaining;
        return 0;
    }

    public long getTimePeriodicStartNanoseconds() {
        return timePeriodicStartNanoseconds;
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
        // If we have not initialized the timer yet, just say no time has elapsed
        //    since the start of the current op-mode.
        if (!hasInitializedTimeStartOfOpMode)
            return 0;

        // Return the time elapsed since start of game
        return timeStartOfOpMode.getElapsedSeconds();
    }

    /**
     * Call this method from runOpMode() in the main {@link LinearOpMode} ftcRobot file. This will in
     * turn call the appropriate init(), onPeriodic(), and exit() methods for the ftcRobot and
     * specified op modes.
     */
    public void runOpMode() {

        // Run the init() functions
        initRobot();
        switch (opModeType) {
            case TELEOP:
                initTeleOp();
                break;
            case AUTONOMOUS:
                initAutonomous();
                break;
        }

        // Wait for the game to start (driver presses PLAY)
        ftcRobot.waitForStart();

        // Begin the start-of-game timer and start the periodic timer.
        saveTimeStartOfOpMode();

        // Execute the periodic commands as long as the mode is active.
        while (ftcRobot.opModeIsActive()) {
            // Remember when the periodic run occurred.
            resetTimePeriodic();

            // Run the onPeriodic() methods
            // ...Execute the methods registered with the PeriodicRunner
            getPeriodicRunner().runAllPeriodicRunnables();

            // ...Execute the op-mode-specific periodic method.
            switch (opModeType) {
                case TELEOP:
                    onPeriodicTeleOp();

                    // Execute any running actions that have been queued by the onPeriodicTeleOp()
                    //    method.
                    executeTeleOpActions();
                    break;
                case AUTONOMOUS:
                    onPeriodicAutonomous();
                    break;
            }

            // ...Execute the robot's periodic method.
            onPeriodicRobot();

            // Update telemetry each cycle.
            telemetry.update();

            // Ensure that sufficient time has elapsed before the next periodic run.
            long timeRemaining = getMillisToRunPeriodicAgain();
            if (timeRemaining > 0)
                ftcRobot.sleep(timeRemaining);
        }

        //  all the exit() functions
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

    /**
     * This is called when the ftcRobot is started.  Use to initialize anything common to
     * both op modes.
     */
    protected abstract void initRobot();

    /**
     * This is called periodically regardless of op mode, but only after the corresponding
     * op mode function is called.
     */
    protected abstract void onPeriodicRobot();

    /**
     * This is called when exiting the ftcRobot, after the exit of the specific op mode.
     */
    protected abstract void exitRobot();

    /**
     * Initialize the ftcRobot for autonomous mode.  This is called after {@link #initRobot()} is
     * called.
     */
    protected abstract void initAutonomous();

    /**
     * This is called periodically during autonomous opmode.
     */
    protected abstract void onPeriodicAutonomous();

    /**
     * This is called when exiting autonomous mode.
     */
    protected abstract void exitAutonomous();

    /**
     * Initialize the ftcRobot for teleop mode.  This is called after {@link #initRobot()} is
     * called.
     */
    protected abstract void initTeleOp();

    /**
     * This is called periodically during teleop mode.
     */
    protected abstract void onPeriodicTeleOp();

    /**
     * This is called when exiting tele-op mode.
     */
    protected abstract void exitTeleOp();

    /**
     * Enqueue {@link Action}s during tele-op mode ({@link #onPeriodicTeleOp()})
     * that should be executed.
     *
     * @param action Action to enqueue.
     * @see #executeTeleOpActions()
     */
    // TODO: Add a way to cancel actions when the inserting state is exited or if the same
    //       subsystem is used by another action.
    public void addTeleOpAction(Action action) {
        runningActions.add(action);
    }

    public void sleep(long milliSeconds) {
        ftcRobot.sleep(milliSeconds);
    }

    /**
     * Execute {@link Action}s that are queued during {@link #onPeriodicTeleOp()}.
     *
     * @see #addTeleOpAction(Action)
     */
    private void executeTeleOpActions() {
        TelemetryPacket packet = new TelemetryPacket();

        // update running actions
        List<Action> newActions = new ArrayList<>();
        for (Action action : runningActions) {
            action.preview(packet.fieldOverlay());
            if (action.run(packet)) {
                newActions.add(action);
            }
        }
        runningActions = newActions;

        dash.sendTelemetryPacket(packet);
    }


    /**
     * The different op modes in FTC.
     */
    public enum OpModeType {
        TELEOP,
        AUTONOMOUS
    }

    /**
     * The alliance for this match.
     */
    public enum AllianceColor {
        RED,
        BLUE
    }

    /**
     * What is the starting position of the ftcRobot with respect to the audience
     */
    public enum StartPosition {
        NEAR_TO_AUDIENCE,
        AWAY_FROM_AUDIENCE
    }
}

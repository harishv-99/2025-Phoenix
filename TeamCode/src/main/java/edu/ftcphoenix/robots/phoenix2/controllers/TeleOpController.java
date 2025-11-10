package edu.ftcphoenix.robots.phoenix2.controllers;

import java.util.Collections;
import java.util.List;
import java.util.function.DoubleSupplier;

import edu.ftcphoenix.fw2.core.FrameClock;
import edu.ftcphoenix.fw2.drive.DriveSignal;
import edu.ftcphoenix.fw2.drive.DriveSource;
import edu.ftcphoenix.fw2.drive.source.GamepadSource;
import edu.ftcphoenix.fw2.drivegraph.BranchSpec;
import edu.ftcphoenix.fw2.drivegraph.DriveGraph;
import edu.ftcphoenix.fw2.drivegraph.DriveGraphOptions;
import edu.ftcphoenix.fw2.gamepad.GamepadInputs;
import edu.ftcphoenix.fw2.gamepad.GamepadKeys;
import edu.ftcphoenix.fw2.gamepad.rev.GamepadController;
import edu.ftcphoenix.robots.phoenix2.Constants;
import edu.ftcphoenix.robots.phoenix2.Robot;

/**
 * TeleOpController — wires driver inputs, DriveGraph, and mechanisms.
 *
 * <p>Loop philosophy:</p>
 * <ol>
 *   <li>Read inputs and set live targets/toggles.</li>
 *   <li>Let DriveTrainSubsystem pull from a single DriveSource during its update(clock).</li>
 *   <li>Service mechanism controls.</li>
 * </ol>
 */
public final class TeleOpController {

    private final Robot robot;
    private final GamepadController gp1;
    private final GamepadController gp2;

    private DriveGraph.Result graph;
    private DriveSource driver;

    public TeleOpController(Robot robot) {
        this.robot = robot;
        this.gp1 = robot.getGamepad1();
        this.gp2 = robot.getGamepad2();

        createGamepadControls();
        buildDriveGraph(); // installs graph.source into the drivetrain
    }

    public void onPeriodicTeleOp() {
        // RobotBase already did clock.beginFrame()
        FrameClock clock = robot.getClock();
        GamepadInputs gpIn = robot.getGamepadInputs();

        // --- DRIVETRAIN ---
        DriveSignal cmd = graph.source.get(clock);
        // robot.driveTrainSubsystem.drive(cmd);

        // --- DEBUG / DRIVER HUD ---
        if (gpIn.getButton(Constants.BUTTON_NAME_DEBUG).wasJustPressed()) {
            // Core drive signal preview
            robot.getTelemetry().addData("lat", cmd.lateral());
            robot.getTelemetry().addData("ax",  cmd.axial());
            robot.getTelemetry().addData("om",  cmd.omega());

            // Add drivetrain + shooter subsystem telemetry (verbose=true)
            robot.driveTrainSubsystem.addTelemetry(robot.getTelemetry(), "drive", true);
            robot.shooterSubsystem.addTelemetry(robot.getTelemetry(), "shooter", true);

            // Optional: include gamepad debug & any extra points of interest
            robot.getGamepad1().addTelemetry(robot.getTelemetry());
            robot.getTelemetry().update();
        }

        // --- TRANSFER ---
        if (gpIn.getButton(Constants.BUTTON_NAME_TRANSFER_FORWARD).isDown()) {
            robot.shooterSubsystem.setTransferPower(1.0);
        } else if (gpIn.getButton(Constants.BUTTON_NAME_TRANSFER_BACKWARD).isDown()) {
            robot.shooterSubsystem.setTransferPower(-1.0);
        } else {
            robot.shooterSubsystem.setTransferPower(0.0);
        }

        // --- PUSHER ---
        if (gpIn.getButton(Constants.BUTTON_NAME_PUSHER_FORWARD).wasJustPressed()) {
            robot.shooterSubsystem.setPusherPos(1.0);
        } else if (gpIn.getButton(Constants.BUTTON_NAME_PUSHER_BACKWARD).wasJustPressed()) {
            robot.shooterSubsystem.setPusherPos(0.0);
        }

        // --- LOADER ---
        if (gpIn.getButton(Constants.BUTTON_NAME_LOAD_ON).wasJustPressed()) {
            robot.shooterSubsystem.startLoad();
        } else if (gpIn.getButton(Constants.BUTTON_NAME_LOAD_OFF).wasJustPressed()) {
            robot.shooterSubsystem.stopLoad();
        }

        // --- SHOOTER RPM (live controls) ---
        if (gpIn.getButton(Constants.BUTTON_NAME_SHOOTER_ON).wasJustPressed()) {
            robot.shooterSubsystem.resumeRpm();
            robot.getTelemetry().addData("Shooter target", robot.shooterSubsystem.getShooterTargetRpm());
            robot.getTelemetry().update();
        } else if (gpIn.getButton(Constants.BUTTON_NAME_SHOOTER_OFF).wasJustPressed()) {
            robot.shooterSubsystem.setShooterRpm(0.0);
        } else if (gpIn.getButton(Constants.BUTTON_NAME_SHOOTER_RPM_INCREASE).wasJustPressed()) {
            double rpm = Math.max(robot.shooterSubsystem.getShooterTargetRpm() + 200, 0.0);
            robot.shooterSubsystem.setShooterRpm(rpm);
            robot.getTelemetry().addData("Shooter target", rpm);
            robot.getTelemetry().update();
        } else if (gpIn.getButton(Constants.BUTTON_NAME_SHOOTER_RPM_DECREASE).wasJustPressed()) {
            double rpm = Math.max(robot.shooterSubsystem.getShooterTargetRpm() - 200, 0.0);
            robot.shooterSubsystem.setShooterRpm(rpm);
            robot.getTelemetry().addData("Shooter target", rpm);
            robot.getTelemetry().update();
        }

        // --- SUBSYSTEM UPDATES ---
        robot.shooterSubsystem.update(clock);
    }

    private void createGamepadControls() {
        // Driver axes
        gp1.createIntervalDoubleUnit(Constants.INTERVAL_NAME_LATERAL, GamepadKeys.Stick.LEFT_STICK_X);
        gp1.createIntervalDoubleUnit(Constants.INTERVAL_NAME_AXIAL, GamepadKeys.Stick.LEFT_STICK_Y);
        gp1.createIntervalDoubleUnit(Constants.INTERVAL_NAME_ANGULAR, GamepadKeys.Stick.RIGHT_STICK_X);

        // Debug
        gp1.createButton(Constants.BUTTON_NAME_DEBUG, GamepadKeys.Trigger.LEFT_TRIGGER, 0.8);

        // Precision + omega fine (live)
        gp1.createButton(Constants.BUTTON_NAME_PRECISION, GamepadKeys.Button.RIGHT_BUMPER);
        gp1.createButton(Constants.BUTTON_NAME_OMEGA_FINE, GamepadKeys.Trigger.LEFT_TRIGGER, 0.5);

        // Operator
        gp2.createButton(Constants.BUTTON_NAME_TRANSFER_FORWARD, GamepadKeys.Button.B);
        gp2.createButton(Constants.BUTTON_NAME_TRANSFER_BACKWARD, GamepadKeys.Button.X);
        gp2.createButton(Constants.BUTTON_NAME_PUSHER_FORWARD, GamepadKeys.Button.Y);
        gp2.createButton(Constants.BUTTON_NAME_PUSHER_BACKWARD, GamepadKeys.Button.A);
        gp2.createButton(Constants.BUTTON_NAME_SHOOTER_ON, GamepadKeys.Button.LEFT_BUMPER);
        gp2.createButton(Constants.BUTTON_NAME_SHOOTER_OFF, GamepadKeys.Button.RIGHT_BUMPER);
        gp2.createButton(Constants.BUTTON_NAME_SHOOTER_RPM_INCREASE, GamepadKeys.Button.DPAD_UP);
        gp2.createButton(Constants.BUTTON_NAME_SHOOTER_RPM_DECREASE, GamepadKeys.Button.DPAD_DOWN);
        gp2.createButton(Constants.BUTTON_NAME_LOAD_ON, GamepadKeys.Button.DPAD_LEFT);
        gp2.createButton(Constants.BUTTON_NAME_LOAD_OFF, GamepadKeys.Button.DPAD_RIGHT);
    }

    private void buildDriveGraph() {
        // Raw source
        driver = new GamepadSource(
                robot.getGamepadInputs(),
                Constants.INTERVAL_NAME_LATERAL,
                Constants.INTERVAL_NAME_AXIAL,
                Constants.INTERVAL_NAME_ANGULAR
        );

        // Live scales
        DoubleSupplier precisionScale = () ->
                robot.getGamepadInputs().getButton(Constants.BUTTON_NAME_PRECISION).isDown() ? 0.5 : 1.0;

        DoubleSupplier omegaFineScale = () ->
                robot.getGamepadInputs().getButton(Constants.BUTTON_NAME_OMEGA_FINE).isDown() ? 0.4 : 1.0;

        // Options
        DriveGraphOptions opt = new DriveGraphOptions();
        opt.dbLat = 0.05;
        opt.expoLat = 1.8;
        opt.slewLat = 3.0;
        opt.dbAx = 0.05;
        opt.expoAx = 1.8;
        opt.slewAx = 3.0;
        opt.dbOm = 0.05;
        opt.expoOm = 1.4;
        opt.slewOm = 6.0;

        opt.precisionScale = precisionScale;
        opt.omegaFineScale = omegaFineScale;

        opt.limLatAbs = () -> 1.0;
        opt.limAxAbs = () -> 1.0;
        opt.limOmAbs = () -> 1.0;
        opt.mixLimLat = () -> 1.0;
        opt.mixLimAx = () -> 1.0;
        opt.mixLimOm = () -> 1.0;

        List<BranchSpec> assists = Collections.emptyList();

        graph = DriveGraph.build(driver, opt, assists);
        robot.driveTrainSubsystem.setSource(graph.source);
    }
}

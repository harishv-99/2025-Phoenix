package edu.ftcphoenix.robots.phoenix.opmodes;

import com.qualcomm.robotcore.eventloop.opmode.OpMode;
import com.qualcomm.robotcore.eventloop.opmode.TeleOp;
import com.qualcomm.robotcore.hardware.DcMotor;
import com.qualcomm.robotcore.hardware.DcMotorEx;
import com.qualcomm.robotcore.hardware.IMU;

import edu.ftcphoenix.fw.util.LoopClock;

import edu.ftcphoenix.fw.flow.Lanes;
import edu.ftcphoenix.fw.flow.simple.SimpleLanedFlow;

import edu.ftcphoenix.fw.adapters.ftc.FtcAdapters;
import edu.ftcphoenix.fw.adapters.ftc.FtcImuYaw;

import edu.ftcphoenix.fw.hal.Motor;
import edu.ftcphoenix.fw.hal.ServoLike;
import edu.ftcphoenix.fw.hal.ImuYaw;

import edu.ftcphoenix.fw.drive.DriveSignal;
import edu.ftcphoenix.fw.drive.Drivebase;
import edu.ftcphoenix.fw.drive.MecanumConfig;
import edu.ftcphoenix.fw.drive.MecanumDrivebase;
import edu.ftcphoenix.fw.drive.source.FieldCentricDriveSource;
import edu.ftcphoenix.fw.drive.source.StickDriveSource;

import edu.ftcphoenix.fw.stages.intake.HumanPusherIntakeConfig;
import edu.ftcphoenix.fw.stages.intake.HumanPusherIntakeStage;
import edu.ftcphoenix.fw.stages.indexer.TransferTimedConfig;
import edu.ftcphoenix.fw.stages.indexer.TransferTimedStage;
import edu.ftcphoenix.fw.stages.shooter.ShooterConfig;
import edu.ftcphoenix.fw.stages.shooter.ShooterStage;

import edu.ftcphoenix.fw.routers.ButtonEdgeRouter;
import edu.ftcphoenix.fw.routers.AndRouter;

import edu.ftcphoenix.robots.phoenix.config.HwNames;
import edu.ftcphoenix.robots.phoenix.config.Tuning;

import java.util.function.BooleanSupplier;
import java.util.function.DoubleSupplier;

/**
 * Stage-driven TeleOp:
 * - RB (edge): ingest one piece (intake -> indexer).
 * - RT>0.5 (edge, gated by atSpeed): deliver one piece (indexer -> shooter).
 * - LB (edge): reject one piece upstream (indexer "reject" lane).
 * - D-pad: shooter target speed (rad/s). Right-stick press toggles enable.
 * - Drive: LS (axial/lateral), RS X (turn), LB (held) = slow mode.
 * - Field-centric: Y on, A off, X zeros heading.
 * <p>
 * Stages encapsulate timing/busy/count; gamepad events just open lanes.
 */
@TeleOp(name = "TeleOpPhoenix", group = "Phoenix")
public final class TeleOpPhoenix extends OpMode {

    // Loop timing
    private final LoopClock clock = new LoopClock();

    // Stages & flow
    private SimpleLanedFlow flow;
    private HumanPusherIntakeStage intake;
    private TransferTimedStage indexer;
    private ShooterStage shooter;
    private ShooterStage.Spooler spooler;

    // Drive
    private Drivebase drivebase;
    private StickDriveSource rcSource;
    private FieldCentricDriveSource fcSource;
    private ImuYaw imuYaw;
    private boolean fieldCentric = false;

    // Shooter controls
    private boolean shooterEnabled = false;
    private double shooterTargetRadPerSec = Tuning.SHOOTER_TARGET_RAD_PER_S;
    private boolean prevRS = false;  // edge for RS press

    // Local edge for reject
    private boolean prevLB = false;

    // Drive motor names (adjust if centralized elsewhere)
    private static final String DRIVE_FL = "lf", DRIVE_FR = "rf", DRIVE_BL = "lb", DRIVE_BR = "rb";

    @Override
    public void init() {
        clock.reset(0.0);

        // -------- HAL --------
        final ServoLike pusherServo = FtcAdapters.servoLike(hardwareMap, HwNames.SERVO_PUSHER, false);
        final Motor transfer = FtcAdapters.crServoMotor(hardwareMap, HwNames.MOTOR_INTAKE_TRANSPORT, false);

        final DcMotorEx shooterMotor = hardwareMap.get(DcMotorEx.class, HwNames.MOTOR_SHOOTER);
        shooterMotor.setMode(DcMotor.RunMode.RUN_USING_ENCODER);
        spooler = FtcAdapters.spooler(
                hardwareMap,
                HwNames.MOTOR_SHOOTER,
                Tuning.SHOOTER_TICKS_PER_REV_AT_WHEEL,
                Tuning.SHOOTER_AT_SPEED_TOL_RAD_PER_S,
                /*inverted*/ false
        );
        spooler.setTargetRadPerSec(0.0); // safe default

        // -------- Stages --------
        indexer = new TransferTimedStage(
                transfer,
                TransferTimedConfig.defaults() // capacity=3; one-pitch timings
        );

        intake = new HumanPusherIntakeStage(
                pusherServo,
                /*assist*/ null,
                /*entry sensor*/ null, // no sensors on this robot
                new HumanPusherIntakeStage.CountSupplier() {
                    public int get() {
                        return indexer.count();
                    }
                },
                new HumanPusherIntakeConfig(
                        Tuning.PUSH_HOME_BASE, Tuning.PUSH_HOME_STEP,
                        Tuning.PUSH_POS_BASE, Tuning.PUSH_POS_STEP,
                        Tuning.PUSH_FWD_SEC, Tuning.PUSH_RET_SEC,
                        /*feedPower*/ 0.0, /*rejectPower*/ -0.6, /*rejectEjectSec*/ 0.25
                )
        );

        // NOTE: 3-arg ShooterConfig: (targetRadPerSec, ingestWindowSec, recoverSec)
        shooter = new ShooterStage(
                spooler,
                new ShooterConfig(Tuning.SHOOTER_TARGET_RAD_PER_S, 0.25, 0.05),
                /*exit sensor*/ null
        );

        // -------- Flow (routers) --------
        // RB edge => one ingest: intake -> indexer (ACCEPT)
        ButtonEdgeRouter ingestOnce = new ButtonEdgeRouter(
                new BooleanSupplier() {
                    public boolean getAsBoolean() {
                        return gamepad1.right_bumper;
                    }
                },
                Lanes.ACCEPT
        );

        // RT edge => one deliver, gated by shooter at-speed: indexer -> shooter (ACCEPT)
        ButtonEdgeRouter deliverOnce = new ButtonEdgeRouter(
                new BooleanSupplier() {
                    public boolean getAsBoolean() {
                        return gamepad1.right_trigger > 0.5;
                    }
                },
                Lanes.ACCEPT
        );
        AndRouter deliverAtSpeed = new AndRouter(
                deliverOnce,
                new BooleanSupplier() {
                    public boolean getAsBoolean() {
                        return shooter.atSpeed();
                    }
                }
        );

        flow = new SimpleLanedFlow()
                .add(intake).add(indexer).add(shooter)
                .connect(intake, indexer, Lanes.ACCEPT, ingestOnce)
                .connect(indexer, shooter, Lanes.ACCEPT, deliverAtSpeed);
        // Reject is a local lane on indexer; we trigger it in loop() on LB.

        // -------- Drive --------
        final Motor fl = FtcAdapters.motor(hardwareMap, DRIVE_FL, false);
        final Motor fr = FtcAdapters.motor(hardwareMap, DRIVE_FR, false);
        final Motor bl = FtcAdapters.motor(hardwareMap, DRIVE_BL, false);
        final Motor br = FtcAdapters.motor(hardwareMap, DRIVE_BR, false);

        drivebase = new MecanumDrivebase(fl, fr, bl, br, MecanumConfig.defaults());

        final DoubleSupplier axial = new DoubleSupplier() {
            public double getAsDouble() {
                return -gamepad1.left_stick_y;
            }
        };
        final DoubleSupplier lateral = new DoubleSupplier() {
            public double getAsDouble() {
                return gamepad1.left_stick_x;
            }
        };
        final DoubleSupplier omega = new DoubleSupplier() {
            public double getAsDouble() {
                return gamepad1.right_stick_x;
            }
        };
        final BooleanSupplier slow = new BooleanSupplier() {
            public boolean getAsBoolean() {
                return gamepad1.left_bumper;
            }
        };

        rcSource = new StickDriveSource(axial, lateral, omega, slow, 0.05, 0.35, 4.0);

        imuYaw = new FtcImuYaw(hardwareMap.get(IMU.class, HwNames.IMU));
        imuYaw.zero();
        fcSource = new FieldCentricDriveSource(rcSource, imuYaw);
    }

    @Override
    public void loop() {
        clock.update(getRuntime());

        // ---- Drive ----
        DriveSignal sig = (fieldCentric ? fcSource.sample(clock) : rcSource.sample(clock));
        drivebase.drive(sig);
        drivebase.update(clock);

        // ---- Shooter target (manual) ----
        if (gamepad1.dpad_up) shooterTargetRadPerSec += 50.0;
        if (gamepad1.dpad_down) shooterTargetRadPerSec -= 50.0;
        if (gamepad1.dpad_right) shooterTargetRadPerSec = Tuning.SHOOTER_TARGET_RAD_PER_S;
        if (gamepad1.dpad_left) shooterTargetRadPerSec = 0.0;
        if (shooterTargetRadPerSec < 0) shooterTargetRadPerSec = 0.0;

        boolean rs = gamepad1.right_stick_button;
        if (rs && !prevRS) shooterEnabled = !shooterEnabled;
        prevRS = rs;

        spooler.setTargetRadPerSec(shooterEnabled ? shooterTargetRadPerSec : 0.0);

        // ---- Reject (edge): LB -> indexer.accept(REJECT) ----
        boolean lb = gamepad1.left_bumper;
        if (lb && !prevLB && indexer.canAccept(Lanes.REJECT)) {
            indexer.accept(Lanes.REJECT);
        }
        prevLB = lb;

        // ---- Flow update (routers read buttons; stages run timing/busy/count) ----
        flow.update(clock);

        // ---- Field-centric toggles ----
        if (gamepad1.y) fieldCentric = true;
        if (gamepad1.a) fieldCentric = false; // A reserved for FC toggle here
        if (gamepad1.x) imuYaw.zero();

        // ---- Telemetry ----
        telemetry.addData("dt", clock.dtSec());
        telemetry.addData("drive", fieldCentric ? "FC" : "RC");
        telemetry.addData("indexer", indexer.count() + " / " + indexer.capacity());
        telemetry.addData("shooter/enabled", shooterEnabled);
        telemetry.addData("shooter/target(rad/s)", shooterTargetRadPerSec);
        telemetry.addData("shooter/atSpeed", shooter.atSpeed());
        telemetry.update();
    }

    @Override
    public void stop() {
        if (drivebase != null) drivebase.stop();
        if (spooler != null) spooler.setTargetRadPerSec(0.0);
    }
}

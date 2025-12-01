package edu.ftcphoenix.robots.phoenix_subsystem.subsystem;

import com.qualcomm.robotcore.hardware.HardwareMap;

import edu.ftcphoenix.fw.actuation.Actuators;
import edu.ftcphoenix.fw.actuation.Plant;
import edu.ftcphoenix.fw.adapters.ftc.FtcHardware;
import edu.ftcphoenix.fw.actuation.Plants;
import edu.ftcphoenix.fw.hal.PositionOutput;
import edu.ftcphoenix.fw.hal.PowerOutput;
import edu.ftcphoenix.fw.input.Gamepads;
import edu.ftcphoenix.fw.robot.Subsystem;
import edu.ftcphoenix.fw.sensing.AprilTagObservation;
import edu.ftcphoenix.fw.debug.DebugSink;
import edu.ftcphoenix.fw.util.InterpolatingTable1D;
import edu.ftcphoenix.fw.util.LoopClock;

/**
 * P2 shooter subsystem: pusher, feeder, and dual-velocity shooter.
 * <p>
 * Shooter velocity is chosen based on distance to a scoring AprilTag.
 */
public final class ShooterSubsystem implements Subsystem {

    // --- Hardware names (edit to match your config) ---
    private static final String HW_PUSHER = "pusher";
    private static final String HW_FEED_LEFT = "transferLeft";
    private static final String HW_FEED_RIGHT = "transferRight";
    private static final String HW_SHOOT_LEFT = "shooterLeft";
    private static final String HW_SHOOT_RIGHT = "shooterRight";

    // --- Pusher positions ---
    private static final double PUSHER_RETRACT = 0.0;
    private static final double PUSHER_EXTEND = 1.0;

    // --- Feeder powers ---
    private static final double FEED_FORWARD = 1.0;
    private static final double FEED_REVERSE = -1.0;
    private static final double FEED_TRIGGER_THRESHOLD = 0.40;

    // --- Shooter + tags ---
    private static final double SHOOTER_TICKS_PER_REV = 28.0; // TODO: real value
    private static final double MAX_TAG_AGE_SEC = 0.30;

    // Distance (inches) → velocity (rad/s).
    // TODO: replace with your calibrated values.
    private static final double[] RANGE_INCHES = {
            24.0, 36.0, 48.0
    };

    private static final double[] VELOCITY_RAD_PER_SEC = {
            rpmToRadPerSec(3000.0),
            rpmToRadPerSec(3500.0),
            rpmToRadPerSec(4000.0)
    };

    private final Gamepads gamepads;
    private final VisionSubsystem vision;

    private final Plant pusher;
    private final Plant feeder;

    // Shooter uses velocity plant (rad/s) for two motors.
    private final Plant shooterPlant;
    private final InterpolatingTable1D rangeToVelocity;

    private double shooterTargetRadPerSec = 0.0;
    private boolean shooterEnabled = false;

    public ShooterSubsystem(HardwareMap hw,
                            Gamepads gamepads,
                            VisionSubsystem vision) {
        this.gamepads = gamepads;
        this.vision = vision;

        // Pusher positional servo
        this.pusher = Actuators.plant(hw)
                .servo(HW_PUSHER, false)
                .position()
                .build();

        // Feeder CR servos as motors
        this.feeder = Actuators.plant(hw)
                .crServoPair(HW_FEED_LEFT, false,
                    HW_FEED_RIGHT, false)
                .power()
                .build();

        // Shooter dual-velocity plant (rad/s target).
        this.shooterPlant = Actuators.plant(hw)
                .motorPair(HW_SHOOT_LEFT, false,
                        HW_SHOOT_RIGHT, true)
                .power()
                .build();

        // Interpolation table for distance → velocity.
        this.rangeToVelocity =
                InterpolatingTable1D.ofSorted(RANGE_INCHES, VELOCITY_RAD_PER_SEC);
    }

    private static double rpmToRadPerSec(double rpm) {
        return rpm * 2.0 * Math.PI / 60.0;
    }

    @Override
    public void onTeleopInit() {
        pusher.setTarget(PUSHER_RETRACT);
        setFeederPower(0.0);
        shooterTargetRadPerSec = 0.0;
        shooterEnabled = false;
        shooterPlant.setTarget(0.0);
    }

    @Override
    public void onTeleopLoop(LoopClock clock) {
        updatePusher();
        updateFeeder();
        updateShooter();

        // Drive shooter motors in velocity mode.
        shooterPlant.setTarget(shooterTargetRadPerSec);
        shooterPlant.update(clock.dtSec());

        addTelemetry();
    }

    @Override
    public void onStop() {
        setFeederPower(0.0);
        shooterPlant.setTarget(0.0);
        shooterPlant.update(0.0);
    }

    // --- Controls ---

    private void updatePusher() {
        // P2 A: extend; P2 B: retract.
        if (gamepads.p2().a().isHeld()) {
            pusher.setTarget(PUSHER_EXTEND);
        } else if (gamepads.p2().b().isHeld()) {
            pusher.setTarget(PUSHER_RETRACT);
        }
    }

    private void updateFeeder() {
        double lt = gamepads.p2().leftTrigger().get();
        double rt = gamepads.p2().rightTrigger().get();

        double power = 0.0;
        if (lt >= FEED_TRIGGER_THRESHOLD && rt < FEED_TRIGGER_THRESHOLD) {
            power = FEED_FORWARD;
        } else if (rt >= FEED_TRIGGER_THRESHOLD && lt < FEED_TRIGGER_THRESHOLD) {
            power = FEED_REVERSE;
        }

        setFeederPower(power);
    }

    private void updateShooter() {
        // P2 Y: while held, aim for distance-based shooter velocity.
        if (gamepads.p2().y().isHeld()) {
            shooterEnabled = true;
            shooterTargetRadPerSec = computeTargetVelocityFromRange();
        } else {
            shooterEnabled = false;
            shooterTargetRadPerSec = 0.0;
        }
    }

    private double computeTargetVelocityFromRange() {
        AprilTagObservation obs =
                vision.getBestScoringTag(MAX_TAG_AGE_SEC);

        if (!obs.hasTarget) {
            // Shooter has no fresh tag; target=0
            return 0.0;
        }

        double rangeIn = obs.rangeInches;
        double target = rangeToVelocity.interpolate(rangeIn);

        return target;
    }

    private void setFeederPower(double power) {
        feeder.setTarget(power);
    }

    private void addTelemetry() {

    }

    public void debugDump(DebugSink dbg, String prefix) {
        dbg.addData(prefix + ".enabled", shooterEnabled);
        dbg.addData(prefix + ".targetRadPerSec", "%.1f", shooterTargetRadPerSec);
        dbg.addData(prefix + ".pusher.targetPos", pusher.getTarget());
        dbg.addData(prefix + ".feeder.targetPower", feeder.getTarget());
    }
}

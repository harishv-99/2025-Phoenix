package edu.ftcphoenix.fw.adapters.ftc;

import com.qualcomm.robotcore.hardware.DcMotor;
import com.qualcomm.robotcore.hardware.DcMotorEx;

import edu.ftcphoenix.fw.stages.shooter.ShooterStage;

/**
 * FTC adapter for a flywheel "spooler" using a REV DcMotorEx encoder.
 * <p>
 * - Uses RUN_USING_ENCODER velocity control (ticks/second).
 * - Exposes speeds in rad/s at the wheel.
 * - atSpeed() compares |measured - target| <= tolerance.
 * <p>
 * Contract:
 * - target is clamped to >= 0 rad/s. Direction is handled via {@code inverted}.
 * - ticksPerRevAtWheel must reflect wheel-side ticks (include gear ratio).
 */
public final class FtcSpooler implements ShooterStage.Spooler {

    private static final double TWO_PI = 2.0 * Math.PI;

    private final DcMotorEx motor;
    private final double ticksPerRevAtWheel;
    private final double atSpeedTolRadPerSec;
    private final boolean inverted;

    // We persist the last commanded target (rad/s) for getTargetRadPerSec()
    private double targetRadPerSec = 0.0;

    public FtcSpooler(DcMotorEx motor,
                      double ticksPerRevAtWheel,
                      double atSpeedTolRadPerSec,
                      boolean inverted) {
        this.motor = motor;
        this.ticksPerRevAtWheel = Math.max(1e-6, ticksPerRevAtWheel);
        this.atSpeedTolRadPerSec = Math.max(0.0, atSpeedTolRadPerSec);
        this.inverted = inverted;

        // Ensure correct control mode
        try {
            motor.setMode(DcMotor.RunMode.RUN_USING_ENCODER);
        } catch (Throwable t) {
            // Ignore if already in a valid mode; teams sometimes set this elsewhere.
        }
    }

    @Override
    public void setTargetRadPerSec(double target) {
        if (target < 0) target = 0.0;
        this.targetRadPerSec = target;

        // Convert rad/s -> ticks/s and apply inversion as motor sign
        double ticksPerSec = (target / TWO_PI) * ticksPerRevAtWheel;
        if (inverted) ticksPerSec = -ticksPerSec;
        try {
            motor.setVelocity(ticksPerSec);
        } catch (Throwable t) {
            // Fallback: if setVelocity not available for some reason, approximate with power
            // NOTE: This is a last-resort; best results come from setVelocity.
            double approxPower = Math.min(1.0, Math.max(0.0, target / (TWO_PI * ticksPerRevAtWheel))); // crude
            motor.setPower(inverted ? -approxPower : approxPower);
        }
    }

    @Override
    public double getTargetRadPerSec() {
        return targetRadPerSec;
    }

    @Override
    public double getMeasuredRadPerSec() {
        // Motor reports ticks/second (signed). Convert to rad/s and return magnitude.
        double ticksPerSec = 0.0;
        try {
            ticksPerSec = motor.getVelocity();
        } catch (Throwable t) {
            // If getVelocity not available, approximate from power (very rough)
            // This keeps dashboards alive but isn't control-quality.
            double approxRps = Math.abs(motor.getPower()); // crude proxy
            return approxRps * TWO_PI;
        }
        double radPerSecSigned = (ticksPerSec * TWO_PI) / ticksPerRevAtWheel;
        // Report positive magnitude so atSpeed() compares magnitudes against non-negative target
        return Math.abs(radPerSecSigned);
    }

    @Override
    public boolean atSpeed() {
        // Compare magnitudes: both target and measured are >= 0 by construction
        return Math.abs(getMeasuredRadPerSec() - targetRadPerSec) <= atSpeedTolRadPerSec;
    }
}

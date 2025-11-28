package edu.ftcphoenix.fw.actuation;

import java.util.Objects;

import edu.ftcphoenix.fw.actuation.Plant;
import edu.ftcphoenix.fw.actuation.controller.*;
import edu.ftcphoenix.fw.debug.DebugSink;

/**
 * A {@link Plant} decorator that rate-limits how quickly the target can
 * change before it is applied to an underlying plant.
 *
 * <p>Think of this as:</p>
 *
 * <pre>{@code
 * // raw plant (no smoothing)
 * Plant rawShooter = FtcPlants.motorVelocity(hw, "shooter", false, TICKS_PER_REV);
 *
 * // rate-limited wrapper: target can only change 100 units/sec
 * Plant shooter = new RateLimitedPlant(rawShooter, 100.0);
 *
 * // now pass 'shooter' into higher-level controllers:
 * GoalController<ShooterMode> shooterCtrl =
 *     GoalController.forEnum(
 *         shooter,
 *         ShooterMode.class,
 *         ShooterMode.OFF,
 *         GoalController.target(ShooterMode.OFF,  0.0),
 *         GoalController.target(ShooterMode.LOW, 30.0),
 *         GoalController.target(ShooterMode.HIGH,45.0)
 *     );
 * }</pre>
 *
 * <p>Typical use cases:</p>
 * <ul>
 *   <li>Soft-starting intake or drive power to avoid brownouts.</li>
 *   <li>Slewing shooter velocity up/down instead of instant jumps.</li>
 *   <li>Limiting how quickly an arm position setpoint can change.</li>
 * </ul>
 *
 * <p><b>Composition:</b> because this implements {@link Plant}, you can pass
 * a {@code RateLimitedPlant} anywhere a plant is expected (e.g. into
 * {@link GoalController} or {@link FunctionController}).</p>
 */
public final class RateLimitedPlant implements Plant {

    private final Plant inner;

    /**
     * Maximum allowed increase per second (in plant units).
     */
    private final double maxUpPerSec;

    /**
     * Maximum allowed decrease per second (in plant units, positive).
     */
    private final double maxDownPerSec;

    /**
     * Last target value actually applied to the inner plant.
     */
    private double currentTarget;

    /**
     * Desired target as most recently set via {@link #setTarget(double)}.
     */
    private double desiredTarget;

    /**
     * Construct a {@link RateLimitedPlant} with separate up/down limits.
     *
     * @param inner         plant to wrap (non-null)
     * @param maxUpPerSec   max rate of increase (target units per second, must be >= 0)
     * @param maxDownPerSec max rate of decrease (target units per second, must be >= 0)
     */
    public RateLimitedPlant(Plant inner,
                            double maxUpPerSec,
                            double maxDownPerSec) {
        this.inner = Objects.requireNonNull(inner, "inner plant is required");
        if (maxUpPerSec < 0.0) {
            throw new IllegalArgumentException("maxUpPerSec must be >= 0");
        }
        if (maxDownPerSec < 0.0) {
            throw new IllegalArgumentException("maxDownPerSec must be >= 0");
        }
        this.maxUpPerSec = maxUpPerSec;
        this.maxDownPerSec = maxDownPerSec;

        double initial = inner.getTarget();
        this.currentTarget = initial;
        this.desiredTarget = initial;
    }

    /**
     * Construct a {@link RateLimitedPlant} with a symmetric rate limit for
     * both increases and decreases.
     *
     * @param inner          plant to wrap
     * @param maxDeltaPerSec maximum absolute rate of change per second
     *                       (target units per second, must be >= 0)
     */
    public RateLimitedPlant(Plant inner,
                            double maxDeltaPerSec) {
        this(inner, maxDeltaPerSec, maxDeltaPerSec);
    }

    // ---------------------------------------------------------------------
    // Plant implementation
    // ---------------------------------------------------------------------

    /**
     * Set the <b>desired</b> target. The actual command applied to the
     * underlying plant will move toward this value at a bounded rate on
     * subsequent calls to {@link #update(double)}.
     *
     * <p>This is cheap and may be called every loop.</p>
     */
    @Override
    public void setTarget(double target) {
        this.desiredTarget = target;
    }

    /**
     * @return the current commanded target after rate limiting (what is
     * actually being sent to the inner plant).
     */
    @Override
    public double getTarget() {
        return currentTarget;
    }

    /**
     * @return the desired target as most recently set via {@link #setTarget(double)}.
     */
    public double getDesiredTarget() {
        return desiredTarget;
    }

    @Override
    public void update(double dtSec) {
        if (dtSec < 0.0) {
            dtSec = 0.0;
        }

        double maxUp = maxUpPerSec * dtSec;
        double maxDown = maxDownPerSec * dtSec;

        double error = desiredTarget - currentTarget;

        double delta;
        if (error > 0.0) {
            // Increasing toward desiredTarget, limit by maxUp.
            delta = Math.min(error, maxUp);
        } else if (error < 0.0) {
            // Decreasing toward desiredTarget, limit by maxDown.
            double down = -error; // positive magnitude
            double limitedDown = Math.min(down, maxDown);
            delta = -limitedDown;
        } else {
            delta = 0.0;
        }

        currentTarget += delta;

        inner.setTarget(currentTarget);
        inner.update(dtSec);
    }

    @Override
    public void reset() {
        inner.reset();
        double t = inner.getTarget();
        currentTarget = t;
        desiredTarget = t;
    }

    /**
     * By default, we defer to the inner plant's notion of at-setpoint.
     *
     * <p>Note: this does <b>not</b> check that desired == current. If you want
     * a stricter definition ("rate limiter has also finished slewing"),
     * you can inspect {@link #getDesiredTarget()} and {@link #getTarget()}.</p>
     */
    @Override
    public boolean atSetpoint() {
        return inner.atSetpoint();
    }

    @Override
    public void debugDump(DebugSink dbg, String prefix) {
        if (dbg == null) return;
        String p = (prefix == null || prefix.isEmpty()) ? "rate" : prefix;
        dbg.addData(p + ".desiredTarget", desiredTarget)
                .addData(p + ".currentTarget", currentTarget)
                .addData(p + ".maxUpPerSec", maxUpPerSec)
                .addData(p + ".maxDownPerSec", maxDownPerSec);
        inner.debugDump(dbg, p + ".inner");
    }

    // ---------------------------------------------------------------------
    // Introspection
    // ---------------------------------------------------------------------

    /**
     * @return the wrapped inner plant.
     */
    public Plant getInner() {
        return inner;
    }

    public double getMaxUpPerSec() {
        return maxUpPerSec;
    }

    public double getMaxDownPerSec() {
        return maxDownPerSec;
    }
}

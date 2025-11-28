package edu.ftcphoenix.fw.actuation;

import java.util.Objects;
import java.util.function.BooleanSupplier;

import edu.ftcphoenix.fw.debug.DebugSink;

/**
 * A {@link Plant} decorator that gates commands based on a boolean condition.
 *
 * <p>Typical use cases:</p>
 * <ul>
 *   <li>Only allow an intake or feeder to run when a "safe to run" condition is true.</li>
 *   <li>Disable a mechanism when a limit switch is hit (and force a safe target).</li>
 *   <li>Global "kill switch" / safety interlock for a plant.</li>
 * </ul>
 *
 * <p>Conceptually:</p>
 *
 * <pre>{@code
 * Plant rawFeeder = FtcPlants.motorPower(hw, "feeder", false);
 *
 * // Feeder only runs when shooterReady.getAsBoolean() is true.
 * // Otherwise, it is held at 0 power.
 * Plant feeder = new InterlockPlant(
 *     rawFeeder,
 *     shooter::isReady,
 *     0.0   // blockedTarget
 * );
 *
 * // Now pass 'feeder' into a BufferController, GoalController, etc.
 * BufferController buffer =
 *     new BufferController(feeder, +1.0, -1.0, 0.0, 0.35, null);
 * }</pre>
 *
 * <p>Semantics:</p>
 * <ul>
 *   <li>{@link #setTarget(double)} sets the <b>desired target</b> for the plant.</li>
 *   <li>On each {@link #update(double)}:
 *     <ul>
 *       <li>If {@code condition.getAsBoolean()} is true:
 *         <ul>
 *           <li>The inner plant's target is set to the desired target.</li>
 *         </ul>
 *       </li>
 *       <li>Otherwise:
 *         <ul>
 *           <li>The inner plant's target is set to {@code blockedTarget}.</li>
 *         </ul>
 *       </li>
 *       <li>The inner plant's {@link Plant#update(double)} is then called.</li>
 *     </ul>
 *   </li>
 * </ul>
 *
 * <p>This is intentionally simple: interlocking is handled entirely here, and
 * higher-level controllers (GoalController, FunctionController, etc.) just see
 * a normal {@link Plant}.</p>
 */
public final class InterlockPlant implements Plant {

    private final Plant inner;
    private final BooleanSupplier condition;
    private final double blockedTarget;

    private double desiredTarget = 0.0;
    private double lastAppliedTarget = 0.0;

    /**
     * Construct an {@link InterlockPlant}.
     *
     * @param inner         underlying plant to wrap (non-null)
     * @param condition     gate condition; when true, desired targets are
     *                      allowed, when false, {@code blockedTarget} is used
     * @param blockedTarget target to apply when {@code condition} is false
     *                      (e.g., 0.0 power)
     */
    public InterlockPlant(Plant inner,
                          BooleanSupplier condition,
                          double blockedTarget) {
        this.inner = Objects.requireNonNull(inner, "inner plant is required");
        this.condition = Objects.requireNonNull(condition, "condition is required");
        this.blockedTarget = blockedTarget;
    }

    // ---------------------------------------------------------------------
    // Plant implementation
    // ---------------------------------------------------------------------

    /**
     * Set the desired target. Whether this actually reaches the underlying
     * plant depends on the interlock {@link #condition}.
     */
    @Override
    public void setTarget(double target) {
        this.desiredTarget = target;
    }

    /**
     * @return the last target value that was actually applied to the inner
     * plant (after interlocking), not necessarily the desired target.
     */
    @Override
    public double getTarget() {
        return lastAppliedTarget;
    }

    /**
     * @return the desired target as most recently set via {@link #setTarget(double)}.
     */
    public double getDesiredTarget() {
        return desiredTarget;
    }

    /**
     * Advance the interlock and underlying plant by {@code dtSec}.
     *
     * <p>If {@code condition.getAsBoolean()} is true, the underlying plant
     * receives the desired target. Otherwise, it receives {@code blockedTarget}.</p>
     */
    @Override
    public void update(double dtSec) {
        double applied = condition.getAsBoolean() ? desiredTarget : blockedTarget;
        lastAppliedTarget = applied;

        inner.setTarget(applied);
        inner.update(dtSec);
    }

    @Override
    public void reset() {
        inner.reset();
        // After reset, keep desiredTarget but recompute lastAppliedTarget on next update.
        lastAppliedTarget = inner.getTarget();
    }

    /**
     * By default, we defer to the inner plant's notion of at-setpoint.
     *
     * <p>Note that if the interlock is currently blocking (condition == false),
     * the inner plant will only be at-setpoint with respect to the
     * {@code blockedTarget}.</p>
     */
    @Override
    public boolean atSetpoint() {
        return inner.atSetpoint();
    }

    @Override
    public void debugDump(DebugSink dbg, String prefix) {
        if (dbg == null) return;
        String p = (prefix == null || prefix.isEmpty()) ? "interlock" : prefix;
        dbg.addData(p + ".desiredTarget", desiredTarget)
                .addData(p + ".lastAppliedTarget", lastAppliedTarget)
                .addData(p + ".blockedTarget", blockedTarget)
                .addData(p + ".condition", condition.getAsBoolean());
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

    /**
     * @return the configured blocked target (applied when condition is false).
     */
    public double getBlockedTarget() {
        return blockedTarget;
    }

    /**
     * @return the current value of the interlock condition.
     */
    public boolean isConditionTrue() {
        return condition.getAsBoolean();
    }
}

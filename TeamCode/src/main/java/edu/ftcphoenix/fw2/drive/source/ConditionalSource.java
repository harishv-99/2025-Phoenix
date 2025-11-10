package edu.ftcphoenix.fw2.drive.source;

import java.util.function.BooleanSupplier;

import edu.ftcphoenix.fw2.core.FrameClock;
import edu.ftcphoenix.fw2.drive.DriveSignal;
import edu.ftcphoenix.fw2.drive.DriveSource;

/**
 * Conditionally contributes an inner {@link DriveSource}.
 *
 * <p><b>Behavior:</b>
 * <ul>
 *   <li>If {@code enabled.getAsBoolean()} is <b>true</b>, returns {@code inner.get(clock)}.</li>
 *   <li>If <b>false</b>, returns {@link DriveSignal#ZERO} and <b>does not evaluate</b> the inner source
 *       (lazy skip: no compute and no state advancement inside the inner source).</li>
 * </ul>
 *
 * <p><b>Use when:</b>
 * <ul>
 *   <li>The inner source is <b>expensive</b> (vision, solver, trajectory follower) and you want to save compute while inactive.</li>
 *   <li>You need the inner source to <b>stop advancing internal state</b> while disabled (e.g., don’t integrate timers, PID histories, or consume frames).</li>
 *   <li>You want mixers (e.g., {@code DriveArbiter}) to see <b>no contribution</b> from this branch when disabled.</li>
 * </ul>
 *
 * <p><b>When <i>not</i> to use:</b>
 * <ul>
 *   <li>If you want the upstream branch to keep running but merely <b>mute the output</b>, use a
 *       final-stage <i>gate</i> filter (e.g., {@code s -> enabled ? s : DriveSignal.ZERO}) instead.</li>
 *   <li>If you’re toggling a single shaping step, wrap that <b>filter</b> with an {@code EnabledFilter}
 *       so it bypasses when disabled, rather than disabling the whole source.</li>
 * </ul>
 *
 * <p><b>Typical examples:</b>
 * <pre>
 * // Enable TagAim only while the button is held (saves compute and freezes its state):
 * DriveSource tagAimLive = new ConditionalSource(tagAimSource, () -> gamepad1.left_bumper);
 *
 * // In an arbiter:
 * DriveSource mixed = new DriveArbiter()
 *     .add(driverSource, 1.0)
 *     .add(tagAimLive,   1.0)   // contributes only when enabled
 *     .strategy(DriveArbiter.MixStrategy.WEIGHTED_SUM);
 * </pre>
 *
 * <p><b>API consistency:</b> decorator-style order is used:
 * <code>(inner, enabled)</code>.
 */
public final class ConditionalSource implements DriveSource {
    private final DriveSource inner;
    private final BooleanSupplier enabled;

    /**
     * @param inner   the wrapped source to evaluate when enabled
     * @param enabled live predicate; when false, returns {@link DriveSignal#ZERO} without calling inner
     */
    public ConditionalSource(DriveSource inner, BooleanSupplier enabled) {
        this.inner = inner;
        this.enabled = enabled;
    }

    @Override
    public DriveSignal get(FrameClock clock) {
        return enabled.getAsBoolean() ? inner.get(clock) : DriveSignal.ZERO;
    }
}

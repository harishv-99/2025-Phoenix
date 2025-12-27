package edu.ftcphoenix.fw.drive;

import edu.ftcphoenix.fw.core.debug.DebugSink;
import edu.ftcphoenix.fw.core.time.LoopClock;

import java.util.Objects;
import java.util.function.BooleanSupplier;

/**
 * Source of high-level drive commands for a drivetrain.
 *
 * <p>A {@link DriveSource} takes in the current loop timing (via {@link LoopClock}) and produces a
 * {@link DriveSignal} each loop.</p>
 *
 * <p>Typical implementations include:</p>
 * <ul>
 *   <li>{@code GamepadDriveSource} – map gamepad sticks to a drive signal.</li>
 *   <li>A motion planner – follow a trajectory and emit commands.</li>
 *   <li>A closed-loop heading controller – maintain or turn to a target angle.</li>
 * </ul>
 *
 * <h2>Semantics</h2>
 * <ul>
 *   <li>{@link #get(LoopClock)} is called once per loop by the OpMode (or owning robot class).</li>
 *   <li>Implementations may be stateless (pure function of current inputs) or stateful
 *       (e.g., with internal filters, rate limiters, etc.).</li>
 *   <li>The returned {@link DriveSignal} is typically expected to be in the range [-1, +1] per
 *       component when driving a normalized-power drivebase, but callers may clamp if needed.</li>
 * </ul>
 *
 * <h2>Composition helpers</h2>
 * <p>This interface provides a few default methods for simple composition, so that higher-level
 * code can build up complex behaviors by <em>wrapping</em> existing sources rather than creating
 * many small concrete classes.</p>
 *
 * <ul>
 *   <li>{@link #scaledWhen(BooleanSupplier, double, double)} – conditional slow mode with
 *       separate translation vs rotation scaling.</li>
 *   <li>{@link #scaled(double, double)} – unconditional scaling (useful for always-on “microdrive”).</li>
 *   <li>{@link #overlayWhen(BooleanSupplier, DriveOverlay, DriveOverlayMask)} – conditionally
 *       apply a {@link DriveOverlay} to override one or more components of this source.</li>
 *   <li>{@link #blendedWith(DriveSource, double)} – blend this source with another using
 *       {@link DriveSignal#lerp(DriveSignal, double)}.</li>
 * </ul>
 */
public interface DriveSource {

    /**
     * Produce a drive signal for the current loop.
     *
     * <p>Implementations should return a non-null {@link DriveSignal} every time they are called.
     * If a source has nothing to do (or is disabled), it should return {@link DriveSignal#zero()}.</p>
     *
     * @param clock loop timing helper; implementations may use this for dt-based smoothing,
     *              rate limiting, or controller updates
     * @return drive command for this loop (never null)
     */
    DriveSignal get(LoopClock clock);

    /**
     * Optional debug hook: emit a compact summary of this source's state.
     *
     * <p>The default implementation only records the implementing class name. Concrete sources are
     * encouraged to override this to include additional details such as last output, configuration
     * parameters, and any internal filter/controller state.</p>
     *
     * <p>Framework classes consistently follow the pattern that if {@code dbg} is {@code null}, the
     * method simply does nothing. This lets callers freely pass either a real sink or a
     * {@code NullDebugSink} (or {@code null}) without having to guard every call.</p>
     *
     * @param dbg    debug sink to write to (may be {@code null})
     * @param prefix key prefix for all entries (may be {@code null} or empty)
     */
    default void debugDump(DebugSink dbg, String prefix) {
        if (dbg == null) {
            return;
        }
        String p = (prefix == null || prefix.isEmpty()) ? "drive" : prefix;
        dbg.addData(p + ".class", getClass().getSimpleName());
    }

    /**
     * Return a new {@link DriveSource} that conditionally applies slow-mode scaling.
     *
     * <p>This wrapper is meant to match the Phoenix TeleOp conventions used by
     * {@link edu.ftcphoenix.fw.drive.source.GamepadDriveSource}: translation (axial/lateral) and
     * rotation (omega) are often slowed by different amounts.</p>
     *
     * <p>When {@code when} is {@code true}, the returned source produces:</p>
     * <ul>
     *   <li>{@code axial'}   = {@code axial}   × {@code translationScale}</li>
     *   <li>{@code lateral'} = {@code lateral} × {@code translationScale}</li>
     *   <li>{@code omega'}   = {@code omega}   × {@code omegaScale}</li>
     * </ul>
     *
     * <p>Typical scales are in (0, 1], but no clamping is performed here. If you need a strict
     * output range, call {@link DriveSignal#clamped()} at the point you send the command to a
     * drivebase.</p>
     *
     * <p>Example:</p>
     * <pre>{@code
     * DriveSource manual = GamepadDriveSource.teleOpMecanum(gamepads);
     * DriveSource slowable = manual.scaledWhen(
     *         () -> gamepads.p1().rightBumper().isPressed(),
     *         0.35,  // translation scale
     *         0.20); // omega scale
     * }</pre>
     *
     * @param when             condition indicating when the scales should be applied (non-null)
     * @param translationScale scale factor for axial/lateral (often in (0, 1])
     * @param omegaScale       scale factor for omega (often in (0, 1])
     * @return wrapped {@link DriveSource} that conditionally scales output
     */
    default DriveSource scaledWhen(BooleanSupplier when, double translationScale, double omegaScale) {
        Objects.requireNonNull(when, "when must not be null");

        // If both scales are 1.0, no need to wrap.
        if (translationScale == 1.0 && omegaScale == 1.0) {
            return this;
        }

        DriveSource self = this;
        return clock -> {
            DriveSignal base = self.get(clock);
            if (!when.getAsBoolean()) {
                return base;
            }
            return base.scaled(translationScale, omegaScale);
        };
    }

    /**
     * Return a new {@link DriveSource} that always scales translation and rotation.
     *
     * <p>This is the unconditional sibling of {@link #scaledWhen(BooleanSupplier, double, double)}.
     * It's useful for building “always slow” sources such as:</p>
     *
     * <ul>
     *   <li>driver 2 D-pad microdrive, or</li>
     *   <li>a low-speed autonomous nudge source.</li>
     * </ul>
     */
    default DriveSource scaled(double translationScale, double omegaScale) {
        // Reuse the implementation above without forcing callers to provide a dummy supplier.
        if (translationScale == 1.0 && omegaScale == 1.0) {
            return this;
        }
        DriveSource self = this;
        return clock -> self.get(clock).scaled(translationScale, omegaScale);
    }

    /**
     * Return a new {@link DriveSource} that conditionally applies a {@link DriveOverlay}.
     *
     * <p>When enabled, the overlay's output replaces the corresponding components of this source,
     * as selected by {@code requestedMask} <em>and</em> the overlay's dynamic mask.</p>
     *
     * <p>This is Phoenix's primary “override” mechanism and is intended to be used for both:
     * </p>
     * <ul>
     *   <li>driver assist (e.g., aim overlay overrides omega), and</li>
     *   <li>multi-driver arbitration (e.g., driver 2 overrides translation while driver 1 still
     *       controls everything else).</li>
     * </ul>
     *
     * <p>Example: auto-aim overrides rotation while holding a button:</p>
     * <pre>{@code
     * DriveSource manual = GamepadDriveSource.teleOpMecanum(pads);
     * DriveOverlay aim = DriveGuidance.plan()...build().overlay();
     * DriveSource assisted = manual.overlayWhen(
     *         () -> pads.p1().x().isPressed(),
     *         aim,
     *         DriveOverlayMask.ROTATION);
     * }</pre>
     */
    default DriveSource overlayWhen(BooleanSupplier when, DriveOverlay overlay, DriveOverlayMask requestedMask) {
        Objects.requireNonNull(when, "when must not be null");
        Objects.requireNonNull(overlay, "overlay must not be null");
        Objects.requireNonNull(requestedMask, "requestedMask must not be null");

        DriveSource self = this;

        return new DriveSource() {
            private boolean lastEnabled = false;

            @Override
            public DriveSignal get(LoopClock clock) {
                DriveSignal base = self.get(clock);

                boolean enabled = when.getAsBoolean();

                if (!enabled) {
                    if (lastEnabled) {
                        overlay.onDisable(clock);
                        lastEnabled = false;
                    }
                    return base;
                }

                if (!lastEnabled) {
                    overlay.onEnable(clock);
                    lastEnabled = true;
                }

                DriveOverlayOutput out = overlay.get(clock);
                if (out == null) {
                    // Be defensive; treat as “no override”.
                    return base;
                }

                DriveOverlayMask eff = out.mask.intersect(requestedMask);
                if (eff.isNone()) {
                    return base;
                }

                double axial = eff.axial ? out.signal.axial : base.axial;
                double lateral = eff.lateral ? out.signal.lateral : base.lateral;
                double omega = eff.omega ? out.signal.omega : base.omega;

                return new DriveSignal(axial, lateral, omega);
            }

            @Override
            public void debugDump(DebugSink dbg, String prefix) {
                if (dbg == null) {
                    return;
                }
                String p = (prefix == null || prefix.isEmpty()) ? "drive" : prefix;
                dbg.addData(p + ".class", getClass().getSimpleName());
                dbg.addData(p + ".overlay.enabled", lastEnabled);
                dbg.addData(p + ".overlay.requestedMask", requestedMask.toString());
                overlay.debugDump(dbg, p + ".overlay");
                self.debugDump(dbg, p + ".base");
            }
        };
    }

    /**
     * Convenience overload: requested mask defaults to {@link DriveOverlayMask#ALL}.
     */
    default DriveSource overlayWhen(BooleanSupplier when, DriveOverlay overlay) {
        return overlayWhen(when, overlay, DriveOverlayMask.ALL);
    }

    /**
     * Convenience overload: adapt a {@link DriveSource} into an overlay with the given mask.
     */
    default DriveSource overlayWhen(BooleanSupplier when, DriveSource override, DriveOverlayMask requestedMask) {
        return overlayWhen(when, DriveOverlays.fromDriveSource(override, requestedMask), requestedMask);
    }

    /**
     * Return a new {@link DriveSource} that blends this source with another.
     *
     * <p>The blend is performed using {@link DriveSignal#lerp(DriveSignal, double)} with a fixed
     * {@code alpha}:</p>
     * <ul>
     *   <li>{@code alpha = 0} → pure output of this source</li>
     *   <li>{@code alpha = 1} → pure output of {@code other}</li>
     *   <li>Values in-between produce a simple linear mix</li>
     * </ul>
     *
     * <p>This is useful for "driver-assist" behaviors, where you want to mix manual control with
     * an automatic behavior (e.g., auto-align) at some fixed strength.</p>
     *
     * @param other another {@link DriveSource} to blend with (non-null)
     * @param alpha blend factor in [0, 1] (values outside this range are clamped)
     * @return wrapped {@link DriveSource} that blends the two sources
     */
    default DriveSource blendedWith(DriveSource other, double alpha) {
        Objects.requireNonNull(other, "other DriveSource must not be null");
        final double alphaClamped = Math.max(0.0, Math.min(1.0, alpha));
        DriveSource self = this;
        return clock -> {
            DriveSignal a = self.get(clock);
            DriveSignal b = other.get(clock);
            return a.lerp(b, alphaClamped);
        };
    }
}

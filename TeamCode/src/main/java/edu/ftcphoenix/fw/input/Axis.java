package edu.ftcphoenix.fw.input;

import java.util.function.BooleanSupplier;
import java.util.function.DoubleSupplier;

import edu.ftcphoenix.fw.util.MathUtil;

/**
 * Continuous input in [-1..+1] or [0..1].
 *
 * <h2>What an Axis represents</h2>
 * <p>
 * {@code Axis} is a small abstraction for "something that returns a double each loop":
 * a stick, a trigger, a synthesized value from multiple buttons, etc.
 * </p>
 *
 * <p>
 * It is intentionally tiny:
 * </p>
 *
 * <ul>
 *   <li>{@link #get()} – sample the current value.</li>
 *   <li>Default helpers for deadband, scaling, clamping, etc.</li>
 *   <li>Static helpers to construct axes from raw suppliers and buttons.</li>
 * </ul>
 *
 * <h2>Typical usage</h2>
 *
 * <pre>{@code
 * Gamepads pads = Gamepads.create(gamepad1, gamepad2);
 * LoopClock clock = new LoopClock();
 *
 * void loop(double runtimeSec) {
 *     clock.update(runtimeSec);
 *     pads.update(clock); // updates registered button edges (idempotent by clock.cycle())
 *
 *     Axis lx = pads.p1().leftX();
 *     Axis ly = pads.p1().leftY();
 *
 *     double strafe = lx.get();
 *     double forward = -ly.get(); // if you prefer up = +1 in your math
 * }
 * }</pre>
 *
 * <h2>Why not just use doubles?</h2>
 *
 * <p>
 * The main benefits of {@code Axis} are:
 * </p>
 *
 * <ul>
 *   <li>It can be composed (deadband, scaling, signed-from-triggers).</li>
 *   <li>You can pass it around as "an input source" instead of wiring raw gamepad fields everywhere.</li>
 *   <li>You can build higher-level helpers on top (e.g. drive shaping) without caring where the data comes from.</li>
 * </ul>
 */
public interface Axis {

    /**
     * Sample the current value of this axis.
     *
     * <p>
     * The returned value is typically in [-1, +1] or [0, 1] depending on what
     * the axis represents (stick vs trigger), but this is not enforced; it is
     * acceptable for synthesized axes to use other ranges as long as callers
     * know what to expect.
     * </p>
     */
    double get();

    // ------------------------------------------------------------------------
    // Common per-axis transforms
    // ------------------------------------------------------------------------

    /**
     * Return a new {@link Axis} that is this axis clamped to a given range.
     *
     * <p>
     * This is a simple pass-through wrapper; it does not store any state.
     * </p>
     */
    default Axis clamped(double min, double max) {
        final Axis self = this;
        return () -> MathUtil.clamp(self.get(), min, max);
    }

    /**
     * Return a new {@link Axis} scaled by a constant factor.
     *
     * <p>
     * For example, to reduce a stick's sensitivity to 50%:
     * </p>
     *
     * <pre>{@code
     * Axis slow = pads.p1().leftY().scaled(0.5);
     * }</pre>
     */
    default Axis scaled(double factor) {
        final Axis self = this;
        return () -> self.get() * factor;
    }

    /**
     * Apply a deadband to this axis, returning a new axis.
     *
     * <p>
     * Values within {@code [-deadband, +deadband]} are treated as 0.0.
     * </p>
     *
     * <p>
     * This is useful for joystick center wobble or accidental small motions.
     * </p>
     */
    default Axis deadband(double deadband) {
        final Axis self = this;
        final double db = Math.abs(deadband);

        return () -> {
            double v = self.get();
            return (Math.abs(v) <= db) ? 0.0 : v;
        };
    }

    /**
     * Convert this axis into a {@link Button} by thresholding.
     *
     * <p>
     * This lets you use the <b>same method</b> for both "positive direction"
     * and "negative direction" checks on signed axes:
     * </p>
     *
     * <pre>{@code
     * Axis axial = player.leftY(); // [-1, +1]
     *
     * Button forward = axial.asButton(+0.5); // pressed when value >= +0.5
     * Button back    = axial.asButton(-0.5); // pressed when value <= -0.5
     * }</pre>
     *
     * <p>
     * For positive-only axes (e.g., triggers in [0, 1]), you would typically
     * use a positive threshold (e.g., {@code 0.5}). Passing a negative threshold
     * for such axes is allowed but not meaningful; it would almost always result
     * in the button being "not pressed".
     * </p>
     *
     * @param threshold cutoff value at or beyond which the button is "held";
     *                  positive thresholds use {@code >=}, negative thresholds
     *                  use {@code <=}
     * @return a stateful, registered {@link Button} view of this axis
     */
    default Button asButton(final double threshold) {
        final Axis self = this;
        final BooleanSupplier raw;
        if (threshold >= 0.0) {
            raw = () -> self.get() >= threshold;
        } else {
            raw = () -> self.get() <= threshold;
        }
        return Button.of(raw);
    }

    // ------------------------------------------------------------------------
    // Factories
    // ------------------------------------------------------------------------

    /**
     * Create an axis from a raw supplier.
     */
    static Axis of(DoubleSupplier raw) {
        return raw::getAsDouble;
    }

    /**
     * Create a signed axis from two buttons (negative and positive).
     *
     * <p>Returns:</p>
     * <ul>
     *   <li>-1 when {@code negative} is held and {@code positive} is not</li>
     *   <li>+1 when {@code positive} is held and {@code negative} is not</li>
     *   <li>0 when neither or both are held</li>
     * </ul>
     */
    static Axis signedFromButtons(Button negative, Button positive) {
        return () -> {
            boolean neg = negative != null && negative.isHeld();
            boolean pos = positive != null && positive.isHeld();
            if (neg == pos) return 0.0;
            return pos ? 1.0 : -1.0;
        };
    }

    /**
     * Create a [0..1] axis from a single button.
     *
     * <p>Returns 1 when held, otherwise 0.</p>
     */
    static Axis fromButton(Button button) {
        return () -> (button != null && button.isHeld()) ? 1.0 : 0.0;
    }
}

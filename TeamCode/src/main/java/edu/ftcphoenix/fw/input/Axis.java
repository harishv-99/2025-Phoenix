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
 * <ul>
 *   <li>For sticks, the natural range is typically [-1, +1].</li>
 *   <li>For triggers, the natural range is typically [0, 1].</li>
 *   <li>For axes built from buttons, the value is usually either 0 or ±1.</li>
 * </ul>
 *
 * <p>
 * Higher-level code (like drive sources) may assume that an axis is "roughly" in [-1, +1],
 * but all shaping code is defensive: values are clamped where appropriate so that misuse
 * leads to odd <em>feel</em>, not unsafe behavior.
 * </p>
 *
 * <h2>Responsibilities</h2>
 * <ul>
 *   <li>Expose a single {@link #get()} method that returns the current value.</li>
 *   <li>Offer small, composable helpers to build axes from other axes and buttons.</li>
 *   <li>Provide a convenient way to threshold an axis into a {@link Button}.</li>
 * </ul>
 */
public interface Axis {

    /**
     * Latest value of this axis.
     *
     * <p>
     * The nominal range is [-1, +1] for signed axes (sticks) or [0, 1] for
     * "positive-only" axes (triggers), but implementations may clamp or shape
     * as needed.
     * </p>
     */
    double get();

    // ------------------------------------------------------------------------
    // Axis → Button
    // ------------------------------------------------------------------------

    /**
     * Threshold this axis into a digital button without exposing any registry.
     *
     * <p>
     * Semantics depend on the sign of {@code threshold}:
     * </p>
     *
     * <ul>
     *   <li>If {@code threshold >= 0}, the button is pressed when
     *       {@code axis.get() >= threshold}.</li>
     *   <li>If {@code threshold < 0}, the button is pressed when
     *       {@code axis.get() <= threshold}.</li>
     * </ul>
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
     * @param threshold cutoff value at or beyond which the button is "pressed";
     *                  positive thresholds use {@code >=}, negative thresholds
     *                  use {@code <=}
     * @return a {@link Button} view of this axis
     */
    default Button asButton(final double threshold) {
        final Axis self = this;
        if (threshold >= 0.0) {
            // Positive threshold: pressed when value >= threshold.
            return new Button() {
                @Override
                public boolean isPressed() {
                    return self.get() >= threshold;
                }
            };
        } else {
            // Negative threshold: pressed when value <= threshold.
            return new Button() {
                @Override
                public boolean isPressed() {
                    return self.get() <= threshold;
                }
            };
        }
    }

    // ------------------------------------------------------------------------
    // Factory helpers
    // ------------------------------------------------------------------------

    /**
     * Factory for an {@link Axis} backed by a {@link DoubleSupplier}.
     *
     * <p>
     * This is the most generic way to adapt arbitrary numeric sources into
     * the {@code Axis} interface.
     * </p>
     *
     * @param supplier source of axis values; must not be {@code null}
     * @return an {@link Axis} that calls {@link DoubleSupplier#getAsDouble()}
     */
    static Axis of(final DoubleSupplier supplier) {
        if (supplier == null) {
            throw new IllegalArgumentException("DoubleSupplier is required");
        }
        return new Axis() {
            @Override
            public double get() {
                return supplier.getAsDouble();
            }
        };
    }

    /**
     * Create an axis that always returns a constant value.
     *
     * <p>
     * This can be useful for testing or for simple cases where you want a
     * fixed bias or offset.
     * </p>
     *
     * @param value constant value to return
     * @return an axis that always returns {@code value}
     */
    static Axis constant(final double value) {
        return new Axis() {
            @Override
            public double get() {
                return value;
            }
        };
    }

    /**
     * Create an axis from a {@link Button}, returning 1.0 when pressed and 0.0 otherwise.
     *
     * <p>
     * This is useful when you want to feed digital inputs into code that expects
     * an {@code Axis}, such as rate limiters or drive shaping.
     * </p>
     *
     * @param button source button; must not be {@code null}
     * @return axis that is 1.0 when the button is pressed, 0.0 otherwise
     */
    static Axis fromButton(final Button button) {
        if (button == null) {
            throw new IllegalArgumentException("Button is required");
        }
        return new Axis() {
            @Override
            public double get() {
                return button.isPressed() ? 1.0 : 0.0;
            }
        };
    }

    /**
     * Create an axis from a {@link BooleanSupplier}, returning 1.0 when the condition is
     * true and 0.0 otherwise.
     *
     * <p>
     * This is a convenience for simple lambdas, for example:
     * </p>
     *
     * <pre>{@code
     * Axis enabled = Axis.fromBoolean(() -> gamepad1.a);
     * }</pre>
     *
     * @param supplier boolean source; must not be {@code null}
     * @return axis that is 1.0 when the supplier returns true, 0.0 otherwise
     */
    static Axis fromBoolean(final BooleanSupplier supplier) {
        if (supplier == null) {
            throw new IllegalArgumentException("BooleanSupplier is required");
        }
        return new Axis() {
            @Override
            public double get() {
                return supplier.getAsBoolean() ? 1.0 : 0.0;
            }
        };
    }

    // ------------------------------------------------------------------------
    // Combinators: build signed axes from positive axes / buttons
    // ------------------------------------------------------------------------

    /**
     * Build a signed axis in [-1, +1] from two "positive" axes (typically triggers).
     *
     * <p>
     * Intended usage is to combine a forward and backward trigger:
     * </p>
     *
     * <pre>{@code
     * Axis forward = player.rightTrigger(); // [0,1]
     * Axis back    = player.leftTrigger();  // [0,1]
     *
     * Axis axial = Axis.signedFromPositivePair(forward, back);
     * }</pre>
     *
     * <p>
     * Internally, both inputs are clamped to [0, 1] via
     * {@link MathUtil#clamp01(double)} before subtraction, so accidental misuse
     * (e.g. passing a stick axis) is still bounded and safe.
     * </p>
     *
     * <p>
     * The resulting axis is computed as:
     * </p>
     * <pre>{@code
     *   value = clamp01(positive.get()) - clamp01(negative.get());
     * }</pre>
     *
     * @param positive axis representing the "positive" direction (e.g., forward trigger)
     * @param negative axis representing the "negative" direction (e.g., backward trigger)
     * @return signed axis in [-1, +1]
     */
    static Axis signedFromPositivePair(final Axis positive, final Axis negative) {
        if (positive == null) {
            throw new IllegalArgumentException("positive axis is required");
        }
        if (negative == null) {
            throw new IllegalArgumentException("negative axis is required");
        }

        return new Axis() {
            @Override
            public double get() {
                double pos = MathUtil.clamp01(positive.get());
                double neg = MathUtil.clamp01(negative.get());
                return pos - neg; // in [-1, +1]
            }
        };
    }

    /**
     * Build a signed axis in [-1, +1] from two buttons.
     *
     * <p>
     * This is useful for simple "digital" controls, especially when combined
     * with a rate limiter:
     * </p>
     *
     * <pre>{@code
     * Axis axial = Axis.signedFromButtons(
     *         player.dpadDown(), // negative
     *         player.dpadUp()    // positive
     * );
     * }</pre>
     *
     * <p>
     * The resulting axis is:</p>
     *
     * <pre>{@code
     *   +1 when positive is pressed and negative is not
     *   -1 when negative is pressed and positive is not
     *    0 otherwise (both pressed or both unpressed)
     * }</pre>
     *
     * @param negative button that represents the negative direction (e.g., "down")
     * @param positive button that represents the positive direction (e.g., "up")
     * @return signed axis in [-1, +1]
     */
    static Axis signedFromButtons(final Button negative, final Button positive) {
        if (negative == null) {
            throw new IllegalArgumentException("negative button is required");
        }
        if (positive == null) {
            throw new IllegalArgumentException("positive button is required");
        }

        return new Axis() {
            @Override
            public double get() {
                double pos = positive.isPressed() ? 1.0 : 0.0;
                double neg = negative.isPressed() ? 1.0 : 0.0;
                return pos - neg; // in [-1, +1]
            }
        };
    }
}

package edu.ftcphoenix.fw.input;

import java.util.function.BooleanSupplier;

/**
 * Simple digital input.
 *
 * <h2>What a Button represents</h2>
 * <p>
 * {@code Button} is the minimal abstraction for a boolean input that can change
 * each loop: a gamepad button, a synthesized condition, or the result of
 * combining other buttons.
 * </p>
 *
 * <p>
 * Edge detection (on-press, on-release, toggles) is <b>not</b> handled here;
 * see {@link edu.ftcphoenix.fw.input.binding.Bindings} for that higher-level
 * behavior. {@code Button} simply answers the question: "is it pressed
 * <em>right now</em>?"
 * </p>
 *
 * <h2>Responsibilities</h2>
 * <ul>
 *   <li>Expose {@link #isPressed()} as the single core operation.</li>
 *   <li>Provide factories for adapting a {@link BooleanSupplier} or {@link Axis}.</li>
 *   <li>Offer basic boolean combinators ({@link #and(Button)},
 *       {@link #or(Button)}, {@link #not()}).</li>
 *   <li>Provide a convenient bridge to {@link Axis} via {@link #asAxis()}.</li>
 * </ul>
 */
public interface Button {

    /**
     * Whether the button is currently pressed.
     *
     * <p>
     * This should be a cheap, side-effect free call that reflects the most
     * recent input state (e.g., directly reading an FTC {@code Gamepad} field
     * or a cached value updated once per loop).
     * </p>
     *
     * @return {@code true} if pressed, {@code false} otherwise
     */
    boolean isPressed();

    // ------------------------------------------------------------------------
    // Factories
    // ------------------------------------------------------------------------

    /**
     * Factory for a {@link Button} backed by a {@link BooleanSupplier}.
     *
     * <p>
     * This is the most generic way to adapt arbitrary boolean sources into the
     * {@code Button} interface.
     * </p>
     *
     * <pre>{@code
     * Button shoot = Button.of(() -> gamepad1.right_bumper);
     * }</pre>
     *
     * @param supplier source of the button state; must not be {@code null}
     * @return a {@link Button} that delegates to {@link BooleanSupplier#getAsBoolean()}
     */
    static Button of(final BooleanSupplier supplier) {
        if (supplier == null) {
            throw new IllegalArgumentException("BooleanSupplier is required");
        }
        return new Button() {
            @Override
            public boolean isPressed() {
                return supplier.getAsBoolean();
            }
        };
    }

    /**
     * Create a button from an {@link Axis} and a threshold.
     *
     * <p>
     * This is a convenience that delegates to {@link Axis#asButton(double)},
     * so there is a single canonical implementation of the thresholding logic.
     * </p>
     *
     * <p>
     * Semantics follow the sign-aware behavior of {@link Axis#asButton(double)}:
     * </p>
     *
     * <ul>
     *   <li>If {@code threshold >= 0}, the button is pressed when
     *       {@code axis.get() >= threshold}.</li>
     *   <li>If {@code threshold < 0}, the button is pressed when
     *       {@code axis.get() <= threshold}.</li>
     * </ul>
     *
     * <p>Examples:</p>
     *
     * <pre>{@code
     * Axis trigger = player.rightTrigger(); // [0, 1]
     * Button shoot = Button.fromAxis(trigger, 0.5); // "pressed" when >= 0.5
     *
     * Axis axial  = player.leftY(); // [-1, +1]
     * Button fwd  = Button.fromAxis(axial,  +0.5); // axis >= +0.5
     * Button back = Button.fromAxis(axial,  -0.5); // axis <= -0.5
     * }</pre>
     *
     * @param axis      axis to threshold; must not be {@code null}
     * @param threshold cutoff value at or beyond which the button is "pressed";
     *                  positive thresholds use {@code >=}, negative thresholds
     *                  use {@code <=}
     * @return a {@link Button} view of the axis
     */
    static Button fromAxis(final Axis axis, final double threshold) {
        if (axis == null) {
            throw new IllegalArgumentException("Axis is required");
        }
        return axis.asButton(threshold);
    }

    // ------------------------------------------------------------------------
    // Boolean combinators
    // ------------------------------------------------------------------------

    /**
     * Logical AND of this button and another.
     *
     * <p>
     * The returned button is considered pressed only when both inputs are
     * pressed. This is useful for "safety" or "shift" combos, e.g.:
     * </p>
     *
     * <pre>{@code
     * Button shootWhileHeld = dk.p1().rightBumper()
     *         .and(dk.p1().buttonX());
     * }</pre>
     *
     * @param other other button; must not be {@code null}
     * @return a new {@link Button} representing {@code this && other}
     */
    default Button and(final Button other) {
        if (other == null) {
            throw new IllegalArgumentException("other Button is required");
        }
        final Button self = this;
        return new Button() {
            @Override
            public boolean isPressed() {
                return self.isPressed() && other.isPressed();
            }
        };
    }

    /**
     * Logical OR of this button and another.
     *
     * <p>
     * The returned button is considered pressed when either input is pressed.
     * </p>
     *
     * <pre>{@code
     * Button shootEither = dk.p1().buttonX()
     *         .or(dk.p1().buttonY());
     * }</pre>
     *
     * @param other other button; must not be {@code null}
     * @return a new {@link Button} representing {@code this || other}
     */
    default Button or(final Button other) {
        if (other == null) {
            throw new IllegalArgumentException("other Button is required");
        }
        final Button self = this;
        return new Button() {
            @Override
            public boolean isPressed() {
                return self.isPressed() || other.isPressed();
            }
        };
    }

    /**
     * Logical NOT of this button.
     *
     * <p>
     * The returned button is considered pressed when this one is not pressed.
     * This is occasionally useful when combined with {@link #and(Button)} or
     * {@link #or(Button)}.
     * </p>
     *
     * @return a new {@link Button} representing {@code !this}
     */
    default Button not() {
        final Button self = this;
        return new Button() {
            @Override
            public boolean isPressed() {
                return !self.isPressed();
            }
        };
    }

    // ------------------------------------------------------------------------
    // Bridge to Axis
    // ------------------------------------------------------------------------

    /**
     * View this button as an {@link Axis} that is 1.0 when pressed and 0.0 otherwise.
     *
     * <p>
     * This is useful when feeding digital inputs into code that expects an
     * {@code Axis}, such as rate limiters or drive shaping:
     * </p>
     *
     * <pre>{@code
     * Axis forward = dk.p1().dpadUp().asAxis();   // 1.0 when up is held
     * Axis back    = dk.p1().dpadDown().asAxis(); // 1.0 when down is held
     *
     * Axis axial = Axis.signedFromPositivePair(forward, back);
     * }</pre>
     *
     * <p>
     * This default implementation delegates to
     * {@link Axis#fromButton(Button)}, so behavior stays consistent across the
     * framework.
     * </p>
     *
     * @return an {@link Axis} that is 1.0 when this button is pressed, 0.0 otherwise
     */
    default Axis asAxis() {
        return Axis.fromButton(this);
    }
}

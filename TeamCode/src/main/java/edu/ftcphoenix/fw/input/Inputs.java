package edu.ftcphoenix.fw.input;

import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.DoubleSupplier;

import edu.ftcphoenix.fw.input.extras.Combos;

/**
 * Convenience factories for creating RAW inputs from {@link GamepadDevice},
 * plus helpers for common FTC virtual inputs:
 * <ul>
 *   <li>PlayStation aliases (cross/circle/square/triangle) in addition to A/B/X/Y.</li>
 *   <li>Virtual axes from buttons or triggers.</li>
 *   <li>Triggers from buttons (0..1), two-buttons → axis (-1..1).</li>
 *   <li>Axis → DPAD-like buttons (pos/neg) with hysteresis.</li>
 *   <li>2D stick → DPAD (up/down/left/right) with hysteresis.</li>
 * </ul>
 *
 * <p>All factory methods register the created inputs with the given {@link InputRegistry}.
 * Call {@link Gamepads#update(double)} once per loop to update all inputs (RAW then DERIVED).
 */
public final class Inputs {
    private Inputs() {
    }

    // ---------------------------
    // RAW AXES (sticks & triggers)
    // ---------------------------

    public static Axis leftX(InputRegistry reg, GamepadDevice d) {
        return Axis.raw(require(reg), require(d)::leftX);
    }

    public static Axis leftY(InputRegistry reg, GamepadDevice d) {
        return Axis.raw(require(reg), require(d)::leftY);
    }

    public static Axis rightX(InputRegistry reg, GamepadDevice d) {
        return Axis.raw(require(reg), require(d)::rightX);
    }

    public static Axis rightY(InputRegistry reg, GamepadDevice d) {
        return Axis.raw(require(reg), require(d)::rightY);
    }

    public static Axis leftTriggerAxis(InputRegistry reg, GamepadDevice d) {
        return Axis.raw(require(reg), require(d)::leftTrigger);  // 0..1
    }

    public static Axis rightTriggerAxis(InputRegistry reg, GamepadDevice d) {
        return Axis.raw(require(reg), require(d)::rightTrigger); // 0..1
    }

    // ---------------------------
    // RAW BUTTONS (Xbox/Logitech names)
    // ---------------------------

    public static Button a(InputRegistry reg, GamepadDevice d) {
        return Button.raw(require(reg), require(d)::a);
    }

    public static Button b(InputRegistry reg, GamepadDevice d) {
        return Button.raw(require(reg), require(d)::b);
    }

    public static Button x(InputRegistry reg, GamepadDevice d) {
        return Button.raw(require(reg), require(d)::x);
    }

    public static Button y(InputRegistry reg, GamepadDevice d) {
        return Button.raw(require(reg), require(d)::y);
    }

    public static Button leftBumper(InputRegistry reg, GamepadDevice d) {
        return Button.raw(require(reg), require(d)::leftBumper);
    }

    public static Button rightBumper(InputRegistry reg, GamepadDevice d) {
        return Button.raw(require(reg), require(d)::rightBumper);
    }

    public static Button start(InputRegistry reg, GamepadDevice d) {
        return Button.raw(require(reg), require(d)::start);
    }

    public static Button back(InputRegistry reg, GamepadDevice d) {
        return Button.raw(require(reg), require(d)::back);
    }

    public static Button dpadUp(InputRegistry reg, GamepadDevice d) {
        return Button.raw(require(reg), require(d)::dpadUp);
    }

    public static Button dpadDown(InputRegistry reg, GamepadDevice d) {
        return Button.raw(require(reg), require(d)::dpadDown);
    }

    public static Button dpadLeft(InputRegistry reg, GamepadDevice d) {
        return Button.raw(require(reg), require(d)::dpadLeft);
    }

    public static Button dpadRight(InputRegistry reg, GamepadDevice d) {
        return Button.raw(require(reg), require(d)::dpadRight);
    }

    public static Button leftStickButton(InputRegistry reg, GamepadDevice d) {
        return Button.raw(require(reg), require(d)::leftStickButton);
    }

    public static Button rightStickButton(InputRegistry reg, GamepadDevice d) {
        return Button.raw(require(reg), require(d)::rightStickButton);
    }

    // ---------------------------
    // RAW BUTTONS (PlayStation aliases)
    // ---------------------------

    /**
     * CROSS ↔ A
     */
    public static Button cross(InputRegistry reg, GamepadDevice d) {
        return Button.raw(require(reg), require(d)::cross);
    }

    /**
     * CIRCLE ↔ B
     */
    public static Button circle(InputRegistry reg, GamepadDevice d) {
        return Button.raw(require(reg), require(d)::circle);
    }

    /**
     * SQUARE ↔ X
     */
    public static Button square(InputRegistry reg, GamepadDevice d) {
        return Button.raw(require(reg), require(d)::square);
    }

    /**
     * TRIANGLE ↔ Y
     */
    public static Button triangle(InputRegistry reg, GamepadDevice d) {
        return Button.raw(require(reg), require(d)::triangle);
    }

    // ---------------------------
    // VIRTUALS: Buttons → Axis/Trigger
    // ---------------------------

    /**
     * Two buttons to a symmetric axis in [-1, 1]:
     * when {@code neg} is down -> -1; when {@code pos} is down -> +1; both -> 0; none -> 0.
     */
    public static Axis axisFromButtons(InputRegistry reg, Button neg, Button pos) {
        Objects.requireNonNull(neg, "neg");
        Objects.requireNonNull(pos, "pos");
        return Axis.derived(require(reg), () -> (pos.isDown() ? 1.0 : 0.0) - (neg.isDown() ? 1.0 : 0.0));
    }

    /**
     * Single button to trigger axis in [0, 1] (1 when pressed, else 0).
     */
    public static Axis triggerFromButton(InputRegistry reg, Button btn) {
        Objects.requireNonNull(btn, "btn");
        return Axis.derived(require(reg), () -> btn.isDown() ? 1.0 : 0.0);
    }

    /**
     * Two triggers (0..1) to axis (-1..1) where left is negative and right is positive.
     * Values subtract and clamp.
     */
    public static Axis axisFromTriggers(InputRegistry reg, Axis leftTrigger0to1, Axis rightTrigger0to1) {
        Objects.requireNonNull(leftTrigger0to1, "leftTrigger0to1");
        Objects.requireNonNull(rightTrigger0to1, "rightTrigger0to1");
        return Axis.derived(require(reg), () -> {
            double v = rightTrigger0to1.get() - leftTrigger0to1.get();
            if (v > 1.0) v = 1.0;
            if (v < -1.0) v = -1.0;
            return v;
        });
    }

    // ---------------------------
    // VIRTUALS: Axis → DPAD-like Buttons (hysteresis)
    // ---------------------------

    /**
     * Decompose a 1D axis into two DPAD-like buttons with hysteresis to avoid chatter.
     * <ul>
     *   <li>Positive button turns ON when axis >= high, OFF when axis <= low.</li>
     *   <li>Negative button turns ON when axis <= -high, OFF when axis >= -low.</li>
     * </ul>
     * Requires 0 <= low <= high <= 1.
     */
    public static AxisSplit splitAxisToButtons(InputRegistry reg, Axis axis, double low, double high) {
        require(reg);
        Objects.requireNonNull(axis, "axis");
        double lo = Math.max(0.0, Math.min(low, high));
        double hi = Math.max(lo, high);

        // Positive side via built-in hysteresis helper
        Button pos = axis.asHysteresisButton(reg, lo, hi);

        // Negative side: custom hysteresis on the negative direction
        Button neg = Button.derived(reg, new BooleanSupplier() {
            boolean state = false;

            @Override
            public boolean getAsBoolean() {
                double v = axis.get();
                if (!state) {
                    if (v <= -hi) state = true;
                } else {
                    if (v >= -lo) state = false;
                }
                return state;
            }
        });

        return new AxisSplit(neg, pos);
    }

    /**
     * Convenience: split two axes (x,y) into 4-way DPAD with hysteresis.
     */
    public static Dpad split2DToDpad(InputRegistry reg,
                                     Axis x, Axis y,
                                     double low, double high) {
        AxisSplit xs = splitAxisToButtons(reg, x, low, high);
        AxisSplit ys = splitAxisToButtons(reg, y, low, high);
        return new Dpad(ys.pos /*up*/, ys.neg /*down*/, xs.neg /*left*/, xs.pos /*right*/);
    }

    // ---------------------------
    // VIRTUALS: Custom axis samplers
    // ---------------------------

    /**
     * Create a DERIVED axis from any sampler (already in [-1,1] or [0,1]).
     */
    public static Axis axis(InputRegistry reg, DoubleSupplier sampler) {
        return Axis.derived(require(reg), require(sampler));
    }

    /**
     * Create a DERIVED button from any sampler. Prefer {@link Combos} for AND/OR/chords.
     */
    public static Button button(InputRegistry reg, BooleanSupplier sampler) {
        return Button.derived(require(reg), require(sampler));
    }

    // ---------------------------
    // Return types
    // ---------------------------

    /**
     * Result of splitting a 1D axis into negative and positive buttons.
     */
    public static final class AxisSplit {
        public final Button neg;
        public final Button pos;

        AxisSplit(Button neg, Button pos) {
            this.neg = neg;
            this.pos = pos;
        }
    }

    /**
     * Result of splitting two axes into a 4-way DPAD.
     */
    public static final class Dpad {
        public final Button up, down, left, right;

        Dpad(Button up, Button down, Button left, Button right) {
            this.up = up;
            this.down = down;
            this.left = left;
            this.right = right;
        }
    }

    // ---------------------------
    // Utils
    // ---------------------------

    private static <T> T require(T t) {
        return Objects.requireNonNull(t, "argument must be non-null");
    }
}

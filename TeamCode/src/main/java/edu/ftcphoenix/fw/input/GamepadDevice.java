package edu.ftcphoenix.fw.input;

import com.qualcomm.robotcore.hardware.Gamepad;

import java.util.Objects;

/**
 * Thin, student-friendly wrapper around FTC {@link Gamepad}.
 *
 * <p>Goals:</p>
 * <ul>
 *   <li>Expose sticks and triggers as {@link Axis} objects.</li>
 *   <li>Expose buttons as stateful {@link Button} objects with
 *       {@link Button#onPress()}, {@link Button#onRelease()}, and
 *       {@link Button#isHeld()} semantics.</li>
 *   <li>Hide raw FTC field names (e.g., {@code left_stick_x}) behind clearer
 *       method names.</li>
 * </ul>
 *
 * <p>Typical usage (via {@link Gamepads}):</p>
 *
 * <pre>{@code
 * Gamepads pads = Gamepads.create(gamepad1, gamepad2);
 *
 * void loop(double dtSec) {
 *     pads.update(dtSec); // updates all registered Buttons
 *
 *     // Sticks as axes:
 *     double driveX = pads.p1().leftX().get();
 *     double driveY = pads.p1().leftY().get();
 *
 *     // Buttons with edge + level semantics:
 *     if (pads.p1().a().onPress()) {
 *         // Fire once when A is pressed.
 *     }
 *
 *     if (pads.p1().rightBumper().isHeld()) {
 *         // Runs every loop while RB is held.
 *     }
 * }
 * }</pre>
 */
public final class GamepadDevice {

    private final Gamepad gp;

    // Sticks/triggers as axes.
    private final Axis leftX;
    private final Axis leftY;
    private final Axis rightX;
    private final Axis rightY;
    private final Axis leftTrigger;
    private final Axis rightTrigger;

    // Buttons, as stateful Buttons (registered automatically via Button.of).
    private final Button a;
    private final Button b;
    private final Button x;
    private final Button y;
    private final Button lb;
    private final Button rb;
    private final Button dpadUp;
    private final Button dpadDown;
    private final Button dpadLeft;
    private final Button dpadRight;
    private final Button start;
    private final Button back;
    private final Button leftStickButton;
    private final Button rightStickButton;

    /**
     * Wrap an FTC {@link Gamepad}.
     *
     * <p>All {@link Button} instances created here are registered with the
     * global {@link Button} registry via {@link Button#of(java.util.function.BooleanSupplier)}.
     * Their {@link Button#update()} method will be called automatically when
     * the framework calls {@link Button#updateAllRegistered()} (typically from
     * {@code Gamepads.update(dtSec)}).</p>
     */
    public GamepadDevice(Gamepad gp) {
        this.gp = Objects.requireNonNull(gp, "gamepad is required");

        // Axes: direct views of stick/trigger values.
        this.leftX        = Axis.of(() -> gp.left_stick_x);
        this.leftY        = Axis.of(() -> gp.left_stick_y);
        this.rightX       = Axis.of(() -> gp.right_stick_x);
        this.rightY       = Axis.of(() -> gp.right_stick_y);
        this.leftTrigger  = Axis.of(() -> gp.left_trigger);
        this.rightTrigger = Axis.of(() -> gp.right_trigger);

        // Buttons: stateful, registered via Button.of(...).
        this.a  = Button.of(() -> gp.a);
        this.b  = Button.of(() -> gp.b);
        this.x  = Button.of(() -> gp.x);
        this.y  = Button.of(() -> gp.y);
        this.lb = Button.of(() -> gp.left_bumper);
        this.rb = Button.of(() -> gp.right_bumper);

        this.dpadUp    = Button.of(() -> gp.dpad_up);
        this.dpadDown  = Button.of(() -> gp.dpad_down);
        this.dpadLeft  = Button.of(() -> gp.dpad_left);
        this.dpadRight = Button.of(() -> gp.dpad_right);

        this.start = Button.of(() -> gp.start);
        this.back  = Button.of(() -> gp.back);

        this.leftStickButton  = Button.of(() -> gp.left_stick_button);
        this.rightStickButton = Button.of(() -> gp.right_stick_button);
    }

    // ---------------------------------------------------------------------
    // Axes
    // ---------------------------------------------------------------------

    /**
     * Left stick X axis.
     *
     * <p>Matches {@link Gamepad#left_stick_x}:
     * <ul>
     *   <li>-1.0 = full left</li>
     *   <li>+1.0 = full right</li>
     * </ul>
     * </p>
     */
    public Axis leftX() {
        return leftX;
    }

    /**
     * Left stick Y axis.
     *
     * <p>Matches {@link Gamepad#left_stick_y}:
     * <ul>
     *   <li>-1.0 = full up</li>
     *   <li>+1.0 = full down</li>
     * </ul>
     * </p>
     */
    public Axis leftY() {
        return leftY;
    }

    /**
     * Right stick X axis.
     */
    public Axis rightX() {
        return rightX;
    }

    /**
     * Right stick Y axis.
     */
    public Axis rightY() {
        return rightY;
    }

    /**
     * Left trigger axis in [0, 1].
     */
    public Axis leftTrigger() {
        return leftTrigger;
    }

    /**
     * Right trigger axis in [0, 1].
     */
    public Axis rightTrigger() {
        return rightTrigger;
    }

    // ---------------------------------------------------------------------
    // Buttons (canonical names)
    // ---------------------------------------------------------------------

    /** Button A. */
    public Button a() {
        return a;
    }

    /** Button B. */
    public Button b() {
        return b;
    }

    /** Button X. */
    public Button x() {
        return x;
    }

    /** Button Y. */
    public Button y() {
        return y;
    }

    /** Left bumper (short name). */
    public Button lb() {
        return lb;
    }

    /** Right bumper (short name). */
    public Button rb() {
        return rb;
    }

    /** D-pad up. */
    public Button dpadUp() {
        return dpadUp;
    }

    /** D-pad down. */
    public Button dpadDown() {
        return dpadDown;
    }

    /** D-pad left. */
    public Button dpadLeft() {
        return dpadLeft;
    }

    /** D-pad right. */
    public Button dpadRight() {
        return dpadRight;
    }

    /** Start button. */
    public Button start() {
        return start;
    }

    /** Back / options button. */
    public Button back() {
        return back;
    }

    /** Left stick button (L3). */
    public Button leftStickButton() {
        return leftStickButton;
    }

    /** Right stick button (R3). */
    public Button rightStickButton() {
        return rightStickButton;
    }

    // ---------------------------------------------------------------------
    // Aliases (to keep older / more descriptive names working)
    // ---------------------------------------------------------------------

    /**
     * Alias for {@link #lb()}.
     *
     * <p>Provided to support code that prefers {@code leftBumper()} naming.</p>
     */
    public Button leftBumper() {
        return lb;
    }

    /**
     * Alias for {@link #rb()}.
     *
     * <p>Provided to support code that prefers {@code rightBumper()} naming.</p>
     */
    public Button rightBumper() {
        return rb;
    }

    // ---------------------------------------------------------------------
    // Raw access (advanced)
    // ---------------------------------------------------------------------

    /**
     * @return the underlying FTC {@link Gamepad}.
     *
     * <p>Most code should not need this; it is provided for advanced cases
     * where you need a field that does not yet have a wrapper.</p>
     */
    public Gamepad raw() {
        return gp;
    }
}

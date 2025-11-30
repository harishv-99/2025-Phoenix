package edu.ftcphoenix.fw.input;

import com.qualcomm.robotcore.hardware.Gamepad;
import edu.ftcphoenix.fw.input.Axis;
import edu.ftcphoenix.fw.input.Button;

/**
 * Thin wrapper around an FTC {@link Gamepad} that exposes:
 * <ul>
 *     <li>Axes ({@link Axis}) for sticks and triggers.</li>
 *     <li>Buttons ({@link Button}) for all digital inputs.</li>
 * </ul>
 *
 * <h2>Axis conventions</h2>
 * Axes use a <b>human-friendly</b> convention:
 * <ul>
 *     <li><b>leftX</b>:  -1.0 = full left,  +1.0 = full right</li>
 *     <li><b>leftY</b>:  -1.0 = full down, +1.0 = full up</li>
 *     <li><b>rightX</b>: -1.0 = full left,  +1.0 = full right</li>
 *     <li><b>rightY</b>: -1.0 = full down, +1.0 = full up</li>
 * </ul>
 *
 * <p>
 * This inverts the raw FTC {@link Gamepad} Y-axis values (where pushing a stick
 * up yields a <em>negative</em> value and down yields a <em>positive</em> value) so that
 * <b>\"up\" is always positive</b> in Phoenix code. X-axis values are passed through as-is.
 * </p>
 *
 * <h2>Typical usage</h2>
 * <p>
 * You normally do not construct {@code GamepadDevice} yourself. Instead, you use
 * {@link Gamepads} as a manager for both controllers and update it once per loop:
 * </p>
 *
 * <pre>{@code
 * public final class PhoenixRobot {
 *     private final Gamepads pads;
 *     private final Bindings bindings = new Bindings();
 *
 *     public PhoenixRobot(HardwareMap hw, Gamepads pads) {
 *         this.pads = pads;
 *         configureBindings();
 *     }
 *
 *     private void configureBindings() {
 *         GamepadDevice p1 = pads.p1();
 *
 *         // Example: while A is held, run intake forward; stop on release.
 *         bindings.whileHeld(p1.a(), () -> intakePlant.setTarget(+1.0));
 *         bindings.onRelease(p1.a(), () -> intakePlant.setTarget(0.0));
 *     }
 *
 *     public void updateTeleOp(LoopClock clock) {
 *         double dt = clock.dtSec();
 *
 *         // In your OpMode loop:
 *         //   pads.update(dt);
 *         //   bindings.update(dt);
 *         //
 *         // You can read stick axes directly if needed:
 *         //
 *         // GamepadDevice p1 = pads.p1();
 *         // double forward = p1.leftY().get();   // +1.0 when stick is pushed up
 *         // double strafe  = p1.leftX().get();   // +1.0 to the right
 *         // double turn    = p1.rightX().get();  // +1.0 to the right
 *     }
 * }
 *
 * // OpMode skeleton:
 * public class MyTeleOp extends OpMode {
 *     private final LoopClock clock = new LoopClock();
 *     private Gamepads pads;
 *     private PhoenixRobot robot;
 *
 *     @Override
 *     public void init() {
 *         pads = Gamepads.create(gamepad1, gamepad2);
 *         robot = new PhoenixRobot(hardwareMap, pads);
 *         clock.reset(getRuntime());
 *     }
 *
 *     @Override
 *     public void loop() {
 *         clock.update(getRuntime());
 *         double dt = clock.dtSec();
 *
 *         pads.update(dt);
 *         robot.bindings().update(dt);
 *         robot.updateTeleOp(clock);
 *     }
 * }
 * }</pre>
 *
 * <p>
 * The intent is that all raw controller access goes through {@link Gamepads} /
 * {@code GamepadDevice}, and all \"which button does what\" logic lives in a
 * single {@code configureBindings()} method on your robot.
 * </p>
 */
public final class GamepadDevice {
    private final Gamepad gp;

    // Axes
    private final Axis leftX;
    private final Axis leftY;
    private final Axis rightX;
    private final Axis rightY;
    private final Axis leftTrigger;
    private final Axis rightTrigger;

    // Buttons (add/keep whatever you already expose here)
    private final Button a;
    private final Button b;
    private final Button x;
    private final Button y;
    private final Button leftBumper;
    private final Button rightBumper;
    private final Button dpadUp;
    private final Button dpadDown;
    private final Button dpadLeft;
    private final Button dpadRight;
    // ... other buttons as in your existing file ...

    public GamepadDevice(Gamepad gp) {
        this.gp = gp;

        // Axes: X passes through, Y is inverted so that "up" is positive.
        this.leftX  = Axis.of(() -> gp.left_stick_x);
        this.leftY  = Axis.of(() -> -gp.left_stick_y);   // NOTE: inverted
        this.rightX = Axis.of(() -> gp.right_stick_x);
        this.rightY = Axis.of(() -> -gp.right_stick_y);   // NOTE: inverted

        this.leftTrigger  = Axis.of(() -> gp.left_trigger);
        this.rightTrigger = Axis.of(() -> gp.right_trigger);

        // Buttons – same mapping as before.
        this.a = Button.of(() -> gp.a);
        this.b = Button.of(() -> gp.b);
        this.x = Button.of(() -> gp.x);
        this.y = Button.of(() -> gp.y);

        this.leftBumper  = Button.of(() -> gp.left_bumper);
        this.rightBumper = Button.of(() -> gp.right_bumper);

        this.dpadUp    = Button.of(() -> gp.dpad_up);
        this.dpadDown  = Button.of(() -> gp.dpad_down);
        this.dpadLeft  = Button.of(() -> gp.dpad_left);
        this.dpadRight = Button.of(() -> gp.dpad_right);

        // ... initialize the rest of your buttons exactly as before ...
    }

    /** Left stick X axis: -1.0 = full left, +1.0 = full right. */
    public Axis leftX() {
        return leftX;
    }

    /**
     * Left stick Y axis: -1.0 = full down, +1.0 = full up.
     *
     * <p>This inverts the raw FTC {@link Gamepad#left_stick_y} (which is
     * negative when pushed up) so that pushing the stick up yields a
     * <b>positive</b> value and pushing it down yields a <b>negative</b> value.</p>
     */
    public Axis leftY() {
        return leftY;
    }

    /** Right stick X axis: -1.0 = full left, +1.0 = full right. */
    public Axis rightX() {
        return rightX;
    }

    /**
     * Right stick Y axis: -1.0 = full down, +1.0 = full up.
     *
     * <p>This inverts the raw FTC {@link Gamepad#right_stick_y} (which is
     * negative when pushed up) so that pushing the stick up yields a
     * <b>positive</b> value and pushing it down yields a <b>negative</b> value.</p>
     */
    public Axis rightY() {
        return rightY;
    }

    public Axis leftTrigger() {
        return leftTrigger;
    }

    public Axis rightTrigger() {
        return rightTrigger;
    }

    public Button a() { return a; }
    public Button b() { return b; }
    public Button x() { return x; }
    public Button y() { return y; }

    public Button leftBumper() { return leftBumper; }
    public Button rightBumper() { return rightBumper; }

    public Button dpadUp() { return dpadUp; }
    public Button dpadDown() { return dpadDown; }
    public Button dpadLeft() { return dpadLeft; }
    public Button dpadRight() { return dpadRight; }

    // ... expose the rest of your buttons as in the existing implementation ...
}

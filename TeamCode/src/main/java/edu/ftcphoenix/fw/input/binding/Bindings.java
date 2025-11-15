package edu.ftcphoenix.fw.input.binding;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import edu.ftcphoenix.fw.input.Button;

/**
 * Simple binding manager that turns raw {@link Button} states into higher-level
 * behaviors for teleop:
 *
 * <ul>
 *   <li>{@link #onPress(Button, Runnable)} – fire once on rising edge.</li>
 *   <li>{@link #whileHeld(Button, Runnable, Runnable)} – run once per loop while
 *       pressed, and a separate action when released.</li>
 *   <li>{@link #toggle(Button, Consumer)} – flip a boolean on rising edge and
 *       notify a consumer.</li>
 * </ul>
 *
 * <p>Usage pattern:
 * <pre>
 * Bindings bindings = new Bindings();
 *
 * bindings.onPress(dk.p1().buttonA(), new Runnable() {
 *     public void run() { doSomethingOnce(); }
 * });
 *
 * bindings.whileHeld(dk.p1().rightBumper(),
 *     new Runnable() { public void run() { runWhilePressed(); } },
 *     new Runnable() { public void run() { stopWhenReleased(); } }
 * );
 *
 * bindings.toggle(dk.p1().buttonX(), new Consumer<Boolean>() {
 *     public void accept(Boolean on) { setShooterOn(on != null && on.booleanValue()); }
 * });
 *
 * // In loop():
 * bindings.update(dtSec);
 * </pre>
 *
 * <p>This class is deliberately tiny and SDK-agnostic. It does not know about
 * gamepads directly, only the {@link Button} abstraction provided by the FW.
 */
public final class Bindings {

    private static final class OnPressBinding {
        Button button;
        Runnable action;
        boolean lastPressed;
    }

    private static final class WhileHeldBinding {
        Button button;
        Runnable whilePressed;
        Runnable onRelease;
        boolean lastPressed;
    }

    private static final class ToggleBinding {
        Button button;
        Consumer<Boolean> consumer;
        boolean lastPressed;
        boolean toggled;
    }

    private final List<OnPressBinding> onPressBindings = new ArrayList<OnPressBinding>();
    private final List<WhileHeldBinding> whileHeldBindings = new ArrayList<WhileHeldBinding>();
    private final List<ToggleBinding> toggleBindings = new ArrayList<ToggleBinding>();

    /**
     * Bind a button to an action that fires once on the rising edge
     * (when the button transitions from not pressed to pressed).
     */
    public void onPress(Button button, Runnable action) {
        if (button == null || action == null) {
            throw new IllegalArgumentException("button and action are required");
        }
        OnPressBinding b = new OnPressBinding();
        b.button = button;
        b.action = action;
        b.lastPressed = button.isPressed(); // start from current state
        onPressBindings.add(b);
    }

    /**
     * Bind a button to an action that runs every loop while the button is pressed,
     * and another action that runs once when the button is released.
     *
     * <p>Semantics:
     * <ul>
     *   <li>If the button is pressed, {@code whilePressed.run()} is called on
     *       every {@link #update(double)}.</li>
     *   <li>When the button transitions from pressed → not pressed, the
     *       {@code onRelease.run()} action is fired once.</li>
     * </ul>
     */
    public void whileHeld(Button button, Runnable whilePressed, Runnable onRelease) {
        if (button == null || whilePressed == null || onRelease == null) {
            throw new IllegalArgumentException("button, whilePressed, and onRelease are required");
        }
        WhileHeldBinding b = new WhileHeldBinding();
        b.button = button;
        b.whilePressed = whilePressed;
        b.onRelease = onRelease;
        b.lastPressed = button.isPressed(); // start from current state
        whileHeldBindings.add(b);
    }

    /**
     * Bind a button to a boolean toggle. Each rising edge of the button flips
     * the internal boolean state and notifies the consumer.
     *
     * <p>Semantics:
     * <ul>
     *   <li>The internal state starts at {@code false}.</li>
     *   <li>On each rising edge (not pressed → pressed), the state toggles
     *       and {@code consumer.accept(state)} is called.</li>
     *   <li>There is no automatic call at init; if you need an initial state
     *       applied to hardware, set it yourself in your OpMode init.</li>
     * </ul>
     */
    public void toggle(Button button, Consumer<Boolean> consumer) {
        if (button == null || consumer == null) {
            throw new IllegalArgumentException("button and consumer are required");
        }
        ToggleBinding b = new ToggleBinding();
        b.button = button;
        b.consumer = consumer;
        b.lastPressed = button.isPressed();
        b.toggled = false;
        toggleBindings.add(b);
    }

    /**
     * Process all bindings for this loop.
     *
     * <p>{@code dtSec} is provided for symmetry with other FW components, but
     * is not currently used. It is kept to allow future time-based bindings
     * without changing the signature.
     */
    public void update(double dtSec) {
        // onPress: detect rising edge
        for (int i = 0; i < onPressBindings.size(); i++) {
            OnPressBinding b = onPressBindings.get(i);
            boolean pressed = b.button.isPressed();
            if (pressed && !b.lastPressed) {
                b.action.run();
            }
            b.lastPressed = pressed;
        }

        // whileHeld: run while pressed, fire onRelease on falling edge
        for (int i = 0; i < whileHeldBindings.size(); i++) {
            WhileHeldBinding b = whileHeldBindings.get(i);
            boolean pressed = b.button.isPressed();
            if (pressed) {
                b.whilePressed.run();
            } else if (b.lastPressed) {
                // Just released
                b.onRelease.run();
            }
            b.lastPressed = pressed;
        }

        // toggle: flip state on rising edge and notify consumer
        for (int i = 0; i < toggleBindings.size(); i++) {
            ToggleBinding b = toggleBindings.get(i);
            boolean pressed = b.button.isPressed();
            if (pressed && !b.lastPressed) {
                b.toggled = !b.toggled;
                b.consumer.accept(Boolean.valueOf(b.toggled));
            }
            b.lastPressed = pressed;
        }
    }
}

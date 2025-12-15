package edu.ftcphoenix.fw.input.binding;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

import edu.ftcphoenix.fw.input.Button;
import edu.ftcphoenix.fw.util.LoopClock;

/**
 * Binding manager that maps {@link Button} state to higher-level behavior.
 *
 * <p>Phoenix uses a polled input model:</p>
 * <ol>
 *   <li>Once per loop: update inputs (which advances button edge state).</li>
 *   <li>Once per loop: update bindings (which runs actions based on button state).</li>
 * </ol>
 *
 * <h2>Per-cycle idempotency</h2>
 * <p>{@link #update(LoopClock)} is <b>idempotent by {@link LoopClock#cycle()}</b>.
 * If called twice in the same loop cycle, the second call is a no-op. This prevents
 * nested or layered code from double-firing actions.</p>
 *
 * <p><b>Important:</b> Call {@code Gamepads.update(clock)} (or {@code Button.updateAllRegistered(clock)})
 * <em>before</em> calling {@link #update(LoopClock)}, so {@link Button#onPress()} /
 * {@link Button#onRelease()} reflect the current cycle.</p>
 */
public final class Bindings {

    // ---------------------------------------------------------------------------------------------
    // Binding record types
    // ---------------------------------------------------------------------------------------------

    private static final class PressBinding {
        final Button button;
        final Runnable action;

        PressBinding(Button button, Runnable action) {
            this.button = button;
            this.action = action;
        }
    }

    private static final class WhileHeldBinding {
        final Button button;
        final Runnable whileHeld;
        final Runnable onRelease; // may be null

        WhileHeldBinding(Button button, Runnable whileHeld, Runnable onRelease) {
            this.button = button;
            this.whileHeld = whileHeld;
            this.onRelease = onRelease;
        }
    }

    private static final class ToggleBinding {
        final Button button;
        final Consumer<Boolean> consumer;
        boolean toggled;

        ToggleBinding(Button button, Consumer<Boolean> consumer) {
            this.button = button;
            this.consumer = consumer;
            this.toggled = false;
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Binding storage
    // ---------------------------------------------------------------------------------------------

    private final List<PressBinding> pressBindings = new ArrayList<>();
    private final List<WhileHeldBinding> whileHeldBindings = new ArrayList<>();
    private final List<ToggleBinding> toggleBindings = new ArrayList<>();

    /**
     * Tracks which loop cycle we last updated for, to prevent double-firing actions
     * if update() is accidentally called more than once per loop cycle.
     */
    private long lastUpdatedCycle = Long.MIN_VALUE;

    /**
     * Register an action to run once whenever the given button is pressed (rising edge).
     *
     * @param button button to monitor (non-null)
     * @param action action to run once per press (non-null)
     */
    public void onPress(Button button, Runnable action) {
        pressBindings.add(new PressBinding(
                Objects.requireNonNull(button, "button is required"),
                Objects.requireNonNull(action, "action is required")
        ));
    }

    /**
     * Register an action to run once per loop while the given button is held.
     *
     * @param button    button to monitor (non-null)
     * @param whileHeld action to run every loop while held (non-null)
     */
    public void whileHeld(Button button, Runnable whileHeld) {
        whileHeld(button, whileHeld, null);
    }

    /**
     * Register actions to run while a button is held, and once when it is released.
     *
     * <ul>
     *   <li>If {@link Button#isHeld()} is true, {@code whileHeld} is executed.</li>
     *   <li>If {@link Button#onRelease()} is true and {@code onRelease} is non-null,
     *       {@code onRelease} is executed once.</li>
     * </ul>
     *
     * @param button    button to monitor (non-null)
     * @param whileHeld action to run every loop while held (non-null)
     * @param onRelease action to run once on release (may be null)
     */
    public void whileHeld(Button button, Runnable whileHeld, Runnable onRelease) {
        whileHeldBindings.add(new WhileHeldBinding(
                Objects.requireNonNull(button, "button is required"),
                Objects.requireNonNull(whileHeld, "whileHeld action is required"),
                onRelease
        ));
    }

    /**
     * Register a toggle: flip a boolean each time the button is pressed (rising edge) and
     * deliver the new value to the consumer.
     *
     * @param button   button to monitor (non-null)
     * @param consumer consumer that receives the new toggle state (non-null)
     */
    public void toggle(Button button, Consumer<Boolean> consumer) {
        toggleBindings.add(new ToggleBinding(
                Objects.requireNonNull(button, "button is required"),
                Objects.requireNonNull(consumer, "consumer is required")
        ));
    }

    /**
     * Remove all registered bindings from this instance.
     *
     * <p>This does not unregister any {@link Button}s from the global registry.</p>
     */
    public void clear() {
        pressBindings.clear();
        whileHeldBindings.clear();
        toggleBindings.clear();
        lastUpdatedCycle = Long.MIN_VALUE;
    }

    /**
     * Poll all registered bindings and trigger their actions as appropriate.
     *
     * <p>Idempotent by {@link LoopClock#cycle()}.</p>
     *
     * @param clock loop clock (non-null; advanced once per OpMode loop cycle)
     */
    public void update(LoopClock clock) {
        Objects.requireNonNull(clock, "clock is required");

        long c = clock.cycle();
        if (c == lastUpdatedCycle) {
            return; // already updated this cycle
        }
        lastUpdatedCycle = c;

        // One-shot press bindings
        for (int i = 0; i < pressBindings.size(); i++) {
            PressBinding b = pressBindings.get(i);
            if (b.button.onPress()) {
                b.action.run();
            }
        }

        // While-held bindings
        for (int i = 0; i < whileHeldBindings.size(); i++) {
            WhileHeldBinding b = whileHeldBindings.get(i);

            if (b.button.isHeld()) {
                b.whileHeld.run();
            }

            if (b.onRelease != null && b.button.onRelease()) {
                b.onRelease.run();
            }
        }

        // Toggle bindings
        for (int i = 0; i < toggleBindings.size(); i++) {
            ToggleBinding b = toggleBindings.get(i);
            if (b.button.onPress()) {
                b.toggled = !b.toggled;
                b.consumer.accept(b.toggled);
            }
        }
    }
}

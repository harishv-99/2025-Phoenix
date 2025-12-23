package edu.ftcphoenix.fw.input;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.BooleanSupplier;

import edu.ftcphoenix.fw.core.time.LoopClock;

/**
 * Logical button with built-in edge detection and a global registry.
 *
 * <p>Phoenix uses a polled input model:</p>
 * <ol>
 *   <li>Create {@link Button} instances (typically via {@link #of(BooleanSupplier)}).</li>
 *   <li>Once per OpMode loop, call {@link #updateAllRegistered(LoopClock)}.</li>
 *   <li>Query buttons via {@link #onPress()}, {@link #onRelease()}, {@link #isHeld()}.</li>
 * </ol>
 *
 * <h2>Edge semantics</h2>
 * <ul>
 *   <li>{@link #onPress()} is true for exactly one loop on a rising edge.</li>
 *   <li>{@link #onRelease()} is true for exactly one loop on a falling edge.</li>
 *   <li>{@link #isHeld()} is true every loop while the button is down.</li>
 * </ul>
 *
 * <h2>Per-cycle idempotency</h2>
 * <p>{@link #updateAllRegistered(LoopClock)} is <b>idempotent by</b> {@link LoopClock#cycle()}.
 * If called twice in the same loop cycle, the second call is a no-op. This prevents accidental
 * “double update” scenarios (nested menus, layered helpers) from consuming button edges.</p>
 *
 * <p>This requires that your robot code advances a single {@link LoopClock} exactly once per loop
 * cycle and passes that clock to input/binding updates.</p>
 */
public interface Button {

    /**
     * Advance this button's internal state by sampling the underlying raw value.
     *
     * <p>Most code should not call this directly. Instead, call
     * {@link #updateAllRegistered(LoopClock)} once per loop.</p>
     */
    void update();

    /**
     * Rising edge: true only on the first loop where the button transitions
     * from not-held to held.
     */
    boolean onPress();

    /**
     * Falling edge: true only on the first loop where the button transitions
     * from held to not-held.
     */
    boolean onRelease();

    /**
     * Level: true on every loop while the button is currently held.
     */
    boolean isHeld();

    // ---------------------------------------------------------------------------------------------
    // Registry operations
    // ---------------------------------------------------------------------------------------------

    /**
     * Register a button so it participates in {@link #updateAllRegistered(LoopClock)}.
     *
     * <p>Buttons created via {@link #of(BooleanSupplier)} and {@link #constant(boolean)}
     * are automatically registered. This method is mainly for advanced cases where you
     * provide your own {@link Button} implementation.</p>
     */
    static void register(Button button) {
        Registry.register(button);
    }

    /**
     * Update all registered buttons for this loop.
     *
     * <p>Idempotent by {@link LoopClock#cycle()}.</p>
     *
     * @param clock loop clock (non-null, and advanced once per OpMode cycle)
     */
    static void updateAllRegistered(LoopClock clock) {
        Registry.updateAll(clock);
    }

    /**
     * Clear the global registry of all registered buttons.
     *
     * <p>Primarily intended for framework lifecycle management (e.g., when an OpMode
     * starts fresh). Most user code should not call this directly.</p>
     */
    static void clearRegistered() {
        Registry.clear();
    }

    // ---------------------------------------------------------------------------------------------
    // Factory helpers
    // ---------------------------------------------------------------------------------------------

    /**
     * Create a stateful {@link Button} from a raw boolean supplier and automatically
     * register it with the global registry.
     *
     * <p><b>Construction priming:</b> the returned button samples the raw supplier once
     * at construction time to prime its internal state. This prevents a “phantom press”
     * the first time you enter a menu/tester while the physical button is already held.</p>
     *
     * @param raw supplier providing the raw held state (non-null)
     */
    static Button of(BooleanSupplier raw) {
        StatefulButton b = new StatefulButton(raw);
        Registry.register(b);
        return b;
    }

    /**
     * Convenience: create a button that is always held or always released.
     *
     * <p>Edges never fire for constant buttons.</p>
     */
    static Button constant(boolean held) {
        StatefulButton b = new StatefulButton(() -> held);
        Registry.register(b);
        return b;
    }

    // ---------------------------------------------------------------------------------------------
    // Default implementation used by factories
    // ---------------------------------------------------------------------------------------------

    /**
     * Default stateful {@link Button} implementation used by {@link #of(BooleanSupplier)}
     * and {@link #constant(boolean)}.
     */
    final class StatefulButton implements Button {
        private final BooleanSupplier raw;
        private boolean prev;
        private boolean curr;

        /**
         * @param raw supplier providing the raw "is down" state (non-null)
         */
        public StatefulButton(BooleanSupplier raw) {
            this.raw = Objects.requireNonNull(raw, "raw supplier is required");

            // Prime internal state from current raw value to avoid a phantom press
            // on first update when entering a new screen while the button is already held.
            boolean initial = this.raw.getAsBoolean();
            this.prev = initial;
            this.curr = initial;
        }

        @Override
        public void update() {
            prev = curr;
            curr = raw.getAsBoolean();
        }

        @Override
        public boolean onPress() {
            return curr && !prev;
        }

        @Override
        public boolean onRelease() {
            return !curr && prev;
        }

        @Override
        public boolean isHeld() {
            return curr;
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Internal registry
    // ---------------------------------------------------------------------------------------------

    /**
     * Global registry for buttons created via the factories (and any others explicitly registered).
     *
     * <p>Registry updates are idempotent by {@link LoopClock#cycle()}.</p>
     */
    final class Registry {
        private static final List<Button> BUTTONS = new ArrayList<>();
        private static long lastUpdatedCycle = Long.MIN_VALUE;

        private Registry() {
        }

        static void register(Button button) {
            BUTTONS.add(Objects.requireNonNull(button, "button"));
        }

        static void updateAll(LoopClock clock) {
            Objects.requireNonNull(clock, "clock");
            long c = clock.cycle();
            if (c == lastUpdatedCycle) {
                return; // already updated this cycle
            }
            lastUpdatedCycle = c;

            for (Button b : BUTTONS) {
                b.update();
            }
        }

        static void clear() {
            BUTTONS.clear();
            lastUpdatedCycle = Long.MIN_VALUE;
        }
    }
}

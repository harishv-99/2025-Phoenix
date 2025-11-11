package edu.ftcphoenix.fw.input.binding;

import edu.ftcphoenix.fw.input.Axis;
import edu.ftcphoenix.fw.input.Button;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.DoubleConsumer;
import java.util.function.Supplier;

/**
 * Declarative bindings from inputs to actions.
 *
 * <h3>Update contract</h3>
 * Call {@link #update(double)} once per loop <b>after</b> you've updated inputs
 * (i.e., after Gamepads.update(dt)). Bindings are stateless except for local toggles.
 *
 * <h3>Design goals</h3>
 * <ul>
 *   <li>One-liners for common FTC use cases.</li>
 *   <li>No threads; runs in your normal loop.</li>
 *   <li>Composable: add as many bindings as you want, they all execute each tick.</li>
 * </ul>
 *
 * <h3>Examples</h3>
 * <pre>{@code
 * Bindings bind = new Bindings();
 * bind.onPress(shootBtn, shooter::fireOnce);
 * bind.toggle(enableBtn, shooter::setEnabled); // true/false flip each press
 * bind.whileHeld(intakeBtn, () -> intake.setPower(1.0), () -> intake.setPower(0.0));
 * bind.onDoubleTap(altFireBtn, shooter::burst);
 * bind.onLongHoldStart(spinUpBtn.configureLongHold(0.5), shooter::spinUp);
 * bind.whileLongHeld(slowBtn.configureLongHold(0.25), () -> drive.setScale(0.4), () -> drive.setScale(1.0));
 * bind.stream(throttleAxis, drive::setForward); // every tick
 * // in loop:
 * pads.update(dt);
 * bind.update(dt);
 * }</pre>
 */
public final class Bindings {

    private final List<Runnable> tasks = new ArrayList<>();

    // ---------------------------
    // Basic button bindings
    // ---------------------------

    /**
     * Run action on the tick a button transitions from up -> down.
     */
    public Bindings onPress(Button b, Runnable action) {
        require(b);
        require(action);
        tasks.add(() -> {
            if (b.justPressed()) action.run();
        });
        return this;
    }

    /**
     * Run action on the tick a button transitions from down -> up.
     */
    public Bindings onRelease(Button b, Runnable action) {
        require(b);
        require(action);
        tasks.add(() -> {
            if (b.justReleased()) action.run();
        });
        return this;
    }

    /**
     * Toggle a boolean state each time the button is pressed.
     * The consumer is given the new state (true/false).
     */
    public Bindings toggle(Button b, java.util.function.Consumer<Boolean> consumer) {
        require(b);
        require(consumer);
        final boolean[] state = {false};
        tasks.add(() -> {
            if (b.justPressed()) {
                state[0] = !state[0];
                consumer.accept(state[0]);
            }
        });
        return this;
    }

    /**
     * While button is held, run {@code doWhile} every tick; when released, run {@code onStop} once.
     */
    public Bindings whileHeld(Button b, Runnable doWhile, Runnable onStop) {
        require(b);
        require(doWhile);
        require(onStop);
        final boolean[] wasHeld = {false};
        tasks.add(() -> {
            if (b.isDown()) {
                doWhile.run();
                wasHeld[0] = true;
            } else if (wasHeld[0]) {
                onStop.run();
                wasHeld[0] = false;
            }
        });
        return this;
    }

    // ---------------------------
    // Gesture bindings
    // ---------------------------

    /**
     * Run action when a configured double-tap is detected (see Button.configureDoubleTap).
     */
    public Bindings onDoubleTap(Button b, Runnable action) {
        require(b);
        require(action);
        tasks.add(() -> {
            if (b.justDoubleTapped()) action.run();
        });
        return this;
    }

    /**
     * Run action when a configured long-hold first crosses its threshold (edge).
     */
    public Bindings onLongHoldStart(Button b, Runnable action) {
        require(b);
        require(action);
        tasks.add(() -> {
            if (b.longHoldStarted()) action.run();
        });
        return this;
    }

    /**
     * While long-hold condition is true, run {@code doWhile}; on exit, run {@code onStop} once.
     */
    public Bindings whileLongHeld(Button b, Runnable doWhile, Runnable onStop) {
        require(b);
        require(doWhile);
        require(onStop);
        final boolean[] was = {false};
        tasks.add(() -> {
            if (b.isLongHeld()) {
                doWhile.run();
                was[0] = true;
            } else if (was[0]) {
                onStop.run();
                was[0] = false;
            }
        });
        return this;
    }

    // ---------------------------
    // Axis streaming
    // ---------------------------

    /**
     * Stream an axis value to a consumer each tick (e.g., motor power).
     */
    public Bindings stream(Axis axis, DoubleConsumer consumer) {
        require(axis);
        require(consumer);
        tasks.add(() -> consumer.accept(axis.get()));
        return this;
    }

    /**
     * Stream an axis value multiplied by a dynamic scale (e.g., slow mode).
     * Scale supplier is sampled every tick.
     */
    public Bindings streamScaled(Axis axis, Supplier<Double> scale, DoubleConsumer consumer) {
        require(axis);
        require(scale);
        require(consumer);
        tasks.add(() -> consumer.accept(axis.get() * safe(scale.get())));
        return this;
    }

    /**
     * Stream an axis value only while a condition is true (e.g., while a combo chord is active).
     * When condition is false, {@code onBlocked} runs once (use to zero outputs).
     */
    public Bindings streamWhile(Axis axis,
                                BooleanSupplier condition,
                                DoubleConsumer consumer,
                                Runnable onBlocked) {
        require(axis);
        require(condition);
        require(consumer);
        require(onBlocked);
        final boolean[] blocked = {false};
        tasks.add(() -> {
            if (condition.getAsBoolean()) {
                consumer.accept(axis.get());
                blocked[0] = false;
            } else if (!blocked[0]) {
                onBlocked.run();
                blocked[0] = true;
            }
        });
        return this;
    }

    // ---------------------------
    // Lifecycle
    // ---------------------------

    /**
     * Run all bindings for this loop. Call once per loop, after inputs update.
     */
    public void update(double dtSec) {
        // dtSec reserved for future time-aware per-binding filters if needed.
        for (int i = 0, n = tasks.size(); i < n; i++) tasks.get(i).run();
    }

    /**
     * Remove all bindings.
     */
    public void clear() {
        tasks.clear();
    }

    // ---------------------------
    // Utils
    // ---------------------------

    private static <T> T require(T obj) {
        return Objects.requireNonNull(obj, "argument must be non-null");
    }

    private static double safe(Double d) {
        return (d == null || d.isNaN() || d.isInfinite()) ? 1.0 : d;
    }
}

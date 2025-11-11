package edu.ftcphoenix.fw.input.extras;

import edu.ftcphoenix.fw.input.Button;
import edu.ftcphoenix.fw.input.Gamepads;
import edu.ftcphoenix.fw.input.InputRegistry;

import java.util.Objects;

/**
 * Utility builders for composing multiple {@link Button} inputs into higher-level
 * virtual buttons (AND/OR/XOR/NOT) and timing-based <b>chords</b>.
 *
 * <h2>Overview</h2>
 * <p>
 * The methods in this class return <b>DERIVED</b> {@link Button} instances that read
 * the state of other buttons (usually RAW hardware-backed buttons created via {@code Inputs}).
 * All returned buttons automatically register with the provided {@link InputRegistry},
 * so they are updated once per loop during the registry's DERIVED phase.
 * </p>
 *
 * <h2>Update model &amp; contract</h2>
 * <ul>
 *   <li>Call {@link Gamepads#update(double)} exactly once per robot loop. This ensures RAW
 *       buttons update before DERIVED buttons, so edge detection and chords behave predictably.</li>
 *   <li>Prefer using <b>RAW</b> buttons as inputs to these combinators (e.g., those from
 *       {@code Inputs.a(...)} or {@code Inputs.leftBumper(...)}). If you use other DERIVED
 *       buttons as inputs, create them <i>before</i> the combo so registration order maintains
 *       a sensible evaluation sequence within the DERIVED phase.</li>
 *   <li>All returned buttons expose the standard {@link Button} API (e.g., {@link Button#isDown()},
 *       {@link Button#justPressed()}, {@link Button#justReleased()}). Their edges are computed from
 *       the composite/chord logic.</li>
 * </ul>
 *
 * <h2>Typical usage</h2>
 * <pre>{@code
 * InputRegistry reg = pads.registry();
 * Button lb = Inputs.leftBumper(reg, pads.p1());
 * Button a  = Inputs.a(reg, pads.p1());
 *
 * // 1) Simple AND: true while both are held
 * Button lbAndA = Combos.all(reg, lb, a);
 *
 * // 2) Chord with simultaneity: press both within 10 ticks (~200ms @50Hz)
 * Button chord = Combos.chordTicks(reg, 10, lb, a);
 *
 * // 3) Time-based chord: press both within 0.25 seconds (wall-clock)
 * Button chordTime = Combos.chordSec(reg, 0.25, lb, a);
 *
 * // Bind them normally
 * bindings.onPress(chord, shooter::fireOnce);
 * bindings.whileHeld(lbAndA, () -> intake.setPower(1.0), () -> intake.setPower(0.0));
 * }</pre>
 *
 * <h2>When to use what</h2>
 * <ul>
 *   <li><b>Boolean combinators</b> (<i>all/any/none/xor/not</i>): when you want a virtual button that reflects pure
 *       logical relations of held/released states (e.g., "both held" safety).</li>
 *   <li><b>Chords</b> (<i>chordTicks/chordSec</i>): when you care about <i>nearly simultaneous</i> presses to avoid
 *       accidental activation (e.g., LB then A within 200–300ms). Chords <i>latch</i> ON while all are held and drop
 *       as soon as any is released.</li>
 * </ul>
 *
 * <h2>Performance</h2>
 * <p>All methods use constant-time checks per loop; no allocations after construction.</p>
 *
 * <h2>Threading</h2>
 * <p>Designed for single-threaded OpMode loops. Do not call from other threads.</p>
 *
 * @see edu.ftcphoenix.fw.input.Inputs#axisFromButtons(InputRegistry, Button, Button)
 * @see edu.ftcphoenix.fw.input.Inputs#split2DToDpad(InputRegistry, edu.ftcphoenix.fw.input.Axis, edu.ftcphoenix.fw.input.Axis, double, double)
 */
public final class Combos {
    private Combos() {
    }

    // =====================================================================
    // Boolean combinators
    // =====================================================================

    /**
     * Logical AND of all inputs.
     *
     * <p>Returns a DERIVED {@link Button} whose {@link Button#isDown()} is true <b>only</b>
     * while <i>every</i> source button is currently down.</p>
     *
     * <p><b>Edge semantics:</b> The returned button's {@link Button#justPressed()} fires on the first
     * loop where all sources become down simultaneously; {@link Button#justReleased()} fires when any
     * source releases (breaking the conjunction).</p>
     *
     * @param reg     input registry (must be the same registry the sources use)
     * @param buttons one or more source buttons (prefer RAW)
     * @return a DERIVED AND-button
     * @throws IllegalArgumentException if no buttons are provided or any is null
     */
    public static Button all(InputRegistry reg, Button... buttons) {
        require(reg);
        requireButtons(buttons);
        return Button.derived(reg, () -> {
            for (Button b : buttons) if (!b.isDown()) return false;
            return true;
        });
    }

    /**
     * Logical OR of all inputs.
     *
     * <p>True while <i>any</i> source button is down.</p>
     *
     * <p><b>Edge semantics:</b> {@code justPressed()} fires when the first source becomes down while previously
     * none were; {@code justReleased()} fires when the last held source releases.</p>
     *
     * @param reg     input registry (must be the same registry the sources use)
     * @param buttons one or more source buttons
     * @return a DERIVED OR-button
     * @throws IllegalArgumentException if no buttons are provided or any is null
     */
    public static Button any(InputRegistry reg, Button... buttons) {
        require(reg);
        requireButtons(buttons);
        return Button.derived(reg, () -> {
            for (Button b : buttons) if (b.isDown()) return true;
            return false;
        });
    }

    /**
     * Logical NONE (NOR) of all inputs.
     *
     * <p>True while <i>no</i> source buttons are down.</p>
     *
     * @param reg     input registry
     * @param buttons one or more source buttons
     * @return a DERIVED NONE-button
     * @throws IllegalArgumentException if no buttons are provided or any is null
     */
    public static Button none(InputRegistry reg, Button... buttons) {
        require(reg);
        requireButtons(buttons);
        return Button.derived(reg, () -> {
            for (Button b : buttons) if (b.isDown()) return false;
            return true;
        });
    }

    /**
     * Exclusive OR over N buttons.
     *
     * <p>True iff <i>exactly one</i> source is down.</p>
     *
     * @param reg     input registry
     * @param buttons one or more source buttons
     * @return a DERIVED XOR-button
     * @throws IllegalArgumentException if no buttons are provided or any is null
     */
    public static Button xor(InputRegistry reg, Button... buttons) {
        require(reg);
        requireButtons(buttons);
        return Button.derived(reg, () -> {
            int c = 0;
            for (Button b : buttons) if (b.isDown()) c++;
            return c == 1;
        });
    }

    /**
     * Logical NOT of a single button.
     *
     * <p>True while the given button is <i>not</i> down.</p>
     *
     * @param reg input registry
     * @param b   source button
     * @return a DERIVED NOT-button
     * @throws NullPointerException if {@code b} is null
     */
    public static Button not(InputRegistry reg, Button b) {
        require(reg);
        Objects.requireNonNull(b, "button");
        return Button.derived(reg, () -> !b.isDown());
    }

    // =====================================================================
    // Chords (with simultaneity window)
    // =====================================================================

    /**
     * Tick-based chord recognition with latching.
     *
     * <p>The returned button turns <b>ON</b> when:</p>
     * <ol>
     *   <li>All source buttons are currently down, and</li>
     *   <li>The <i>most recent</i> press among them occurred within {@code windowTicks}
     *       loops (ticks) of the other presses.</li>
     * </ol>
     * <p>Once ON, the chord stays ON <b>while all sources remain held</b> and turns OFF as soon as any source is released.</p>
     *
     * <h3>Notes</h3>
     * <ul>
     *   <li>If {@code windowTicks &lt;= 0}, this reduces to a pure AND (no simultaneity requirement).</li>
     *   <li>Tick-based windows scale with loop frequency. At ~50Hz, 8–15 ticks ≈ 160–300ms.</li>
     *   <li>Use this when you want “press nearly together” semantics without relying on wall-clock time.</li>
     * </ul>
     *
     * <h3>Example</h3>
     * <pre>{@code
     * // LB + A within 10 ticks (~200ms @50Hz) to fire
     * Button chord = Combos.chordTicks(reg, 10, lb, a);
     * bindings.onPress(chord, shooter::fireOnce);
     * }</pre>
     *
     * @param reg         input registry
     * @param windowTicks allowed simultaneity window in loop ticks (0 → AND)
     * @param buttons     two or more source buttons (prefer RAW)
     * @return a DERIVED chord button with latching behavior
     * @throws IllegalArgumentException if fewer than one button provided or any is null
     */
    public static Button chordTicks(InputRegistry reg, int windowTicks, Button... buttons) {
        require(reg);
        requireButtons(buttons);
        final int win = Math.max(0, windowTicks);
        return Button.derived(reg, new java.util.function.BooleanSupplier() {
            int ticksSinceAnyPress = 1_000_000; // effectively "infinite" until a press occurs
            boolean latched = false;

            @Override
            public boolean getAsBoolean() {
                // advance per-tick counter (derived is evaluated once per loop)
                ticksSinceAnyPress++;

                boolean anyJustPressed = false;
                boolean allDown = true;
                for (Button b : buttons) {
                    if (b.justPressed()) anyJustPressed = true;
                    if (!b.isDown()) allDown = false;
                }
                if (anyJustPressed) ticksSinceAnyPress = 0;

                if (!latched) {
                    if (allDown && (win == 0 || ticksSinceAnyPress <= win)) {
                        latched = true; // chord formed
                    }
                } else if (!allDown) {
                    latched = false;   // chord broken
                }
                return latched;
            }
        });
    }

    /**
     * Time-based chord recognition with latching (wall-clock).
     *
     * <p>Like {@link #chordTicks(InputRegistry, int, Button...)}, but the simultaneity window is specified
     * in seconds and measured using {@link System#nanoTime()} to decouple from loop dt variations.</p>
     *
     * <h3>When to prefer this</h3>
     * <ul>
     *   <li>If your loop rate varies significantly (telemetry, GC hiccups), seconds-based windows feel more consistent.</li>
     *   <li>Typical values are 0.20–0.35 seconds.</li>
     * </ul>
     *
     * <h3>Example</h3>
     * <pre>{@code
     * // LB + A within 0.25 seconds to activate
     * Button chord = Combos.chordSec(reg, 0.25, lb, a);
     * bindings.onPress(chord, shooter::fireOnce);
     * }</pre>
     *
     * @param reg       input registry
     * @param windowSec allowed simultaneity window in seconds (0 → AND)
     * @param buttons   two or more source buttons (prefer RAW)
     * @return a DERIVED chord button with latching behavior
     * @throws IllegalArgumentException if fewer than one button provided or any is null
     */
    public static Button chordSec(InputRegistry reg, double windowSec, Button... buttons) {
        require(reg);
        requireButtons(buttons);
        final double win = Math.max(0.0, windowSec);
        return Button.derived(reg, new java.util.function.BooleanSupplier() {
            long lastNs = System.nanoTime();
            double sinceAnyPressSec = 1e9; // large until first press
            boolean latched = false;

            @Override
            public boolean getAsBoolean() {
                long now = System.nanoTime();
                double dt = (now - lastNs) / 1e9;
                lastNs = now;
                sinceAnyPressSec += dt;

                boolean anyJustPressed = false;
                boolean allDown = true;
                for (Button b : buttons) {
                    if (b.justPressed()) anyJustPressed = true;
                    if (!b.isDown()) allDown = false;
                }
                if (anyJustPressed) sinceAnyPressSec = 0.0;

                if (!latched) {
                    if (allDown && (win == 0.0 || sinceAnyPressSec <= win)) {
                        latched = true;
                    }
                } else if (!allDown) {
                    latched = false;
                }
                return latched;
            }
        });
    }

    // =====================================================================
    // Internal helpers
    // =====================================================================

    private static void requireButtons(Button[] buttons) {
        if (buttons == null || buttons.length == 0)
            throw new IllegalArgumentException("at least one button required");
        for (int i = 0; i < buttons.length; i++) {
            if (buttons[i] == null) throw new IllegalArgumentException("button[" + i + "] is null");
        }
    }

    private static <T> T require(T t) {
        return Objects.requireNonNull(t, "argument must be non-null");
    }
}

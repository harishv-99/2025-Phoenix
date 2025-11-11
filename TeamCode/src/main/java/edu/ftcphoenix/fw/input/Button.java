package edu.ftcphoenix.fw.input;

import org.firstinspires.ftc.robotcore.external.Telemetry;

import java.util.Objects;
import java.util.function.BooleanSupplier;

/**
 * Edge-aware boolean input with optional gestures (double-tap, long-hold).
 *
 * <h2>Design</h2>
 * <ul>
 *   <li>Implements {@link InputRegistry.Updatable} and self-registers with the provided registry.</li>
 *   <li>Create as RAW for hardware-backed buttons or DERIVED for combos/virtuals.</li>
 *   <li>Tracks prev/curr + edges: {@link #justPressed()}, {@link #justReleased()}.</li>
 *   <li>Optional gestures:
 *     <ul>
 *       <li><b>Double-tap</b>: edge when two presses occur within a configured window.</li>
 *       <li><b>Long-hold</b>: edge when held time crosses a configured threshold; also exposes {@link #isLongHeld()}.</li>
 *     </ul>
 *   </li>
 *   <li><b>Gesture policy</b>: control interaction between long-hold and double-tap.</li>
 * </ul>
 *
 * <h2>Contract</h2>
 * <ul>
 *   <li>Construct via {@link #raw(InputRegistry, BooleanSupplier)} or
 *       {@link #derived(InputRegistry, BooleanSupplier)}.</li>
 *   <li>Registry must be updated exactly once per loop to keep edge/gesture timing correct.</li>
 * </ul>
 */
public final class Button implements InputRegistry.Updatable {

    /**
     * Policy for how gestures interact.
     */
    public enum GesturePolicy {
        /**
         * Long-hold and double-tap are independent; a long-hold won't prevent a later
         * double-tap edge (if timing still matches).
         */
        INDEPENDENT,

        /**
         * Once a long-hold starts for the current press, the double-tap detector is
         * suppressed until the button is released (prevents accidental DT after LH).
         */
        LONG_HOLD_SUPPRESSES_DOUBLE_TAP
    }

    private final InputRegistry.UpdatePriority priority;
    private final BooleanSupplier sampler;

    private boolean prev = false;
    private boolean curr = false;

    // Gesture configuration (<=0 disables)
    private double doubleTapWindowSec = -1.0;
    private double longHoldThresholdSec = -1.0;

    // Gesture policy
    private GesturePolicy gesturePolicy = GesturePolicy.INDEPENDENT;

    // Gesture state
    private double timeSinceLastPressSec = 1e9; // counts between presses for DT
    private double timeSincePressHeldSec = 1e9; // counts duration of current hold
    private boolean longHoldEdgeFired = false;  // ensure long-hold edge fires once per press
    private boolean suppressDoubleTap = false;  // used by policy=LONG_HOLD_SUPPRESSES_DOUBLE_TAP

    // Per-tick edges
    private boolean doubleTapEdge = false;
    private boolean longHoldEdge = false;

    private Button(InputRegistry registry,
                   InputRegistry.UpdatePriority priority,
                   BooleanSupplier sampler) {
        this.priority = Objects.requireNonNull(priority, "priority");
        this.sampler = Objects.requireNonNull(sampler, "sampler");
        Objects.requireNonNull(registry, "registry").register(this);
    }

    /**
     * Create a RAW (hardware-backed) button.
     */
    public static Button raw(InputRegistry registry, BooleanSupplier sampler) {
        return new Button(registry, InputRegistry.UpdatePriority.RAW, sampler);
    }

    /**
     * Create a DERIVED (virtual/composed) button.
     */
    public static Button derived(InputRegistry registry, BooleanSupplier sampler) {
        return new Button(registry, InputRegistry.UpdatePriority.DERIVED, sampler);
    }

    @Override
    public InputRegistry.UpdatePriority priority() {
        return priority;
    }

    @Override
    public void update(double dtSec) {
        // Advance timers
        timeSinceLastPressSec += dtSec;
        if (curr) timeSincePressHeldSec += dtSec;

        // Keep prior, sample current
        prev = curr;
        curr = sampler.getAsBoolean();

        // Reset per-tick edges
        doubleTapEdge = false;
        longHoldEdge = false;

        // On press
        if (!prev && curr) {
            // Double-tap detection (unless suppressed by policy)
            if (!suppressDoubleTap && doubleTapWindowSec > 0 && timeSinceLastPressSec <= doubleTapWindowSec) {
                doubleTapEdge = true;
            }
            timeSinceLastPressSec = 0.0;

            // Start hold timing for this press
            timeSincePressHeldSec = 0.0;
            longHoldEdgeFired = false;
            // Reset suppression for this new press
            suppressDoubleTap = false;
        }

        // Long-hold threshold crossing
        if (curr && longHoldThresholdSec > 0 && !longHoldEdgeFired &&
                timeSincePressHeldSec >= longHoldThresholdSec) {
            longHoldEdge = true;
            longHoldEdgeFired = true;

            // Apply policy: once LH starts, suppress DT until release
            if (gesturePolicy == GesturePolicy.LONG_HOLD_SUPPRESSES_DOUBLE_TAP) {
                suppressDoubleTap = true;
                // Also push DT timer far away so release won't be misinterpreted as part of a future DT
                timeSinceLastPressSec = 1e9;
            }
        }

        // On release
        if (prev && !curr) {
            // After release, allow DT again on next press (unless policy dictates lingering suppression)
            // We clear suppression here so the *next* press can qualify for DT normally.
            suppressDoubleTap = false;
        }
    }

    // ---------------------------
    // Basic edges
    // ---------------------------

    /**
     * True while the button is held down this tick.
     */
    public boolean isDown() {
        return curr;
    }

    /**
     * True while the button was held down on the previous tick.
     */
    public boolean wasDown() {
        return prev;
    }

    /**
     * True only on the tick the button transitions from up→down.
     */
    public boolean justPressed() {
        return !prev && curr;
    }

    /**
     * True only on the tick the button transitions from down→up.
     */
    public boolean justReleased() {
        return prev && !curr;
    }

    // ---------------------------
    // Gestures
    // ---------------------------

    /**
     * Enable/disable double-tap recognition. Set windowSec <= 0 to disable.
     */
    public Button configureDoubleTap(double windowSec) {
        this.doubleTapWindowSec = windowSec;
        return this;
    }

    /**
     * Enable/disable long-hold recognition. Set thresholdSec <= 0 to disable.
     */
    public Button configureLongHold(double thresholdSec) {
        this.longHoldThresholdSec = thresholdSec;
        return this;
    }

    /**
     * Configure how long-hold interacts with double-tap. Default: {@link GesturePolicy#INDEPENDENT}.
     */
    public Button gesturePolicy(GesturePolicy policy) {
        this.gesturePolicy = (policy != null) ? policy : GesturePolicy.INDEPENDENT;
        return this;
    }

    /**
     * True only on the tick where the second tap is recognized.
     */
    public boolean justDoubleTapped() {
        return doubleTapEdge;
    }

    /**
     * True only on the tick when the button first exceeds the long-hold threshold.
     */
    public boolean longHoldStarted() {
        return longHoldEdge;
    }

    /**
     * True while the button is currently held longer than the configured threshold.
     */
    public boolean isLongHeld() {
        return longHoldThresholdSec > 0 && curr && timeSincePressHeldSec >= longHoldThresholdSec;
    }

    // ---------------------------
    // Telemetry
    // ---------------------------

    /**
     * Convenience telemetry line with edges/gestures for quick debugging.
     */
    public void addTelemetry(Telemetry t, String label) {
        if (t == null) return;
        t.addData(label + ".down", curr)
                .addData(label + ".justP", justPressed())
                .addData(label + ".justR", justReleased())
                .addData(label + ".dblTap", doubleTapEdge)
                .addData(label + ".longEdge", longHoldEdge)
                .addData(label + ".heldSec", curr ? timeSincePressHeldSec : 0.0)
                .addData(label + ".policy", gesturePolicy.name());
    }
}

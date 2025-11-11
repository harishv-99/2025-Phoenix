package edu.ftcphoenix.fw.input.extras;

import edu.ftcphoenix.fw.input.Button;
import edu.ftcphoenix.fw.input.InputDefaults;

import java.util.Objects;

/**
 * One-liner helpers to configure {@link Button} gestures using {@link InputDefaults}.
 *
 * <h2>Why</h2>
 * When teams enable both <em>double-tap</em> and <em>long-hold</em> on the same button,
 * timing overlaps can surprise you. These presets apply the recommended windows/policy:
 * DT window < LH threshold + {@link Button.GesturePolicy#LONG_HOLD_SUPPRESSES_DOUBLE_TAP}.
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * // Use project defaults
 * GesturePresets.doubleTapOnly(fireBtn);         // DT using defaults.doubleTapWindowSec
 * GesturePresets.longHoldOnly(slowBtn);          // LH using defaults.longHoldSec
 * GesturePresets.doubleTapAndHold(modeBtn);      // DT+LH using coexistence defaults + policy
 *
 * // Or pass a tuned InputDefaults instance:
 * InputDefaults tuned = InputDefaults.create()
 *     .doubleTapWindowSec(0.22)
 *     .longHoldSec(0.40)
 *     .coexistDoubleTapSec(0.22)
 *     .coexistLongHoldSec(0.40)
 *     .coexistPolicy(Button.GesturePolicy.LONG_HOLD_SUPPRESSES_DOUBLE_TAP);
 * GesturePresets.doubleTapAndHold(tuned, modeBtn);
 *
 * // Works with varargs to configure many buttons at once:
 * GesturePresets.doubleTapOnly(fireA, fireB, fireC);
 * }</pre>
 *
 * <h2>Policy</h2>
 * For the coexistence preset, we set
 * {@link Button.GesturePolicy#LONG_HOLD_SUPPRESSES_DOUBLE_TAP} so once long-hold starts,
 * any later tap in that cycle will not create a double-tap edge.
 *
 * <h2>Contract</h2>
 * Call these during init/setup (before your loop). They simply call the fluent configuration
 * methods on {@link Button} and return the same instance for chaining.
 */
public final class GesturePresets {
    private GesturePresets() {
    }

    // =====================================================================
    // SINGLE-GESTURE PRESETS
    // =====================================================================

    /**
     * Configure the button for double-tap only using {@link InputDefaults#standard()}.
     */
    public static Button doubleTapOnly(Button b) {
        return doubleTapOnly(InputDefaults.standard(), b);
    }

    /**
     * Configure the button for double-tap only using the provided defaults.
     */
    public static Button doubleTapOnly(InputDefaults d, Button b) {
        require(d, b);
        return b.configureDoubleTap(d.doubleTapWindowSec)
                .configureLongHold(0.0) // disable LH
                .gesturePolicy(Button.GesturePolicy.INDEPENDENT);
    }

    /**
     * Configure multiple buttons for double-tap only (standard defaults).
     */
    public static void doubleTapOnly(Button... buttons) {
        for (Button b : buttons) doubleTapOnly(InputDefaults.standard(), b);
    }

    /**
     * Configure the button for long-hold only using {@link InputDefaults#standard()}.
     */
    public static Button longHoldOnly(Button b) {
        return longHoldOnly(InputDefaults.standard(), b);
    }

    /**
     * Configure the button for long-hold only using the provided defaults.
     */
    public static Button longHoldOnly(InputDefaults d, Button b) {
        require(d, b);
        return b.configureDoubleTap(0.0) // disable DT
                .configureLongHold(d.longHoldSec)
                .gesturePolicy(Button.GesturePolicy.INDEPENDENT);
    }

    /**
     * Configure multiple buttons for long-hold only (standard defaults).
     */
    public static void longHoldOnly(Button... buttons) {
        for (Button b : buttons) longHoldOnly(InputDefaults.standard(), b);
    }

    // =====================================================================
    // COEXISTENCE PRESET (DT + LH on the same button)
    // =====================================================================

    /**
     * Configure button for both double-tap and long-hold using {@link InputDefaults#standard()}
     * coexistence guidance: short DT window, longer LH threshold, and suppression policy.
     */
    public static Button doubleTapAndHold(Button b) {
        return doubleTapAndHold(InputDefaults.standard(), b);
    }

    /**
     * Configure button for both double-tap and long-hold using the provided defaults:
     * <ul>
     *   <li>{@link InputDefaults#coexistDoubleTapSec} for DT window</li>
     *   <li>{@link InputDefaults#coexistLongHoldSec} for LH threshold</li>
     *   <li>{@link InputDefaults#coexistPolicy} for gesture interaction</li>
     * </ul>
     *
     * <p><b>Note:</b> We intentionally do not auto “fix up” inversions (e.g., if DT ≥ LH).
     * If you supply conflicting values, both gestures may fire; set the defaults sensibly
     * (DT &lt; LH) or rely on the policy to suppress DT after LH.</p>
     */
    public static Button doubleTapAndHold(InputDefaults d, Button b) {
        require(d, b);
        return b.configureDoubleTap(d.coexistDoubleTapSec)
                .configureLongHold(d.coexistLongHoldSec)
                .gesturePolicy(d.coexistPolicy);
    }

    /**
     * Batch version for coexistence preset (standard defaults).
     */
    public static void doubleTapAndHold(Button... buttons) {
        for (Button b : buttons) doubleTapAndHold(InputDefaults.standard(), b);
    }

    // =====================================================================
    // utils
    // =====================================================================

    private static void require(InputDefaults d, Button b) {
        Objects.requireNonNull(d, "defaults");
        Objects.requireNonNull(b, "button");
    }
}

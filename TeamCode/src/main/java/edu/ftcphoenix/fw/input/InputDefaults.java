package edu.ftcphoenix.fw.input;

/**
 * Centralized default tuning for the Phoenix input layer.
 *
 * <h2>Purpose</h2>
 * Most FTC robots want the same shaping/gestures. These constants power the
 * higher-level {@code DriverKit} and {@code GesturePresets} so your robot code can be concise
 * without retyping deadband/expo/slew/hysteresis and gesture timings in each OpMode.
 *
 * <h2>What’s new</h2>
 * Includes <b>coexistence</b> guidance for when a single button uses <i>both</i> double-tap and long-hold:
 * by default we set a smaller DT window, a larger LH threshold, and
 * {@link Button.GesturePolicy#LONG_HOLD_SUPPRESSES_DOUBLE_TAP} to avoid accidental overlap.
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * // 1) Stick shaping and single-gesture defaults are applied by DriverKit automatically.
 * DriverKit kit = DriverKit.of(pads); // uses InputDefaults.standard()
 *
 * // 2) If you want to change the global defaults for your robot:
 * InputDefaults d = InputDefaults.create()
 *     .deadband(0.07)
 *     .expo(2.2)
 *     .slew(10.0)
 *     .hysteresis(0.12, 0.22)
 *     .doubleTapWindowSec(0.22)
 *     .longHoldSec(0.40)
 *     .coexistDoubleTapSec(0.22)
 *     .coexistLongHoldSec(0.40)
 *     .coexistPolicy(Button.GesturePolicy.LONG_HOLD_SUPPRESSES_DOUBLE_TAP);
 * DriverKit tuned = DriverKit.of(pads, d);
 * }</pre>
 *
 * <h2>Design notes</h2>
 * <ul>
 *   <li>Immutable value object; fluent "builder" setters return a new instance.</li>
 *   <li>Numbers are conservative defaults that feel good on most drivetrains.</li>
 *   <li>Coexistence settings are used by helper utilities when both gestures are enabled on one button.</li>
 * </ul>
 */
public final class InputDefaults {

    // ---- Stick shaping ----
    /**
     * Default stick/triggers deadband in unit interval.
     */
    public final double deadband;
    /**
     * Default input curve exponent (aka squaring): 1=no curve; 2=classic "square".
     */
    public final double expo;
    /**
     * Default slew limit in units-per-second (applied to axes prone to twitch).
     */
    public final double slewPerSec;

    // ---- Axis→button hysteresis (for DPAD-like splits) ----
    /**
     * Default hysteresis low threshold for axis→button splitting.
     */
    public final double hystLow;
    /**
     * Default hysteresis high threshold for axis→button splitting.
     */
    public final double hystHigh;

    // ---- Single-gesture defaults ----
    /**
     * Default double-tap detection window in seconds (<=0 disables).
     */
    public final double doubleTapWindowSec;
    /**
     * Default long-hold threshold in seconds (<=0 disables).
     */
    public final double longHoldSec;

    // ---- Coexistence defaults (applied when a single button uses both gestures) ----
    /**
     * Recommended DT window (s) when DT and LH are both enabled on the same button.
     */
    public final double coexistDoubleTapSec;
    /**
     * Recommended LH threshold (s) when DT and LH are both enabled on the same button.
     */
    public final double coexistLongHoldSec;
    /**
     * Recommended policy when both gestures coexist.
     */
    public final Button.GesturePolicy coexistPolicy;

    private InputDefaults(double deadband, double expo, double slewPerSec,
                          double hystLow, double hystHigh,
                          double doubleTapWindowSec, double longHoldSec,
                          double coexistDoubleTapSec, double coexistLongHoldSec,
                          Button.GesturePolicy coexistPolicy) {
        this.deadband = deadband;
        this.expo = expo;
        this.slewPerSec = slewPerSec;
        this.hystLow = hystLow;
        this.hystHigh = hystHigh;
        this.doubleTapWindowSec = doubleTapWindowSec;
        this.longHoldSec = longHoldSec;
        this.coexistDoubleTapSec = coexistDoubleTapSec;
        this.coexistLongHoldSec = coexistLongHoldSec;
        this.coexistPolicy = coexistPolicy == null
                ? Button.GesturePolicy.LONG_HOLD_SUPPRESSES_DOUBLE_TAP
                : coexistPolicy;
    }

    // ---------------------------
    // Factory & built-in defaults
    // ---------------------------

    /**
     * Defaults tuned for typical FTC drivers (safe, responsive, and overlap-safe).
     */
    public static InputDefaults standard() {
        return new InputDefaults(
                0.05,   // deadband
                2.0,    // expo
                8.0,    // slew per second
                0.15,   // hysteresis low
                0.25,   // hysteresis high

                // Single-gesture defaults (fine if only one gesture used on a button)
                0.30,   // double-tap window (s)
                0.25,   // long-hold threshold (s)

                // Coexistence defaults (recommended when both DT + LH are on the same button)
                0.22,   // DT window (s) → shorter than LH to avoid overlap
                0.40,   // LH threshold (s) → deliberate hold
                Button.GesturePolicy.LONG_HOLD_SUPPRESSES_DOUBLE_TAP
        );
    }

    /**
     * Start a mutable builder initialized to {@link #standard()}.
     */
    public static InputDefaults create() {
        return standard();
    }

    // ---------------------------
    // Fluent "builder" setters
    // ---------------------------

    /**
     * Copy with a new deadband.
     */
    public InputDefaults deadband(double v) {
        return new InputDefaults(v, expo, slewPerSec, hystLow, hystHigh,
                doubleTapWindowSec, longHoldSec,
                coexistDoubleTapSec, coexistLongHoldSec, coexistPolicy);
    }

    /**
     * Copy with a new expo exponent.
     */
    public InputDefaults expo(double v) {
        return new InputDefaults(deadband, v, slewPerSec, hystLow, hystHigh,
                doubleTapWindowSec, longHoldSec,
                coexistDoubleTapSec, coexistLongHoldSec, coexistPolicy);
    }

    /**
     * Copy with a new slew rate in units per second.
     */
    public InputDefaults slew(double unitsPerSec) {
        return new InputDefaults(deadband, expo, unitsPerSec, hystLow, hystHigh,
                doubleTapWindowSec, longHoldSec,
                coexistDoubleTapSec, coexistLongHoldSec, coexistPolicy);
    }

    /**
     * Copy with new hysteresis thresholds (low <= high).
     */
    public InputDefaults hysteresis(double low, double high) {
        double lo = Math.max(0.0, Math.min(low, high));
        double hi = Math.max(lo, high);
        return new InputDefaults(deadband, expo, slewPerSec, lo, hi,
                doubleTapWindowSec, longHoldSec,
                coexistDoubleTapSec, coexistLongHoldSec, coexistPolicy);
    }

    /**
     * Copy with a new default double-tap window in seconds (<=0 disables).
     */
    public InputDefaults doubleTapWindowSec(double sec) {
        return new InputDefaults(deadband, expo, slewPerSec, hystLow, hystHigh,
                sec, longHoldSec,
                coexistDoubleTapSec, coexistLongHoldSec, coexistPolicy);
    }

    /**
     * Copy with a new default long-hold threshold in seconds (<=0 disables).
     */
    public InputDefaults longHoldSec(double sec) {
        return new InputDefaults(deadband, expo, slewPerSec, hystLow, hystHigh,
                doubleTapWindowSec, sec,
                coexistDoubleTapSec, coexistLongHoldSec, coexistPolicy);
    }

    /**
     * Copy with a new coexistence DT window (s).
     */
    public InputDefaults coexistDoubleTapSec(double sec) {
        return new InputDefaults(deadband, expo, slewPerSec, hystLow, hystHigh,
                doubleTapWindowSec, longHoldSec,
                sec, coexistLongHoldSec, coexistPolicy);
    }

    /**
     * Copy with a new coexistence LH threshold (s).
     */
    public InputDefaults coexistLongHoldSec(double sec) {
        return new InputDefaults(deadband, expo, slewPerSec, hystLow, hystHigh,
                doubleTapWindowSec, longHoldSec,
                coexistDoubleTapSec, sec, coexistPolicy);
    }

    /**
     * Copy with a new coexistence gesture policy.
     */
    public InputDefaults coexistPolicy(Button.GesturePolicy policy) {
        return new InputDefaults(deadband, expo, slewPerSec, hystLow, hystHigh,
                doubleTapWindowSec, longHoldSec,
                coexistDoubleTapSec, coexistLongHoldSec, policy);
    }
}

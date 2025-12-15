package edu.ftcphoenix.fw.tester.ui;

import org.firstinspires.ftc.robotcore.external.Telemetry;

import java.util.Locale;
import java.util.function.BooleanSupplier;

import edu.ftcphoenix.fw.input.Axis;
import edu.ftcphoenix.fw.input.Button;
import edu.ftcphoenix.fw.input.binding.Bindings;
import edu.ftcphoenix.fw.util.MathUtil;

/**
 * Reusable "integer target" controller for tester UIs.
 *
 * <p>This is the integer sibling of {@link ScalarTuner}. It exists to avoid duplicating
 * the same bindings/telemetry for things like:
 * <ul>
 *   <li>encoder position targets (ticks)</li>
 *   <li>velocity targets (ticks/sec or RPM-scaled ints)</li>
 *   <li>index/selection values</li>
 * </ul>
 *
 * <h2>What it owns</h2>
 * <ul>
 *   <li>An integer {@code target} clamped to [{@code min}, {@code max}]</li>
 *   <li>Fine/coarse step sizing</li>
 *   <li>Optional enabled/arm toggle (if you want it)</li>
 *   <li>Optional analog axis "nudge" that changes the target continuously</li>
 * </ul>
 */
public final class IntTuner {

    private final String label;

    private int min;
    private int max;

    private int fineStep;
    private int coarseStep;

    private boolean enabledSupported = true;
    private boolean enabled = false;

    private boolean fine = true;

    private int target;
    private int disabledValue;

    // Optional axis nudge
    private Axis axis = null;
    private double axisDeadband = 0.08;

    // ticks/sec at full deflection for fine vs coarse
    private double axisFineRatePerSec = 0.0;
    private double axisCoarseRatePerSec = 0.0;

    // carry for fractional tick accumulation
    private double axisCarry = 0.0;

    /**
     * @param label         display label (e.g., "TargetTicks")
     * @param min           minimum target (inclusive)
     * @param max           maximum target (inclusive)
     * @param fineStep      fine step increment
     * @param coarseStep    coarse step increment
     * @param initialTarget initial target (clamped)
     */
    public IntTuner(String label,
                    int min,
                    int max,
                    int fineStep,
                    int coarseStep,
                    int initialTarget) {

        this.label = (label == null) ? "Value" : label;

        // Robust if min/max are swapped.
        if (min > max) {
            int t = min;
            min = max;
            max = t;
        }

        this.min = min;
        this.max = max;

        this.fineStep = Math.max(1, Math.abs(fineStep));
        this.coarseStep = Math.max(1, Math.abs(coarseStep));

        this.target = MathUtil.clamp(initialTarget, min, max);

        // Default disabled value is 0 clamped into range.
        this.disabledValue = MathUtil.clamp(0, min, max);
    }

    // ---------------------------------------------------------------------------------------------
    // Configuration
    // ---------------------------------------------------------------------------------------------

    /** If enable is not supported, this tuner is always considered enabled. */
    public IntTuner setEnableSupported(boolean supported) {
        this.enabledSupported = supported;
        if (!supported) {
            this.enabled = true;
        }
        return this;
    }

    /** Value returned by {@link #applied()} when disabled. */
    public IntTuner setDisabledValue(int value) {
        this.disabledValue = MathUtil.clamp(value, min, max);
        return this;
    }

    /**
     * Attach an analog axis that continuously adjusts the target.
     *
     * <p>Example: for encoder targets, you might want:
     * <ul>
     *   <li>fineRate = 250 ticks/sec</li>
     *   <li>coarseRate = 1500 ticks/sec</li>
     * </ul>
     * so holding the stick moves the target smoothly.</p>
     *
     * @param axis              axis to read (e.g., gamepads.p1().leftY())
     * @param deadband          raw axis deadband
     * @param fineRatePerSec    ticks/sec at full deflection in fine mode
     * @param coarseRatePerSec  ticks/sec at full deflection in coarse mode
     */
    public IntTuner attachAxisNudge(Axis axis,
                                    double deadband,
                                    double fineRatePerSec,
                                    double coarseRatePerSec) {
        this.axis = axis;
        this.axisDeadband = Math.abs(deadband);
        this.axisFineRatePerSec = Math.max(0.0, fineRatePerSec);
        this.axisCoarseRatePerSec = Math.max(0.0, coarseRatePerSec);
        return this;
    }

    // ---------------------------------------------------------------------------------------------
    // State access
    // ---------------------------------------------------------------------------------------------

    public boolean isEnabled() {
        return enabledSupported ? enabled : true;
    }

    public boolean isFine() {
        return fine;
    }

    public int target() {
        return target;
    }

    public int step() {
        return fine ? fineStep : coarseStep;
    }

    public void setTarget(int target) {
        this.target = MathUtil.clamp(target, min, max);
    }

    public void inc() {
        setTarget(target + step());
    }

    public void dec() {
        setTarget(target - step());
    }

    public void zero() {
        setTarget(disabledValue);
    }

    public void toggleFine() {
        fine = !fine;
    }

    public void toggleEnabled() {
        if (!enabledSupported) return;
        enabled = !enabled;
    }

    /**
     * Apply the enabled gate to the target (if you use enabled semantics).
     * If you don't want enabled semantics, call {@link #setEnableSupported(boolean)} with false.
     */
    public int applied() {
        return isEnabled() ? target : disabledValue;
    }

    /**
     * Update the target from the attached axis nudge, if configured.
     *
     * @param dtSec  time since last loop (seconds)
     * @param active gate; if false, axis nudge is ignored
     */
    public void updateFromAxis(double dtSec, BooleanSupplier active) {
        if (axis == null) return;
        if (active != null && !active.getAsBoolean()) return;

        double raw = axis.get();
        if (Math.abs(raw) <= axisDeadband) {
            axisCarry = 0.0;
            return;
        }

        double rate = fine ? axisFineRatePerSec : axisCoarseRatePerSec;
        if (rate <= 0.0) return;

        axisCarry += raw * rate * Math.max(0.0, dtSec);

        int delta = 0;
        if (axisCarry >= 1.0) {
            delta = (int) Math.floor(axisCarry);
        } else if (axisCarry <= -1.0) {
            delta = (int) Math.ceil(axisCarry); // negative
        }

        if (delta != 0) {
            axisCarry -= delta;
            setTarget(target + delta);
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Bindings
    // ---------------------------------------------------------------------------------------------

    /**
     * Bind a standard set of controls.
     *
     * <p>All actions are gated by {@code active}. Pass something like {@code () -> ready}
     * so picker/menu inputs don't conflict.</p>
     */
    public void bind(Bindings bindings,
                     Button enableToggle,
                     Button fineToggle,
                     Button incButton,
                     Button decButton,
                     Button zeroButton,
                     BooleanSupplier active) {

        BooleanSupplier ok = (active == null) ? () -> true : active;

        if (enableToggle != null) {
            bindings.onPress(enableToggle, () -> {
                if (!ok.getAsBoolean()) return;
                toggleEnabled();
            });
        }

        if (fineToggle != null) {
            bindings.onPress(fineToggle, () -> {
                if (!ok.getAsBoolean()) return;
                toggleFine();
            });
        }

        if (incButton != null) {
            bindings.onPress(incButton, () -> {
                if (!ok.getAsBoolean()) return;
                inc();
            });
        }

        if (decButton != null) {
            bindings.onPress(decButton, () -> {
                if (!ok.getAsBoolean()) return;
                dec();
            });
        }

        if (zeroButton != null) {
            bindings.onPress(zeroButton, () -> {
                if (!ok.getAsBoolean()) return;
                zero();
            });
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Telemetry
    // ---------------------------------------------------------------------------------------------

    public void render(Telemetry t) {
        t.addLine(String.format(Locale.US,
                "%s: target=%d applied=%d range=[%d, %d]",
                label, target(), applied(), min, max));

        t.addLine(String.format(Locale.US,
                "Enabled=%s Step=%s (%d)",
                isEnabled() ? "ON" : "OFF",
                fine ? "FINE" : "COARSE",
                step()));
    }
}

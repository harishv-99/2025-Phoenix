package edu.ftcphoenix.fw.input;

import org.firstinspires.ftc.robotcore.external.Telemetry;

import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.DoubleSupplier;
import java.util.function.Supplier;

/**
 * Sampled, filtered axis with prev/curr snapshot and time-aware filters.
 *
 * <p>Design:
 * <ul>
 *   <li>Implements {@link InputRegistry.Updatable} and self-registers into the provided registry.</li>
 *   <li>Can be created as RAW (direct hardware) or DERIVED (virtual/combined) via factories.</li>
 *   <li>Filter chain is composable and time-aware (dt passed to each stage).</li>
 *   <li>Supports dynamic scaling (e.g., slow mode) and clamping.</li>
 *   <li>Provides helpers to create buttons from an axis with or without hysteresis.</li>
 * </ul>
 *
 * Contract:
 * <ul>
 *   <li>Create via {@link #raw(InputRegistry, DoubleSupplier)} for hardware-backed axes
 *       or {@link #derived(InputRegistry, DoubleSupplier)} for virtual/composed axes.</li>
 *   <li>The registry's {@link InputRegistry#updateAll(double)} must be called exactly once per loop.</li>
 * </ul>
 */
public final class Axis implements InputRegistry.Updatable {

    private final InputRegistry.UpdatePriority priority;
    private final DoubleSupplier sampler;

    // filter chain: f(x, dt) -> y
    private BiFunction<Double, Double, Double> filter = (x, dt) -> x;

    // multiplicative scaling (e.g., slow mode). May be dynamic per tick.
    private Supplier<Double> scaleSupplier = () -> 1.0;

    private double prev = 0.0;
    private double curr = 0.0;

    private Axis(InputRegistry registry,
                 InputRegistry.UpdatePriority priority,
                 DoubleSupplier sampler) {
        this.priority = Objects.requireNonNull(priority, "priority");
        this.sampler = Objects.requireNonNull(sampler, "sampler");
        Objects.requireNonNull(registry, "registry").register(this);
    }

    /** Create a RAW axis (directly from hardware). */
    public static Axis raw(InputRegistry registry, DoubleSupplier sampler) {
        return new Axis(registry, InputRegistry.UpdatePriority.RAW, sampler);
    }

    /** Create a DERIVED axis (computed from other inputs). */
    public static Axis derived(InputRegistry registry, DoubleSupplier sampler) {
        return new Axis(registry, InputRegistry.UpdatePriority.DERIVED, sampler);
    }

    @Override
    public InputRegistry.UpdatePriority priority() {
        return priority;
    }

    @Override
    public void update(double dtSec) {
        prev = curr;
        double v = sampler.getAsDouble();
        v = filter.apply(v, dtSec);
        v *= safe(scaleSupplier.get());
        curr = v;
    }

    /** Current value after filtering and scaling. */
    public double get() { return curr; }

    /** Previous tick's value (after filtering/scaling). */
    public double getPrev() { return prev; }

    // ---------------------
    // Filter composition
    // ---------------------

    /** Compose a new stage after the current filter chain. */
    public Axis withFilter(BiFunction<Double, Double, Double> stage) {
        final BiFunction<Double, Double, Double> prior = this.filter;
        this.filter = (x, dt) -> stage.apply(prior.apply(x, dt), dt);
        return this;
    }

    /** Deadband that snaps to 0 within |x| <= band, scaled outside (band in [0,1)). */
    public Axis deadband(double band) {
        return withFilter((x, dt) -> {
            double a = Math.abs(x);
            if (a <= band) return 0.0;
            return Math.copySign((a - band) / (1.0 - band), x);
        });
    }

    /** Expo (a.k.a. squaring): y = sign(x) * |x|^p; p >= 1. */
    public Axis expo(double p) {
        double pow = Math.max(1.0, p);
        return withFilter((x, dt) -> Math.copySign(Math.pow(Math.abs(x), pow), x));
    }

    /** Slew-rate limiter: limits change to at most maxPerSec units per second. */
    public Axis rateLimit(double maxPerSec) {
        return withFilter(new BiFunction<Double, Double, Double>() {
            double last = 0.0;
            @Override public Double apply(Double x, Double dt) {
                double maxStep = maxPerSec * Math.max(0.0, dt);
                double delta = x - last;
                if (delta >  maxStep) last += maxStep;
                else if (delta < -maxStep) last -= maxStep;
                else last = x;
                return last;
            }
        });
    }

    /**
     * Hysteresis snap-to-zero: stays at 0 inside |x| <= low; once outside |x| >= high,
     * passthrough resumes until falling back under low. Requires 0 <= low <= high <= 1.
     */
    public Axis hysteresisToZero(double low, double high) {
        final double l = Math.max(0, Math.min(low, high));
        final double h = Math.max(l, high);
        return withFilter(new BiFunction<Double, Double, Double>() {
            boolean zeroed = true;
            @Override public Double apply(Double x, Double dt) {
                double a = Math.abs(x);
                if (zeroed) {
                    if (a >= h) zeroed = false;
                } else {
                    if (a <= l) zeroed = true;
                }
                return zeroed ? 0.0 : x;
            }
        });
    }

    /** Clamp output to [min, max]. */
    public Axis clamp(double min, double max) {
        final double lo = Math.min(min, max);
        final double hi = Math.max(min, max);
        return withFilter((x, dt) -> Math.max(lo, Math.min(hi, x)));
    }

    /** Multiply output by a dynamic factor (e.g., slow mode). Supplier is sampled each tick. */
    public Axis scaled(Supplier<Double> factor) {
        final Supplier<Double> prior = this.scaleSupplier;
        this.scaleSupplier = () -> safe(prior.get()) * safe(factor.get());
        return this;
    }

    // ---------------------
    // Axis → Button helpers
    // ---------------------

    /**
     * Create a DERIVED button that is pressed when this axis exceeds threshold.
     * Uses strictly greater-than comparison.
     */
    public Button asButton(InputRegistry registry, double threshold) {
        return Button.derived(registry, () -> get() > threshold);
    }

    /**
     * Create a DERIVED hysteresis button that turns on above {@code high} and
     * off below {@code low}. Requires low <= high.
     */
    public Button asHysteresisButton(InputRegistry registry, double low, double high) {
        final double lo = Math.min(low, high);
        final double hi = Math.max(low, high);
        return Button.derived(registry, new java.util.function.BooleanSupplier() {
            boolean state = false;
            @Override public boolean getAsBoolean() {
                double v = get();
                if (!state) {
                    if (v >= hi) state = true;
                } else {
                    if (v <= lo) state = false;
                }
                return state;
            }
        });
    }

    // ---------------------
    // Telemetry
    // ---------------------

    public void addTelemetry(Telemetry t, String label) {
        if (t == null) return;
        t.addData(label, curr).addData(label + ".prev", prev);
    }

    // ---------------------
    // Utils
    // ---------------------

    private static double safe(Double d) {
        return (d == null || d.isNaN() || d.isInfinite()) ? 1.0 : d;
    }
}

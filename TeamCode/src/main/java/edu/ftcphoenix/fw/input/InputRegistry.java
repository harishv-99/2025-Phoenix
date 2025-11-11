package edu.ftcphoenix.fw.input;

import org.firstinspires.ftc.robotcore.external.Telemetry;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Central registry for inputs that need a per-loop update.
 *
 * <h2>Update model</h2>
 * <ul>
 *   <li>Two-phase update to avoid race conditions:
 *     <ol>
 *       <li><b>RAW</b> phase (hardware-backed): read FTC Gamepad & sensors.</li>
 *       <li><b>DERIVED</b> phase (virtuals/combinations): depend on RAW/other inputs.</li>
 *     </ol>
 *   </li>
 *   <li>Call {@link #updateAll(double)} exactly once per robot loop.
 *       All registered {@link Updatable}s will be updated in priority order.</li>
 * </ul>
 *
 * <h2>Registration contract</h2>
 * <ul>
 *   <li>RAW inputs must not depend on other inputs in their sampler.</li>
 *   <li>DERIVED inputs may read other inputs safely (they run after RAW).</li>
 *   <li>Construct inputs up-front so registration order within the DERIVED phase is stable.</li>
 * </ul>
 *
 * <h2>Debug guardrails (optional)</h2>
 * <p>
 * Use {@link #setDebugGuardEnabled(boolean)} to enable a lightweight heuristic that warns if
 * {@link #updateAll(double)} appears to be called more than once in a single loop. This is a
 * common integration mistake that breaks edge/gesture logic.
 * </p>
 * <ul>
 *   <li>Heuristic: if {@code dtSec} is ~0 <i>or</i> the wall-clock gap between successive
 *       {@code updateAll} calls is tiny, we log a warning (rate-limited to 1/sec).</li>
 *   <li>Default thresholds: {@code dtSec <= 1e-6} or {@code gap < 2ms} ⇒ suspicious.</li>
 *   <li>Tune via {@link #configureDebugGuard(double, double)} if needed.</li>
 * </ul>
 */
public final class InputRegistry {

    /**
     * Update priority for a registered input.
     */
    public enum UpdatePriority {RAW, DERIVED}

    /**
     * Anything that participates in the per-loop update.
     * Implement this in Axis, Button, Trigger, VirtualAxis, etc.
     */
    public interface Updatable {
        /**
         * Return whether this input should be updated in RAW or DERIVED phase.
         */
        UpdatePriority priority();

        /**
         * Update internal state for this tick. dtSec is time since last call in seconds.
         */
        void update(double dtSec);
    }

    private final List<Updatable> raws = new ArrayList<>();
    private final List<Updatable> derived = new ArrayList<>();

    // ---------------------------
    // Debug guard (optional)
    // ---------------------------
    private boolean debugGuardEnabled = false;
    private long lastUpdateNs = Long.MIN_VALUE;
    private long lastWarnNs = Long.MIN_VALUE;
    // thresholds (defaults): dt ~ 0 or wall-gap < 2ms → suspicious
    private double epsilonDtSec = 1e-6;
    private long minGapNs = 2_000_000L; // 2 ms
    private static final long WARN_COOLDOWN_NS = 1_000_000_000L; // 1 s

    /**
     * Register an input to be updated every loop. The input's {@link UpdatePriority}
     * determines whether it is processed in the RAW or DERIVED phase.
     */
    public void register(Updatable u) {
        if (u == null) return;
        if (u.priority() == UpdatePriority.DERIVED) derived.add(u);
        else raws.add(u);
    }

    /**
     * Register many updatables at once.
     */
    public void registerAll(Iterable<? extends Updatable> list) {
        if (list == null) return;
        for (Updatable u : list) register(u);
    }

    /**
     * Execute a full input update cycle. Call exactly ONCE per loop.
     * RAW (hardware-backed) → DERIVED (composites, virtuals, chords, etc.).
     *
     * @param dtSec delta time in seconds since previous loop.
     */
    public void updateAll(double dtSec) {
        // Debug guard: detect suspicious multiple invocations per loop
        if (debugGuardEnabled) {
            final long now = System.nanoTime();
            boolean suspicious = false;

            if (lastUpdateNs != Long.MIN_VALUE) {
                long gap = now - lastUpdateNs;
                if (dtSec <= epsilonDtSec || gap < minGapNs) suspicious = true;
            }
            lastUpdateNs = now;

            if (suspicious) {
                if (lastWarnNs == Long.MIN_VALUE || now - lastWarnNs >= WARN_COOLDOWN_NS) {
                    System.out.println("[Phoenix InputRegistry] Warning: updateAll() called multiple times in one loop? "
                            + "dtSec=" + dtSec + " gapNs≈" + (lastUpdateNs == Long.MIN_VALUE ? -1 : (now - lastUpdateNs))
                            + " (edges/gestures may flicker).");
                    lastWarnNs = now;
                }
            }
        }

        // RAW first
        for (int i = 0, n = raws.size(); i < n; i++) {
            raws.get(i).update(dtSec);
        }
        // Then DERIVED (composites, virtuals, combos/chords)
        for (int i = 0, n = derived.size(); i < n; i++) {
            derived.get(i).update(dtSec);
        }
    }

    /**
     * Clear all registrations (typically not needed except in tests).
     */
    public void clear() {
        raws.clear();
        derived.clear();
    }

    /**
     * Number of registered inputs (RAW + DERIVED).
     */
    public int size() {
        return raws.size() + derived.size();
    }

    /**
     * Read-only views (useful in tests/telemetry).
     */
    public List<Updatable> rawInputsView() {
        return Collections.unmodifiableList(raws);
    }

    public List<Updatable> derivedInputsView() {
        return Collections.unmodifiableList(derived);
    }

    /**
     * Optional helper for quick sanity telemetry.
     */
    public void addTelemetry(Telemetry t) {
        if (t == null) return;
        t.addData("inputs.raw", raws.size());
        t.addData("inputs.derived", derived.size());
        t.addData("debugGuard", debugGuardEnabled ? "on" : "off");
    }

    // ---------------------------
    // Debug guard configuration
    // ---------------------------

    /**
     * Enable/disable the debug guard that warns about suspicious multiple calls of {@link #updateAll(double)} per loop.
     * <p>Default is {@code false}. Safe to enable during bring-up; disable for events if you like.</p>
     */
    public void setDebugGuardEnabled(boolean enabled) {
        this.debugGuardEnabled = enabled;
    }

    /**
     * Configure the heuristics used by the debug guard.
     *
     * @param epsilonDtSec treat calls with dtSec <= this as "same loop" (default 1e-6)
     * @param minGapSec    treat wall-clock gaps < this as "same loop" (default 0.002s)
     */
    public void configureDebugGuard(double epsilonDtSec, double minGapSec) {
        this.epsilonDtSec = Math.max(0.0, epsilonDtSec);
        this.minGapNs = (long) Math.max(0.0, minGapSec * 1e9);
    }
}

package edu.ftcphoenix.fw2.drive;

import edu.ftcphoenix.fw2.core.FrameClock;
import edu.ftcphoenix.fw2.util.MathUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.DoubleSupplier;

/**
 * DriveArbiter — Combine multiple {@link DriveSource} signals into one.
 *
 * <p><b>Strategies:</b>
 * <ul>
 *   <li><b>WEIGHTED_SUM</b>: sum wᵢ·sᵢ; optionally normalize weights (Σw ≤ 1). Linear and predictable.</li>
 *   <li><b>PRIORITY_SOFT_SAT</b>: in list order (driver first), each source gets the remaining capacity; if it would
 *       overflow, uniformly scale that source’s contribution to fit. Preserves each source’s axis ratios and priority.</li>
 * </ul>
 *
 * <p><b>Final limiting:</b> {@link OutputLimitPolicy#UNIFORM_SCALE} uses a single factor
 * via {@link MathUtil#uniformScaleFactorToFit3(double, double, double, double, double, double)} to keep ratios intact.
 */
public final class DriveArbiter implements DriveSource {

    public enum MixStrategy {
        WEIGHTED_SUM,
        PRIORITY_SOFT_SAT
    }

    public enum OutputLimitPolicy {
        UNIFORM_SCALE,
        NONE
    }

    /** Pair a source with a (live) weight. */
    public final class Entry {
        private final DriveSource src;
        private final DoubleSupplier weightSup;

        public Entry(DriveSource src, DoubleSupplier weightSup) {
            this.src = src; this.weightSup = weightSup;
        }
        public DriveSource src() { return src; }
        public double weight()   { return Math.max(0.0, weightSup.getAsDouble()); }

        @Override public String toString() { return "Entry[src=" + src + ", weight=" + weight() + "]"; }
        @Override public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Entry e = (Entry) o;
            return Double.compare(e.weight(), weight()) == 0 && Objects.equals(src, e.src);
        }
        @Override public int hashCode() { return Objects.hash(src, weight()); }
    }

    // ----------------- Config -----------------
    private final List<Entry> entries = new ArrayList<>();
    private MixStrategy strategy = MixStrategy.WEIGHTED_SUM;
    private OutputLimitPolicy limitPolicy = OutputLimitPolicy.UNIFORM_SCALE;
    private boolean normalizeWeights = true; // default on for WEIGHTED_SUM

    private DoubleSupplier maxLat = () -> 1.0, maxAx = () -> 1.0, maxOm = () -> 1.0;

    public DriveArbiter add(DriveSource s, double w) { return add(s, () -> w); }
    public DriveArbiter add(DriveSource s, DoubleSupplier wSup) {
        entries.add(new Entry(s, wSup));
        return this;
    }

    public DriveArbiter strategy(MixStrategy m) { if (m != null) strategy = m; return this; }
    public DriveArbiter outputLimit(OutputLimitPolicy p) { if (p != null) limitPolicy = p; return this; }
    /** Enables Σw ≤ 1 normalization for WEIGHTED_SUM (ignored by PRIORITY_SOFT_SAT). */
    public DriveArbiter normalizeWeights(boolean on) { normalizeWeights = on; return this; }

    public DriveArbiter limits(double lat, double ax, double om) {
        maxLat = () -> lat; maxAx = () -> ax; maxOm = () -> om; return this;
    }
    public DriveArbiter limits(DoubleSupplier lat, DoubleSupplier ax, DoubleSupplier om) {
        maxLat = lat; maxAx = ax; maxOm = om; return this;
    }

    // ----------------- Runtime -----------------
    @Override
    public DriveSignal get(FrameClock clock) {
        final double limLat = Math.abs(maxLat.getAsDouble());
        final double limAx  = Math.abs(maxAx.getAsDouble());
        final double limOm  = Math.abs(maxOm.getAsDouble());

        final DriveSignal mixed = (strategy == MixStrategy.PRIORITY_SOFT_SAT)
                ? prioritySoftSaturate(clock, limLat, limAx, limOm)
                : weightedSum(clock);

        if (limitPolicy == OutputLimitPolicy.UNIFORM_SCALE) {
            final double k = MathUtil.uniformScaleFactorToFit3(
                    mixed.lateral(), mixed.axial(), mixed.omega(),
                    limLat, limAx, limOm);
            return (k >= 1.0) ? mixed : mixed.scaled(k);
        }
        return mixed; // NONE
    }

    // ----------------- Strategies -----------------

    /** Sum wᵢ·sᵢ; optionally normalize weights so Σw ≤ 1. */
    private DriveSignal weightedSum(FrameClock clock) {
        final int n = entries.size();
        double[] w = new double[n];
        double sum = 0.0;

        for (int i = 0; i < n; i++) {
            w[i] = Math.max(0.0, entries.get(i).weight());
            sum += w[i];
        }
        if (normalizeWeights && sum > 1.0) {
            final double inv = 1.0 / sum;
            for (int i = 0; i < n; i++) w[i] *= inv;
        }

        double lat = 0, ax = 0, om = 0;
        for (int i = 0; i < n; i++) {
            if (w[i] == 0.0) continue;
            DriveSignal s = entries.get(i).src().get(clock);
            lat += s.lateral() * w[i];
            ax  += s.axial()   * w[i];
            om  += s.omega()   * w[i];
        }
        return new DriveSignal(lat, ax, om);
    }

    /**
     * Priority soft-saturation:
     * iterate in list order; for each source, compute its weighted contribution c;
     * uniformly scale c so that adding it stays within per-axis limits; add; continue.
     * Uses {@link MathUtil#uniformScaleFactorForContribution3(double, double, double, double, double, double)}.
     */
    private DriveSignal prioritySoftSaturate(FrameClock clock, double limLat, double limAx, double limOm) {
        double outLat = 0, outAx = 0, outOm = 0;

        for (Entry e : entries) {
            double w = e.weight();
            if (w <= 0.0) continue;

            DriveSignal s = e.src().get(clock);
            double cLat = s.lateral() * w;
            double cAx  = s.axial()   * w;
            double cOm  = s.omega()   * w;

            // Remaining capacity per axis
            double availLat = Math.max(0.0, limLat - Math.abs(outLat));
            double availAx  = Math.max(0.0, limAx  - Math.abs(outAx));
            double availOm  = Math.max(0.0, limOm  - Math.abs(outOm));

            // Uniformly scale this contribution to fit remaining capacity
            double k = MathUtil.uniformScaleFactorForContribution3(
                    cLat, cAx, cOm, availLat, availAx, availOm);

            outLat += cLat * k;
            outAx  += cAx  * k;
            outOm  += cOm  * k;

            // Early exit if saturated on all axes
            if (availLat == 0.0 && availAx == 0.0 && availOm == 0.0) break;
        }

        return new DriveSignal(outLat, outAx, outOm);
    }
}

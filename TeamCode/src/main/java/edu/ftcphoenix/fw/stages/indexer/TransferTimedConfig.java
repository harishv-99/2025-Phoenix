package edu.ftcphoenix.fw.stages.indexer;

/**
 * Open-loop timing/power config for {@link TransferTimedStage}.
 *
 * <h3>Fields</h3>
 * <ul>
 *   <li>{@code capacity}: how many pieces this indexer should buffer (e.g., 3).</li>
 *   <li>{@code inwardPower}: +power to move a piece downstream (toward the next stage).</li>
 *   <li>{@code outwardPower}: -power to purge upstream.</li>
 *   <li>{@code ingestSec}: time to move one new piece from upstream contact to the held queue.</li>
 *   <li>{@code deliverSec}: time to move the front piece into the downstream mechanism.</li>
 *   <li>{@code rejectSec}: time to expel one piece upstream.</li>
 * </ul>
 *
 * <h3>Tuning tips</h3>
 * Each of the three durations should correspond to exactly one "pitch" (one-piece advance).
 * If deliver needs a slightly different duration than ingest, set it independently.
 */
public final class TransferTimedConfig {
    public final int capacity;
    public final double inwardPower;
    public final double outwardPower;
    public final double ingestSec;
    public final double deliverSec;
    public final double rejectSec;

    public TransferTimedConfig(int capacity,
                               double inwardPower, double outwardPower,
                               double ingestSec, double deliverSec, double rejectSec) {
        this.capacity = Math.max(0, capacity);
        this.inwardPower = clamp(inwardPower);
        this.outwardPower = clamp(outwardPower);
        this.ingestSec = Math.max(0, ingestSec);
        this.deliverSec = Math.max(0, deliverSec);
        this.rejectSec = Math.max(0, rejectSec);
    }

    public static TransferTimedConfig defaults() {
        return new TransferTimedConfig(
                3,      // capacity
                +0.60,  // inwardPower
                -0.60,  // outwardPower
                0.30,   // ingestSec (one-piece advance into queue)
                0.25,   // deliverSec (front piece -> downstream)
                0.30    // rejectSec (one-piece purge)
        );
    }

    private static double clamp(double v) {
        return v < -1 ? -1 : (v > 1 ? 1 : v);
    }
}

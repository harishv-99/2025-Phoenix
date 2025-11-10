package edu.ftcphoenix.fw.stages.indexer;

/**
 * Timing configuration for {@link ChamberStage}.
 *
 * <h3>Rationale</h3>
 * First ball typically needs a longer prefeed/open than subsequent balls to seat reliably.
 * Recovery pauses are short to let sensors settle before the next action.
 */
public final class ChamberConfig {
    public final double prefeedFirstSec;
    public final double openFirstSec;
    public final double prefeedNextSec;
    public final double openNextSec;
    public final double recoverSec;
    public final double rejectSec;

    public ChamberConfig(double prefeedFirstSec, double openFirstSec,
                         double prefeedNextSec, double openNextSec,
                         double recoverSec, double rejectSec) {
        this.prefeedFirstSec = prefeedFirstSec;
        this.openFirstSec = openFirstSec;
        this.prefeedNextSec = prefeedNextSec;
        this.openNextSec = openNextSec;
        this.recoverSec = recoverSec;
        this.rejectSec = rejectSec;
    }

    public static ChamberConfig defaults() {
        return new ChamberConfig(
                0.10,  // prefeedFirstSec
                0.12,  // openFirstSec
                0.05,  // prefeedNextSec
                0.10,  // openNextSec
                0.06,  // recoverSec
                0.25   // rejectSec
        );
    }
}

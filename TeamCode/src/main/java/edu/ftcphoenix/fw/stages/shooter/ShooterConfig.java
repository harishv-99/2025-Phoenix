package edu.ftcphoenix.fw.stages.shooter;

/**
 * Configuration for {@link ShooterStage}.
 *
 * <h3>Fields</h3>
 * <ul>
 *   <li>{@code defaultTargetRadPerSec} – convenience default target (rad/s). The stage does not
 *       auto-apply this; your TeleOp/Auto should call Spooler#setTargetRadPerSec.</li>
 *   <li>{@code ingestWindowSec} – duration (s) the shooter allows a feed after an accept.</li>
 *   <li>{@code recoverSec} – cooldown (s) after the ingest window to avoid immediate retrigger.</li>
 * </ul>
 *
 * <h3>Notes</h3>
 * <ul>
 *   <li>All values are clamped to valid ranges (times ≥ 0, target ≥ 0).</li>
 *   <li>Speed tolerance belongs in the {@link ShooterStage.Spooler} implementation.</li>
 * </ul>
 */
public final class ShooterConfig {
    /**
     * Convenience default target (rad/s); not auto-applied.
     */
    public final double defaultTargetRadPerSec;
    /**
     * Allowed feed window after an accept (seconds).
     */
    public final double ingestWindowSec;
    /**
     * Cooldown after the window ends (seconds).
     */
    public final double recoverSec;

    /**
     * Primary (and only) constructor.
     *
     * @param defaultTargetRadPerSec nominal shooter target (rad/s), non-negative
     * @param ingestWindowSec        feed-allowed window duration (s), non-negative
     * @param recoverSec             cooldown duration (s), non-negative
     */
    public ShooterConfig(double defaultTargetRadPerSec, double ingestWindowSec, double recoverSec) {
        this.defaultTargetRadPerSec = clampNonNeg(defaultTargetRadPerSec);
        this.ingestWindowSec = clampNonNeg(ingestWindowSec);
        this.recoverSec = clampNonNeg(recoverSec);
    }

    /**
     * Reasonable defaults for bring-up: target=0, window=0.25s, recover=0.05s.
     */
    public static ShooterConfig defaults() {
        return new ShooterConfig(0.0, 0.25, 0.05);
    }

    private static double clampNonNeg(double v) {
        return (v < 0.0) ? 0.0 : v;
    }
}

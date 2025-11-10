package edu.ftcphoenix.fw.stages.intake;

/**
 * Timing and positions for {@link HumanPusherIntakeStage}.
 *
 * <h3>Positions</h3>
 * The pusher home and push positions are computed per downstream-count as:
 * <pre>
 *   home(count) = homeBase + count * homeStep
 *   push(count) = pushBase + count * pushStep
 * </pre>
 *
 * <h3>Assist motor</h3>
 * If an assist motor (CR-servo/motor) is provided, it runs at {@link #feedPower}
 * during the push forward and {@link #rejectPower} during a reject purge.
 */
public final class HumanPusherIntakeConfig {
    public final double homeBase;
    public final double homeStep;
    public final double pushBase;
    public final double pushStep;

    public final double pushForwardSec;
    public final double pushReturnSec;

    public final double feedPower;      // assist power during forward push (0 if no assist motor)
    public final double rejectPower;    // assist power during reject
    public final double rejectEjectSec; // duration of reject purge

    public HumanPusherIntakeConfig(double homeBase, double homeStep,
                                   double pushBase, double pushStep,
                                   double pushForwardSec, double pushReturnSec,
                                   double feedPower, double rejectPower, double rejectEjectSec) {
        this.homeBase = clamp01(homeBase);
        this.homeStep = clamp(-1, +1, homeStep);
        this.pushBase = clamp01(pushBase);
        this.pushStep = clamp(-1, +1, pushStep);

        this.pushForwardSec = Math.max(0, pushForwardSec);
        this.pushReturnSec = Math.max(0, pushReturnSec);

        this.feedPower = clamp(-1, +1, feedPower);
        this.rejectPower = clamp(-1, +1, rejectPower);
        this.rejectEjectSec = Math.max(0, rejectEjectSec);
    }

    public static HumanPusherIntakeConfig defaults() {
        return new HumanPusherIntakeConfig(
                0.20, 0.00,   // homeBase, homeStep
                0.65, 0.00,   // pushBase, pushStep
                0.15, 0.15,   // pushForwardSec, pushReturnSec
                0.00, -0.60,  // feedPower, rejectPower
                0.25          // rejectEjectSec
        );
    }

    /**
     * Home position for a given downstream load count.
     */
    public double homePosFor(int count) {
        return clamp01(homeBase + count * homeStep);
    }

    /**
     * Push position for a given downstream load count.
     */
    public double pushPosFor(int count) {
        return clamp01(pushBase + count * pushStep);
    }

    private static double clamp01(double v) {
        return (v < 0) ? 0 : (v > 1 ? 1 : v);
    }

    private static double clamp(double lo, double hi, double v) {
        return (v < lo) ? lo : (v > hi ? hi : v);
    }
}

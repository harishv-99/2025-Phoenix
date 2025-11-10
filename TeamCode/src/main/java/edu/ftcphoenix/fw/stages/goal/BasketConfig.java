package edu.ftcphoenix.fw.stages.goal;

/**
 * Pose tolerances for {@link BasketStage} lane readiness.
 */
public final class BasketConfig {
    public final double posTolMeters;
    public final double yawTolRad;

    public BasketConfig(double posTolMeters, double yawTolRad) {
        this.posTolMeters = posTolMeters;
        this.yawTolRad = yawTolRad;
    }

    public static BasketConfig defaults() {
        return new BasketConfig(0.05, 0.06);
    }
}

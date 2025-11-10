package edu.ftcphoenix.fw.stages.intake;

/**
 * Configuration for {@link RollerIntakeStage}.
 */
public final class RollerIntakeConfig {
    public final double inwardPower;
    public final double outwardPower;
    public final double ingestSec;
    public final double ejectSec;

    public RollerIntakeConfig(double inwardPower, double outwardPower, double ingestSec, double ejectSec) {
        this.inwardPower = inwardPower;
        this.outwardPower = outwardPower;
        this.ingestSec = ingestSec;
        this.ejectSec = ejectSec;
    }

    public static RollerIntakeConfig defaults() {
        return new RollerIntakeConfig(+0.85, -0.75, 0.35, 0.30);
    }
}

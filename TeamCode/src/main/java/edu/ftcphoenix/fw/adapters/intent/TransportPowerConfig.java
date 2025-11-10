package edu.ftcphoenix.fw.adapters.intent;

/**
 * Powers for FEED/REVERSE/IDLE used by {@link TransportModeToMotor}.
 */
public final class TransportPowerConfig {
    public final double feedPower;
    public final double reversePower;
    public final double idlePower;

    public TransportPowerConfig(double feedPower, double reversePower, double idlePower) {
        this.feedPower = clamp(feedPower);
        this.reversePower = clamp(reversePower);
        this.idlePower = clamp(idlePower);
    }

    public static TransportPowerConfig defaults() {
        return new TransportPowerConfig(+0.5, -0.5, 0.0);
    }

    private static double clamp(double v) {
        return v < -1 ? -1 : (v > 1 ? 1 : v);
    }
}

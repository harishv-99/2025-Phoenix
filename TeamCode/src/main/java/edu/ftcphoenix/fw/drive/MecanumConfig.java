package edu.ftcphoenix.fw.drive;

/**
 * Configuration for {@link MecanumDrivebase}.
 *
 * <p><b>turnGain:</b> scales omega contribution relative to axial/lateral. Increase if rotation feels weak,
 * decrease if it overwhelms translation. Typical range ~0.6..1.4.</p>
 */
public final class MecanumConfig {
    public final double turnGain;     // scales omega → wheel mix
    public final boolean invertFL, invertFR, invertBL, invertBR;

    public MecanumConfig(double turnGain,
                         boolean invertFL, boolean invertFR, boolean invertBL, boolean invertBR) {
        this.turnGain = turnGain;
        this.invertFL = invertFL;
        this.invertFR = invertFR;
        this.invertBL = invertBL;
        this.invertBR = invertBR;
    }

    public static MecanumConfig defaults() {
        return new MecanumConfig(1.0, false, true, false, true); // common wiring: right side inverted
    }
}

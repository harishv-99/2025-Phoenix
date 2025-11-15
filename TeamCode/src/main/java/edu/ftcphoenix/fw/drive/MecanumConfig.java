package edu.ftcphoenix.fw.drive;

/**
 * Configuration for {@link MecanumDrivebase}.
 *
 * <p>This class currently controls simple per-axis scaling of the
 * high-level {@link DriveSignal} components before they are mixed into
 * wheel powers. It is intentionally small and immutable.</p>
 *
 * <h2>Scaling semantics</h2>
 *
 * <ul>
 *   <li>{@link #maxAxial}: scales forward/back commands.</li>
 *   <li>{@link #maxLateral}: scales strafe left/right commands.</li>
 *   <li>{@link #maxOmega}: scales rotation commands.</li>
 * </ul>
 *
 * <p>All three values are typically in the range (0, 1], where 1.0 means
 * "full power allowed" and values less than 1.0 reduce the maximum power
 * in that axis.</p>
 *
 * <p>For most teams, the {@link #defaults()} configuration (all ones) is
 * sufficient. More advanced teams may construct modified configs using
 * the {@code withXxx(...)} methods.</p>
 */
public final class MecanumConfig {

    /**
     * Maximum scale factor for axial (forward/back) commands.
     */
    public final double maxAxial;

    /**
     * Maximum scale factor for lateral (strafe left/right) commands.
     */
    public final double maxLateral;

    /**
     * Maximum scale factor for rotational (omega) commands.
     */
    public final double maxOmega;

    private MecanumConfig(double maxAxial, double maxLateral, double maxOmega) {
        this.maxAxial = maxAxial;
        this.maxLateral = maxLateral;
        this.maxOmega = maxOmega;
    }

    /**
     * Default configuration: no additional scaling (all axes = 1.0).
     *
     * <p>This is the recommended starting point for most robots.</p>
     */
    public static MecanumConfig defaults() {
        return new MecanumConfig(1.0, 1.0, 1.0);
    }

    /**
     * Create a copy of this config with a different maximum axial scale.
     *
     * @param maxAxial new axial scale factor
     * @return new {@link MecanumConfig} with updated axial scale
     */
    public MecanumConfig withMaxAxial(double maxAxial) {
        return new MecanumConfig(maxAxial, this.maxLateral, this.maxOmega);
    }

    /**
     * Create a copy of this config with a different maximum lateral scale.
     *
     * @param maxLateral new lateral scale factor
     * @return new {@link MecanumConfig} with updated lateral scale
     */
    public MecanumConfig withMaxLateral(double maxLateral) {
        return new MecanumConfig(this.maxAxial, maxLateral, this.maxOmega);
    }

    /**
     * Create a copy of this config with a different maximum omega scale.
     *
     * @param maxOmega new rotational scale factor
     * @return new {@link MecanumConfig} with updated omega scale
     */
    public MecanumConfig withMaxOmega(double maxOmega) {
        return new MecanumConfig(this.maxAxial, this.maxLateral, maxOmega);
    }
}

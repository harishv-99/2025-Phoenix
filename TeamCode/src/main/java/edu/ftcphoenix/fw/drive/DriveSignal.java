package edu.ftcphoenix.fw.drive;

import edu.ftcphoenix.fw.util.MathUtil;

/**
 * Simple drive command for holonomic drivetrains (e.g., mecanum).
 *
 * <p>Components are expressed in <b>robot-centric</b> coordinates:
 * <ul>
 *   <li>{@link #axial}: forward/backward (+ is forward).</li>
 *   <li>{@link #lateral}: left/right (+ is left).</li>
 *   <li>{@link #omega}: rotation rate (+ is counterclockwise).</li>
 * </ul>
 *
 * <p>The units are framework-defined "normalized commands", typically
 * in the range [-1, +1], but nothing in this class enforces that
 * automatically; use {@link #clamped()} if you want per-component
 * clamping to [-1, +1].</p>
 *
 * <p>This class is <b>immutable</b>. All "mutator" style methods return
 * new instances.</p>
 */
public final class DriveSignal {

    /**
     * Forward/backward command (+ is forward).
     */
    public final double axial;

    /**
     * Left/right command (+ is left).
     */
    public final double lateral;

    /**
     * Rotational command (+ is counterclockwise).
     */
    public final double omega;

    /**
     * Construct a new drive signal.
     *
     * @param axial   forward/backward command
     * @param lateral left/right command
     * @param omega   rotational command
     */
    public DriveSignal(double axial, double lateral, double omega) {
        this.axial = axial;
        this.lateral = lateral;
        this.omega = omega;
    }

    /**
     * Return a new signal with each component multiplied by the given scale.
     */
    public DriveSignal scaled(double scale) {
        return new DriveSignal(
                axial * scale,
                lateral * scale,
                omega * scale
        );
    }

    /**
     * Return a new signal with each component clamped to [-1, +1].
     *
     * <p>Note: this does <b>not</b> normalize the vector as a whole; it only
     * clamps each component independently. For most FTC use cases (where you
     * already keep outputs in [-1, +1]), this is sufficient as a safety net.
     */
    public DriveSignal clamped() {
        return new DriveSignal(
                MathUtil.clamp(axial, -1.0, 1.0),
                MathUtil.clamp(lateral, -1.0, 1.0),
                MathUtil.clamp(omega, -1.0, 1.0)
        );
    }

    /**
     * Return a copy of this signal with a different axial component.
     */
    public DriveSignal withAxial(double newAxial) {
        return new DriveSignal(newAxial, lateral, omega);
    }

    /**
     * Return a copy of this signal with a different lateral component.
     */
    public DriveSignal withLateral(double newLateral) {
        return new DriveSignal(axial, newLateral, omega);
    }

    /**
     * Return a copy of this signal with a different omega component.
     */
    public DriveSignal withOmega(double newOmega) {
        return new DriveSignal(axial, lateral, newOmega);
    }

    @Override
    public String toString() {
        return "DriveSignal{" +
                "axial=" + axial +
                ", lateral=" + lateral +
                ", omega=" + omega +
                '}';
    }
}

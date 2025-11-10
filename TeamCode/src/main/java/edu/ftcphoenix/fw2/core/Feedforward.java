package edu.ftcphoenix.fw2.core;

/**
 * Computes open-loop effort for a desired setpoint (and optionally its derivative).
 *
 * <p>Use this to model known plant behavior (e.g., velocity kV, static kS, acceleration kA)
 * and add it to closed-loop corrections for better tracking and reduced integral windup.</p>
 *
 * <h2>When to use</h2>
 * <ul>
 *   <li>Any mechanism with roughly linear velocity/acceleration effects (flywheels, drivetrains).</li>
 * </ul>
 */
public interface Feedforward {
    /** @param setpoint desired value; @param derivative desired rate (optional) */
    double calculate(double setpoint, double derivative);
}

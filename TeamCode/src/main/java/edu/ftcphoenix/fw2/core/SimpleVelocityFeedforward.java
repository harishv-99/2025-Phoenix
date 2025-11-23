package edu.ftcphoenix.fw2.core;

/**
 * Minimal velocity feedforward: {@code effort = kF * setpoint}.
 *
 * <p>Use as a baseline when your mechanism behaves roughly linearly with velocity and you don’t
 * need static or acceleration terms.</p>
 *
 * <h2>When to use</h2>
 * <ul>
 *   <li>Flywheels and wheels where {@code kF} alone gets you most of the way.</li>
 * </ul>
 *
 * <h2>Notes</h2>
 * <ul>
 *   <li>The {@code derivative} parameter is ignored. For more accurate models, consider a
 *       richer implementation (e.g., {@code kS * sign + kV * vel + kA * accel}).</li>
 * </ul>
 */
public class SimpleVelocityFeedforward implements Feedforward {
    private final double kF;

    public SimpleVelocityFeedforward(double kF) {
        this.kF = kF;
    }

    @Override
    public double calculate(double setpoint, double derivative) {
        return kF * setpoint;
    }
}

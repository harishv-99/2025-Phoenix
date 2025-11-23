package edu.ftcphoenix.fw2.filters.scalar;

import edu.ftcphoenix.fw2.filters.Filter;

/**
 * Static friction compensation (kS): adds a small sign bias to overcome stiction near zero.
 * <p>
 * Best practice: no clamp here; add one explicit clamp near the sink.
 */
public final class StaticFrictionComp implements Filter<Double> {
    private final double kS; // 0..1 (power units)

    public StaticFrictionComp(double kS) {
        this.kS = Math.abs(kS);
    }

    @Override
    public Double apply(Double u, double dt) {
        if (Math.abs(u) < 1e-6) return 0.0;        // exactly zero stays zero
        return u + Math.copySign(kS, u);           // bias with sign of command
    }
}

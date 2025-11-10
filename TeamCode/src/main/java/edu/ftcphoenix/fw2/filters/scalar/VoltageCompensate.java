package edu.ftcphoenix.fw2.filters.scalar;

import java.util.function.DoubleSupplier;

import edu.ftcphoenix.fw2.filters.Filter;

/**
 * Voltage compensation.
 * Multiplies the command by V_nom/V_now to preserve feel under battery sag.
 * <p>
 * Best practice: do NOT clamp here. Put a single, explicit clamp at the sink.
 */
public final class VoltageCompensate implements Filter<Double> {
    private final DoubleSupplier vNow;
    private final double vNominal;

    public VoltageCompensate(DoubleSupplier voltageSupplier, double nominalVolts) {
        this.vNow = voltageSupplier;
        this.vNominal = Math.max(1e-3, nominalVolts);
    }

    @Override
    public Double apply(Double cmd, double dt) {
        double v = Math.max(1e-3, vNow.getAsDouble());
        return cmd * (vNominal / v);
    }
}

package edu.ftcphoenix.fw2.drive.filters;

import java.util.function.DoubleSupplier;

import edu.ftcphoenix.fw2.drive.DriveSignal;
import edu.ftcphoenix.fw2.filters.Filter;

/**
 * Field-centric transform: rotates (axial, lateral) by -heading into robot frame.
 * <p>
 * Use when:
 * - Driver wants field-aligned translation regardless of robot yaw.
 * <p>
 * Conventions:
 * - headingRad supplier returns yaw in radians, CCW+.
 */
public final class FieldCentric implements Filter<DriveSignal> {
    private final DoubleSupplier headingRad;

    public FieldCentric(DoubleSupplier headingRad) {
        this.headingRad = headingRad;
    }

    @Override
    public DriveSignal apply(DriveSignal s, double dt) {
        double h = headingRad.getAsDouble();
        if (Double.isNaN(h)) return s;

        double cos = Math.cos(-h), sin = Math.sin(-h);
        double ax = s.axial() * cos - s.lateral() * sin;
        double lat = s.axial() * sin + s.lateral() * cos;
        return new DriveSignal(lat, ax, s.omega());
    }
}

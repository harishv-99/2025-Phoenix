package edu.ftcphoenix.fw2.drive.filters;

import edu.ftcphoenix.fw2.drive.DriveSignal;
import edu.ftcphoenix.fw2.filters.Filter;

/**
 * Applies independent scalar filters to each axis of a DriveSignal.
 * <p>
 * Use when:
 * - You want per-axis shaping using scalar filters (Deadband, Expo, SlewLimiter, LowPass, Clamp, Scale).
 * <p>
 * Notes:
 * - Pass null for any axis to leave it unmodified.
 * - This replaces ad-hoc per-axis modifiers (e.g., DeadbandModifier).
 */
public final class DriveAxesFilter implements Filter<DriveSignal> {
    private final Filter<Double> lateral, axial, omega;

    public DriveAxesFilter(Filter<Double> lateral, Filter<Double> axial, Filter<Double> omega) {
        this.lateral = lateral;
        this.axial = axial;
        this.omega = omega;
    }

    @Override
    public DriveSignal apply(DriveSignal s, double dtSeconds) {
        double lat = (lateral != null) ? lateral.apply(s.lateral(), dtSeconds) : s.lateral();
        double ax = (axial != null) ? axial.apply(s.axial(), dtSeconds) : s.axial();
        double om = (omega != null) ? omega.apply(s.omega(), dtSeconds) : s.omega();
        return new DriveSignal(lat, ax, om);
    }
}

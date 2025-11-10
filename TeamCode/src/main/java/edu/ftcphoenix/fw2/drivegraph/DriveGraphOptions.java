package edu.ftcphoenix.fw2.drivegraph;

import java.util.function.BooleanSupplier;
import java.util.function.DoubleSupplier;

import edu.ftcphoenix.fw2.drive.DriveArbiter;

/**
 * Knobs/toggles for building a drive graph.
 * Uses AxisChains, so each axis needs a final abs limit (usually 1.0).
 */
public final class DriveGraphOptions {
    // Driver shaping (teleop feel)
    public double dbLat = 0.05, dbAx = 0.05, dbOm = 0.05;
    public double expoLat = 1.8, expoAx = 1.8, expoOm = 1.4;
    public double slewLat = 3.0, slewAx = 3.0, slewOm = 6.0;

    // Final per-axis absolute limits (AxisChains clamp once per axis)
    public DoubleSupplier limLatAbs = () -> 1.0;
    public DoubleSupplier limAxAbs = () -> 1.0;
    public DoubleSupplier limOmAbs = () -> 1.0;

    // Live scales/toggles
    public DoubleSupplier precisionScale = () -> 1.0;      // slow/turbo
    public DoubleSupplier omegaFineScale = () -> 1.0;      // extra fine rotation
    public BooleanSupplier fieldCentric = () -> false;    // rotate translation by heading
    public DoubleSupplier headingRad = () -> Double.NaN;

    // Mixer
    public DriveArbiter.MixStrategy mix = DriveArbiter.MixStrategy.PRIORITY_SOFT_SAT;
    public boolean normalizeWeights = true; // used only by WEIGHTED_SUM

    // Logical mixed-signal limits for the arbiter’s uniform-scale step (keep equal to axis limits)
    public DoubleSupplier mixLimLat = () -> 1.0;
    public DoubleSupplier mixLimAx = () -> 1.0;
    public DoubleSupplier mixLimOm = () -> 1.0;
}

package edu.ftcphoenix.fw.drive.source;

import edu.ftcphoenix.fw.drive.DriveSignal;
import edu.ftcphoenix.fw.util.LoopClock;
import edu.ftcphoenix.fw.util.RateLimiter;

import java.util.function.BooleanSupplier;
import java.util.function.DoubleSupplier;

/**
 * Robot-centric drive source from three axes (axial/lateral/omega) with shaping:
 * deadband, exponential response (expo), and per-axis rate limiting.
 *
 * <h3>Why here</h3>
 * Encapsulates all "feel" settings in one place; the drivebase stays pure.
 */
public final class StickDriveSource {
    private final DoubleSupplier axialIn, lateralIn, omegaIn;
    private final BooleanSupplier slowMode; // nullable
    private final double deadband, expo, slowScalar;

    private final RateLimiter limAx, limLt, limOm;

    /**
     * @param axialIn    forward/backward input in [-1,1]
     * @param lateralIn  strafe input in [-1,1]
     * @param omegaIn    yaw input in [-1,1]
     * @param slowMode   optional on-the-fly slow toggle (nullable)
     * @param deadband   values within [-deadband,+deadband] become 0
     * @param expo       0..1 where 0 = linear, 1 = very soft around center (applied as x*|x|^expo)
     * @param ratePerSec symmetric rate limit per axis (units/s)
     */
    public StickDriveSource(DoubleSupplier axialIn,
                            DoubleSupplier lateralIn,
                            DoubleSupplier omegaIn,
                            BooleanSupplier slowMode,
                            double deadband,
                            double expo,
                            double ratePerSec) {
        this.axialIn = axialIn;
        this.lateralIn = lateralIn;
        this.omegaIn = omegaIn;
        this.slowMode = slowMode;
        this.deadband = Math.max(0, deadband);
        this.expo = Math.max(0, Math.min(1, expo));
        this.slowScalar = 0.5; // fixed 50% slow; adjust if desired
        this.limAx = RateLimiter.symmetric(Math.max(0, ratePerSec), 0);
        this.limLt = RateLimiter.symmetric(Math.max(0, ratePerSec), 0);
        this.limOm = RateLimiter.symmetric(Math.max(0, ratePerSec), 0);
    }

    /**
     * Compute a shaped robot-centric signal for this tick.
     */
    public DriveSignal sample(LoopClock clock) {
        final double dt = Math.max(0, clock.dtSec());

        double ax = shape(axialIn.getAsDouble());
        double lt = shape(lateralIn.getAsDouble());
        double om = shape(omegaIn.getAsDouble());

        // Rate limit
        ax = limAx.update(ax, dt);
        lt = limLt.update(lt, dt);
        om = limOm.update(om, dt);

        // Slow mode
        boolean slow = slowMode != null && slowMode.getAsBoolean();
        if (slow) {
            ax *= slowScalar;
            lt *= slowScalar;
            om *= slowScalar;
        }

        return new DriveSignal(ax, lt, om);
    }

    private double shape(double xRaw) {
        double x = applyDeadband(xRaw, deadband);
        // Expo curve: y = x * |x|^expo (expo in [0,1])
        final double mag = Math.pow(Math.abs(x), 1.0 + expo);
        return Math.signum(x) * mag;
    }

    private static double applyDeadband(double x, double db) {
        if (Math.abs(x) <= db) return 0.0;
        // Re-scale so output starts at 0 beyond deadband
        double sign = Math.signum(x);
        double y = (Math.abs(x) - db) / (1.0 - db);
        return sign * y;
    }
}

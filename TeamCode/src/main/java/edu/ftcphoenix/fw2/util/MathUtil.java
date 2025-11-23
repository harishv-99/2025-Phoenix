package edu.ftcphoenix.fw2.util;

/**
 * Small math helpers for control code.
 * <p>
 * Design goals:
 * <ul>
 *   <li><b>Pure functions</b> (no telemetry/side effects)</li>
 *   <li><b>Defensive</b> against divide-by-zero and domain errors</li>
 *   <li><b>Documented</b> where intent isn’t obvious</li>
 * </ul>
 */
public final class MathUtil {
    private MathUtil() {
    }

    // ----------------------------- Scalars -----------------------------

    /**
     * Clamp {@code v} to the closed interval {@code [lo, hi]}.
     */
    public static double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    /**
     * Clamp {@code v} to {@code [-m, m]}.
     */
    public static double clampAbs(double v, double m) {
        return clamp(v, -Math.abs(m), Math.abs(m));
    }

    /**
     * Clamp {@code v} to {@code [0, 1]}.
     */
    public static double clamp01(double v) {
        return clamp(v, 0.0, 1.0);
    }

    /**
     * Linear interpolation between {@code a} and {@code b} with fraction {@code t} in [0,1].
     */
    public static double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }

    /**
     * Returns true if {@code |a-b| ≤ eps}.
     */
    public static boolean epsilonEquals(double a, double b, double eps) {
        return Math.abs(a - b) <= eps;
    }

    /**
     * Returns true if {@code |a| ≤ eps}.
     */
    public static boolean epsilonZero(double a, double eps) {
        return Math.abs(a) <= eps;
    }

    /**
     * Deadband around zero with no re-scaling of the remainder.
     */
    public static double deadband(double v, double db) {
        return Math.abs(v) < db ? 0.0 : v;
    }

    /**
     * Deadband with linear re-scaling to preserve full range.
     * Maps {@code [db,1]} → {@code [0,1]} (and symmetric for negatives).
     */
    public static double deadbandRescale(double v, double db) {
        if (db <= 0) return v;
        double a = Math.abs(v);
        if (a <= db) return 0.0;
        return Math.copySign((a - db) / (1.0 - db), v);
    }

    /**
     * Safe divide: returns {@code fallback} when {@code |den| < eps}.
     * Keep for legacy uses where a custom fallback is desired.
     */
    public static double safeDiv(double num, double den, double fallback, double eps) {
        return Math.abs(den) < eps ? fallback : (num / den);
    }

    /**
     * Map {@code x} in {@code [inMin,inMax]} to {@code [outMin,outMax]} with clamping.
     */
    public static double map(double x, double inMin, double inMax, double outMin, double outMax) {
        if (inMax == inMin) return outMin;
        double t = clamp01((x - inMin) / (inMax - inMin));
        return lerp(outMin, outMax, t);
    }

    /**
     * Sign-preserving exponent. Commonly used for "expo"/sensitivity shaping.
     *
     * @param x        input in [-1,1] (typical)
     * @param exponent >= 1 recommended (1 = linear, 2 = square, ...)
     */
    public static double expoSigned(double x, double exponent) {
        double e = Math.max(1.0, exponent);
        double sign = Math.signum(x);
        return sign * Math.pow(Math.abs(x), e);
    }

    /**
     * One step of a slew-rate limiter with separate up/down rates (units/sec).
     *
     * @param prev      previous output
     * @param target    desired target
     * @param rateUp    max positive-going rate (units/sec)
     * @param rateDown  max negative-going rate (units/sec)
     * @param dtSeconds time step in seconds (≥ 0)
     * @return next output after applying the rate limit
     */
    public static double slewStep(double prev, double target, double rateUp, double rateDown, double dtSeconds) {
        double maxInc = (target >= prev ? rateUp : rateDown) * Math.max(0.0, dtSeconds);
        double delta = target - prev;
        if (Math.abs(delta) > maxInc) return prev + Math.copySign(maxInc, delta);
        return target;
    }

    /**
     * Symmetric variant of {@link #slewStep} when up/down rates are equal.
     */
    public static double slewStepSymmetric(double prev, double target, double rate, double dtSeconds) {
        return slewStep(prev, target, rate, rate, dtSeconds);
    }

    /**
     * True if finite (not NaN/Inf). Useful before sending to sinks.
     */
    public static boolean isFinite(double x) {
        return !Double.isNaN(x) && !Double.isInfinite(x);
    }

    /**
     * Coerce NaN/Inf to a fallback value (e.g., 0 for motor power).
     */
    public static double coerceFinite(double x, double fallback) {
        return isFinite(x) ? x : fallback;
    }

    /**
     * Clamp to [min,max] and coerce non-finite to fallback.
     */
    public static double clampFinite(double v, double lo, double hi, double fallback) {
        return coerceFinite(Math.max(lo, Math.min(hi, v)), fallback);
    }

    // ----------------------------- Angles -----------------------------

    /**
     * Wrap {@code x} into the half-open interval {@code [min, max)}.
     */
    public static double wrap(double x, double min, double max) {
        double w = max - min;
        double y = (x - min) % w;
        if (y < 0) y += w;
        return y + min;
    }

    /**
     * Normalize angle (degrees) to {@code (-180, 180]}.
     */
    public static double normalizeAngle(double deg) {
        double d = wrap(deg, -180.0, 180.0);
        return d == -180.0 ? 180.0 : d;
    }

    /**
     * Normalize angle (radians) to {@code (-π, π]}.
     */
    public static double normalizeRad(double rad) {
        double d = wrap(rad, -Math.PI, Math.PI);
        return d == -Math.PI ? Math.PI : d;
    }

    /**
     * Smallest difference {@code target - current} in degrees, normalized to {@code (-180,180]}.
     */
    public static double shortestAngleDeg(double currentDeg, double targetDeg) {
        return normalizeAngle(targetDeg - currentDeg);
    }

    /**
     * Smallest difference {@code target - current} in radians, normalized to {@code (-π,π]}.
     */
    public static double shortestAngleRad(double currentRad, double targetRad) {
        return normalizeRad(targetRad - currentRad);
    }

    /**
     * Degrees ↔ radians helpers.
     */
    public static double deg2rad(double deg) {
        return Math.toRadians(deg);
    }

    public static double rad2deg(double rad) {
        return Math.toDegrees(rad);
    }

    /**
     * Low-pass filter alpha from time constant τ (seconds); α∈[0,1].
     */
    public static double lowPassAlphaFromTau(double dtSeconds, double tauSeconds) {
        if (tauSeconds <= 0) return 1.0;           // no smoothing
        if (dtSeconds <= 0) return 0.0;            // hold previous
        return dtSeconds / (tauSeconds + dtSeconds);
    }

    /**
     * One low-pass step: {@code y ← y + α (x - y)}.
     */
    public static double lowPassStep(double prev, double input, double alpha) {
        double a = clamp(alpha, 0.0, 1.0);
        return prev + a * (input - prev);
    }

    // ------------------------- Vectors (3-axis) -------------------------
    // Useful for DriveSignal-like triplets (lateral, axial, omega), but generic.

    /**
     * Division with absolute denominator semantics for scaling ratios:
     * returns {@code 1.0} when {@code |den|} is ~0 (i.e., "no scaling needed from this axis").
     */
    public static double safeDivAbsOrOne(double numerator, double denominatorAbs) {
        if (denominatorAbs <= 1e-12) return 1.0;
        return numerator / denominatorAbs;
    }

    /**
     * Division with "capacity" semantics:
     * returns {@code 0.0} when capacity is ≤ 0, {@code 1.0} when {@code |den|} ~ 0 (no contribution on that axis),
     * otherwise {@code capacity / |den|}.
     */
    public static double safeDivAbsCapacity(double capacityNonNeg, double denominatorAbs) {
        if (denominatorAbs <= 1e-12) return 1.0; // no contribution → this axis does not constrain k
        if (capacityNonNeg <= 0.0) return 0.0; // no remaining capacity
        return capacityNonNeg / denominatorAbs;
    }

    /**
     * Uniform scale factor to fit a 3-axis vector within per-axis absolute limits.
     * Returns k ∈ (0,1] such that (k*x, k*y, k*z) satisfies |axis| ≤ limit for all axes.
     * If already within limits, returns 1.0.
     */
    public static double uniformScaleFactorToFit3(double x, double y, double z,
                                                  double limX, double limY, double limZ) {
        double rX = (limX > 0) ? Math.abs(x) / limX : 0.0;
        double rY = (limY > 0) ? Math.abs(y) / limY : 0.0;
        double rZ = (limZ > 0) ? Math.abs(z) / limZ : 0.0;
        double maxRatio = Math.max(1.0, Math.max(rX, Math.max(rY, rZ)));
        return 1.0 / maxRatio; // <= 1.0; 1.0 means "already fits"
    }

    /**
     * Uniform scale factor for a 3-axis contribution (cX,cY,cZ) so it fits within remaining
     * per-axis capacities (availX, availY, availZ), each ≥ 0. Returns k ∈ (0,1].
     * <p>Typical use: priority mixing where you add a new contribution onto a partially filled output.</p>
     */
    public static double uniformScaleFactorForContribution3(double cX, double cY, double cZ,
                                                            double availX, double availY, double availZ) {
        double k = 1.0;
        if (Math.abs(cX) > 1e-12) k = Math.min(k, safeDivAbsCapacity(availX, Math.abs(cX)));
        if (Math.abs(cY) > 1e-12) k = Math.min(k, safeDivAbsCapacity(availY, Math.abs(cY)));
        if (Math.abs(cZ) > 1e-12) k = Math.min(k, safeDivAbsCapacity(availZ, Math.abs(cZ)));
        if (k > 1.0) k = 1.0;
        if (k < 0.0) k = 0.0;
        return k;
    }
}

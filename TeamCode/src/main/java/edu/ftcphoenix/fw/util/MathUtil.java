package edu.ftcphoenix.fw.util;

public final class MathUtil {
    private MathUtil() {
    }

    public static double clamp(double x, double lo, double hi) {
        return Math.max(lo, Math.min(hi, x));
    }

    public static double deadband(double x, double db) {
        return Math.abs(x) < db ? 0.0 : x;
    }

    public static double copySignSquare(double x) {
        return Math.copySign(x * x, x);
    }
}

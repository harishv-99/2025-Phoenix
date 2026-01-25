package edu.ftcphoenix.fw.spatial;

import edu.ftcphoenix.fw.core.math.MathUtil;

/**
 * Factory methods for common {@link Region2d} shapes.
 */
public final class Regions2d {

    private Regions2d() {
    }

    /**
     * Axis-aligned rectangle region.
     *
     * <p>Coordinates are expressed in the same frame as the point you test.</p>
     *
     * @param minXInches left (minimum X)
     * @param maxXInches right (maximum X)
     * @param minYInches bottom (minimum Y)
     * @param maxYInches top (maximum Y)
     */
    public static Region2d rectangleAabb(double minXInches, double maxXInches,
                                         double minYInches, double maxYInches) {
        double minX = Math.min(minXInches, maxXInches);
        double maxX = Math.max(minXInches, maxXInches);
        double minY = Math.min(minYInches, maxYInches);
        double maxY = Math.max(minYInches, maxYInches);
        return (x, y) -> {
            // Signed distance to an AABB: inside -> +min distance to any edge.
            double dxIn = Math.min(x - minX, maxX - x);
            double dyIn = Math.min(y - minY, maxY - y);
            if (x >= minX && x <= maxX && y >= minY && y <= maxY) {
                return Math.min(dxIn, dyIn);
            }

            // Outside -> negative distance to the rectangle.
            double dxOut = 0.0;
            if (x < minX) {
                dxOut = minX - x;
            } else if (x > maxX) {
                dxOut = x - maxX;
            }

            double dyOut = 0.0;
            if (y < minY) {
                dyOut = minY - y;
            } else if (y > maxY) {
                dyOut = y - maxY;
            }

            // Negative distance outside; use hypot for corners.
            return -Math.hypot(dxOut, dyOut);
        };
    }

    /**
     * Circle region.
     *
     * @param centerXInches center X
     * @param centerYInches center Y
     * @param radiusInches  radius (must be >= 0)
     */
    public static Region2d circle(double centerXInches, double centerYInches, double radiusInches) {
        double r = Math.max(0.0, radiusInches);
        return (x, y) -> {
            double d = Math.hypot(x - centerXInches, y - centerYInches);
            return r - d;
        };
    }

    /**
     * Convenience: clamp a signed-distance output.
     *
     * <p>This is occasionally useful when you want to treat “very far outside” as the same
     * as “somewhat outside” for a latch input.</p>
     */
    public static Region2d clamped(Region2d region, double min, double max) {
        return (x, y) -> MathUtil.clamp(region.signedDistanceInches(x, y), min, max);
    }
}

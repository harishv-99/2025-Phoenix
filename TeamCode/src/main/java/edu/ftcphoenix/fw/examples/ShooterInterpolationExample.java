package edu.ftcphoenix.fw.examples;

import edu.ftcphoenix.fw.util.InterpolatingTable1D;

/**
 * Example: distance → shooter velocity lookup using InterpolatingTable1D.
 *
 * <p>This example shows how to:</p>
 * <ul>
 *   <li>Declare a distance → shooter velocity table using {@link InterpolatingTable1D}.</li>
 *   <li>Build that table in three different ways (arrays, pairs, builder).</li>
 *   <li>Wire it between a distance source (vision / sensor) and a shooter subsystem.</li>
 *   <li>Call it once per loop from TeleOp / Autonomous code.</li>
 * </ul>
 *
 * <p>Units in this example:</p>
 * <ul>
 *   <li>Distance: inches.</li>
 *   <li>Shooter velocity: rad/s (change to RPM if you prefer; just keep the table consistent).</li>
 * </ul>
 *
 * <p>Typical usage from an OpMode:</p>
 *
 * <pre>{@code
 * // Somewhere in your robot / OpMode init:
 * ShooterInterpolationExample controller = new ShooterInterpolationExample(
 *     vision::getTargetDistanceInches,      // DistanceSource
 *     shooterSubsystem::setTargetRadPerSec  // ShooterCommand
 * );
 *
 * // In your loop():
 * controller.update();
 * }</pre>
 */
public final class ShooterInterpolationExample {

    // -------------------------------------------------------------------------
    //  Three equivalent ways to build the same distance → velocity table.
    //  Pick whichever style you like in your robot code.
    // -------------------------------------------------------------------------

    /**
     * Style 1: from parallel sorted arrays (original API).
     */
    private static final InterpolatingTable1D SHOOTER_TABLE_SORTED_ARRAYS =
            InterpolatingTable1D.ofSorted(
                    new double[]{
                            24.0, // close shot
                            30.0,
                            36.0,
                            42.0,
                            48.0  // farther shot
                    },
                    new double[]{
                            170.0, // rad/s at 24"
                            180.0, // rad/s at 30"
                            195.0, // rad/s at 36"
                            210.0, // rad/s at 42"
                            225.0  // rad/s at 48"
                    }
            );

    /**
     * Style 2: from flattened (x, y) pairs.
     *
     * <p>Handy for one-liners and avoids manual array creation.</p>
     */
    private static final InterpolatingTable1D SHOOTER_TABLE_SORTED_PAIRS =
            InterpolatingTable1D.ofSortedPairs(
                    24.0, 170.0,
                    30.0, 180.0,
                    36.0, 195.0,
                    42.0, 210.0,
                    48.0, 225.0
            );

    /**
     * Style 3: using the builder for maximum readability.
     *
     * <p>This is nice when you're tweaking calibration values often.</p>
     */
    private static final InterpolatingTable1D SHOOTER_TABLE_BUILDER =
            InterpolatingTable1D.builder()
                    .add(24.0, 170.0)
                    .add(30.0, 180.0)
                    .add(36.0, 195.0)
                    .add(42.0, 210.0)
                    .add(48.0, 225.0)
                    .build();

    /**
     * The table actually used by this example.
     *
     * <p>Swap this to any of the three above to try different creation styles:</p>
     *
     * <pre>{@code
     * private static final InterpolatingTable1D SHOOTER_VELOCITY_TABLE =
     *         SHOOTER_TABLE_SORTED_ARRAYS;
     * }</pre>
     */
    private static final InterpolatingTable1D SHOOTER_VELOCITY_TABLE =
            SHOOTER_TABLE_BUILDER;

    // -------------------------------------------------------------------------
    //  Simple functional interfaces to keep this example framework-agnostic.
    // -------------------------------------------------------------------------

    /**
     * Source of target distance (e.g., from vision or a range sensor).
     */
    public interface DistanceSource {
        /**
         * @return target distance in inches.
         */
        double getTargetDistanceInches();
    }

    /**
     * Sink for shooter velocity commands (e.g., wraps your shooter subsystem / RPM controller).
     */
    public interface ShooterCommand {
        /**
         * Command the shooter target velocity.
         *
         * @param velocityRadPerSec target in rad/s (or RPM if your table uses RPM).
         */
        void setTargetVelocity(double velocityRadPerSec);
    }

    private final DistanceSource distanceSource;
    private final ShooterCommand shooterCommand;

    /**
     * Wire together the distance source and shooter command using the interpolation table.
     *
     * @param distanceSource provides distance in inches
     * @param shooterCommand consumes target shooter velocity
     */
    public ShooterInterpolationExample(DistanceSource distanceSource,
                                       ShooterCommand shooterCommand) {
        this.distanceSource = distanceSource;
        this.shooterCommand = shooterCommand;
    }

    /**
     * Call once per loop from your TeleOp or Autonomous code.
     *
     * <p>Reads the current distance, computes the interpolated shooter velocity,
     * and sends it to the shooter subsystem.</p>
     */
    public void update() {
        double distanceInches = distanceSource.getTargetDistanceInches();

        // Lookup with clamping and interpolation.
        double targetVelocityRadPerSec = SHOOTER_VELOCITY_TABLE.interpolate(distanceInches);

        shooterCommand.setTargetVelocity(targetVelocityRadPerSec);
    }

    /**
     * Optional: expose the table so other code (e.g., telemetry) can inspect it or log entries.
     */
    public static InterpolatingTable1D shooterVelocityTable() {
        return SHOOTER_VELOCITY_TABLE;
    }

    /*
     * Optional note:
     *
     * If InterpolatingTable1D implements DoubleUnaryOperator,
     * you can also treat it as a function:
     *
     *   DoubleUnaryOperator shooterVel = SHOOTER_TABLE_SORTED_PAIRS;
     *   double vel = shooterVel.applyAsDouble(distanceInches);
     *
     * This can be convenient when composing with other functional utilities.
     */
}

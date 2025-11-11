package edu.ftcphoenix.fw.drive.source;

import edu.ftcphoenix.fw.input.Axis;

import org.firstinspires.ftc.robotcore.external.Telemetry;

import java.util.Objects;
import java.util.function.Supplier;

/**
 * Minimal mapper from pre-shaped gamepad axes to a chassis command (x, y, omega).
 *
 * <h2>Intent</h2>
 * Keep drive mapping dead-simple: shape inputs at the {@link Axis} level (deadband/expo/slew),
 * then scale rotation with an optional slow-mode. This class makes no assumptions about your
 * drivetrain implementation—consume {@link ChassisCommand} however you like.
 *
 * <h2>Typical usage</h2>
 * <pre>{@code
 * // Build shaped axes with DriverKit (or Inputs + Axis filters)
 * var kit = DriverKit.of(pads);
 * Axis lx = kit.p1().lx();
 * Axis ly = kit.p1().ly();
 * Axis rx = kit.p1().rx();
 *
 * // Slow omega while LB long-held
 * Supplier<Double> omegaScale = kit.p1().slowModeScale(0.5); // 50% while held
 *
 * GamepadDriveSource mapper = new GamepadDriveSource(lx, ly, rx)
 *     .maxOmega(2.5)
 *     .omegaScale(omegaScale);
 *
 * // In loop after pads.update(dt):
 * GamepadDriveSource.ChassisCommand cmd = mapper.sample();
 * drive.setLateral(cmd.x);
 * drive.setAxial(cmd.y);
 * drive.setOmega(cmd.omega);
 * }</pre>
 *
 * <h2>Design choices</h2>
 * <ul>
 *   <li>No "square" option here—use {@link Axis#expo(double)} upstream (avoids duplication).</li>
 *   <li>No field-oriented math here—layer that above if needed.</li>
 *   <li>No clamping here—the caller may clamp or normalize if their drive requires it.</li>
 * </ul>
 */
public final class GamepadDriveSource {

    private final Axis lateral; // +X right/left per your drive code
    private final Axis axial;   // +Y forward/back (up is + thanks to GamepadDevice)
    private final Axis angular; // rotation command in [-1,1] (already shaped)

    private double maxOmega = 1.0;                  // scales angular → rad/s (or unit your drive expects)
    private Supplier<Double> omegaScale = () -> 1.0; // dynamic multiplier (e.g., slow mode)

    /**
     * @param lateral shaped lateral axis (e.g., left stick X)
     * @param axial   shaped axial axis (e.g., left stick Y)
     * @param angular shaped rotation axis (e.g., right stick X)
     */
    public GamepadDriveSource(Axis lateral, Axis axial, Axis angular) {
        this.lateral = Objects.requireNonNull(lateral, "lateral");
        this.axial = Objects.requireNonNull(axial, "axial");
        this.angular = Objects.requireNonNull(angular, "angular");
    }

    /**
     * Max rotational rate (e.g., rad/s) applied to the angular axis.
     */
    public GamepadDriveSource maxOmega(double value) {
        this.maxOmega = Math.max(0.0, value);
        return this;
    }

    /**
     * Optional dynamic multiplier for omega each tick (e.g., slow mode while LB long-held).
     */
    public GamepadDriveSource omegaScale(Supplier<Double> scale) {
        this.omegaScale = (scale != null) ? scale : () -> 1.0;
        return this;
    }

    /**
     * Snapshot the current axes into a {@link ChassisCommand}. Call once per loop after inputs update.
     */
    public ChassisCommand sample() {
        double x = lateral.get();
        double y = axial.get();
        double w = angular.get() * maxOmega * safe(omegaScale.get());
        return new ChassisCommand(x, y, w);
    }

    /**
     * Optional quick reads if you don't need the full command object.
     */
    public double lateral() {
        return lateral.get();
    }

    public double axial() {
        return axial.get();
    }

    public double omega() {
        return angular.get() * maxOmega * safe(omegaScale.get());
    }

    /**
     * Compact state for chassis control. Immutable value object.
     */
    public static final class ChassisCommand {
        public final double x;     // lateral
        public final double y;     // axial
        public final double omega; // rotation (scaled)

        public ChassisCommand(double x, double y, double omega) {
            this.x = x;
            this.y = y;
            this.omega = omega;
        }
    }

    /**
     * Optional telemetry for tuning.
     */
    public void addTelemetry(Telemetry t, String label) {
        if (t == null) return;
        t.addLine(label);
        t.addData("lat", lateral.get())
                .addData("ax", axial.get())
                .addData("om", angular.get());
        t.addData("maxOmega", maxOmega).addData("omegaScale", safe(omegaScale.get()));
    }

    private static double safe(Double d) {
        return (d == null || d.isNaN() || d.isInfinite()) ? 1.0 : d;
    }
}

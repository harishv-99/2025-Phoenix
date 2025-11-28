package edu.ftcphoenix.fw.actuation;

import edu.ftcphoenix.fw.hal.PowerOutput;
import edu.ftcphoenix.fw.hal.PositionOutput;
import edu.ftcphoenix.fw.hal.VelocityOutput;
import edu.ftcphoenix.fw.util.MathUtil;

/**
 * Helpers for constructing {@link Plant} instances on top of Phoenix HAL
 * output interfaces using native units.
 *
 * <p>This class sits just above the hardware-abstraction layer:
 * it takes {@link PowerOutput}, {@link PositionOutput}, and
 * {@link VelocityOutput} channels (in their <b>native</b> units) and wraps
 * them in simple {@link Plant} implementations.</p>
 *
 * <h2>Unit conventions</h2>
 *
 * <ul>
 *   <li><b>Power plants</b>:
 *       <ul>
 *         <li>Target: normalized power (typically {@code [-1.0, +1.0]}).</li>
 *       </ul>
 *   </li>
 *   <li><b>Position plants</b>:
 *       <ul>
 *         <li>Servos: {@code 0.0 .. 1.0} (normalized position).</li>
 *         <li>Motors: encoder ticks (or other native units defined by the adapter).</li>
 *       </ul>
 *       <p>Position plants are "set-and-hold": they command the target
 *       position and consider themselves at setpoint immediately (there is
 *       no measured position exposed in {@link PositionOutput}).</p>
 *   </li>
 *   <li><b>Velocity plants</b>:
 *       <ul>
 *         <li>Motors: encoder ticks per second (or other native velocity
 *             units defined by the adapter).</li>
 *       </ul>
 *       <p>Velocity plants compare commanded vs. measured velocity (via
 *       {@link VelocityOutput#getMeasuredVelocity()}) and implement
 *       {@link Plant#atSetpoint()} with a configurable tolerance in native
 *       velocity units.</p>
 *   </li>
 * </ul>
 *
 * <p>Higher-level code is free to convert to/from physical units
 * (radians, degrees, meters), but this class deliberately stays in
 * native units to keep the interface small and portable.</p>
 */
public final class Plants {

    private Plants() {
        // utility class; no instances
    }

    // =====================================================================
    // POWER PLANTS (PowerOutput, normalized power)
    // =====================================================================

    /**
     * Open-loop power plant driving a single {@link PowerOutput}.
     *
     * <p>The target is treated as a normalized power command, typically
     * in the range {@code [-1.0, +1.0]}. The underlying
     * {@link PowerOutput} implementation is responsible for mapping
     * that to the platform (motor power, CR servo power, etc.).</p>
     *
     * @param out power channel (motor, CR servo, etc.)
     */
    public static Plant power(PowerOutput out) {
        if (out == null) {
            throw new IllegalArgumentException("PowerOutput is required");
        }
        return new PowerPlant(out);
    }

    /**
     * Plant that drives two {@link PowerOutput}s with the same power.
     *
     * <p>Direction handling (inversion) should be done by the adapter
     * that created the {@link PowerOutput}s; this plant simply mirrors
     * the same target to both outputs.</p>
     *
     * @param a first power channel
     * @param b second power channel
     */
    public static Plant powerPair(PowerOutput a, PowerOutput b) {
        if (a == null || b == null) {
            throw new IllegalArgumentException("Both PowerOutputs are required");
        }
        return new PowerPairPlant(a, b);
    }

    // =====================================================================
    // POSITION PLANTS (PositionOutput, native units)
    // =====================================================================

    /**
     * Position plant over a {@link PositionOutput} in native units.
     *
     * <p>For servos this is typically {@code 0.0 .. 1.0}. For motors,
     * this is encoder ticks (or any other native position unit defined
     * by the adapter).</p>
     *
     * <p>This plant is "set-and-hold": it commands the target position
     * when {@link Plant#setTarget(double)} is called and considers itself
     * at setpoint immediately (i.e., {@link Plant#atSetpoint()} always returns
     * {@code true}). If you need more sophisticated position setpoint
     * detection, combine this with sensor feedback in your own logic or
     * extend this pattern with a measured position source.</p>
     *
     * @param out position channel
     */
    public static Plant position(PositionOutput out) {
        if (out == null) {
            throw new IllegalArgumentException("PositionOutput is required");
        }
        return new PositionPlant(out);
    }

    /**
     * Position plant that drives two {@link PositionOutput}s with the
     * same native position target.
     *
     * <p>Typical use: two symmetric servos or motors that should track
     * the same position command.</p>
     *
     * @param a first position channel
     * @param b second position channel
     */
    public static Plant positionPair(PositionOutput a, PositionOutput b) {
        if (a == null || b == null) {
            throw new IllegalArgumentException("Both PositionOutputs are required");
        }
        return new PositionPairPlant(a, b);
    }

    // =====================================================================
    // VELOCITY PLANTS (VelocityOutput, native units)
    // =====================================================================

    /**
     * Velocity plant over a single {@link VelocityOutput} in native units
     * (for example, encoder ticks per second).
     *
     * <p>{@link Plant#atSetpoint()} compares the measured velocity (via
     * {@link VelocityOutput#getMeasuredVelocity()}) to the commanded
     * target and returns {@code true} when the absolute error is less
     * than or equal to {@code toleranceNative}.</p>
     *
     * @param out             velocity channel
     * @param toleranceNative allowed error between commanded and measured,
     *                        in native velocity units
     */
    public static Plant velocity(VelocityOutput out, double toleranceNative) {
        if (out == null) {
            throw new IllegalArgumentException("VelocityOutput is required");
        }
        if (toleranceNative < 0.0) {
            throw new IllegalArgumentException("toleranceNative must be >= 0");
        }
        return new VelocityPlant(out, toleranceNative);
    }

    /**
     * Velocity plant that drives two {@link VelocityOutput}s with the
     * same target velocity, and considers itself at setpoint only when
     * <b>both</b> channels are within {@code toleranceNative} of the
     * commanded velocity.
     *
     * @param a               first velocity channel
     * @param b               second velocity channel
     * @param toleranceNative allowed error between commanded and measured,
     *                        in native velocity units
     */
    public static Plant velocityPair(VelocityOutput a,
                                     VelocityOutput b,
                                     double toleranceNative) {
        if (a == null || b == null) {
            throw new IllegalArgumentException("Both VelocityOutputs are required");
        }
        if (toleranceNative < 0.0) {
            throw new IllegalArgumentException("toleranceNative must be >= 0");
        }
        return new VelocityPairPlant(a, b, toleranceNative);
    }

    // =====================================================================
    // INTERNAL IMPLEMENTATIONS
    // =====================================================================

    /**
     * Internal implementation for single-output power.
     */
    private static final class PowerPlant implements Plant {
        private final PowerOutput out;
        private double target = 0.0;

        PowerPlant(PowerOutput out) {
            this.out = out;
        }

        @Override
        public void setTarget(double target) {
            double p = MathUtil.clamp(target, -1.0, 1.0);
            this.target = p;
            out.setPower(p);
        }

        @Override
        public double getTarget() {
            return target;
        }

        @Override
        public void update(double dtSec) {
            // Open-loop: nothing to do per tick.
        }

        @Override
        public boolean atSetpoint() {
            // Open-loop; no notion of "reached" beyond the command itself.
            return true;
        }

        @Override
        public String toString() {
            return "PowerPlant{target=" + target + "}";
        }
    }

    /**
     * Internal implementation for dual-output power.
     */
    private static final class PowerPairPlant implements Plant {
        private final PowerOutput a;
        private final PowerOutput b;
        private double target = 0.0;

        PowerPairPlant(PowerOutput a, PowerOutput b) {
            this.a = a;
            this.b = b;
        }

        @Override
        public void setTarget(double target) {
            double p = MathUtil.clamp(target, -1.0, 1.0);
            this.target = p;
            a.setPower(p);
            b.setPower(p);
        }

        @Override
        public double getTarget() {
            return target;
        }

        @Override
        public void update(double dtSec) {
            // Open-loop.
        }

        @Override
        public boolean atSetpoint() {
            return true;
        }

        @Override
        public String toString() {
            return "PowerPairPlant{target=" + target + "}";
        }
    }

    /**
     * Internal implementation for single-output position (native units).
     */
    private static final class PositionPlant implements Plant {
        private final PositionOutput out;
        private double target = 0.0;

        PositionPlant(PositionOutput out) {
            this.out = out;
        }

        @Override
        public void setTarget(double target) {
            this.target = target;
            out.setPosition(target);
        }

        @Override
        public double getTarget() {
            return target;
        }

        @Override
        public void update(double dtSec) {
            // set-and-hold; no additional per-tick work.
        }

        @Override
        public boolean atSetpoint() {
            // Without a measured position, we treat "commanded" as "reached".
            return true;
        }

        @Override
        public String toString() {
            return "PositionPlant{target=" + target + "}";
        }
    }

    /**
     * Internal implementation for dual-output position (native units).
     */
    private static final class PositionPairPlant implements Plant {
        private final PositionOutput a;
        private final PositionOutput b;
        private double target = 0.0;

        PositionPairPlant(PositionOutput a, PositionOutput b) {
            this.a = a;
            this.b = b;
        }

        @Override
        public void setTarget(double target) {
            this.target = target;
            a.setPosition(target);
            b.setPosition(target);
        }

        @Override
        public double getTarget() {
            return target;
        }

        @Override
        public void update(double dtSec) {
            // set-and-hold.
        }

        @Override
        public boolean atSetpoint() {
            return true;
        }

        @Override
        public String toString() {
            return "PositionPairPlant{target=" + target + "}";
        }
    }

    /**
     * Internal implementation for single-output velocity (native units).
     */
    private static final class VelocityPlant implements Plant {
        private final VelocityOutput out;
        private final double toleranceNative;
        private double target = 0.0;

        VelocityPlant(VelocityOutput out, double toleranceNative) {
            this.out = out;
            this.toleranceNative = toleranceNative;
        }

        @Override
        public void setTarget(double target) {
            this.target = target;
            out.setVelocity(target);
        }

        @Override
        public double getTarget() {
            return target;
        }

        @Override
        public void update(double dtSec) {
            // No additional control logic; relies on underlying implementation.
        }

        @Override
        public boolean atSetpoint() {
            double measured = out.getMeasuredVelocity();
            double error = measured - target;
            return Math.abs(error) <= toleranceNative;
        }

        @Override
        public String toString() {
            return "VelocityPlant{target=" + target +
                    ", toleranceNative=" + toleranceNative + "}";
        }
    }

    /**
     * Internal implementation for dual-output velocity (native units).
     */
    private static final class VelocityPairPlant implements Plant {
        private final VelocityOutput a;
        private final VelocityOutput b;
        private final double toleranceNative;
        private double target = 0.0;

        VelocityPairPlant(VelocityOutput a,
                          VelocityOutput b,
                          double toleranceNative) {
            this.a = a;
            this.b = b;
            this.toleranceNative = toleranceNative;
        }

        @Override
        public void setTarget(double target) {
            this.target = target;
            a.setVelocity(target);
            b.setVelocity(target);
        }

        @Override
        public double getTarget() {
            return target;
        }

        @Override
        public void update(double dtSec) {
            // No additional control logic; relies on underlying implementation.
        }

        @Override
        public boolean atSetpoint() {
            double errA = a.getMeasuredVelocity() - target;
            double errB = b.getMeasuredVelocity() - target;
            return Math.abs(errA) <= toleranceNative &&
                    Math.abs(errB) <= toleranceNative;
        }

        @Override
        public String toString() {
            return "VelocityPairPlant{target=" + target +
                    ", toleranceNative=" + toleranceNative + "}";
        }
    }
}

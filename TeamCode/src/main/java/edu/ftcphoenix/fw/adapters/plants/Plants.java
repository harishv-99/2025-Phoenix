package edu.ftcphoenix.fw.adapters.plants;

import com.qualcomm.robotcore.hardware.DcMotor;
import com.qualcomm.robotcore.hardware.DcMotorEx;
import com.qualcomm.robotcore.hardware.HardwareMap;

import edu.ftcphoenix.fw.adapters.ftc.FtcHardware;
import edu.ftcphoenix.fw.util.MathUtil;
import edu.ftcphoenix.fw.hal.MotorOutput;
import edu.ftcphoenix.fw.hal.ServoOutput;
import edu.ftcphoenix.fw.stage.setpoint.SetpointStage;

/**
 * Helpers for constructing {@link SetpointStage.Plant} implementations
 * from FTC hardware.
 *
 * <p>Design goals:
 * <ul>
 *   <li>Keep common FTC use-cases trivial to wire from {@link HardwareMap}.</li>
 *   <li>Hide FTC SDK details behind small, reusable plant implementations.</li>
 *   <li>Provide both single-output and paired-output variants with consistent naming.</li>
 * </ul>
 *
 * <p>Single-output plants:
 * <ul>
 *   <li>{@link #power(HardwareMap, String, boolean)} – open-loop power.</li>
 *   <li>{@link #velocity(HardwareMap, String, double, boolean)} – velocity (rad/s).</li>
 *   <li>{@link #servoPosition(HardwareMap, String, boolean)} – servo position [0,1].</li>
 *   <li>{@link #motorPosition(HardwareMap, String, double, boolean, double)} – motor angle (rad).</li>
 * </ul>
 *
 * <p>Paired-output plants (two outputs driven as one mechanism):
 * <ul>
 *   <li>{@link #powerPair(HardwareMap, String, boolean, String, boolean)} – dual power.</li>
 *   <li>{@link #velocityPair(HardwareMap, String, boolean, String, boolean, double)} – dual velocity.</li>
 *   <li>{@link #servoPositionPair(HardwareMap, String, boolean, String, boolean)} – dual servo.</li>
 *   <li>{@link #motorPositionPair(HardwareMap, String, boolean, String, boolean, double, double)} – dual angle.</li>
 * </ul>
 */
public final class Plants {

    private Plants() {
        // utility holder
    }

    // =====================================================================
    // POWER PLANTS (SINGLE)
    // =====================================================================

    /**
     * Open-loop power plant from a {@link MotorOutput}.
     *
     * <p>Semantics:
     * <ul>
     *   <li>target ∈ [-1, +1] → normalized power.</li>
     *   <li>{@link SetpointStage.Plant#update(double)} is a no-op.</li>
     *   <li>{@link SetpointStage.Plant#atSetpoint()} always returns true.</li>
     * </ul>
     */
    public static SetpointStage.Plant power(MotorOutput motor, boolean inverted) {
        if (motor == null) {
            throw new IllegalArgumentException("motor is required");
        }
        return new PowerPlant(motor, inverted);
    }

    /**
     * Convenience: open-loop power plant from a motor name.
     *
     * <p>Students only need to know the hardware name and whether it is
     * inverted in their configuration.</p>
     */
    public static SetpointStage.Plant power(HardwareMap hw, String name, boolean inverted) {
        MotorOutput m = FtcHardware.motor(hw, name, inverted);
        // MotorOutput already handles inversion; plant can use non-inverted.
        return power(m, false);
    }

    /**
     * Internal implementation for open-loop power.
     */
    private static final class PowerPlant implements SetpointStage.Plant {
        private final MotorOutput motor;
        private final boolean inverted;

        private double target = 0.0;

        PowerPlant(MotorOutput motor, boolean inverted) {
            this.motor = motor;
            this.inverted = inverted;
        }

        @Override
        public void setTarget(double target) {
            double p = MathUtil.clamp(target, -1.0, 1.0);
            this.target = p;
            double cmd = inverted ? -p : p;
            motor.setPower(cmd);
        }

        @Override
        public void update(double dtSec) {
            // No-op: open-loop, last commanded power is held.
        }

        @Override
        public boolean atSetpoint() {
            // Open-loop power has no "setpoint" concept; consider it always ready.
            return true;
        }

        @Override
        public String toString() {
            return "PowerPlant{target=" + target + "}";
        }
    }

    // =====================================================================
    // POWER PLANTS (PAIR)
    // =====================================================================

    /**
     * Plant that drives two {@link MotorOutput}s with the same power.
     *
     * <p>This is useful for dual-shooter mechanisms or paired intakes where
     * both motors should always be commanded together.</p>
     *
     * <p>Semantics:
     * <ul>
     *   <li>target ∈ [-1, +1] → normalized power.</li>
     *   <li>{@link SetpointStage.Plant#update(double)} is a no-op.</li>
     *   <li>{@link SetpointStage.Plant#atSetpoint()} always returns true.</li>
     * </ul>
     */
    public static SetpointStage.Plant powerPair(MotorOutput a,
                                                boolean invertA,
                                                MotorOutput b,
                                                boolean invertB) {
        if (a == null || b == null) {
            throw new IllegalArgumentException("Both motors are required");
        }
        return new PowerPairPlant(a, invertA, b, invertB);
    }

    /**
     * Convenience: dual power plant from two motor names.
     *
     * <p>Each motor can have its own inversion flag.</p>
     */
    public static SetpointStage.Plant powerPair(HardwareMap hw,
                                                String nameA,
                                                boolean invertA,
                                                String nameB,
                                                boolean invertB) {
        MotorOutput a = FtcHardware.motor(hw, nameA, invertA);
        MotorOutput b = FtcHardware.motor(hw, nameB, invertB);
        // Outputs already handle inversion; plant can treat both as non-inverted.
        return powerPair(a, false, b, false);
    }

    /**
     * Internal implementation for paired open-loop power.
     */
    private static final class PowerPairPlant implements SetpointStage.Plant {
        private final MotorOutput a;
        private final MotorOutput b;
        private final boolean invertA;
        private final boolean invertB;

        private double target = 0.0;

        PowerPairPlant(MotorOutput a, boolean invertA, MotorOutput b, boolean invertB) {
            this.a = a;
            this.b = b;
            this.invertA = invertA;
            this.invertB = invertB;
        }

        @Override
        public void setTarget(double target) {
            double p = target;
            this.target = p;
            double cmdA = invertA ? -p : p;
            double cmdB = invertB ? -p : p;
            a.setPower(cmdA);
            b.setPower(cmdB);
        }

        @Override
        public void update(double dtSec) {
            // No-op: open-loop, last commanded power is held.
        }

        @Override
        public boolean atSetpoint() {
            // Open-loop power has no "setpoint" concept; consider it always ready.
            return true;
        }

        @Override
        public String toString() {
            return "PowerPairPlant{target=" + target + "}";
        }
    }

    // =====================================================================
    // VELOCITY PLANTS (SINGLE)
    // =====================================================================

    /**
     * Velocity plant using a {@link DcMotorEx} with encoder.
     *
     * <p>Semantics:
     * <ul>
     *   <li>target is angular velocity in rad/s at the motor shaft.</li>
     *   <li>Uses {@link DcMotorEx#setVelocity(double)} under the hood with
     *       ticks/sec computed from the specified ticks-per-revolution.</li>
     *   <li>No internal closed-loop control beyond what the SDK provides.</li>
     * </ul>
     */
    public static SetpointStage.Plant velocity(DcMotorEx motor,
                                               double ticksPerRev,
                                               boolean inverted) {
        if (motor == null) {
            throw new IllegalArgumentException("motor is required");
        }
        if (ticksPerRev <= 0.0) {
            throw new IllegalArgumentException("ticksPerRev must be > 0");
        }
        return new VelocityPlant(motor, ticksPerRev, inverted);
    }

    /**
     * Convenience: velocity plant from a motor name.
     *
     * <p>The ticks-per-rev value should match your motor + encoder setup.</p>
     */
    public static SetpointStage.Plant velocity(HardwareMap hw,
                                               String name,
                                               double ticksPerRev,
                                               boolean inverted) {
        DcMotorEx m = hw.get(DcMotorEx.class, name);
        return velocity(m, ticksPerRev, inverted);
    }

    /**
     * Internal implementation for single-motor velocity.
     */
    private static final class VelocityPlant implements SetpointStage.Plant {
        private final DcMotorEx motor;
        private final double ticksPerRev;
        private final boolean inverted;

        private double targetRadPerSec = 0.0;

        VelocityPlant(DcMotorEx motor, double ticksPerRev, boolean inverted) {
            this.motor = motor;
            this.ticksPerRev = ticksPerRev;
            this.inverted = inverted;
        }

        @Override
        public void setTarget(double targetRadPerSec) {
            this.targetRadPerSec = targetRadPerSec;
            double radPerSec = inverted ? -targetRadPerSec : targetRadPerSec;
            double revPerSec = radPerSec / (2.0 * Math.PI);
            double ticksPerSec = revPerSec * ticksPerRev;
            motor.setVelocity(ticksPerSec);
        }

        @Override
        public void update(double dtSec) {
            // No-op: we rely on the SDK's internal velocity control.
        }

        @Override
        public boolean atSetpoint() {
            // Without feedback, we assume velocity is maintained as requested.
            return true;
        }

        @Override
        public String toString() {
            return "VelocityPlant{targetRadPerSec=" + targetRadPerSec + "}";
        }
    }

    // =====================================================================
    // VELOCITY PLANTS (PAIR)
    // =====================================================================

    /**
     * Paired velocity plant using two {@link DcMotorEx} with encoders.
     *
     * <p>Semantics:
     * <ul>
     *   <li>target is angular velocity in rad/s, applied to both motors.</li>
     *   <li>Each motor may be inverted independently.</li>
     *   <li>Uses {@link DcMotorEx#setVelocity(double)} for each motor.</li>
     * </ul>
     */
    public static SetpointStage.Plant velocityPair(DcMotorEx a,
                                                   boolean invertA,
                                                   DcMotorEx b,
                                                   boolean invertB,
                                                   double ticksPerRev) {
        if (a == null || b == null) {
            throw new IllegalArgumentException("Both motors are required");
        }
        if (ticksPerRev <= 0.0) {
            throw new IllegalArgumentException("ticksPerRev must be > 0");
        }
        return new VelocityPairPlant(a, invertA, b, invertB, ticksPerRev);
    }

    /**
     * Convenience: paired velocity plant from two motor names.
     */
    public static SetpointStage.Plant velocityPair(HardwareMap hw,
                                                   String nameA,
                                                   boolean invertA,
                                                   String nameB,
                                                   boolean invertB,
                                                   double ticksPerRev) {
        DcMotorEx a = hw.get(DcMotorEx.class, nameA);
        DcMotorEx b = hw.get(DcMotorEx.class, nameB);
        return velocityPair(a, invertA, b, invertB, ticksPerRev);
    }

    /**
     * Internal implementation for dual-motor velocity.
     */
    private static final class VelocityPairPlant implements SetpointStage.Plant {
        private final DcMotorEx a;
        private final DcMotorEx b;
        private final boolean invertA;
        private final boolean invertB;
        private final double ticksPerRev;

        private double targetRadPerSec = 0.0;

        VelocityPairPlant(DcMotorEx a,
                          boolean invertA,
                          DcMotorEx b,
                          boolean invertB,
                          double ticksPerRev) {
            this.a = a;
            this.b = b;
            this.invertA = invertA;
            this.invertB = invertB;
            this.ticksPerRev = ticksPerRev;
        }

        @Override
        public void setTarget(double targetRadPerSec) {
            this.targetRadPerSec = targetRadPerSec;

            double radA = invertA ? -targetRadPerSec : targetRadPerSec;
            double radB = invertB ? -targetRadPerSec : targetRadPerSec;

            double revA = radA / (2.0 * Math.PI);
            double revB = radB / (2.0 * Math.PI);

            double ticksPerSecA = revA * ticksPerRev;
            double ticksPerSecB = revB * ticksPerRev;

            a.setVelocity(ticksPerSecA);
            b.setVelocity(ticksPerSecB);
        }

        @Override
        public void update(double dtSec) {
            // No-op; rely on SDK's internal velocity control.
        }

        @Override
        public boolean atSetpoint() {
            // Without feedback, assume both motors are maintained at the target speed.
            return true;
        }

        @Override
        public String toString() {
            return "VelocityPairPlant{targetRadPerSec=" + targetRadPerSec + "}";
        }
    }

    // =====================================================================
    // SERVO POSITION PLANTS (SINGLE)
    // =====================================================================

    /**
     * Servo position plant using a {@link ServoOutput}.
     *
     * <p>Semantics:
     * <ul>
     *   <li>target ∈ [0, 1] → servo position.</li>
     *   <li>No feedback; {@link SetpointStage.Plant#atSetpoint()} always returns true.</li>
     * </ul>
     */
    public static SetpointStage.Plant servoPosition(ServoOutput servo) {
        if (servo == null) {
            throw new IllegalArgumentException("servo is required");
        }
        return new ServoPositionPlant(servo);
    }

    /**
     * Convenience: servo position plant using a hardware name.
     */
    public static SetpointStage.Plant servoPosition(HardwareMap hw,
                                                    String name,
                                                    boolean inverted) {
        ServoOutput s = FtcHardware.servo(hw, name, inverted);
        return servoPosition(s);
    }

    /**
     * Internal implementation for simple servo position plants.
     */
    private static final class ServoPositionPlant implements SetpointStage.Plant {
        private final ServoOutput servo;
        private double target = 0.0;

        ServoPositionPlant(ServoOutput servo) {
            this.servo = servo;
        }

        @Override
        public void setTarget(double target) {
            double pos = MathUtil.clamp01(target);
            this.target = pos;
            servo.setPosition(pos);
        }

        @Override
        public void update(double dtSec) {
            // No-op; we assume servo moves on its own.
        }

        @Override
        public boolean atSetpoint() {
            // Without feedback, we assume servo is always "ready".
            return true;
        }

        @Override
        public String toString() {
            return "ServoPositionPlant{target=" + target + "}";
        }
    }

    // =====================================================================
    // SERVO POSITION PLANTS (PAIR)
    // =====================================================================

    /**
     * Paired servo position plant using two {@link ServoOutput}s.
     *
     * <p>Semantics:
     * <ul>
     *   <li>target ∈ [0, 1] → same position command for both servos.</li>
     *   <li>No feedback; {@link SetpointStage.Plant#atSetpoint()} always returns true.</li>
     * </ul>
     */
    public static SetpointStage.Plant servoPositionPair(ServoOutput a,
                                                        ServoOutput b) {
        if (a == null || b == null) {
            throw new IllegalArgumentException("Both servos are required");
        }
        return new ServoPositionPairPlant(a, b);
    }

    /**
     * Convenience: paired servo position plant from two servo names.
     */
    public static SetpointStage.Plant servoPositionPair(HardwareMap hw,
                                                        String nameA,
                                                        boolean invertA,
                                                        String nameB,
                                                        boolean invertB) {
        ServoOutput a = FtcHardware.servo(hw, nameA, invertA);
        ServoOutput b = FtcHardware.servo(hw, nameB, invertB);
        return servoPositionPair(a, b);
    }

    /**
     * Internal implementation for paired servo position plants.
     */
    private static final class ServoPositionPairPlant implements SetpointStage.Plant {
        private final ServoOutput a;
        private final ServoOutput b;
        private double target = 0.0;

        ServoPositionPairPlant(ServoOutput a, ServoOutput b) {
            this.a = a;
            this.b = b;
        }

        @Override
        public void setTarget(double target) {
            double pos = target;
            this.target = pos;
            a.setPosition(pos);
            b.setPosition(pos);
        }

        @Override
        public void update(double dtSec) {
            // No-op; we assume servos move on their own.
        }

        @Override
        public boolean atSetpoint() {
            // Without feedback, we assume servos are always "ready".
            return true;
        }

        @Override
        public String toString() {
            return "ServoPositionPairPlant{target=" + target + "}";
        }
    }

    // =====================================================================
    // MOTOR POSITION PLANTS (SINGLE)
    // =====================================================================

    /**
     * Position plant using a {@link DcMotorEx} with encoder for angular control.
     *
     * <p>Semantics:
     * <ul>
     *   <li>target is angle in radians at the motor shaft.</li>
     *   <li>Uses {@link DcMotorEx#setTargetPosition(int)} and
     *       {@link DcMotorEx#setMode(DcMotor.RunMode)} to manage RUN_TO_POSITION.</li>
     * </ul>
     */
    public static SetpointStage.Plant motorPosition(DcMotorEx motor,
                                                    double ticksPerRev,
                                                    boolean inverted,
                                                    double toleranceRad) {
        if (motor == null) {
            throw new IllegalArgumentException("motor is required");
        }
        if (ticksPerRev <= 0.0) {
            throw new IllegalArgumentException("ticksPerRev must be > 0");
        }
        if (toleranceRad < 0.0) {
            throw new IllegalArgumentException("toleranceRad must be >= 0");
        }
        return new MotorPositionPlant(motor, ticksPerRev, inverted, toleranceRad);
    }

    /**
     * Convenience: motor position plant from a motor name.
     */
    public static SetpointStage.Plant motorPosition(HardwareMap hw,
                                                    String name,
                                                    double ticksPerRev,
                                                    boolean inverted,
                                                    double toleranceRad) {
        DcMotorEx m = hw.get(DcMotorEx.class, name);
        return motorPosition(m, ticksPerRev, inverted, toleranceRad);
    }

    /**
     * Internal implementation for single-motor position.
     */
    private static final class MotorPositionPlant implements SetpointStage.Plant {
        private final DcMotorEx motor;
        private final double ticksPerRev;
        private final boolean inverted;
        private final double toleranceRad;

        private double targetRad = 0.0;
        private int targetTicks = 0;

        MotorPositionPlant(DcMotorEx motor,
                           double ticksPerRev,
                           boolean inverted,
                           double toleranceRad) {
            this.motor = motor;
            this.ticksPerRev = ticksPerRev;
            this.inverted = inverted;
            this.toleranceRad = toleranceRad;

            motor.setMode(DcMotor.RunMode.STOP_AND_RESET_ENCODER);
            motor.setMode(DcMotor.RunMode.RUN_TO_POSITION);
        }

        @Override
        public void setTarget(double targetRad) {
            this.targetRad = targetRad;
            double rad = inverted ? -targetRad : targetRad;
            double rev = rad / (2.0 * Math.PI);
            targetTicks = (int) Math.round(rev * ticksPerRev);
            motor.setTargetPosition(targetTicks);
            motor.setMode(DcMotor.RunMode.RUN_TO_POSITION);
        }

        @Override
        public void update(double dtSec) {
            // No-op; rely on SDK's RUN_TO_POSITION.
        }

        @Override
        public boolean atSetpoint() {
            int current = motor.getCurrentPosition();
            int errorTicks = targetTicks - current;
            double errorRad = errorTicks * (2.0 * Math.PI / ticksPerRev);
            return Math.abs(errorRad) <= toleranceRad;
        }

        @Override
        public String toString() {
            return "MotorPositionPlant{targetRad=" + targetRad +
                    ", targetTicks=" + targetTicks + "}";
        }
    }

    // =====================================================================
    // MOTOR POSITION PLANTS (PAIR)
    // =====================================================================

    /**
     * Paired motor position plant using two {@link DcMotorEx} with encoders.
     *
     * <p>Semantics:
     * <ul>
     *   <li>target is angle in radians, applied to both motors.</li>
     *   <li>Each motor may be inverted independently.</li>
     *   <li>Both motors are driven with RUN_TO_POSITION mode.</li>
     * </ul>
     */
    public static SetpointStage.Plant motorPositionPair(DcMotorEx a,
                                                        boolean invertA,
                                                        DcMotorEx b,
                                                        boolean invertB,
                                                        double ticksPerRev,
                                                        double toleranceRad) {
        if (a == null || b == null) {
            throw new IllegalArgumentException("Both motors are required");
        }
        if (ticksPerRev <= 0.0) {
            throw new IllegalArgumentException("ticksPerRev must be > 0");
        }
        if (toleranceRad < 0.0) {
            throw new IllegalArgumentException("toleranceRad must be >= 0");
        }
        return new MotorPositionPairPlant(a, invertA, b, invertB, ticksPerRev, toleranceRad);
    }

    /**
     * Convenience: paired motor position plant from two motor names.
     */
    public static SetpointStage.Plant motorPositionPair(HardwareMap hw,
                                                        String nameA,
                                                        boolean invertA,
                                                        String nameB,
                                                        boolean invertB,
                                                        double ticksPerRev,
                                                        double toleranceRad) {
        DcMotorEx a = hw.get(DcMotorEx.class, nameA);
        DcMotorEx b = hw.get(DcMotorEx.class, nameB);
        return motorPositionPair(a, invertA, b, invertB, ticksPerRev, toleranceRad);
    }

    /**
     * Internal implementation for dual-motor position.
     */
    private static final class MotorPositionPairPlant implements SetpointStage.Plant {
        private final DcMotorEx a;
        private final DcMotorEx b;
        private final boolean invertA;
        private final boolean invertB;
        private final double ticksPerRev;
        private final double toleranceRad;

        private double targetRad = 0.0;
        private int targetTicksA = 0;
        private int targetTicksB = 0;

        MotorPositionPairPlant(DcMotorEx a,
                               boolean invertA,
                               DcMotorEx b,
                               boolean invertB,
                               double ticksPerRev,
                               double toleranceRad) {
            this.a = a;
            this.b = b;
            this.invertA = invertA;
            this.invertB = invertB;
            this.ticksPerRev = ticksPerRev;
            this.toleranceRad = toleranceRad;

            a.setMode(DcMotor.RunMode.STOP_AND_RESET_ENCODER);
            b.setMode(DcMotor.RunMode.STOP_AND_RESET_ENCODER);
            a.setMode(DcMotor.RunMode.RUN_TO_POSITION);
            b.setMode(DcMotor.RunMode.RUN_TO_POSITION);
        }

        @Override
        public void setTarget(double targetRad) {
            this.targetRad = targetRad;

            double radA = invertA ? -targetRad : targetRad;
            double radB = invertB ? -targetRad : targetRad;

            double revA = radA / (2.0 * Math.PI);
            double revB = radB / (2.0 * Math.PI);

            targetTicksA = (int) Math.round(revA * ticksPerRev);
            targetTicksB = (int) Math.round(revB * ticksPerRev);

            a.setTargetPosition(targetTicksA);
            b.setTargetPosition(targetTicksB);
        }

        @Override
        public void update(double dtSec) {
            // No-op; rely on SDK's RUN_TO_POSITION.
        }

        @Override
        public boolean atSetpoint() {
            int curA = a.getCurrentPosition();
            int curB = b.getCurrentPosition();

            int errorTicksA = targetTicksA - curA;
            int errorTicksB = targetTicksB - curB;

            double radPerTick = 2.0 * Math.PI / ticksPerRev;

            double errorRadA = errorTicksA * radPerTick;
            double errorRadB = errorTicksB * radPerTick;

            return Math.abs(errorRadA) <= toleranceRad &&
                    Math.abs(errorRadB) <= toleranceRad;
        }

        @Override
        public String toString() {
            return "MotorPositionPairPlant{" +
                    "targetRad=" + targetRad +
                    ", targetTicksA=" + targetTicksA +
                    ", targetTicksB=" + targetTicksB + "}";
        }
    }
}

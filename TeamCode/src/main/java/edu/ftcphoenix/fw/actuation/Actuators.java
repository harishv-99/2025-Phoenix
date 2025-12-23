package edu.ftcphoenix.fw.actuation;

import com.qualcomm.robotcore.hardware.HardwareMap;

import java.util.Objects;

import edu.ftcphoenix.fw.ftc.FtcHardware;
import edu.ftcphoenix.fw.core.hal.Direction;
import edu.ftcphoenix.fw.core.hal.PowerOutput;
import edu.ftcphoenix.fw.core.hal.PositionOutput;
import edu.ftcphoenix.fw.core.hal.VelocityOutput;

/**
 * Beginner-friendly helpers for wiring FTC hardware into {@link Plant} instances.
 *
 * <p>The goal of {@code Actuators} is to let teams create plants in a
 * readable, staged style without having to know about {@link FtcHardware}
 * or the underlying HAL interfaces:</p>
 *
 * <pre>{@code
 * // Shooter: dual-motor velocity plant with a rate limit.
 * Plant shooter = Actuators.plant(hardwareMap)
 *         .motorPair("shooterLeftMotor",  Direction.FORWARD,
 *                    "shooterRightMotor", Direction.REVERSE)
 *         .velocity()                 // uses a default tolerance
 *         .rateLimit(500.0)           // max delta in native units per second
 *         .build();
 *
 * // Transfer: dual CR servo power plant.
 * Plant transfer = Actuators.plant(hardwareMap)
 *         .crServoPair("transferLeftServo",  Direction.FORWARD,
 *                      "transferRightServo", Direction.REVERSE)
 *         .power()
 *         .build();
 *
 * // Pusher: positional servo.
 * Plant pusher = Actuators.plant(hardwareMap)
 *         .servo("pusherServo", Direction.FORWARD)
 *         .position()                 // servo set-and-hold (no feedback)
 *         .build();
 * }</pre>
 *
 * <p>The builder has three conceptual stages:</p>
 *
 * <ol>
 *   <li><b>Pick hardware</b> – {@link HardwareStep}:
 *     <ul>
 *       <li>{@link HardwareStep#motor(String, Direction)}</li>
 *       <li>{@link HardwareStep#motorPair(String, Direction, String, Direction)}</li>
 *       <li>{@link HardwareStep#servo(String, Direction)}</li>
 *       <li>{@link HardwareStep#servoPair(String, Direction, String, Direction)}</li>
 *       <li>{@link HardwareStep#crServo(String, Direction)}</li>
 *       <li>{@link HardwareStep#crServoPair(String, Direction, String, Direction)}</li>
 *     </ul>
 *   </li>
 *   <li><b>Pick control type</b> – {@link ControlStep}:
 *     <ul>
 *       <li>{@link ControlStep#power()} for open-loop power (-1..+1).</li>
 *       <li>{@link ControlStep#velocity()} or
 *           {@link ControlStep#velocity(double)} for closed-loop velocity
 *           with default or explicit tolerance.</li>
 *       <li>{@link ControlStep#position()} or
 *           {@link ControlStep#position(double)} for positional control:
 *           <ul>
 *             <li>DC motors: encoder-backed, feedback-based position plants
 *                 with a tolerance in native units.</li>
 *             <li>Servos: open-loop "set-and-hold" plants without feedback.</li>
 *           </ul>
 *       </li>
 *     </ul>
 *   </li>
 *   <li><b>Modifiers + build</b> – {@link ModifiersStep}:
 *     <ul>
 *       <li>{@link ModifiersStep#rateLimit(double)} to limit how quickly
 *           the target may change.</li>
 *       <li>{@link ModifiersStep#build()} to get the final {@link Plant}.</li>
 *     </ul>
 *   </li>
 * </ol>
 *
 * <p>The intent is that <b>students</b> mostly interact with {@code Actuators}
 * and higher-level APIs like {@link PlantTasks}, while the {@link Plants}
 * class (and the HAL interfaces) remain an internal detail.</p>
 */
public final class Actuators {

    /**
     * Default tolerance for closed-loop motor position plants created via
     * {@link ControlStep#position()} when used with DC motors.
     *
     * <p>This value is in native position units (for REV encoders, ticks).
     * Teams with more advanced needs can use
     * {@link ControlStep#position(double)} to override it.</p>
     */
    private static final double DEFAULT_MOTOR_POSITION_TOLERANCE_NATIVE = 10.0;

    /**
     * Default tolerance for closed-loop motor velocity plants created via
     * {@link ControlStep#velocity()}.
     *
     * <p>This value is in native velocity units (e.g. ticks per second).
     * Teams that need a tighter or looser band can use
     * {@link ControlStep#velocity(double)} to override it.</p>
     */
    private static final double DEFAULT_MOTOR_VELOCITY_TOLERANCE_NATIVE = 100.0;

    private Actuators() {
        // no instances
    }

    /**
     * Entry point for the staged builder.
     *
     * @param hw FTC {@link HardwareMap}
     * @return first step where you choose which hardware you want to control
     */
    public static HardwareStep plant(HardwareMap hw) {
        return new HardwareStep(hw);
    }

    // =====================================================================
    // BUILDER STEPS
    // =====================================================================

    /**
     * Internal enumeration describing which kind of hardware this plant
     * controls. This is used to route to the correct HAL adapters when
     * the control type is chosen.
     */
    private enum HardwareKind {
        NONE,
        MOTOR,
        MOTOR_PAIR,
        SERVO,
        SERVO_PAIR,
        CR_SERVO,
        CR_SERVO_PAIR
    }

    /**
     * Stage 1: choose the hardware being controlled.
     *
     * <p>You call exactly one of the hardware selection methods. Each
     * method returns a {@link ControlStep}, where you choose the control
     * type (power / velocity / position).</p>
     */
    public static final class HardwareStep {

        private final HardwareMap hw;

        private HardwareKind hardwareKind = HardwareKind.NONE;

        private String nameA;
        private String nameB;
        private Direction dirA;
        private Direction dirB;

        private HardwareStep(HardwareMap hw) {
            if (hw == null) {
                throw new IllegalArgumentException("HardwareMap is required");
            }
            this.hw = hw;
        }

        private void ensureUnset() {
            if (hardwareKind != HardwareKind.NONE) {
                throw new IllegalStateException(
                        "Hardware already selected for this builder: " + describeHardware());
            }
        }

        /**
         * Use a single DC motor as the underlying actuator.
         *
         * @param name      motor name in the FTC Robot Configuration
         * @param direction logical direction for the motor
         * @return {@link ControlStep} to choose control type
         */
        public ControlStep motor(String name, Direction direction) {
            ensureUnset();
            this.hardwareKind = HardwareKind.MOTOR;
            this.nameA = Objects.requireNonNull(name, "name");
            this.dirA = Objects.requireNonNull(direction, "direction");
            return new ControlStep(hw, hardwareKind, nameA, dirA, null, Direction.FORWARD);
        }

        /**
         * Use a pair of DC motors as a single logical actuator.
         *
         * <p>Both motors will receive the same command.</p>
         *
         * @param nameA first motor name
         * @param dirA  direction for first motor
         * @param nameB second motor name
         * @param dirB  direction for second motor
         * @return {@link ControlStep} to choose control type
         */
        public ControlStep motorPair(String nameA,
                                     Direction dirA,
                                     String nameB,
                                     Direction dirB) {
            ensureUnset();
            this.hardwareKind = HardwareKind.MOTOR_PAIR;
            this.nameA = Objects.requireNonNull(nameA, "nameA");
            this.nameB = Objects.requireNonNull(nameB, "nameB");
            this.dirA = Objects.requireNonNull(dirA, "dirA");
            this.dirB = Objects.requireNonNull(dirB, "dirB");
            return new ControlStep(hw, hardwareKind, nameA, dirA, nameB, dirB);
        }

        /**
         * Use a single positional servo as the underlying actuator.
         *
         * <p>This selection supports {@link ControlStep#position()} and
         * {@link ControlStep#position(double)}, but will throw if you attempt
         * to use {@link ControlStep#power()} or
         * {@link ControlStep#velocity()} / {@link ControlStep#velocity(double)}.</p>
         *
         * @param name      servo name in the FTC Robot Configuration
         * @param direction logical direction for the actuator
         * @return {@link ControlStep} to choose control type
         */
        public ControlStep servo(String name, Direction direction) {
            ensureUnset();
            this.hardwareKind = HardwareKind.SERVO;
            this.nameA = Objects.requireNonNull(name, "name");
            this.dirA = Objects.requireNonNull(direction, "direction");
            return new ControlStep(hw, hardwareKind, nameA, dirA, null, Direction.FORWARD);
        }

        /**
         * Use a pair of positional servos as a single logical actuator.
         *
         * <p>Both servos will receive the same position command.</p>
         *
         * <p>This selection supports {@link ControlStep#position()} and
         * {@link ControlStep#position(double)}, but will throw if you attempt
         * to use {@link ControlStep#power()} or
         * {@link ControlStep#velocity()} / {@link ControlStep#velocity(double)}.</p>
         *
         * @param nameA first servo name
         * @param dirA  direction for first servo
         * @param nameB second servo name
         * @param dirB  direction for second servo
         * @return {@link ControlStep} to choose control type
         */
        public ControlStep servoPair(String nameA,
                                     Direction dirA,
                                     String nameB,
                                     Direction dirB) {
            ensureUnset();
            this.hardwareKind = HardwareKind.SERVO_PAIR;
            this.nameA = Objects.requireNonNull(nameA, "nameA");
            this.nameB = Objects.requireNonNull(nameB, "nameB");
            this.dirA = Objects.requireNonNull(dirA, "dirA");
            this.dirB = Objects.requireNonNull(dirB, "dirB");
            return new ControlStep(hw, hardwareKind, nameA, dirA, nameB, dirB);
        }

        /**
         * Use a single continuous-rotation (CR) servo as the underlying actuator.
         *
         * <p>This selection supports {@link ControlStep#power()}, but will
         * throw if you attempt to use {@link ControlStep#velocity()} /
         * {@link ControlStep#velocity(double)} or
         * {@link ControlStep#position()} / {@link ControlStep#position(double)}.</p>
         *
         * @param name      CR servo name in the FTC Robot Configuration
         * @param direction logical direction for the actuator
         * @return {@link ControlStep} to choose control type
         */
        public ControlStep crServo(String name, Direction direction) {
            ensureUnset();
            this.hardwareKind = HardwareKind.CR_SERVO;
            this.nameA = Objects.requireNonNull(name, "name");
            this.dirA = Objects.requireNonNull(direction, "direction");
            return new ControlStep(hw, hardwareKind, nameA, dirA, null, Direction.FORWARD);
        }

        /**
         * Use a pair of continuous-rotation (CR) servos as a single logical actuator.
         *
         * <p>Both CR servos will receive the same power command.</p>
         *
         * <p>This selection supports {@link ControlStep#power()}, but will
         * throw if you attempt to use {@link ControlStep#velocity()} /
         * {@link ControlStep#velocity(double)} or
         * {@link ControlStep#position()} / {@link ControlStep#position(double)}.</p>
         *
         * @param nameA first CR servo name
         * @param dirA  direction for first CR servo
         * @param nameB second CR servo name
         * @param dirB  direction for second CR servo
         * @return {@link ControlStep} to choose control type
         */
        public ControlStep crServoPair(String nameA,
                                       Direction dirA,
                                       String nameB,
                                       Direction dirB) {
            ensureUnset();
            this.hardwareKind = HardwareKind.CR_SERVO_PAIR;
            this.nameA = Objects.requireNonNull(nameA, "nameA");
            this.nameB = Objects.requireNonNull(nameB, "nameB");
            this.dirA = Objects.requireNonNull(dirA, "dirA");
            this.dirB = Objects.requireNonNull(dirB, "dirB");
            return new ControlStep(hw, hardwareKind, nameA, dirA, nameB, dirB);
        }

        private String describeHardware() {
            switch (hardwareKind) {
                case MOTOR:
                    return "motor('" + nameA + "')";
                case MOTOR_PAIR:
                    return "motorPair('" + nameA + "', '" + nameB + "')";
                case SERVO:
                    return "servo('" + nameA + "')";
                case SERVO_PAIR:
                    return "servoPair('" + nameA + "', '" + nameB + "')";
                case CR_SERVO:
                    return "crServo('" + nameA + "')";
                case CR_SERVO_PAIR:
                    return "crServoPair('" + nameA + "', '" + nameB + "')";
                case NONE:
                default:
                    return "none";
            }
        }
    }

    /**
     * Stage 2: choose the control type (power / velocity / position)
     * for the selected hardware.
     *
     * <p>This stage combines the hardware choice from {@link HardwareStep}
     * with a control mode, and constructs the underlying {@link Plant}
     * using {@link Plants} helpers.</p>
     */
    public static final class ControlStep {

        private final HardwareMap hw;
        private final HardwareKind kind;
        private final String nameA;
        private final String nameB;
        private final Direction dirA;
        private final Direction dirB;

        private ControlStep(HardwareMap hw,
                            HardwareKind kind,
                            String nameA,
                            Direction dirA,
                            String nameB,
                            Direction dirB) {
            this.hw = Objects.requireNonNull(hw, "hw");
            this.kind = Objects.requireNonNull(kind, "kind");
            this.nameA = nameA;
            this.nameB = nameB;
            this.dirA = Objects.requireNonNull(dirA, "dirA");
            this.dirB = Objects.requireNonNull(dirB, "dirB");
        }

        /**
         * Choose open-loop power control for the selected hardware.
         *
         * <p>Valid hardware kinds:</p>
         *
         * <ul>
         *   <li>{@link HardwareKind#MOTOR}</li>
         *   <li>{@link HardwareKind#MOTOR_PAIR}</li>
         *   <li>{@link HardwareKind#CR_SERVO}</li>
         *   <li>{@link HardwareKind#CR_SERVO_PAIR}</li>
         * </ul>
         *
         * @return {@link ModifiersStep} to apply modifiers and build
         */
        public ModifiersStep power() {
            Plant plant;
            switch (kind) {
                case MOTOR: {
                    PowerOutput out = FtcHardware.motorPower(hw, nameA, dirA);
                    plant = Plants.power(out);
                    break;
                }
                case MOTOR_PAIR: {
                    PowerOutput outA = FtcHardware.motorPower(hw, nameA, dirA);
                    PowerOutput outB = FtcHardware.motorPower(hw, nameB, dirB);
                    plant = Plants.powerPair(outA, outB);
                    break;
                }
                case CR_SERVO: {
                    PowerOutput out = FtcHardware.crServoPower(hw, nameA, dirA);
                    plant = Plants.power(out);
                    break;
                }
                case CR_SERVO_PAIR: {
                    PowerOutput outA = FtcHardware.crServoPower(hw, nameA, dirA);
                    PowerOutput outB = FtcHardware.crServoPower(hw, nameB, dirB);
                    plant = Plants.powerPair(outA, outB);
                    break;
                }
                case SERVO:
                case SERVO_PAIR:
                default:
                    throw new IllegalStateException(
                            "power() is only valid for motor / motorPair / crServo / crServoPair");
            }
            return new ModifiersStep(plant);
        }

        // -----------------------------------------------------------------
        // VELOCITY CONTROL
        // -----------------------------------------------------------------

        /**
         * Choose closed-loop velocity control with a default tolerance.
         *
         * <p>This is equivalent to calling
         * {@link #velocity(double)} with
         * {@link #DEFAULT_MOTOR_VELOCITY_TOLERANCE_NATIVE}.</p>
         *
         * @return {@link ModifiersStep} to apply modifiers and build
         */
        public ModifiersStep velocity() {
            return velocity(DEFAULT_MOTOR_VELOCITY_TOLERANCE_NATIVE);
        }

        /**
         * Choose closed-loop velocity control for the selected hardware.
         *
         * <p>Valid hardware kinds:</p>
         *
         * <ul>
         *   <li>{@link HardwareKind#MOTOR}</li>
         *   <li>{@link HardwareKind#MOTOR_PAIR}</li>
         * </ul>
         *
         * @param toleranceNative acceptable error band around the target, in native units
         * @return {@link ModifiersStep} to apply modifiers and build
         */
        public ModifiersStep velocity(double toleranceNative) {
            Plant plant;
            switch (kind) {
                case MOTOR: {
                    VelocityOutput out = FtcHardware.motorVelocity(hw, nameA, dirA);
                    plant = Plants.velocity(out, toleranceNative);
                    break;
                }
                case MOTOR_PAIR: {
                    VelocityOutput outA = FtcHardware.motorVelocity(hw, nameA, dirA);
                    VelocityOutput outB = FtcHardware.motorVelocity(hw, nameB, dirB);
                    plant = Plants.velocityPair(outA, outB, toleranceNative);
                    break;
                }
                case SERVO:
                case SERVO_PAIR:
                case CR_SERVO:
                case CR_SERVO_PAIR:
                default:
                    throw new IllegalStateException(
                            "velocity() is only valid for motor / motorPair");
            }
            return new ModifiersStep(plant);
        }

        // -----------------------------------------------------------------
        // POSITION CONTROL
        // -----------------------------------------------------------------

        /**
         * Choose positional control with a default motor tolerance.
         *
         * <p>For DC motors, this is equivalent to calling
         * {@link #position(double)} with
         * {@link #DEFAULT_MOTOR_POSITION_TOLERANCE_NATIVE}.</p>
         *
         * <p>For standard servos, the tolerance is not used because there is
         * no feedback; the plant is open-loop "set-and-hold" and always
         * considers itself at setpoint.</p>
         *
         * @return {@link ModifiersStep} to apply modifiers and build
         */
        public ModifiersStep position() {
            return position(DEFAULT_MOTOR_POSITION_TOLERANCE_NATIVE);
        }

        /**
         * Choose positional control for the selected hardware.
         *
         * <p>For DC motors, this creates a <b>feedback-based</b> position plant
         * (using encoders via {@link FtcHardware#motorPosition}) with the given
         * tolerance in native units, so that {@link Plant#atSetpoint()} and
         * {@link Plant#hasFeedback()} behave as expected for "move to" style
         * tasks.</p>
         *
         * <p>For standard servos, this creates an open-loop "set-and-hold"
         * plant that simply commands the target (typically 0..1) and treats
         * itself as always at setpoint; servos generally do not expose a
         * measured position, so {@code toleranceNative} is ignored and
         * {@link Plant#hasFeedback()} will be {@code false}.</p>
         *
         * <p>Valid hardware kinds:</p>
         *
         * <ul>
         *   <li>{@link HardwareKind#MOTOR}</li>
         *   <li>{@link HardwareKind#MOTOR_PAIR}</li>
         *   <li>{@link HardwareKind#SERVO}</li>
         *   <li>{@link HardwareKind#SERVO_PAIR}</li>
         * </ul>
         *
         * @param toleranceNative acceptable error band (native units) for motors
         * @return {@link ModifiersStep} to apply modifiers and build
         */
        public ModifiersStep position(double toleranceNative) {
            Plant plant;
            switch (kind) {
                case MOTOR: {
                    PositionOutput out = FtcHardware.motorPosition(hw, nameA, dirA);
                    plant = Plants.motorPosition(out, toleranceNative);
                    break;
                }
                case MOTOR_PAIR: {
                    PositionOutput outA = FtcHardware.motorPosition(hw, nameA, dirA);
                    PositionOutput outB = FtcHardware.motorPosition(hw, nameB, dirB);
                    plant = Plants.motorPositionPair(outA, outB, toleranceNative);
                    break;
                }
                case SERVO: {
                    PositionOutput out = FtcHardware.servoPosition(hw, nameA, dirA);
                    plant = Plants.servoPosition(out);
                    break;
                }
                case SERVO_PAIR: {
                    PositionOutput outA = FtcHardware.servoPosition(hw, nameA, dirA);
                    PositionOutput outB = FtcHardware.servoPosition(hw, nameB, dirB);
                    plant = Plants.servoPositionPair(outA, outB);
                    break;
                }
                case CR_SERVO:
                case CR_SERVO_PAIR:
                default:
                    throw new IllegalStateException(
                            "position() is only valid for motor / motorPair / servo / servoPair");
            }
            return new ModifiersStep(plant);
        }
    }

    /**
     * Stage 3: apply optional modifiers (such as rate limiting) and build
     * the final {@link Plant}.
     *
     * <p>Every {@code ModifiersStep} starts with a base plant and wraps it
     * in zero or more decorators.</p>
     */
    public static final class ModifiersStep {

        private Plant plant;

        private ModifiersStep(Plant basePlant) {
            if (basePlant == null) {
                throw new IllegalArgumentException("basePlant is required");
            }
            this.plant = basePlant;
        }

        /**
         * Wrap the current plant in a {@link RateLimitedPlant}.
         *
         * <p>This limits how quickly {@link Plant#setTarget(double)} can
         * change the underlying command value.</p>
         *
         * @param maxDeltaPerSec maximum allowed change in the target value
         *                       per second, in the same native units as the
         *                       plant’s target
         * @return this {@link ModifiersStep} for chaining
         */
        public ModifiersStep rateLimit(double maxDeltaPerSec) {
            if (maxDeltaPerSec < 0.0) {
                throw new IllegalArgumentException("maxDeltaPerSec must be non-negative");
            }
            if (maxDeltaPerSec > 0.0) {
                this.plant = new RateLimitedPlant(plant, maxDeltaPerSec);
            }
            return this;
        }

        /**
         * Finish the builder and return the configured {@link Plant}.
         *
         * @return the final plant instance
         */
        public Plant build() {
            return plant;
        }
    }
}

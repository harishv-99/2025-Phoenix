package edu.ftcphoenix.fw.drive;

import com.qualcomm.robotcore.hardware.HardwareMap;

import edu.ftcphoenix.fw.adapters.ftc.FtcHardware;
import edu.ftcphoenix.fw.hal.MotorOutput;

/**
 * Convenience factories for constructing drivebases from a {@link HardwareMap}.
 *
 * <p>Design goals:
 * <ul>
 *   <li>Hide {@link FtcHardware} and {@link MotorOutput} from robot-centric code.</li>
 *   <li>Make wiring a mecanum drive simple, explicit, and readable.</li>
 *   <li>Mirror the style of {@code FtcPlants.*(hardwareMap, ...)} for mechanisms.</li>
 * </ul>
 *
 * <p>Typical usage:</p>
 * <pre>{@code
 * // Simple: default names "fl","fr","bl","br" and invert right side.
 * MecanumDrivebase drive = Drives.mecanum(hardwareMap)
 *     .invertRightSide()
 *     .build();
 *
 * // Custom names and inversion:
 * MecanumDrivebase drive = Drives.mecanum(hardwareMap)
 *     .frontLeft("frontLeft")
 *     .frontRight("frontRight")
 *     .backLeft("backLeft")
 *     .backRight("backRight")
 *     .invertFrontRight()   // only this motor is inverted
 *     .build();
 * }</pre>
 */
public final class Drives {

    private Drives() {
        // utility class
    }

    // =====================================================================
    // Mecanum builder
    // =====================================================================

    /**
     * Entry point for building a 4-wheel mecanum drivebase from {@link HardwareMap}.
     *
     * <p>By default, the builder uses motor names:
     * <ul>
     *   <li>"fl" – front-left</li>
     *   <li>"fr" – front-right</li>
     *   <li>"bl" – back-left</li>
     *   <li>"br" – back-right</li>
     * </ul>
     * and assumes no inversion for any motor.</p>
     *
     * <p>You can override names and inversion with the builder methods.</p>
     */
    public static MecanumBuilder mecanum(HardwareMap hw) {
        if (hw == null) {
            throw new IllegalArgumentException("hardwareMap is required");
        }
        return new MecanumBuilder(hw);
    }

    /**
     * Builder for a 4-wheel mecanum drivebase.
     *
     * <p>Primary methods use clear names like {@link #frontLeft(String)} and
     * {@link #invertFrontLeft()}. Short aliases ({@link #fl(String)},
     * {@link #invertFl()}) are provided for teams that prefer brevity, but the
     * long names should be favored for readability.</p>
     */
    public static final class MecanumBuilder {
        private final HardwareMap hw;

        private String frontLeftName = "fl";
        private String frontRightName = "fr";
        private String backLeftName = "bl";
        private String backRightName = "br";

        private boolean invertFrontLeft = false;
        private boolean invertFrontRight = false;
        private boolean invertBackLeft = false;
        private boolean invertBackRight = false;

        private MecanumConfig config = null;

        private MecanumBuilder(HardwareMap hw) {
            this.hw = hw;
        }

        // ----- Naming (primary, descriptive) -------------------------------

        /**
         * Set the front-left motor name.
         */
        public MecanumBuilder frontLeft(String name) {
            if (name != null && !name.isEmpty()) {
                this.frontLeftName = name;
            }
            return this;
        }

        /**
         * Set the front-right motor name.
         */
        public MecanumBuilder frontRight(String name) {
            if (name != null && !name.isEmpty()) {
                this.frontRightName = name;
            }
            return this;
        }

        /**
         * Set the back-left motor name.
         */
        public MecanumBuilder backLeft(String name) {
            if (name != null && !name.isEmpty()) {
                this.backLeftName = name;
            }
            return this;
        }

        /**
         * Set the back-right motor name.
         */
        public MecanumBuilder backRight(String name) {
            if (name != null && !name.isEmpty()) {
                this.backRightName = name;
            }
            return this;
        }

        /**
         * Set all four motor names at once.
         */
        public MecanumBuilder names(String frontLeft,
                                    String frontRight,
                                    String backLeft,
                                    String backRight) {
            return frontLeft(frontLeft)
                    .frontRight(frontRight)
                    .backLeft(backLeft)
                    .backRight(backRight);
        }

        // ----- Naming aliases (short forms) --------------------------------

        /**
         * Alias for {@link #frontLeft(String)}.
         */
        public MecanumBuilder fl(String name) {
            return frontLeft(name);
        }

        /**
         * Alias for {@link #frontRight(String)}.
         */
        public MecanumBuilder fr(String name) {
            return frontRight(name);
        }

        /**
         * Alias for {@link #backLeft(String)}.
         */
        public MecanumBuilder bl(String name) {
            return backLeft(name);
        }

        /**
         * Alias for {@link #backRight(String)}.
         */
        public MecanumBuilder br(String name) {
            return backRight(name);
        }

        // ----- Inversion (primary, descriptive) ----------------------------

        /**
         * Invert the front-left motor.
         */
        public MecanumBuilder invertFrontLeft() {
            this.invertFrontLeft = true;
            return this;
        }

        /**
         * Invert the front-right motor.
         */
        public MecanumBuilder invertFrontRight() {
            this.invertFrontRight = true;
            return this;
        }

        /**
         * Invert the back-left motor.
         */
        public MecanumBuilder invertBackLeft() {
            this.invertBackLeft = true;
            return this;
        }

        /**
         * Invert the back-right motor.
         */
        public MecanumBuilder invertBackRight() {
            this.invertBackRight = true;
            return this;
        }

        /**
         * Convenience: invert both right-side motors (front-right and back-right).
         *
         * <p>This is purely sugar on top of {@link #invertFrontRight()} and
         * {@link #invertBackRight()}, and is not required for custom patterns.</p>
         */
        public MecanumBuilder invertRightSide() {
            return invertFrontRight().invertBackRight();
        }

        /**
         * Convenience: invert both left-side motors (front-left and back-left).
         */
        public MecanumBuilder invertLeftSide() {
            return invertFrontLeft().invertBackLeft();
        }

        // ----- Inversion aliases (short forms) -----------------------------

        /**
         * Alias for {@link #invertFrontLeft()}.
         */
        public MecanumBuilder invertFl() {
            return invertFrontLeft();
        }

        /**
         * Alias for {@link #invertFrontRight()}.
         */
        public MecanumBuilder invertFr() {
            return invertFrontRight();
        }

        /**
         * Alias for {@link #invertBackLeft()}.
         */
        public MecanumBuilder invertBl() {
            return invertBackLeft();
        }

        /**
         * Alias for {@link #invertBackRight()}.
         */
        public MecanumBuilder invertBr() {
            return invertBackRight();
        }

        // ----- Config -----------------------------------------------------

        /**
         * Set a custom {@link MecanumConfig}.
         *
         * <p>If not set, {@link MecanumConfig#defaults()} is used.</p>
         */
        public MecanumBuilder config(MecanumConfig cfg) {
            this.config = cfg;
            return this;
        }

        // ----- Build ------------------------------------------------------

        /**
         * Build the {@link MecanumDrivebase}.
         */
        public MecanumDrivebase build() {
            MotorOutput fl = FtcHardware.motor(hw, frontLeftName, invertFrontLeft);
            MotorOutput fr = FtcHardware.motor(hw, frontRightName, invertFrontRight);
            MotorOutput bl = FtcHardware.motor(hw, backLeftName, invertBackLeft);
            MotorOutput br = FtcHardware.motor(hw, backRightName, invertBackRight);

            MecanumConfig actualCfg = (config != null) ? config : MecanumConfig.defaults();
            return new MecanumDrivebase(fl, fr, bl, br, actualCfg);
        }
    }
}

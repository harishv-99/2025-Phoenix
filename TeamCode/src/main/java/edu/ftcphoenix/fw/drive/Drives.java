package edu.ftcphoenix.fw.drive;

import com.qualcomm.robotcore.hardware.HardwareMap;

import edu.ftcphoenix.fw.adapters.ftc.FtcHardware;
import edu.ftcphoenix.fw.hal.MotorOutput;

/**
 * Convenience factories for constructing drivebases from a {@link HardwareMap}.
 *
 * <p>Design goals:</p>
 * <ul>
 *   <li>Hide {@link FtcHardware} and {@link MotorOutput} from robot-centric code.</li>
 *   <li>Make wiring a mecanum drive simple, explicit, and readable.</li>
 *   <li>Mirror the style of FtcPlants for mechanisms: config objects plus
 *       small static helpers.</li>
 * </ul>
 *
 * <p>
 * The Phoenix philosophy is:
 * </p>
 *
 * <ul>
 *   <li>Drive behavior (scaling, smoothing) is configured via
 *       {@link MecanumConfig}.</li>
 *   <li>Hardware wiring (motor names and inversions) is configured here via
 *       simple static factories, with sane defaults.</li>
 *   <li>Robot-specific TeleOps should usually call a <em>single</em> factory
 *       method to get a fully-wired {@link MecanumDrivebase}.</li>
 * </ul>
 *
 * <h2>Standard inversion pattern</h2>
 *
 * <p>
 * Most FTC mecanum drivetrains follow this physical convention:
 * </p>
 *
 * <ul>
 *   <li>Left side motors are mounted so that “+power” drives forward.</li>
 *   <li>Right side motors are mounted in the mirrored orientation, so they
 *       must be <em>inverted in software</em> to make “+power” drive forward.</li>
 * </ul>
 *
 * <p>
 * Phoenix encodes this as the <b>standard inversion pattern</b>:
 * </p>
 *
 * <ul>
 *   <li>front-left:  not inverted</li>
 *   <li>front-right: inverted</li>
 *   <li>back-left:   not inverted</li>
 *   <li>back-right:  inverted</li>
 * </ul>
 *
 * <p>
 * The simple {@code mecanum(...)} overloads use this pattern by default. Only
 * unusual robots (e.g., mixed mounting, chains crossing sides) should need
 * the “full control” overload that exposes per-motor inversion flags.
 * </p>
 */
public final class Drives {

    /**
     * Default hardware names used by {@link #mecanum(HardwareMap)} and
     * {@link #mecanum(HardwareMap, MecanumConfig)}.
     */
    public static final String DEFAULT_FL = "fl";
    public static final String DEFAULT_FR = "fr";
    public static final String DEFAULT_BL = "bl";
    public static final String DEFAULT_BR = "br";

    private Drives() {
        // Utility class; no instances.
    }

    // ------------------------------------------------------------------------
    // Mecanum drive factories (standard inversion pattern)
    // ------------------------------------------------------------------------

    /**
     * Construct a {@link MecanumDrivebase} with default names and default config.
     *
     * <p>
     * This is the simplest entry point. It assumes:
     * </p>
     *
     * <ul>
     *   <li>Motor names:
     *     <ul>
     *       <li>{@link #DEFAULT_FL} – front-left</li>
     *       <li>{@link #DEFAULT_FR} – front-right</li>
     *       <li>{@link #DEFAULT_BL} – back-left</li>
     *       <li>{@link #DEFAULT_BR} – back-right</li>
     *     </ul>
     *   </li>
     *   <li>Standard inversion pattern:
     *     <ul>
     *       <li>front-left:  not inverted</li>
     *       <li>front-right: inverted</li>
     *       <li>back-left:   not inverted</li>
     *       <li>back-right:  inverted</li>
     *     </ul>
     *   </li>
     *   <li>Drive behavior tuned by {@link MecanumConfig#defaults()}.</li>
     * </ul>
     *
     * @param hw FTC {@link HardwareMap} from your OpMode
     * @return a new {@link MecanumDrivebase} with default wiring and config
     */
    public static MecanumDrivebase mecanum(HardwareMap hw) {
        return mecanum(hw, MecanumConfig.defaults());
    }

    /**
     * Construct a {@link MecanumDrivebase} with default names and a custom config.
     *
     * <p>
     * This is the recommended factory for most teams:
     * </p>
     *
     * <pre>{@code
     * MecanumConfig cfg = MecanumConfig.defaults();
     * cfg.maxLateralRatePerSec = 4.0;
     * MecanumDrivebase drive = Drives.mecanum(hardwareMap, cfg);
     * }</pre>
     *
     * @param hw  FTC {@link HardwareMap} from your OpMode (must not be null)
     * @param cfg {@link MecanumConfig} controlling scaling & smoothing
     *            (if null, {@link MecanumConfig#defaults()} is used)
     * @return a new {@link MecanumDrivebase} wired using the default motor names
     * and the standard inversion pattern
     */
    public static MecanumDrivebase mecanum(HardwareMap hw, MecanumConfig cfg) {
        if (hw == null) {
            throw new IllegalArgumentException("hardwareMap is required");
        }

        MecanumConfig actualCfg = (cfg != null) ? cfg : MecanumConfig.defaults();

        // Standard inversion pattern: left side normal, right side inverted.
        MotorOutput fl = FtcHardware.motor(hw, DEFAULT_FL, false);
        MotorOutput fr = FtcHardware.motor(hw, DEFAULT_FR, true);
        MotorOutput bl = FtcHardware.motor(hw, DEFAULT_BL, false);
        MotorOutput br = FtcHardware.motor(hw, DEFAULT_BR, true);

        return new MecanumDrivebase(fl, fr, bl, br, actualCfg);
    }

    /**
     * Construct a {@link MecanumDrivebase} with custom motor names, default config,
     * and the standard inversion pattern.
     *
     * <p>
     * This overload is useful when your motor names differ from the Phoenix
     * defaults but you are otherwise happy with the default drive behavior and
     * the standard inversion (left normal, right inverted).
     * </p>
     *
     * @param hw         FTC {@link HardwareMap} from your OpMode (must not be null)
     * @param frontLeft  hardware name for the front-left motor
     * @param frontRight hardware name for the front-right motor
     * @param backLeft   hardware name for the back-left motor
     * @param backRight  hardware name for the back-right motor
     * @return a new {@link MecanumDrivebase} with the given motor names,
     * standard inversion pattern, and default config
     */
    public static MecanumDrivebase mecanum(HardwareMap hw,
                                           String frontLeft,
                                           String frontRight,
                                           String backLeft,
                                           String backRight) {
        return mecanum(hw, frontLeft, frontRight, backLeft, backRight,
                MecanumConfig.defaults());
    }

    /**
     * Construct a {@link MecanumDrivebase} with custom motor names, custom config,
     * and the standard inversion pattern.
     *
     * <p>
     * This overload covers the common case of:
     * </p>
     *
     * <ul>
     *   <li>non-standard hardware names for the four drive motors, and</li>
     *   <li>a custom {@link MecanumConfig} for scaling/smoothing, and</li>
     *   <li>left side normal, right side inverted.</li>
     * </ul>
     *
     * <p>
     * Example:
     * </p>
     *
     * <pre>{@code
     * MecanumConfig cfg = MecanumConfig.defaults();
     * cfg.maxLateralRatePerSec = 4.0;
     *
     * MecanumDrivebase drive = Drives.mecanum(
     *         hardwareMap,
     *         "frontLeftMotor",
     *         "frontRightMotor",
     *         "backLeftMotor",
     *         "backRightMotor",
     *         cfg
     * );
     * }</pre>
     *
     * @param hw         FTC {@link HardwareMap} from your OpMode (must not be null)
     * @param frontLeft  hardware name for the front-left motor
     * @param frontRight hardware name for the front-right motor
     * @param backLeft   hardware name for the back-left motor
     * @param backRight  hardware name for the back-right motor
     * @param cfg        {@link MecanumConfig} controlling scaling & smoothing
     *                   (if null, {@link MecanumConfig#defaults()} is used)
     * @return a new {@link MecanumDrivebase} with custom names and standard inversion
     */
    public static MecanumDrivebase mecanum(HardwareMap hw,
                                           String frontLeft,
                                           String frontRight,
                                           String backLeft,
                                           String backRight,
                                           MecanumConfig cfg) {
        if (hw == null) {
            throw new IllegalArgumentException("hardwareMap is required");
        }
        if (frontLeft == null || frontRight == null || backLeft == null || backRight == null) {
            throw new IllegalArgumentException("All four motor names are required");
        }

        MecanumConfig actualCfg = (cfg != null) ? cfg : MecanumConfig.defaults();

        // Standard inversion pattern: left side normal, right side inverted.
        MotorOutput fl = FtcHardware.motor(hw, frontLeft, false);
        MotorOutput fr = FtcHardware.motor(hw, frontRight, true);
        MotorOutput bl = FtcHardware.motor(hw, backLeft, false);
        MotorOutput br = FtcHardware.motor(hw, backRight, true);

        return new MecanumDrivebase(fl, fr, bl, br, actualCfg);
    }

    // ------------------------------------------------------------------------
    // Mecanum drive (full control) – rare cases only
    // ------------------------------------------------------------------------

    /**
     * Construct a {@link MecanumDrivebase} with full control over names and inversion.
     *
     * <p>
     * Most teams should not need this overload. Prefer the simpler factories
     * above, which assume the standard inversion pattern (left normal, right
     * inverted). Use this only if:
     * </p>
     *
     * <ul>
     *   <li>your motors are mounted in a non-standard way, or</li>
     *   <li>you deliberately want a different inversion pattern.</li>
     * </ul>
     *
     * @param hw               FTC {@link HardwareMap} from your OpMode (must not be null)
     * @param frontLeft        hardware name for the front-left motor
     * @param frontRight       hardware name for the front-right motor
     * @param backLeft         hardware name for the back-left motor
     * @param backRight        hardware name for the back-right motor
     * @param invertFrontLeft  whether to invert the front-left motor
     * @param invertFrontRight whether to invert the front-right motor
     * @param invertBackLeft   whether to invert the back-left motor
     * @param invertBackRight  whether to invert the back-right motor
     * @param cfg              {@link MecanumConfig} controlling scaling & smoothing
     *                         (if null, {@link MecanumConfig#defaults()} is used)
     * @return a fully configured {@link MecanumDrivebase}
     */
    public static MecanumDrivebase mecanum(HardwareMap hw,
                                           String frontLeft,
                                           String frontRight,
                                           String backLeft,
                                           String backRight,
                                           boolean invertFrontLeft,
                                           boolean invertFrontRight,
                                           boolean invertBackLeft,
                                           boolean invertBackRight,
                                           MecanumConfig cfg) {
        if (hw == null) {
            throw new IllegalArgumentException("hardwareMap is required");
        }
        if (frontLeft == null || frontRight == null || backLeft == null || backRight == null) {
            throw new IllegalArgumentException("All four motor names are required");
        }

        MecanumConfig actualCfg = (cfg != null) ? cfg : MecanumConfig.defaults();

        MotorOutput fl = FtcHardware.motor(hw, frontLeft, invertFrontLeft);
        MotorOutput fr = FtcHardware.motor(hw, frontRight, invertFrontRight);
        MotorOutput bl = FtcHardware.motor(hw, backLeft, invertBackLeft);
        MotorOutput br = FtcHardware.motor(hw, backRight, invertBackRight);

        return new MecanumDrivebase(fl, fr, bl, br, actualCfg);
    }
}

package edu.ftcphoenix.fw2.drive.hw.rev;

import com.qualcomm.hardware.rev.RevHubOrientationOnRobot;
import com.qualcomm.robotcore.hardware.DcMotor;
import com.qualcomm.robotcore.hardware.DcMotorEx;
import com.qualcomm.robotcore.hardware.DcMotorSimple;
import com.qualcomm.robotcore.hardware.HardwareMap;
import com.qualcomm.robotcore.hardware.IMU;
import com.qualcomm.robotcore.hardware.VoltageSensor;

import org.firstinspires.ftc.robotcore.external.navigation.AngleUnit;
import org.firstinspires.ftc.robotcore.external.Telemetry;

import edu.ftcphoenix.fw2.drive.hw.DriveIO;
import edu.ftcphoenix.fw2.util.MathUtil;

/**
 * MecanumIO — concrete {@link DriveIO} backed by REV Hub hardware.
 *
 * <p><b>Responsibilities:</b>
 * <ul>
 *   <li>Create/configure motors, optional IMU, and optional voltage sensor (via {@link Builder}).</li>
 *   <li>Provide the final “sink guard” in {@link #setWheelPowers(double, double, double, double)}:
 *       coerce non-finite values and clamp to [-1, +1] before writing to motors.</li>
 *   <li>Expose heading and bus voltage if available.</li>
 * </ul>
 *
 * <p><b>Non-responsibilities:</b>
 * <ul>
 *   <li>Input shaping, mixing, or field-centric; keep these upstream (e.g., DriveGraph + filters).</li>
 * </ul>
 */
public final class MecanumIO implements DriveIO {

    /**
     * Builder for {@link MecanumIO}.
     *
     * <p>Typical usage:
     * <pre>{@code
     * MecanumIO io = new MecanumIO.Builder(hw)
     *     .motors("fl","fr","bl","br")
     *     .directionsPresetStandard() // or .motorDirections(...)
     *     .imu("imu", new RevHubOrientationOnRobot(...)) // optional
     *     .voltageSensor("Control Hub")                  // optional
     *     .zeroPower(DcMotor.ZeroPowerBehavior.BRAKE)
     *     .runMode(DcMotor.RunMode.RUN_WITHOUT_ENCODER)
     *     .build();
     * }</pre>
     */
    public static final class Builder {
        private final HardwareMap hardwareMap;

        private String nameFL, nameFR, nameBL, nameBR;
        private DcMotorSimple.Direction dirFL = DcMotorSimple.Direction.FORWARD;
        private DcMotorSimple.Direction dirFR = DcMotorSimple.Direction.FORWARD;
        private DcMotorSimple.Direction dirBL = DcMotorSimple.Direction.FORWARD;
        private DcMotorSimple.Direction dirBR = DcMotorSimple.Direction.FORWARD;

        private String imuName = null;
        private RevHubOrientationOnRobot imuOrientation =
                new RevHubOrientationOnRobot(
                        RevHubOrientationOnRobot.LogoFacingDirection.UP,
                        RevHubOrientationOnRobot.UsbFacingDirection.BACKWARD);

        private String voltageSensorName = null;

        private DcMotor.ZeroPowerBehavior zeroPower = DcMotor.ZeroPowerBehavior.BRAKE;
        private DcMotor.RunMode runMode = DcMotor.RunMode.RUN_WITHOUT_ENCODER;

        /**
         * Create a builder bound to a {@link HardwareMap}.
         *
         * @param hardwareMap FTC hardware map
         */
        public Builder(HardwareMap hardwareMap) {
            this.hardwareMap = hardwareMap;
        }

        /**
         * Motor names (front-left, front-right, back-left, back-right).
         *
         * @param fl front-left config name
         * @param fr front-right config name
         * @param bl back-left config name
         * @param br back-right config name
         * @return this builder
         */
        public Builder motors(String fl, String fr, String bl, String br) {
            this.nameFL = fl;
            this.nameFR = fr;
            this.nameBL = bl;
            this.nameBR = br;
            return this;
        }

        /**
         * Motor directions in the order (FL, FR, BL, BR).
         *
         * @param fl dir for front-left
         * @param fr dir for front-right
         * @param bl dir for back-left
         * @param br dir for back-right
         * @return this builder
         */
        public Builder motorDirections(DcMotorSimple.Direction fl, DcMotorSimple.Direction fr,
                                       DcMotorSimple.Direction bl, DcMotorSimple.Direction br) {
            this.dirFL = fl;
            this.dirFR = fr;
            this.dirBL = bl;
            this.dirBR = br;
            return this;
        }

        /**
         * Install an IMU with the given name and mounting orientation.
         * <p>If {@code imuName} is {@code null}, the orientation is ignored and no IMU is created.</p>
         *
         * @param imuName     config name, or {@code null} to skip IMU
         * @param orientation hub orientation for correct axes
         * @return this builder
         */
        public Builder imu(String imuName, RevHubOrientationOnRobot orientation) {
            this.imuName = imuName;
            this.imuOrientation = orientation;
            return this;
        }

        /**
         * Attach a voltage sensor by name (e.g., {@code "Control Hub"}). Optional.
         *
         * @param name voltage sensor name
         * @return this builder
         */
        public Builder voltageSensor(String name) {
            this.voltageSensorName = name;
            return this;
        }

        /**
         * Zero-power behavior for all four motors.
         *
         * @param zpb zero power behavior
         * @return this builder
         */
        public Builder zeroPower(DcMotor.ZeroPowerBehavior zpb) {
            this.zeroPower = zpb;
            return this;
        }

        /**
         * Run mode for all four motors.
         *
         * @param mode run mode
         * @return this builder
         */
        public Builder runMode(DcMotor.RunMode mode) {
            this.runMode = mode;
            return this;
        }

        /**
         * Convenience preset for a common convention:
         * <ul>
         *   <li>+axial (forward) moves robot forward,</li>
         *   <li>+lateral (left) moves robot left,</li>
         *   <li>+omega (CCW) rotates robot CCW.</li>
         * </ul>
         * Internally sets: FL=FWD, FR=REV, BL=FWD, BR=REV.
         *
         * @return this builder
         */
        public Builder directionsPresetStandard() {
            return motorDirections(
                    DcMotorSimple.Direction.FORWARD,
                    DcMotorSimple.Direction.REVERSE,
                    DcMotorSimple.Direction.FORWARD,
                    DcMotorSimple.Direction.REVERSE
            );
        }

        /**
         * Create the {@link MecanumIO}.
         *
         * @return configured IO
         * @throws IllegalStateException if motor names were not provided
         */
        public MecanumIO build() {
            if (nameFL == null || nameFR == null || nameBL == null || nameBR == null) {
                throw new IllegalStateException("motors(FL,FR,BL,BR) are required");
            }
            return new MecanumIO(hardwareMap, nameFL, nameFR, nameBL, nameBR,
                    dirFL, dirFR, dirBL, dirBR,
                    imuName, imuOrientation, voltageSensorName,
                    zeroPower, runMode);
        }
    }

    private final DcMotorEx fl, fr, bl, br;
    private final IMU imu;                 // nullable
    private final VoltageSensor vSense;    // nullable

    // Last written wheel powers (after clamp/finite guard) for telemetry/debug.
    private double lastFL, lastFR, lastBL, lastBR;

    private MecanumIO(HardwareMap map,
                      String nameFL, String nameFR, String nameBL, String nameBR,
                      DcMotorSimple.Direction dirFL, DcMotorSimple.Direction dirFR,
                      DcMotorSimple.Direction dirBL, DcMotorSimple.Direction dirBR,
                      String imuName, RevHubOrientationOnRobot imuOrientation,
                      String voltageSensorName,
                      DcMotor.ZeroPowerBehavior zeroPower, DcMotor.RunMode runMode) {

        this.fl = map.get(DcMotorEx.class, nameFL);
        this.fr = map.get(DcMotorEx.class, nameFR);
        this.bl = map.get(DcMotorEx.class, nameBL);
        this.br = map.get(DcMotorEx.class, nameBR);

        fl.setDirection(dirFL);
        fr.setDirection(dirFR);
        bl.setDirection(dirBL);
        br.setDirection(dirBR);

        fl.setZeroPowerBehavior(zeroPower);
        fr.setZeroPowerBehavior(zeroPower);
        bl.setZeroPowerBehavior(zeroPower);
        br.setZeroPowerBehavior(zeroPower);

        fl.setMode(runMode);
        fr.setMode(runMode);
        bl.setMode(runMode);
        br.setMode(runMode);

        if (imuName != null) {
            imu = map.get(IMU.class, imuName);
            IMU.Parameters params = new IMU.Parameters(imuOrientation);
            imu.initialize(params);
        } else {
            imu = null;
        }

        vSense = (voltageSensorName != null) ? map.get(VoltageSensor.class, voltageSensorName) : null;
    }

    /**
     * Set individual wheel powers (FL, FR, BL, BR).
     * <p>
     * This is the final sink-guard:
     * <ul>
     *   <li>Coerces non-finite values (NaN/Inf) to a safe fallback (0.0).</li>
     *   <li>Clamps each value to {@code [-1, +1]}.</li>
     * </ul>
     * Values stored in {@code lastFL/lastFR/lastBL/lastBR} are post-guard and can be used for telemetry.
     *
     * @param flp front-left power
     * @param frp front-right power
     * @param blp back-left power
     * @param brp back-right power
     */
    @Override
    public void setWheelPowers(double flp, double frp, double blp, double brp) {
        final double lo = -1.0, hi = +1.0, fb = 0.0;
        lastFL = MathUtil.clampFinite(flp, lo, hi, fb);
        lastFR = MathUtil.clampFinite(frp, lo, hi, fb);
        lastBL = MathUtil.clampFinite(blp, lo, hi, fb);
        lastBR = MathUtil.clampFinite(brp, lo, hi, fb);

        fl.setPower(lastFL);
        fr.setPower(lastFR);
        bl.setPower(lastBL);
        br.setPower(lastBR);
    }

    /**
     * @return heading in radians (+CCW) if IMU present; {@code Double.NaN} otherwise.
     */
    @Override
    public double getHeadingRad() {
        if (imu == null) return Double.NaN;
        return imu.getRobotYawPitchRollAngles().getYaw(AngleUnit.RADIANS);
    }

    /**
     * @return bus voltage if a sensor was configured; {@code Double.NaN} otherwise.
     */
    @Override
    public double getVoltage() {
        return (vSense != null) ? vSense.getVoltage() : Double.NaN;
    }

    /**
     * Placeholder for total current. SDK exposes per-motor current; aggregate if needed later.
     *
     * @return {@code Double.NaN} (not available)
     */
    @Override
    public double getCurrentAmps() {
        return Double.NaN;
    }

    /**
     * Returns the last wheel powers written after clamping and finite coercion.
     *
     * @return array {@code [fl, fr, bl, br]}
     */
    @Override
    public double[] getLastWheelPowers() {
        return new double[]{lastFL, lastFR, lastBL, lastBR};
    }

    /**
     * Emit a compact telemetry block for quick tuning.
     *
     * <p>Shows heading (rad) if IMU is present, bus voltage if available, and the last
     * wheel powers that were written (post-clamp and finite fixup).</p>
     *
     * @param tel     telemetry sink
     * @param label   prefix label (e.g., "drive")
     * @param verbose if {@code true}, prints wheel powers with 3 decimals; otherwise fewer decimals
     */
    public void addTelemetry(Telemetry tel, String label, boolean verbose) {
        final double hdg = getHeadingRad();
        final double vbus = getVoltage();
        final double[] w = getLastWheelPowers();

        if (!Double.isNaN(hdg)) tel.addData(label + "/hdg(rad)", "%.3f", hdg);
        if (!Double.isNaN(vbus)) tel.addData(label + "/Vbus", "%.1f", vbus);

        if (verbose) {
            tel.addData(label + "/wheels", "FL=%.3f FR=%.3f BL=%.3f BR=%.3f", w[0], w[1], w[2], w[3]);
        } else {
            tel.addData(label + "/w", "FL=%.2f FR=%.2f BL=%.2f BR=%.2f", w[0], w[1], w[2], w[3]);
        }
    }
}

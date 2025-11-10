package edu.ftcphoenix.fw2.mechanisms;

import com.qualcomm.robotcore.hardware.DcMotor;
import com.qualcomm.robotcore.hardware.DcMotorEx;
import com.qualcomm.robotcore.hardware.DcMotorSimple;
import com.qualcomm.robotcore.hardware.HardwareMap;

import org.firstinspires.ftc.robotcore.external.Telemetry;

import edu.ftcphoenix.fw2.core.DoubleSetpoint;
import edu.ftcphoenix.fw2.core.FrameClock;
import edu.ftcphoenix.fw2.filters.Filter;
import edu.ftcphoenix.fw2.filters.Pipeline;
import edu.ftcphoenix.fw2.filters.scalar.Clamp;
import edu.ftcphoenix.fw2.filters.scalar.SlewLimiter;
import edu.ftcphoenix.fw2.subsystems.Subsystem;
import edu.ftcphoenix.fw2.util.MathUtil;

/**
 * Dual shooter using FrameClock + Source + Filter pipelines.
 * - Mode OPEN_LOOP: power in [-1,1]
 * - Mode VELOCITY_RPM: velocity in RPM (internally converted to ticks/s)
 * <p>
 * Builder encapsulates all hardware creation + configuration.
 */
public final class DualShooter implements Subsystem {

    // ===== Modes =====
    public enum Mode {OPEN_LOOP, VELOCITY_RPM}

    private enum ControlPath {UNSET, OPEN_LOOP, VELOCITY}

    // ===== Hardware & model =====
    private final DcMotorEx left, right;
    private final double ticksPerRev;

    // ===== Config/state =====
    private ControlPath path = ControlPath.UNSET;
    private Mode mode = Mode.VELOCITY_RPM;

    // Targets & shaping (composition)
    private final DoubleSetpoint baseTarget; // units depend on mode (power or rpm)

    // Scalar pipelines for setpoint shaping (built once)
    private final Filter<Double> powerPipe; // clamp -> slew
    private final Filter<Double> rpmPipe;   // clamp -> slew(up/down)

    // Bias/offsets and limits
    private double leftOffsetRpm = 0.0;
    private double rightOffsetRpm = 0.0;
    private double toleranceRpm = 50.0;

    private double maxAbsPower = 1.0;
    private double maxRpm = 5500.0;

    // Slew rates
    private double powerRate = 4.0;           // power units per second
    private double rpmRateUp = 1200.0;        // rpm/sec
    private double rpmRateDown = 1800.0;      // rpm/sec

    // Shaped values for telemetry, if needed
    private double lastShapedPower = 0.0;
    private double lastShapedRpm = 0.0;

    // ===== Builder =====
    public static final class PIDFCoefficients {
        public final double p, i, d, f;

        public PIDFCoefficients(double p, double i, double d, double f) {
            this.p = p;
            this.i = i;
            this.d = d;
            this.f = f;
        }
    }

    public static final class Config {
        public double toleranceRpm = 50.0;
        public double maxRpm = 5500.0;
        public double powerRate = 4.0;
        public double rpmRateUp = 1200.0;
        public double rpmRateDown = 1800.0;
        public PIDFCoefficients leftPIDF = null; // null => don't touch
        public PIDFCoefficients rightPIDF = null;
        public boolean resetEncodersOnInit = true;
        public DcMotor.ZeroPowerBehavior zeroPower = DcMotor.ZeroPowerBehavior.BRAKE;
        public Mode initialMode = Mode.VELOCITY_RPM;
    }

    public static final class Builder {
        private final HardwareMap hw;
        private String leftName, rightName;
        private DcMotorSimple.Direction leftDir = DcMotorSimple.Direction.FORWARD;
        private DcMotorSimple.Direction rightDir = DcMotorSimple.Direction.REVERSE; // reverse one so commands match
        private double ticksPerRev;
        private final Config cfg = new Config();

        public Builder(HardwareMap hw) {
            this.hw = hw;
        }

        public Builder setNames(String left, String right) {
            this.leftName = left;
            this.rightName = right;
            return this;
        }

        public Builder setDirections(DcMotorSimple.Direction l, DcMotorSimple.Direction r) {
            this.leftDir = l;
            this.rightDir = r;
            return this;
        }

        public Builder setTicksPerRev(double tpr) {
            this.ticksPerRev = tpr;
            return this;
        }

        // tuning
        public Builder setToleranceRpm(double t) {
            cfg.toleranceRpm = t;
            return this;
        }

        public Builder setMaxRpm(double max) {
            cfg.maxRpm = max;
            return this;
        }

        public Builder setPowerRate(double rate) {
            cfg.powerRate = rate;
            return this;
        }

        public Builder setRpmRates(double up, double down) {
            cfg.rpmRateUp = up;
            cfg.rpmRateDown = down;
            return this;
        }

        public Builder setVelocityPidfLeft(double p, double i, double d, double f) {
            cfg.leftPIDF = new PIDFCoefficients(p, i, d, f);
            return this;
        }

        public Builder setVelocityPidfRight(double p, double i, double d, double f) {
            cfg.rightPIDF = new PIDFCoefficients(p, i, d, f);
            return this;
        }

        public Builder setResetEncodersOnInit(boolean on) {
            cfg.resetEncodersOnInit = on;
            return this;
        }

        public Builder setZeroPower(DcMotor.ZeroPowerBehavior z) {
            cfg.zeroPower = z;
            return this;
        }

        public Builder setInitialMode(Mode m) {
            cfg.initialMode = m;
            return this;
        }

        public DualShooter build() {
            if (leftName == null || rightName == null)
                throw new IllegalStateException("names(left,right) is required");
            if (ticksPerRev <= 0) throw new IllegalStateException("ticksPerRev must be > 0");

            DcMotorEx left = hw.get(DcMotorEx.class, leftName);
            DcMotorEx right = hw.get(DcMotorEx.class, rightName);

            // Directions first so encoder signs align with commands
            left.setDirection(leftDir);
            right.setDirection(rightDir);

            if (cfg.resetEncodersOnInit) {
                left.setMode(DcMotor.RunMode.STOP_AND_RESET_ENCODER);
                right.setMode(DcMotor.RunMode.STOP_AND_RESET_ENCODER);
            }
            left.setZeroPowerBehavior(cfg.zeroPower);
            right.setZeroPowerBehavior(cfg.zeroPower);

            if (cfg.leftPIDF != null)
                left.setVelocityPIDFCoefficients(cfg.leftPIDF.p, cfg.leftPIDF.i, cfg.leftPIDF.d, cfg.leftPIDF.f);
            if (cfg.rightPIDF != null)
                right.setVelocityPIDFCoefficients(cfg.rightPIDF.p, cfg.rightPIDF.i, cfg.rightPIDF.d, cfg.rightPIDF.f);

            DualShooter s = new DualShooter(left, right, ticksPerRev);
            s.toleranceRpm = cfg.toleranceRpm;
            s.maxRpm = Math.abs(cfg.maxRpm);
            s.powerRate = Math.max(0, cfg.powerRate);
            s.rpmRateUp = Math.max(0, cfg.rpmRateUp);
            s.rpmRateDown = Math.max(0, cfg.rpmRateDown);
            s.mode = cfg.initialMode;
            // Initialize run mode lazily on first command, or select here:
            if (cfg.initialMode == Mode.VELOCITY_RPM) s.ensureVelocityMode();
            else s.ensureOpenLoopMode();
            return s;
        }
    }

    // ===== Construction =====
    private DualShooter(DcMotorEx left, DcMotorEx right, double ticksPerRev) {
        this.left = left;
        this.right = right;
        this.ticksPerRev = ticksPerRev;

        // Targets start at 0. Your opmode sets them each frame.
        this.baseTarget = new DoubleSetpoint(0.0);

        // Build pipelines once; reuse per frame
        this.powerPipe = new Pipeline<Double>()
                .add(new Clamp(() -> -maxAbsPower, () -> maxAbsPower))
                .add(new SlewLimiter(() -> powerRate, () -> powerRate, true));

        this.rpmPipe = new Pipeline<Double>()
                .add(new Clamp(() -> -maxRpm, () -> maxRpm))
                .add(new SlewLimiter(() -> rpmRateUp, () -> rpmRateDown, true));

    }

    // ===== Public API =====
    public void setMode(Mode m) {
        this.mode = m;
    }

    public Mode getMode() {
        return mode;
    }

    /**
     * Velocity path: set target RPM (shared by both wheels)
     */
    public void setTargetRpm(double rpm) {
        ensureVelocityMode();
        baseTarget.set(rpm);
    }

    /**
     * Open-loop path: set power immediately via update() loop
     */
    public void setOpenLoopPower(double power) {
        ensureOpenLoopMode();
        baseTarget.set(power);
    }

    /**
     * Adjust per-side offsets in RPM for shot centering.
     */
    public void setLeftOffsetRpm(double rpm) {
        this.leftOffsetRpm = rpm;
    }

    public void setRightOffsetRpm(double rpm) {
        this.rightOffsetRpm = rpm;
    }

    /**
     * Positive values nudge shot to the right by speeding the right wheel.
     */
    public void setSideBiasRpm(double biasRpm) {
        this.leftOffsetRpm = -0.5 * biasRpm;
        this.rightOffsetRpm = +0.5 * biasRpm;
    }

    public void setTolerance(double tolRpm) {
        this.toleranceRpm = Math.abs(tolRpm);
    }

    public double getTargetRpm() {
        return baseTarget.peek();   // raw target, no dt needed, no side effects
    }

    public void setMaxRpm(double max) {
        this.maxRpm = Math.abs(max);
    }

    public void setPowerRate(double rate) {
        this.powerRate = Math.max(0, rate);
    }

    public void setRpmRates(double up, double down) {
        this.rpmRateUp = Math.max(0, up);
        this.rpmRateDown = Math.max(0, down);
    }

    public void stop() {
        left.setPower(0);
        right.setPower(0);
    }

    // ===== Loop =====

    /**
     * Call once per frame.
     */
    public void update(FrameClock clock) {
        if (mode == Mode.OPEN_LOOP) {
            ensureOpenLoopMode();
            // Shape power setpoint (baseTarget holds power) then apply per side
            lastShapedPower = powerPipe.apply(baseTarget.get(clock), clock.dtSec());
            double l = MathUtil.clampFinite(lastShapedPower, -maxAbsPower, maxAbsPower, 0.0);
            double r = MathUtil.clampFinite(lastShapedPower, -maxAbsPower, maxAbsPower, 0.0);
            left.setPower(l);
            right.setPower(r);
        } else { // VELOCITY_RPM
            ensureVelocityMode();
            // Shape RPM setpoint and add per-side offsets
            lastShapedRpm = rpmPipe.apply(baseTarget.get(clock), clock.dtSec());
            double leftRpm = MathUtil.clampFinite(lastShapedRpm + leftOffsetRpm, -maxRpm, maxRpm, 0.0);
            double rightRpm = MathUtil.clampFinite(lastShapedRpm + rightOffsetRpm, -maxRpm, maxRpm, 0.0);
            left.setVelocity(rpmToTicksPerSec(leftRpm));
            right.setVelocity(rpmToTicksPerSec(rightRpm));
        }
    }


    // ===== Telemetry =====
    public double getLeftRpm() {
        return (left.getVelocity() * 60.0) / ticksPerRev;
    }

    public double getRightRpm() {
        return (right.getVelocity() * 60.0) / ticksPerRev;
    }

    public double getAvgRpm() {
        return 0.5 * (getLeftRpm() + getRightRpm());
    }

    public boolean atTarget() {
        return atTarget(toleranceRpm);
    }

    public boolean atTarget(double tolRpm) {
        return Math.abs(getAvgRpm() - getTargetRpm()) <= Math.abs(tolRpm);
    }

    public boolean atTarget(double tolRpm, double deltaRpm) {
        double l = getLeftRpm(), r = getRightRpm(), t = getTargetRpm();
        boolean bothNearTarget = Math.abs(l - t) <= Math.abs(tolRpm) && Math.abs(r - t) <= Math.abs(tolRpm);
        boolean matchedPair = Math.abs(l - r) <= Math.abs(deltaRpm);
        return bothNearTarget && matchedPair;
    }

    public void addTelemetry(Telemetry tel, String label, boolean verbose) {
        final String modeStr =
                (path == ControlPath.VELOCITY) ? "VELOCITY" :
                        (path == ControlPath.OPEN_LOOP) ? "OPEN_LOOP" : "UNSET";

        tel.addData(label + "/mode", modeStr);

        double lRpm = getLeftRpm(), rRpm = getRightRpm();
        tel.addData(label + "/rpmL", "%.0f", lRpm);
        tel.addData(label + "/rpmR", "%.0f", rRpm);
        tel.addData(label + "/rpmAvg", "%.0f", getAvgRpm());

        if (mode == Mode.VELOCITY_RPM) {
            tel.addData(label + "/targetRpm", "%.0f", getTargetRpm());
            tel.addData(label + "/atTarget(±" + (int) toleranceRpm + ")", atTarget());
        }

        if (verbose) {
            tel.addData(label + "/ticksPerRev", "%.1f", ticksPerRev);
            tel.addData(label + "/offsetsRpm", "L=%.0f R=%.0f", leftOffsetRpm, rightOffsetRpm);
            tel.addData(label + "/toleranceRpm", "%.0f", toleranceRpm);
        }
    }

    // ===== Internals =====
    private void ensureOpenLoopMode() {
        if (path != ControlPath.OPEN_LOOP) {
            left.setMode(DcMotor.RunMode.RUN_WITHOUT_ENCODER);
            right.setMode(DcMotor.RunMode.RUN_WITHOUT_ENCODER);
            path = ControlPath.OPEN_LOOP;
        }
    }

    private void ensureVelocityMode() {
        if (path != ControlPath.VELOCITY) {
            left.setMode(DcMotor.RunMode.RUN_USING_ENCODER);
            right.setMode(DcMotor.RunMode.RUN_USING_ENCODER);
            path = ControlPath.VELOCITY;
        }
    }

    private double rpmToTicksPerSec(double rpm) {
        return (rpm / 60.0) * ticksPerRev;
    }
}

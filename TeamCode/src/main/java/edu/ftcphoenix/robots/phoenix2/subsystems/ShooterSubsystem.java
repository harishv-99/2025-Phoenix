package edu.ftcphoenix.robots.phoenix2.subsystems;

import static edu.ftcphoenix.robots.phoenix2.Constants.MOTOR_NAME_SHOOTER_LEFT;
import static edu.ftcphoenix.robots.phoenix2.Constants.MOTOR_NAME_SHOOTER_RIGHT;
import static edu.ftcphoenix.robots.phoenix2.Constants.SERVO_NAME_PUSHER;
import static edu.ftcphoenix.robots.phoenix2.Constants.SERVO_NAME_TRANSFER_LEFT;
import static edu.ftcphoenix.robots.phoenix2.Constants.SERVO_NAME_TRANSFER_RIGHT;

import com.qualcomm.robotcore.hardware.CRServo;
import com.qualcomm.robotcore.hardware.DcMotorSimple;
import com.qualcomm.robotcore.hardware.HardwareMap;
import com.qualcomm.robotcore.hardware.Servo;

import org.firstinspires.ftc.robotcore.external.Telemetry;

import edu.ftcphoenix.fw2.core.FrameClock;
import edu.ftcphoenix.fw2.mechanisms.DualShooter;
import edu.ftcphoenix.fw2.subsystems.Subsystem;
import edu.ftcphoenix.fw2.util.MathUtil;

/**
 * ShooterSubsystem — wraps the generic {@link DualShooter} mechanism + transfer/pusher hardware
 * into a framework Subsystem with a clean lifecycle.
 *
 * <p>Responsibilities:</p>
 * <ul>
 *   <li>Owns transfer servos and pusher servo.</li>
 *   <li>Owns and delegates to a {@link DualShooter} for RPM control.</li>
 *   <li>Implements {@link Subsystem} lifecycle so RobotBase can manage it.</li>
 * </ul>
 *
 * <p>Usage:</p>
 * <pre>
 * ShooterSubsystem shooter = new ShooterSubsystem(hw, telemetry);
 * // RobotBase.registerSubsystem(shooter);
 * shooter.setShooterRpm(3000);
 * shooter.setTransferPower(1.0);
 * shooter.update(clock); // RobotBase calls this each loop
 * </pre>
 */
public final class ShooterSubsystem implements Subsystem {
    // --- Hardware ---
    private final CRServo transferLeft;
    private final CRServo transferRight;
    private final Servo pusher;
    private final Telemetry telemetry;

    // --- Mechanism (framework) ---
    private final DualShooter shooter;

    // --- Model / config ---
    private static final double TICKS_PER_REV = 28.0;

    // Keep pusher motion within a safe window on your linkage
    private static final double PUSHER_POS_MIN = 0.50;
    private static final double PUSHER_POS_MAX = 0.90;

    // Remember last non-trivial target for resume
    private double previousRpm = 0.0;

    /**
     * Build and wire all shooter-related hardware.
     */
    public ShooterSubsystem(HardwareMap hardwareMap, Telemetry telemetry) {
        this.telemetry = telemetry;

        // Pusher servo
        pusher = hardwareMap.get(Servo.class, SERVO_NAME_PUSHER);

        // Dual shooter (uses framework "mechanism" builder)
        shooter = new DualShooter.Builder(hardwareMap)
                .setNames(MOTOR_NAME_SHOOTER_LEFT, MOTOR_NAME_SHOOTER_RIGHT)
                // Set directions so positive RPM spins both wheels in the intended shot direction.
                // (If needed, flip one here; many builds require opposite directions.)
                .setDirections(DcMotorSimple.Direction.FORWARD, DcMotorSimple.Direction.FORWARD)
                .setTicksPerRev(TICKS_PER_REV)
                .build();

        // Transfer (CR) servos
        transferLeft = hardwareMap.get(CRServo.class, SERVO_NAME_TRANSFER_LEFT);
        transferLeft.setDirection(CRServo.Direction.REVERSE); // make both +power feed inward

        transferRight = hardwareMap.get(CRServo.class, SERVO_NAME_TRANSFER_RIGHT);
        transferRight.setDirection(CRServo.Direction.FORWARD);
    }

    // =====================================================================
    // Subsystem lifecycle
    // =====================================================================

    @Override
    public void onEnable() {
        // Nothing special; you could zero pusher/transfer here if desired.
    }

    @Override
    public void update(FrameClock clock) {
        shooter.update(clock);
    }

    @Override
    public void onDisable() {
        // Make the mechanism safe but allow RobotBase.stop() to be the hard kill.
        setTransferPower(0.0);
        shooter.setTargetRpm(0.0);
    }

    @Override
    public void stop() {
        // Final hard stop — make sure everything is idle.
        setTransferPower(0.0);
        shooter.setTargetRpm(0.0);
        // If you have a neutral pusher position, set it explicitly here (optional).
        // pusher.setPosition(MathUtil.clamp(NEUTRAL, PUSHER_POS_MIN, PUSHER_POS_MAX));
    }

    // =====================================================================
    // Public API used by TeleOpController
    // =====================================================================

    /**
     * Resume last non-trivial RPM target.
     */
    public void resumeRpm() {
        shooter.setTargetRpm(previousRpm);
    }

    /**
     * Set shooter target in RPM. Values ≤ 1 are treated as "off" and won't be saved as previous.
     */
    public void setShooterRpm(double rpm) {
        if (rpm > 1.0) previousRpm = rpm;
        shooter.setTargetRpm(rpm);
    }

    /**
     * Current RPM target reported by the mechanism.
     */
    public double getShooterTargetRpm() {
        return shooter.getTargetRpm();
    }

    /**
     * Clamp and set the pusher servo position to a safe range.
     */
    public void setPusherPos(double pusherPos) {
        pusher.setPosition(MathUtil.clamp(pusherPos, PUSHER_POS_MIN, PUSHER_POS_MAX));
    }

    /**
     * Current pusher servo position.
     */
    public double getPusherPos() {
        return pusher.getPosition();
    }

    /**
     * Set both transfer servos power in [-1,1]. Positive should feed toward the shooter.
     */
    public void setTransferPower(double transferPower) {
        transferLeft.setPower(transferPower);
        transferRight.setPower(transferPower);
    }

    /**
     * Convenience: start intake from feeder toward shooter (direction per wiring).
     */
    public void startLoad() {
        // If you want the pusher clear for loading, drive to the min bound
        setPusherPos(PUSHER_POS_MIN);
        setTransferPower(-1.0); // tune sign to your build
    }

    /**
     * Stop feeding.
     */
    public void stopLoad() {
        setTransferPower(0.0);
    }

    /**
     * Telemetry hook for dashboards.
     *
     * @param tel     telemetry to write to
     * @param label   base label to prefix keys (e.g., "shooter")
     * @param verbose include extra details when true
     */
    public void addTelemetry(Telemetry tel, String label, boolean verbose) {
        tel.addData(label + "/rpmL", "%.0f", shooter.getLeftRpm());
        tel.addData(label + "/rpmR", "%.0f", shooter.getRightRpm());
        tel.addData(label + "/target", "%.0f", shooter.getTargetRpm());
        if (verbose) {
            tel.addData(label + "/pusher", "%.2f", getPusherPos());
            tel.addData(label + "/xferL/R", "%.2f/%.2f", transferLeft.getPower(), transferRight.getPower());
        }
    }
}

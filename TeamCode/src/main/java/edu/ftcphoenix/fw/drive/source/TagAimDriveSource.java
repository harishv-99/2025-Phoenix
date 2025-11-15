package edu.ftcphoenix.fw.drive.source;

import java.util.Objects;

import edu.ftcphoenix.fw.drive.DriveSignal;
import edu.ftcphoenix.fw.drive.DriveSource;
import edu.ftcphoenix.fw.input.Button;
import edu.ftcphoenix.fw.sensing.TagAimController;
import edu.ftcphoenix.fw.util.LoopClock;

/**
 * Drive source that wraps another {@link DriveSource} and adds AprilTag-based
 * auto-aim behavior when a button is held.
 *
 * <p>This class is intended to be used in TeleOp, where driver stick inputs
 * produce a base {@link DriveSignal} (via, for example,
 * {@link StickDriveSource}) and a button is used to temporarily override
 * the turn component ({@link DriveSignal#omega}) so that the robot rotates
 * to face an AprilTag.</p>
 *
 * <h2>Responsibilities</h2>
 *
 * <ul>
 *   <li>Delegate to an underlying "base" {@link DriveSource} to compute
 *       the raw drive signal each loop.</li>
 *   <li>While the aim button is pressed:
 *     <ul>
 *       <li>Use a {@link TagAimController} to compute an omega command
 *           based on tag bearing.</li>
 *       <li>Replace the base signal's omega with the controller's output.</li>
 *     </ul>
 *   </li>
 *   <li>When the aim button is not pressed, pass the base signal through
 *       unchanged.</li>
 * </ul>
 *
 * <h2>Typical usage</h2>
 *
 * <pre>{@code
 * // TeleOp init:
 * AprilTagSensor tags = Tags.aprilTags(hardwareMap, "Webcam 1");
 * Set<Integer> scoringTags = Set.of(1, 2, 3);
 *
 * TagAimController aim = TagAimController.withDefaults(tags, scoringTags);
 *
 * StickDriveSource sticks = new StickDriveSource(p1(), new StickDriveSource.Params());
 *
 * DriveSource drive = new TagAimDriveSource(
 *         sticks,
 *         p1().leftBumper(),
 *         aim);
 *
 * // TeleOp loop:
 * drivebase.drive(drive.get(clock()));
 * }</pre>
 *
 * <p>Most teams will not instantiate this class directly; they can instead
 * use a helper such as {@code TagAim.forTeleOp(...)} that wires everything
 * together in one call.</p>
 */
public final class TagAimDriveSource implements DriveSource {

    private final DriveSource base;
    private final Button aimButton;
    private final TagAimController controller;

    // Telemetry helpers
    private boolean lastAiming = false;
    private double lastOmega = 0.0;

    /**
     * Create a new TagAimDriveSource.
     *
     * @param base       base drive source (e.g., {@link StickDriveSource});
     *                   must not be {@code null}
     * @param aimButton  button that enables auto-aim while pressed;
     *                   must not be {@code null}
     * @param controller tag aim controller used to compute omega;
     *                   must not be {@code null}
     */
    public TagAimDriveSource(DriveSource base,
                             Button aimButton,
                             TagAimController controller) {
        this.base = Objects.requireNonNull(base, "base");
        this.aimButton = Objects.requireNonNull(aimButton, "aimButton");
        this.controller = Objects.requireNonNull(controller, "controller");
    }

    @Override
    public DriveSignal get(LoopClock clock) {
        // Always compute the base signal first.
        DriveSignal baseSignal = base.get(clock);

        // Check whether the driver is requesting auto-aim.
        lastAiming = aimButton.isPressed();
        if (!lastAiming) {
            // Not aiming: pass through the base signal unchanged.
            lastOmega = baseSignal.omega;
            return baseSignal;
        }

        // Aiming: use tag-based controller to compute omega.
        double dtSec = clock.dtSec();
        double omega = controller.update(dtSec);
        lastOmega = omega;

        // Replace the turn component; keep axial/lateral from the base signal.
        return baseSignal.withOmega(omega);
    }

    // -------------------------------------------------------------------------
    // Telemetry helpers
    // -------------------------------------------------------------------------

    /**
     * @return whether the aim button was pressed during the last call to
     * {@link #get(LoopClock)}
     */
    public boolean isAiming() {
        return lastAiming;
    }

    /**
     * @return whether the {@link TagAimController} had a valid tag during
     * the last call to {@link #get(LoopClock)}
     */
    public boolean hasTarget() {
        return controller.hasTarget();
    }

    /**
     * @return last omega command produced while aiming; 0 when not aiming
     */
    public double getLastOmega() {
        return lastOmega;
    }

    /**
     * @return last bearing error (degrees) reported by the controller; 0 if
     * no valid tag was seen
     */
    public double getErrorDeg() {
        return controller.getErrorDeg();
    }
}

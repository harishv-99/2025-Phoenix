package edu.ftcphoenix.fw.drive.assist;

import edu.ftcphoenix.fw.core.debug.DebugSink;
import edu.ftcphoenix.fw.drive.DriveSignal;
import edu.ftcphoenix.fw.drive.DriveSource;
import edu.ftcphoenix.fw.input.Button;
import edu.ftcphoenix.fw.drive.assist.BearingSource.BearingSample;
import edu.ftcphoenix.fw.core.time.LoopClock;

/**
 * {@link DriveSource} wrapper that adds tag-based auto-aim to an existing drive.
 *
 * <h2>Role</h2>
 * <p>
 * {@code TagAimDriveSource} is a reusable implementation of the same behavior that
 * {@code TagAim.teleOpAim(...)} provides:
 * </p>
 *
 * <ul>
 *   <li>It holds a “base” {@link DriveSource} (e.g., stick-based drive).</li>
 *   <li>It reads a {@link BearingSource} (target bearing sample).</li>
 *   <li>It uses a {@link TagAimController} to turn bearing into {@code omega}.</li>
 *   <li>When the {@link Button} is pressed:
 *     <ul>
 *       <li>Axial and lateral commands come from the base source.</li>
 *       <li>Omega is overridden by the controller’s output.</li>
 *     </ul>
 *   </li>
 *   <li>When the button is <b>not</b> pressed, it passes through the base {@link DriveSignal} unchanged.</li>
 * </ul>
 *
 * <p>
 * This class is useful if you prefer to construct and hold an explicit object rather than using
 * a helper factory or inline lambda.
 * </p>
 *
 * <h2>Camera offset and robot-centric aiming</h2>
 * <p>
 * This class does not know where the camera is mounted — it simply consumes a {@link BearingSource}.
 * If your camera is offset from the robot center and you want the <b>robot center</b> to face the tag,
 * build a robot-centric bearing source (using {@code CameraMountConfig} + {@code CameraMountLogic}),
 * or use the {@code TagAim.teleOpAim(..., cameraMount)} overload.
 * </p>
 *
 * <p>
 * Conceptually:
 * </p>
 * <ul>
 *   <li>Camera sees {@code cameraToTagPose} (camera→tag).</li>
 *   <li>Mount gives {@code robotToCameraPose} (robot→camera).</li>
 *   <li>Compute {@code robotToTagPose = robotToCameraPose.then(cameraToTagPose)}.</li>
 *   <li>Robot-centric bearing is {@code atan2(left, forward)} in the robot frame.</li>
 * </ul>
 *
 * <h2>Sign conventions</h2>
 *
 * <p>
 * {@code TagAimDriveSource} does not change the meaning of {@link DriveSignal}; it only decides
 * <em>who</em> supplies {@link DriveSignal#omega}:
 * </p>
 *
 * <ul>
 *   <li>When aiming is <b>off</b>, {@code omega} comes from {@code baseDrive}.</li>
 *   <li>When aiming is <b>on</b>, {@code omega} comes from {@link TagAimController}.</li>
 * </ul>
 *
 * <p>
 * The maintained sign conventions are those of {@link DriveSignal} (Phoenix pose conventions):
 * </p>
 *
 * <ul>
 *   <li>{@code axial > 0} &rarr; drive forward</li>
 *   <li>{@code lateral > 0} &rarr; strafe left</li>
 *   <li>{@code omega > 0} &rarr; rotate counter-clockwise (turn left, viewed from above)</li>
 * </ul>
 *
 * <p>
 * Typical {@link BearingSource} implementations use:
 * </p>
 *
 * <ul>
 *   <li>{@code bearingRad > 0} &rarr; target appears to the left</li>
 *   <li>{@code bearingRad < 0} &rarr; target appears to the right</li>
 * </ul>
 *
 * <p>
 * With Phoenix conventions, the correct steering behavior is:
 * </p>
 *
 * <ul>
 *   <li>Target left (positive bearing) &rarr; {@code omega > 0} (turn left)</li>
 *   <li>Target right (negative bearing) &rarr; {@code omega < 0} (turn right)</li>
 * </ul>
 *
 * <p>{@link TagAimController} is responsible for choosing {@code omega} to achieve that behavior.</p>
 *
 * <h2>Typical usage</h2>
 *
 * <pre>{@code
 * // Base driver control (sticks) for mecanum drive.
 * DriveSource baseDrive = GamepadDriveSource.teleOpMecanumStandard(gamepads);
 *
 * // Tag tracking (updated elsewhere each loop).
 * TagTarget target = new TagTarget(tagSensor, scoringTagIds, 0.5);
 *
 * // Camera-centric bearing (camera faces tag):
 * BearingSource bearing = clock -> target.toBearingSample();
 *
 * // OR robot-centric bearing (robot center faces tag) if you have a CameraMountConfig:
 * // BearingSource bearing = clock -> CameraMountLogic.robotBearingSample(target.last(), cameraMount);
 *
 * TagAimController aimCtrl = TagAim.controllerFromConfig(TagAim.Config.defaults());
 * Button aimButton = gamepads.p1().leftBumper();
 *
 * DriveSource aimedDrive = new TagAimDriveSource(baseDrive, aimButton, bearing, aimCtrl);
 *
 * // In your loop:
 * clock.update();
 * target.update();
 * DriveSignal signal = aimedDrive.get(clock).clamped();
 * drivebase.update(clock);   // provides dt for drivebase rate limiting (if enabled)
 * drivebase.drive(signal);
 * }</pre>
 */
public final class TagAimDriveSource implements DriveSource {

    private final DriveSource baseDrive;
    private final Button aimButton;
    private final BearingSource bearingSource;
    private final TagAimController controller;

    // Debug / introspection state
    private DriveSignal lastBaseSignal = DriveSignal.zero();
    private DriveSignal lastOutputSignal = DriveSignal.zero();
    private BearingSample lastBearingSample = null;
    private double lastOmegaFromController = 0.0;
    private boolean lastAimActive = false;

    /**
     * Construct a tag-aiming drive source.
     *
     * @param baseDrive     existing drive source (sticks, planner, etc.); must not be null
     * @param aimButton     button that enables aiming while pressed; must not be null
     * @param bearingSource source of bearing measurements to the target; must not be null
     * @param controller    controller that turns bearing into omega; must not be null
     */
    public TagAimDriveSource(DriveSource baseDrive,
                             Button aimButton,
                             BearingSource bearingSource,
                             TagAimController controller) {
        if (baseDrive == null) {
            throw new IllegalArgumentException("baseDrive is required");
        }
        if (aimButton == null) {
            throw new IllegalArgumentException("aimButton is required");
        }
        if (bearingSource == null) {
            throw new IllegalArgumentException("bearingSource is required");
        }
        if (controller == null) {
            throw new IllegalArgumentException("controller is required");
        }

        this.baseDrive = baseDrive;
        this.aimButton = aimButton;
        this.bearingSource = bearingSource;
        this.controller = controller;
    }

    /**
     * Get the current drive signal, optionally overriding omega to aim at the tag.
     *
     * <p>Behavior:</p>
     * <ul>
     *   <li>If {@code aimButton} is <b>not</b> pressed, returns the result of
     *       {@link DriveSource#get(LoopClock)} from {@code baseDrive}.</li>
     *   <li>If {@code aimButton} <b>is</b> pressed:
     *     <ul>
     *       <li>Sample bearing via {@link BearingSource#sample(LoopClock)}.</li>
     *       <li>Update the {@link TagAimController} via
     *           {@link TagAimController#update(LoopClock, BearingSample)}.</li>
     *       <li>Keep axial/lateral from the base drive, override omega using the
     *           controller's output.</li>
     *     </ul>
     *   </li>
     * </ul>
     *
     * <p>
     * The omega used when aiming is the controller's output and follows the
     * {@link DriveSignal} convention: {@code omega > 0} rotates CCW (turn left),
     * {@code omega < 0} rotates CW (turn right).
     * </p>
     */
    @Override
    public DriveSignal get(LoopClock clock) {
        // Get the base driver command first.
        DriveSignal base = baseDrive.get(clock);
        if (base == null) {
            base = DriveSignal.zero();
        }
        lastBaseSignal = base;

        if (!aimButton.isHeld()) {
            // No aiming: pass through base drive signal.
            lastAimActive = false;
            lastBearingSample = null;
            lastOmegaFromController = base.omega;
            lastOutputSignal = base;
            return base;
        }

        // Aiming: sample bearing and update the controller.
        BearingSample sample = bearingSource.sample(clock);
        lastBearingSample = sample;

        double omega = controller.update(clock, sample);
        lastOmegaFromController = omega;
        lastAimActive = true;

        // Preserve driver-controlled axial/lateral, override omega to aim.
        DriveSignal out = new DriveSignal(base.axial, base.lateral, omega);
        lastOutputSignal = out;
        return out;
    }

    // ------------------------------------------------------------------------
    // Debug support
    // ------------------------------------------------------------------------

    /**
     * Dump internal state to a {@link DebugSink}.
     *
     * <p>
     * This is intended for one-off debugging and tuning. Callers can choose any prefix they like;
     * nested callers often use dotted paths such as {@code "drive.tagAim"}.
     * </p>
     *
     * <p>
     * This method is defensive: if {@code dbg} is {@code null}, it does nothing.
     * Framework classes consistently follow this pattern so callers may freely pass
     * a {@code NullDebugSink} or {@code null}.
     * </p>
     *
     * @param dbg    debug sink to write to (may be {@code null})
     * @param prefix key prefix for all entries (may be {@code null} or empty)
     */
    public void debugDump(DebugSink dbg, String prefix) {
        if (dbg == null) {
            return;
        }
        String p = (prefix == null || prefix.isEmpty()) ? "tagAim" : prefix;

        dbg.addLine(p + ": TagAimDriveSource");

        dbg.addData(p + ".aimActive", lastAimActive);

        if (lastBearingSample != null) {
            dbg.addData(p + ".bearing.hasTarget", lastBearingSample.hasTarget);
            dbg.addData(p + ".bearing.bearingRad", lastBearingSample.bearingRad);
        } else {
            dbg.addData(p + ".bearing.hasTarget", false);
        }

        dbg.addData(p + ".omega.fromController", lastOmegaFromController);

        dbg.addData(p + ".base.axial", lastBaseSignal.axial);
        dbg.addData(p + ".base.lateral", lastBaseSignal.lateral);
        dbg.addData(p + ".base.omega", lastBaseSignal.omega);

        dbg.addData(p + ".out.axial", lastOutputSignal.axial);
        dbg.addData(p + ".out.lateral", lastOutputSignal.lateral);
        dbg.addData(p + ".out.omega", lastOutputSignal.omega);
    }

    /**
     * Whether aiming was active the last time {@link #get(LoopClock)} was called.
     */
    public boolean isLastAimActive() {
        return lastAimActive;
    }

    /**
     * Last bearing sample observed while aiming, if any.
     */
    public BearingSample getLastBearingSample() {
        return lastBearingSample;
    }

    /**
     * Last omega command produced by the {@link TagAimController}.
     */
    public double getLastOmegaFromController() {
        return lastOmegaFromController;
    }

    /**
     * Last base drive signal used before any aiming override.
     */
    public DriveSignal getLastBaseSignal() {
        return lastBaseSignal;
    }

    /**
     * Last output signal produced by this drive source (after aiming logic).
     */
    public DriveSignal getLastOutputSignal() {
        return lastOutputSignal;
    }
}

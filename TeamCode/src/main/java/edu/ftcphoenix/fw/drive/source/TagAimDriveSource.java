package edu.ftcphoenix.fw.drive.source;

import edu.ftcphoenix.fw.debug.DebugSink;
import edu.ftcphoenix.fw.drive.DriveSignal;
import edu.ftcphoenix.fw.drive.DriveSource;
import edu.ftcphoenix.fw.input.Button;
import edu.ftcphoenix.fw.sensing.BearingSource;
import edu.ftcphoenix.fw.sensing.BearingSource.BearingSample;
import edu.ftcphoenix.fw.sensing.TagAimController;
import edu.ftcphoenix.fw.util.LoopClock;

/**
 * {@link DriveSource} wrapper that adds tag-based auto-aim to an existing drive.
 *
 * <h2>Role</h2>
 * <p>
 * {@code TagAimDriveSource} is a reusable implementation of the same behavior
 * that a typical {@code TagAim.teleOpAim(...)} helper would provide:
 * </p>
 *
 * <ul>
 *   <li>It holds a "base" {@link DriveSource} (e.g., stick-based drive).</li>
 *   <li>It reads a {@link BearingSource} (e.g., AprilTags wrapped as bearing).</li>
 *   <li>It uses a {@link TagAimController} to turn bearing into omega.</li>
 *   <li>When the {@link Button} is pressed:
 *     <ul>
 *       <li>Axial and lateral commands come from the base source.</li>
 *       <li>Omega is overridden by the controller's output.</li>
 *     </ul>
 *   </li>
 *   <li>When the button is <b>not</b> pressed, it simply passes through the
 *       base {@link DriveSignal} unchanged.</li>
 * </ul>
 *
 * <p>
 * This class is useful if you prefer to construct and hold an explicit object
 * instead of using an anonymous wrapper or inline lambda.
 * </p>
 */
public final class TagAimDriveSource implements DriveSource {

    private final DriveSource baseDrive;
    private final Button aimButton;
    private final BearingSource bearingSource;
    private final TagAimController controller;

    // Debug / introspection state
    private DriveSignal lastBaseSignal = DriveSignal.ZERO;
    private DriveSignal lastOutputSignal = DriveSignal.ZERO;
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
     *       <li>Keep axial/lateral from the base drive, override omega.</li>
     *     </ul>
     *   </li>
     * </ul>
     */
    @Override
    public DriveSignal get(LoopClock clock) {
        // Always start from the base drive.
        DriveSignal base = baseDrive.get(clock);
        lastBaseSignal = base;

        if (!aimButton.isPressed()) {
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
     * This is intended for one-off debugging and tuning. Callers can choose
     * any prefix they like; nested callers often use dotted paths such as
     * {@code "drive.tagAim"}.
     * </p>
     *
     * <p>
     * This method is defensive: if {@code dbg} is {@code null}, it does
     * nothing. Framework classes consistently follow this pattern so callers
     * may freely pass a {@code NullDebugSink} or {@code null}.
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

        // Identity of sub-components.
        dbg.addData(p + ".baseDrive.class", baseDrive.getClass().getSimpleName());
        dbg.addData(p + ".controller.class", controller.getClass().getSimpleName());
        dbg.addData(p + ".aimButton.pressed", aimButton.isPressed());

        // Aiming state.
        dbg.addData(p + ".aim.active", lastAimActive);
        dbg.addData(p + ".aim.lastOmega", lastOmegaFromController);

        // Base vs output command.
        dbg.addData(p + ".base.axial", lastBaseSignal.axial);
        dbg.addData(p + ".base.lateral", lastBaseSignal.lateral);
        dbg.addData(p + ".base.omega", lastBaseSignal.omega);

        dbg.addData(p + ".out.axial", lastOutputSignal.axial);
        dbg.addData(p + ".out.lateral", lastOutputSignal.lateral);
        dbg.addData(p + ".out.omega", lastOutputSignal.omega);

        // Last bearing sample (if any).
        boolean hasSample = (lastBearingSample != null);
        dbg.addData(p + ".bearing.samplePresent", hasSample);
        if (hasSample) {
            dbg.addData(p + ".bearing.hasTarget", lastBearingSample.hasTarget);
            dbg.addData(p + ".bearing.bearingRad", lastBearingSample.bearingRad);
        }
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

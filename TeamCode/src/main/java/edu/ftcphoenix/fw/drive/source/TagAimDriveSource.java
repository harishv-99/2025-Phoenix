package edu.ftcphoenix.fw.drive.source;

import edu.ftcphoenix.fw.drive.DriveSignal;
import edu.ftcphoenix.fw.drive.DriveSource;
import edu.ftcphoenix.fw.input.Button;
import edu.ftcphoenix.fw.sensing.BearingSource;
import edu.ftcphoenix.fw.sensing.BearingSource.BearingSample;
import edu.ftcphoenix.fw.sensing.TagAimController;
import edu.ftcphoenix.fw.util.LoopClock;

/**
 * DriveSource that wraps another DriveSource and adds tag-based auto-aim.
 *
 * <h2>Role</h2>
 * <p>{@code TagAimDriveSource} is a reusable implementation of the same
 * behavior that {@code TagAim.forTeleOp(...)} provides:
 *
 * <ul>
 *   <li>It holds a "base" {@link DriveSource} (e.g. stick mapping).</li>
 *   <li>It reads a {@link BearingSource} (e.g. AprilTags wrapped as bearing).</li>
 *   <li>It uses a {@link TagAimController} to turn bearing into omega.</li>
 *   <li>When the {@link Button} is pressed:
 *     <ul>
 *       <li>Axial and lateral commands come from the base source.</li>
 *       <li>Omega is overridden by the controller's output.</li>
 *     </ul>
 *   </li>
 *   <li>When the button is <b>not</b> pressed, it simply passes through
 *       the base {@link DriveSignal} unchanged.</li>
 * </ul>
 *
 * <p>This class is useful if you prefer to construct and hold an explicit
 * object instead of using anonymous wrappers.
 */
public final class TagAimDriveSource implements DriveSource {

    private final DriveSource baseDrive;
    private final Button aimButton;
    private final BearingSource bearingSource;
    private final TagAimController controller;

    /**
     * Construct a tag-aiming drive source.
     *
     * @param baseDrive     existing drive source (sticks, planner, etc.)
     * @param aimButton     button that enables aiming while pressed
     * @param bearingSource source of bearing measurements to the target
     * @param controller    controller that turns bearing into omega
     */
    public TagAimDriveSource(DriveSource baseDrive,
                             Button aimButton,
                             BearingSource bearingSource,
                             TagAimController controller) {
        this.baseDrive = baseDrive;
        this.aimButton = aimButton;
        this.bearingSource = bearingSource;
        this.controller = controller;
    }

    /**
     * Get the current drive signal.
     *
     * <p>Behavior:
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
        DriveSignal base = baseDrive.get(clock);

        if (!aimButton.isPressed()) {
            // No aiming: pass through base drive signal.
            return base;
        }

        BearingSample sample = bearingSource.sample(clock);
        double omega = controller.update(clock, sample);

        // Preserve driver-controlled axial/lateral, override omega to aim.
        return new DriveSignal(base.axial, base.lateral, omega);
    }
}

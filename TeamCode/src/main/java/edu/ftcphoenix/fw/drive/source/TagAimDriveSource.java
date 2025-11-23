package edu.ftcphoenix.fw.drive.source;

import edu.ftcphoenix.fw.drive.DriveSignal;
import edu.ftcphoenix.fw.drive.DriveSource;
import edu.ftcphoenix.fw.input.Button;
import edu.ftcphoenix.fw.sensing.BearingSource;
import edu.ftcphoenix.fw.sensing.BearingSource.BearingSample;
import edu.ftcphoenix.fw.sensing.TagAimController;
import edu.ftcphoenix.fw.util.LoopClock;

import java.util.Objects;

/**
 * DriveSource that wraps another DriveSource and adds tag-based auto-aim.
 *
 * <h2>Role</h2>
 * <p>{@code TagAimDriveSource} is a reusable implementation of the same
 * behavior that {@code TagAim.teleOpAim(...)} provides:</p>
 *
 * <ul>
 *   <li>It holds a "base" {@link DriveSource} (e.g. stick mapping).</li>
 *   <li>It reads a {@link BearingSource} (e.g. AprilTags wrapped as bearing).</li>
 *   <li>It uses a {@link TagAimController} to turn bearing into an omega command.</li>
 *   <li>It preserves driver axial/lateral commands and only overrides omega.</li>
 * </ul>
 *
 * <p>Most teams should prefer the static helper
 * {@link edu.ftcphoenix.fw.sensing.TagAim#teleOpAim(DriveSource, Button, edu.ftcphoenix.fw.sensing.AprilTagSensor, java.util.Set)}
 * or its advanced overload. This class remains as an explicit, reusable
 * DriveSource implementation for advanced users who prefer to wire the pieces
 * manually.</p>
 */
public final class TagAimDriveSource implements DriveSource {

    private final DriveSource baseDrive;
    private final Button aimButton;
    private final BearingSource bearingSource;
    private final TagAimController controller;

    /**
     * Construct a TagAimDriveSource.
     *
     * @param baseDrive     underlying drive behavior (e.g., sticks)
     * @param aimButton     button that enables aiming while pressed
     * @param bearingSource bearing source (e.g. AprilTags wrapped as BearingSource)
     * @param controller    controller that converts bearing into omega
     */
    public TagAimDriveSource(DriveSource baseDrive,
                             Button aimButton,
                             BearingSource bearingSource,
                             TagAimController controller) {

        this.baseDrive = Objects.requireNonNull(baseDrive, "baseDrive must not be null");
        this.aimButton = Objects.requireNonNull(aimButton, "aimButton must not be null");
        this.bearingSource = Objects.requireNonNull(bearingSource, "bearingSource must not be null");
        this.controller = Objects.requireNonNull(controller, "controller must not be null");
    }

    /**
     * Compute a drive command for this loop.
     *
     * <p>Behavior:</p>
     * <ul>
     *   <li>Always starts from {@code baseDrive.get(clock)}.</li>
     *   <li>If {@code aimButton} is not pressed, we return the base command
     *       unchanged.</li>
     *   <li>If the button is pressed:
     *     <ul>
     *       <li>We sample the bearing ({@link BearingSource#sample(LoopClock)}).</li>
     *       <li>We ask the controller for an omega command.</li>
     *       <li>We return a new {@link DriveSignal} that preserves axial/lateral
     *           from the base command and overrides omega.</li>
     *     </ul>
     *   </li>
     * </ul>
     */
    @Override
    public DriveSignal get(LoopClock clock) {
        // Underlying behavior (usually stick mapping).
        DriveSignal base = baseDrive.get(clock);

        if (!aimButton.isPressed()) {
            // No aiming: pass through base drive signal.
            return base;
        }

        // Use bearing + controller to compute omega.
        BearingSample sample = bearingSource.sample(clock);
        double omega = controller.update(clock, sample);

        // Preserve driver-controlled axial/lateral, override omega to aim.
        return new DriveSignal(base.axial, base.lateral, omega);
    }
}

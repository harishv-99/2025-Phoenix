package edu.ftcphoenix.robots.phoenix.subsystem;

import com.qualcomm.robotcore.hardware.HardwareMap;

import edu.ftcphoenix.fw.drive.DriveSignal;
import edu.ftcphoenix.fw.drive.DriveSource;
import edu.ftcphoenix.fw.drive.Drives;
import edu.ftcphoenix.fw.drive.MecanumDrivebase;
import edu.ftcphoenix.fw.drive.source.StickDriveSource;
import edu.ftcphoenix.fw.input.DriverKit;
import edu.ftcphoenix.fw.robot.Subsystem;
import edu.ftcphoenix.fw.sensing.TagAim;
import edu.ftcphoenix.fw.util.LoopClock;

/**
 * DriveSubsystem: mecanum drive with optional tag-based auto-aim.
 *
 * <h2>Role</h2>
 * <p>This subsystem owns:
 * <ul>
 *   <li>The mecanum {@link MecanumDrivebase}.</li>
 *   <li>The mapping from {@link DriverKit} inputs to {@link DriveSignal}
 *       (via {@link StickDriveSource}).</li>
 *   <li>Optional AprilTag-based auto-aim (via {@link TagAim}).</li>
 * </ul>
 *
 * <p>In TeleOp:
 * <ul>
 *   <li>Drivers move the robot with the sticks as usual.</li>
 *   <li>Holding the aim button (default: {@code p1.leftBumper}) causes the
 *       robot to auto-aim its heading towards one of the configured tags
 *       from {@link VisionSubsystem}.</li>
 * </ul>
 *
 * <p>Only the robot's turn rate ({@code omega}) is modified by aiming.
 * Axial and lateral motion remain under driver control.
 */
public final class DriveSubsystem implements Subsystem {

    private final MecanumDrivebase drivebase;
    private final DriveSource driveSource;

    /**
     * Construct the drive subsystem.
     *
     * @param hw        FTC hardware map
     * @param driverKit driver input helpers
     * @param vision    vision subsystem providing tag sensor and IDs
     */
    public DriveSubsystem(HardwareMap hw,
                          DriverKit driverKit,
                          VisionSubsystem vision) {

        // 1) Build mecanum drivebase from motor names
        this.drivebase = Drives
                .mecanum(hw)
                .frontLeft("fl")
                .frontRight("fr")
                .backLeft("bl")
                .backRight("br")
                .invertRightSide()     // typical wiring; adjust if needed
                .build();

        // 2) Base drive from sticks with slow mode (right bumper)
        StickDriveSource sticks =
                StickDriveSource.defaultMecanumWithSlowMode(
                        driverKit,
                        driverKit.p1().rightBumper(), // hold for slow/precision driving
                        0.30
                );

        // 3) Wrap with tag-aim when left bumper is held
        //    - axial/lateral still come from sticks
        //    - omega is overridden while aiming
        this.driveSource = TagAim.forTeleOp(
                sticks,
                driverKit.p1().leftBumper(),       // hold to aim at tag
                vision.getTagSensor(),
                vision.getScoringTagIds()
        );
    }

    // ------------------------------------------------------------------------
    // Subsystem lifecycle
    // ------------------------------------------------------------------------

    @Override
    public void onTeleopInit() {
        // Nothing special yet; drivebase is ready to use.
    }

    @Override
    public void onTeleopLoop(LoopClock clock) {
        // LoopClock is the single source of timing information.
        DriveSignal cmd = driveSource.get(clock);
        drivebase.drive(cmd);
    }

    @Override
    public void onAutoInit() {
        // Optional: reset encoders or heading before autonomous.
    }

    @Override
    public void onAutoLoop(LoopClock clock) {
        // For now, this can be left empty or used for simple autonomous drive.
        // More advanced autos will likely build Tasks that interact with
        // the drivebase directly or via higher-level planners.
    }

    @Override
    public void onStop() {
        drivebase.stop();
    }
}

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
 * P1 drive subsystem: mecanum + TagAim.
 * <p>
 * Controls:
 * - P1 sticks: drive
 * - P1 right bumper: slow mode
 * - P1 left bumper: face scoring AprilTag
 */
public final class DriveSubsystem implements Subsystem {

    private final MecanumDrivebase drivebase;
    private final DriveSource driveSource;

    public DriveSubsystem(HardwareMap hw,
                          DriverKit driverKit,
                          VisionSubsystem vision) {

        this.drivebase = Drives
                .mecanum(hw)
                .frontLeft("fl")    // TODO: match your config names
                .frontRight("fr")
                .backLeft("bl")
                .backRight("br")
                .invertRightSide()
                .build();

        StickDriveSource sticks =
                StickDriveSource.defaultMecanumWithSlowMode(
                        driverKit,
                        driverKit.p1().rightBumper(), // hold for slow mode
                        0.30);

        // Beginner TagAim API: AprilTagSensor directly.
        this.driveSource = TagAim.forTeleOp(
                sticks,
                driverKit.p1().leftBumper(),   // hold to aim at tag
                vision.sensor(),
                vision.getScoringTagIds());
    }

    @Override
    public void onTeleopLoop(LoopClock clock) {
        DriveSignal cmd = driveSource.get(clock);
        drivebase.drive(cmd);
    }

    @Override
    public void onStop() {
        drivebase.stop();
    }
}

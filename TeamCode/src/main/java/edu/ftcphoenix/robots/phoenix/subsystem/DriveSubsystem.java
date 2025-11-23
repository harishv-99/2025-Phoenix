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
import edu.ftcphoenix.fw.util.DebugSink;
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

    // --- Hardware names (edit to match your config) ---
    private static final String HW_FL = "frontLeftMotor";
    private static final String HW_FR = "frontRightMotor";
    private static final String HW_BL = "backLeftMotor";
    private static final String HW_BR = "backRightMotor";

    // --- Driving parameters ---
    private static final double MULT_SLOWDOWN = 0.3;

    private final MecanumDrivebase drivebase;
    private final DriveSource driveSource;
    StickDriveSource stickSource;

    public DriveSubsystem(HardwareMap hw,
                          DriverKit driverKit,
                          VisionSubsystem vision) {

        this.drivebase = Drives
                .mecanum(hw)
                .frontLeft(HW_FL)
                .frontRight(HW_FR)
                .backLeft(HW_BL)
                .backRight(HW_BR)
                .invertRightSide()
                .invertFrontLeft()
                .build();

        StickDriveSource sticks =
                StickDriveSource.teleOpMecanumWithSlowMode(
                        driverKit,
                        driverKit.p1().rightBumper(), // hold for slow mode
                        MULT_SLOWDOWN);
        this.stickSource = sticks;

        this.driveSource = sticks;

//        // Beginner TagAim API: AprilTagSensor directly.
//        this.driveSource = TagAim.teleOpAim(
//                sticks,
//                driverKit.p1().leftBumper(),   // hold to aim at tag
//                vision.sensor(),
//                vision.getScoringTagIds());
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

    public void debugDump(DebugSink dbg, String prefix) {
        stickSource.debugDump(dbg, "stick");
        drivebase.debugDump(dbg, "drivebase");
    }
}

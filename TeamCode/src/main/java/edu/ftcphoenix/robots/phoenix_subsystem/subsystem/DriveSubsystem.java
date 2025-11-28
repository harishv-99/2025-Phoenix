package edu.ftcphoenix.robots.phoenix_subsystem.subsystem;

import com.qualcomm.robotcore.hardware.HardwareMap;

import edu.ftcphoenix.fw.debug.DebugSink;
import edu.ftcphoenix.fw.drive.DriveSignal;
import edu.ftcphoenix.fw.drive.DriveSource;
import edu.ftcphoenix.fw.drive.Drives;
import edu.ftcphoenix.fw.drive.MecanumConfig;
import edu.ftcphoenix.fw.drive.MecanumDrivebase;
import edu.ftcphoenix.fw.drive.source.StickConfig;
import edu.ftcphoenix.fw.drive.source.StickDriveSource;
import edu.ftcphoenix.fw.input.Gamepads;
import edu.ftcphoenix.fw.robot.Subsystem;
import edu.ftcphoenix.fw.util.LoopClock;

/**
 * P1 drive subsystem: mecanum + TagAim.
 * <p>
 * Controls:
 * - P1 sticks: drive
 * - P1 right bumper: slow mode
 * - P1 left bumper: face scoring AprilTag (if TagAim is enabled)
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
    private DriveSignal lastCommand = DriveSignal.ZERO;

    public DriveSubsystem(HardwareMap hw,
                          Gamepads gamepads,
                          VisionSubsystem vision) {

        // Configure drive behavior (can stay default for this older example).
        MecanumConfig driveCfg = MecanumConfig.defaults();

        // Match old builder semantics:
        //   .invertRightSide().invertFrontLeft()
        // → FL, FR, BR inverted; BL not inverted.
        this.drivebase = Drives.mecanum(
                hw,
                /* invertFrontLeft  */ true,
                /* invertFrontRight */ true,
                /* invertBackLeft   */ false,
                /* invertBackRight  */ true,
                driveCfg
        );

        // Stick drive with custom slow-mode scale.
        StickConfig stickCfg = StickConfig.defaults();
        DriveSource sticks = StickDriveSource.teleOpMecanum(
                gamepads,
                stickCfg,
                gamepads.p1().rightBumper(), // hold for slow mode
                MULT_SLOWDOWN
        );

        this.driveSource = sticks;

        // Old TagAim usage left here as a commented reference; you can re-enable
        // it if you want this subsystem to auto-aim as well.
        //
        // this.driveSource = TagAim.teleOpAim(
        //         sticks,
        //         gamepads.p1().leftBumper(),   // hold to aim at tag
        //         vision.sensor(),
        //         vision.getScoringTagIds());
    }

    @Override
    public void onTeleopLoop(LoopClock clock) {
        DriveSignal cmd = driveSource.get(clock);
        lastCommand = cmd;
        drivebase.drive(cmd);
        // Note: we do not call drivebase.update(clock) here on purpose; this
        // older example effectively runs with rate limiting disabled (dtSec=0).
    }

    @Override
    public void onStop() {
        drivebase.stop();
    }

    public void debugDump(DebugSink dbg, String prefix) {
        String p = (prefix == null || prefix.isEmpty()) ? "drive" : prefix;

        // High-level command
        dbg.addData(p + ".cmd.axial", lastCommand.axial)
                .addData(p + ".cmd.lateral", lastCommand.lateral)
                .addData(p + ".cmd.omega", lastCommand.omega);

        // Delegate to deeper layers if you want more detail
        if (driveSource instanceof StickDriveSource) {
            StickDriveSource sds = (StickDriveSource) driveSource;
            sds.debugDump(dbg, p + ".sticks");
        }
        drivebase.debugDump(dbg, p + ".base");
    }
}

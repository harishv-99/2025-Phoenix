package edu.ftcphoenix.robots.phoenix;

import com.qualcomm.robotcore.hardware.HardwareMap;

import org.firstinspires.ftc.robotcore.external.Telemetry;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import edu.ftcphoenix.fw.input.DriverKit;
import edu.ftcphoenix.fw.robot.Subsystem;
import edu.ftcphoenix.fw.util.LoopClock;
import edu.ftcphoenix.robots.phoenix.subsystem.DriveSubsystem;
import edu.ftcphoenix.robots.phoenix.subsystem.ShooterSubsystem;
import edu.ftcphoenix.robots.phoenix.subsystem.VisionSubsystem;

/**
 * Season-specific robot class that owns subsystems.
 * <p>
 * Thin OpMode shells create this and call the lifecycle methods.
 */
public final class PhoenixRobot {

    private final DriverKit driverKit;
    private final Telemetry telemetry;

    private final DriveSubsystem drive;
    private final VisionSubsystem vision;
    private final ShooterSubsystem shooter;

    private final List<Subsystem> subsystems = new ArrayList<>();

    public PhoenixRobot(HardwareMap hw,
                        DriverKit driverKit,
                        Telemetry telemetry) {
        this.driverKit = driverKit;
        this.telemetry = telemetry;

        // Vision first so others can depend on it
        vision = new VisionSubsystem(
                hw,
                telemetry,
                Set.of(1, 2, 3)); // TODO: scoring tag IDs for this game

        drive = new DriveSubsystem(hw, driverKit, vision);
        shooter = new ShooterSubsystem(hw, driverKit, telemetry, vision);

        subsystems.add(drive);
        subsystems.add(shooter);
        subsystems.add(vision);
    }

    public void onTeleopInit() {
        for (Subsystem s : subsystems) {
            s.onTeleopInit();
        }
    }

    public void onTeleopLoop(LoopClock clock) {
        for (Subsystem s : subsystems) {
            s.onTeleopLoop(clock);
        }
        telemetry.update();
    }

    public void onAutoInit() {
        for (Subsystem s : subsystems) {
            s.onAutoInit();
        }
    }

    public void onAutoLoop(LoopClock clock) {
        for (Subsystem s : subsystems) {
            s.onAutoLoop(clock);
        }
        telemetry.update();
    }

    public void onStop() {
        for (Subsystem s : subsystems) {
            s.onStop();
        }
    }
}

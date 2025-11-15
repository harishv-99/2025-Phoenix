package edu.ftcphoenix.robots.phoenix.subsystem;

import com.qualcomm.robotcore.hardware.HardwareMap;

import org.firstinspires.ftc.robotcore.external.Telemetry;

import edu.ftcphoenix.fw.adapters.ftc.FtcHardware;
import edu.ftcphoenix.fw.hal.MotorOutput;
import edu.ftcphoenix.fw.input.DriverKit;
import edu.ftcphoenix.fw.robot.Subsystem;
import edu.ftcphoenix.fw.util.LoopClock;

/**
 * ShooterSubsystem: two motors controlled by buttons.
 *
 * <p>Beginner-friendly open-loop shooter: press A for low power, B for full power.
 */
public final class ShooterSubsystem implements Subsystem {

    private final Telemetry telemetry;
    private final MotorOutput left;
    private final MotorOutput right;
    private final DriverKit.Player gunner;

    private double lastPower = 0.0;

    public ShooterSubsystem(HardwareMap hw,
                            DriverKit driverKit,
                            Telemetry telemetry) {
        this.telemetry = telemetry;
        this.gunner = driverKit.p2(); // or p1 if single driver

        this.left = FtcHardware.motor(hw, "shooterLeft", false);
        this.right = FtcHardware.motor(hw, "shooterRight", true); // invert if needed
    }

    @Override
    public void onTeleopInit() {
        setPower(0.0);
    }

    @Override
    public void onTeleopLoop(LoopClock clock) {
        double power = 0.0;
        if (gunner.a().isPressed()) {
            power = 0.5;
        } else if (gunner.b().isPressed()) {
            power = 1.0;
        }
        setPower(power);

        telemetry.addData("Shooter power", "%.2f", lastPower);
    }

    @Override
    public void onAutoInit() {
        setPower(0.0);
    }

    @Override
    public void onAutoLoop(LoopClock clock) {
        // Later: add auto shooter behavior (spin up before shooting, etc.).
    }

    @Override
    public void onStop() {
        setPower(0.0);
    }

    private void setPower(double power) {
        lastPower = power;
        left.setPower(power);
        right.setPower(power);
    }
}

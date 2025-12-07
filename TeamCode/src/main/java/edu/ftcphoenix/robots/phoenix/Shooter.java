package edu.ftcphoenix.robots.phoenix;

import com.qualcomm.robotcore.hardware.HardwareMap;

import org.firstinspires.ftc.robotcore.external.Telemetry;

import edu.ftcphoenix.fw.actuation.Actuators;
import edu.ftcphoenix.fw.actuation.Plant;
import edu.ftcphoenix.fw.actuation.PlantTasks;
import edu.ftcphoenix.fw.input.Gamepads;
import edu.ftcphoenix.fw.task.Task;
import edu.ftcphoenix.fw.task.Tasks;
import edu.ftcphoenix.fw.util.MathUtil;

public class Shooter {

    public enum TransferDirection {
        FORWARD,
        BACKWARD
    }

    private Plant plantPusher;
    private Plant plantTransfer;
    private Plant plantShooter;

    private double velocity;
    private boolean isShooterOn;

    public Shooter(HardwareMap hardwareMap, Telemetry telemetry, Gamepads gamepads) {
        plantPusher = Actuators.plant(hardwareMap)
                .servo(RobotConfig.Shooter.nameServoPusher,
                        RobotConfig.Shooter.invertServoPusher)
                .position()
                .build();

        plantTransfer = Actuators.plant(hardwareMap)
                .crServoPair(RobotConfig.Shooter.nameCrServoTransferLeft,
                        RobotConfig.Shooter.invertServoTransferLeft,
                        RobotConfig.Shooter.nameCrServoTransferRight,
                        RobotConfig.Shooter.invertServoTransferRight)
                .power()
                .build();

        plantShooter = Actuators.plant(hardwareMap)
                .motorPair(RobotConfig.Shooter.nameMotorShooterLeft,
                        RobotConfig.Shooter.invertMotorShooterLeft,
                        RobotConfig.Shooter.nameMotorShooterRight,
                        RobotConfig.Shooter.invertMotorShooterRight)
                .velocity(100)
                .build();

        isShooterOn = false;
        velocity = RobotConfig.Shooter.velocityMin;
    }

    public Task instantIncreaseVelocity() {
        velocity += RobotConfig.Shooter.velocityIncrement;
        velocity = MathUtil.clamp(velocity,
                RobotConfig.Shooter.velocityMin,
                RobotConfig.Shooter.velocityMax);

        if (isShooterOn) {
            return instantStartShooter();
        }

        return Tasks.noop();
    }

    public Task instantDecreaseVelocity() {
        velocity -= RobotConfig.Shooter.velocityIncrement;
        velocity = MathUtil.clamp(velocity,
                RobotConfig.Shooter.velocityMin,
                RobotConfig.Shooter.velocityMax);

        if (isShooterOn) {
            return instantStartShooter();
        }

        return Tasks.noop();
    }

    public double getVelocity() {
        return velocity;
    }

    public Task instantStartShooter() {
        isShooterOn = true;
        return PlantTasks.setInstant(plantShooter, velocity);
    }

    public Task instantStopShooter() {
        isShooterOn = false;
        return PlantTasks.setInstant(plantShooter, 0);
    }

    public Task instantSetPusherBack() {
//        return PlantTasks.holdFor(plantPusher,
//                RobotConfig.Shooter.targetPusherBack,
//                0.5);
        return PlantTasks.setInstant(plantPusher,
                RobotConfig.Shooter.targetPusherBack);
    }

    public Task instantSetPusherFront() {
//        return PlantTasks.holdFor(plantPusher,
//                RobotConfig.Shooter.targetPusherFront,
//                0.5);
        return PlantTasks.setInstant(plantPusher,
                RobotConfig.Shooter.targetPusherFront);
    }

    public Task instantStartTransfer(TransferDirection direction) {
        switch (direction) {
            case FORWARD:
                return PlantTasks.setInstant(plantTransfer, 1);
            case BACKWARD:
                return PlantTasks.setInstant(plantTransfer, -1);
        }

        throw new IllegalArgumentException("Unknown direction provided!!!");
    }

    public Task instantStopTransfer() {
        return PlantTasks.setInstant(plantTransfer, 0);
    }
}

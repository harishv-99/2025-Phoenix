package edu.ftcphoenix.robots.phoenix.subsystems;

import static edu.ftcphoenix.robots.phoenix.Constants.*;

import androidx.annotation.NonNull;

import com.acmerobotics.dashboard.telemetry.TelemetryPacket;
import com.acmerobotics.roadrunner.Action;
import com.qualcomm.robotcore.hardware.CRServo;
import com.qualcomm.robotcore.hardware.DcMotor;
import com.qualcomm.robotcore.hardware.DcMotorEx;
import com.qualcomm.robotcore.hardware.HardwareMap;

import org.firstinspires.ftc.robotcore.external.Telemetry;

import edu.ftcphoenix.fw.util.ElapsedTimeMillis;

public class ArmSubsystem {
    private final DcMotorEx armRaiserMotor;
    private final CRServo rollerIntake;
    private final DcMotorEx slideMotor;
    private final CRServo armExtender;
    private final Telemetry telemetry;

    public ArmSubsystem(HardwareMap hardwareMap, Telemetry telemetry) {
        this.telemetry = telemetry;

        armRaiserMotor = hardwareMap.get(DcMotorEx.class, MOTOR_NAME_ARM_RAISER);
        armRaiserMotor.setDirection(DcMotor.Direction.REVERSE);
        armRaiserMotor.setMode(DcMotor.RunMode.STOP_AND_RESET_ENCODER);

        rollerIntake = hardwareMap.get(CRServo.class, SERVO_NAME_INTAKE);
        rollerIntake.setDirection(CRServo.Direction.REVERSE);

        slideMotor = hardwareMap.get(DcMotorEx.class, MOTOR_NAME_SLIDE);
        slideMotor.setDirection(DcMotor.Direction.FORWARD);
        slideMotor.setMode(DcMotor.RunMode.STOP_AND_RESET_ENCODER);

        armExtender = hardwareMap.get(CRServo.class, SERVO_NAME_EXTENDER);
        armExtender.setDirection(CRServo.Direction.FORWARD);
    }

    public Telemetry getTelemetry() {
        return telemetry;
    }

    public int getArmRaiserPosition() {
        return armRaiserMotor.getCurrentPosition();
    }

    public void moveArmRaiser(int position) {
        armRaiserMotor.setTargetPosition(position);
        armRaiserMotor.setMode(DcMotor.RunMode.RUN_TO_POSITION);
        armRaiserMotor.setPower(0.5);
    }



    public Action getMoveArmRaiserAction(int position) {
        return new MoveArmRaiserAction(this, position);
    }

    public void moveRollerIntake(double power) {
        // set power
        rollerIntake.setPower(power);
    }
    
    public void stopRollerIntake() {
        rollerIntake.setPower(0);
    }

    public void moveRollerIntake(double power, int milliSeconds) {
        moveRollerIntake(power);
        try {
            Thread.sleep(milliSeconds);
        } catch (InterruptedException e) {
//            throw new RuntimeException(e);
        }
        stopRollerIntake();
    }

    public Action getMoveRollerIntakeAction(double power, int milliseconds) {
        return new MoveRollerIntakeAction(this, power, milliseconds);
    }

    public void moveSlides(int position) {
        slideMotor.setTargetPosition(position);
        slideMotor.setMode(DcMotor.RunMode.RUN_TO_POSITION);
        slideMotor.setPower(1);
    }

    public int getSlidesPosition() {
        return slideMotor.getCurrentPosition();
    }

    public Action getMoveSlidesAction(int position) {
        return new MoveSlidesAction(this, position);
    }


    public void moveArmExtender(double power) {
        telemetry.addData("Ext", "startMove");
        telemetry.update();
        armExtender.setPower(power);
    }

    public void stopArmExtender() {
        telemetry.addData("Ext", "stop");
        telemetry.update();
        armExtender.setPower(0);
    }

    public void moveArmExtender(double power, int milliSeconds) {
        moveArmExtender(power);
        try {
            Thread.sleep(milliSeconds);
        } catch (InterruptedException e) {
//            throw new RuntimeException(e);
        }
        stopArmExtender();
    }

    public Action getArmExtenderAction(double power, int milliseconds) {
        return new MoveArmExtenderAction(this, power, milliseconds);
    }
}

class MoveArmExtenderAction implements Action {
    private final double power;
    private final int milliSeconds;
    private final ArmSubsystem armSubsystem;
    private boolean initialized = false;

    private ElapsedTimeMillis elapsedTimeMillis;

    public MoveArmExtenderAction(ArmSubsystem armSubsystem, double power, int milliSeconds) {
        this.power = power;
        this.milliSeconds = milliSeconds;
        this.armSubsystem = armSubsystem;
    }

    @Override
    public boolean run(@NonNull TelemetryPacket packet) {
        if (!initialized) {
            armSubsystem.moveArmExtender(power);
            initialized = true;
            elapsedTimeMillis = new ElapsedTimeMillis();
        }
        boolean bFinishedAction = elapsedTimeMillis.getElapsedMilliseconds() >= milliSeconds;
        if (bFinishedAction) {
            armSubsystem.stopArmExtender();
        }
        return !bFinishedAction;
    }
}
class MoveRollerIntakeAction implements Action {
    private final double power;
    private final int milliSeconds;
    private final ArmSubsystem armSubsystem;
    private boolean initialized = false;

    private ElapsedTimeMillis elapsedTimeMillis;

    public MoveRollerIntakeAction(ArmSubsystem armSubsystem, double power, int milliSeconds) {
        this.power = power;
        this.milliSeconds = milliSeconds;
        this.armSubsystem = armSubsystem;
    }

    @Override
    public boolean run(@NonNull TelemetryPacket packet) {
        if (!initialized) {
            armSubsystem.moveRollerIntake(power);
            initialized = true;
            elapsedTimeMillis = new ElapsedTimeMillis();
        }
        boolean bFinishedAction = elapsedTimeMillis.getElapsedMilliseconds() >= milliSeconds;
        if (bFinishedAction) {
            armSubsystem.stopRollerIntake();
        }
        return !bFinishedAction;
    }
}

class MoveSlidesAction implements Action {

    private final int targetPosition;
    private int startPosition;
    private final ArmSubsystem armSubsystem;
    private boolean initialized = false;

    public MoveSlidesAction(ArmSubsystem armSubsystem, int targetPosition) {
        this.targetPosition = targetPosition;
        this.armSubsystem = armSubsystem;
    }

    @Override
    public boolean run(@NonNull TelemetryPacket packet) {
        if (!initialized) {
            startPosition = armSubsystem.getSlidesPosition();
            armSubsystem.moveSlides(targetPosition);
            initialized = true;
        }
        armSubsystem.getTelemetry().addLine("pos: " + armSubsystem.getSlidesPosition());
        armSubsystem.getTelemetry().update();
        if (startPosition < targetPosition) {
            armSubsystem.getTelemetry().addLine("S < T: " + (armSubsystem.getSlidesPosition() <= (targetPosition - MOTOR_ERROR_THRESHOLD)));
            armSubsystem.getTelemetry().update();
            return armSubsystem.getSlidesPosition() <= (targetPosition - MOTOR_ERROR_THRESHOLD);
        }
        else {
            armSubsystem.getTelemetry().addLine("S >= T: " + (armSubsystem.getSlidesPosition() >= (targetPosition + MOTOR_ERROR_THRESHOLD)));
            armSubsystem.getTelemetry().update();
            return armSubsystem.getSlidesPosition() >= (targetPosition + MOTOR_ERROR_THRESHOLD);
        }
    }

}

class MoveArmRaiserAction implements Action {
    private final int targetPosition;
    private final ArmSubsystem armSubsystem;
    private boolean initialized = false;

    public MoveArmRaiserAction(ArmSubsystem armSubsystem, int targetPosition) {
        this.targetPosition = targetPosition;
        this.armSubsystem = armSubsystem;
    }

    @Override
    public boolean run(@NonNull TelemetryPacket packet) {
        if (!initialized) {
            armSubsystem.moveArmRaiser(targetPosition);
            initialized = true;
        }
        return Math.abs(targetPosition - armSubsystem.getArmRaiserPosition()) >= MOTOR_ERROR_THRESHOLD;
    }
}

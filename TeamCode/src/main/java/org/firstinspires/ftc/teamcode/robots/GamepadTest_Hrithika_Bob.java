package org.firstinspires.ftc.teamcode.robots;

import com.qualcomm.robotcore.eventloop.opmode.LinearOpMode;
import com.qualcomm.robotcore.eventloop.opmode.TeleOp;
import com.qualcomm.robotcore.hardware.CRServo;
import com.qualcomm.robotcore.hardware.DcMotor;
import com.qualcomm.robotcore.hardware.Servo;

import org.firstinspires.ftc.robotcore.external.JavaUtil;

import edu.ftcphoenix.fw.robotbase.RobotBase;
import edu.ftcphoenix.robots.phoenix.Robot;

@TeleOp(name = "GameDayCode_Hrihtika_Bob")
public class GamepadTest_Hrithika_Bob extends LinearOpMode {
    Robot robot;

    public void runOpMode() {
        robot = new Robot(this, RobotBase.OpModeType.TELEOP,
                RobotBase.AllianceColor.RED,
                RobotBase.StartPosition.AWAY_FROM_AUDIENCE);
        robot.runOpMode();
    }
}


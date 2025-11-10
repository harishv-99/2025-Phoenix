package org.firstinspires.ftc.teamcode.robots;

import com.qualcomm.robotcore.eventloop.opmode.LinearOpMode;
import com.qualcomm.robotcore.eventloop.opmode.TeleOp;

import edu.ftcphoenix.fw2.robotbase.RobotBase;
import edu.ftcphoenix.robots.phoenix2.Robot;

@TeleOp(name = "GameDayCode_Hrithika_Bob")
public class GameDayCode_Hrithika_Bob extends LinearOpMode {
    Robot robot;

    public void runOpMode() {
        robot = new Robot(this, RobotBase.OpModeType.TELEOP,
                RobotBase.AllianceColor.BLUE,
                RobotBase.StartPosition.AWAY_FROM_AUDIENCE);
        robot.runOpMode();
    }
}


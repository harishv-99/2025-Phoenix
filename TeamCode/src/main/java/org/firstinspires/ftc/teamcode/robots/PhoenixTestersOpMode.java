package org.firstinspires.ftc.teamcode.robots;

import com.qualcomm.robotcore.eventloop.opmode.OpMode;
import com.qualcomm.robotcore.eventloop.opmode.TeleOp;

import edu.ftcphoenix.fw.tester.StandardTesters;
import edu.ftcphoenix.fw.tester.TesterContext;
import edu.ftcphoenix.fw.tester.TesterSuite;
import edu.ftcphoenix.fw.util.LoopClock;
import edu.ftcphoenix.robots.phoenix.tester.PhoenixRobotTesters;

/**
 * Driver Hub entry point for Phoenix robot testers.
 *
 * <p>This OpMode runs a single {@link TesterSuite} that contains:
 * <ul>
 *   <li>Framework-standard testers (via {@link StandardTesters})</li>
 *   <li>Phoenix robot-specific testers (via {@link PhoenixRobotTesters})</li>
 * </ul>
 *
 * <p>Keeping this as ONE OpMode avoids cluttering the Driver Hub menu.</p>
 */
@TeleOp(name = "Phoenix Testers", group = "Phoenix")
public final class PhoenixTestersOpMode extends OpMode {

    private final LoopClock clock = new LoopClock();

    private TesterSuite suite;
    private TesterContext ctx;

    @Override
    public void init() {
        ctx = new TesterContext(hardwareMap, telemetry, gamepad1, gamepad2, clock);

        suite = StandardTesters.createSuite();
        PhoenixRobotTesters.register(suite);

        suite.init(ctx);

        clock.reset(getRuntime());
    }

    @Override
    public void init_loop() {
        clock.update(getRuntime());
        suite.initLoop(clock.dtSec());
    }

    @Override
    public void start() {
        clock.reset(getRuntime());
        suite.start();
    }

    @Override
    public void loop() {
        clock.update(getRuntime());
        suite.loop(clock.dtSec());
    }

    @Override
    public void stop() {
        suite.stop();
    }
}

package edu.ftcphoenix.robots.phoenix.tester;

import edu.ftcphoenix.fw.tester.TesterSuite;

/**
 * Registers robot-specific testers for the Phoenix robot.
 *
 * <p>Use this from your robot-side tester OpMode after you register the framework-standard testers:
 * <pre>{@code
 * TesterSuite suite = StandardTesters.createSuite();
 * PhoenixRobotTesters.register(suite);
 * }</pre>
 *
 * <p>This keeps robot-specific testers in one place, and avoids polluting the Driver Hub menu
 * with many separate OpModes.</p>
 */
public final class PhoenixRobotTesters {

    private PhoenixRobotTesters() {
    }

    /**
     * Add Phoenix robot-specific testers to the given suite.
     */
    public static void register(TesterSuite suite) {
        if (suite == null) return;

        DrivetrainMotorDirectionTester.register(suite);

        // Add future robot-specific testers here, e.g.:
        // IntakeTester.register(suite);
        // LiftCalibrationTester.register(suite);
    }
}

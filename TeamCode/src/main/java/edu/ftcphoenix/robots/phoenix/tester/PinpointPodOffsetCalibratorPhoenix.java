package edu.ftcphoenix.robots.phoenix.tester;

import edu.ftcphoenix.fw.drive.Drives;
import edu.ftcphoenix.fw.tools.tester.TesterSuite;
import edu.ftcphoenix.fw.tools.tester.calibration.PinpointPodOffsetCalibrator;
import edu.ftcphoenix.robots.phoenix.RobotConfig;

/**
 * Phoenix robot-specific wrapper for {@link PinpointPodOffsetCalibrator}.
 */
public final class PinpointPodOffsetCalibratorPhoenix {

    private PinpointPodOffsetCalibratorPhoenix() {
    }

    /**
     * Registers the Pinpoint pod-offset calibrator into the Phoenix tester suite.
     */
    public static void register(TesterSuite suite) {
        suite.add(
                "Robot: Calib (Pinpoint Pod Offsets)",
                "Calibrate Pinpoint odometry pod offsets by rotating in place. Optional AprilTag assist.",
                () -> {
                    PinpointPodOffsetCalibrator.Config cfg = PinpointPodOffsetCalibrator.Config.defaults();
                    cfg.pinpoint = RobotConfig.Localization.pinpoint;

                    // Provide drivetrain wiring so the calibrator can rotate the robot.
                    cfg.mecanumWiring = RobotConfig.DriveTrain.mecanumWiring();

                    // Enable AprilTag assist by default (it still works if no tags are visible).
                    cfg.useAprilTagsAssist = true;
                    cfg.preferredCameraName = RobotConfig.Vision.nameWebcam;
                    cfg.cameraMount = RobotConfig.Vision.cameraMount;

                    return new PinpointPodOffsetCalibrator(cfg);
                }
        );
    }
}

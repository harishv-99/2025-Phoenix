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
        if (suite == null) return;

        boolean canUseAssist = RobotConfig.Calibration.canUseAprilTagAssist();
        String assistStatus = canUseAssist
                ? "AprilTag assist: AUTO-ENABLED (camera mount OK)"
                : "AprilTag assist: DISABLED (calibrate camera mount first)";

        String offsetsStatus = RobotConfig.Calibration.pinpointPodOffsetsCalibrated
                ? "offsets: OK"
                : "offsets: NOT CALIBRATED";

        suite.add(
                "Calib: Pinpoint Pod Offsets (Robot)",
                "Rotate in place to estimate pod offsets; " + assistStatus + "; " + offsetsStatus,
                () -> {
                    PinpointPodOffsetCalibrator.Config cfg = PinpointPodOffsetCalibrator.Config.defaults();
                    cfg.pinpoint = RobotConfig.Localization.pinpoint;

                    // Provide drivetrain wiring so the calibrator can rotate the robot.
                    cfg.mecanumWiring = RobotConfig.DriveTrain.mecanumWiring();

                    // Use a full turn for better signal-to-noise.
                    cfg.targetTurnRad = 2.0 * Math.PI;

                    // AprilTag assist is optional. Enable automatically once camera mount is calibrated.
                    cfg.enableAprilTagAssist = canUseAssist;
                    cfg.preferredCameraName = RobotConfig.Vision.nameWebcam;
                    cfg.cameraMount = RobotConfig.Vision.cameraMount;

                    return new PinpointPodOffsetCalibrator(cfg);
                }
        );
    }
}

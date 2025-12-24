package edu.ftcphoenix.robots.phoenix.tester;

import edu.ftcphoenix.fw.ftc.localization.PinpointPoseEstimator;
import edu.ftcphoenix.fw.tools.tester.TesterSuite;
import edu.ftcphoenix.fw.tools.tester.localization.PinpointAprilTagFusionLocalizationTester;
import edu.ftcphoenix.robots.phoenix.RobotConfig;

/**
 * Phoenix robot-specific wrapper for {@link PinpointAprilTagFusionLocalizationTester}.
 */
public final class PinpointAprilTagFusionLocalizationTesterPhoenix {

    private PinpointAprilTagFusionLocalizationTesterPhoenix() {
    }

    public static void register(TesterSuite suite) {
        if (suite == null) return;

        suite.add(
                "Robot: Loc (Pinpoint + AprilTag Fusion)",
                "Fused global pose from Pinpoint odometry with AprilTag corrections.",
                () -> {
                    PinpointPoseEstimator.Config cfg = RobotConfig.Localization.pinpoint;

                    return new PinpointAprilTagFusionLocalizationTester(
                            RobotConfig.Vision.nameWebcam,
                            RobotConfig.Vision.cameraMount,
                            cfg
                    );
                }
        );
    }
}

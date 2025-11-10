package edu.ftcphoenix.robots.phoenix.pose;

import edu.ftcphoenix.fw.stages.gripperarm.GripperArmStage;

/**
 * Maps lane semantics to arm poses for the Phoenix robot.
 *
 * <p>Keep numbers here; the stage stays hardware-agnostic.</p>
 */
public final class PhoenixArmPoseLib implements GripperArmStage.PoseLibrary<PhoenixPose> {

    // Example targets (replace with tuned values)
    private static final PhoenixPose PICKUP = new PhoenixPose(0.00, 0.85, 0.00);
    private static final PhoenixPose HANDOFF = new PhoenixPose(0.05, 0.60, -0.10);
    private static final PhoenixPose PLACE_L = new PhoenixPose(0.20, 0.30, -0.25);
    private static final PhoenixPose PLACE_R = new PhoenixPose(0.20, 0.30, -0.25);

    @Override
    public PhoenixPose pickupPose() {
        return PICKUP;
    }

    @Override
    public PhoenixPose handoffPose() {
        return HANDOFF;
    }

    @Override
    public PhoenixPose placePose(String laneName) {
        if ("left".equals(laneName)) return PLACE_L;
        if ("right".equals(laneName)) return PLACE_R;
        // default fallback
        return PLACE_L;
    }
}

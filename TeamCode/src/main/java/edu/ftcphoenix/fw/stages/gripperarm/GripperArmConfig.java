package edu.ftcphoenix.fw.stages.gripperarm;

/**
 * Config for {@link GripperArmStage}: pose tolerance and claw pulse timings.
 *
 * <h3>Rationale</h3>
 * Poses can be hit within a small angular tolerance; claw pulses should be precise and
 * non-blocking to avoid timing jitter from the flow.
 */
public final class GripperArmConfig {
    /**
     * Angular tolerance for pose checks (radians).
     */
    public final double tolRad;
    /**
     * Duration to keep claw open when releasing (seconds).
     */
    public final double openPulseSec;
    /**
     * Duration to hold claw closed when grasping (seconds).
     */
    public final double closeHoldSec;

    public GripperArmConfig(double tolRad, double openPulseSec, double closeHoldSec) {
        this.tolRad = tolRad;
        this.openPulseSec = openPulseSec;
        this.closeHoldSec = closeHoldSec;
    }

    public static GripperArmConfig defaults() {
        return new GripperArmConfig(0.06, 0.12, 0.10);
    }
}

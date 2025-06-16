package edu.ftcphoenix.fw.assisteddriving.precondition;

import com.acmerobotics.roadrunner.Pose2d;
import com.acmerobotics.roadrunner.PoseVelocity2d;

/**
 * Precondition object for an adjustor which has an enable toggle.  This can be controlled through other triggers
 * like operator input.
 */
public class ToggleEnablePrecondition implements GuidanceAdjustorPrecondition {

    private boolean bEnabled;

    /**
     * Precondition is met if the object is enabled.
     *
     * @param bEnabled Is the object enabled.
     */
    public ToggleEnablePrecondition(boolean bEnabled) {
        this.bEnabled = bEnabled;
    }

    /**
     * Toggle the enabled state of the object.
     */
    public void toggleEnabled() {
        bEnabled = !bEnabled;
    }

    /**
     * Find out whether the object is enabled.
     *
     * @return Whether the object is enabled.
     */
    public boolean isEnabled() {
        return bEnabled;
    }

    /**
     * Set whether the object is enabled.
     *
     * @param bEnabled Enabled state of the object
     */
    public void setEnabled(boolean bEnabled) {
        this.bEnabled = bEnabled;
    }

    @Override
    public boolean hasMetPrecondition(Pose2d poseRobot, PoseVelocity2d moveProposed_Robot) {
        return bEnabled;
    }
}

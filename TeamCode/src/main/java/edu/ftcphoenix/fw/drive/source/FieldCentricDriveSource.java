package edu.ftcphoenix.fw.drive.source;

import edu.ftcphoenix.fw.drive.DriveSignal;
import edu.ftcphoenix.fw.hal.ImuYaw;
import edu.ftcphoenix.fw.util.LoopClock;

/**
 * Wraps a robot-centric source and rotates its (axial,lateral) into field-frame using IMU yaw.
 * Omega is passed through unchanged.
 */
public final class FieldCentricDriveSource {
    private final StickDriveSource base;
    private final ImuYaw yaw;

    public FieldCentricDriveSource(StickDriveSource base, ImuYaw yaw) {
        this.base = base;
        this.yaw = yaw;
    }

    public DriveSignal sample(LoopClock clock) {
        DriveSignal s = base.sample(clock);
        double c = Math.cos(-yaw.yawRad());
        double sgn = Math.sin(-yaw.yawRad());
        // rotate (axial, lateral)
        double ax = s.axial * c - s.lateral * sgn;
        double lt = s.axial * sgn + s.lateral * c;
        return new DriveSignal(ax, lt, s.omega);
    }
}

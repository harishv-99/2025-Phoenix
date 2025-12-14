package edu.ftcphoenix.fw.sensing;

import edu.ftcphoenix.fw.geom.Pose3d;

/**
 * Immutable snapshot of a single AprilTag observation (sensor measurement), expressed in
 * <b>Phoenix framing</b>.
 *
 * <h2>Principle: core framework uses Phoenix framing only</h2>
 * <p>
 * All core Phoenix framework types (outside {@code edu.ftcphoenix.fw.adapters.*}) operate only in
 * the Phoenix pose convention:
 * </p>
 * <ul>
 *   <li><b>+X</b> forward</li>
 *   <li><b>+Y</b> left</li>
 *   <li><b>+Z</b> up</li>
 * </ul>
 *
 * <p>
 * Any FTC-SDK-specific coordinate conventions must be converted inside the FTC adapter layer
 * before constructing this object.
 * </p>
 *
 * <h2>Phoenix pose naming convention</h2>
 * <p>
 * Whenever a variable or accessor represents a pose/transform, Phoenix code uses:
 * </p>
 * <pre>
 * pFromFrameToToFrame
 * </pre>
 *
 * <p>Examples:</p>
 * <ul>
 *   <li>{@code pRobotToCamera}: camera pose expressed in the robot frame</li>
 *   <li>{@code pCameraToTag}: tag pose expressed in the camera frame</li>
 *   <li>{@code pFieldToRobot}: robot pose expressed in the field frame</li>
 * </ul>
 *
 * <h2>Component naming convention (non-pose values derived from a pose)</h2>
 * <p>
 * For a component derived from a pose's translation, Phoenix uses:
 * </p>
 * <pre>
 * &lt;frame&gt;&lt;Direction&gt;&lt;Unit&gt;()
 * </pre>
 *
 * <p>Examples:</p>
 * <ul>
 *   <li>{@code cameraForwardInches()} is the +X (forward) component of {@code pCameraToTag}.</li>
 *   <li>{@code robotLeftInches()} would be the +Y (left) component of a {@code pRobotToSomething} pose.</li>
 * </ul>
 *
 * <h2>Phoenix camera frame (for pCameraToTag)</h2>
 * <p>
 * Phoenix defines the camera frame using the same axis convention as all Phoenix frames:
 * </p>
 * <ul>
 *   <li><b>+X</b> forward (out of the lens)</li>
 *   <li><b>+Y</b> left</li>
 *   <li><b>+Z</b> up</li>
 * </ul>
 *
 * <h2>Derived aiming helpers</h2>
 * <p>
 * Bearing and range are derived from {@link #pCameraToTag} and are not stored separately to avoid
 * redundancy and drift.
 * </p>
 */
public final class AprilTagObservation {

    /**
     * True if this observation represents a real detected tag.
     */
    public final boolean hasTarget;

    /**
     * AprilTag numeric ID code.
     *
     * <p>Only meaningful when {@link #hasTarget} is true. When {@link #hasTarget} is false, this is -1.</p>
     */
    public final int id;

    /**
     * Age of this observation in seconds.
     *
     * <p>This is the elapsed time between the camera frame that produced this observation and
     * the moment the observation was created.</p>
     */
    public final double ageSec;

    /**
     * Tag pose expressed in the Phoenix camera frame: {@code pCameraToTag}.
     *
     * <p>Non-null when {@link #hasTarget} is true.</p>
     */
    public final Pose3d pCameraToTag;

    /**
     * Optional robot pose expressed in the Phoenix field frame: {@code pFieldToRobot}.
     *
     * <p>If present, this is a field-centric pose measurement source (6DOF). For example, an FTC
     * adapter may populate this from SDK robotPose after converting into Phoenix framing.</p>
     */
    public final Pose3d pFieldToRobot;

    private AprilTagObservation(boolean hasTarget,
                                int id,
                                double ageSec,
                                Pose3d pCameraToTag,
                                Pose3d pFieldToRobot) {
        this.hasTarget = hasTarget;
        this.id = id;
        this.ageSec = ageSec;
        this.pCameraToTag = pCameraToTag;
        this.pFieldToRobot = pFieldToRobot;
    }

    /**
     * Create an observation representing "no target".
     *
     * @param ageSec how long ago the last camera frame was, in seconds
     */
    public static AprilTagObservation noTarget(double ageSec) {
        return new AprilTagObservation(false, -1, ageSec, null, null);
    }

    /**
     * Create an observation representing a detected tag, expressed in Phoenix framing.
     *
     * @param id               AprilTag ID
     * @param pCameraToTagPose tag pose in Phoenix camera frame (non-null)
     * @param ageSec           age of the underlying camera frame (seconds)
     */
    public static AprilTagObservation target(int id, Pose3d pCameraToTagPose, double ageSec) {
        if (pCameraToTagPose == null) {
            throw new IllegalArgumentException("pCameraToTagPose must be non-null when hasTarget is true");
        }
        return new AprilTagObservation(true, id, ageSec, pCameraToTagPose, null);
    }

    /**
     * Create an observation representing a detected tag with an additional field-centric robot pose
     * measurement.
     *
     * @param id                AprilTag ID
     * @param pCameraToTagPose  tag pose in Phoenix camera frame (non-null)
     * @param pFieldToRobotPose robot pose in Phoenix field frame (non-null)
     * @param ageSec            age of the underlying camera frame (seconds)
     */
    public static AprilTagObservation target(int id,
                                             Pose3d pCameraToTagPose,
                                             Pose3d pFieldToRobotPose,
                                             double ageSec) {
        if (pCameraToTagPose == null) {
            throw new IllegalArgumentException("pCameraToTagPose must be non-null when hasTarget is true");
        }
        if (pFieldToRobotPose == null) {
            throw new IllegalArgumentException("pFieldToRobotPose must be non-null when provided");
        }
        return new AprilTagObservation(true, id, ageSec, pCameraToTagPose, pFieldToRobotPose);
    }

    /**
     * Returns true if this observation contains a {@link #pFieldToRobot} measurement.
     */
    public boolean hasPFieldToRobot() {
        return hasTarget && pFieldToRobot != null;
    }

    /**
     * Convenience helper to test whether this observation is considered "fresh enough".
     *
     * @param maxAgeSec maximum acceptable age in seconds
     * @return true if this observation has a target and {@link #ageSec} is <= maxAgeSec
     */
    public boolean isFresh(double maxAgeSec) {
        return hasTarget && ageSec <= maxAgeSec;
    }

    // ---------------------------------------------------------------------------------------------
    // Components derived from pCameraToTag (Phoenix camera frame)
    // ---------------------------------------------------------------------------------------------

    /**
     * Tag forward component in the Phoenix camera frame (+X forward), in inches.
     */
    public double cameraForwardInches() {
        return hasTarget ? pCameraToTag.xInches : 0.0;
    }

    /**
     * Tag left component in the Phoenix camera frame (+Y left), in inches.
     */
    public double cameraLeftInches() {
        return hasTarget ? pCameraToTag.yInches : 0.0;
    }

    /**
     * Tag up component in the Phoenix camera frame (+Z up), in inches.
     */
    public double cameraUpInches() {
        return hasTarget ? pCameraToTag.zInches : 0.0;
    }

    /**
     * Horizontal bearing from camera forward (+X) to the tag center, in radians.
     *
     * <p>Derived from {@link #pCameraToTag} using Phoenix sign convention: positive = left.</p>
     */
    public double cameraBearingRad() {
        if (!hasTarget) {
            return 0.0;
        }
        return Math.atan2(cameraLeftInches(), cameraForwardInches());
    }

    /**
     * 3D line-of-sight distance from camera origin to the tag center, in inches.
     *
     * <p>Derived from {@link #pCameraToTag} translation components.</p>
     */
    public double cameraRangeInches() {
        if (!hasTarget) {
            return 0.0;
        }
        double f = cameraForwardInches();
        double l = cameraLeftInches();
        double u = cameraUpInches();
        return Math.sqrt(f * f + l * l + u * u);
    }

    @Override
    public String toString() {
        if (!hasTarget) {
            return "AprilTagObservation{no target, ageSec=" + ageSec + "}";
        }
        return "AprilTagObservation{"
                + "id=" + id
                + ", ageSec=" + ageSec
                + ", cameraBearingRad=" + cameraBearingRad()
                + ", cameraRangeInches=" + cameraRangeInches()
                + ", hasPFieldToRobot=" + (pFieldToRobot != null)
                + '}';
    }
}

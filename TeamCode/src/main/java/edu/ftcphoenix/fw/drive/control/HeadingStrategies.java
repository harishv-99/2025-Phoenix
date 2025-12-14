package edu.ftcphoenix.fw.drive.control;

import edu.ftcphoenix.fw.field.TagLayout;
import edu.ftcphoenix.fw.field.TagLayout.TagPose;
import edu.ftcphoenix.fw.geom.Pose3d;

/**
 * Factory methods for common {@link HeadingStrategy} behaviors.
 *
 * <p>These helpers choose a <em>desired heading</em> given the current robot pose and a
 * goal pose. They do not perform feedback control; they only choose an angle. Use
 * together with {@link HeadingController} to turn heading error into an angular
 * velocity command.</p>
 *
 * <h2>Coordinate conventions</h2>
 *
 * <ul>
 *   <li>{@link edu.ftcphoenix.fw.geom.Pose2d} headings are measured in radians, CCW-positive from +X.</li>
 *   <li>All returned headings follow that same convention.</li>
 * </ul>
 *
 * <h2>Pose naming conventions</h2>
 *
 * <ul>
 *   <li>{@code fieldRobotPose}: current robot pose expressed in the field frame.</li>
 *   <li>{@code fieldRobotTargetPose}: desired robot pose expressed in the field frame.</li>
 * </ul>
 */
public final class HeadingStrategies {

    private HeadingStrategies() {
        // utility class; do not instantiate
    }

    /**
     * Returns a strategy that always uses the goal pose's heading.
     *
     * <p>This is the simplest option: “drive to the goal position and face the goal heading.”</p>
     */
    public static HeadingStrategy faceFinalHeading() {
        return (fieldRobotPose, fieldRobotTargetPose) -> fieldRobotTargetPose.headingRad;
    }

    /**
     * Returns a strategy that always faces the goal position, ignoring the goal's heading.
     *
     * <p>The desired heading is the angle from the robot's current position to the goal's position:
     * {@code atan2(goal.y - robot.y, goal.x - robot.x)}.</p>
     *
     * <p>This can be useful for “drive while pointing where you’re going” behaviors, e.g. driving
     * toward a scoring location while keeping the robot facing the travel direction.</p>
     */
    public static HeadingStrategy faceGoalPosition() {
        return (fieldRobotPose, fieldRobotTargetPose) -> {
            double dx = fieldRobotTargetPose.xInches - fieldRobotPose.xInches;
            double dy = fieldRobotTargetPose.yInches - fieldRobotPose.yInches;
            return Math.atan2(dy, dx);
        };
    }

    /**
     * Returns a strategy that always faces a fixed AprilTag location (tag center).
     *
     * <p>This is a common driver-assist behavior: as the robot approaches a scoring area,
     * keep the robot facing a known tag so aiming / alignment is easier.</p>
     *
     * @param layout tag layout describing tag placement in the field frame
     * @param tagId  AprilTag numeric ID code
     * @throws IllegalArgumentException if {@code layout} is null or the tag ID is not present
     */
    public static HeadingStrategy faceTag(TagLayout layout, int tagId) {
        if (layout == null) {
            throw new IllegalArgumentException("layout must not be null");
        }

        // Fail fast if the tag is unknown (TagLayout is field metadata and should be static).
        TagPose tag = layout.require(tagId);
        Pose3d fieldToTagPose = tag.fieldToTagPose();
        final double tagXInches = fieldToTagPose.xInches;
        final double tagYInches = fieldToTagPose.yInches;

        return (fieldRobotPose, fieldRobotTargetPose) -> {
            double dx = tagXInches - fieldRobotPose.xInches;
            double dy = tagYInches - fieldRobotPose.yInches;
            return Math.atan2(dy, dx);
        };
    }

    /**
     * Returns a strategy that faces a tag while far from the goal, then switches to the
     * goal pose's final heading when close enough.
     *
     * <p>This is useful when you want to “approach while looking at the tag”, but still finish
     * at a specific final heading near the goal (for example, parking precisely).</p>
     *
     * @param layout               tag layout describing tag placement in the field frame
     * @param tagId                AprilTag numeric ID code
     * @param switchDistanceInches when the robot is within this distance to the goal position,
     *                             switch from facing the tag to facing the final goal heading
     * @throws IllegalArgumentException if {@code layout} is null or the tag ID is not present
     */
    public static HeadingStrategy faceTagThenGoal(
            TagLayout layout,
            int tagId,
            double switchDistanceInches
    ) {
        if (layout == null) {
            throw new IllegalArgumentException("layout must not be null");
        }

        // Fail fast if the tag is unknown.
        TagPose tag = layout.require(tagId);
        Pose3d fieldToTagPose = tag.fieldToTagPose();
        final double tagXInches = fieldToTagPose.xInches;
        final double tagYInches = fieldToTagPose.yInches;

        final double switchDistAbs = Math.abs(switchDistanceInches);

        return (fieldRobotPose, fieldRobotTargetPose) -> {
            double distanceToGoalInches = fieldRobotPose.distanceTo(fieldRobotTargetPose);

            if (distanceToGoalInches > switchDistAbs) {
                // Far from the goal: face the tag.
                double dx = tagXInches - fieldRobotPose.xInches;
                double dy = tagYInches - fieldRobotPose.yInches;
                return Math.atan2(dy, dx);
            } else {
                // Close to the goal: face the final goal heading.
                return fieldRobotTargetPose.headingRad;
            }
        };
    }
}

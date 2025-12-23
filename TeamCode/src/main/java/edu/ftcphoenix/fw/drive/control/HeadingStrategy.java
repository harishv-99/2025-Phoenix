package edu.ftcphoenix.fw.drive.control;

import edu.ftcphoenix.fw.core.geometry.Pose2d;

/**
 * Strategy object that decides what heading the robot should face at a given
 * moment, based on its current pose and a goal pose.
 *
 * <p>This interface intentionally does <strong>not</strong> talk about how the
 * robot will turn to achieve that heading. It is purely about choosing a
 * desired heading angle. Feedback control (for example, a PID that turns
 * heading error into an {@code omega} command) is handled by a separate
 * component.</p>
 *
 * <h2>Coordinate conventions</h2>
 *
 * <ul>
 *   <li>Both {@code robotPose} and {@code goalPose} are expressed in a common
 *       2D coordinate frame (typically a field coordinate frame), using the
 *       conventions defined in {@link Pose2d}:
 *     <ul>
 *       <li>{@code xInches}, {@code yInches} in inches.</li>
 *       <li>{@code headingRad} in radians, measured CCW from +X.</li>
 *     </ul>
 *   </li>
 *   <li>The returned heading is also in radians, measured CCW from +X, and is
 *       intended to be used as the <em>desired</em> robot heading in that same
 *       frame.</li>
 * </ul>
 *
 * <h2>Examples</h2>
 *
 * <p>Some common strategies a caller might implement:</p>
 *
 * <ul>
 *   <li><b>Face final heading:</b> Always return {@code goalPose.headingRad}.</li>
 *   <li><b>Face the goal position:</b> Return the angle from the robot position
 *       to the goal position:
 *       {@code atan2(goal.y - robot.y, goal.x - robot.x)}.</li>
 *   <li><b>Face an AprilTag while approaching a goal:</b> A higher-level helper
 *       can construct a {@code HeadingStrategy} that internally reads the
 *       current tag pose (via a supplier) and returns the angle from the robot
 *       to that tag, possibly switching to {@code goalPose.headingRad} once
 *       the robot is close.</li>
 * </ul>
 *
 * <p>Because {@code HeadingStrategy} does not depend on AprilTags or any other
 * specific sensor, it is suitable for use in both tag-based and odometry-only
 * motion controllers.</p>
 */
@FunctionalInterface
public interface HeadingStrategy {

    /**
     * Computes the desired robot heading in radians for the given current pose
     * and goal pose.
     *
     * @param robotPose current pose of the robot in the common coordinate frame
     * @param goalPose  desired goal pose (position and nominal heading)
     * @return desired heading in radians, measured CCW from +X
     */
    double desiredHeading(Pose2d robotPose, Pose2d goalPose);
}

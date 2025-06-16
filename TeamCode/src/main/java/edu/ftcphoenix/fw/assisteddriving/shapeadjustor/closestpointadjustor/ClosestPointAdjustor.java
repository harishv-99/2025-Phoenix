package edu.ftcphoenix.fw.assisteddriving.shapeadjustor.closestpointadjustor;

import edu.ftcphoenix.fw.assisteddriving.shapeadjustor.ShapeAdjustorBuilder;

/**
 * Adjust the pose velocity components for adjustors that operate on the closest point in a shape.  This will
 * get information about the closest point, the normal and tangent vector to the closest point etc.  The magnitude
 * of the movement along those vectors can be adjusted.
 */
public interface ClosestPointAdjustor {

    /**
     * Adjust the pose velocity components by calling appropriate methods in {@link ClosestPointAdjustorData}.  These
     * adjustors are managed by {@code ShapeAdjustor}.  (See {@link ShapeAdjustorBuilder}.
     *
     * @param data The data used by closest point adjustors.
     * @see ClosestPointAdjustorData#setModifiedVelocityAngle(double)
     * @see ClosestPointAdjustorData#setModifiedMagnitudeFactorOfVelClosestNormal(double)
     * @see ClosestPointAdjustorData#setModifiedMagnitudeFactorOfVelClosestTangent(double)
     */
    public void adjustPoseVelocityComponents(ClosestPointAdjustorData data);

    /**
     * Define the axes of movement that can be adjusted.
     */
    enum AxisToAdjust {
        NORMAL_TO_CLOSEST_POINT,
        TANGENT_TO_CLOSEST_POINT,
        HEADING,
        POSITION,
        POSITION_AND_HEADING;

        /**
         * Is this item a superset of the compared-to item.
         *
         * @param compareTo The item to compare to.
         * @return Whether this is a superset.
         */
        public boolean isSupersetOf(AxisToAdjust compareTo) {
            boolean bCompareToIsPosition = (compareTo == NORMAL_TO_CLOSEST_POINT) ||
                    (compareTo == TANGENT_TO_CLOSEST_POINT) ||
                    (compareTo == POSITION);

            // A direct match is a superset
            if (this == compareTo)
                return true;

            // POSITION is a superset of all positions
            if (this == POSITION)
                return bCompareToIsPosition;

            // POSITION_AND_HEADING will match anything; otherwise we did not match any valid options.
            return this == POSITION_AND_HEADING;
        }
    }


    /**
     * Define direction of movement with respect to the closest point.
     */
    enum RelativeMovement {
        TOWARDS_CLOSEST_POINT,
        AWAY_FROM_CLOSEST_POINT,
        ANYWHERE;

        /**
         * Is this relative movement a superset of the given relative movement?  ANYWHERE is a superset of all other
         * options.
         *
         * @param compareTo The relative movement to compare to.
         * @return Whether this is a superset of the given item.
         */
        public boolean isSupsersetOf(RelativeMovement compareTo) {
            if (this == ANYWHERE || compareTo == ANYWHERE)
                return true;

            return this == compareTo;
        }
    }
}

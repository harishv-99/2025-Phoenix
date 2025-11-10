package edu.ftcphoenix.fw2.util;

import com.acmerobotics.roadrunner.PoseVelocity2d;
import com.acmerobotics.roadrunner.Vector2d;

public class RoadRunner {
    public static PoseVelocity2d toPoseVelocity2d(double velocityAxial,
                                                  double velocityLateral,
                                                  double velocityAngular) {

        PoseVelocity2d pv = new PoseVelocity2d(new Vector2d(velocityAxial, velocityLateral),
                velocityAngular);

        return pv;
    }
}

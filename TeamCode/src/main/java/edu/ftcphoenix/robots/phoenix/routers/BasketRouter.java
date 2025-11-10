package edu.ftcphoenix.robots.phoenix.routers;

import edu.ftcphoenix.fw.flow.simple.SimpleLanedFlow;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Lane selector for basket placement ("left"/"right").
 *
 * <p>Keep this tiny and explicit. Your OpMode can set the lane based on gamepad,
 * AprilTag, alliance, etc., and the flow will read the latest choice each loop.</p>
 */
public final class BasketRouter implements SimpleLanedFlow.Router {
    private final AtomicReference<String> lane = new AtomicReference<String>("left");

    /** Set current lane (expected values like "left" or "right"). */
    public void setLane(String laneName) {
        if (laneName != null) lane.set(laneName);
    }

    /** Get current lane (never null). */
    public String currentLane() {
        String v = lane.get();
        return v == null ? "left" : v;
    }

    @Override
    public String chooseLane() {
        return currentLane();
    }
}

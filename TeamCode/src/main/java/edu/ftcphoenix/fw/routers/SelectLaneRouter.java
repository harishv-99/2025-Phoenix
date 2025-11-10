package edu.ftcphoenix.fw.routers;

import edu.ftcphoenix.fw.flow.Lanes;
import edu.ftcphoenix.fw.flow.simple.SimpleLanedFlow;

/**
 * Filters a base router to a single lane: forwards the lane only if base chose that lane,
 * otherwise disables this connection for the tick.
 */
public final class SelectLaneRouter implements SimpleLanedFlow.Router {
    private final SimpleLanedFlow.Router base;
    private final String lane;

    public SelectLaneRouter(SimpleLanedFlow.Router base, String lane) {
        this.base = base;
        this.lane = lane;
    }

    @Override
    public String chooseLane() {
        String pick = base.chooseLane();
        return lane.equals(pick) ? lane : Lanes.DISABLED;
    }
}

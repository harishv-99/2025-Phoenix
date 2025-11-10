package edu.ftcphoenix.robots.phoenix.routers;

import edu.ftcphoenix.fw.flow.simple.SimpleLanedFlow;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Simple router that toggles between "accept" and "reject" lanes.
 *
 * <p>Useful for diagnostics or driver-initiated purges.</p>
 */
public final class AcceptRejectRouter implements SimpleLanedFlow.Router {
    private final AtomicBoolean reject = new AtomicBoolean(false);

    public void setReject(boolean on) { reject.set(on); }
    public boolean isReject() { return reject.get(); }

    @Override public String chooseLane() { return reject.get() ? "reject" : "accept"; }
}

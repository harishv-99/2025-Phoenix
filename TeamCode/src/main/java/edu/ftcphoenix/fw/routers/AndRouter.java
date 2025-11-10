package edu.ftcphoenix.fw.routers;

import edu.ftcphoenix.fw.flow.Lanes;
import edu.ftcphoenix.fw.flow.simple.SimpleLanedFlow;

import java.util.function.BooleanSupplier;

/**
 * Gates a router with a condition: if cond=false, disables; else forwards base.chooseLane().
 */
public final class AndRouter implements SimpleLanedFlow.Router {
    private final SimpleLanedFlow.Router base;
    private final BooleanSupplier cond;

    public AndRouter(SimpleLanedFlow.Router base, BooleanSupplier cond) {
        this.base = base;
        this.cond = cond;
    }

    @Override
    public String chooseLane() {
        return cond.getAsBoolean() ? base.chooseLane() : Lanes.DISABLED;
    }
}

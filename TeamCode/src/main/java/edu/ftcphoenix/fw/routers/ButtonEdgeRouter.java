package edu.ftcphoenix.fw.routers;

import edu.ftcphoenix.fw.flow.Lanes;
import edu.ftcphoenix.fw.flow.simple.SimpleLanedFlow;

import java.util.function.BooleanSupplier;

/**
 * One-shot: returns `lane` exactly once per rising edge; otherwise "__disabled__".
 */
public final class ButtonEdgeRouter implements SimpleLanedFlow.Router {
    private final BooleanSupplier button;
    private final String lane;
    private boolean prev = false;

    public ButtonEdgeRouter(BooleanSupplier button, String lane) {
        this.button = button;
        this.lane = lane;
    }

    @Override
    public String chooseLane() {
        boolean cur = button.getAsBoolean();
        String out = (!prev && cur) ? lane : Lanes.DISABLED;
        prev = cur;
        return out;
    }
}

package edu.ftcphoenix.fw.routers;

import edu.ftcphoenix.fw.flow.Lanes;
import edu.ftcphoenix.fw.flow.simple.SimpleLanedFlow;

import java.util.function.BooleanSupplier;

/**
 * While-held: returns `lane` while button true; otherwise "__disabled__".
 */
public final class ButtonHoldRouter implements SimpleLanedFlow.Router {
    private final BooleanSupplier button;
    private final String lane;

    public ButtonHoldRouter(BooleanSupplier button, String lane) {
        this.button = button;
        this.lane = lane;
    }

    @Override
    public String chooseLane() {
        return button.getAsBoolean() ? lane : Lanes.DISABLED;
    }
}

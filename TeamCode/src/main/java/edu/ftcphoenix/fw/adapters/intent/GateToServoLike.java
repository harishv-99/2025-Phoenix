package edu.ftcphoenix.fw.adapters.intent;

import edu.ftcphoenix.fw.core.Sink;
import edu.ftcphoenix.fw.hal.ServoLike;
import edu.ftcphoenix.fw.intent.primitive.GateIntent;

/**
 * Maps {@link GateIntent} to a {@link ServoLike} position.
 * Keep geometry-specific positions here (openPos/closedPos) so stages stay generic.
 */
public final class GateToServoLike implements Sink<GateIntent> {
    private final ServoLike servo;
    private final double openPos, closedPos;

    public GateToServoLike(ServoLike servo, double openPos, double closedPos) {
        this.servo = servo;
        this.openPos = clamp01(openPos);
        this.closedPos = clamp01(closedPos);
    }

    @Override
    public void accept(GateIntent intent) {
        servo.setPosition(intent.state() == GateIntent.State.OPEN ? openPos : closedPos);
    }

    private static double clamp01(double v) {
        return v < 0 ? 0 : (v > 1 ? 1 : v);
    }
}

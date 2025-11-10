package edu.ftcphoenix.fw.adapters.intent;

import edu.ftcphoenix.fw.core.Sink;
import edu.ftcphoenix.fw.hal.Motor;
import edu.ftcphoenix.fw.intent.primitive.TransportModeIntent;

/**
 * Maps {@link TransportModeIntent} to motor power.
 * Powers are provided by {@link TransportPowerConfig}. No blocking, clamps to [-1,1].
 */
public final class TransportModeToMotor implements Sink<TransportModeIntent> {
    private final Motor motor;
    private final TransportPowerConfig cfg;

    public TransportModeToMotor(Motor motor, TransportPowerConfig cfg) {
        this.motor = motor;
        this.cfg = (cfg == null) ? TransportPowerConfig.defaults() : cfg;
    }

    @Override
    public void accept(TransportModeIntent intent) {
        switch (intent.mode()) {
            case FEED:
                motor.setPower(cfg.feedPower);
                break;
            case REVERSE:
                motor.setPower(cfg.reversePower);
                break;
            case IDLE:
            default:
                motor.setPower(cfg.idlePower);
        }
    }
}

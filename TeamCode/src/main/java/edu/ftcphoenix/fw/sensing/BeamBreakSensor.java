package edu.ftcphoenix.fw.sensing;

import edu.ftcphoenix.fw.hal.BeamBreak;
import edu.ftcphoenix.fw.util.LoopClock;

/**
 * Thin adapter: {@link BeamBreak} → {@link BooleanSensor}.
 *
 * <p>No internal filtering; use {@link DebouncedBoolean} if you need debounce.</p>
 */
public final class BeamBreakSensor implements BooleanSensor {
    private final BeamBreak bb;

    public BeamBreakSensor(BeamBreak bb) {
        this.bb = bb;
    }

    @Override
    public void update(LoopClock clock) { /* stateless */ }

    @Override
    public boolean asBoolean() {
        return bb.blocked();
    }
}

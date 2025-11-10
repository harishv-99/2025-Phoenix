package edu.ftcphoenix.fw.sensing;

import edu.ftcphoenix.fw.util.Debounce;
import edu.ftcphoenix.fw.util.LoopClock;

import java.util.function.BooleanSupplier;

/**
 * Debounced boolean wrapper with edge detection.
 *
 * <h3>Usage</h3>
 * <pre>
 *   BooleanSupplier raw = () -> chamberBeam.blocked();
 *   BooleanSensor seated = new DebouncedBoolean(raw, 0.02, 0.02);
 * </pre>
 *
 * <h3>Notes</h3>
 * <ul>
 *   <li>Non-blocking; call {@link #update(LoopClock)} each loop with the framework clock.</li>
 *   <li>Edges are computed between successive {@code update} calls.</li>
 * </ul>
 */
public final class DebouncedBoolean implements BooleanSensor {

    private final BooleanSupplier raw;
    private final Debounce db;

    private boolean last = false;
    private boolean cur = false;

    /**
     * @param raw     unfiltered source (e.g., {@code () -> beamBreak.blocked()})
     * @param riseSec debounce time for false→true transitions (seconds)
     * @param fallSec debounce time for true→false transitions (seconds)
     */
    public DebouncedBoolean(BooleanSupplier raw, double riseSec, double fallSec) {
        this.raw = raw;
        this.db = new Debounce(riseSec, fallSec);
    }

    @Override
    public void update(LoopClock clock) {
        last = cur;
        double dt = Math.max(0, clock.dtSec());
        cur = db.update(dt, raw.getAsBoolean());
    }

    @Override
    public boolean asBoolean() {
        return cur;
    }

    @Override
    public boolean rose() {
        return !last && cur;
    }

    @Override
    public boolean fell() {
        return last && !cur;
    }
}

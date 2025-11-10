package edu.ftcphoenix.fw.util;

/**
 * Simple time-based debounce with separate rising/falling windows.
 *
 * <p><b>Why:</b> Stages must not gate transfers on single noisy samples. Use this to produce
 * stable booleans for {@code canProvide}/{@code canAccept}.</p>
 */
public final class Debounce {
    private final double riseSec;
    private final double fallSec;

    private boolean stable = false;
    private double acc = 0.0;

    /**
     * @param riseSec required time high before output becomes true
     * @param fallSec required time low before output becomes false
     */
    public Debounce(double riseSec, double fallSec) {
        this.riseSec = Math.max(0, riseSec);
        this.fallSec = Math.max(0, fallSec);
    }

    /**
     * Update with the latest raw input.
     *
     * @param dtSec loop delta time (non-negative)
     * @param raw   current sample (noisy)
     * @return debounced/stable output
     */
    public boolean update(double dtSec, boolean raw) {
        if (dtSec < 0) dtSec = 0;
        if (raw == stable) {
            // moving away from threshold; count down toward 0
            acc = 0.0;
            return stable;
        }
        // accumulating time toward switching
        acc += dtSec;
        final double need = stable ? fallSec : riseSec;
        if (acc >= need) {
            stable = raw;
            acc = 0.0;
        }
        return stable;
    }

    /**
     * Current debounced value without updating.
     */
    public boolean value() {
        return stable;
    }
}

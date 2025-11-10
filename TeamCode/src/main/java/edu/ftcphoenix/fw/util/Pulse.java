package edu.ftcphoenix.fw.util;

/**
 * Tiny helper to implement precise, non-blocking pulses inside a Stage.
 *
 * <p><b>Why:</b> Timing must live inside a stage to avoid jitter from the flow. Use {@link #start(double)}
 * in {@code provide/accept}, and call {@link #tick(double)} from {@code update(clock)} each loop.</p>
 *
 * <p><b>Best practice:</b> While the pulse is active, return {@code false} from {@code canProvide/canAccept}
 * to enforce busy gating and prevent re-entry.</p>
 */
public final class Pulse {
    /**
     * Actuations to perform when the pulse begins/ends.
     */
    public interface Act {
        void on();

        void off();
    }

    private final Act act;
    private double durSec = 0.0;
    private double tSec = 0.0;
    private boolean active = false;

    public Pulse(Act act) {
        this.act = act;
    }

    /**
     * Start a pulse for {@code seconds}. If seconds <= 0, this is a no-op.
     */
    public void start(double seconds) {
        if (seconds <= 0) return;
        this.durSec = seconds;
        this.tSec = 0.0;
        this.active = true;
        act.on();
    }

    /**
     * Advance the pulse by {@code dtSec}. When complete, invokes {@code off()} exactly once.
     */
    public void tick(double dtSec) {
        if (!active) return;
        if (dtSec < 0) dtSec = 0;
        tSec += dtSec;
        if (tSec >= durSec) {
            active = false;
            act.off();
        }
    }

    /**
     * True while the pulse is active (use to gate readiness).
     */
    public boolean isActive() {
        return active;
    }
}

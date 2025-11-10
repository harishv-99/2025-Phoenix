package edu.ftcphoenix.fw.stages.indexer;

import edu.ftcphoenix.fw.core.Sink;
import edu.ftcphoenix.fw.flow.Lanes;
import edu.ftcphoenix.fw.flow.simple.SimpleLanedStage;
import edu.ftcphoenix.fw.hal.BeamBreak;
import edu.ftcphoenix.fw.intent.primitive.GateIntent;
import edu.ftcphoenix.fw.intent.primitive.TransportModeIntent;
import edu.ftcphoenix.fw.sensing.BooleanSensor;
import edu.ftcphoenix.fw.sensing.DebouncedBoolean;
import edu.ftcphoenix.fw.util.LoopClock;
import edu.ftcphoenix.fw.telemetry.Telem;

import java.util.HashSet;
import java.util.Set;

/**
 * Single-ball chamber with two lanes:
 * <ul>
 *   <li>"accept" – feed forward to the next stage (e.g., shooter). Handles first/next timing.</li>
 *   <li>"reject" – reverse back toward the intake/field.</li>
 * </ul>
 *
 * <h3>Design choices</h3>
 * <ul>
 *   <li><b>Sensing injected:</b> Accept a {@link BooleanSensor} for "seated" so debouncing/edges are reusable
 *       and not reimplemented here. A legacy constructor wraps a {@link BeamBreak} in a {@link DebouncedBoolean}.</li>
 *   <li><b>Process-first indexing:</b> {@link #process(String)} indexes toward seat; pulses happen in {@link #update(LoopClock)}.</li>
 *   <li><b>Busy gating:</b> While pulsing window / reversing, readiness returns false.</li>
 *   <li><b>No blocking:</b> All timing lives in the stage; loop never sleeps.</li>
 * </ul>
 */
public final class ChamberStage implements SimpleLanedStage {

    private final Sink<GateIntent> window;
    private final Sink<TransportModeIntent> transport;
    private final BooleanSensor seatedSensor;
    private final ChamberConfig cfg;

    private final Set<String> lanes = new HashSet<String>();

    private boolean seated = false;
    private boolean busy = false;
    private boolean firstSinceEmpty = true;

    private double t = 0.0;
    private Mode mode = Mode.IDLE;

    private enum Mode {IDLE, ACCEPT_PREFEED, ACCEPT_OPEN, ACCEPT_RECOVER, REJECT_RUN}

    /**
     * Preferred ctor: provide a debounced boolean sensor that is true when a ball is seated.
     */
    public ChamberStage(Sink<GateIntent> window,
                        Sink<TransportModeIntent> transport,
                        BooleanSensor seatedSensor,
                        ChamberConfig cfg) {
        this.window = window;
        this.transport = transport;
        this.seatedSensor = seatedSensor;
        this.cfg = (cfg == null) ? ChamberConfig.defaults() : cfg;

        lanes.add(Lanes.ACCEPT);
        lanes.add(Lanes.REJECT);

        if (this.window != null) this.window.accept(GateIntent.closed());
        if (this.transport != null) this.transport.accept(TransportModeIntent.idle());
    }

    /**
     * Legacy convenience ctor: wraps a raw {@link BeamBreak} with a default {@link DebouncedBoolean}.
     * Debounce times chosen to suppress typical flicker; tune upstream if needed.
     */
    public ChamberStage(Sink<GateIntent> window,
                        Sink<TransportModeIntent> transport,
                        BeamBreak chamberSensor,
                        ChamberConfig cfg) {
        this(window, transport,
                (chamberSensor == null)
                        ? new DebouncedBoolean(new java.util.function.BooleanSupplier() {
                    public boolean getAsBoolean() {
                        return false;
                    }
                }, 0.02, 0.02)
                        : new DebouncedBoolean(new java.util.function.BooleanSupplier() {
                    public boolean getAsBoolean() {
                        return chamberSensor.blocked();
                    }
                }, 0.02, 0.02),
                cfg);
    }

    @Override
    public Set<String> lanes() {
        return lanes;
    }

    /**
     * Index toward seat for the requested lane; non-blocking.
     */
    @Override
    public void process(String lane) {
        if (Lanes.ACCEPT.equals(lane) && !busy && !seated) {
            transport.accept(TransportModeIntent.feed());
        }
    }

    /**
     * Provide only when a ball is seated and we're not busy.
     */
    @Override
    public boolean canProvide(String lane) {
        if (busy) return false;
        if (!seated) return false;
        return (Lanes.ACCEPT.equals(lane) || Lanes.REJECT.equals(lane));
    }

    /**
     * Start the lane action:
     * <ul>
     *   <li>"accept": prefeed (first/next), open briefly, then close and recover</li>
     *   <li>"reject": reverse for {@code rejectSec}</li>
     * </ul>
     */
    @Override
    public void provide(String lane) {
        busy = true;
        t = 0.0;
        if (Lanes.ACCEPT.equals(lane)) {
            mode = Mode.ACCEPT_PREFEED;
            transport.accept(TransportModeIntent.feed());
            Telem.event("chamber", "provide=accept");
        } else {
            mode = Mode.REJECT_RUN;
            window.accept(GateIntent.closed());
            transport.accept(TransportModeIntent.reverse());
            Telem.event("chamber", "provide=reject");
        }
    }

    @Override
    public boolean canAccept(String lane) {
        return !busy && !seated;
    }

    @Override
    public void accept(String lane) { /* no-op; seat sensor drives state */ }

    @Override
    public void update(LoopClock clock) {
        // Update sensors first
        if (seatedSensor != null) seatedSensor.update(clock);
        seated = (seatedSensor != null) && seatedSensor.asBoolean();
        Telem.kv("chamber", "seated", seated);

        // Idle transport if seated and not busy (prevents grinding)
        if (!busy && seated) {
            transport.accept(TransportModeIntent.idle());
        }

        if (!busy) return;

        t += Math.max(0, clock.dtSec());
        switch (mode) {
            case ACCEPT_PREFEED: {
                double pre = firstSinceEmpty ? cfg.prefeedFirstSec : cfg.prefeedNextSec;
                if (t >= pre) {
                    window.accept(GateIntent.open());
                    t = 0.0;
                    mode = Mode.ACCEPT_OPEN;
                    Telem.event("chamber.window", "OPEN");
                }
                break;
            }
            case ACCEPT_OPEN: {
                double open = firstSinceEmpty ? cfg.openFirstSec : cfg.openNextSec;
                if (t >= open) {
                    window.accept(GateIntent.closed());
                    transport.accept(TransportModeIntent.idle());
                    t = 0.0;
                    mode = Mode.ACCEPT_RECOVER;
                    firstSinceEmpty = false;
                    Telem.event("chamber.window", "CLOSED");
                }
                break;
            }
            case ACCEPT_RECOVER: {
                if (t >= cfg.recoverSec) {
                    busy = false;
                    mode = Mode.IDLE;
                    Telem.event("chamber", "accept_done");
                }
                break;
            }
            case REJECT_RUN: {
                if (t >= cfg.rejectSec) {
                    transport.accept(TransportModeIntent.idle());
                    busy = false;
                    mode = Mode.IDLE;
                    firstSinceEmpty = true;
                    Telem.event("chamber", "reject_done");
                }
                break;
            }
            case IDLE:
            default:
                busy = false;
                mode = Mode.IDLE;
        }
        Telem.kv("chamber", "mode", mode.name());
    }
}

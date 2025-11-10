package edu.ftcphoenix.fw.stages.indexer;

import edu.ftcphoenix.fw.flow.Lanes;
import edu.ftcphoenix.fw.flow.simple.SimpleLanedStage;
import edu.ftcphoenix.fw.hal.Motor;
import edu.ftcphoenix.fw.telemetry.Telem;
import edu.ftcphoenix.fw.util.LoopClock;

import java.util.HashSet;
import java.util.Set;

/**
 * Sensorless, time-based indexer (a.k.a. transfer/chamber) that buffers multiple pieces and moves
 * them one "pitch" at a time using timed motor motion.
 *
 * <h3>Behavior (lanes)</h3>
 * <ul>
 *   <li><b>accept("accept")</b>: ingest one piece from upstream (spin inward for {@code ingestSec}); {@code count++}.</li>
 *   <li><b>provide("accept")</b>: deliver one piece downstream (spin inward for {@code deliverSec}); {@code count--}.</li>
 *   <li><b>accept("reject")</b>: purge one piece upstream (spin outward for {@code rejectSec}); {@code count--}.</li>
 * </ul>
 *
 * <h3>Contract & assumptions</h3>
 * <ul>
 *   <li><b>One-pitch actions:</b> Each action moves exactly one piece-length. Configure times accordingly.</li>
 *   <li><b>No sensors:</b> Piece presence is tracked by an internal {@code count} in [0, capacity].</li>
 *   <li><b>Busy gating:</b> While an action runs, {@code canProvide}/{@code canAccept} return false.</li>
 *   <li><b>Limited awareness:</b> The stage is agnostic of what lives downstream/upstream; it only exposes lanes.</li>
 * </ul>
 */
public final class TransferTimedStage implements SimpleLanedStage {

    private final Motor motor;
    private final TransferTimedConfig cfg;

    private final Set<String> lanes = new HashSet<String>();

    private int count = 0;           // number of buffered pieces (0..capacity)
    private boolean busy = false;

    private double t = 0.0;
    private Mode mode = Mode.IDLE;

    /**
     * Internal action state (generic).
     */
    private enum Mode {IDLE, INGEST, DELIVER, REJECT}

    public TransferTimedStage(Motor transferMotor, TransferTimedConfig config) {
        this.motor = transferMotor;
        this.cfg = (config == null) ? TransferTimedConfig.defaults() : config;
        lanes.add(Lanes.ACCEPT);
        lanes.add(Lanes.REJECT);
        this.motor.setPower(0.0);
        Telem.kv("indexer", "capacity", cfg.capacity);
        Telem.kv("indexer", "count", count);
    }

    @Override
    public Set<String> lanes() {
        return lanes;
    }

    /**
     * No pre-positioning needed for a timed, sensorless indexer.
     */
    @Override
    public void process(String lane) { /* no-op */ }

    /**
     * Provide downstream only when we hold & are idle.
     */
    @Override
    public boolean canProvide(String lane) {
        if (!Lanes.ACCEPT.equals(lane)) return false;
        return !busy && (count > 0);
    }

    /**
     * Start delivery by time; decremented on completion.
     */
    @Override
    public void provide(String lane) {
        if (!Lanes.ACCEPT.equals(lane) || busy || count <= 0) return;
        busy = true;
        t = 0.0;
        mode = Mode.DELIVER;
        motor.setPower(cfg.inwardPower);
        Telem.event("indexer", "deliver_start");
    }

    /**
     * Accept when we have room & are idle; purge allowed when idle.
     */
    @Override
    public boolean canAccept(String lane) {
        if (Lanes.ACCEPT.equals(lane)) return !busy && (count < cfg.capacity);
        if (Lanes.REJECT.equals(lane)) return !busy;
        return false;
    }

    /**
     * Start ingest or reject; count adjusted on completion.
     */
    @Override
    public void accept(String lane) {
        if (busy) return;
        t = 0.0;
        if (Lanes.ACCEPT.equals(lane)) {
            if (count >= cfg.capacity) return;
            busy = true;
            mode = Mode.INGEST;
            motor.setPower(cfg.inwardPower);
            Telem.event("indexer", "ingest_start");
        } else if (Lanes.REJECT.equals(lane)) {
            busy = true;
            mode = Mode.REJECT;
            motor.setPower(cfg.outwardPower);
            Telem.event("indexer", "reject_start");
        }
    }

    @Override
    public void update(LoopClock clock) {
        if (!busy) return;

        t += Math.max(0, clock.dtSec());
        switch (mode) {
            case INGEST:
                if (t >= cfg.ingestSec) {
                    motor.setPower(0.0);
                    if (count < cfg.capacity) count++;
                    finish("ingest_done");
                }
                break;
            case DELIVER:
                if (t >= cfg.deliverSec) {
                    motor.setPower(0.0);
                    if (count > 0) count--;
                    finish("deliver_done");
                }
                break;
            case REJECT:
                if (t >= cfg.rejectSec) {
                    motor.setPower(0.0);
                    if (count > 0) count--; // assume we expelled one upstream
                    finish("reject_done");
                }
                break;
            case IDLE:
            default:
                busy = false;
                mode = Mode.IDLE;
        }
    }

    private void finish(String tag) {
        busy = false;
        mode = Mode.IDLE;
        t = 0.0;
        Telem.event("indexer", tag);
        Telem.kv("indexer", "count", count);
    }

    /**
     * Current buffered pieces (0..capacity).
     */
    public int count() {
        return count;
    }

    /**
     * Max pieces this indexer is expected to buffer.
     */
    public int capacity() {
        return cfg.capacity;
    }

    /**
     * Whether an action is active (ingest/deliver/reject).
     */
    public boolean isBusy() {
        return busy;
    }
}

package edu.ftcphoenix.fw.stages.intake;

import edu.ftcphoenix.fw.flow.Lanes;
import edu.ftcphoenix.fw.flow.simple.SimpleLanedStage;
import edu.ftcphoenix.fw.hal.Motor;
import edu.ftcphoenix.fw.hal.ServoLike;
import edu.ftcphoenix.fw.sensing.BooleanSensor;
import edu.ftcphoenix.fw.telemetry.Telem;
import edu.ftcphoenix.fw.util.LoopClock;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Human-fed intake using a servo “pusher”.
 *
 * <h3>Behavior</h3>
 * <ul>
 *   <li>{@link Lanes#ACCEPT} via {@code provide}: executes a push stroke (forward then return).</li>
 *   <li>{@link Lanes#REJECT} via {@code accept}: optional purge using an assist motor (if present).</li>
 *   <li>Optional entry {@link BooleanSensor}: if provided, {@code canProvide(ACCEPT)} requires it to be true.</li>
 *   <li>Busy gating prevents re-triggering while a stroke/purge is running.</li>
 * </ul>
 *
 * <h3>Notes</h3>
 * <ul>
 *   <li>The pusher “home” and “push” positions are computed from a downstream load count
 *       (e.g., how many pieces the indexer holds) to avoid collisions.</li>
 *   <li>No blocking or sleeps. All timing runs inside {@link #update(LoopClock)}.</li>
 * </ul>
 */
public final class HumanPusherIntakeStage implements SimpleLanedStage {

    /**
     * Supplies how many pieces are already buffered downstream (affects home/push poses).
     */
    public interface CountSupplier {
        int get();
    }

    private final ServoLike pusher;
    /**
     * Optional assist motor (CR-servo/motor). May be null.
     */
    private final Motor assist;
    /**
     * Optional entry sensor. May be null.
     */
    private final BooleanSensor entry;

    private final CountSupplier loadCount;
    private final HumanPusherIntakeConfig cfg;

    private final Set<String> lanes;

    private double t = 0.0;
    private Mode mode = Mode.IDLE;

    private enum Mode {IDLE, PUSH_FORWARD, PUSH_RETURN, REJECT_EJECT}

    /**
     * Preferred ctor: works with or without sensor and assist motor.
     */
    public HumanPusherIntakeStage(ServoLike pusher,
                                  Motor assist /* nullable */,
                                  BooleanSensor entry /* nullable */,
                                  CountSupplier loadCount,
                                  HumanPusherIntakeConfig cfg) {
        this.pusher = pusher;
        this.assist = assist;
        this.entry = entry;
        this.loadCount = (loadCount == null) ? new CountSupplier() {
            public int get() {
                return 0;
            }
        } : loadCount;
        this.cfg = (cfg == null) ? HumanPusherIntakeConfig.defaults() : cfg;

        HashSet<String> s = new HashSet<String>();
        s.add(Lanes.ACCEPT);
        s.add(Lanes.REJECT);
        this.lanes = Collections.unmodifiableSet(s);

        // Safe startup: go home and stop assist
        pusher.setPosition(this.cfg.homePosFor(this.loadCount.get()));
        if (this.assist != null) this.assist.setPower(0.0);

        Telem.kv("intake", "mode", mode.name());
    }

    /**
     * Convenience ctor when there is no entry sensor on the robot.
     */
    public HumanPusherIntakeStage(ServoLike pusher,
                                  Motor assist /* nullable */,
                                  CountSupplier loadCount,
                                  HumanPusherIntakeConfig cfg) {
        this(pusher, assist, (BooleanSensor) null, loadCount, cfg);
    }

    // ---------------- SimpleLanedStage ----------------

    @Override
    public Set<String> lanes() {
        return lanes;
    }

    /**
     * Pre-position for {@link Lanes#ACCEPT}: drive pusher to home based on downstream load count.
     * Idempotent and safe to call each loop; has no effect when busy.
     */
    @Override
    public void process(String lane) {
        if (!Lanes.ACCEPT.equals(lane) || isBusy()) return;
        double home = cfg.homePosFor(loadCount.get());
        pusher.setPosition(home);
    }

    /**
     * This stage never "provides" on {@link Lanes#REJECT}. Only {@link Lanes#ACCEPT} is supported.
     */
    @Override
    public boolean canProvide(String lane) {
        if (!Lanes.ACCEPT.equals(lane)) return false;
        if (isBusy()) return false;
        // If an entry sensor exists, require it
        return (entry == null) || entry.asBoolean();
    }

    @Override
    public void provide(String lane) {
        if (!Lanes.ACCEPT.equals(lane)) return;
        if (!canProvide(lane)) return;

        mode = Mode.PUSH_FORWARD;
        t = 0.0;

        // Command forward stroke and optional assist feed
        pusher.setPosition(cfg.pushPosFor(loadCount.get()));
        if (assist != null) assist.setPower(cfg.feedPower);

        Telem.event("intake", "push_forward");
        Telem.kv("intake", "mode", mode.name());
    }

    /**
     * Accepts only {@link Lanes#REJECT} locally to purge upstream if an assist motor exists.
     */
    @Override
    public boolean canAccept(String lane) {
        if (!Lanes.REJECT.equals(lane)) return false;
        return !isBusy() && (assist != null);
    }

    @Override
    public void accept(String lane) {
        if (!Lanes.REJECT.equals(lane)) return;
        if (!canAccept(lane)) return;

        mode = Mode.REJECT_EJECT;
        t = 0.0;

        assist.setPower(cfg.rejectPower);
        Telem.event("intake", "reject_start");
        Telem.kv("intake", "mode", mode.name());
    }

    @Override
    public void update(LoopClock clock) {
        if (entry != null) entry.update(clock);

        double dt = Math.max(0, clock.dtSec());
        switch (mode) {
            case IDLE:
                // keep pusher homed relative to downstream count
                pusher.setPosition(cfg.homePosFor(loadCount.get()));
                break;

            case PUSH_FORWARD:
                t += dt;
                if (t >= cfg.pushForwardSec) {
                    // Start return
                    pusher.setPosition(cfg.homePosFor(loadCount.get()));
                    t = 0.0;
                    mode = Mode.PUSH_RETURN;
                    Telem.event("intake", "push_return");
                    Telem.kv("intake", "mode", mode.name());
                }
                break;

            case PUSH_RETURN:
                t += dt;
                if (t >= cfg.pushReturnSec) {
                    // Done
                    if (assist != null) assist.setPower(0.0);
                    mode = Mode.IDLE;
                    t = 0.0;
                    Telem.event("intake", "push_done");
                    Telem.kv("intake", "mode", mode.name());
                }
                break;

            case REJECT_EJECT:
                t += dt;
                if (t >= cfg.rejectEjectSec) {
                    assist.setPower(0.0);
                    mode = Mode.IDLE;
                    t = 0.0;
                    Telem.event("intake", "reject_done");
                    Telem.kv("intake", "mode", mode.name());
                }
                break;

            default:
                mode = Mode.IDLE;
        }
    }

    // ---------------- Helpers ----------------

    private boolean isBusy() {
        return mode != Mode.IDLE;
    }
}

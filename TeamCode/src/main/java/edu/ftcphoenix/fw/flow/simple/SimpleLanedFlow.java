package edu.ftcphoenix.fw.flow.simple;

import edu.ftcphoenix.fw.core.Updatable;
import edu.ftcphoenix.fw.flow.Lanes;
import edu.ftcphoenix.fw.util.LoopClock;

import java.util.*;

/**
 * Lightweight, lane-aware pull-first flow coordinator.
 *
 * <h3>What it guarantees</h3>
 * <ul>
 *   <li>Calls {@code update()} on every stage once per loop.</li>
 *   <li>For each downstream stage, attempts at most <b>one</b> handoff per loop (prevents double-push).</li>
 *   <li>Round-robin fairness across multiple upstream links feeding the same downstream (prevents starvation).</li>
 *   <li>Before attempting a transfer, it nudges both sides via {@link SimpleLanedStage#process(String)} for the chosen lane.</li>
 *   <li>When both sides are ready on that lane, it atomically calls {@code provide(lane)} then {@code accept(lane)} in the same tick.</li>
 * </ul>
 *
 * <h3>What it does NOT do</h3>
 * <ul>
 *   <li>No sleeping or timing: all precise pulses are owned by stages.</li>
 *   <li>No global pause: gate at the receiver by returning {@code canAccept=false}. Use a global veto only for hard safety elsewhere.</li>
 *   <li>No capacity bookkeeping: buffered stages keep their own count/capacity and expose readiness accordingly.</li>
 * </ul>
 *
 * <h3>Best practices this enforces</h3>
 * <ul>
 *   <li><b>Process-first:</b> {@code process(lane)} is always called before readiness checks—implement it idempotently.</li>
 *   <li><b>Busy gating:</b> stages must return {@code false} while a timed action is active to avoid re-entry.</li>
 *   <li><b>One transfer per downstream per loop:</b> proven pattern to avoid double actuations and race conditions.</li>
 * </ul>
 */
public final class SimpleLanedFlow implements Updatable {

    /**
     * Selects a lane name for a link (e.g., "accept", "reject", "left", "right"). May return null to use the defaultLane.
     */
    public interface Router {
        String chooseLane();
    }

    /**
     * A directed connection between an upstream and a downstream stage.
     */
    public static final class Link {
        final SimpleLanedStage up;
        final SimpleLanedStage down;
        final String defaultLane;
        final Router router;

        /**
         * @param up          upstream stage (provides the item)
         * @param down        downstream stage (accepts the item)
         * @param defaultLane fallback lane name if router returns null
         * @param router      optional lane selector (may be null)
         */
        public Link(SimpleLanedStage up, SimpleLanedStage down, String defaultLane, Router router) {
            this.up = up;
            this.down = down;
            this.defaultLane = (defaultLane == null ? Lanes.ACCEPT : defaultLane);
            this.router = router;
        }
    }

    private final List<SimpleLanedStage> stages = new ArrayList<SimpleLanedStage>();
    private final List<Link> links = new ArrayList<Link>();
    /**
     * Round-robin cursor per downstream to ensure fairness across multiple upstreams.
     */
    private final Map<SimpleLanedStage, Integer> rrCursor = new HashMap<SimpleLanedStage, Integer>();

    /**
     * Register a stage. Call this once per stage you intend to update via the flow.
     */
    public SimpleLanedFlow add(SimpleLanedStage stage) {
        if (stage != null) stages.add(stage);
        return this;
    }

    /**
     * Create a link from {@code up} to {@code down}.
     */
    public SimpleLanedFlow connect(SimpleLanedStage up, SimpleLanedStage down, String defaultLane, Router router) {
        if (up != null && down != null) links.add(new Link(up, down, defaultLane, router));
        return this;
    }

    @Override
    public void update(LoopClock clock) {
        // 1) Let each stage run its own timers/controllers (pulses, PIDs, debounces, etc.)
        for (int i = 0; i < stages.size(); i++) {
            stages.get(i).update(clock);
        }

        // 2) Group links by downstream to enforce "one transfer per downstream per loop".
        Map<SimpleLanedStage, List<Link>> byDown = new HashMap<SimpleLanedStage, List<Link>>();
        for (int i = 0; i < links.size(); i++) {
            Link e = links.get(i);
            List<Link> bucket = byDown.get(e.down);
            if (bucket == null) {
                bucket = new ArrayList<Link>();
                byDown.put(e.down, bucket);
            }
            bucket.add(e);
        }

        // 3) For each downstream, attempt one transfer with fairness across upstreams.
        for (Map.Entry<SimpleLanedStage, List<Link>> entry : byDown.entrySet()) {
            SimpleLanedStage down = entry.getKey();
            List<Link> es = entry.getValue();
            final int n = es.size();
            final int start = rrCursor.containsKey(down) ? rrCursor.get(down) : 0;

            for (int k = 0; k < n; k++) {
                Link e = es.get((start + k) % n);

                // Choose lane and sanity check support on both sides.
                String lane = (e.router != null) ? e.router.chooseLane() : e.defaultLane;
                if (lane == null) lane = e.defaultLane;

                if (!e.up.lanes().contains(lane) || !e.down.lanes().contains(lane)) {
                    continue; // unsupported path — skip this link this loop
                }

                // Nudge both stages toward readiness on this lane (idempotent, non-blocking).
                e.up.process(lane);
                e.down.process(lane);

                // Only when BOTH sides are ready for this lane do we trigger the handoff.
                if (e.up.canProvide(lane) && e.down.canAccept(lane)) {
                    // Upstream starts its action (e.g., open window, reverse transport).
                    e.up.provide(lane);
                    // Downstream starts its action (e.g., close claw, spin intake outward).
                    e.down.accept(lane);

                    // Advance fairness cursor and move to next downstream.
                    rrCursor.put(down, (start + k + 1) % n);
                    break;
                }
            }
        }
    }
}

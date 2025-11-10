package edu.ftcphoenix.fw.stages.goal;

import edu.ftcphoenix.fw.flow.simple.SimpleLanedStage;
import edu.ftcphoenix.fw.util.LoopClock;

import java.util.HashSet;
import java.util.Set;

/**
 * Goal “sink” that gates acceptance by chassis pose for multi-basket lanes.
 *
 * <h3>Semantics</h3>
 * <ul>
 *   <li>Lanes are basket names, e.g., "left" and "right".</li>
 *   <li>As a passive endpoint, {@code provide(lane)} is a no-op and {@code canProvide=false}.</li>
 *   <li>{@code canAccept(lane)} returns true only when chassis pose is within tolerances of the target lane.</li>
 * </ul>
 *
 * <h3>Best practices enforced</h3>
 * <ul>
 *   <li>Gating is per-edge: only this handoff waits; upstream chambers/intakes can continue operating.</li>
 *   <li>No blocking: pose checks are pure; no sleeps.</li>
 * </ul>
 */
public final class BasketStage implements SimpleLanedStage {

    /**
     * Minimal chassis pose provider.
     */
    public interface DrivePose {
        double x();

        double y();

        double yawRad();
    }

    /**
     * Lane → world target pose lookup.
     */
    public interface TargetLookup {
        Pose forLane(String lane);
    }

    /**
     * Simple target structure.
     */
    public static final class Pose {
        public final double x, y, yawRad;

        public Pose(double x, double y, double yawRad) {
            this.x = x;
            this.y = y;
            this.yawRad = yawRad;
        }
    }

    private final DrivePose drive;
    private final TargetLookup targets;
    private final BasketConfig cfg;
    private final Set<String> lanes = new HashSet<String>();

    public BasketStage(DrivePose drive, TargetLookup targets, BasketConfig cfg, Set<String> laneNames) {
        this.drive = drive;
        this.targets = targets;
        this.cfg = (cfg == null) ? BasketConfig.defaults() : cfg;
        if (laneNames != null) lanes.addAll(laneNames);
    }

    @Override
    public Set<String> lanes() {
        return lanes;
    }

    @Override
    public void process(String lane) { /* passive gating; nothing to do */ }

    @Override
    public boolean canProvide(String lane) {
        return false;
    } // sink endpoint

    @Override
    public void provide(String lane) { /* no-op */ }

    @Override
    public boolean canAccept(String lane) {
        if (lane == null || !lanes.contains(lane)) return false;
        Pose p = targets.forLane(lane);
        if (p == null) return false;

        double dx = drive.x() - p.x;
        double dy = drive.y() - p.y;
        double dist = Math.hypot(dx, dy);
        double dyaw = Math.abs(norm(drive.yawRad() - p.yawRad));

        return dist <= cfg.posTolMeters && dyaw <= cfg.yawTolRad;
    }

    @Override
    public void accept(String lane) { /* consume immediately; telemetry can count */ }

    @Override
    public void update(LoopClock clock) { /* passive */ }

    private static double norm(double a) {
        while (a > Math.PI) a -= 2 * Math.PI;
        while (a < -Math.PI) a += 2 * Math.PI;
        return a;
    }
}

package edu.ftcphoenix.fw.stages.gripperarm;

import edu.ftcphoenix.fw.core.Sink;
import edu.ftcphoenix.fw.flow.simple.SimpleLanedStage;
import edu.ftcphoenix.fw.intent.primitive.GateIntent;
import edu.ftcphoenix.fw.util.LoopClock;
import edu.ftcphoenix.fw.util.Pulse;

import java.util.HashSet;
import java.util.Set;

/**
 * Arm + claw “placer” stage that can both pick from the environment and place to world poses.
 *
 * <h3>Lanes (semantics)</h3>
 * <ul>
 *   <li>"pickup"  – receive from the field (downstream side does the work: close claw at pickup pose)</li>
 *   <li>"handoff" – provide to an internal buffer (e.g., chamber) at a handoff pose</li>
 *   <li>"left", "right" – provide to a goal by placing over the basket then opening claw</li>
 * </ul>
 *
 * <h3>Invariants & best practices enforced</h3>
 * <ul>
 *   <li><b>Process-first:</b> {@link #process(String)} sets target poses continuously; no pulses here.</li>
 *   <li><b>Busy gating:</b> During timed claw pulses, {@code canProvide/canAccept} return false.</li>
 *   <li><b>Timing inside the stage:</b> Precise open/close pulses use {@link Pulse} in {@link #update(LoopClock)}.</li>
 *   <li><b>No hidden sleeps:</b> All methods are non-blocking; timers advance only via {@link #update(LoopClock)}.</li>
 * </ul>
 *
 * @param <P> Pose type used by the robot's pose controller (could be a struct, enum, or ID)
 */
public final class GripperArmStage<P> implements SimpleLanedStage {

    /**
     * Minimal pose controller for arm/lift/wrist stacks.
     */
    public interface PoseController<P> {
        void setTarget(P pose);

        boolean atTarget(P pose, double tolRad);

        void update(LoopClock clock);
    }

    /**
     * Pose library maps lanes to target poses.
     */
    public interface PoseLibrary<P> {
        P pickupPose();

        P handoffPose();

        P placePose(String laneName); // e.g., "left"/"right"
    }

    private final PoseController<P> arm;
    private final Sink<GateIntent> claw;
    private final PoseLibrary<P> poses;
    private final GripperArmConfig cfg;

    private final Set<String> lanes = new HashSet<String>();

    private boolean hasItem = false;
    private boolean busy = false;
    private String activeLane = "pickup";

    // Precise, non-blocking pulses for claw open/close
    private final Pulse openPulse;
    private final Pulse closePulse;

    public GripperArmStage(PoseController<P> arm,
                           Sink<GateIntent> claw,
                           PoseLibrary<P> poses,
                           GripperArmConfig cfg) {
        this.arm = arm;
        this.claw = claw;
        this.poses = poses;
        this.cfg = (cfg == null) ? GripperArmConfig.defaults() : cfg;

        lanes.add("pickup");
        lanes.add("handoff");
        lanes.add("left");
        lanes.add("right");

        this.openPulse = new Pulse(new Pulse.Act() {
            public void on() {
                claw.accept(GateIntent.open());
            }

            public void off() {
                claw.accept(GateIntent.closed());
            }
        });
        this.closePulse = new Pulse(new Pulse.Act() {
            public void on() {
                claw.accept(GateIntent.closed());
            }

            public void off() { /* stay closed after hold */ }
        });
    }

    @Override
    public Set<String> lanes() {
        return lanes;
    }

    /**
     * Drive toward the lane’s pose. Idempotent; safe every loop.
     */
    @Override
    public void process(String lane) {
        this.activeLane = (lane == null) ? this.activeLane : lane;
        if ("pickup".equals(lane)) {
            arm.setTarget(poses.pickupPose());
        } else if ("handoff".equals(lane)) {
            arm.setTarget(poses.handoffPose());
        } else {
            // place to a goal lane (left/right or others you add)
            arm.setTarget(poses.placePose(lane));
        }
    }

    /**
     * Ready to provide only when holding an item, not busy, and at the lane's pose.
     */
    @Override
    public boolean canProvide(String lane) {
        if (busy) return false;
        if (!hasItem) return false;

        if ("handoff".equals(lane)) {
            return arm.atTarget(poses.handoffPose(), cfg.tolRad);
        } else if ("left".equals(lane) || "right".equals(lane)) {
            return arm.atTarget(poses.placePose(lane), cfg.tolRad);
        }
        // "pickup" is a receive-only lane
        return false;
    }

    /**
     * Provide (start action) for the lane. Non-blocking; starts a pulse and marks busy.
     * <ul>
     *   <li>"handoff" – open briefly to pass into a chamber, then close</li>
     *   <li>"left"/"right" – open briefly to drop to the basket, then close</li>
     * </ul>
     */
    @Override
    public void provide(String lane) {
        busy = true;
        if ("handoff".equals(lane)) {
            openPulse.start(cfg.openPulseSec);
            hasItem = false;
        } else if ("left".equals(lane) || "right".equals(lane)) {
            openPulse.start(cfg.openPulseSec);
            hasItem = false;
        } else {
            // no-op for pickup lane
        }
    }

    /**
     * Ready to accept only when empty, not busy, and at the pickup pose.
     */
    @Override
    public boolean canAccept(String lane) {
        if (busy) return false;
        if ("pickup".equals(lane)) {
            return !hasItem && arm.atTarget(poses.pickupPose(), cfg.tolRad);
        }
        // For handoff-from-chamber (if you choose), accept via "handoff" lane when empty & at handoff pose.
        if ("handoff".equals(lane)) {
            return !hasItem && arm.atTarget(poses.handoffPose(), cfg.tolRad);
        }
        return false;
    }

    /**
     * Accept (start action) for the lane. Non-blocking; starts a close-hold pulse and marks busy.
     * <ul>
     *   <li>"pickup" – close claw to grasp, hold for {@code closeHoldSec}</li>
     *   <li>"handoff" – optionally close if you pull an item from a chamber into the claw</li>
     * </ul>
     */
    @Override
    public void accept(String lane) {
        busy = true;
        closePulse.start(cfg.closeHoldSec);
        hasItem = true;
    }

    @Override
    public void update(LoopClock clock) {
        double dt = Math.max(0, clock.dtSec());
        arm.update(clock);

        // advance pulses; busy while any pulse is active
        openPulse.tick(dt);
        closePulse.tick(dt);

        busy = openPulse.isActive() || closePulse.isActive();
    }
}

package edu.ftcphoenix.fw.stages.shooter;

import edu.ftcphoenix.fw.flow.Lanes;
import edu.ftcphoenix.fw.flow.simple.SimpleLanedStage;
import edu.ftcphoenix.fw.sensing.BooleanSensor;
import edu.ftcphoenix.fw.telemetry.Telem;
import edu.ftcphoenix.fw.util.LoopClock;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Shooter stage: maintains a flywheel target via {@link Spooler} and exposes a
 * single lane {@link Lanes#ACCEPT} that becomes available when the shooter is at speed.
 *
 * <h3>Contract</h3>
 * <ul>
 *   <li><b>Lane set:</b> only {@link Lanes#ACCEPT} is supported.</li>
 *   <li><b>canAccept(ACCEPT):</b> true iff not busy and {@link #atSpeed()}.</li>
 *   <li><b>accept(ACCEPT):</b> starts a short ingest window (non-blocking). Window duration is
 *       {@code cfg.ingestWindowSec}. A brief {@code cfg.recoverSec} follows to avoid re-trigger spam.</li>
 *   <li><b>Busy gating:</b> while the ingest/recover window runs, additional accepts are blocked.</li>
 *   <li><b>Limited awareness:</b> the stage does not command upstream indexers; it only
 *       signals readiness and consumes one accept event. Upstream motion/timing is handled there.</li>
 * </ul>
 *
 * <h3>When to use</h3>
 * Use this stage whenever a flywheel (or similar) should gate deliveries "only at speed".
 * Your TeleOp or task code sets the target via {@link Spooler#setTargetRadPerSec(double)}.
 *
 * <h3>Telemetry</h3>
 * Emits {@code shooter.target}, {@code shooter.measured}, {@code shooter.atSpeed}, and {@code shooter.mode}.
 */
public final class ShooterStage implements SimpleLanedStage {

    /**
     * Low-level velocity wrapper (usually provided by an FTC adapter).
     */
    public interface Spooler {
        /**
         * Set the target speed at the wheel (rad/s). Zero to stop.
         */
        void setTargetRadPerSec(double target);

        /**
         * Last commanded target (rad/s).
         */
        double getTargetRadPerSec();

        /**
         * Measured wheel speed (rad/s).
         */
        double getMeasuredRadPerSec();

        /**
         * True if the current measured speed is within a configured tolerance of the target.
         * Implementations typically compare |measured - target| <= tol.
         */
        boolean atSpeed();
    }

    private final Spooler spooler;
    private final ShooterConfig cfg;
    private final BooleanSensor exitSensor; // optional (null allowed)

    private final Set<String> lanes;
    private Mode mode = Mode.IDLE;
    private double t = 0.0;

    private enum Mode {IDLE, INGEST_WINDOW, RECOVER}

    /**
     * @param spooler    flywheel controller
     * @param cfg        timing parameters (ingest window & recover)
     * @param exitSensor optional piece-exit sensor (can be null; not required)
     */
    public ShooterStage(Spooler spooler, ShooterConfig cfg, BooleanSensor exitSensor) {
        this.spooler = spooler;
        this.cfg = (cfg == null) ? ShooterConfig.defaults() : cfg;
        this.exitSensor = exitSensor;

        HashSet<String> s = new HashSet<String>();
        s.add(Lanes.ACCEPT);
        this.lanes = Collections.unmodifiableSet(s);

        Telem.kv("shooter", "target", this.spooler.getTargetRadPerSec());
        Telem.kv("shooter", "measured", this.spooler.getMeasuredRadPerSec());
        Telem.kv("shooter", "atSpeed", this.spooler.atSpeed());
        Telem.kv("shooter", "mode", mode.name());
    }

    // ------------ Stage API ------------

    @Override
    public Set<String> lanes() {
        return lanes;
    }

    /**
     * No pre-positioning needed here; flywheel regulation is handled inside the {@link Spooler}.
     * Keep this method side-effect free.
     */
    @Override
    public void process(String lane) { /* no-op */ }

    /**
     * Shooter never "provides" to another stage.
     */
    @Override
    public boolean canProvide(String lane) {
        return false;
    }

    @Override
    public void provide(String lane) { /* no-op */ }

    /**
     * Accept is allowed when we're idle and the shooter is at speed.
     * If you prefer to move the at-speed check to a router gate, you can change this to "!busy".
     */
    @Override
    public boolean canAccept(String lane) {
        if (!Lanes.ACCEPT.equals(lane)) return false;
        return mode == Mode.IDLE && spooler.atSpeed();
    }

    /**
     * Start a short "ingest window": lets the upstream indexer feed for a bounded time.
     * The stage itself does not drive any motors; it just blocks re-accepts until the window completes.
     */
    @Override
    public void accept(String lane) {
        if (!Lanes.ACCEPT.equals(lane)) return;
        if (!canAccept(lane)) return;

        mode = Mode.INGEST_WINDOW;
        t = 0.0;
        Telem.event("shooter", "ingest_window_start");
        Telem.kv("shooter", "mode", mode.name());
    }

    @Override
    public void update(LoopClock clock) {
        // Optional exit sensor update (if provided)
        if (exitSensor != null) exitSensor.update(clock);

        // Telemetry taps
        Telem.kv("shooter", "target", spooler.getTargetRadPerSec());
        Telem.kv("shooter", "measured", spooler.getMeasuredRadPerSec());
        Telem.kv("shooter", "atSpeed", spooler.atSpeed());

        double dt = Math.max(0, clock.dtSec());
        switch (mode) {
            case IDLE:
                // nothing to do
                break;

            case INGEST_WINDOW:
                t += dt;
                if (t >= cfg.ingestWindowSec) {
                    mode = (cfg.recoverSec > 0.0) ? Mode.RECOVER : Mode.IDLE;
                    t = 0.0;
                    Telem.event("shooter", "ingest_window_end");
                    Telem.kv("shooter", "mode", mode.name());
                }
                break;

            case RECOVER:
                t += dt;
                if (t >= cfg.recoverSec) {
                    mode = Mode.IDLE;
                    t = 0.0;
                    Telem.event("shooter", "recover_end");
                    Telem.kv("shooter", "mode", mode.name());
                }
                break;

            default:
                mode = Mode.IDLE;
        }
    }

    // ------------ Convenience (for routers / TeleOp) ------------

    /**
     * True if the shooter is at speed (delegates to {@link Spooler#atSpeed()}).
     */
    public boolean atSpeed() {
        return spooler.atSpeed();
    }

    /**
     * Current measured speed (rad/s), for telemetry or dashboards.
     */
    public double measuredRadPerSec() {
        return spooler.getMeasuredRadPerSec();
    }

    /**
     * Current target speed (rad/s).
     */
    public double targetRadPerSec() {
        return spooler.getTargetRadPerSec();
    }

    /**
     * Human-readable mode (IDLE / INGEST_WINDOW / RECOVER).
     */
    public String mode() {
        return mode.name();
    }
}

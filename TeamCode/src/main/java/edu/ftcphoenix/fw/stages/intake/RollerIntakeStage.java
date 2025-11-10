package edu.ftcphoenix.fw.stages.intake;

import edu.ftcphoenix.fw.flow.Lanes;
import edu.ftcphoenix.fw.flow.simple.SimpleLanedStage;
import edu.ftcphoenix.fw.hal.BeamBreak;
import edu.ftcphoenix.fw.hal.Motor;
import edu.ftcphoenix.fw.util.Debounce;
import edu.ftcphoenix.fw.util.LoopClock;

import java.util.HashSet;
import java.util.Set;

/**
 * Field-facing roller intake (or CR-servo) with bidirectional behavior.
 *
 * <h3>Lanes</h3>
 * <ul>
 *   <li>"accept" – downstream role: receive from the field by running rollers inward.</li>
 *   <li>"reject" – downstream role when linked from a chamber: eject back to field by spinning outward.</li>
 * </ul>
 *
 * <h3>Best practices enforced</h3>
 * <ul>
 *   <li>Debounced entry sensor (optional) to declare completion of ingest.</li>
 *   <li>Busy gating during ingest/eject pulses.</li>
 *   <li>No blocking; precise durations live inside the stage.</li>
 * </ul>
 */
public final class RollerIntakeStage implements SimpleLanedStage {

    private final Motor roller;
    private final BeamBreak entrySensor;    // optional; indicates ball at mouth
    private final RollerIntakeConfig cfg;

    private final Set<String> lanes = new HashSet<String>();
    private final Debounce entryDb = new Debounce(0.02, 0.02);

    private boolean busy = false;
    private double t = 0.0;
    private Mode mode = Mode.IDLE;

    private enum Mode {IDLE, INGEST, EJECT}

    public RollerIntakeStage(Motor roller, BeamBreak entrySensor /* nullable */, RollerIntakeConfig cfg) {
        this.roller = roller;
        this.entrySensor = entrySensor;
        this.cfg = (cfg == null) ? RollerIntakeConfig.defaults() : cfg;

        lanes.add(Lanes.ACCEPT);
        lanes.add(Lanes.REJECT);

        roller.setPower(0.0);
    }

    @Override
    public Set<String> lanes() {
        return lanes;
    }

    @Override
    public void process(String lane) {
        // Nothing to pre-position; readiness is purely availability (not busy)
    }

    /**
     * For a field source, upstream provides; we act as downstream receiver during "accept".
     */
    @Override
    public boolean canProvide(String lane) {
        return false;
    }

    @Override
    public void provide(String lane) { /* no-op; we are not a provider in typical wiring */ }

    @Override
    public boolean canAccept(String lane) {
        return !busy && (Lanes.ACCEPT.equals(lane) || Lanes.REJECT.equals(lane));
    }

    @Override
    public void accept(String lane) {
        busy = true;
        t = 0.0;
        if (Lanes.ACCEPT.equals(lane)) {
            mode = Mode.INGEST;
            roller.setPower(cfg.inwardPower);
        } else {
            mode = Mode.EJECT;
            roller.setPower(cfg.outwardPower);
        }
    }

    @Override
    public void update(LoopClock clock) {
        double dt = Math.max(0, clock.dtSec());
        if (entrySensor != null) entryDb.update(dt, entrySensor.blocked());

        if (!busy) return;

        t += dt;
        switch (mode) {
            case INGEST:
                // End ingest by time or by sensor seeing the piece “inside”
                if (t >= cfg.ingestSec || (entrySensor != null && entryDb.value())) {
                    roller.setPower(0.0);
                    busy = false;
                    mode = Mode.IDLE;
                }
                break;
            case EJECT:
                if (t >= cfg.ejectSec) {
                    roller.setPower(0.0);
                    busy = false;
                    mode = Mode.IDLE;
                }
                break;
            case IDLE:
            default:
                roller.setPower(0.0);
                busy = false;
                mode = Mode.IDLE;
        }
    }
}

package edu.ftcphoenix.fw2.core;

import edu.ftcphoenix.fw2.actuation.EffortSink;
import edu.ftcphoenix.fw2.sensing.DoubleFeedbackSource;

/**
 * A simple closed-loop controller that reads a timestamped scalar measurement,
 * compares it to a setpoint, and drives an {@link edu.ftcphoenix.fw2.actuation.EffortSink}
 * using PID (+ optional feedforward).
 *
 * <h2>Data flow</h2>
 * <ol>
 *   <li>Sample {@link edu.ftcphoenix.fw2.sensing.DoubleFeedbackSource} at {@code nowNanos}.</li>
 *   <li>Compute error = setpoint - measurement.</li>
 *   <li>PID correction = {@link PidController#update(double, double)}.</li>
 *   <li>Optional {@link Feedforward} (e.g., kV·setpoint) added.</li>
 *   <li>Apply {@code ff + correction} to {@link edu.ftcphoenix.fw2.actuation.EffortSink}.</li>
 * </ol>
 *
 * <h2>When to use</h2>
 * <ul>
 *   <li>Velocity, position, or heading control where you have a scalar feedback.</li>
 *   <li>Quickly wire a sensor/source, a setpoint, and an actuator without bespoke glue.</li>
 * </ul>
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * FeedbackController ctrl = new FeedbackController(velSensor, motor, targetRps, pid, new SimpleVelocityFeedforward(kV));
 * ctrl.update(clock);
 * }</pre>
 *
 * <h2>Best practices</h2>
 * <ul>
 *   <li><b>Invalid samples:</b> {@code sampleOrNaN()} may propagate NaNs to the PID and sink.
 *       Consider switching to a held-value adapter
 *       ({@link edu.ftcphoenix.fw2.core.Adapters#sourceHolding}) or explicitly handle NaN here
 *       (e.g., skip update or hold last output).</li>
 *   <li><b>Feedforward:</b> Treat feedforward as optional; ensure {@code enableFeedforward} matches your tuning plan.</li>
 * </ul>
 */
public class FeedbackController implements Controller {
    private final DoubleFeedbackSource source;
    private final EffortSink sink;
    private final DoubleSetpoint setpoint;
    private final PidController pid;
    private Feedforward feedforward;
    private boolean enableFeedforward = true;

    public FeedbackController(DoubleFeedbackSource source,
                              EffortSink sink,
                              DoubleSetpoint setpoint,
                              PidController pid,
                              Feedforward feedforward) {
        this.source = source;
        this.sink = sink;
        this.setpoint = setpoint;
        this.pid = pid;
        this.feedforward = feedforward;
    }

    @Override
    public void update(FrameClock clock) {
        final long nowNanos = clock.nanoTime();
        final double dtSec = clock.dtSec();

        final double measured = source.sampleOrNaN(nowNanos);
        final double target = setpoint.get(clock);
        final double error = target - measured;

        final double correction = pid.update(error, dtSec);
        final double ff = (enableFeedforward && feedforward != null)
                ? feedforward.calculate(target, 0.0)
                : 0.0;

        sink.applyEffort(ff + correction);
    }

    public void setFeedforward(Feedforward ff) {
        this.feedforward = ff;
    }

    public void enableFeedforward(boolean enabled) {
        this.enableFeedforward = enabled;
    }

    /**
     * Returns the instantaneous error (setpoint - measurement) using the provided {@link FrameClock}.
     * <p>Does not apply any filtering or validity checks.</p>
     */
    public double getError(FrameClock clock) {
        return setpoint.get(clock) - source.sampleOrNaN(clock.nanoTime());
    }
}

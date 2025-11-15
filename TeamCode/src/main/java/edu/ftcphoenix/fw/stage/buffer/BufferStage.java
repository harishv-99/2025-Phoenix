package edu.ftcphoenix.fw.stage.buffer;

import com.qualcomm.robotcore.hardware.HardwareMap;

import java.util.function.BooleanSupplier;

import edu.ftcphoenix.fw.adapters.ftc.FtcHardware;
import edu.ftcphoenix.fw.hal.MotorOutput;

/**
 * Timed pulse stage for a buffer / indexer mechanism.
 *
 * <p>The typical FTC use-case is:
 * <ul>
 *   <li>Use a buffer motor to feed one game piece at a time into a shooter.</li>
 *   <li>Each feed is a fixed-duration pulse (eg. 0.4s at 80% power).</li>
 *   <li>Only fire when the shooter is ready (eg. at RPM setpoint).</li>
 * </ul>
 *
 * <p>{@code BufferStage} encapsulates this behavior:
 * <ul>
 *   <li>You configure the motor, power, duration, and "downstream ready" gate via
 *       {@link TransferSpecs}.</li>
 *   <li>At runtime, you call {@link #handle(BufferCmd)} (typically from input bindings).</li>
 *   <li>You call {@link #update(double)} each loop with dt seconds.</li>
 * </ul>
 *
 * <h2>Commands</h2>
 * <ul>
 *   <li>{@link BufferCmd#SEND} – queue a forward pulse. Pulses run one at a time
 *       when {@code downstreamReady} is true.</li>
 *   <li>{@link BufferCmd#EJECT} – queue a reverse pulse. This ignores
 *       {@code downstreamReady} and runs as soon as the buffer is idle.</li>
 *   <li>{@link BufferCmd#CANCEL} – stop any active pulse and clear the queue.</li>
 * </ul>
 *
 * <p>Multiple SEND commands may be queued; the stage will execute them in order as
 * downstream readiness allows. EJECT commands are also queued; they run after any
 * in-progress pulse completes.</p>
 */
public final class BufferStage {

    /**
     * High-level buffer commands.
     */
    public enum BufferCmd {
        /**
         * Fire one forward pulse when downstream is ready.
         */
        SEND,

        /**
         * Fire one reverse pulse as soon as possible, ignoring downstream readiness.
         */
        EJECT,

        /**
         * Stop any active pulse and clear all queued commands.
         */
        CANCEL
    }

    // ---------------------------------------------------------------------
    // Configuration struct
    // ---------------------------------------------------------------------

    /**
     * Configuration for a timed power pulse.
     *
     * <p>Used to describe both forward (SEND) and reverse (EJECT) pulses. The
     * {@code power} field is the magnitude used for forward pulses; reverse
     * pulses use {@code -power} with the same duration.</p>
     */
    public static final class TransferSpecs {
        /**
         * Motor to drive.
         */
        public MotorOutput motor;

        /**
         * Forward pulse power (reverse pulses use the negated value).
         */
        public double power;

        /**
         * Pulse duration in seconds (must be &gt; 0).
         */
        public double seconds;

        /**
         * Optional gating condition for SEND pulses. If this returns false,
         * SEND pulses will not start yet (but may remain queued).
         */
        public BooleanSupplier downstreamReady = ALWAYS_READY;

        private static final BooleanSupplier ALWAYS_READY = new BooleanSupplier() {
            @Override
            public boolean getAsBoolean() {
                return true;
            }
        };

        private TransferSpecs() {
        }

        /**
         * Build specs from a {@link MotorOutput}.
         */
        public static TransferSpecs powerFor(MotorOutput motor, double power, double seconds) {
            if (motor == null) {
                throw new IllegalArgumentException("motor is required");
            }
            if (seconds <= 0.0) {
                throw new IllegalArgumentException("seconds must be > 0");
            }
            TransferSpecs ts = new TransferSpecs();
            ts.motor = motor;
            ts.power = power;
            ts.seconds = seconds;
            return ts;
        }

        /**
         * Student-friendly factory: build specs from a hardware name.
         *
         * <p>This mirrors the {@code Plants.*(hardwareMap, name, ...)} helpers:
         * call this from robot wiring code instead of touching {@link MotorOutput}
         * or {@link FtcHardware} directly.</p>
         *
         * @param hw       hardware map
         * @param name     configured motor name
         * @param inverted whether to invert the motor direction
         * @param power    forward pulse power (reverse is -power)
         * @param seconds  pulse duration in seconds (must be &gt; 0)
         */
        public static TransferSpecs powerFor(HardwareMap hw,
                                             String name,
                                             boolean inverted,
                                             double power,
                                             double seconds) {
            MotorOutput m = FtcHardware.motor(hw, name, inverted);
            return powerFor(m, power, seconds);
        }

        /**
         * Set the downstream readiness condition.
         *
         * <p>If {@code ready} is null, a default "always ready" condition is used.</p>
         */
        public TransferSpecs downstreamReady(BooleanSupplier ready) {
            this.downstreamReady = (ready != null) ? ready : ALWAYS_READY;
            return this;
        }
    }

    // ---------------------------------------------------------------------
    // Builder
    // ---------------------------------------------------------------------

    public static final class Builder {
        private String name = "Buffer";
        private TransferSpecs egressSpecs;

        private Builder() {
        }

        /**
         * Set a human-readable name (used for telemetry diagnostics).
         */
        public Builder name(String name) {
            if (name != null && !name.isEmpty()) {
                this.name = name;
            }
            return this;
        }

        /**
         * Configure egress (forward) behavior.
         *
         * <p>Required; there is no sensible default.</p>
         */
        public Builder egress(TransferSpecs specs) {
            this.egressSpecs = specs;
            return this;
        }

        /**
         * Build the {@link BufferStage}.
         */
        public BufferStage build() {
            if (egressSpecs == null) {
                throw new IllegalStateException("egress TransferSpecs must be provided");
            }
            return new BufferStage(name, egressSpecs);
        }
    }

    /**
     * Start building a new {@link BufferStage}.
     */
    public static Builder builder() {
        return new Builder();
    }

    // ---------------------------------------------------------------------
    // Instance state
    // ---------------------------------------------------------------------

    private final String name;
    private final TransferSpecs egress;

    // Queue counts: how many SEND or EJECT pulses are waiting to be run.
    private int queuedSendCount = 0;
    private int queuedEjectCount = 0;

    // Active pulse state.
    private boolean active = false;
    private boolean activeEject = false; // true if current pulse is an EJECT
    private double remainingSec = 0.0;

    private BufferStage(String name, TransferSpecs egress) {
        this.name = name;
        this.egress = egress;
    }

    // ---------------------------------------------------------------------
    // Public API
    // ---------------------------------------------------------------------

    /**
     * Handle a buffer command, typically invoked from input bindings.
     *
     * <ul>
     *   <li>{@link BufferCmd#SEND} – increments the queued SEND count. Pulses
     *       will be executed one at a time when the stage is idle and
     *       {@code downstreamReady} is true.</li>
     *   <li>{@link BufferCmd#EJECT} – increments the queued EJECT count. Pulses
     *       will be executed as soon as the stage is idle, ignoring downstream
     *       readiness.</li>
     *   <li>{@link BufferCmd#CANCEL} – stops any active pulse, clears both
     *       queues, and stops the motor.</li>
     * </ul>
     */
    public void handle(BufferCmd cmd) {
        if (cmd == null) {
            return;
        }
        switch (cmd) {
            case SEND:
                queuedSendCount++;
                break;

            case EJECT:
                queuedEjectCount++;
                break;

            case CANCEL:
                queuedSendCount = 0;
                queuedEjectCount = 0;
                stopPulse();
                break;
        }
    }

    /**
     * Update the buffer stage.
     *
     * <p>Call this once per loop with the loop delta time (seconds).</p>
     *
     * @param dtSec delta time in seconds
     */
    public void update(double dtSec) {
        if (dtSec < 0.0) {
            dtSec = 0.0;
        }

        // Update active pulse, if any.
        if (active) {
            remainingSec -= dtSec;
            if (remainingSec <= 0.0) {
                stopPulse();
            }
        }

        // If idle, try to start a new pulse from the queue.
        if (!active) {
            tryStartNextPulse();
        }
    }

    /**
     * @return true if a pulse is currently active.
     */
    public boolean isActive() {
        return active;
    }

    /**
     * @return number of queued SEND pulses.
     */
    public int getQueuedSendCount() {
        return queuedSendCount;
    }

    /**
     * @return number of queued EJECT pulses.
     */
    public int getQueuedEjectCount() {
        return queuedEjectCount;
    }

    /**
     * @return the configured stage name.
     */
    public String getName() {
        return name;
    }

    // ---------------------------------------------------------------------
    // Internal helpers
    // ---------------------------------------------------------------------

    private void tryStartNextPulse() {
        // EJECT pulses take priority when queued, ignoring downstream readiness.
        if (queuedEjectCount > 0) {
            queuedEjectCount--;
            startPulse(true);
            return;
        }

        // Otherwise, consider SEND pulses, but only when downstream is ready.
        if (queuedSendCount > 0 && egress.downstreamReady.getAsBoolean()) {
            queuedSendCount--;
            startPulse(false);
        }
    }

    private void startPulse(boolean eject) {
        active = true;
        activeEject = eject;
        remainingSec = egress.seconds;

        double p = egress.power;
        if (activeEject) {
            p = -p;
        }
        egress.motor.setPower(p);
    }

    private void stopPulse() {
        active = false;
        activeEject = false;
        remainingSec = 0.0;
        egress.motor.setPower(0.0);
    }
}

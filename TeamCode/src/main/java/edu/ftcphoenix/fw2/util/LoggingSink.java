package edu.ftcphoenix.fw2.util;

import java.util.function.Consumer;

import edu.ftcphoenix.fw2.actuation.EffortSink;

/**
 * Decorator for EffortSink that logs each applied effort.
 * - Use a Consumer<String> for flexible routing (Driver Station telemetry, file, etc.).
 * - Optional throttle to reduce spam (seconds between logs).
 */
public class LoggingSink implements EffortSink {
    private final EffortSink inner;
    private final Consumer<String> logger;
    private final double throttleSec;
    private double lastLogTime = -1; // wall clock seconds; set via setTimeSource

    /** Provides current time in seconds (monotonic). */
    public interface TimeSource { double now(); }
    private TimeSource timeSource = System::nanoTimeSeconds;

    public LoggingSink(EffortSink inner, Consumer<String> logger) {
        this(inner, logger, 0.0);
    }

    public LoggingSink(EffortSink inner, Consumer<String> logger, double throttleSec) {
        this.inner = inner;
        this.logger = logger;
        this.throttleSec = Math.max(0, throttleSec);
    }

    public LoggingSink setTimeSource(TimeSource ts) {
        this.timeSource = ts != null ? ts : System::nanoTimeSeconds;
        return this;
    }

    @Override
    public void applyEffort(double effort) {
        inner.applyEffort(effort);
        double now = timeSource.now();
        if (shouldLog(now)) {
            logger.accept(String.format("Effort applied: %.6f", effort));
            lastLogTime = now;
        }
    }

    private boolean shouldLog(double now) {
        return throttleSec <= 0 || lastLogTime < 0 || (now - lastLogTime) >= throttleSec;
    }

    /** Small system helper */
    private static class System {
        static double nanoTimeSeconds() { return java.lang.System.nanoTime() * 1e-9; }
    }
}

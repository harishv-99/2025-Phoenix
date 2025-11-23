package edu.ftcphoenix.fw2.util;

/**
 * Frame-based loop clock.
 *
 * Usage:
 *   clock.beginFrame();                // once at the top of each loop
 *   double dt = clock.getDtSec();      // seconds elapsed since previous beginFrame()
 *   long   ts = clock.getFrameTimestampNanos(); // timestamp for this frame
 *
 * Notes:
 * - Exactly ONE System.nanoTime() call per frame (in beginFrame()).
 * - Large dt can be clamped to 0 to protect integrators after long pauses.
 * - Not thread-safe; intended for the OpMode loop thread.
 */
public final class LoopClock implements edu.ftcphoenix.fw2.core.FrameClock {
    /** Max Δt to accept in seconds. Larger frames are treated as reset frames (dt=0). */
    private double maxDtSec = 0.2; // tune to taste (e.g., 0.2s == 5 Hz)

    /** Timestamp of previous frame (nanos), or -1 if never initialized. */
    private long prevStampNanos = -1;

    /** Cached values for the current frame, valid after beginFrame(). */
    private long   frameStampNanos = -1;
    private double frameDtSec      = 0.0;

    public LoopClock() {}
    public LoopClock(double maxDtSec) { this.maxDtSec = Math.abs(maxDtSec); }

    /**
     * Stamp the start of a new frame, compute and cache dt.
     * Call exactly once per loop, at the top.
     */
    public void beginFrame() {
        final long now = System.nanoTime();
        frameStampNanos = now;

        if (prevStampNanos < 0) {
            // First frame: establish baseline
            frameDtSec = 0.0;
        } else {
            double dt = (now - prevStampNanos) * 1e-9;
            // Defensive clamps (protect controllers after long pauses or time quirks)
            if (dt < 0 || dt > maxDtSec) dt = 0.0;
            frameDtSec = dt;
        }
        prevStampNanos = now;
    }

    /** Current frame timestamp (nanos), valid after beginFrame(). */
    public long nanoTime() { return frameStampNanos; }

    /** Elapsed seconds since the previous beginFrame(). */
    public double dtSec() { return frameDtSec; }

    /** Manually reset the baseline; next beginFrame() will report dt=0. */
    public void reset() { prevStampNanos = -1; frameStampNanos = -1; frameDtSec = 0.0; }

    /** Configure the maximum acceptable Δt (seconds) before treating a frame as a reset. */
    public void setMaxDtSec(double maxDtSec) { this.maxDtSec = Math.abs(maxDtSec); }
    public double getMaxDtSec() { return maxDtSec; }
}

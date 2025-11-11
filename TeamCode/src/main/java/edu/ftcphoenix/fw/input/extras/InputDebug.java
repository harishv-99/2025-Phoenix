package edu.ftcphoenix.fw.input.extras;

import edu.ftcphoenix.fw.input.Axis;
import edu.ftcphoenix.fw.input.Button;

import org.firstinspires.ftc.robotcore.external.Telemetry;

import java.util.Locale;

/**
 * Compact telemetry utilities for debugging Phoenix input mappings.
 *
 * <h2>Why</h2>
 * Most bring-up issues are "why didn't this fire?" or "is my shaping right?".
 * These helpers make that obvious without spamming the driver station.
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * // After pads.update(dt) and before telemetry.update():
 * InputDebug.axis(t, "p1.lx", kit.p1().lx());
 * InputDebug.axis(t, "p1.ly", kit.p1().ly());
 * InputDebug.button(t, "fireChord", kit.p1().chordLB_A());
 * InputDebug.buttonsRow(t, "face", kit.p1().cross(), kit.p1().circle(),
 *                             kit.p1().square(), kit.p1().triangle());
 * }</pre>
 *
 * <h2>Conventions</h2>
 * <ul>
 *   <li>Axis line shows current/previous values and a small 21-char bar graph.</li>
 *   <li>Button line shows flags: D=down, P=justPressed, R=justReleased,
 *       DT=doubleTap edge, LH=longHold edge, HL=holding past long-hold.</li>
 * </ul>
 *
 * <p>All methods are allocation-light (a couple short strings per call) and
 * safe to call every loop.</p>
 */
public final class InputDebug {
    private InputDebug() {
    }

    // =====================================================================
    // Axis helpers
    // =====================================================================

    /**
     * Print one line for an axis: current value, previous value, and a small bar.
     * Call after inputs are updated for the tick.
     */
    public static void axis(Telemetry t, String label, Axis a) {
        if (t == null || a == null) return;
        double v = clamp(a.get());
        double p = clamp(a.getPrev());
        t.addData(label, "%s v=% .3f prev=% .3f", bar21(v), v, p);
    }

    /**
     * Bar-only version (0..1 or -1..1 values are fine).
     */
    public static void axisBar(Telemetry t, String label, double v) {
        if (t == null) return;
        t.addData(label, bar21(clamp(v)));
    }

    // =====================================================================
    // Button helpers
    // =====================================================================

    /**
     * Print one line for a button with edge/gesture flags.
     * D=down, P=justPressed, R=justReleased, DT=doubleTap edge, LH=longHold edge, HL=holding past long-hold.
     */
    public static void button(Telemetry t, String label, Button b) {
        if (t == null || b == null) return;
        String flags = flags(b);
        t.addData(label, flags);
    }

    /**
     * Print a compact row for multiple buttons. Example:
     * face: [D P ..] [.. R ..] [...]
     */
    public static void buttonsRow(Telemetry t, String rowLabel, Button... buttons) {
        if (t == null || buttons == null || buttons.length == 0) return;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < buttons.length; i++) {
            if (i > 0) sb.append(' ');
            sb.append('[').append(flags(buttons[i])).append(']');
        }
        t.addData(rowLabel, sb.toString());
    }

    // =====================================================================
    // internals
    // =====================================================================

    private static String flags(Button b) {
        if (b == null) return "--";
        boolean d = b.isDown();
        boolean jp = b.justPressed();
        boolean jr = b.justReleased();
        boolean dt = b.justDoubleTapped();
        boolean lh = b.longHoldStarted();
        boolean hl = b.isLongHeld();

        // Compact, predictable order
        return String.format(Locale.US, "%s%s%s%s%s%s",
                d ? "D" : ".",
                jp ? "P" : ".",
                jr ? "R" : ".",
                dt ? "DT" : "..",
                lh ? "LH" : "..",
                hl ? "HL" : ".."
        );
    }

    /**
     * 21-character bar: index 0..20 with center at 10; shows sign and magnitude.
     */
    private static String bar21(double v) {
        final int width = 21;
        final int mid = (width - 1) / 2; // 10
        char[] chars = new char[width];
        for (int i = 0; i < width; i++) chars[i] = '·';

        int idx = (int) Math.round((v + 1.0) * mid); // map [-1,1] → [0,20]
        idx = Math.max(0, Math.min(width - 1, idx));
        chars[mid] = '|';
        if (idx >= mid) {
            for (int i = mid + 1; i <= idx; i++) chars[i] = '=';
            chars[idx] = '>';
        } else {
            for (int i = mid - 1; i >= idx; i--) chars[i] = '=';
            chars[idx] = '<';
        }
        return new String(chars);
    }

    private static double clamp(double v) {
        if (v > 1.0) return 1.0;
        if (v < -1.0) return -1.0;
        return v;
    }
}

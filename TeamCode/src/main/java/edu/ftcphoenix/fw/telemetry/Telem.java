// fw/telemetry/Telem.java
package edu.ftcphoenix.fw.telemetry;

/**
 * Super-lightweight structured telemetry hook.
 * If no appender is installed, all calls are no-ops (zero overhead).
 */
public final class Telem {
    private Telem() {
    }

    public interface Appender {
        void add(String tag, String message);
    }

    private static volatile Appender appender = null;

    /**
     * Install an appender (e.g., wraps FTC telemetry). Pass null to disable.
     */
    public static void set(Appender a) {
        appender = a;
    }

    /**
     * Log a one-line event under a tag (e.g., "chamber.window", "intake.transport").
     */
    public static void event(String tag, String message) {
        Appender a = appender;
        if (a != null) a.add(tag, message);
    }

    /**
     * Log key=value under a tag.
     */
    public static void kv(String tag, String key, Object value) {
        Appender a = appender;
        if (a != null) a.add(tag, key + "=" + String.valueOf(value));
    }
}

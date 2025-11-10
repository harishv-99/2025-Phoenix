package edu.ftcphoenix.fw2.core;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Runs multiple {@link Controller} instances with a single {@link #update(FrameClock)} call.
 *
 * <p>Features:</p>
 * <ul>
 *   <li><b>Deterministic order:</b> preserves insertion order (e.g., sensors → inner loops → outer loops).</li>
 *   <li><b>Per-controller enable/disable:</b> gate specific controllers at runtime.</li>
 *   <li><b>Failure isolation:</b> optionally swallow exceptions so one failure doesn’t stop the rest.</li>
 * </ul>
 *
 * <h2>When to use</h2>
 * <ul>
 *   <li>Compose your robot loop from multiple controllers while controlling execution order.</li>
 *   <li>Enable/disable large features (e.g., auto-aim) without altering wiring code.</li>
 * </ul>
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * ControllerGroup loop = new ControllerGroup()
 *     .put("sensors", sensors)
 *     .put("inner", velocityPid)
 *     .put("outer", positionPid);
 * loop.update(clock);
 * }</pre>
 *
 * <h2>Best practices</h2>
 * <ul>
 *   <li>Consider providing a logging hook for caught exceptions (e.g., a {@code Consumer<Throwable>})
 *       instead of a silent TODO.</li>
 *   <li>Names should be unique; {@link #put(String, Controller)} replaces by name.</li>
 * </ul>
 */
public class ControllerGroup implements Controller {
    private final Map<String, Entry> entries = new LinkedHashMap<>();
    private boolean swallowExceptions = true;

    private static class Entry {
        final Controller controller;
        boolean enabled = true;

        Entry(Controller c) {
            this.controller = c;
        }
    }

    /**
     * Add or replace a controller under a name.
     */
    public ControllerGroup put(String name, Controller controller) {
        entries.put(name, new Entry(controller));
        return this;
    }

    public ControllerGroup enable(String name, boolean enabled) {
        Entry e = entries.get(name);
        if (e != null) e.enabled = enabled;
        return this;
    }

    public boolean isEnabled(String name) {
        Entry e = entries.get(name);
        return e != null && e.enabled;
    }

    /**
     * If true, exceptions are caught per-controller; otherwise they propagate.
     */
    public ControllerGroup setSwallowExceptions(boolean swallow) {
        this.swallowExceptions = swallow;
        return this;
    }

    @Override
    public void update(FrameClock clock) {
        for (Map.Entry<String, Entry> en : entries.entrySet()) {
            Entry e = en.getValue();
            if (!e.enabled) continue;
            if (swallowExceptions) {
                try {
                    e.controller.update(clock);
                } catch (Throwable t) {
                    // TODO: route to your telemetry/logging if desired
                }
            } else {
                e.controller.update(clock);
            }
        }
    }

    public boolean contains(String name) {
        return entries.containsKey(name);
    }

    public Controller get(String name) {
        return entries.get(name) != null ? entries.get(name).controller : null;
    }

    public ControllerGroup clear() {
        entries.clear();
        return this;
    }
}

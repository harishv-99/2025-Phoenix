package edu.ftcphoenix.fw.telemetry;

import edu.ftcphoenix.fw.core.Sink;

/**
 * Wraps any Sink<T> and emits a labeled telemetry line whenever accept(T) is called.
 * Keeps telemetry semantics *outside* your intents and stages.
 */
public final class NamedSink<T> implements Sink<T> {
    public interface Stringify<T> {
        String asString(T value);
    }

    private final String tag;
    private final Sink<T> downstream;
    private final Stringify<T> fmt;

    public NamedSink(String tag, Sink<T> downstream, Stringify<T> fmt) {
        this.tag = (tag == null ? "sink" : tag);
        this.downstream = downstream;
        this.fmt = (fmt == null) ? new Stringify<T>() {
            public String asString(T v) {
                return String.valueOf(v);
            }
        } : fmt;
    }

    @Override
    public void accept(T intent) {
        Telem.event(tag, fmt.asString(intent)); // no-op if no appender installed
        downstream.accept(intent);
    }

    /**
     * Convenience factory.
     */
    public static <T> NamedSink<T> of(String tag, Sink<T> downstream, Stringify<T> fmt) {
        return new NamedSink<T>(tag, downstream, fmt);
    }
}

package edu.ftcphoenix.fw.core;

/**
 * Minimal semantic command sink. Implementations should be non-blocking and fast.
 */
public interface Sink<T> {
    void accept(T intent);
}

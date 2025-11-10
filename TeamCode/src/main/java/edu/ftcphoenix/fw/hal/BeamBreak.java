package edu.ftcphoenix.fw.hal;

/**
 * Simple piece-present sensor.
 *
 * <p>Return {@code true} when the beam is blocked (piece present),
 * {@code false} otherwise. Implementations should encode any active-low mapping
 * so framework code doesn't care about wiring polarity.</p>
 */
public interface BeamBreak {
    boolean blocked();
}

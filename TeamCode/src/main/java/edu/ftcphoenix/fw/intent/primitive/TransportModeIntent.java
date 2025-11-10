package edu.ftcphoenix.fw.intent.primitive;

/**
 * Semantic transport command for belts/rollers/conveyors inside the robot.
 *
 * <p>Stages translate this into motor power (e.g., +feed, -reverse, 0 idle). Keeping
 * the semantic layer decoupled from raw power makes reuse across different hardware easy.</p>
 */
public final class TransportModeIntent {
    public enum Mode {FEED, REVERSE, IDLE}

    private final Mode mode;

    private TransportModeIntent(Mode mode) {
        this.mode = mode;
    }

    public static TransportModeIntent feed() {
        return new TransportModeIntent(Mode.FEED);
    }

    public static TransportModeIntent reverse() {
        return new TransportModeIntent(Mode.REVERSE);
    }

    public static TransportModeIntent idle() {
        return new TransportModeIntent(Mode.IDLE);
    }

    public Mode mode() {
        return mode;
    }

    @Override
    public String toString() {
        return "TransportModeIntent{" + mode + "}";
    }

    @Override
    public int hashCode() {
        return mode.hashCode();
    }

    @Override
    public boolean equals(Object o) {
        return (o instanceof TransportModeIntent) && ((TransportModeIntent) o).mode == this.mode;
    }
}

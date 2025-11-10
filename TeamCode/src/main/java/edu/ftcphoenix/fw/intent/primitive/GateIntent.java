package edu.ftcphoenix.fw.intent.primitive;

/**
 * Semantic command for a gate/claw/etc. that has two logical states: OPEN or CLOSED.
 *
 * <p>Stage code should translate this to actuator-specific outputs (e.g., servo positions).</p>
 */
public final class GateIntent {
    /**
     * Logical state.
     */
    public enum State {OPEN, CLOSED}

    private final State state;

    private GateIntent(State state) {
        this.state = state;
    }

    public static GateIntent open() {
        return new GateIntent(State.OPEN);
    }

    public static GateIntent closed() {
        return new GateIntent(State.CLOSED);
    }

    public State state() {
        return state;
    }

    @Override
    public String toString() {
        return "GateIntent{" + state + "}";
    }

    @Override
    public int hashCode() {
        return state.hashCode();
    }

    @Override
    public boolean equals(Object o) {
        return (o instanceof GateIntent) && ((GateIntent) o).state == this.state;
    }
}

package edu.ftcphoenix.fw.fsm;

public final class NullState implements FsmState {
    private static NullState state;

    private NullState() {
    }

    @Override
    public void initState() {
        // do nothing
    }

    @Override
    public void executeState() {
        // do nothing
    }

    @Override
    public void exitState() {
        // do nothing
    }

    @Override
    public String getName() {
        return null;
    }

    @Override
    public void setContainer(FsmContainer container) {
        // do nothing
    }

    @Override
    public FsmContainer getContainer() {
        return null;
    }

    public static FsmState getInstance() {
        if (state == null) {
            state = new NullState();
        }

        return state;
    }
}

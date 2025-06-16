package edu.ftcphoenix.fw.fsm;

public abstract class AbstractFsmState implements FsmState {

    private FsmContainer container;

    @Override
    public String getName() {
        return this.getClass().getSimpleName();
    }

    @Override
    public void setContainer(FsmContainer container) {
        this.container = container;
    }

    @Override
    public FsmContainer getContainer() {
        return container;
    }
}

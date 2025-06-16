package edu.ftcphoenix.fw.fsm;

public abstract class AbstractFsmContainerState extends AbstractFsmContainer implements FsmState {

    FsmContainer container;

    @Override
    public void initState() {
        initContainer();
    }

    @Override
    public void executeState() {
        getCurrentState().executeState();
    }

    @Override
    public void exitState() {
        getCurrentState().exitState();
    }

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

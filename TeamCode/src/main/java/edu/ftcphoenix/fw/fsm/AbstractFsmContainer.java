package edu.ftcphoenix.fw.fsm;

import java.util.HashMap;

public abstract class AbstractFsmContainer implements FsmContainer{

    private final HashMap<String, FsmState> states = new HashMap<>();
    private FsmState currentState = NullState.getInstance();
    private FsmState startState = NullState.getInstance();
    private boolean hasInitializedContainer = false;


    @Override
    public void addState(FsmState state) {
        // If the container has been initialized, we cannot add any more states.
        if (hasInitializedContainer)
            throw new IllegalStateException("Cannot add state to container after it has been initialized.");

        // If this is the first state being added, make it the start state.
        if (states.isEmpty()) {
            startState = state;
        }

        // Add the new state to the list of states.
        states.put(state.getName(), state);

        // Have the state point back to this container.
        state.setContainer(this);
    }

    @Override
    public void transitionToState(String stateId) {
        // Ensure that the container has been initialized.
        if (!hasInitializedContainer)
            throw new IllegalStateException("Cannot transition to a state in container without initializing container.");

        // Make sure that the new state to transition to is found.
        if (!states.containsKey(stateId)) {
            throw new IllegalArgumentException("Trying to transition to non-existent state ["
                    + stateId + "].");
        }

        // Transition to the new state by exiting the current state
        currentState.exitState();
        currentState = states.get(stateId);
        assert currentState != null;
        currentState.initState();
    }

    @Override
    public FsmState getCurrentState() {
        // Ensure that the container has been initialized.
        if (!hasInitializedContainer)
            throw new IllegalStateException("Cannot get current state in container without initializing container.");

        return currentState;
    }


    @Override
    public void initContainer() {
        hasInitializedContainer = true;

        // Move the first state to execute to be the start state
        currentState = startState;
        currentState.initState();
    }

    @Override
    public void executeContainer() {
        // Ensure that the container has been initialized.
        if (!hasInitializedContainer)
            throw new IllegalStateException("Cannot execute container without initializing container.");

        currentState.executeState();
    }

    @Override
    public void exitContainer() {
        // Ensure that the container has been initialized.
        if (!hasInitializedContainer)
            throw new IllegalStateException("Cannot exit container without initializing container.");

        // Exit the current state of the FSM.
        currentState.exitState();
    }
}

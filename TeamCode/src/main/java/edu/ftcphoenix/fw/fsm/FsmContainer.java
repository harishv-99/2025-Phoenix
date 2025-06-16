package edu.ftcphoenix.fw.fsm;

/**
 * Implements a finite state machine container which contains all the states in the machine in the
 * next level of depth.  Each state can in itself be a separate {@link FsmContainer} and have its own
 * finite state machine.
 */
public interface FsmContainer {

    /**
     * Add a state to the finite state machine.  This will also set the state's container
     * ({@link FsmState#setContainer(FsmContainer)}).  The name of the state is queried
     * through {@link FsmState#getName()}.
     *
     * @param state The new state to add.
     */
    void addState(FsmState state);

    /**
     * Transition to a new state from the list of possible states in this container.
     *
     * @param stateId ID of the new state.
     */
    void transitionToState(String stateId);

    /**
     * Get a reference to the current state.
     *
     * @return Current {@link FsmState} in the state machine.
     */
    FsmState getCurrentState();

    /**
     * Initialize the finite state machine container.  This will initialize the start state
     * within the machine as well.
     */
    void initContainer();

    /**
     * Execute the finite state machine
     */
    void executeContainer();

    /**
     * Exit the container.  This will also exit the current state within the machine.
     */
    void exitContainer();
}
